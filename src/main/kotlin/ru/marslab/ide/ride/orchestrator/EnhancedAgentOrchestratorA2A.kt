package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.agent.tools.A2AProjectScannerToolAgent
import ru.marslab.ide.ride.agent.tools.A2ABugDetectionToolAgent
import ru.marslab.ide.ride.agent.tools.A2ACodeQualityToolAgent
import ru.marslab.ide.ride.agent.tools.A2AReportGeneratorToolAgent
import ru.marslab.ide.ride.agent.tools.A2AArchitectureToolAgent
import ru.marslab.ide.ride.agent.tools.A2ALLMReviewToolAgent
import ru.marslab.ide.ride.agent.tools.A2AEmbeddingIndexerToolAgent
import ru.marslab.ide.ride.agent.tools.A2ACodeChunkerToolAgent
import ru.marslab.ide.ride.agent.tools.A2AOpenSourceFileToolAgent
import ru.marslab.ide.ride.agent.tools.A2AUserInteractionAgent
import ru.marslab.ide.ride.agent.tools.A2ACodeGeneratorToolAgent
import ru.marslab.ide.ride.agent.tools.UserInteractionAgent
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.AgentOrchestrator
import ru.marslab.ide.ride.agent.OrchestratorStep
import ru.marslab.ide.ride.agent.ToolAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.ToolPlanStep

/**
 * Расширение EnhancedAgentOrchestrator с поддержкой A2A коммуникации
 *
 * Добавляет возможность работы с A2A агентами, MessageBus коммуникацией
 * и автоматической адаптацией legacy агентов для A2A режима.
 */
