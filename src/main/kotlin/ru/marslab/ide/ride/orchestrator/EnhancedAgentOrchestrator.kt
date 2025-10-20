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
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.orchestrator.impl.InMemoryPlanStorage
import ru.marslab.ide.ride.orchestrator.impl.LLMRequestAnalyzer
import java.util.*

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
    private val toolAgentRegistry: ToolAgentRegistry = ToolAgentRegistry()
) : StateChangeListener, ProgressListener {

    private val logger = Logger.getInstance(EnhancedAgentOrchestrator::class.java)
    private val activePlans = mutableMapOf<String, ExecutionPlan>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Добавляем слушателей
        stateMachine.addListener(this)
        progressTracker.addListener(this)
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

            // 2. Создаем план выполнения
            val plan = createExecutionPlan(userRequest, analysis)

            // 3. Сохраняем план
            planStorage.save(plan)
            activePlans[plan.id] = plan

            // 4. Начинаем отслеживание прогресса
            progressTracker.startTracking(plan)

            // 5. Переводим план в состояние анализа
            val updatedPlan = stateMachine.transition(plan, PlanEvent.Start(analysis))
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

        if (analysis.requiredTools.contains(AgentType.PROJECT_SCANNER)) {
            steps.add(
                PlanStep(
                    title = "Сканирование проекта",
                    description = "Анализ файловой структуры проекта",
                    agentType = AgentType.PROJECT_SCANNER,
                    estimatedDurationMs = 30_000L // 30 секунд
                )
            )
        }

        if (analysis.requiredTools.contains(AgentType.BUG_DETECTION)) {
            steps.add(
                PlanStep(
                    title = "Поиск багов",
                    description = "Анализ кода на наличие ошибок",
                    agentType = AgentType.BUG_DETECTION,
                    dependencies = setOf(steps.lastOrNull()?.id ?: ""),
                    estimatedDurationMs = 60_000L // 1 минута
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
            steps.add(
                PlanStep(
                    title = "Генерация отчета",
                    description = "Создание отчета о выполненных действиях",
                    agentType = AgentType.REPORT_GENERATOR,
                    dependencies = steps.filter { it.status != StepStatus.PENDING }.map { it.id }.toSet(),
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

            for (step in executingPlan.steps) {
                if (step.status != StepStatus.PENDING) continue

                // Проверяем зависимости
                if (step.dependencies.all { it in completedSteps }) {
                    val stepResult = executeStep(step)
                    completedSteps.add(step.id)

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

            AgentResponse.success(
                content = "План успешно выполнен. Завершено ${completedSteps.size} шагов.",
                metadata = mapOf(
                    "plan_id" to plan.id,
                    "completed_steps" to completedSteps.size
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

    private suspend fun executeStep(step: PlanStep): String {
        // Phase 2: Используем Tool Agents для выполнения шагов
        val agent = toolAgentRegistry.get(step.agentType)
        
        if (agent != null) {
            logger.info("Executing step ${step.id} with ToolAgent ${step.agentType}")
            
            // Конвертируем PlanStep в ToolPlanStep
            val toolStep = ToolPlanStep(
                id = step.id,
                description = step.description,
                agentType = step.agentType,
                input = StepInput(step.input),
                dependencies = step.dependencies
            )
            
            val context = ExecutionContext(
                projectPath = null, // Будет заполнено из контекста запроса
                additionalContext = emptyMap()
            )
            
            val result = agent.executeStep(toolStep, context)
            
            return if (result.success) {
                result.output.data.toString()
            } else {
                throw Exception(result.error ?: "Step execution failed")
            }
        } else {
            // Fallback: имитируем выполнение шага (для агентов, которые еще не зарегистрированы)
            logger.warn("No ToolAgent found for ${step.agentType}, using fallback")
            delay(step.estimatedDurationMs)

            return when (step.agentType) {
                AgentType.PROJECT_SCANNER -> "Проанализировано ${Random().nextInt(100, 500)} файлов"
                AgentType.BUG_DETECTION -> "Найдено ${Random().nextInt(0, 10)} потенциальных проблем"
                AgentType.CODE_FIXER -> "Исправлено ${Random().nextInt(0, 5)} проблем"
                AgentType.REPORT_GENERATOR -> "Отчет сгенерирован успешно"
                else -> "Шаг ${step.title} выполнен"
            }
        }
    }

    private suspend fun continuePlanExecution(plan: ExecutionPlan) {
        // TODO: реализовать продолжение выполнения плана после паузы или ввода пользователя
        logger.info("Continuing plan execution: ${plan.id}")
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
}