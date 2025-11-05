package ru.marslab.ide.ride.service

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.agent.LLMProviderFactory
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.agent.impl.EnhancedChatAgent
import ru.marslab.ide.ride.agent.impl.MCPFileSystemAgent
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceProvider
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTConfig
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTProvider
import ru.marslab.ide.ride.mcp.MCPServerManager
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentSettings
import ru.marslab.ide.ride.model.chat.*
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.llm.TokenUsage
import ru.marslab.ide.ride.model.schema.ResponseFormat
import ru.marslab.ide.ride.model.schema.ResponseSchema
import ru.marslab.ide.ride.orchestrator.StandaloneA2AOrchestrator
import ru.marslab.ide.ride.orchestrator.ToolAgentProgressListener
import ru.marslab.ide.ride.agent.tools.ProjectScannerToolAgent
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.service.storage.ChatStorageService
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.ui.chat.JcefChatView
import ru.marslab.ide.ride.util.TokenEstimator
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.agent.a2a.MessageBusProvider
import ru.marslab.ide.ride.testing.StubTestingAgentOrchestrator
import ru.marslab.ide.ride.testing.TestRunResult
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
    private var currentSessionId: String = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Временное хранилище текущего проекта для вызовов saveCurrentSession()
    private var currentProject: Project? = null

    // Текущие настройки формата ответа (для UI)
    private var currentFormat: ResponseFormat? = null
    private var currentSchema: ResponseSchema? = null
    // Активный план для фильтрации A2A событий в UI
    private var currentPlanId: String? = null

    // Агент создаётся и может быть пересоздан при смене настроек
    // EnhancedChatAgent автоматически определяет сложность задачи:
    // - Простые вопросы → ChatAgent (быстро)
    // - Сложные задачи → EnhancedAgentOrchestrator (многошаговое выполнение)
    private var agent: Agent = AgentFactory.createEnhancedChatAgent()

    // Chat view для отображения прогресса tool agents
    private var chatView: JcefChatView? = null

    // Callback для отправки сообщений в UI (устанавливается при отправке сообщения)
    private var currentResponseCallback: ((Message) -> Unit)? = null

    // Progress listener для tool agents
    private val toolAgentProgressListener = object : ToolAgentProgressListener {
        override fun onToolAgentStarted(message: ToolAgentStatusMessage) {
            displayToolAgentStatus(message)
        }
        override fun onToolAgentStatusUpdated(message: ToolAgentStatusMessage) {
            displayToolAgentStatus(message)
        }
        override fun onToolAgentCompleted(message: ToolAgentStatusMessage) {
            displayToolAgentStatus(message)
        }
        override fun onToolAgentFailed(message: ToolAgentStatusMessage, error: String) {
            displayToolAgentStatus(message)
        }
    }

    /**
     * Формирует человеко-понятное сообщение об ошибке из AgentResponse,
     * используя поля error/content/metadata, чтобы избежать пустого "Неизвестная ошибка".
     */
    private fun buildUserError(agentResponse: ru.marslab.ide.ride.model.agent.AgentResponse): String {
        val base = agentResponse.error?.takeIf { it.isNotBlank() }
        if (base != null) return base

        val meta = agentResponse.metadata
        val details = sequenceOf(
            meta["errorDetails"],
            meta["exception"],
            meta["status"],
            meta["reason"]
        ).mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
            .firstOrNull()

        val sourceHints = listOfNotNull(
            meta["agentType"]?.toString()?.let { "источник: $it" },
            meta["stepId"]?.toString()?.let { "шаг: $it" },
            meta["planId"]?.toString()?.let { "план: $it" }
        ).joinToString(", ")

        val contentSnippet = agentResponse.content.takeIf { it.isNotBlank() }?.let { it.take(140) }

        return buildString {
            append("Неизвестная ошибка")
            if (details != null) append(": ").append(details)
            if (sourceHints.isNotBlank()) append(" (" + sourceHints + ")")
            if (contentSnippet != null) append(". Детали: ").append(contentSnippet)
        }
    }

    /**
     * Подписка на A2A события и отображение прогресса в чате
     */
    private fun startA2AEventSubscription() {
        // Запускаем сбор событий в фоне
        scope.launch {
            try {
                MessageBusProvider.get().subscribeAll().collect { msg ->
                    when (msg) {
                        is AgentMessage.Event -> handleA2AUiEvent(msg)
                        else -> { /* ignore */ }
                    }
                }
            } catch (e: Exception) {
                logger.warn("ChatService: A2A event subscription error: ${e.message}", e)
            }
        }
    }

    private fun handleA2AUiEvent(event: AgentMessage.Event) {
        // Определяем planId из metadata/payload
        val metaPlanId = event.metadata["planId"] as? String
        val payloadRequestId = (event.payload as? MessagePayload.ExecutionStatusPayload)?.requestId
        val planIdForEvent = metaPlanId ?: payloadRequestId

        // Захватываем текущий planId на старте оркестрации/плана
        if (currentPlanId == null && (event.eventType == "ORCHESTRATION_STARTED" || event.eventType == "PLAN_EXECUTION_STARTED")) {
            currentPlanId = planIdForEvent
        }

        // Фильтрация событий других планов
        if (currentPlanId != null && planIdForEvent != null && planIdForEvent != currentPlanId) {
            return
        }

        // Создаем системное сообщение для отображения в чате
        val systemMessage = createSystemMessageForEvent(event)

        // Создаем детальную информацию для раскрытия по клику
        val detailedInfo = createDetailedInfoForEvent(event)

        val message = Message(
            content = systemMessage,
            role = MessageRole.SYSTEM,
            metadata = mapOf(
                "type" to "a2a_system_event",
                "eventType" to event.eventType,
                "senderId" to event.senderId,
                "detailedInfo" to detailedInfo,
                "agentType" to extractAgentType(event.senderId),
                "stage" to extractStageFromEvent(event.eventType)
            )
        )
        sendProgressMessageToUI(message)

        // Сброс план-контекста при завершении
        if (event.eventType == "PLAN_EXECUTION_COMPLETED" || event.eventType == "PLAN_EXECUTION_FAILED" ||
            event.eventType == "ORCHESTRATION_COMPLETED" || event.eventType == "ORCHESTRATION_FAILED") {
            currentPlanId = null
        }
    }

    /**
     * Создает короткое системное сообщение для отображения в чате
     */
    private fun createSystemMessageForEvent(event: AgentMessage.Event): String {
        val agentType = extractAgentType(event.senderId)
        val stage = extractStageFromEvent(event.eventType)

        return when (event.eventType) {
            "TOOL_EXECUTION_STARTED" -> "🔄 $agentType: начало работы"
            "TOOL_EXECUTION_COMPLETED" -> "✅ $agentType: выполнено"
            "TOOL_EXECUTION_FAILED" -> "❌ $agentType: ошибка"
            "STEP_STARTED" -> "🔄 $stage"
            "STEP_COMPLETED" -> "✅ $stage"
            "STEP_FAILED" -> "❌ $stage"
            "ORCHESTRATION_STARTED" -> "🚀 Запуск многошагового выполнения"
            "PLAN_EXECUTION_STARTED" -> "📋 Начало выполнения плана"
            "PLAN_EXECUTION_COMPLETED" -> "🎉 План успешно выполнен"
            "PLAN_EXECUTION_FAILED" -> "⚠️ Выполнение плана прервано"
            "ORCHESTRATION_COMPLETED" -> "🏁 Все задачи завершены"
            else -> "ℹ️ $agentType: $stage"
        }
    }

    /**
     * Создает детальную информацию для отображения при клике на системное сообщение
     */
    private fun createDetailedInfoForEvent(event: AgentMessage.Event): String {
        return when (val payload = event.payload) {
            is MessagePayload.ExecutionStatusPayload -> {
                val status = payload.status
                val agent = payload.agentId ?: event.senderId
                val result = payload.result
                val error = payload.error
                val planId = event.metadata["planId"] ?: payload.requestId ?: ""
                buildString {
                    appendLine("## Детальная информация A2A события")
                    appendLine("**Тип события:** ${event.eventType}")
                    appendLine("**Агент:** $agent")
                    appendLine("**Статус:** $status")
                    if (planId.toString().isNotBlank()) appendLine("**ID плана:** $planId")
                    if (!result.isNullOrBlank()) appendLine("**Результат:** $result")
                    if (!error.isNullOrBlank()) appendLine("**Ошибка:** $error")
                    appendLine("**Время:** ${java.time.Instant.now()}")
                }
            }
            is MessagePayload.ProgressPayload -> {
                val planId = event.metadata["planId"] ?: ""
                val attempt = event.metadata["attempt"]
                val attempts = event.metadata["attempts"]
                buildString {
                    appendLine("## Детальная информация о прогрессе")
                    appendLine("**Тип события:** ${event.eventType}")
                    appendLine("**Шаг:** ${payload.stepId}")
                    appendLine("**Статус:** ${payload.status}")
                    appendLine("**Прогресс:** ${payload.progress}%")
                    if (planId.toString().isNotBlank()) appendLine("**ID плана:** $planId")
                    if (attempt != null) appendLine("**Попытка:** $attempt")
                    if (attempts != null) appendLine("**Всего попыток:** $attempts")
                    if (!payload.message.isNullOrBlank()) appendLine("**Сообщение:** ${payload.message}")
                    appendLine("**Время:** ${java.time.Instant.now()}")
                }
            }
            is MessagePayload.CustomPayload -> {
                val type = payload.type
                val data = payload.data
                buildString {
                    appendLine("## Детальная информация о событии")
                    appendLine("**Тип события:** ${event.eventType}")
                    appendLine("**Тип данных:** $type")
                    appendLine("**Отправитель:** ${event.senderId}")
                    if (data.isNotEmpty()) {
                        appendLine("**Данные:**")
                        appendLine("```json")
                        appendLine(simpleJsonString(data))
                        appendLine("```")
                    }
                    appendLine("**Время:** ${java.time.Instant.now()}")
                }
            }
            else -> {
                buildString {
                    appendLine("## Информация о событии")
                    appendLine("**Тип:** ${event.eventType}")
                    appendLine("**Payload:** ${payload::class.simpleName}")
                    appendLine("**Отправитель:** ${event.senderId}")
                    appendLine("**Время:** ${java.time.Instant.now()}")
                }
            }
        }
    }

    /**
     * Извлекает тип агента из ID отправителя
     */
    private fun extractAgentType(senderId: String): String {
        return when {
            senderId.contains("code-generator") -> "Генератор кода"
            senderId.contains("project-scanner") -> "Сканер проекта"
            senderId.contains("llm-review") -> "LLM ревьюер"
            senderId.contains("bug-detection") -> "Детектор багов"
            senderId.contains("architecture") -> "Анализатор архитектуры"
            senderId.contains("quality") -> "Анализатор качества"
            senderId.contains("report") -> "Генератор отчетов"
            senderId.contains("file-operations") -> "Операции с файлами"
            senderId.contains("embedding") -> "Индексатор"
            senderId.contains("documentation") -> "Генератор документации"
            senderId.contains("orchestrator") -> "Оркестратор"
            else -> "Агент ${senderId.take(8)}..."
        }
    }

    /**
     * Извлекает стадию работы из типа события
     */
    private fun extractStageFromEvent(eventType: String): String {
        return when (eventType) {
            "TOOL_EXECUTION_STARTED" -> "Запуск"
            "TOOL_EXECUTION_COMPLETED" -> "Завершение"
            "TOOL_EXECUTION_FAILED" -> "Ошибка"
            "STEP_STARTED" -> "Выполнение шага"
            "STEP_COMPLETED" -> "Шаг завершен"
            "STEP_FAILED" -> "Шаг завершился с ошибкой"
            "ORCHESTRATION_STARTED" -> "Начало оркестрации"
            "PLAN_EXECUTION_STARTED" -> "Начало выполнения плана"
            "PLAN_EXECUTION_COMPLETED" -> "План выполнен"
            "PLAN_EXECUTION_FAILED" -> "План не выполнен"
            "ORCHESTRATION_COMPLETED" -> "Оркестрация завершена"
            else -> "Обработка"
        }
    }

    // Список активных progress сообщений для обновления
    private val activeProgressMessages = mutableMapOf<String, Message>()

    /**
     * Запускает ProjectScannerToolAgent по команде "/files".
     * Поддерживаемые параметры: page, batch_size, since_ts (напр.: "page=2 batch_size=200 since_ts=1698000000000").
     */
    fun sendFilesCommand(
        userMessage: String,
        project: Project,
        onResponse: (Message) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val params = parseKeyValueArgs(userMessage)
                val page = params["page"]?.toIntOrNull()
                val batchSize = params["batch_size"]?.toIntOrNull()
                val sinceTs = params["since_ts"]?.toLongOrNull()
                val forceRescan = params["force_rescan"]?.equals("true", ignoreCase = true) ?: false
                val projectPathOverride = params["project_path"]

                val agent = ProjectScannerToolAgent()
                val inputMap = buildMap<String, Any> {
                    page?.let { put("page", it) }
                    batchSize?.let { put("batch_size", it) }
                    sinceTs?.let { put("since_ts", it) }
                    if (forceRescan) put("force_rescan", true)
                }

                val step = ToolPlanStep(
                    description = "List project files (JSON batch)",
                    agentType = AgentType.PROJECT_SCANNER,
                    input = StepInput(
                        if (projectPathOverride != null) inputMap + mapOf("project_path" to projectPathOverride) else inputMap
                    )
                )
                val ctx = ExecutionContext(projectPath = projectPathOverride ?: (project.basePath ?: "."))

                val result = agent.executeStep(step, ctx)

                withContext(Dispatchers.EDT) {
                    if (result.success) {
                        val output = result.output.data
                        val jsonPart = (output["json"]) ?: output
                        val content = buildString {
                            append("```json\n")
                            append(simpleJsonString(jsonPart))
                            append("\n```")
                        }
                        onResponse(
                            Message(
                                content = content,
                                role = MessageRole.ASSISTANT,
                                metadata = mapOf(
                                    "command" to "/files",
                                    "format" to (output["format"] ?: "JSON"),
                                    "page" to (output["json"]?.let { (it as? Map<*, *>)?.get("batch") } ?: inputMap)
                                )
                            )
                        )
                    } else {
                        onError(result.error ?: "Ошибка выполнения ProjectScannerToolAgent")
                    }
                }
            } catch (e: Exception) {
                logger.error("Error executing /files command", e)
                withContext(Dispatchers.EDT) { onError(e.message ?: "Неизвестная ошибка") }
            }
        }
    }

    private fun parseKeyValueArgs(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        return text.split(" ", "\n", "\t").mapNotNull { token ->
            val idx = token.indexOf('=')
            if (idx > 0 && idx < token.length - 1) token.substring(0, idx) to token.substring(idx + 1) else null
        }.toMap()
    }

    private fun simpleJsonString(any: Any?): String {
        return when (any) {
            null -> "null"
            is String -> "\"" + any.replace("\"", "\\\"") + "\""
            is Number, is Boolean -> any.toString()
            is Map<*, *> -> any.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                val key = k?.toString() ?: "null"
                "\"$key\": ${simpleJsonString(v)}"
            }
            is Iterable<*> -> any.joinToString(prefix = "[", postfix = "]") { v -> simpleJsonString(v) }
            is Array<*> -> any.joinToString(prefix = "[", postfix = "]") { v -> simpleJsonString(v) }
            else -> "\"" + any.toString().replace("\"", "\\\"") + "\""
        }
    }

    /**
     * Инициализирует сервис с проектом (синхронно)
     */
    fun initializeWithProject(project: Project) {
        currentProject = project
        try {
            // Синхронная загрузка через runBlocking
            kotlinx.coroutines.runBlocking {
                val storage = service<ChatStorageService>()
                val (loadedSessions, histories) = storage.loadAllSessions(project)

                // Инициализируем состояния
                sessions.clear()
                if (loadedSessions.isNotEmpty()) {
                    sessions.addAll(loadedSessions)
                    // Восстанавливаем истории
                    histories.forEach { (sid, msgs) ->
                        val mh = MessageHistory()
                        msgs.forEach { mh.addMessage(it) }
                        sessionHistories[sid] = mh
                    }
                    currentSessionId = loadedSessions.first().id
                    logger.info("ChatService: sessions loaded for project ${project.name}: ${sessions.size}")
                } else {
                    // Нет сохранённых сессий — создаём новую
                    currentSessionId = createNewSessionInternal().id
                    logger.info("ChatService: created new session for project ${project.name}")
                }
            }
        } catch (e: Exception) {
            logger.warn("ChatService: failed to load sessions for project ${project.name}, fallback to in-memory", e)
            if (currentSessionId.isBlank()) {
                currentSessionId = createNewSessionInternal().id
            }
        }
    }


    /**
     * Устанавливает chat view для отображения progress сообщений
     */
    fun setChatView(view: JcefChatView) {
        this.chatView = view
        // Добавляем listener к orchestrator если он есть
        setupProgressListener()
        // Подписка на A2A события для отображения прогресса
        startA2AEventSubscription()
    }

    /**
     * Открывает DevTools JCEF, если доступен JcefChatView
     */
    fun openDevToolsIfAvailable() {
        val view = chatView
        if (view == null) {
            logger.warn("ChatService: JCEF view is not available; cannot open DevTools")
            return
        }
        runCatching { view.openDevTools() }
            .onFailure { logger.warn("ChatService: failed to open DevTools", it) }
    }

    fun isDevToolsAvailable(): Boolean = chatView != null

    /**
     * Настраивает listener для tool agent progress
     */
    private fun setupProgressListener() {
        // Ищем EnhancedAgentOrchestrator в агенте
        val orchestrator = findEnhancedAgentOrchestrator()
        // StandaloneA2AOrchestrator не требует addProgressListener - использует A2A события
        // if (orchestrator != null) {
        //     orchestrator.addProgressListener(toolAgentProgressListener)
        // }
    }

    /**
     * Ищет EnhancedAgentOrchestratorA2A в текущем агенте
     */
    private fun findEnhancedAgentOrchestrator(): Any? {
        val currentAgent = agent
        try {
            when (currentAgent) {
//                is EnhancedAgentOrchestrator -> return currentAgent
                is EnhancedChatAgent -> {
                    // Пытаемся получить orchestrator из EnhancedChatAgent
                    val field = currentAgent::class.java.getDeclaredField("orchestrator")
                    field.isAccessible = true
                    val orchestrator = field.get(currentAgent)
                    if (orchestrator is StandaloneA2AOrchestrator) {
                        // StandaloneA2AOrchestrator не имеет legacy зависимостей
                        // Возвращаем null поскольку нет базового оркестратора
                        logger.info("Using StandaloneA2AOrchestrator - no legacy orchestrator available")
                        return null
                    }
                }

                else -> {
                    // Другие типы агентов не поддерживают progress tracking
                }
            }
        } catch (e: Exception) {
            logger.warn("Could not access orchestrator from agent", e)
        }
        return null
    }

    /**
     * Отображает статус tool agent в чате
     */
    private fun displayToolAgentStatus(message: ToolAgentStatusMessage) {
        chatView?.let { view ->
            try {
                // Выполняем в UI потоке
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    view.addOrUpdateToolAgentStatus(message.toHtml(), message.id)
                }
            } catch (e: Exception) {
                logger.error("Error displaying tool agent status", e)
            }
        }

        // Создаем сообщение в истории для всех обновлений статусов
        createProgressMessage(message)
    }

    /**
     * Отправляет сообщение напрямую в UI и историю
     */
    private fun sendProgressMessageToUI(message: Message) {
        try {
            // Добавляем в историю
            getCurrentHistory().addMessage(message)

            // Отправляем в UI если есть callback
            currentResponseCallback?.invoke(message)

            logger.info("Progress message sent to UI: ${message.content.take(50)}...")
        } catch (e: Exception) {
            logger.error("Error sending progress message to UI", e)
        }
    }

    /**
     * Создает сообщение о прогрессе в истории чата
     */
    private fun createProgressMessage(message: ToolAgentStatusMessage): Message {
        return try {
            val content = when (message.result.status) {
                ToolAgentExecutionStatus.RUNNING -> {
                    message.toHtml() // Используем HTML для progress сообщений
                }

                ToolAgentExecutionStatus.COMPLETED -> {
                    message.toHtml() // Используем HTML для завершенных сообщений с результатами
                }

                ToolAgentExecutionStatus.FAILED -> {
                    message.toHtml() // Используем HTML для сообщений об ошибках
                }

                ToolAgentExecutionStatus.CANCELLED -> {
                    "**${message.result.agentName} отменен**"
                }

                else -> {
                    "**${message.displayMessage}**"
                }
            }

            val progressMessage = Message(
                content = content,
                role = MessageRole.ASSISTANT, // Используем ASSISTANT для отображения в чате
                metadata = mapOf(
                    "type" to "tool_agent_progress",
                    "agentType" to message.result.agentType,
                    "agentName" to message.result.agentName,
                    "stepId" to message.result.stepId,
                    "executionTime" to (message.result.executionTimeMs ?: 0),
                    "status" to message.result.status.name,
                    "isProgress" to true,
                    "messageId" to message.id
                )
            )

            // Сохраняем сообщение для возможности обновления
            activeProgressMessages[message.result.stepId] = progressMessage

            logger.info("Tool agent ${message.result.status.name}: ${message.result.agentName}")
            progressMessage
        } catch (e: Exception) {
            logger.error("Error creating progress message", e)
            Message(
                content = "Ошибка отображения прогресса: ${e.message}",
                role = MessageRole.SYSTEM
            )
        }
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

        // Сохраняем callback для progress сообщений
        currentResponseCallback = onResponse

        // Создаем и сохраняем сообщение пользователя
        val userMsg = Message(
            content = userMessage,
            role = MessageRole.USER
        )
        val history = getCurrentHistory()
        val wasEmpty = history.getMessageCount() == 0
        history.addMessage(userMsg)
        // Автосохранение после пользовательского сообщения
        scope.launch { saveCurrentSession() }
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

                // Обработка команды запуска тестов: "/test @path/to/File.kt"
                val trimmed = userMessage.trim()
                if (trimmed.startsWith("/test")) {
                    // Ищем первый токен, начинающийся с '@'
                    val atToken = trimmed.split(" ", "\n", "\t")
                        .firstOrNull { it.startsWith("@") && it.length > 1 }

                    if (atToken == null) {
                        withContext(Dispatchers.EDT) {
                            onError("Не указан файл. Используйте: /test @relative/path/ToFile.kt")
                        }
                        return@launch
                    }

                    val relativePath = atToken.removePrefix("@")

                    // Временный оркестратор (заглушка)
                    val orchestrator = StubTestingAgentOrchestrator()
                    val result: TestRunResult = runCatching { orchestrator.generateAndRun(relativePath) }
                        .getOrElse { ex ->
                            withContext(Dispatchers.EDT) {
                                onError("Ошибка запуска тестов: ${ex.message}")
                            }
                            return@launch
                        }

                    withContext(Dispatchers.EDT) {
                        val content = buildString {
                            appendLine("### 🧪 Результаты тестов (stub)")
                            appendLine()
                            appendLine("Файл: `" + relativePath + "`")
                            appendLine()
                            appendLine("- Успех: ${result.success}")
                            appendLine("- Passed: ${result.passed}")
                            appendLine("- Failed: ${result.failed}")
                            appendLine("- Skipped: ${result.skipped}")
                            appendLine("- Duration: ${result.durationMs} ms")
                            appendLine()
                            appendLine("```")
                            appendLine(result.reportText)
                            appendLine("```")
                        }
                        val assistantMsg = Message(
                            content = content,
                            role = MessageRole.ASSISTANT,
                            metadata = mapOf("command" to "/test", "file" to relativePath)
                        )
                        getCurrentHistory().addMessage(assistantMsg)
                        onResponse(assistantMsg)
                        scope.launch { saveCurrentSession() }
                    }

                    // Не продолжаем обычную обработку LLM для этой команды
                    return@launch
                }

                // Получаем параметры из настроек
                val settings = service<PluginSettings>()
                val llmParameters = LLMParameters(
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens
                )

                // БЕЗ прямого RAG обогащения - простые запросы обрабатываются напрямую
                val enrichedRequest = userMessage

                // Формируем контекст
                val context = ChatContext(
                    project = project,
                    history = getCurrentHistory().getMessages()
                )

                // Создаем запрос к агенту
                val agentRequest = AgentRequest(
                    request = enrichedRequest,
                    context = context,
                    parameters = llmParameters
                )

                // RAG метаданные отключены
                val ragMetadata = emptyMap<String, Any>()

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
                        val baseMetadata = mapOf(
                            "isFinal" to agentResponse.isFinal,
                            "uncertainty" to (agentResponse.uncertainty ?: 0.0),
                            "responseTimeMs" to responseTime,
                            "tokensUsed" to tokensUsed,
                            "tokenUsage" to (tokenUsage ?: TokenUsage.EMPTY),
                            "formattedOutput" to (agentResponse.formattedOutput ?: Unit)
                        )

                        // Добавляем RAG метаданные если было обогащение
                        val metadata = agentResponse.metadata + baseMetadata + ragMetadata

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
                        // Автосохранение сессии
                        scope.launch { saveCurrentSession() }
                    } else {
                        // Расширенное логирование причины ошибки
                        val meta = agentResponse.metadata
                        val errText = buildUserError(agentResponse)
                        logger.warn(
                            "Agent returned error. text='${agentResponse.error}', content='${agentResponse.content.take(200)}', " +
                                "metadataKeys=${meta.keys}, agentType=${meta["agentType"]}, stepId=${meta["stepId"]}, planId=${meta["planId"]}"
                        )
                        onError(errText)
                    }
                }

            } catch (e: Exception) {
                logger.error("Error processing message", e)
            } finally {
                // Очищаем callback после завершения
                currentResponseCallback = null
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
            println("🔧 ChatService: Trying to start MCP Server...")
            val started = serverManager.ensureServerRunning()
            println("🔧 ChatService: MCP Server start result: $started")

            if (!started && !serverManager.isServerRunning()) {
                logger.warn("MCP Server not available - file operations may be limited")
                // Продолжаем работу без MCP сервера
            }
        }

        // Сохраняем callback для progress сообщений
        currentResponseCallback = onResponse

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
                val projectPath = project.basePath
                val mcpFileSystemAgent = MCPFileSystemAgent(config, projectPath = projectPath)

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
                        val meta = agentResponse.metadata
                        val errText = buildUserError(agentResponse)
                        logger.warn(
                            "Agent returned error (tools path). text='${agentResponse.error}', content='${agentResponse.content.take(200)}', " +
                                "metadataKeys=${meta.keys}, agentType=${meta["agentType"]}, stepId=${meta["stepId"]}, planId=${meta["planId"]}"
                        )
                        onError(errText)
                    }
                }

            } catch (e: Exception) {
                logger.error("Error processing message with tools", e)
                withContext(Dispatchers.EDT) {
                    onError("Ошибка: ${e.message}")
                }
            } finally {
                // Очищаем callback после завершения
                currentResponseCallback = null
            }
        }
    }

    /**
     * Отправляет сообщение через EnhancedAgentOrchestrator с поддержкой progress отображения
     *
     * @param userMessage Сообщение пользователя
     * @param project Текущий проект
     * @param onStepComplete Callback для завершения каждого шага
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

        logger.info("Sending user message to enhanced orchestrator, length: ${userMessage.length}")

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

        // Устанавливаем callback для progress сообщений
        currentResponseCallback = onStepComplete

        // Обрабатываем запрос асинхронно
        scope.launch {
            try {
                // Создаем StandaloneA2AOrchestrator без зависимостей от легаси
                val llmProvider = LLMProviderFactory.createLLMProvider()
                val enhancedOrchestrator = StandaloneA2AOrchestrator()

                // Инициализируем A2A агентов
                try {
                    kotlinx.coroutines.runBlocking {
                        enhancedOrchestrator.registerAllAgents(llmProvider)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to initialize StandaloneA2AOrchestrator: ${e.message}", e)
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

                // Создаем запрос к оркестратору
                val agentRequest = AgentRequest(
                    request = userMessage,
                    context = context,
                    parameters = llmParameters
                )

                // Добавляем progress listener для отображения статуса в реальном времени
                val progressListener = object : ToolAgentProgressListener {
                    override fun onToolAgentStarted(message: ToolAgentStatusMessage) {
                        logger.info("Tool agent started: ${message.result.agentName}")
                        val progressMessage = createProgressMessage(message)
                        sendProgressMessageToUI(progressMessage)
                    }

                    override fun onToolAgentStatusUpdated(message: ToolAgentStatusMessage) {
                        logger.info("Tool agent status updated: ${message.result.agentName} -> ${message.result.status}")
                        val progressMessage = createProgressMessage(message)
                        sendProgressMessageToUI(progressMessage)
                    }

                    override fun onToolAgentCompleted(message: ToolAgentStatusMessage) {
                        logger.info("Tool agent completed: ${message.result.agentName}")
                        val progressMessage = createProgressMessage(message)
                        sendProgressMessageToUI(progressMessage)
                    }

                    override fun onToolAgentFailed(message: ToolAgentStatusMessage, error: String) {
                        logger.error("Tool agent failed: ${message.result.agentName} - $error")
                        val errorMessage = createProgressMessage(message)
                        sendProgressMessageToUI(errorMessage)
                    }
                }

                // StandaloneA2AOrchestrator не использует addProgressListener - события приходят через A2A шину
                // enhancedOrchestrator.addProgressListener(progressListener)

                // Запускаем StandaloneA2AOrchestrator
                val result = enhancedOrchestrator.processRequest(agentRequest) { stepResult ->
                    // Отправляем прогресс в UI
                    withContext(Dispatchers.EDT) {
                        onStepComplete(
                            ru.marslab.ide.ride.model.chat.Message(
                                role = ru.marslab.ide.ride.model.chat.MessageRole.ASSISTANT,
                                content = "🔄 ${stepResult.stepTitle}: ${if (stepResult.success) "✅" else "❌"}",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }

                withContext(Dispatchers.EDT) {
                    if (result.success) {
                        val finalMessage = Message(
                            content = result.content,
                            role = MessageRole.ASSISTANT,
                            metadata = result.metadata + mapOf(
                                "isFinal" to result.isFinal,
                                "uncertainty" to (result.uncertainty ?: 0.0)
                            )
                        )
                        getCurrentHistory().addMessage(finalMessage)
                        onStepComplete(finalMessage)
                    } else {
                        onError(result.error ?: "Ошибка выполнения плана")
                    }
                }

                // StandaloneA2AOrchestrator не использует removeProgressListener
                // baseOrchestrator.removeProgressListener(progressListener)
                currentResponseCallback = null

            } catch (e: Exception) {
                logger.error("Error processing message with enhanced orchestrator", e)
                currentResponseCallback = null
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
                    // Автосохранение сессии
                    scope.launch { saveCurrentSession() }

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
        // Автосохранение пустой истории
        scope.launch { saveCurrentSession() }
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
        agent = AgentFactory.createEnhancedChatAgent()

        // Заново настраиваем progress listener
        setupProgressListener()

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
        // Проверяем EnhancedChatAgent
        val enhancedAgent = agent as? EnhancedChatAgent
        if (enhancedAgent != null) {
            val provider = enhancedAgent.getProvider()
            return when (provider) {
                is HuggingFaceProvider -> provider.getModelDisplayName()
                is YandexGPTProvider -> "${provider.getProviderName()} (${provider.getModelDisplayName()})"
                else -> provider.getProviderName()
            }
        }

        // Проверяем обычный ChatAgent
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
        // Сохраняем новую сессию в индекс
        scope.launch { saveCurrentSession() }
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
            // Сохранить переименованную сессию
            if (sessionId == currentSessionId) {
                scope.launch { saveCurrentSession() }
            }
        }
    }

    /**
     * Удаляет или скрывает сессию из UI
     * @param deleteFromStorage true — полностью удалить и из диска; false — только убрать из UI (оставить файлы)
     */
    fun removeSession(sessionId: String, deleteFromStorage: Boolean) {
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx < 0) return
        sessions.removeAt(idx)
        sessionHistories.remove(sessionId)

        scope.launch {
            try {
                if (deleteFromStorage) {
                    currentProject?.let {
                        service<ChatStorageService>().deleteSession(
                            it,
                            sessionId,
                            withBackup = true
                        )
                    }
                }
            } catch (e: Exception) {
                logger.warn("Failed to ${if (deleteFromStorage) "delete" else "hide"} session $sessionId", e)
            }
        }

        // Переключаемся на первую доступную или создаем новую
        currentSessionId = sessions.firstOrNull()?.id ?: createNewSessionInternal().id
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
     * Сохраняет текущую сессию и её сообщения в персистентное хранилище
     */
    private suspend fun saveCurrentSession() {
        currentProject?.let { saveCurrentSession(it) }
    }

    private suspend fun saveCurrentSession(project: Project) {
        try {
            if (currentSessionId.isBlank()) return
            val session = sessions.firstOrNull { it.id == currentSessionId } ?: return
            val messages = getCurrentHistory().getMessages()
            service<ChatStorageService>().saveSession(project, session, messages)
        } catch (e: Exception) {
            logger.warn("Failed to save current session", e)
        }
    }

    /**
     * Возвращает TokenCounter для подсчёта токенов
     */
    fun getTokenCounter(): ru.marslab.ide.ride.integration.llm.TokenCounter {
        return ru.marslab.ide.ride.integration.llm.impl.TiktokenCounter()
    }
}
