package ru.marslab.ide.ride.service

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.agent.OrchestratorStep
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceProvider
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTProvider
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.agent.impl.MCPFileSystemAgent
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTConfig
import ru.marslab.ide.ride.mcp.MCPServerManager
import ru.marslab.ide.ride.model.agent.*
import ru.marslab.ide.ride.model.chat.*
import ru.marslab.ide.ride.model.llm.*
import ru.marslab.ide.ride.model.schema.*
import ru.marslab.ide.ride.model.chat.ChatSession
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
                if (chatAgent != null && chatAgent.getProvider().getProviderName().isBlank()) {
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
                        // Получаем детальную статистику токенов из ответа
                        val tokenUsage = agentResponse.metadata["tokenUsage"] as? TokenUsage
                        val tokensUsed = tokenUsage?.totalTokens ?: run {
                            // Fallback: оцениваем токены по размеру запроса и ответа
                            TokenEstimator.estimateTotalTokens(userMessage, agentResponse.content)
                        }
                        
                        // Создаем и сохраняем сообщение ассистента с учетом анализа неопределенности
                        val metadata = agentResponse.metadata + mapOf<String, Any>(
                            "isFinal" to agentResponse.isFinal,
                            "uncertainty" to (agentResponse.uncertainty ?: 0.0),
                            "responseTimeMs" to responseTime,
                            "tokensUsed" to tokensUsed,
                            "tokenUsage" to (tokenUsage ?: TokenUsage.EMPTY),
                            "formattedOutput" to (agentResponse.formattedOutput ?: Unit)
                        )

                        // Проверяем наличие системного сообщения о сжатии/обрезке
                        val systemMessage = agentResponse.metadata["systemMessage"] as? String
                        if (systemMessage != null) {
                            // Получаем сжатую историю из метаданных
                            @Suppress("UNCHECKED_CAST")
                            val compressedHistory = agentResponse.metadata["compressedHistory"] as? List<Message>
                            
                            if (compressedHistory != null) {
                                // Заменяем всю историю на сжатую
                                val currentHistory = getCurrentHistory()
                                currentHistory.clear()
                                compressedHistory.forEach { currentHistory.addMessage(it) }
                                logger.info("History replaced with compressed version: ${compressedHistory.size} messages")
                            }
                            
                            // Добавляем системное сообщение в историю
                            val sysMsg = Message(
                                content = systemMessage,
                                role = MessageRole.SYSTEM,
                                metadata = mapOf("type" to "context_management")
                            )
                            getCurrentHistory().addMessage(sysMsg)
                            onResponse(sysMsg)
                        }
                        
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
     * Отправляет сообщение с поддержкой MCP Tools
     * 
     * @param userMessage Текст сообщения пользователя
     * @param project Текущий проект
     * @param onResponse Callback для получения ответа
     * @param onError Callback для обработки ошибок
     * @param onToolExecution Callback для индикации выполнения tool
     */
    fun sendMessageWithTools(
        userMessage: String,
        project: Project,
        onResponse: (Message) -> Unit,
        onError: (String) -> Unit,
        onToolExecution: ((String) -> Unit)? = null
    ) {
        if (userMessage.isBlank()) {
            onError("Сообщение не может быть пустым")
            return
        }

        logger.info("Sending user message with tools support, length: ${userMessage.length}")

        // Проверяем, запущен ли MCP Server
        val serverManager = MCPServerManager.getInstance()
        println("🔧 ChatService: MCP Server running: ${serverManager.isServerRunning()}")

        if (!serverManager.isServerRunning()) {
            println("🔧 ChatService: Starting MCP Server...")
            val started = serverManager.ensureServerRunning()
            println("🔧 ChatService: MCP Server start result: $started")

            if (!started) {
                logger.error("Failed to start MCP Server")
                onError("Не удалось запустить MCP Server. Файловые операции недоступны.")
                return
            }
        }

        // Создаем сообщение пользователя
        val userMsg = Message(
            content = userMessage,
            role = MessageRole.USER
        )
        val history = getCurrentHistory()
        val wasEmpty = history.getMessageCount() == 0

        if (wasEmpty) {
            // Добавляем сообщение пользователя для авто-именования сессии
            history.addMessage(userMsg)
            val title = deriveTitleFrom(userMessage)
            updateSessionTitle(currentSessionId, title)
        }

        // Обрабатываем запрос асинхронно
        scope.launch {
            try {
                // Получаем настройки
                val settings = service<PluginSettings>()
                
                // Создаем конфигурацию для Yandex GPT
                val config = YandexGPTConfig(
                    apiKey = settings.getApiKey(),
                    folderId = settings.folderId,
                    modelId = "yandexgpt-lite"
                )
                
                // Создаем агента с поддержкой tools
                val mcpFileSystemAgent = MCPFileSystemAgent(config)
                
                // Формируем историю для агента (включаем системные сообщения для контекста)
                val allMessages = if (wasEmpty) {
                    // Если история была пустой, пользовательское сообщение уже добавлено
                    history.getMessages()
                } else {
                    // Если история не была пустой, добавляем текущее сообщение
                    history.getMessages() + userMsg
                }

                val conversationHistory = allMessages
                    .map { msg ->
                        ConversationMessage(
                            role = when (msg.role) {
                                MessageRole.USER -> ConversationRole.USER
                                MessageRole.ASSISTANT -> ConversationRole.ASSISTANT
                                MessageRole.SYSTEM -> ConversationRole.SYSTEM
                            },
                            content = msg.content
                        )
                    }
                
                // Получаем параметры
                val llmParameters = LLMParameters(
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens
                )

                // Измеряем время выполнения
                val startTime = System.currentTimeMillis()
                val agentResponse = mcpFileSystemAgent.processRequest(
                    userMessage = userMessage,
                    conversationHistory = conversationHistory,
                    parameters = llmParameters
                )
                val responseTime = System.currentTimeMillis() - startTime

                // Обрабатываем ответ в UI потоке
                withContext(Dispatchers.EDT) {
                    if (agentResponse.success) {
                        // Извлекаем информацию о выполненных tools
                        val executedTools = agentResponse.metadata["executedTools"] as? String
                        val iterations = agentResponse.metadata["iterations"] as? String
                        
                        // Если были выполнены tools, показываем индикатор
                        if (!executedTools.isNullOrBlank() && onToolExecution != null) {
                            onToolExecution("Executed tools: $executedTools")
                        }
                        
                        // Получаем статистику токенов
                        val inputTokens = agentResponse.metadata["inputTokens"] as? String
                        val outputTokens = agentResponse.metadata["outputTokens"] as? String
                        val totalTokens = agentResponse.metadata["totalTokens"] as? String
                        
                        val tokenUsage = if (inputTokens != null && outputTokens != null && totalTokens != null) {
                            TokenUsage(
                                inputTokens = inputTokens.toIntOrNull() ?: 0,
                                outputTokens = outputTokens.toIntOrNull() ?: 0,
                                totalTokens = totalTokens.toIntOrNull() ?: 0
                            )
                        } else {
                            TokenUsage.EMPTY
                        }
                        
                        // Добавляем сообщение пользователя в историю (если еще не добавлено)
                        if (!wasEmpty) {
                            getCurrentHistory().addMessage(userMsg)
                        }

                        // Создаем метаданные
                        val metadata = mapOf<String, Any>(
                            "responseTimeMs" to responseTime,
                            "tokenUsage" to tokenUsage,
                            "executedTools" to (executedTools ?: "none"),
                            "toolIterations" to (iterations ?: "0"),
                            "usedMCPTools" to true,
                            "formattedOutput" to (agentResponse.formattedOutput ?: Unit)
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
                logger.error("Error processing message with tools", e)
                withContext(Dispatchers.EDT) {
                    onError("Ошибка: ${e.message}")
                }
            }
        }
    }

    /**
     * Отправляет сообщение через систему двух агентов (PlannerAgent + ExecutorAgent)
     * 
     * @param userMessage Текст сообщения пользователя
     * @param project Текущий проект
     * @param onStepComplete Callback для каждого шага выполнения
     * @param onError Callback для обработки ошибок
     */
    fun sendMessageWithOrchestrator(
        userMessage: String,
        project: Project,
        onStepComplete: (Message) -> Unit,
        onError: (String) -> Unit
    ) {
        if (userMessage.isBlank()) {
            onError("Сообщение не может быть пустым")
            return
        }

        logger.info("Sending user message to orchestrator, length: ${userMessage.length}")

        // Создаем и сохраняем сообщение пользователя
        val userMsg = Message(
            content = userMessage,
            role = MessageRole.USER
        )
        val history = getCurrentHistory()
        val wasEmpty = history.getMessageCount() == 0
        history.addMessage(userMsg)
        if (wasEmpty) {
            val title = deriveTitleFrom(userMessage)
            updateSessionTitle(currentSessionId, title)
        }

        // Обрабатываем запрос асинхронно
        scope.launch {
            try {
                // Создаем оркестратор
                val orchestrator = AgentFactory.createAgentOrchestrator()

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

                // Создаем запрос к оркестратору
                val agentRequest = AgentRequest(
                    request = userMessage,
                    context = context,
                    parameters = llmParameters
                )

                // Запускаем оркестратор с callback для каждого шага
                orchestrator.process(agentRequest) { step ->
                    withContext(Dispatchers.EDT) {
                        when (step) {
                            is OrchestratorStep.PlanningComplete -> {
                                if (step.success) {
                                    val message = Message(
                                        content = step.content,
                                        role = MessageRole.ASSISTANT,
                                        metadata = mapOf(
                                            "agentName" to step.agentName,
                                            "responseTimeMs" to step.responseTimeMs,
                                            "tokenUsage" to step.tokenUsage,
                                            "tokensUsed" to step.tokenUsage.totalTokens,
                                            "isFinal" to true,
                                            "uncertainty" to 0.0
                                        )
                                    )
                                    getCurrentHistory().addMessage(message)
                                    onStepComplete(message)
                                } else {
                                    onError(step.error ?: "Ошибка планирования")
                                }
                            }
                            is OrchestratorStep.TaskComplete -> {
                                val content = buildString {
                                    if (step.success) {
                                        appendLine("✅ **Задача ${step.taskId}: ${step.taskTitle}**")
                                        appendLine()
                                        appendLine(step.content)
                                    } else {
                                        appendLine("❌ **Задача ${step.taskId}: ${step.taskTitle}**")
                                        appendLine()
                                        appendLine("**Ошибка:** ${step.error}")
                                    }
                                }
                                val message = Message(
                                    content = content,
                                    role = MessageRole.ASSISTANT,
                                    metadata = mapOf(
                                        "agentName" to step.agentName,
                                        "taskId" to step.taskId,
                                        "taskTitle" to step.taskTitle,
                                        "success" to step.success,
                                        "responseTimeMs" to step.responseTimeMs,
                                        "tokenUsage" to step.tokenUsage,
                                        "tokensUsed" to step.tokenUsage.totalTokens,
                                        "isFinal" to true,
                                        "uncertainty" to 0.0
                                    )
                                )
                                getCurrentHistory().addMessage(message)
                                onStepComplete(message)
                            }
                            is OrchestratorStep.AllComplete -> {
                                val message = Message(
                                    content = step.content,
                                    role = MessageRole.ASSISTANT,
                                    metadata = mapOf(
                                        "totalTasks" to step.totalTasks,
                                        "successfulTasks" to step.successfulTasks,
                                        "responseTimeMs" to step.totalTimeMs,
                                        "tokenUsage" to step.totalTokenUsage,
                                        "tokensUsed" to step.totalTokenUsage.totalTokens,
                                        "isFinal" to true,
                                        "uncertainty" to 0.0
                                    )
                                )
                                getCurrentHistory().addMessage(message)
                                onStepComplete(message)
                            }
                            is OrchestratorStep.Error -> {
                                onError(step.error)
                            }
                        }
                    }
                }

                // Освобождаем ресурсы оркестратора
                orchestrator.dispose()

            } catch (e: Exception) {
                logger.error("Error processing message with orchestrator", e)
                withContext(Dispatchers.EDT) {
                    onError(e.message ?: "Неизвестная ошибка")
                }
            }
        }
    }

    /**
     * Выполняет терминальную команду через TerminalAgent
     * 
     * @param command Команда для выполнения
     * @param project Текущий проект
     * @param onResponse Callback для получения ответа
     * @param onError Callback для обработки ошибок
     */
    fun executeTerminalCommand(
        command: String,
        project: Project,
        onResponse: (Message) -> Unit,
        onError: (String) -> Unit
    ) {
        if (command.isBlank()) {
            onError("Команда не может быть пустой")
            return
        }

        logger.info("Executing terminal command: $command")

        scope.launch {
            try {
                // Создаем терминальный агент
                val terminalAgent = AgentFactory.createTerminalAgent()
                
                // Формируем контекст
                val context = ChatContext(
                    project = project,
                    history = getCurrentHistory().getMessages()
                )
                
                // Создаем запрос
                val request = AgentRequest(
                    request = command,
                    context = context,
                    parameters = LLMParameters.DEFAULT
                )
                
                // Выполняем команду
                val startTime = System.currentTimeMillis()
                val response = terminalAgent.ask(request)
                val responseTime = System.currentTimeMillis() - startTime
                
                withContext(Dispatchers.EDT) {
                    // Всегда показываем результат выполнения команды (даже при ошибке)
                    val metadata = response.metadata + mapOf<String, Any>(
                        "agentType" to "terminal",
                        "responseTimeMs" to responseTime,
                        "isFinal" to true,
                        "uncertainty" to 0.0,
                        "commandSuccess" to response.success,
                        "formattedOutput" to (response.formattedOutput ?: Unit)
                    )
                    
                    val message = Message(
                        content = response.content,
                        role = MessageRole.ASSISTANT,
                        metadata = metadata
                    )
                    
                    getCurrentHistory().addMessage(message)
                    onResponse(message)
                    
                    // Логируем ошибку, но не прерываем показ результата
                    if (!response.success) {
                        logger.warn("Terminal command failed: ${response.error}")
                    }
                }
                
                // Освобождаем ресурсы
                terminalAgent.dispose()
                
            } catch (e: Exception) {
                logger.error("Error executing terminal command", e)
                withContext(Dispatchers.EDT) {
                    onError("Ошибка: ${e.message}")
                }
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
        // Восстановим формат ответа через настройки, если был задан
        if (previousFormat != null) {
            val agentSettings = AgentSettings(
                defaultResponseFormat = previousFormat
            )
            agent.updateSettings(agentSettings)
            currentFormat = previousFormat
            currentSchema = previousSchema
        }
    }

    /**
     * Устанавливает формат ответа для текущего агента
     */
    fun setResponseFormat(format: ResponseFormat, schema: ResponseSchema?) {
        // Обновляем настройки агента
        val agentSettings = AgentSettings(
            defaultResponseFormat = format
        )
        agent.updateSettings(agentSettings)
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
        // Сбрасываем формат через настройки
        val agentSettings = AgentSettings(
            defaultResponseFormat = ResponseFormat.TEXT
        )
        agent.updateSettings(agentSettings)
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
    fun getResponseFormat(): ResponseFormat? = currentFormat

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
            val provider = chatAgent.getProvider()
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
    
    /**
     * Возвращает TokenCounter для подсчёта токенов
     */
    fun getTokenCounter(): ru.marslab.ide.ride.integration.llm.TokenCounter {
        return ru.marslab.ide.ride.integration.llm.impl.TiktokenCounter()
    }
}
