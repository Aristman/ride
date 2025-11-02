package ru.marslab.ide.ride.agent

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.tool.*

/**
 * Условный шаг плана выполнения
 */
data class ConditionalStep(
    val id: String,
    val description: String,
    val condition: (ExecutionContext, Map<String, StepOutput>) -> Boolean,
    val thenStep: PlanStep,
    val elseStep: PlanStep? = null,
    val dependencies: Set<String> = emptySet()
)

/**
 * Модификатор для динамического изменения планов выполнения
 * 
 * Позволяет:
 * - Добавлять новые шаги в процессе выполнения
 * - Удалять шаги
 * - Заменять шаги
 * - Изменять порядок выполнения
 */
class DynamicPlanModifier {
    private val logger = Logger.getInstance(DynamicPlanModifier::class.java)
    
    /**
     * Добавляет шаг после указанного шага
     */
    fun addStepAfter(
        plan: ExecutionPlan,
        newStep: PlanStep,
        afterStepId: String
    ): ExecutionPlan {
        logger.info("Adding step ${newStep.id} after step $afterStepId")
        
        val steps = plan.steps.toMutableList()
        val index = steps.indexOfFirst { it.id == afterStepId }
        
        if (index < 0) {
            logger.warn("Step $afterStepId not found, adding to the end")
            steps.add(newStep)
        } else {
            steps.add(index + 1, newStep)
        }
        
        return plan.copy(
            steps = steps,
            version = plan.version + 1
        )
    }
    
    /**
     * Добавляет шаг перед указанным шагом
     */
    fun addStepBefore(
        plan: ExecutionPlan,
        newStep: PlanStep,
        beforeStepId: String
    ): ExecutionPlan {
        logger.info("Adding step ${newStep.id} before step $beforeStepId")
        
        val steps = plan.steps.toMutableList()
        val index = steps.indexOfFirst { it.id == beforeStepId }
        
        if (index < 0) {
            logger.warn("Step $beforeStepId not found, adding to the beginning")
            steps.add(0, newStep)
        } else {
            steps.add(index, newStep)
        }
        
        return plan.copy(
            steps = steps,
            version = plan.version + 1
        )
    }
    
    /**
     * Удаляет шаг из плана
     */
    fun removeStep(
        plan: ExecutionPlan,
        stepId: String
    ): ExecutionPlan {
        logger.info("Removing step $stepId")
        
        val steps = plan.steps.filter { it.id != stepId }
        
        if (steps.size == plan.steps.size) {
            logger.warn("Step $stepId not found")
        }
        
        return plan.copy(
            steps = steps,
            version = plan.version + 1
        )
    }
    
    /**
     * Заменяет шаг новым шагом
     */
    fun replaceStep(
        plan: ExecutionPlan,
        stepId: String,
        newStep: PlanStep
    ): ExecutionPlan {
        logger.info("Replacing step $stepId with ${newStep.id}")
        
        val steps = plan.steps.map { step ->
            if (step.id == stepId) newStep else step
        }
        
        return plan.copy(
            steps = steps,
            version = plan.version + 1
        )
    }
    
    /**
     * Добавляет несколько шагов после указанного шага
     */
    fun addStepsAfter(
        plan: ExecutionPlan,
        newSteps: List<PlanStep>,
        afterStepId: String
    ): ExecutionPlan {
        logger.info("Adding ${newSteps.size} steps after step $afterStepId")
        
        val steps = plan.steps.toMutableList()
        val index = steps.indexOfFirst { it.id == afterStepId }
        
        if (index < 0) {
            logger.warn("Step $afterStepId not found, adding to the end")
            steps.addAll(newSteps)
        } else {
            steps.addAll(index + 1, newSteps)
        }
        
        return plan.copy(
            steps = steps,
            version = plan.version + 1
        )
    }
    
    /**
     * Изменяет порядок шагов
     */
    fun reorderSteps(
        plan: ExecutionPlan,
        newOrder: List<String>
    ): ExecutionPlan {
        logger.info("Reordering steps")
        
        val stepMap = plan.steps.associateBy { it.id }
        val reorderedSteps = newOrder.mapNotNull { stepMap[it] }
        
        // Добавляем шаги, которых нет в новом порядке, в конец
        val missingSteps = plan.steps.filter { it.id !in newOrder }
        
        return plan.copy(
            steps = reorderedSteps + missingSteps,
            version = plan.version + 1
        )
    }
    
    /**
     * Пропускает шаг (устанавливает статус SKIPPED)
     */
    fun skipStep(
        plan: ExecutionPlan,
        stepId: String,
        reason: String? = null
    ): ExecutionPlan {
        logger.info("Skipping step $stepId${reason?.let { ": $it" } ?: ""}")
        
        return plan.updateStepStatus(
            stepId = stepId,
            status = StepStatus.SKIPPED,
            error = reason
        )
    }
    
