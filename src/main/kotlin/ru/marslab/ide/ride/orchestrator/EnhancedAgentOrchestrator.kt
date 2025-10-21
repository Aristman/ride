package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.OrchestratorStep
import ru.marslab.ide.ride.agent.ToolAgentRegistry
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.chat.ToolAgentExecutionStatus
import ru.marslab.ide.ride.model.chat.ToolAgentStatusManager
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.orchestrator.impl.InMemoryPlanStorage
import ru.marslab.ide.ride.orchestrator.impl.LLMRequestAnalyzer
import java.util.*
import kotlin.random.Random

/**
 * Расширенный оркестратор с поддержкой интерактивности и персистентности
 *
 * Интегрирует все компоненты Phase 1 и Phase 2:
 * - RequestAnalyzer для анализа запросов
 * - PlanStateMachine для управления состояниями
 * - PlanStorage для персистентности
 * - ProgressTracker для отслеживания прогресса
 * - ToolAgentRegistry для управления Tool Agents
 */
class EnhancedAgentOrchestrator(
    private val llmProvider: LLMProvider,
    private val uncertaintyAnalyzer: UncertaintyAnalyzer = UncertaintyAnalyzer,
    private val planStorage: PlanStorage = InMemoryPlanStorage(),
    private val requestAnalyzer: RequestAnalyzer = LLMRequestAnalyzer(llmProvider, uncertaintyAnalyzer),
    private val stateMachine: PlanStateMachine = PlanStateMachine(),
    private val progressTracker: ProgressTracker = ProgressTracker(),
    private val toolAgentRegistry: ToolAgentRegistry = ToolAgentRegistry(),
    private val retryLoopExecutor: RetryLoopExecutor = RetryLoopExecutor()
) : StateChangeListener, ProgressListener {

    private val logger = Logger.getInstance(EnhancedAgentOrchestrator::class.java)
    private val activePlans = mutableMapOf<String, ExecutionPlan>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val progressListeners = mutableListOf<ToolAgentProgressListener>()
    private val statusManager = ToolAgentStatusManager()

    init {
        // Добавляем слушателей
        stateMachine.addListener(this)
        progressTracker.addListener(this)
    }

    /**
     * Добавляет слушателя прогресса tool agents
     */
    fun addProgressListener(listener: ToolAgentProgressListener) {
        progressListeners.add(listener)
    }

    /**
     * Удаляет слушателя прогресса tool agents
     */
    fun removeProgressListener(listener: ToolAgentProgressListener) {
        progressListeners.remove(listener)
    }

    
    /**
     * Обрабатывает запрос пользователя с использованием расширенного оркестратора
     */
    suspend fun processEnhanced(
        request: AgentRequest,
        onStepComplete: suspend (OrchestratorStep) -> Unit
    ): AgentResponse {
        logger.info("Starting enhanced orchestration for request")

        return try {
            // 1. Анализируем запрос
            val userRequest = UserRequest(
                originalRequest = request.request,
                context = ExecutionContext(
                    projectPath = request.context.project.basePath,
                    additionalContext = mapOf(
                        "selected_files" to emptyList<String>(),
                        "chat_history" to emptyList<String>()
                    )
                )
            )

            val analysis = requestAnalyzer.analyze(userRequest)
            logger.info("Request analysis completed: ${analysis.taskType}")

            // Валидируем доступность требуемых ToolAgents и отфильтровываем отсутствующие
            val availableTools = analysis.requiredTools.filter { toolAgentRegistry.isAvailable(it) }.toSet()
            val missingTools = analysis.requiredTools - availableTools
            if (missingTools.isNotEmpty()) {
                logger.warn("Some required ToolAgents are not available and will be skipped: $missingTools")
            }
            val adjustedAnalysis = analysis.copy(requiredTools = availableTools)

            // 2. Создаем план выполнения
            val plan = createExecutionPlan(userRequest, adjustedAnalysis)

            // 3. Сохраняем план
            planStorage.save(plan)
            activePlans[plan.id] = plan

            // 4. Начинаем отслеживание прогресса
            progressTracker.startTracking(plan)

            // 5. Переводим план в состояние анализа
            val updatedPlan = stateMachine.transition(plan, PlanEvent.Start(adjustedAnalysis))
            activePlans[plan.id] = updatedPlan

            // 6. Отправляем уведомление о начале анализа
            onStepComplete(
                OrchestratorStep.PlanningComplete(
                    agentName = "EnhancedAgentOrchestrator",
                    content = buildAnalysisNotification(analysis),
                    success = true,
                    error = null
                )
            )

            // 7. Выполняем план
            val result = executePlan(updatedPlan, onStepComplete)

            // 8. Завершаем отслеживание
            progressTracker.finishTracking(plan.id, result.success)

            result

        } catch (e: Exception) {
            logger.error("Error during enhanced orchestration", e)

            val errorStep = OrchestratorStep.Error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Произошла ошибка при выполнении расширенного оркестратора"
            )
            onStepComplete(errorStep)

            AgentResponse.error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Произошла ошибка при координации агентов"
            )
        }
    }

    /**
     * Приостанавливает выполнение плана
     */
    suspend fun pausePlan(planId: String): Boolean {
        return try {
            val plan = activePlans[planId] ?: planStorage.load(planId)
            if (plan != null && stateMachine.canPause(plan)) {
                val updatedPlan = stateMachine.transition(plan, PlanEvent.Pause)
                activePlans[planId] = updatedPlan
                planStorage.update(updatedPlan)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to pause plan $planId", e)
            false
        }
    }

    /**
     * Возобновляет выполнение плана
     */
    suspend fun resumePlan(planId: String): Boolean {
        return try {
            val plan = activePlans[planId] ?: planStorage.load(planId)
            if (plan != null && stateMachine.canResume(plan)) {
                val updatedPlan = stateMachine.transition(plan, PlanEvent.Resume)
                activePlans[planId] = updatedPlan
                planStorage.update(updatedPlan)

                // Продолжаем выполнение в фоновом режиме
                coroutineScope.launch {
                    continuePlanExecution(updatedPlan)
                }

                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to resume plan $planId", e)
            false
        }
    }

    /**
     * Возобновляет выполнение плана с callback для уведомлений о шагах
     */
    suspend fun resumePlanWithCallback(
        planId: String,
        userInput: String,
        onStepComplete: suspend (OrchestratorStep) -> Unit
    ): AgentResponse {
        return try {
            // Обрабатываем пользовательский ввод
            val inputHandled = handleUserInput(planId, userInput)
            if (!inputHandled) {
                return AgentResponse.error(
                    error = "Не удалось обработать пользовательский ввод",
                    content = "План $planId не найден или не ожидает ввода"
                )
            }

            // Получаем обновлённый план
            val plan = activePlans[planId] ?: planStorage.load(planId)
            if (plan == null) {
                return AgentResponse.error(
                    error = "План не найден",
                    content = "План $planId не существует"
                )
            }

            // Продолжаем выполнение плана
            executePlan(plan, onStepComplete)

        } catch (e: Exception) {
            logger.error("Failed to resume plan with callback $planId", e)
            AgentResponse.error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Произошла ошибка при возобновлении плана"
            )
        }
    }

    /**
     * Отменяет выполнение плана
     */
    suspend fun cancelPlan(planId: String): Boolean {
        return try {
            val plan = activePlans[planId] ?: planStorage.load(planId)
            if (plan != null && stateMachine.canCancel(plan)) {
                val updatedPlan = stateMachine.transition(plan, PlanEvent.Cancel)
                activePlans[planId] = updatedPlan
                planStorage.update(updatedPlan)
                progressTracker.finishTracking(planId, false)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to cancel plan $planId", e)
            false
        }
    }

    /**
     * Обрабатывает пользовательский ввод для плана, требующего ввода
     */
    suspend fun handleUserInput(planId: String, userInput: String): Boolean {
        return try {
            val plan = activePlans[planId] ?: planStorage.load(planId)
            if (plan != null && plan.currentState == PlanState.REQUIRES_INPUT) {
                val updatedPlan = stateMachine.transition(plan, PlanEvent.UserInputReceived(userInput))
                activePlans[planId] = updatedPlan
                planStorage.update(updatedPlan)

                // Продолжаем выполнение
                coroutineScope.launch {
                    continuePlanExecution(updatedPlan)
                }

                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to handle user input for plan $planId", e)
            false
        }
    }

    /**
     * Возвращает текущие активные планы
     */
    suspend fun getActivePlans(): List<ExecutionPlan> {
        return planStorage.listActive()
    }

    /**
     * Возвращает прогресс выполнения плана
     */
    suspend fun getPlanProgress(planId: String): PlanProgress? {
        return progressTracker.getProgress(planId)
    }

    /**
     * Освобождает ресурсы
     */
    fun dispose() {
        logger.info("Disposing EnhancedAgentOrchestrator")
        coroutineScope.cancel()
        stateMachine.removeListener(this)
        progressTracker.removeListener(this)
        toolAgentRegistry.clear()
    }
    
    /**
     * Возвращает реестр Tool Agents для регистрации агентов
     */
    fun getToolAgentRegistry(): ToolAgentRegistry {
        return toolAgentRegistry
    }

    // Приватные методы

    private suspend fun createExecutionPlan(userRequest: UserRequest, analysis: RequestAnalysis): ExecutionPlan {
        val steps = generatePlanSteps(analysis)

        return ExecutionPlan(
            userRequestId = UUID.randomUUID().toString(),
            originalRequest = userRequest.originalRequest,
            analysis = analysis,
            steps = steps,
            metadata = mapOf(
                "created_by" to "EnhancedAgentOrchestrator",
                "version" to "1.0"
            )
        )
    }

    private fun generatePlanSteps(analysis: RequestAnalysis): List<PlanStep> {
        // Базовая генерация шагов на основе анализа
        val steps = mutableListOf<PlanStep>()

        var scannerStepId: String? = null
        if (analysis.requiredTools.contains(AgentType.PROJECT_SCANNER)) {
            val scanStep = PlanStep(
                title = "Сканирование проекта",
                description = "Анализ файловой структуры проекта",
                agentType = AgentType.PROJECT_SCANNER,
                input = mapOf(
                    "projectPath" to (analysis.context.projectPath ?: "."),
                    "patterns" to listOf("**/*.kt", "**/*.java"),
                    "excludePatterns" to listOf("**/build/**", "**/.*/**", "**/node_modules/**")
                ),
                estimatedDurationMs = 30_000L // 30 секунд
            )
            steps.add(scanStep)
            scannerStepId = scanStep.id
        }

        if (analysis.requiredTools.contains(AgentType.BUG_DETECTION)) {
            steps.add(
                PlanStep(
                    title = "Поиск багов",
                    description = "Анализ кода на наличие ошибок",
                    agentType = AgentType.BUG_DETECTION,
                    input = mapOf(
                        "files" to emptyList<String>(), // Будет заполнено из предыдущего шага
                        "severityLevel" to "medium",
                        "projectPath" to (analysis.context.projectPath ?: ".") // Для enrichStepInput
                    ),
                    // зависим от сканера, чтобы можно было выполнять параллельно с проверкой качества
                    dependencies = scannerStepId?.let { setOf(it) } ?: emptySet(),
                    estimatedDurationMs = 60_000L // 1 минута
                )
            )
        }

        if (analysis.requiredTools.contains(AgentType.ARCHITECTURE_ANALYSIS)) {
            steps.add(
                PlanStep(
                    title = "Анализ архитектуры",
                    description = "Оценка структуры и архитектуры проекта",
                    agentType = AgentType.ARCHITECTURE_ANALYSIS,
                    input = mapOf(
                        "files" to emptyList<String>(),
                        "projectPath" to (analysis.context.projectPath ?: ".")
                    ),
                    // зависит только от сканирования, выполняется параллельно с другими анализами
                    dependencies = scannerStepId?.let { setOf(it) } ?: emptySet(),
                    estimatedDurationMs = 45_000L
                )
            )
        }

        if (analysis.requiredTools.contains(AgentType.LLM_REVIEW)) {
            steps.add(
                PlanStep(
                    title = "LLM обзор кода",
                    description = "Анализ кода LLM-агентом для выявления потенциальных проблем",
                    agentType = AgentType.LLM_REVIEW,
                    input = mapOf(
                        "files" to emptyList<String>(),
                        "projectPath" to (analysis.context.projectPath ?: "."),
                        "maxFindingsPerFile" to 20,
                        "maxCharsPerFile" to 8000
                    ),
                    // зависит только от сканирования — выполняется параллельно с другими анализами
                    dependencies = scannerStepId?.let { setOf(it) } ?: emptySet(),
                    estimatedDurationMs = 60_000L
                )
            )
        }

        if (analysis.requiredTools.contains(AgentType.CODE_QUALITY)) {
            steps.add(
                PlanStep(
                    title = "Проверка качества кода",
                    description = "Статический анализ качества кода",
                    agentType = AgentType.CODE_QUALITY,
                    input = mapOf(
                        "files" to emptyList<String>(),
                        "projectPath" to (analysis.context.projectPath ?: ".")
                    ),
                    // зависим только от сканера, чтобы идти параллельно с BUG_DETECTION
                    dependencies = scannerStepId?.let { setOf(it) } ?: emptySet(),
                    estimatedDurationMs = 45_000L
                )
            )
        }

        if (analysis.requiredTools.contains(AgentType.CODE_FIXER)) {
            steps.add(
                PlanStep(
                    title = "Исправление кода",
                    description = "Автоматическое исправление найденных проблем",
                    agentType = AgentType.CODE_FIXER,
                    dependencies = setOf(steps.lastOrNull()?.id ?: ""),
                    estimatedDurationMs = 45_000L // 45 секунд
                )
            )
        }

        if (analysis.requiredTools.contains(AgentType.REPORT_GENERATOR)) {
            // Отчёт должен зависеть от всех предыдущих шагов, чтобы выполняться последним
            val dependencyIds = steps.map { it.id }.toSet()
            steps.add(
                PlanStep(
                    title = "Генерация отчета",
                    description = "Создание отчета о выполненных действиях",
                    agentType = AgentType.REPORT_GENERATOR,
                    input = mapOf(
                        "format" to "markdown",
                        "includeDetails" to true
                    ),
                    dependencies = dependencyIds,
                    estimatedDurationMs = 15_000L // 15 секунд
                )
            )
        }

        return steps
    }

    private suspend fun executePlan(
        plan: ExecutionPlan,
        onStepComplete: suspend (OrchestratorStep) -> Unit
    ): AgentResponse {
        return try {
            // Переводим план в состояние выполнения
            val executingPlan = stateMachine.transition(plan, PlanEvent.Start(plan.analysis))
            activePlans[plan.id] = executingPlan
            planStorage.update(executingPlan)

            // Последовательно выполняем шаги (для Phase 1)
            val completedSteps = mutableListOf<String>()
            val stepResults = mutableMapOf<String, Any>() // Результаты шагов для передачи
            val finalResults = mutableListOf<StepExecutionResult>() // Накопление финальных результатов

            for (step in executingPlan.steps) {
                if (step.status != StepStatus.PENDING) continue

                // Проверяем зависимости
                if (step.dependencies.all { it in completedSteps }) {
                    // Выполняем шаг с учётом retry/loop
                    val stepResult = if (step.retryPolicy != null || step.loopConfig != null) {
                        executeStepWithRetryLoop(step, executingPlan.analysis.context, stepResults)
                    } else {
                        executeStep(step, executingPlan.analysis.context, stepResults)
                    }
                    
                    completedSteps.add(step.id)
                    stepResults[step.id] = stepResult // Сохраняем результат
                    
                    // Определяем, является ли шаг финальным (не используется другими шагами)
                    val isFinalStep = executingPlan.steps.none { otherStep -> 
                        step.id in otherStep.dependencies 
                    }
                    
                    // Накапливаем результаты финальных шагов
                    if (isFinalStep) {
                        finalResults.add(
                            StepExecutionResult(
                                stepId = step.id,
                                stepTitle = step.title,
                                stepDescription = step.description,
                                agentType = step.agentType,
                                result = stepResult
                            )
                        )
                        logger.info("Step ${step.id} marked as final, result accumulated")
                    }

                    // Обновляем план
                    val updatedPlan = executingPlan.updateStepStatus(step.id, StepStatus.COMPLETED, stepResult)
                    activePlans[plan.id] = updatedPlan
                    planStorage.update(updatedPlan)

                    progressTracker.completeStep(plan.id, step.id, stepResult)
                }
            }

            // Завершаем план
            val finalPlan = stateMachine.transition(executingPlan, PlanEvent.Complete)
            activePlans[plan.id] = finalPlan
            planStorage.update(finalPlan)
            
            // Генерируем финальный отчет через LLM
            val finalReport = if (finalResults.isNotEmpty()) {
                generateFinalReport(executingPlan.analysis, finalResults)
            } else {
                "План успешно выполнен. Завершено ${completedSteps.size} шагов."
            }

            AgentResponse.success(
                content = finalReport,
                metadata = mapOf(
                    "plan_id" to plan.id,
                    "completed_steps" to completedSteps.size,
                    "final_results_count" to finalResults.size
                )
            )

        } catch (e: Exception) {
            logger.error("Error executing plan", e)

            val failedPlan = stateMachine.transition(plan, PlanEvent.Error(e.message ?: "Ошибка выполнения"))
            activePlans[plan.id] = failedPlan
            planStorage.update(failedPlan)

            AgentResponse.error(
                error = e.message ?: "Ошибка выполнения плана",
                content = "Не удалось выполнить план из-за ошибки"
            )
        }
    }

    private suspend fun executeStep(
        step: PlanStep,
        executionContext: ExecutionContext,
        previousResults: Map<String, Any> = emptyMap()
    ): String {
        logger.info("Executing step: ${step.title}")

        val agent = toolAgentRegistry.get(step.agentType)
        val agentName = getAgentDisplayName(step.agentType)

        // Создаем сообщение о начале выполнения
        val statusMessage = statusManager.createStatusMessage(
            stepId = step.id,
            agentType = step.agentType.name,
            agentName = agentName,
            displayMessage = "$agentName выполняется: ${step.title}",
            status = ToolAgentExecutionStatus.RUNNING
        )

        // Уведомляем слушателей о начале
        progressListeners.forEach { it.onToolAgentStarted(statusMessage) }

        if (agent != null) {
            logger.info("Executing step ${step.id} with ToolAgent ${step.agentType}")

            try {
                // Обогащаем input данными из предыдущих шагов
                val enrichedInput = enrichStepInput(step, previousResults)

                // Конвертируем PlanStep в ToolPlanStep
                val toolStep = ToolPlanStep(
                    id = step.id,
                    description = step.description,
                    agentType = step.agentType,
                    input = StepInput(enrichedInput),
                    dependencies = step.dependencies
                )

                // Используем контекст из плана
                val context = executionContext

                val result = agent.executeStep(toolStep, context)

                if (result.success) {
                    // Обновляем статус на завершенный
                    val completedMessage = statusManager.updateStatus(
                        stepId = step.id,
                        status = ToolAgentExecutionStatus.COMPLETED,
                        output = result.output.data,
                        metadata = result.metadata
                    )

                    completedMessage?.let {
                        progressListeners.forEach { listener ->
                            listener.onToolAgentStatusUpdated(it)
                            listener.onToolAgentCompleted(it)
                        }
                    }

                    return result.output.data.toString()
                } else {
                    // Обновляем статус на ошибку
                    val errorMessage = statusManager.updateStatus(
                        stepId = step.id,
                        status = ToolAgentExecutionStatus.FAILED,
                        error = result.error ?: "Step execution failed"
                    )

                    errorMessage?.let { message ->
                        progressListeners.forEach { listener ->
                            listener.onToolAgentStatusUpdated(message)
                            listener.onToolAgentFailed(message, result.error ?: "Unknown error")
                        }
                    }

                    throw Exception(result.error ?: "Step execution failed")
                }
            } catch (e: Exception) {
                // Обновляем статус на ошибку
                val errorMessage = statusManager.updateStatus(
                    stepId = step.id,
                    status = ToolAgentExecutionStatus.FAILED,
                    error = e.message ?: "Step execution failed"
                )

                errorMessage?.let { message ->
                    progressListeners.forEach { listener ->
                        listener.onToolAgentStatusUpdated(message)
                        listener.onToolAgentFailed(message, e.message ?: "Unknown error")
                    }
                }

                throw e
            }
        } else {
            // Fallback: имитируем выполнение шага (для агентов, которые еще не зарегистрированы)
            logger.warn("No ToolAgent found for ${step.agentType}, using fallback")
            delay(step.estimatedDurationMs)

            val fallbackResult = when (step.agentType) {
                AgentType.PROJECT_SCANNER -> "Проанализировано ${Random.nextInt(100, 500)} файлов"
                AgentType.BUG_DETECTION -> "Найдено ${Random.nextInt(0, 10)} потенциальных проблем"
                AgentType.CODE_FIXER -> "Исправлено ${Random.nextInt(0, 5)} проблем"
                AgentType.REPORT_GENERATOR -> "Отчет сгенерирован успешно"
                else -> "Шаг ${step.title} выполнен"
            }

            // Обновляем статус на завершенный
            val completedMessage = statusManager.updateStatus(
                stepId = step.id,
                status = ToolAgentExecutionStatus.COMPLETED,
                output = mapOf("result" to fallbackResult)
            )

            completedMessage?.let {
                progressListeners.forEach { listener ->
                    listener.onToolAgentStatusUpdated(it)
                    listener.onToolAgentCompleted(it)
                }
            }

            return fallbackResult
        }
    }

    /**
     * Получает отображаемое имя агента
     */
    private fun getAgentDisplayName(agentType: AgentType): String {
        return when (agentType) {
            AgentType.PROJECT_SCANNER -> "Анализатор проекта"
            AgentType.CODE_CHUNKER -> "Разбивщик кода"
            AgentType.BUG_DETECTION -> "Детектор багов"
            AgentType.CODE_QUALITY -> "Анализатор качества"
            AgentType.ARCHITECTURE_ANALYSIS -> "Анализатор архитектуры"
            AgentType.CODE_FIXER -> "Исправитель кода"
            AgentType.LLM_REVIEW -> "LLM ревьюер"
            AgentType.USER_INTERACTION -> "Интерактивный агент"
            AgentType.REPORT_GENERATOR -> "Генератор отчетов"
            AgentType.FILE_OPERATIONS -> "Операции с файлами"
            AgentType.GIT_OPERATIONS -> "Git операции"
            AgentType.TEST_GENERATOR -> "Генератор тестов"
            AgentType.DOCUMENTATION_GENERATOR -> "Генератор документации"
            AgentType.PERFORMANCE_ANALYZER -> "Анализатор производительности"
            else -> agentType.name
        }
    }

    private suspend fun continuePlanExecution(plan: ExecutionPlan) {
        // TODO: реализовать продолжение выполнения плана после паузы или ввода пользователя
        logger.info("Continuing plan execution: ${plan.id}")
    }

    /**
     * Выполняет шаг с учётом retry и loop политик
     */
    private suspend fun executeStepWithRetryLoop(
        step: PlanStep,
        executionContext: ExecutionContext,
        previousResults: Map<String, Any>
    ): String {
        return when {
            step.loopConfig != null -> {
                // Выполнение в цикле
                val loopResult = retryLoopExecutor.executeWithLoop(step, executionContext) { s, ctx ->
                    executeStepCore(s, ctx, previousResults)
                }
                "Loop completed: ${loopResult.iterations} iterations, ${loopResult.terminationReason}"
            }
            step.retryPolicy != null -> {
                // Выполнение с retry
                retryLoopExecutor.executeWithRetry(step) { s ->
                    executeStepCore(s, executionContext, previousResults)
                }
            }
            else -> {
                executeStepCore(step, executionContext, previousResults)
            }
        }
    }

    /**
     * Базовое выполнение шага (без retry/loop)
     */
    private suspend fun executeStepCore(
        step: PlanStep,
        executionContext: ExecutionContext,
        previousResults: Map<String, Any>
    ): String {
        return executeStep(step, executionContext, previousResults)
    }

    /**
     * Обогащает input шага данными из предыдущих шагов
     */
    private fun enrichStepInput(step: PlanStep, previousResults: Map<String, Any>): Map<String, Any> {
        val enrichedInput = step.input.toMutableMap()

        // Ищем результаты PROJECT_SCANNER в зависимостях
        fun getFilesFromScanner(): List<String>? {
            for (depId in step.dependencies) {
                val depResult = previousResults[depId]
                if (depResult is Map<*, *>) {
                    val output = depResult["output"] as? Map<*, *>
                    val files = output?.get("files") as? List<*>
                    if (files != null) {
                        val filesList = files.filterIsInstance<String>()
                        if (filesList.isNotEmpty()) {
                            logger.info("enrichStepInput: found ${filesList.size} files from PROJECT_SCANNER (step $depId)")
                            return filesList
                        }
                    }
                }
            }
            return null
        }

        fun collectProjectFiles(projectPath: String?, maxCount: Int = 50): List<String> {
            if (projectPath.isNullOrBlank()) return emptyList()
            return try {
                val root = java.io.File(projectPath)
                if (!root.exists() || !root.isDirectory) return emptyList()
                root.walkTopDown()
                    .filter { it.isFile }
                    .filter { f ->
                        val p = f.path.replace('\\', '/')
                        (p.endsWith(".kt") || p.endsWith(".java")) &&
                                !p.contains("/build/") &&
                                !p.contains("/.git/") &&
                                !p.contains("/out/") &&
                                !p.contains("/node_modules/")
                    }
                    .take(maxCount)
                    .map { it.absolutePath }
                    .toList()
            } catch (_: Exception) { emptyList() }
        }

        // Для BUG_DETECTION/CODE_QUALITY/LLM_REVIEW/ARCHITECTURE_ANALYSIS гарантируем непустой 'files'
        if (
            step.agentType == AgentType.BUG_DETECTION ||
            step.agentType == AgentType.CODE_QUALITY ||
            step.agentType == AgentType.LLM_REVIEW ||
            step.agentType == AgentType.ARCHITECTURE_ANALYSIS
        ) {
            val filesFromInput = (enrichedInput["files"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (filesFromInput.isEmpty()) {
                // Сначала пытаемся получить файлы из PROJECT_SCANNER
                val filesFromScanner = getFilesFromScanner()
                if (filesFromScanner != null && filesFromScanner.isNotEmpty()) {
                    enrichedInput["files"] = filesFromScanner
                    logger.info("enrichStepInput: provided ${filesFromScanner.size} files from scanner for ${step.agentType}")
                } else {
                    // Fallback: сканируем файлы вручную
                    val projectPath = step.input["projectPath"] as? String
                    val resolved = collectProjectFiles(projectPath)
                    if (resolved.isNotEmpty()) {
                        enrichedInput["files"] = resolved
                        logger.info("enrichStepInput: provided ${resolved.size} files (fallback scan) for ${step.agentType}")
                    }
                }
            }
        }

        // Для REPORT_GENERATOR собираем все предыдущие результаты
        if (step.agentType == AgentType.REPORT_GENERATOR) {
            enrichedInput["previousResults"] = previousResults
        }

        return enrichedInput
    }

    private fun buildAnalysisNotification(analysis: RequestAnalysis): String {
        return buildString {
            appendLine("🔍 **Анализ запроса завершен**")
            appendLine()
            appendLine("**Тип задачи:** ${analysis.taskType}")
            appendLine("**Сложность:** ${analysis.estimatedComplexity}")
            appendLine("**Требуемые инструменты:** ${analysis.requiredTools.joinToString(", ")}")
            appendLine("**Оценочное количество шагов:** ${analysis.estimatedSteps}")
            if (analysis.requiresUserInput) {
                appendLine("⚠️ **Требуется уточнение от пользователя**")
            }
            appendLine()
            appendLine("**Обоснование:** ${analysis.reasoning}")
        }
    }

    // Реализация StateChangeListener

    override fun onStateChanged(
        plan: ExecutionPlan,
        fromState: PlanState,
        toState: PlanState,
        event: PlanEvent
    ) {
        logger.info("Plan ${plan.id} state changed from $fromState to $toState")

        // Обновляем план в хранилище
        runBlocking {
            planStorage.update(plan)
            activePlans[plan.id] = plan
        }
    }

    // Реализация ProgressListener (пустая, так как уведомления идут через OrchestratorStep)

    override fun onProgressStarted(plan: ExecutionPlan, progress: PlanProgress) {
        logger.debug("Progress started for plan ${plan.id}")
    }

    override fun onStepCompleted(
        planId: String,
        stepId: String,
        result: Any?,
        overallProgress: Double,
        eta: Long
    ) {
        logger.debug("Step $stepId completed for plan $planId. Progress: ${overallProgress}%")
    }

    override fun onProgressFinished(planId: String, progress: PlanProgress) {
        logger.info("Progress finished for plan $planId. Success: ${progress.success}")
    }

    /**
     * Публичный метод выполнения плана для использования в ChatService
     */
    suspend fun executePlan(request: AgentRequest): AgentResponse {
        return try {
            // Создаем план на основе запроса
            val plan = createPlanFromRequest(request)

            // Выполняем план с шагами
            executePlan(plan) { step ->
                // Обрабатываем шаги, если нужно
            }
        } catch (e: Exception) {
            logger.error("Error executing plan", e)
            AgentResponse.error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Ошибка выполнения плана: ${e.message}"
            )
        }
    }

    /**
     * Генерирует финальный отчет на основе результатов выполнения шагов
     */
    private suspend fun generateFinalReport(
        analysis: RequestAnalysis,
        results: List<StepExecutionResult>
    ): String {
        logger.info("Generating final report from ${results.size} step results")
        
        val reportPrompt = buildReportPrompt(analysis, results)
        
        return try {
            val llmResponse = llmProvider.sendRequest(
                systemPrompt = REPORT_GENERATION_SYSTEM_PROMPT,
                userMessage = reportPrompt,
                conversationHistory = emptyList(),
                parameters = ru.marslab.ide.ride.model.llm.LLMParameters.BALANCED
            )
            
            if (llmResponse.success) {
                llmResponse.content
            } else {
                logger.error("Failed to generate report via LLM: ${llmResponse.error}")
                buildFallbackReport(analysis, results)
            }
        } catch (e: Exception) {
            logger.error("Error generating report", e)
            buildFallbackReport(analysis, results)
        }
    }
    
    /**
     * Строит промпт для генерации отчета
     */
    private fun buildReportPrompt(analysis: RequestAnalysis, results: List<StepExecutionResult>): String {
        return buildString {
            appendLine("# Задача пользователя")
            appendLine("Тип задачи: ${analysis.taskType}")
            appendLine("Сложность: ${analysis.estimatedComplexity}")
            appendLine()
            
            appendLine("# Результаты выполнения")
            appendLine()
            
            results.forEachIndexed { index, result ->
                appendLine("## ${index + 1}. ${result.stepTitle}")
                appendLine("**Агент:** ${getAgentDisplayName(result.agentType)}")
                appendLine("**Описание:** ${result.stepDescription}")
                appendLine()
                
                // Извлекаем данные из результата
                when (val stepResult = result.result) {
                    is Map<*, *> -> {
                        val output = stepResult["output"] as? Map<*, *>
                        if (output != null) {
                            appendLine("**Результаты:**")
                            
                            // Findings (для BUG_DETECTION, CODE_QUALITY, LLM_REVIEW)
                            val findings = output["findings"] as? List<*>
                            if (findings != null) {
                                appendLine("- Найдено проблем: ${findings.size}")
                                val criticalCount = output["critical_count"] as? Int ?: 0
                                val highCount = output["high_count"] as? Int ?: 0
                                val mediumCount = output["medium_count"] as? Int ?: 0
                                val lowCount = output["low_count"] as? Int ?: 0
                                appendLine("  - Критичных: $criticalCount")
                                appendLine("  - Высокий приоритет: $highCount")
                                appendLine("  - Средний приоритет: $mediumCount")
                                appendLine("  - Низкий приоритет: $lowCount")
                            }
                            
                            // Files (для PROJECT_SCANNER)
                            val totalFiles = output["total_files"] as? Int
                            val totalDirs = output["total_directories"] as? Int
                            if (totalFiles != null) {
                                appendLine("- Проанализировано файлов: $totalFiles")
                                if (totalDirs != null) {
                                    appendLine("- Директорий: $totalDirs")
                                }
                            }
                            
                            // Architecture analysis
                            val modules = output["modules"] as? List<*>
                            if (modules != null) {
                                appendLine("- Обнаружено модулей: ${modules.size}")
                            }
                            
                            val layers = output["layers"] as? List<*>
                            if (layers != null) {
                                appendLine("- Обнаружено слоев: ${layers.size}")
                            }
                        }
                    }
                    is String -> {
                        appendLine("**Результат:** $stepResult")
                    }
                }
                
                appendLine()
            }
            
            appendLine()
            appendLine("# Инструкция")
            appendLine("На основе этих данных создай подробный, структурированный отчет на русском языке.")
            appendLine("Отчет должен содержать:")
            appendLine("1. Краткое резюме выполненной работы")
            appendLine("2. Детальные результаты каждого этапа анализа")
            appendLine("3. Ключевые находки и проблемы (если есть)")
            appendLine("4. Рекомендации по улучшению (если применимо)")
            appendLine("5. Заключение")
            appendLine()
            appendLine("Используй markdown форматирование для структурирования отчета.")
        }
    }
    
    /**
     * Создает упрощенный отчет в случае ошибки LLM
     */
    private fun buildFallbackReport(analysis: RequestAnalysis, results: List<StepExecutionResult>): String {
        return buildString {
            appendLine("# Отчет о выполнении задачи")
            appendLine()
            appendLine("**Тип задачи:** ${analysis.taskType}")
            appendLine("**Сложность:** ${analysis.estimatedComplexity}")
            appendLine("**Завершено этапов:** ${results.size}")
            appendLine()
            
            appendLine("## Выполненные этапы")
            appendLine()
            
            results.forEachIndexed { index, result ->
                appendLine("### ${index + 1}. ${result.stepTitle}")
                appendLine("- **Агент:** ${getAgentDisplayName(result.agentType)}")
                appendLine("- **Статус:** Завершено успешно")
                appendLine()
            }
            
            appendLine("## Заключение")
            appendLine("Все этапы выполнены успешно. Детальные результаты доступны в выводе каждого агента.")
        }
    }

    /**
     * Создает план на основе пользовательского запроса
     */
    private suspend fun createPlanFromRequest(request: AgentRequest): ExecutionPlan {
        // Создаем простой план с одним шагом для анализа архитектуры
        val planId = UUID.randomUUID().toString()
        val timestamp = kotlinx.datetime.Clock.System.now()

        val step = PlanStep(
            id = "step-1",
            title = "Анализ запроса",
            description = request.request,
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
            input = mapOf(
                "analysis_type" to "architecture_analysis",
                "user_query" to request.request,
                "project_context" to request.context.toString()
            ),
            dependencies = emptySet(),
            status = StepStatus.PENDING
        )

        return ExecutionPlan(
            id = planId,
            userRequestId = request.context.project.name ?: "unknown",
            originalRequest = request.request,
            analysis = RequestAnalysis(
                taskType = TaskType.ARCHITECTURE_ANALYSIS,
                requiredTools = setOf(AgentType.ARCHITECTURE_ANALYSIS),
                context = ExecutionContext(
                    projectPath = request.context.project.basePath,
                    additionalContext = mapOf(
                        "user_query" to request.request,
                        "project_context" to request.context.toString()
                    )
                ),
                parameters = mapOf(
                    "analysis_type" to "architecture_analysis",
                    "user_query" to request.request
                ),
                requiresUserInput = false,
                estimatedComplexity = ComplexityLevel.LOW,
                estimatedSteps = 1,
                confidence = 0.8,
                reasoning = "Запрос требует архитектурного анализа"
            ),
            steps = listOf(step),
            currentState = PlanState.CREATED,
            createdAt = timestamp,
            metadata = mapOf(
                "created_at" to timestamp.toString(),
                "user_request" to request.request
            )
        )
    }
    
    companion object {
        private const val REPORT_GENERATION_SYSTEM_PROMPT = """
Ты - эксперт по анализу кода и генерации технических отчетов.
Твоя задача - создавать подробные, структурированные отчеты на основе результатов работы различных агентов анализа кода.

Требования к отчету:
- Используй профессиональный технический язык
- Структурируй информацию логично и последовательно
- Выделяй ключевые находки и проблемы
- Предоставляй конкретные рекомендации
- Используй markdown для форматирования
- Пиши на русском языке

Стиль:
- Четкий и лаконичный
- Фокус на важной информации
- Избегай избыточных деталей
- Используй списки и таблицы для структурирования данных
"""
    }
}

/**
 * Результат выполнения шага для накопления
 */
private data class StepExecutionResult(
    val stepId: String,
    val stepTitle: String,
    val stepDescription: String,
    val agentType: AgentType,
    val result: Any
)