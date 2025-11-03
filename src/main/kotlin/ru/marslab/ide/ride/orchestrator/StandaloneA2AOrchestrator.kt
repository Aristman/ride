package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.agent.tools.*
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.schema.ParsedResponse
import java.util.*

/**
 * Независимый A2A оркестратор, работающий только с A2A агентами
 * без зависимостей от легаси реализации
 */
class StandaloneA2AOrchestrator(
    private val messageBus: MessageBus = MessageBusProvider.get(),
    private val a2aRegistry: A2AAgentRegistry = A2AAgentRegistry.getInstance(),
    private val a2aConfig: A2AConfig = A2AConfig.getInstance()
) {

    private val logger = Logger.getInstance(StandaloneA2AOrchestrator::class.java)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // A2A состояния
    private val activeExecutions = mutableMapOf<String, A2AExecutionContext>()

    init {
        // Подписываемся на A2A события
        subscribeToA2AEvents()
    }

    /**
     * Регистрирует всех доступных A2A агентов
     */
    suspend fun registerAllAgents(llmProvider: LLMProvider) {
        logger.info("Registering standalone A2A agents...")
        logger.info("A2A Config: enabled=${a2aConfig.isA2AEnabled()}, allowed types=${a2aConfig.state.allowedAgentTypes}")

        try {
            // Базовые агенты без LLM
            val basicAgents = listOf(
                A2AProjectScannerToolAgent(),
                A2AArchitectureToolAgent(),
                A2AEmbeddingIndexerToolAgent(),
                A2ACodeChunkerToolAgent(),
                A2AOpenSourceFileToolAgent(),
                A2AUserInteractionAgent(),
                A2ACodeQualityToolAgent()
            )

            // LLM-агенты
            val llmAgents = listOf(
                A2ALLMReviewToolAgent(llmProvider),
                A2ABugDetectionToolAgent(llmProvider),
                A2AReportGeneratorToolAgent(llmProvider),
                A2ACodeGeneratorToolAgent(llmProvider)
            )

            // Регистрируем базовых агентов
            basicAgents.forEach { agent ->
                a2aRegistry.registerAgent(agent)
                logger.info("Registered basic A2A agent: ${agent.a2aAgentId}")
            }

            // Регистрируем LLM-агентов
            llmAgents.forEach { agent ->
                a2aRegistry.registerAgent(agent)
                logger.info("Registered LLM A2A agent: ${agent.a2aAgentId}")
            }

            logger.info("All ${basicAgents.size + llmAgents.size} A2A agents registered successfully")

        } catch (e: Exception) {
            logger.error("Failed to register A2A agents", e)
            throw e
        }
    }

    /**
     * Обеспечивает регистрацию базовых A2A агентов (не требующих LLM)
     * для предотвращения таймаутов при отсутствии обработчиков на шине
     */
    private suspend fun ensureBasicAgentsRegistered() {
        try {
            // Проверяем наличие ключевых агентов по типам
            val requiredTypes = listOf(
                AgentType.PROJECT_SCANNER,
                AgentType.ARCHITECTURE_ANALYSIS,
                AgentType.EMBEDDING_INDEXER,
                AgentType.CODE_CHUNKER,
                AgentType.FILE_OPERATIONS,
                AgentType.USER_INTERACTION,
                AgentType.CODE_QUALITY
            )

            val toRegister = mutableListOf<A2AAgent>()

            requiredTypes.forEach { type ->
                val exists = a2aRegistry.getAgentsByType(type).isNotEmpty()
                if (!exists) {
                    when (type) {
                        AgentType.PROJECT_SCANNER -> toRegister += A2AProjectScannerToolAgent()
                        AgentType.ARCHITECTURE_ANALYSIS -> toRegister += A2AArchitectureToolAgent()
                        AgentType.EMBEDDING_INDEXER -> toRegister += A2AEmbeddingIndexerToolAgent()
                        AgentType.CODE_CHUNKER -> toRegister += A2ACodeChunkerToolAgent()
                        AgentType.FILE_OPERATIONS -> toRegister += A2AOpenSourceFileToolAgent()
                        AgentType.USER_INTERACTION -> toRegister += A2AUserInteractionAgent()
                        AgentType.CODE_QUALITY -> toRegister += A2ACodeQualityToolAgent()
                        else -> {}
                    }
                }
            }

            if (toRegister.isNotEmpty()) {
                logger.info("Registering missing basic A2A agents: ${toRegister.map { it.agentType }}")
                toRegister.forEach { agent -> a2aRegistry.registerAgent(agent) }
            } else {
                logger.info("All basic A2A agents already registered")
            }
        } catch (e: Exception) {
            logger.error("Failed to ensure basic A2A agents registered", e)
            throw e
        }
    }

    /**
     * Обрабатывает запрос через независимую A2A оркестрацию
     */
    suspend fun processRequest(
        request: AgentRequest,
        onStepComplete: suspend (A2AStepResult) -> Unit = {}
    ): AgentResponse {
        logger.info("Starting standalone A2A orchestration for request: ${request.request.take(50)}...")
        logger.info("A2A Config Status: enabled=${a2aConfig.isA2AEnabled()}, metrics=${a2aConfig.isMetricsEnabled()}, timeout=${a2aConfig.getDefaultTimeoutMs()}ms")

        return try {
            // Проверяем доступность A2A агентов
            if (!a2aConfig.isA2AEnabled()) {
                logger.error("A2A mode is disabled")
                return AgentResponse.error(
                    error = "A2A mode is disabled",
                    content = "Невозможно обработать запрос: A2A режим отключен"
                )
            }

            // Гарантируем регистрацию базовых A2A-агентов, если ещё не зарегистрированы
            ensureBasicAgentsRegistered()

            // Создаем контекст выполнения
            val executionContext = createExecutionContext(request)

            // Публикуем событие начала
            publishEvent("ORCHESTRATION_STARTED", mapOf(
                "requestId" to executionContext.executionId,
                "request" to request.request
            ))

            // Создаем план выполнения
            val plan = createExecutionPlan(request, executionContext)

            // Выполняем план
            val result = executePlan(plan, executionContext, onStepComplete)

            // Публикуем событие завершения
            publishEvent("ORCHESTRATION_COMPLETED", mapOf(
                "requestId" to executionContext.executionId,
                "result" to result.content
            ))

            result

        } catch (e: Exception) {
            logger.error("Error in standalone A2A orchestration", e)

            AgentResponse.error(
                error = e.message ?: "Unknown error",
                content = "Ошибка при обработке запроса: ${e.message}"
            )
        } finally {
            cleanup()
        }
    }

    /**
     * Создает план выполнения на основе анализа запроса
     */
    private suspend fun createExecutionPlan(
        request: AgentRequest,
        context: A2AExecutionContext
    ): ExecutionPlan {
        logger.info("Creating execution plan for request...")

        // Анализируем запрос и определяем необходимые шаги
        val analysis = analyzeRequest(request)

        // Создаем шаги на основе анализа
        val steps = createStepsFromAnalysis(analysis, request)

        val plan = ExecutionPlan(
            id = context.executionId,
            userRequestId = context.executionId,
            originalRequest = request.request,
            analysis = analysis,
            steps = steps,
            currentState = PlanState.CREATED
        )

        logger.info("Created execution plan with ${steps.size} steps")
        return plan
    }

    /**
     * Анализирует запрос для определения типа работы
     */
    private suspend fun analyzeRequest(request: AgentRequest): RequestAnalysis {
        val requestLower = request.request.lowercase()

        // Простая эвристика для определения типа задачи
        val taskType = when {
            requestLower.contains("анализ") || requestLower.contains("проанализируй") -> TaskType.CODE_ANALYSIS
            requestLower.contains("найди ошибки") || requestLower.contains("баг") || requestLower.contains("отлад") -> TaskType.BUG_FIX
            requestLower.contains("генерируй") || requestLower.contains("создай") || requestLower.contains("напиши") -> TaskType.CODE_GENERATION
            requestLower.contains("архитектура") || requestLower.contains("структура") -> TaskType.ARCHITECTURE_ANALYSIS
            requestLower.contains("качество") || requestLower.contains("улучш") -> TaskType.REFACTORING
            else -> TaskType.CODE_ANALYSIS
        }

        val requiredTools = when (taskType) {
            TaskType.CODE_ANALYSIS -> setOf(AgentType.PROJECT_SCANNER, AgentType.ARCHITECTURE_ANALYSIS, AgentType.REPORT_GENERATOR)
            TaskType.BUG_FIX -> setOf(AgentType.PROJECT_SCANNER, AgentType.BUG_DETECTION, AgentType.REPORT_GENERATOR)
            TaskType.CODE_GENERATION -> setOf(AgentType.CODE_GENERATOR, AgentType.CODE_QUALITY, AgentType.LLM_REVIEW)
            TaskType.ARCHITECTURE_ANALYSIS -> setOf(AgentType.PROJECT_SCANNER, AgentType.ARCHITECTURE_ANALYSIS, AgentType.REPORT_GENERATOR)
            TaskType.REFACTORING -> setOf(AgentType.PROJECT_SCANNER, AgentType.CODE_QUALITY, AgentType.REPORT_GENERATOR)
            else -> setOf(AgentType.PROJECT_SCANNER, AgentType.REPORT_GENERATOR)
        }

        val complexity = when {
            requestLower.length < 50 -> ComplexityLevel.LOW
            requestLower.length < 200 -> ComplexityLevel.MEDIUM
            else -> ComplexityLevel.HIGH
        }

        return RequestAnalysis(
            taskType = taskType,
            requiredTools = requiredTools,
            context = ExecutionContext.Empty,
            parameters = mapOf("request" to request.request),
            requiresUserInput = false,
            estimatedComplexity = complexity,
            estimatedSteps = when (taskType) {
                TaskType.CODE_GENERATION -> 3
                TaskType.ARCHITECTURE_ANALYSIS -> 3
                else -> 3
            },
            confidence = 0.8,
            reasoning = "Анализ запроса: $requestLower"
        )
    }

    /**
     * Создает шаги выполнения на основе анализа
     */
    private fun createStepsFromAnalysis(
        analysis: RequestAnalysis,
        request: AgentRequest
    ): List<PlanStep> {
        val steps = mutableListOf<PlanStep>()
        var stepIndex = 0

        // В зависимости от типа задачи создаем разные шаги
        when (analysis.taskType) {
            TaskType.CODE_ANALYSIS -> {
                // 1. Сканирование проекта
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Сканирование проекта",
                    description = "Анализ структуры проекта и файлов",
                    agentType = AgentType.PROJECT_SCANNER,
                    input = mapOf(
                        "request" to request.request,
                        "scan_type" to "full"
                    )
                ))

                // 2. Анализ архитектуры
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Анализ архитектуры",
                    description = "Анализ архитектурных особенностей",
                    agentType = AgentType.ARCHITECTURE_ANALYSIS,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "request" to request.request,
                        "analysis_depth" to "medium"
                    )
                ))

                // 3. Генерация отчета
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Генерация отчета",
                    description = "Создание итогового отчета анализа",
                    agentType = AgentType.REPORT_GENERATOR,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "report_type" to "analysis",
                        "include_recommendations" to true
                    )
                ))
            }

            TaskType.BUG_FIX -> {
                // 1. Сканирование проекта
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Сканирование проекта",
                    description = "Поиск файлов для анализа багов",
                    agentType = AgentType.PROJECT_SCANNER,
                    input = mapOf("request" to request.request)
                ))

                // 2. Поиск багов
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Анализ на наличие багов",
                    description = "Поиск потенциальных ошибок в коде",
                    agentType = AgentType.BUG_DETECTION,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "request" to request.request,
                        "analysis_type" to "comprehensive"
                    )
                ))

                // 3. Создание отчета о багах
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Отчет о найденных проблемах",
                    description = "Генерация отчета с найденными багами",
                    agentType = AgentType.REPORT_GENERATOR,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "report_type" to "bug_analysis",
                        "include_fix_suggestions" to true
                    )
                ))
            }

            TaskType.CODE_GENERATION -> {
                // 1. Генерация кода
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Генерация кода",
                    description = "Создание кода на основе запроса",
                    agentType = AgentType.CODE_GENERATOR,
                    input = mapOf(
                        "request" to request.request,
                        "generation_type" to "code"
                    )
                ))

                // 2. Анализ качества сгенерированного кода
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Анализ качества кода",
                    description = "Проверка качества сгенерированного кода",
                    agentType = AgentType.CODE_QUALITY,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "analysis_type" to "quality_check"
                    )
                ))

                // 3. LLM ревью
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "LLM ревью кода",
                    description = "Ревью сгенерированного кода с помощью LLM",
                    agentType = AgentType.LLM_REVIEW,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "review_type" to "comprehensive"
                    )
                ))
            }

            TaskType.ARCHITECTURE_ANALYSIS -> {
                // 1. Сканирование проекта
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Сканирование проекта",
                    description = "Анализ структуры проекта",
                    agentType = AgentType.PROJECT_SCANNER,
                    input = mapOf("request" to request.request)
                ))

                // 2. Архитектурный анализ
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Глубокий анализ архитектуры",
                    description = "Детальный анализ архитектуры",
                    agentType = AgentType.ARCHITECTURE_ANALYSIS,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "analysis_depth" to "deep",
                        "include_patterns" to true
                    )
                ))

                // 3. Создание архитектурного отчета
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Архитектурный отчет",
                    description = "Генерация отчета об архитектуре",
                    agentType = AgentType.REPORT_GENERATOR,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "report_type" to "architecture",
                        "include_diagrams" to true
                    )
                ))
            }

            TaskType.REFACTORING -> {
                // 1. Сканирование проекта
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Сканирование проекта",
                    description = "Поиск файлов для рефакторинга",
                    agentType = AgentType.PROJECT_SCANNER,
                    input = mapOf("request" to request.request)
                ))

                // 2. Анализ качества кода
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Анализ качества кода",
                    description = "Поиск проблем для рефакторинга",
                    agentType = AgentType.CODE_QUALITY,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "analysis_type" to "refactoring_opportunities"
                    )
                ))

                // 3. Создание отчета с рекомендациями
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Отчет о рефакторинге",
                    description = "Генерация отчета с рекомендациями",
                    agentType = AgentType.REPORT_GENERATOR,
                    dependencies = setOf("step_${stepIndex - 1}"),
                    input = mapOf(
                        "report_type" to "refactoring",
                        "include_recommendations" to true
                    )
                ))
            }

            else -> {
                // Действия по умолчанию для других типов задач
                steps.add(PlanStep(
                    id = "step_${++stepIndex}",
                    title = "Анализ запроса",
                    description = "Базовый анализ запроса",
                    agentType = AgentType.PROJECT_SCANNER,
                    input = mapOf("request" to request.request)
                ))
            }
        }

        return steps
    }

    /**
     * Выполняет план через A2A агентов
     */
    private suspend fun executePlan(
        plan: ExecutionPlan,
        context: A2AExecutionContext,
        onStepComplete: suspend (A2AStepResult) -> Unit
    ): AgentResponse {
        logger.info("Executing plan with ${plan.steps.size} steps")

        try {
            val completedSteps = mutableSetOf<String>()
            val stepResults = mutableMapOf<String, Any>()

            // Публикуем начало выполнения плана
            publishEvent("PLAN_EXECUTION_STARTED", mapOf(
                "planId" to plan.id,
                "stepsCount" to plan.steps.size
            ))

            for (step in plan.steps) {
                // Проверяем зависимости
                if (!step.dependencies.all { completedSteps.contains(it) }) {
                    logger.info("Skipping step ${step.id} - dependencies not met")
                    continue
                }

                // Выполняем шаг
                val stepResult = executeStep(step, stepResults, context)

                if (stepResult.success) {
                    completedSteps.add(step.id)
                    stepResult.result?.let { stepResults[step.id] = it }

                    // Callback о завершении шага
                    onStepComplete(A2AStepResult(
                        stepId = step.id,
                        stepTitle = step.title,
                        success = true,
                        result = stepResult.result,
                        error = null
                    ))

                    // Публикуем событие завершения шага
                    publishEvent("STEP_COMPLETED", mapOf(
                        "stepId" to step.id,
                        "stepTitle" to step.title,
                        "planId" to plan.id
                    ))

                } else {
                    // Шаг завершился с ошибкой
                    onStepComplete(A2AStepResult(
                        stepId = step.id,
                        stepTitle = step.title,
                        success = false,
                        result = null,
                        error = stepResult.error
                    ))

                    // Публикуем событие ошибки шага
                    publishEvent("STEP_FAILED", mapOf<String, Any>(
                        "stepId" to step.id,
                        "stepTitle" to step.title,
                        "error" to (stepResult.error ?: "Unknown error"),
                        "planId" to plan.id
                    ))

                    // Прерываем выполнение плана при ошибке
                    return AgentResponse.error(
                        error = stepResult.error ?: "Unknown error",
                        content = "Ошибка выполнения шага '${step.title}': ${stepResult.error}"
                    )
                }
            }

            // Формируем итоговый результат
            val finalContent = generateFinalResult(plan, stepResults)

            // Публикуем завершение плана
            publishEvent("PLAN_EXECUTION_COMPLETED", mapOf(
                "planId" to plan.id,
                "completedSteps" to completedSteps.size,
                "totalSteps" to plan.steps.size
            ))

            return AgentResponse.success(
                content = finalContent,
                metadata = mapOf(
                    "planId" to plan.id,
                    "completedSteps" to completedSteps.size,
                    "totalSteps" to plan.steps.size
                )
            )

        } catch (e: Exception) {
            logger.error("Error executing plan", e)

            publishEvent("PLAN_EXECUTION_FAILED", mapOf<String, Any>(
                "planId" to plan.id,
                "error" to (e.message ?: "Unknown error")
            ))

            return AgentResponse.error(
                error = e.message ?: "Unknown error",
                content = "Ошибка выполнения плана: ${e.message}"
            )
        }
    }

    /**
     * Выполняет отдельный шаг через A2A агента
     */
    private suspend fun executeStep(
        step: PlanStep,
        stepResults: Map<String, Any>,
        context: A2AExecutionContext
    ): A2AStepExecutionResult {
        logger.info("Executing step: ${step.title}")

        try {
            // Обогащаем входные данными из предыдущих шагов
            val enrichedInput = enrichStepInput(step, stepResults)

            // Создаем A2A запрос
            val request = createA2ARequest(step, enrichedInput)

            // Отправляем запрос агенту
            val response = messageBus.requestResponse(request, a2aConfig.getDefaultTimeoutMs())

            if (response.success) {
                val result = extractStepResult(response)
                return A2AStepExecutionResult(
                    success = true,
                    result = result,
                    error = null
                )
            } else {
                return A2AStepExecutionResult(
                    success = false,
                    result = null,
                    error = response.error ?: "Unknown error"
                )
            }

        } catch (e: Exception) {
            logger.error("Error executing step: ${step.title}", e)
            return A2AStepExecutionResult(
                success = false,
                result = null,
                error = e.message ?: "Execution error"
            )
        }
    }

    /**
     * Создает A2A запрос для шага
     */
    private fun createA2ARequest(step: PlanStep, input: Map<String, Any>): AgentMessage.Request {
        val messageType = when (step.agentType) {
            AgentType.PROJECT_SCANNER -> "PROJECT_STRUCTURE_REQUEST"
            AgentType.ARCHITECTURE_ANALYSIS -> "ARCHITECTURE_ANALYSIS_REQUEST"
            AgentType.LLM_REVIEW -> "LLM_REVIEW_REQUEST"
            AgentType.EMBEDDING_INDEXER -> "EMBEDDING_INDEX_REQUEST"
            AgentType.CODE_CHUNKER -> "CODE_CHUNK_REQUEST"
            AgentType.FILE_OPERATIONS -> "OPEN_FILE_REQUEST"
            AgentType.USER_INTERACTION -> "USER_INPUT_REQUEST"
            AgentType.BUG_DETECTION -> "BUG_ANALYSIS_REQUEST"
            AgentType.CODE_QUALITY -> "CODE_QUALITY_ANALYSIS_REQUEST"
            AgentType.REPORT_GENERATOR -> "REPORT_GENERATION_REQUEST"
            AgentType.CODE_GENERATOR -> "CODE_GENERATION_REQUEST"
            else -> "TOOL_EXECUTION_REQUEST"
        }

        return AgentMessage.Request(
            senderId = "standalone-a2a-orchestrator",
            messageType = messageType,
            payload = MessagePayload.CustomPayload(
                type = messageType,
                data = input
            ),
            metadata = mapOf(
                "stepId" to step.id,
                "stepTitle" to step.title,
                "agentType" to step.agentType.name
            )
        )
    }

    /**
     * Обогащает входные данные результатами предыдущих шагов
     */
    private fun enrichStepInput(step: PlanStep, stepResults: Map<String, Any>): Map<String, Any> {
        val enriched = step.input.toMutableMap()

        // Добавляем результаты из зависимых шагов
        step.dependencies.forEach { depId ->
            val depResult = stepResults[depId]
            if (depResult != null) {
                enriched["dependency_${depId}"] = depResult
            }
        }

        return enriched
    }

    /**
     * Извлекает результат из ответа агента
     */
    private fun extractStepResult(response: AgentMessage.Response): Any {
        return when (val payload = response.payload) {
            is MessagePayload.TextPayload -> payload.text
            is MessagePayload.CustomPayload -> payload.data
            is MessagePayload.ProjectStructurePayload -> mapOf(
                "files" to payload.files,
                "directories" to payload.directories,
                "projectType" to payload.projectType
            )
            is MessagePayload.FilesScannedPayload -> mapOf(
                "files" to payload.files,
                "fileTypes" to payload.fileTypes
            )
            is MessagePayload.CodeAnalysisPayload -> mapOf(
                "summary" to payload.summary,
                "findings" to payload.findings
            )
            else -> payload.toString()
        }
    }

    /**
     * Генерирует финальный результат выполнения
     */
    private fun generateFinalResult(plan: ExecutionPlan, stepResults: Map<String, Any>): String {
        // Ищем отчет от репорт генератора
        val reportStep = plan.steps.find { it.agentType == AgentType.REPORT_GENERATOR }
        if (reportStep != null) {
            val reportResult = stepResults[reportStep.id]
            if (reportResult is Map<*, *> && reportResult.containsKey("report")) {
                return reportResult["report"] as? String ?: "Анализ завершен"
            }
            if (reportResult is String) {
                return reportResult
            }
        }

        // Если нет отчета, собираем результаты из всех шагов
        val results = stepResults.map { (stepId, result) ->
            val step = plan.steps.find { it.id == stepId }
            "${step?.title ?: stepId}: ${result.toString().take(200)}..."
        }

        return "План успешно выполнен. Завершено ${stepResults.size} шагов:\n\n${results.joinToString("\n\n")}"
    }

    /**
     * Публикует событие в шину
     */
    private suspend fun publishEvent(eventType: String, data: Map<String, Any>) {
        try {
            val event = AgentMessage.Event(
                senderId = "standalone-a2a-orchestrator",
                eventType = eventType,
                payload = MessagePayload.CustomPayload(
                    type = eventType,
                    data = data
                )
            )
            messageBus.publish(event)
        } catch (e: Exception) {
            logger.error("Failed to publish event: $eventType", e)
        }
    }

    /**
     * Подписывается на события A2A агентов
     */
    private fun subscribeToA2AEvents() {
        coroutineScope.launch {
            messageBus.subscribeAll().collect { message ->
                when (message) {
                    is AgentMessage.Event -> handleA2AEvent(message)
                    else -> { /* ignore other message types */ }
                }
            }
        }
    }

    /**
     * Обрабатывает A2A события
     */
    private suspend fun handleA2AEvent(event: AgentMessage.Event) {
        when (event.eventType) {
            "AGENT_INITIALIZED" -> {
                logger.info("A2A agent initialized: ${event.senderId}")
            }
            "AGENT_ERROR" -> {
                logger.error("A2A agent error: ${event.senderId}")
            }
            "AGENT_SHUTDOWN" -> {
                logger.info("A2A agent shutdown: ${event.senderId}")
            }
            else -> {
                logger.debug("Received A2A event: ${event.eventType} from ${event.senderId}")
            }
        }
    }

    /**
     * Создает контекст выполнения
     */
    private fun createExecutionContext(request: AgentRequest): A2AExecutionContext {
        val executionId = "a2a_exec_${System.currentTimeMillis()}_${request.request.hashCode()}"
        val context = A2AExecutionContext(
            executionId = executionId,
            request = request,
            startTime = System.currentTimeMillis()
        )
        activeExecutions[executionId] = context
        return context
    }

    /**
     * Очищает ресурсы
     */
    private fun cleanup() {
        activeExecutions.clear()
    }

    // Вспомогательные классы и data классы

    data class A2AExecutionContext(
        val executionId: String,
        val request: AgentRequest,
        val startTime: Long
    )

    data class A2AStepResult(
        val stepId: String,
        val stepTitle: String,
        val success: Boolean,
        val result: Any?,
        val error: String?
    )

    data class A2AStepExecutionResult(
        val success: Boolean,
        val result: Any?,
        val error: String?
    )

  }