class EnhancedAgentOrchestratorA2A(
    private val baseOrchestrator: EnhancedAgentOrchestrator,
    private val messageBus: MessageBus = MessageBusProvider.get(),
    private val a2aRegistry: A2AAgentRegistry = A2AAgentRegistry.getInstance(),
    private val a2aConfig: A2AConfig = A2AConfig.getInstance()
) {

    private val logger = Logger.getInstance(EnhancedAgentOrchestratorA2A::class.java)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // A2A кэши и состояния
    private val a2aAgents = mutableMapOf<String, A2AAgent>()
    private val activeExecutions = mutableMapOf<String, A2AExecutionContext>()

    init {
        // Подписываемся на A2A события
        subscribeToA2AEvents()
    }

    /**
     * Регистрирует базовых A2A-агентов в реестре на общей шине MessageBus
     */
    suspend fun registerCoreAgents(llmProvider: LLMProvider) {
        // Независимые A2A агенты без legacy зависимостей
        val scanner = A2AProjectScannerToolAgent()
        val architectureAgent = A2AArchitectureToolAgent() // Независимый агент
        val llmReviewAgent = A2ALLMReviewToolAgent(llmProvider) // Независимый агент
        val embeddingIndexerAgent = A2AEmbeddingIndexerToolAgent() // Независимый агент
        val codeChunkerAgent = A2ACodeChunkerToolAgent() // Независимый агент
        val fileOperationsAgent = A2AOpenSourceFileToolAgent() // Независимый агент

        // Агенты, требующие LLM провайдера
        val bugDetection = A2ABugDetectionToolAgent(llmProvider)
        val reportGenerator = A2AReportGeneratorToolAgent(llmProvider)
        val codeGenerator = A2ACodeGeneratorToolAgent(llmProvider)

        // Агенты, которые еще не переписаны (оставляем как есть временно)
        val codeQuality = A2ACodeQualityToolAgent()
        val userInteractionAgent = A2AUserInteractionAgent()

        // Регистрируем всех агентов
        a2aRegistry.registerAgent(scanner)
        a2aRegistry.registerAgent(architectureAgent)
        a2aRegistry.registerAgent(llmReviewAgent)
        a2aRegistry.registerAgent(embeddingIndexerAgent)
        a2aRegistry.registerAgent(codeChunkerAgent)
        a2aRegistry.registerAgent(fileOperationsAgent)
        a2aRegistry.registerAgent(bugDetection)
        a2aRegistry.registerAgent(reportGenerator)
        a2aRegistry.registerAgent(codeGenerator)
        a2aRegistry.registerAgent(codeQuality)
        a2aRegistry.registerAgent(userInteractionAgent)

        logger.info("Core A2A agents registered: ${listOf(
            scanner.a2aAgentId,
            architectureAgent.a2aAgentId,
            llmReviewAgent.a2aAgentId,
            embeddingIndexerAgent.a2aAgentId,
            codeChunkerAgent.a2aAgentId,
            fileOperationsAgent.a2aAgentId
        )}")
    }

    /**
     * Регистрирует базовые (без LLM) A2A-агенты: сканер, архитектура, чанкинг, файловые операции
     */
    suspend fun registerCoreAgentsBasic() {
        // Независимые A2A агенты без LLM зависимостей
        val scanner = A2AProjectScannerToolAgent()
        val architectureAgent = A2AArchitectureToolAgent() // Независимый агент
        val embeddingIndexerAgent = A2AEmbeddingIndexerToolAgent() // Независимый агент
        val codeChunkerAgent = A2ACodeChunkerToolAgent() // Независимый агент
        val fileOperationsAgent = A2AOpenSourceFileToolAgent() // Независимый агент

        // Временные агенты с legacy зависимостями (до переписывания)
        val codeQuality = A2ACodeQualityToolAgent()
        val userInteractionAgent = A2AUserInteractionAgent()

        // Регистрируем всех агентов
        a2aRegistry.registerAgent(scanner)
        a2aRegistry.registerAgent(architectureAgent)
        a2aRegistry.registerAgent(embeddingIndexerAgent)
        a2aRegistry.registerAgent(codeChunkerAgent)
        a2aRegistry.registerAgent(fileOperationsAgent)
        a2aRegistry.registerAgent(codeQuality)
        a2aRegistry.registerAgent(userInteractionAgent)

        logger.info("Basic A2A agents registered: ${listOf(
            scanner.a2aAgentId,
            architectureAgent.a2aAgentId,
            embeddingIndexerAgent.a2aAgentId,
            codeChunkerAgent.a2aAgentId,
            fileOperationsAgent.a2aAgentId
        )}")
    }

    /**
     * Регистрирует A2A-агентов, требующих LLM-провайдера
     */
    suspend fun registerLLMBasedAgents(llmProvider: LLMProvider) {
        // Независимые A2A агенты с LLM зависимостями
        val llmReviewAgent = A2ALLMReviewToolAgent(llmProvider) // Независимый агент

        // Агенты, требующие LLM провайдера
        val bugDetection = A2ABugDetectionToolAgent(llmProvider)
        val reportGenerator = A2AReportGeneratorToolAgent(llmProvider)
        val codeGenerator = A2ACodeGeneratorToolAgent(llmProvider)

        // Регистрируем всех агентов
        a2aRegistry.registerAgent(llmReviewAgent)
        a2aRegistry.registerAgent(bugDetection)
        a2aRegistry.registerAgent(reportGenerator)
        a2aRegistry.registerAgent(codeGenerator)

        logger.info("LLM-based A2A agents registered: ${listOf(
            llmReviewAgent.a2aAgentId,
            bugDetection.a2aAgentId,
            reportGenerator.a2aAgentId,
            codeGenerator.a2aAgentId
        )}")
    }

    /**
     * Обрабатывает запрос с поддержкой A2A режима
     *
     * @param request Входящий запрос
     * @param context Контекст выполнения
     * @param onStepComplete Callback для завершения шагов
     * @return AgentResponse с результатом
     */
    suspend fun processWithA2A(
        request: AgentRequest,
        context: ExecutionContext = ExecutionContext.Empty,
        onStepComplete: suspend (OrchestratorStep) -> Unit = {}
    ): AgentResponse {
        logger.info("Starting A2A enhanced orchestration for request")

        return try {
            // Проверяем включен ли A2A режим
            if (!a2aConfig.isA2AEnabled()) {
                logger.info("A2A mode disabled, falling back to legacy orchestration")
                return baseOrchestrator.processEnhanced(request, onStepComplete)
            }

            // Создаем A2A контекст выполнения
            val a2aContext = createA2AExecutionContext(request, context)

            // Публикуем событие о начале обработки
            publishA2AEvent(
                eventType = "ORCHESTRATION_STARTED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "STARTED",
                    agentId = "enhanced-orchestrator-a2a",
                    requestId = a2aContext.executionId,
                    timestamp = System.currentTimeMillis()
                ),
                metadata = mapOf("planId" to a2aContext.executionId)
            )

            // Создаем план без выполнения
            val plan = baseOrchestrator.createPlanFor(request)

            // Выполняем план через A2A
            val result = executePlanWithA2A(plan, context, onStepComplete)

            // Публикуем событие о завершении
            publishA2AEvent(
                eventType = "ORCHESTRATION_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = "enhanced-orchestrator-a2a",
                    requestId = a2aContext.executionId,
                    timestamp = System.currentTimeMillis(),
                    result = result.content
                ),
                metadata = mapOf("planId" to a2aContext.executionId)
            )

            result

        } catch (e: Exception) {
            logger.error("Error in A2A orchestration", e)

            // Публикуем событие об ошибке
            publishA2AEvent(
                eventType = "ORCHESTRATION_FAILED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "FAILED",
                    agentId = "enhanced-orchestrator-a2a",
                    requestId = activeExecutions.keys.firstOrNull() ?: "unknown",
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                ),
                metadata = mapOf("planId" to (activeExecutions.keys.firstOrNull() ?: "unknown"))
            )

            // Fallback к legacy режиму
            if (a2aConfig.isA2AEnabled()) {
                logger.info("Falling back to legacy orchestration due to A2A error")
                baseOrchestrator.processEnhanced(request, onStepComplete)
            } else {
                throw e
            }
        } finally {
            cleanupA2AExecution()
        }
    }

  
    /**
     * Отправляет A2A сообщение агенту и ожидает ответ
     */
    suspend fun sendA2AMessage(
        agentId: String,
        message: AgentMessage.Request,
        timeoutMs: Long = a2aConfig.getDefaultTimeoutMs()
    ): AgentMessage.Response {
        return try {
            messageBus.requestResponse(message, timeoutMs)
        } catch (e: MessageBusException.TimeoutException) {
            logger.error("A2A message timeout for agent: $agentId", e)
            throw IllegalStateException("A2A communication timeout with agent: $agentId", e)
        } catch (e: Exception) {
            logger.error("A2A communication error with agent: $agentId", e)
            throw IllegalStateException("A2A communication failed with agent: $agentId", e)
        }
    }

    /**
     * Публикует A2A событие
     */
    suspend fun publishA2AEvent(
        eventType: String,
        payload: MessagePayload,
        metadata: Map<String, Any> = emptyMap()
    ): Boolean {
        val event = AgentMessage.Event(
            senderId = "enhanced-orchestrator-a2a",
            eventType = eventType,
            payload = payload,
            metadata = metadata
        )

        return messageBus.publish(event)
    }

    /**
     * Получает статистику A2A агентов
     */
    suspend fun getA2AStatistics(): Map<String, Any> {
        return mapOf(
            "registered_agents" to 1, // Временно заглушка
            "total_messages" to 0, // Можно добавить счетчик сообщений
            "active_plans" to 0, // Временно, пока нет доступа к executionPlans
            "message_bus_enabled" to true
        )
    }

    /**
     * Очищает A2A ресурсы
     */
    suspend fun cleanup() {
        logger.info("Cleaning up A2A orchestrator resources")

        try {
            // Очищаем активные выполнения
            activeExecutions.clear()

            // Останавливаем всех A2A агентов
            a2aAgents.values.forEach { agent ->
                try {
                    agent.shutdownA2A(messageBus)
                } catch (e: Exception) {
                    logger.error("Error shutting down A2A agent: ${agent.a2aAgentId}", e)
                }
            }
            a2aAgents.clear()

            // Очищаем реестр
            a2aRegistry.clear()

            logger.info("A2A orchestrator cleanup completed")
        } catch (e: Exception) {
            logger.error("Error during A2A orchestrator cleanup", e)
        }
    }

    // Private methods

    private fun createA2AExecutionContext(
        request: AgentRequest,
        context: ExecutionContext
    ): A2AExecutionContext {
        val executionId = "exec_${System.currentTimeMillis()}_${request.hashCode()}"
        val a2aContext = A2AExecutionContext(
            executionId = executionId,
            originalRequest = request,
            context = context,
            messageBus = messageBus,
            startTime = System.currentTimeMillis()
        )

        activeExecutions[executionId] = a2aContext
        return a2aContext
    }

    private suspend fun analyzeRequestAndCreatePlan(
        request: AgentRequest,
        a2aContext: A2AExecutionContext
    ): AgentResponse {
        // Публикуем событие анализа
        publishA2AEvent(
            eventType = "REQUEST_ANALYSIS_STARTED",
            payload = MessagePayload.ProgressPayload(
                stepId = "analysis",
                status = "started",
                progress = 0,
                message = "Analyzing request with A2A support"
            )
        )

        // Используем базовый оркестратор для анализа
        val response = baseOrchestrator.processEnhanced(request) { step ->
            // Публикуем прогресс через A2A
            publishA2AEvent(
                eventType = "PLAN_STEP_PROGRESS",
                payload = MessagePayload.ProgressPayload(
                    stepId = step.javaClass.simpleName,
                    status = "completed",
                    progress = 50,
                    message = "Step completed"
                )
            )
        }

        // Публикуем завершение анализа
        publishA2AEvent(
            eventType = "REQUEST_ANALYSIS_COMPLETED",
            payload = MessagePayload.ProgressPayload(
                stepId = "analysis",
                status = "completed",
                progress = 100,
                message = "Request analysis completed"
            )
        )

        return response
    }

    /**
     * Выполнение плана шагов через A2A MessageBus
     */
    private suspend fun executePlanWithA2A(
        plan: ExecutionPlan,
        context: ExecutionContext,
        onStepComplete: suspend (OrchestratorStep) -> Unit
    ): AgentResponse {
        try {
            val completedSteps = mutableSetOf<String>()
            val stepResults = mutableMapOf<String, Any>()

            // Публикуем старт
            publishA2AEvent(
                eventType = "PLAN_EXECUTION_STARTED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "STARTED",
                    agentId = "enhanced-orchestrator-a2a",
                    requestId = plan.id,
                    timestamp = System.currentTimeMillis()
                )
            )

            for (step in plan.steps) {
                if (!step.dependencies.all { completedSteps.contains(it) }) continue

                // Прогресс: старт шага
                publishA2AEvent(
                    eventType = "STEP_STARTED",
                    payload = MessagePayload.ProgressPayload(
                        stepId = step.id,
                        status = "started",
                        progress = 0,
                        message = step.title
                    ),
                    metadata = mapOf("planId" to plan.id)
                )

                val policy = step.retryPolicy ?: RetryPolicy.DEFAULT
                var attempt = 1
                var lastError: String? = null
                var success = false
                var response: AgentMessage.Response? = null
                var stepResult: Any = emptyMap<String, Any>()

                while (attempt <= policy.maxAttempts && !success) {
                    val enrichedInput = enrichStepInput(step, stepResults)
                    val request = buildA2ARequestForStep(step, enrichedInput)
                    response = messageBus.requestResponse(request, a2aConfig.getDefaultTimeoutMs())

                    if (response.success) {
                        success = true
                        stepResult = mapResponseToResult(step, response)
                        break
                    } else {
                        lastError = response.error ?: "Unknown error"
                        val canRetry = policy.shouldRetryError(lastError)
                        if (canRetry && attempt < policy.maxAttempts) {
                            // Публикуем событие ретрая
                            publishA2AEvent(
                                eventType = "STEP_RETRYING",
                                payload = MessagePayload.ProgressPayload(
                                    stepId = step.id,
                                    status = "retrying",
                                    progress = 0,
                                    message = "${step.title}: retry $attempt/${policy.maxAttempts}"
                                ),
                                metadata = mapOf("attempt" to attempt, "planId" to plan.id)
                            )
                            // Backoff
                            val delayMs = policy.getDelay(attempt).inWholeMilliseconds
                            delay(delayMs)
                            attempt++
                        } else {
                            break
                        }
                    }
                }

                if (!success) {
                    // Публикуем провал шага
                    publishA2AEvent(
                        eventType = "STEP_FAILED",
                        payload = MessagePayload.ProgressPayload(
                            stepId = step.id,
                            status = "failed",
                            progress = 0,
                            message = "${step.title}: ${lastError ?: "failed"}"
                        ),
                        metadata = mapOf("attempts" to attempt - 1, "planId" to plan.id)
                    )

                    onStepComplete(
                        OrchestratorStep.TaskComplete(
                            agentName = "A2A-${step.agentType.name}",
                            taskId = 0,
                            taskTitle = step.title,
                            content = (mapOf("error" to (lastError ?: "Unknown error"))).toString(),
                            success = false,
                            error = lastError
                        )
                    )

                    // Завершаем план с ошибкой (fail-fast)
                    publishA2AEvent(
                        eventType = "PLAN_EXECUTION_FAILED",
                        payload = MessagePayload.ExecutionStatusPayload(
                            status = "FAILED",
                            agentId = "enhanced-orchestrator-a2a",
                            requestId = plan.id,
                            timestamp = System.currentTimeMillis(),
                            error = lastError
                        )
                    )
                    return AgentResponse.error(
                        error = lastError ?: "Execution error",
                        content = "Шаг '${step.title}' завершился ошибкой после ${attempt - 1} попыток"
                    )
                }

                completedSteps.add(step.id)
                stepResults[step.id] = stepResult

                // Callback об окончании шага (совместимость с OrchestratorStep API)
                onStepComplete(
                    OrchestratorStep.TaskComplete(
                        agentName = "A2A-${step.agentType.name}",
                        taskId = 0,
                        taskTitle = step.title,
                        content = stepResult.toString(),
                        success = true,
                        error = null
                    )
                )

                // Прогресс: завершение шага
                publishA2AEvent(
                    eventType = "STEP_COMPLETED",
                    payload = MessagePayload.ProgressPayload(
                        stepId = step.id,
                        status = "completed",
                        progress = 100,
                        message = step.title
                    ),
                    metadata = mapOf("planId" to plan.id)
                )
            }

            // Публикуем завершение плана
            publishA2AEvent(
                eventType = "PLAN_EXECUTION_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = "enhanced-orchestrator-a2a",
                    requestId = plan.id,
                    timestamp = System.currentTimeMillis(),
                    result = "${completedSteps.size} steps completed"
                )
            )

            // Формируем итоговый контент (если есть отчёт — он последний шаг)
            val reportStep = plan.steps.find { it.agentType == AgentType.REPORT_GENERATOR }
            val content = if (reportStep != null) {
                val res = stepResults[reportStep.id]
                when (res) {
                    is String -> res
                    is Map<*, *> -> (res["report"] as? String) ?: "План успешно выполнен."
                    else -> "План успешно выполнен."
                }
            } else {
                "План успешно выполнен. Завершено ${completedSteps.size} шагов."
            }

            return AgentResponse.success(
                content = content,
                metadata = mapOf("plan_id" to plan.id, "completed_steps" to completedSteps.size)
            )

        } catch (e: Exception) {
            logger.error("A2A plan execution error", e)
            publishA2AEvent(
                eventType = "PLAN_EXECUTION_FAILED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "FAILED",
                    agentId = "enhanced-orchestrator-a2a",
                    requestId = plan.id,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            )
            return AgentResponse.error(error = e.message ?: "Execution error", content = "Не удалось выполнить план")
        }
    }

    private fun buildA2ARequestForStep(step: PlanStep, input: Map<String, Any> = step.input): AgentMessage.Request {
        val (messageType, payload) = when (step.agentType) {
            AgentType.PROJECT_SCANNER -> {
                val reqType = if (step.input.containsKey("structure_only")) "PROJECT_STRUCTURE_REQUEST" else "FILE_DATA_REQUEST"
                reqType to MessagePayload.CustomPayload(
                    type = reqType,
                    data = input
                )
            }
            AgentType.ARCHITECTURE_ANALYSIS -> {
                "ARCHITECTURE_ANALYSIS_REQUEST" to MessagePayload.CustomPayload(
                    type = "ARCHITECTURE_ANALYSIS_REQUEST",
                    data = input
                )
            }
            AgentType.LLM_REVIEW -> {
                "LLM_REVIEW_REQUEST" to MessagePayload.CustomPayload(
                    type = "LLM_REVIEW_REQUEST",
                    data = input
                )
            }
            AgentType.EMBEDDING_INDEXER -> {
                "EMBEDDING_INDEX_REQUEST" to MessagePayload.CustomPayload(
                    type = "EMBEDDING_INDEX_REQUEST",
                    data = input
                )
            }
            AgentType.CODE_CHUNKER -> {
                "CODE_CHUNK_REQUEST" to MessagePayload.CustomPayload(
                    type = "CODE_CHUNK_REQUEST",
                    data = input
                )
            }
            AgentType.FILE_OPERATIONS -> {
                "OPEN_FILE_REQUEST" to MessagePayload.CustomPayload(
                    type = "OPEN_FILE_REQUEST",
                    data = input
                )
            }
            AgentType.USER_INTERACTION -> {
                "USER_INPUT_REQUEST" to MessagePayload.CustomPayload(
                    type = "USER_INPUT_REQUEST",
                    data = input
                )
            }
            AgentType.BUG_DETECTION -> {
                "BUG_ANALYSIS_REQUEST" to MessagePayload.CustomPayload(
                    type = "BUG_ANALYSIS_REQUEST",
                    data = input
                )
            }
            AgentType.CODE_QUALITY -> {
                "CODE_QUALITY_REQUEST" to MessagePayload.CustomPayload(
                    type = "CODE_QUALITY_REQUEST",
                    data = input
                )
            }
            AgentType.REPORT_GENERATOR -> {
                "REPORT_GENERATION_REQUEST" to MessagePayload.CustomPayload(
                    type = "REPORT_GENERATION_REQUEST",
                    data = input
                )
            }
            else -> {
                "TOOL_EXECUTION_REQUEST" to MessagePayload.CustomPayload(
                    type = "TOOL_EXECUTION_REQUEST",
                    data = mapOf(
                        "stepId" to step.id,
                        "description" to step.title,
                        "input" to input,
                        "dependencies" to step.dependencies.toList(),
                        "agentType" to step.agentType.name
                    )
                )
            }
        }

        return AgentMessage.Request(
            senderId = "enhanced-orchestrator-a2a",
            messageType = messageType,
            payload = payload,
            metadata = mapOf("stepId" to step.id, "agentType" to step.agentType.name)
        )
    }

    private fun mapResponseToResult(step: PlanStep, response: AgentMessage.Response): Any {
        if (!response.success) return mapOf("error" to (response.error ?: "Unknown error"))
        return when (val payload = response.payload) {
            is MessagePayload.ProjectStructurePayload -> {
                mapOf(
                    "files" to payload.files,
                    "directories" to payload.directories,
                    "project_type" to payload.projectType,
                    "total_files" to payload.totalFiles,
                    "scanned_at" to payload.scannedAt
                )
            }
            is MessagePayload.FilesScannedPayload -> {
                mapOf(
                    "files" to payload.files,
                    "file_types" to payload.fileTypes,
                    "scan_path" to payload.scanPath,
                    "scan_duration_ms" to payload.scanDurationMs
                )
            }
            is MessagePayload.CodeAnalysisPayload -> {
                mapOf(
                    "summary" to payload.summary,
                    "findings" to payload.findings,
                    "processed_files" to payload.processedFiles
                )
            }
            is MessagePayload.TextPayload -> payload.text
            is MessagePayload.CustomPayload -> if (payload.type == "TOOL_EXECUTION_RESULT") {
                payload.data["output"] ?: payload.data
            } else payload.data
            is MessagePayload.ErrorPayload -> mapOf("error" to payload.error)
            is MessagePayload.ProgressPayload -> mapOf("status" to payload.status, "progress" to payload.progress)
            is MessagePayload.ExecutionStatusPayload -> mapOf("status" to payload.status, "result" to (payload.result ?: ""))
            is MessagePayload.AgentInfoPayload -> mapOf("agent" to payload.agentId, "type" to payload.agentType)
        }
    }

    private fun enrichStepInput(step: PlanStep, stepResults: Map<String, Any>): Map<String, Any> {
        // merge original input with data from dependencies if known
        val base = step.input.toMutableMap()
        // if any dependency produced files, propagate
        val filesFromDeps = step.dependencies.mapNotNull { depId ->
            val res = stepResults[depId]
            when (res) {
                is Map<*, *> -> (res["files"] as? List<*>)?.filterIsInstance<String>()
                else -> null
            }
        }.flatten()
        if (filesFromDeps.isNotEmpty()) {
            base["files"] = filesFromDeps
        }
        return base
    }

      
    private fun subscribeToA2AEvents() {
        // Подписываемся на сообщения от A2A агентов
        coroutineScope.launch {
            messageBus.subscribeAll().collect { message ->
                when (message) {
                    is AgentMessage.Event -> handleA2AEvent(message)
                    else -> { /* Другие типы сообщений обрабатываются по необходимости */ }
                }
            }
        }
    }

    private suspend fun handleA2AEvent(event: AgentMessage.Event) {
        logger.debug("Received A2A event: ${event.eventType} from ${event.senderId}")

        when (event.eventType) {
            "TOOL_EXECUTION_FAILED" -> {
                logger.warn("Tool execution failed in A2A agent: ${event.senderId}")
                // Можно добавить логику retries или fallback
            }
            "AGENT_INITIALIZED" -> {
                logger.info("A2A agent initialized: ${event.senderId}")
            }
            "AGENT_SHUTDOWN" -> {
                logger.info("A2A agent shutdown: ${event.senderId}")
                a2aAgents.remove(event.senderId)
            }
        }
    }

    private fun cleanupA2AExecution() {
        // Очищаем контексты выполнения
        activeExecutions.clear()
    }

    /**
     * Контекст выполнения A2A
     */
    data class A2AExecutionContext(
        val executionId: String,
        val originalRequest: AgentRequest,
        val context: ExecutionContext,
        val messageBus: MessageBus,
        val startTime: Long
    )
}