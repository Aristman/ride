package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ru.marslab.ide.ride.agent.ToolAgentRegistry
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.orchestrator.ExecutionPlan
import ru.marslab.ide.ride.model.orchestrator.PlanStep
import ru.marslab.ide.ride.model.orchestrator.StepStatus
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.StepOutput
import ru.marslab.ide.ride.model.tool.ToolPlanStep

/**
 * Executor для параллельного выполнения планов
 *
 * Использует граф зависимостей для определения порядка выполнения
 * и выполняет независимые шаги параллельно
 */
class ParallelPlanExecutor(
    private val toolAgentRegistry: ToolAgentRegistry,
    private val progressTracker: ProgressTracker,
    private val maxParallelTasks: Int = 5
) {
    private val logger = Logger.getInstance(ParallelPlanExecutor::class.java)

    /**
     * Выполняет план с параллельным выполнением независимых шагов
     */
    suspend fun executePlan(
        plan: ExecutionPlan,
        context: ExecutionContext,
        onStepComplete: suspend (String, Any) -> Unit = { _, _ -> }
    ): ExecutionResult {
        logger.info("Starting parallel execution of plan ${plan.id}")

        // Строим граф зависимостей
        val dependencyGraph = DependencyGraph(plan.steps)

        // Проверяем на циклические зависимости
        if (dependencyGraph.hasCycles()) {
            return ExecutionResult.error("Circular dependency detected in plan")
        }

        // Получаем порядок выполнения (батчи для параллельного выполнения)
        val executionOrder = dependencyGraph.topologicalSort()
        logger.info("Execution order: ${executionOrder.size} batches")

        progressTracker.startTracking(plan)

        val stepResults = mutableMapOf<String, Any>()
        val startTime = System.currentTimeMillis()

        try {
            for ((batchIndex, batch) in executionOrder.withIndex()) {
                logger.info("Executing batch ${batchIndex + 1}/${executionOrder.size}: ${batch.size} steps")

                // Выполняем батч параллельно
                val batchResults = executeBatch(batch, plan, context, stepResults)

                // Сохраняем результаты
                batchResults.forEach { (stepId, result) ->
                    stepResults[stepId] = result
                    onStepComplete(stepId, result)
                }

                // Проверяем на ошибки
                val failedStep = plan.steps.find { it.id in batch && it.status == StepStatus.FAILED }
                if (failedStep != null) {
                    logger.error("Step ${failedStep.id} failed: ${failedStep.error}")
                    progressTracker.finishTracking(plan.id, false)
                    return ExecutionResult.error(failedStep.error ?: "Step execution failed")
                }
            }

            val totalTime = System.currentTimeMillis() - startTime
            logger.info("Plan execution completed in ${totalTime}ms")

            progressTracker.finishTracking(plan.id, true)

            return ExecutionResult.success(
                StepOutput.of(
                    "completed_steps" to plan.steps.size,
                    "total_time_ms" to totalTime,
                    "batches" to executionOrder.size
                )
            )

        } catch (e: Exception) {
            logger.error("Error executing plan", e)
            progressTracker.finishTracking(plan.id, false)
            return ExecutionResult.error(e.message ?: "Unknown error")
        }
    }

    /**
     * Выполняет батч шагов параллельно с ограничением на количество параллельных задач
     */
    private suspend fun executeBatch(
        batch: List<String>,
        plan: ExecutionPlan,
        context: ExecutionContext,
        previousResults: Map<String, Any>
    ): Map<String, Any> = coroutineScope {
        val results = mutableMapOf<String, Any>()

        // Разбиваем на чанки по maxParallelTasks
        batch.chunked(maxParallelTasks).forEach { chunk ->
            val chunkResults = chunk.map { stepId ->
                async {
                    val step = plan.steps.find { it.id == stepId }
                        ?: throw IllegalStateException("Step not found: $stepId")

                    logger.info("Executing step ${step.id}: ${step.title}")
                    val result = executeStep(step, context, previousResults, plan.id)
                    stepId to result
                }
            }.awaitAll()

            results.putAll(chunkResults)
        }

        results
    }

    /**
     * Выполняет один шаг
     */
    private suspend fun executeStep(
        step: PlanStep,
        context: ExecutionContext,
        previousResults: Map<String, Any>,
        planId: String = ""
    ): String {
        step.status = StepStatus.IN_PROGRESS
        if (planId.isNotEmpty()) {
            progressTracker.updateStepProgress(planId, step.id, 0.0)
        }

        return try {
            val agent = toolAgentRegistry.get(step.agentType)

            if (agent != null) {
                // Обогащаем input данными из зависимостей
                val enrichedInput = enrichStepInput(step, previousResults)

                val toolStep = ToolPlanStep(
                    id = step.id,
                    description = step.description,
                    agentType = step.agentType,
                    input = StepInput(enrichedInput),
                    dependencies = step.dependencies
                )

                val result = agent.executeStep(toolStep, context)

                if (result.success) {
                    step.status = StepStatus.COMPLETED
                    step.output = result.output.data
                    if (planId.isNotEmpty()) {
                        progressTracker.completeStep(planId, step.id, result.output.data.toString())
                    }
                    result.output.data.toString()
                } else {
                    step.status = StepStatus.FAILED
                    step.error = result.error
                    throw Exception(result.error ?: "Step execution failed")
                }
            } else {
                // Fallback
                logger.warn("No agent found for ${step.agentType}, using fallback")
                step.status = StepStatus.COMPLETED
                val result = "Step ${step.title} completed (fallback)"
                if (planId.isNotEmpty()) {
                    progressTracker.completeStep(planId, step.id, result)
                }
                result
            }

        } catch (e: Exception) {
            step.status = StepStatus.FAILED
            step.error = e.message
            logger.error("Step ${step.id} failed", e)
            throw e
        }
    }

    /**
     * Обогащает input шага данными из предыдущих шагов
     */
    private fun enrichStepInput(step: PlanStep, previousResults: Map<String, Any>): Map<String, Any> {
        val enrichedInput = step.input.toMutableMap()

        // Добавляем результаты зависимостей
        step.dependencies.forEach { depId ->
            val depResult = previousResults[depId]
            if (depResult != null) {
                enrichedInput["dependency_$depId"] = depResult
            }
        }

        return enrichedInput
    }
}

/**
 * Результат выполнения плана
 */
sealed class ExecutionResult {
    data class Success(val output: StepOutput) : ExecutionResult()
    data class Error(val error: String) : ExecutionResult()

    val success: Boolean get() = this is Success
    val errorMessage: String? get() = (this as? Error)?.error

    companion object {
        fun success(output: StepOutput = StepOutput.empty()) = Success(output)
        fun error(error: String) = Error(error)
    }
}
