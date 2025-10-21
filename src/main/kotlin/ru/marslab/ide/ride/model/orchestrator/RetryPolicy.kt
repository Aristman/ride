package ru.marslab.ide.ride.model.orchestrator

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Политика повторных попыток для шагов плана
 */
data class RetryPolicy(
    /**
     * Максимальное количество попыток
     */
    val maxAttempts: Int = 3,
    
    /**
     * Стратегия задержки между попытками
     */
    val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,
    
    /**
     * Начальная задержка
     */
    val initialDelay: Duration = 1.seconds,
    
    /**
     * Максимальная задержка
     */
    val maxDelay: Duration = 30.seconds,
    
    /**
     * Множитель для экспоненциального backoff
     */
    val multiplier: Double = 2.0,
    
    /**
     * Типы ошибок, при которых нужно повторять
     */
    val retryableErrors: Set<String> = setOf(
        "timeout",
        "network_error",
        "temporary_failure",
        "rate_limit"
    ),
    
    /**
     * Функция для определения, нужно ли повторять при данной ошибке
     */
    val shouldRetry: ((String) -> Boolean)? = null
) {
    /**
     * Вычисляет задержку для указанной попытки
     */
    fun getDelay(attempt: Int): Duration {
        return when (backoffStrategy) {
            BackoffStrategy.FIXED -> initialDelay
            BackoffStrategy.LINEAR -> {
                val delay = initialDelay * attempt
                minOf(delay, maxDelay)
            }
            BackoffStrategy.EXPONENTIAL -> {
                val delay = initialDelay * Math.pow(multiplier, (attempt - 1).toDouble())
                minOf(Duration.parse("${delay}s"), maxDelay)
            }
        }
    }
    
    /**
     * Проверяет, нужно ли повторять при данной ошибке
     */
    fun shouldRetryError(error: String): Boolean {
        return shouldRetry?.invoke(error) 
            ?: retryableErrors.any { error.contains(it, ignoreCase = true) }
    }
    
    companion object {
        /**
         * Политика по умолчанию: 3 попытки с экспоненциальным backoff
         */
        val DEFAULT = RetryPolicy()
        
        /**
         * Агрессивная политика: 5 попыток с коротким backoff
         */
        val AGGRESSIVE = RetryPolicy(
            maxAttempts = 5,
            initialDelay = 0.5.seconds,
            multiplier = 1.5
        )
        
        /**
         * Консервативная политика: 2 попытки с длинным backoff
         */
        val CONSERVATIVE = RetryPolicy(
            maxAttempts = 2,
            initialDelay = 5.seconds,
            multiplier = 3.0
        )
        
        /**
         * Без повторов
         */
        val NONE = RetryPolicy(maxAttempts = 1)
    }
}

/**
 * Стратегия задержки между попытками
 */
enum class BackoffStrategy {
    /**
     * Фиксированная задержка
     */
    FIXED,
    
    /**
     * Линейное увеличение
     */
    LINEAR,
    
    /**
     * Экспоненциальное увеличение
     */
    EXPONENTIAL
}

/**
 * Информация о попытке выполнения
 */
data class RetryAttempt(
    val attemptNumber: Int,
    val error: String?,
    val timestamp: kotlinx.datetime.Instant,
    val nextRetryAt: kotlinx.datetime.Instant?
)

/**
 * История повторных попыток для шага
 */
data class RetryHistory(
    val stepId: String,
    val attempts: List<RetryAttempt> = emptyList(),
    val policy: RetryPolicy = RetryPolicy.DEFAULT
) {
    val totalAttempts: Int get() = attempts.size
    val hasMoreAttempts: Boolean get() = totalAttempts < policy.maxAttempts
    val lastError: String? get() = attempts.lastOrNull()?.error
}
