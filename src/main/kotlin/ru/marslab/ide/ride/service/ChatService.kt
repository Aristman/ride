package ru.marslab.ide.ride.service

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceProvider
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTProvider
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.model.*
import ru.marslab.ide.ride.model.ChatSession
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.util.TokenEstimator
import java.time.Instant

/**
 * Центральный сервис для управления чатом
 *
 * Application Service (Singleton) для координации между UI и Agent.
 * Управляет историей сообщений и обработкой запросов.
 */
@Service(Service.Level.APP)
class ChatService {

    private val logger = Logger.getInstance(ChatService::class.java)

    // Несколько сессий и история сообщений по каждой
    private val sessionHistories = mutableMapOf<String, MessageHistory>()
    private val sessions = mutableListOf<ChatSession>()
    private var currentSessionId: String = createNewSessionInternal().id
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Текущие настройки формата ответа (для UI)
    private var currentFormat: ResponseFormat? = null
    private var currentSchema: ResponseSchema? = null

    // Агент создаётся и может быть пересоздан при смене настроек
    private var agent: Agent = AgentFactory.createChatAgent()

    /**
     * Отправляет сообщение пользователя и получает ответ от агента
     *
     * @param userMessage Текст сообщения пользователя
     * @param project Текущий проект
     * @param onResponse Callback для получения ответа
     * @param onError Callback для обработки ошибок
     */
    fun sendMessage(
        userMessage: String,
        project: Project,
        onResponse: (Message) -> Unit,
        onError: (String) -> Unit
    ) {
        if (userMessage.isBlank()) {
            onError("Сообщение не может быть пустым")
            return
        }

        logger.info("Sending user message, length: ${userMessage.length}")

        // Создаем и сохраняем сообщение пользователя
        val userMsg = Message(
            content = userMessage,
            role = MessageRole.USER
        )
        val history = getCurrentHistory()
        val wasEmpty = history.getMessageCount() == 0
        history.addMessage(userMsg)
        if (wasEmpty) {
            // Авто-именование сессии по первым словам
            val title = deriveTitleFrom(userMessage)
            updateSessionTitle(currentSessionId, title)
        }

        // Обрабатываем запрос асинхронно
        scope.launch {
            try {
                // Проверяем настройки в фоновом потоке (не на EDT!)
                val chatAgent = agent as? ChatAgent
                if (chatAgent != null && !chatAgent.getName().isNotBlank()) {
                    withContext(Dispatchers.EDT) {
                        onError("Плагин не настроен. Перейдите в Settings → Tools → Ride")
                    }
                    return@launch
                }

                // Формируем контекст
                val context = ChatContext(
                    project = project,
                    history = getCurrentHistory().getMessages()
                )

                // Получаем параметры из настроек
                val settings = service<PluginSettings>()
                val llmParameters = LLMParameters(
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens
                )

                // Создаем запрос к агенту
                val agentRequest = AgentRequest(
                    request = userMessage,
                    context = context,
                    parameters = llmParameters
                )

                // Измеряем время выполнения запроса к LLM
                val startTime = System.currentTimeMillis()
                val agentResponse = agent.ask(agentRequest)
                val responseTime = System.currentTimeMillis() - startTime

                // Обрабатываем ответ в UI потоке
                withContext(Dispatchers.EDT) {
                    if (agentResponse.success) {
                        // Получаем количество токенов из ответа или оцениваем приблизительно
                        val tokensFromResponse = agentResponse.metadata["tokensUsed"] as? Int ?: 0
                        val tokensUsed = if (tokensFromResponse > 0) {
                            tokensFromResponse
                        } else {
                            // Оцениваем токены по размеру запроса и ответа
                            TokenEstimator.estimateTotalTokens(userMessage, agentResponse.content)
                        }
                        
                        // Создаем и сохраняем сообщение ассистента с учетом анализа неопределенности
                        val metadata = agentResponse.metadata + mapOf(
                            "isFinal" to agentResponse.isFinal,
                            "uncertainty" to (agentResponse.uncertainty ?: 0.0),
                            "responseTimeMs" to responseTime,
                            "tokensUsed" to tokensUsed
                        )

                        val assistantMsg = Message(
                            content = agentResponse.content,
                            role = MessageRole.ASSISTANT,
                            metadata = metadata
                        )
                        getCurrentHistory().addMessage(assistantMsg)
                        onResponse(assistantMsg)
                    } else {
                        logger.warn("Agent returned error: ${agentResponse.error}")
                        onError(agentResponse.error ?: "Неизвестная ошибка")
                    }
                }

            } catch (e: Exception) {
                logger.error("Error processing message", e)
            }
        }
    }

    /**
     * Возвращает историю сообщений
     *
     * @return Список всех сообщений
     */
    fun getHistory(): List<Message> = getCurrentHistory().getMessages()

    /**
     * Очищает историю чата
     */
    fun clearHistory() {
        logger.info("Clearing chat history for session $currentSessionId")
        getCurrentHistory().clear()
    }