    /**
     * Изменяет зависимости шага
     */
    fun updateDependencies(
        plan: ExecutionPlan,
        stepId: String,
        newDependencies: Set<String>
    ): ExecutionPlan {
        logger.info("Updating dependencies for step $stepId")
        
        val steps = plan.steps.map { step ->
            if (step.id == stepId) {
                step.copy(dependencies = newDependencies)
            } else {
                step
            }
        }
        
        return plan.copy(
            steps = steps,
            version = plan.version + 1
        )
    }
    
    /**
     * Добавляет метаданные к плану
     */
    fun addMetadata(
        plan: ExecutionPlan,
        key: String,
        value: Any
    ): ExecutionPlan {
        logger.info("Adding metadata: $key")
        
        val newMetadata = plan.metadata.toMutableMap()
        newMetadata[key] = value
        
        return plan.copy(
            metadata = newMetadata,
            version = plan.version + 1
        )
    }
    
    /**
     * Клонирует план с новым ID
     */
    fun clonePlan(
        plan: ExecutionPlan,
        newUserRequestId: String? = null
    ): ExecutionPlan {
        logger.info("Cloning plan ${plan.id}")
        
        return plan.copy(
            id = java.util.UUID.randomUUID().toString(),
            userRequestId = newUserRequestId ?: plan.userRequestId,
            createdAt = kotlinx.datetime.Clock.System.now(),
            startedAt = null,
            completedAt = null,
            currentState = PlanState.CREATED,
            version = 1
        )
    }
}

/**
 * Исполнитель условных шагов
 */
class ConditionalStepExecutor(
    private val toolAgentRegistry: ToolAgentRegistry
) {
    private val logger = Logger.getInstance(ConditionalStepExecutor::class.java)
    
    /**
     * Выполняет условный шаг
     */
    suspend fun executeConditionalStep(
        conditional: ConditionalStep,
        context: ExecutionContext,
        stepResults: Map<String, StepOutput>
    ): Pair<PlanStep, StepResult> {
        logger.info("Evaluating conditional step: ${conditional.id}")

        // Проверяем условие
        val conditionResult = try {
            conditional.condition(context, stepResults)
        } catch (e: Exception) {
            logger.error("Error evaluating condition for step ${conditional.id}", e)
            false
        }

        // Выбираем шаг для выполнения
        val stepToExecute = if (conditionResult) {
            logger.info("Condition is true, executing 'then' step")
            conditional.thenStep
        } else {
            logger.info("Condition is false, executing 'else' step")
            conditional.elseStep ?: run {
                logger.info("No 'else' step defined, skipping")
                return conditional.thenStep to StepResult.success(
                    output = StepOutput.of("skipped" to true, "reason" to "Condition not met"),
                    metadata = mapOf("conditional_result" to false)
                )
            }
        }

        // Возвращаем выбранный шаг без выполнения (выполнение будет в оркестраторе)
        return stepToExecute to StepResult.success(
            output = StepOutput.of("conditional_selected" to conditionResult),
            metadata = mapOf("conditional_result" to conditionResult)
        )
    }
    
    /**
     * Создает условный шаг из конфигурации
     */
    fun createConditionalStep(
        id: String,
        description: String,
        conditionExpression: String,
        thenStep: PlanStep,
        elseStep: PlanStep? = null,
        dependencies: Set<String> = emptySet()
    ): ConditionalStep {
        // Простой парсер условий
        val condition: (ExecutionContext, Map<String, StepOutput>) -> Boolean = { ctx, results ->
            evaluateCondition(conditionExpression, ctx, results)
        }

        return ConditionalStep(
            id = id,
            description = description,
            condition = condition,
            thenStep = thenStep,
            elseStep = elseStep,
            dependencies = dependencies
        )
    }
    
    /**
     * Вычисляет условие на основе выражения
     */
    private fun evaluateCondition(
        expression: String,
        context: ExecutionContext,
        results: Map<String, StepOutput>
    ): Boolean {
        // Простая реализация для базовых условий
        // Формат: "step_id.output_key operator value"
        // Пример: "analyze.findings.size > 0"
        
        return try {
            when {
                expression.contains("has_critical_findings") -> {
                    val stepId = expression.substringBefore(".").trim()
                    val output = results[stepId]
                    val findings = output?.get<List<Finding>>("findings") ?: emptyList()
                    findings.any { it.severity == Severity.CRITICAL }
                }
                expression.contains("has_errors") -> {
                    val stepId = expression.substringBefore(".").trim()
                    val output = results[stepId]
                    val error = output?.get<String>("error")
                    !error.isNullOrBlank()
                }
                expression.contains("success") -> {
                    val stepId = expression.substringBefore(".").trim()
                    val output = results[stepId]
                    output?.get<Boolean>("success") ?: false
                }
                else -> {
                    logger.warn("Unknown condition expression: $expression, defaulting to false")
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Error evaluating condition: $expression", e)
            false
        }
    }
}
