package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.delay
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.tool.StepResult

/**
 * Executor для выполнения шагов с retry и loop логикой
 */
class RetryLoopExecutor {
    private val logger = Logger.getInstance(RetryLoopExecutor::class.java)

    /**
     * Выполняет шаг с учётом retry политики
     */
    suspend fun executeWithRetry(
        step: PlanStep,
        executor: suspend (PlanStep) -> String
    ): String {
        val policy = step.retryPolicy ?: return executor(step)
        
        var lastError: String? = null
        val attempts = mutableListOf<RetryAttempt>()
        
        for (attempt in 1..policy.maxAttempts) {
            try {
                logger.info("Executing step ${step.id}, attempt $attempt/${policy.maxAttempts}")
                
                val result = executor(step)
                
                // Успешное выполнение
                if (attempt > 1) {
                    logger.info("Step ${step.id} succeeded on attempt $attempt")
                }
                
                // Обновляем историю
                step.retryHistory = RetryHistory(
                    stepId = step.id,
                    attempts = attempts + RetryAttempt(
                        attemptNumber = attempt,
                        error = null,
                        timestamp = kotlinx.datetime.Clock.System.now(),
                        nextRetryAt = null
                    ),
                    policy = policy
                )
                
                return result
                
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                logger.warn("Step ${step.id} failed on attempt $attempt: $lastError")
                
                // Проверяем, нужно ли повторять
                if (attempt < policy.maxAttempts && policy.shouldRetryError(lastError)) {
                    val delay = policy.getDelay(attempt)
                    val nextRetryAt = kotlinx.datetime.Clock.System.now() + delay
                    
                    attempts.add(RetryAttempt(
                        attemptNumber = attempt,
                        error = lastError,
                        timestamp = kotlinx.datetime.Clock.System.now(),
                        nextRetryAt = nextRetryAt
                    ))
                    
                    logger.info("Retrying step ${step.id} after ${delay.inWholeSeconds}s")
                    delay(delay.inWholeMilliseconds)
                } else {
                    // Не повторяем
                    attempts.add(RetryAttempt(
                        attemptNumber = attempt,
                        error = lastError,
                        timestamp = kotlinx.datetime.Clock.System.now(),
                        nextRetryAt = null
                    ))
                    break
                }
            }
        }
        
        // Все попытки исчерпаны
        step.retryHistory = RetryHistory(
            stepId = step.id,
            attempts = attempts,
            policy = policy
        )
        
        throw Exception("Step ${step.id} failed after ${attempts.size} attempts. Last error: $lastError")
    }

    /**
     * Выполняет шаг в цикле
     */
    suspend fun executeWithLoop(
        step: PlanStep,
        context: ExecutionContext,
        executor: suspend (PlanStep, ExecutionContext) -> String
    ): LoopResult {
        val loopConfig = step.loopConfig 
            ?: return LoopResult(
                iterations = 1,
                iterationResults = listOf(executor(step, context)),
                terminationReason = LoopTerminationReason.COLLECTION_EXHAUSTED,
                success = true
            )
        
        logger.info("Executing step ${step.id} with loop type ${loopConfig.type}")
        
        val results = mutableListOf<Any?>()
        var iteration = 0
        var terminationReason = LoopTerminationReason.MAX_ITERATIONS_REACHED
        
        when (loopConfig.type) {
            LoopType.WHILE -> {
                while (iteration < loopConfig.maxIterations) {
                    iteration++
                    
                    // Проверяем условие продолжения
                    val shouldContinue = loopConfig.continueCondition?.invoke(context, results.lastOrNull()) ?: true
                    if (!shouldContinue) {
                        terminationReason = LoopTerminationReason.CONDITION_FALSE
                        break
                    }
                    
                    val result = executor(step, context)
                    results.add(result)
                    
                    // Проверяем условие выхода
                    if (loopConfig.breakCondition?.invoke(context, result) == true) {
                        terminationReason = LoopTerminationReason.BREAK_CONDITION_MET
                        break
                    }
                }
            }
            
            LoopType.FOR_EACH -> {
                val collection = loopConfig.collection ?: emptyList()
                for ((index, item) in collection.withIndex()) {
                    iteration++
                    
                    // Добавляем элемент в контекст
                    val enrichedContext = context.copy(
                        additionalContext = context.additionalContext + mapOf(
                            (loopConfig.iteratorVariable ?: "item") to item,
                            "index" to index
                        )
                    )
                    
                    val result = executor(step, enrichedContext)
                    results.add(result)
                }
                terminationReason = LoopTerminationReason.COLLECTION_EXHAUSTED
            }
            
            LoopType.REPEAT -> {
                repeat(loopConfig.maxIterations) {
                    iteration++
                    val result = executor(step, context)
                    results.add(result)
                }
                terminationReason = LoopTerminationReason.MAX_ITERATIONS_REACHED
            }
            
            LoopType.UNTIL_SUCCESS -> {
                while (iteration < loopConfig.maxIterations) {
                    iteration++
                    
                    try {
                        val result = executor(step, context)
                        results.add(result)
                        
                        // Проверяем успешность
                        if (loopConfig.breakCondition?.invoke(context, result) == true) {
                            terminationReason = LoopTerminationReason.SUCCESS_ACHIEVED
                            break
                        }
                    } catch (e: Exception) {
                        results.add(e.message)
                        logger.warn("Loop iteration $iteration failed: ${e.message}")
                    }
                }
            }
        }
        
        logger.info("Loop completed: $iteration iterations, reason: $terminationReason")
        
        return LoopResult(
            iterations = iteration,
            iterationResults = results,
            terminationReason = terminationReason,
            success = terminationReason in setOf(
                LoopTerminationReason.COLLECTION_EXHAUSTED,
                LoopTerminationReason.SUCCESS_ACHIEVED,
                LoopTerminationReason.BREAK_CONDITION_MET
            )
        )
    }
}