    /**
     * Проверяет, пуста ли история
     *
     * @return true если история пуста
     */
    fun isHistoryEmpty(): Boolean = getCurrentHistory().isEmpty()

    /**
     * Пересоздаёт агента с новыми настройками
     * Вызывается после изменения настроек плагина
     */
    fun recreateAgent() {
        logger.info("Recreating agent with new settings")
        val previousFormat = currentFormat
        val previousSchema = currentSchema
        agent = AgentFactory.createChatAgent()
        // Восстановим формат ответа, если был задан
        val chatAgent = agent as? ChatAgent
        if (chatAgent != null && previousFormat != null) {
            chatAgent.setResponseFormat(previousFormat, previousSchema)
        }
    }

    /**
     * Устанавливает формат ответа для текущего агента
     */
    fun setResponseFormat(format: ResponseFormat, schema: ResponseSchema?) {
        val chatAgent = agent as? ChatAgent
        chatAgent?.setResponseFormat(format, schema)
        logger.info("Response format set to: $format")
        currentFormat = format
        currentSchema = schema
        // Добавляем системное сообщение в историю, чтобы переопределить контекст LLM
        val schemaHint = when (format) {
            ResponseFormat.JSON -> (schema?.schemaDefinition ?: "{}")
            ResponseFormat.XML -> (schema?.schemaDefinition ?: "<root/>")
            ResponseFormat.TEXT -> ""
        }
        val content = buildString {
            append("Формат ответа изменён на ")
            append(format.name)
            if (schemaHint.isNotBlank()) {
                append(". Следуй СТРОГО схеме без пояснений и текста вне структуры.\nСхема:\n")
                append(schemaHint)
            } else if (format == ResponseFormat.TEXT) {
                append(". Отвечай обычным текстом без дополнительной разметки.")
            }
        }
        getCurrentHistory().addMessage(
            Message(content = content, role = MessageRole.SYSTEM)
        )
    }

    /**
     * Сбрасывает формат ответа к TEXT (по умолчанию)
     */
    fun clearResponseFormat() {
        val chatAgent = agent as? ChatAgent
        chatAgent?.clearResponseFormat()
        logger.info("Response format cleared")
        currentFormat = null
        currentSchema = null
        getCurrentHistory().addMessage(
            Message(
                content = "Формат ответа сброшен. Отвечай обычным текстом без пояснений о формате.",
                role = MessageRole.SYSTEM
            )
        )
    }

    /**
     * Возвращает текущий установленный формат ответа
     */
    fun getResponseFormat(): ResponseFormat? {
        val chatAgent = agent as? ChatAgent
        return currentFormat ?: chatAgent?.getResponseFormat()
    }

    /**
     * Возвращает текущую схему ответа (если была задана)
     */
    fun getResponseSchema(): ResponseSchema? = currentSchema

    /**
     * Освобождает ресурсы при закрытии
     */
    fun dispose() {
        logger.info("Disposing ChatService")
        scope.cancel()
    }

    /**
     * Возвращает имя текущего провайдера LLM
     * Для HuggingFace возвращает имя модели, для Yandex - "Yandex GPT (имя модели)"
     */
    fun getCurrentProviderName(): String {
        val chatAgent = agent as? ChatAgent
        if (chatAgent != null) {
            val provider = chatAgent.getLLMProvider()
            return when (provider) {
                is HuggingFaceProvider -> provider.getModelDisplayName()
                is YandexGPTProvider -> "${provider.getProviderName()} (${provider.getModelDisplayName()})"
                else -> provider.getProviderName()
            }
        }
        return "Unknown Provider"
    }

    // --- Sessions API ---
    fun createNewSession(title: String = "Session"): ChatSession {
        val s = createNewSessionInternal(title)
        currentSessionId = s.id
        return s
    }

    private fun createNewSessionInternal(title: String = "Session"): ChatSession {
        val session = ChatSession(title = title, createdAt = Instant.now(), updatedAt = Instant.now())
        sessions.add(0, session)
        sessionHistories[session.id] = MessageHistory()
        return session
    }

    fun getSessions(): List<ChatSession> = sessions.toList()

    fun switchSession(sessionId: String): Boolean {
        if (sessions.any { it.id == sessionId }) {
            currentSessionId = sessionId
            return true
        }
        return false
    }

    fun getCurrentSessionId(): String = currentSessionId

    fun updateSessionTitle(sessionId: String, title: String) {
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            sessions[idx] = sessions[idx].copy(title = title.take(50), updatedAt = Instant.now())
        }
    }

    private fun deriveTitleFrom(text: String): String {
        val clean = text.trim().replace("\n", " ").replace("\\s+".toRegex(), " ")
        if (clean.isEmpty()) return "Session"
        val words = clean.split(' ')
        val pick = words.take(3).joinToString(" ")
        return pick.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun getCurrentHistory(): MessageHistory =
        sessionHistories.getOrPut(currentSessionId) { MessageHistory() }
}
