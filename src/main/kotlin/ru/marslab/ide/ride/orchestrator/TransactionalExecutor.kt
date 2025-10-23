package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.orchestrator.PlanStep
import ru.marslab.ide.ride.model.tool.StepResult

/**
 * Интерфейс для транзакционных шагов с поддержкой отката
 */
interface TransactionalStep {
    suspend fun execute(context: ExecutionContext): StepResult
    suspend fun rollback(context: ExecutionContext)
}

/**
 * Executor для выполнения шагов с поддержкой транзакционности и откатов
 *
 * При ошибке выполнения автоматически откатывает все выполненные шаги
 */
class TransactionalExecutor {
    private val logger = Logger.getInstance(TransactionalExecutor::class.java)
    private val executedSteps = mutableListOf<Pair<PlanStep, TransactionalStep>>()

    /**
     * Выполняет шаги с поддержкой отката при ошибке
     */
    suspend fun executeWithRollback(
        steps: List<PlanStep>,
        context: ExecutionContext,
        stepFactory: (PlanStep) -> TransactionalStep
    ): ExecutionResult {
        logger.info("Starting transactional execution of ${steps.size} steps")

        try {
            for (step in steps) {
                logger.info("Executing transactional step: ${step.id}")

                val transactionalStep = stepFactory(step)
                val result = transactionalStep.execute(context)

                if (!result.success) {
                    logger.warn("Step ${step.id} failed: ${result.error}")
                    // Откатываем все выполненные шаги
                    rollbackAll(context)
                    return ExecutionResult.error(result.error ?: "Step execution failed")
                }

                executedSteps.add(step to transactionalStep)
                logger.info("Step ${step.id} completed successfully")
            }

            logger.info("All ${steps.size} steps completed successfully")
            executedSteps.clear() // Очищаем после успешного выполнения

            return ExecutionResult.success()

        } catch (e: Exception) {
            logger.error("Exception during transactional execution", e)
            rollbackAll(context)
            return ExecutionResult.error(e.message ?: "Unknown error")
        }
    }

    /**
     * Откатывает все выполненные шаги в обратном порядке
     */
    private suspend fun rollbackAll(context: ExecutionContext) {
        logger.warn("Rolling back ${executedSteps.size} steps")

        // Откатываем в обратном порядке
        executedSteps.reversed().forEach { (step, transactionalStep) ->
            try {
                logger.info("Rolling back step: ${step.id}")
                transactionalStep.rollback(context)
                logger.info("Successfully rolled back step: ${step.id}")
            } catch (e: Exception) {
                logger.error("Failed to rollback step ${step.id}", e)
                // Продолжаем откат остальных шагов даже при ошибке
            }
        }

        executedSteps.clear()
        logger.info("Rollback completed")
    }

    /**
     * Очищает историю выполненных шагов
     */
    fun clear() {
        executedSteps.clear()
    }
}

/**
 * Базовая реализация транзакционного шага
 */
abstract class BaseTransactionalStep : TransactionalStep {
    protected val logger = Logger.getInstance(this::class.java)

    /**
     * Состояние до выполнения (для отката)
     */
    protected var previousState: Any? = null

    override suspend fun execute(context: ExecutionContext): StepResult {
        // Сохраняем состояние перед выполнением
        previousState = captureState(context)

        // Выполняем шаг
        return doExecute(context)
    }

    override suspend fun rollback(context: ExecutionContext) {
        if (previousState != null) {
            restoreState(context, previousState!!)
        }
    }

    /**
     * Захватывает текущее состояние для возможного отката
     */
    protected abstract fun captureState(context: ExecutionContext): Any?

    /**
     * Выполняет основную логику шага
     */
    protected abstract suspend fun doExecute(context: ExecutionContext): StepResult

    /**
     * Восстанавливает состояние при откате
     */
    protected abstract fun restoreState(context: ExecutionContext, state: Any)
}
