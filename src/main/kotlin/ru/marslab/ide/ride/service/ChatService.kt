package ru.marslab.ide.ride.service

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.model.*

/**
 * Центральный сервис для управления чатом
 * 
 * Application Service (Singleton) для координации между UI и Agent.
 * Управляет историей сообщений и обработкой запросов.
 */
@Service(Service.Level.APP)
class ChatService {
    
    private val logger = Logger.getInstance(ChatService::class.java)
    private val messageHistory = MessageHistory()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Текущие настройки формата ответа (для UI)
    private var currentFormat: ResponseFormat? = null
    private var currentSchema: ResponseSchema? = null
    
    // Агент создается лениво при первом использовании
    private var agent: Agent? = null
    
    /**
     * Получает или создает агента
     */
    private fun getAgent(): Agent {
        if (agent == null) {
            agent = AgentFactory.createChatAgent()
            logger.info("Agent created: ${agent?.getName()}")
        }
        return agent!!
    }
    
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
        messageHistory.addMessage(userMsg)
        
        // Обрабатываем запрос асинхронно
        scope.launch {
            try {
                // Проверяем настройки в фоновом потоке (не на EDT!)
                val agent = getAgent()
                if (!agent.getName().isNotBlank()) {
                    withContext(Dispatchers.EDT) {
                        onError("Плагин не настроен. Перейдите в Settings → Tools → Ride")
                    }
                    return@launch
                }
                
                // Формируем контекст
                val context = ChatContext(
                    project = project,
                    history = messageHistory.getMessages()
                )
                
                // Отправляем запрос агенту
                val agentResponse = getAgent().processRequest(userMessage, context)
                
                // Обрабатываем ответ в UI потоке
                withContext(Dispatchers.EDT) {
                    if (agentResponse.success) {
                        // Создаем и сохраняем сообщение ассистента
                        val assistantMsg = Message(
                            content = agentResponse.content,
                            role = MessageRole.ASSISTANT,
                            metadata = agentResponse.metadata
                        )
                        messageHistory.addMessage(assistantMsg)
                        
                        logger.info("Response received successfully")
                        onResponse(assistantMsg)
                    } else {
                        logger.warn("Agent returned error: ${agentResponse.error}")
                        onError(agentResponse.error ?: "Неизвестная ошибка")
                    }
                }
                
            } catch (e: Exception) {
                logger.error("Error processing message", e)
                withContext(Dispatchers.EDT) {
                    onError("Ошибка обработки сообщения: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Возвращает историю сообщений
     * 
     * @return Список всех сообщений
     */
    fun getHistory(): List<Message> {
        return messageHistory.getMessages()
    }
    
    /**
     * Очищает историю чата
     */
    fun clearHistory() {
        logger.info("Clearing chat history")
        messageHistory.clear()
    }
    
    /**
     * Проверяет, пуста ли история
     * 
     * @return true если история пуста
     */
    fun isHistoryEmpty(): Boolean {
        return messageHistory.isEmpty()
    }
    
    /**
     * Пересоздает агента с новыми настройками
     * 
     * Вызывается после изменения настроек плагина
     */
    fun recreateAgent() {
        logger.info("Recreating agent with new settings")
        agent = null
    }
    
    /**
     * Устанавливает формат ответа для текущего агента
     */
    fun setResponseFormat(format: ResponseFormat, schema: ResponseSchema?) {
        getAgent().setResponseFormat(format, schema)
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
        messageHistory.addMessage(
            Message(content = content, role = MessageRole.SYSTEM)
        )
    }

    /**
     * Сбрасывает формат ответа к TEXT (по умолчанию)
     */
    fun clearResponseFormat() {
        getAgent().clearResponseFormat()
        logger.info("Response format cleared")
        currentFormat = null
        currentSchema = null
        messageHistory.addMessage(
            Message(
                content = "Формат ответа сброшен. Отвечай обычным текстом без пояснений о формате.",
                role = MessageRole.SYSTEM
            )
        )
    }

    /**
     * Возвращает текущий установленный формат ответа
     */
    fun getResponseFormat(): ResponseFormat? = currentFormat ?: getAgent().getResponseFormat()

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
}
