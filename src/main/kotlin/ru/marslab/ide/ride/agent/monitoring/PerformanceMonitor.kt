package ru.marslab.ide.ride.agent.monitoring

import ru.marslab.ide.ride.agent.analyzer.ComplexityLevel
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.DoubleAdder
import kotlin.math.sqrt

/**
 * Монитор производительности для отслеживания метрик работы EnhancedChatAgent
 *
 * Собирает и анализирует различные метрики производительности:
 * - Время обработки запросов
 * - Кэш эффективность
 * - Качество ответов
 * - Использование ресурсов
 */
class PerformanceMonitor {
    private val logger = Logger.getInstance(PerformanceMonitor::class.java)

    // Метрики времени обработки
    private val totalRequests = AtomicLong(0)
    private val totalProcessingTime = DoubleAdder()
    private val processingTimesByComplexity = ConcurrentHashMap<ComplexityLevel, ProcessingTimeStats>()

    // Метрики кэша
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val cacheEvictions = AtomicLong(0)

    // Метрики качества
    private val qualityScores = DoubleAdder()
    private val qualityCount = AtomicLong(0)

    // Метрики ошибок
    private val errorCount = AtomicLong(0)
    private val errorsByType = ConcurrentHashMap<String, AtomicLong>()

    // Метрики производительности по времени
    private val performanceByHour = ConcurrentHashMap<Int, HourlyStats>()

    init {
        // Инициализируем статистику для каждого уровня сложности
        ComplexityLevel.values().forEach { complexity ->
            processingTimesByComplexity[complexity] = ProcessingTimeStats()
        }
    }

    /**
     * Регистрирует начало обработки запроса
     */
    fun startRequest(requestId: String, complexity: ComplexityLevel): RequestMetrics {
        totalRequests.incrementAndGet()
        return RequestMetrics(
            requestId = requestId,
            startTime = System.currentTimeMillis(),
            complexity = complexity
        )
    }

    /**
     * Завершает обработку запроса и регистрирует метрики
     */
    fun finishRequest(metrics: RequestMetrics, cacheHit: Boolean, qualityScore: Double? = null) {
        val processingTime = System.currentTimeMillis() - metrics.startTime

        // Общее время обработки
        totalProcessingTime.add(processingTime)

        // Время по сложности
        processingTimesByComplexity[metrics.complexity]?.addTime(processingTime)

        // Метрики кэша
        if (cacheHit) {
            cacheHits.incrementAndGet()
        } else {
            cacheMisses.incrementAndGet()
        }

        // Метрики качества
        qualityScore?.let {
            qualityScores.add(it)
            qualityCount.incrementAndGet()
        }

        // Метрики по часам
        val hour = java.time.LocalDateTime.now().hour
        performanceByHour.computeIfAbsent(hour) { HourlyStats() }.addRequest(processingTime)

        logger.debug("Request ${metrics.requestId} completed in ${processingTime}ms, complexity: ${metrics.complexity}")
    }

    /**
     * Регистрирует ошибку
     */
    fun recordError(errorType: String, details: String? = null) {
        errorCount.incrementAndGet()
        errorsByType.computeIfAbsent(errorType) { AtomicLong(0) }.incrementAndGet()
        logger.warn("Error recorded: $errorType${details?.let { " - $it" } ?: ""}")
    }

    /**
     * Регистрирует метрики кэша
     */
    fun recordCacheMetrics(hit: Boolean, eviction: Boolean = false) {
        if (hit) {
            cacheHits.incrementAndGet()
        } else {
            cacheMisses.incrementAndGet()
        }

        if (eviction) {
            cacheEvictions.incrementAndGet()
        }
    }

    /**
     * Возвращает текущую статистику производительности
     */
    fun getCurrentStats(): PerformanceStats {
        val totalReqs = totalRequests.get()
        val totalTime = totalProcessingTime.sum()

        return PerformanceStats(
            totalRequests = totalReqs,
            averageProcessingTime = if (totalReqs > 0) totalTime / totalReqs else 0.0,
            processingTimesByComplexity = processingTimesByComplexity.mapValues { it.value.getStats() },
            cacheStats = CacheStats(
                hits = cacheHits.get(),
                misses = cacheMisses.get(),
                evictions = cacheEvictions.get(),
                hitRate = {
                    val totalCacheRequests = cacheHits.get() + cacheMisses.get()
                    if (totalCacheRequests > 0) cacheHits.get().toDouble() / totalCacheRequests else 0.0
                }()
            ),
            qualityStats = QualityStats(
                averageScore = if (qualityCount.get() > 0) qualityScores.sum() / qualityCount.get() else 0.0,
                totalEvaluations = qualityCount.get()
            ),
            errorStats = ErrorStats(
                totalErrors = errorCount.get(),
                errorsByType = errorsByType.mapValues { it.value.get() }
            ),
            hourlyStats = performanceByHour.mapValues { it.value.getStats() }
        )
    }

    /**
     * Проверяет производительность и возвращает рекомендации
     */
    fun analyzePerformance(): List<PerformanceRecommendation> {
        val stats = getCurrentStats()
        val recommendations = mutableListOf<PerformanceRecommendation>()

        // Анализ времени обработки
        if (stats.averageProcessingTime > 2000) { // > 2 секунды
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.PERFORMANCE_OPTIMIZATION,
                    priority = RecommendationPriority.HIGH,
                    message = "Среднее время обработки запросов превышает 2 секунды",
                    suggestion = "Рассмотрите оптимизацию системных промптов или увеличение кэширования",
                    impact = "Ожидаемое ускорение на 30-50%"
                )
            )
        }

        // Анализ эффективности кэша
        if (stats.cacheStats.hitRate < 0.3) { // < 30%
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.CACHE_OPTIMIZATION,
                    priority = RecommendationPriority.MEDIUM,
                    message = "Низкая эффективность кэша (${(stats.cacheStats.hitRate * 100).toInt()}%)",
                    suggestion = "Настройте более агрессивное кэширование или предиктивную загрузку",
                    impact = "Повышение эффективности до 60-70%"
                )
            )
        }

        // Анализ качества ответов
        if (stats.qualityStats.averageScore < 0.7 && stats.qualityStats.totalEvaluations > 10) {
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.QUALITY_IMPROVEMENT,
                    priority = RecommendationPriority.MEDIUM,
                    message = "Среднее качество ответов ниже оптимального",
                    suggestion = "Оптимизируйте системные промпты для улучшения качества",
                    impact = "Повышение качества на 20-30%"
                )
            )
        }

        // Анализ ошибок
        val errorRate = if (stats.totalRequests > 0) stats.errorStats.totalErrors.toDouble() / stats.totalRequests else 0.0
        if (errorRate > 0.05) { // > 5%
            recommendations.add(
                PerformanceRecommendation(
                    type = RecommendationType.ERROR_REDUCTION,
                    priority = RecommendationPriority.HIGH,
                    message = "Высокий уровень ошибок (${(errorRate * 100).toInt()}%)",
                    suggestion = "Проанализируйте типы ошибок и улучшите обработку исключений",
                    impact = "Снижение уровня ошибок до 1-2%"
                )
            )
        }

        // Анализ по сложности
        stats.processingTimesByComplexity.forEach { (complexity, timeStats) ->
            if (timeStats.averageTime > getExpectedTime(complexity) * 1.5) {
                recommendations.add(
                    PerformanceRecommendation(
                        type = RecommendationType.COMPLEXITY_SPECIFIC,
                        priority = RecommendationPriority.LOW,
                        message = "Запросы сложности $complexity обрабатываются медленнее ожидаемого",
                        suggestion = "Оптимизируйте обработку для этого уровня сложности",
                        impact = "Ускорение обработки на 25-40%"
                    )
                )
            }
        }

        return recommendations.sortedByDescending { it.priority.ordinal }
    }

    /**
     * Возвращает ожидаемое время обработки для каждого уровня сложности
     */
    private fun getExpectedTime(complexity: ComplexityLevel): Long {
        return when (complexity) {
            ComplexityLevel.SIMPLE -> 500    // 0.5 секунды
            ComplexityLevel.MEDIUM -> 2000   // 2 секунды
            ComplexityLevel.COMPLEX -> 5000  // 5 секунд
        }
    }

    /**
     * Сбрасывает всю статистику
     */
    fun reset() {
        totalRequests.set(0)
        totalProcessingTime.reset()
        cacheHits.set(0)
        cacheMisses.set(0)
        cacheEvictions.set(0)
        qualityScores.reset()
        qualityCount.set(0)
        errorCount.set(0)
        errorsByType.clear()
        performanceByHour.clear()

        processingTimesByComplexity.values.forEach { it.reset() }

        logger.info("Performance monitor statistics reset")
    }

    /**
     * Экспортирует статистику в формате JSON
     */
    fun exportStats(): String {
        val stats = getCurrentStats()
        return buildString {
            appendLine("{")
            appendLine("  \"timestamp\": ${System.currentTimeMillis()},")
            appendLine("  \"totalRequests\": ${stats.totalRequests},")
            appendLine("  \"averageProcessingTime\": ${stats.averageProcessingTime},")
            appendLine("  \"cacheHitRate\": ${stats.cacheStats.hitRate},")
            appendLine("  \"averageQuality\": ${stats.qualityStats.averageScore},")
            appendLine("  \"totalErrors\": ${stats.errorStats.totalErrors}")
            appendLine("}")
        }
    }
}

/**
 * Метрики запроса для отслеживания производительности
 */
data class RequestMetrics(
    val requestId: String,
    val startTime: Long,
    val complexity: ComplexityLevel
)

/**
 * Статистика времени обработки для уровня сложности
 */
class ProcessingTimeStats {
    private val count = AtomicLong(0)
    private val totalTime = DoubleAdder()
    private val minTime = AtomicLong(Long.MAX_VALUE)
    private val maxTime = AtomicLong(0)

    fun addTime(time: Long) {
        count.incrementAndGet()
        totalTime.add(time)

        // Обновляем минимум и максимум
        var currentMin = minTime.get()
        while (currentMin > time && !minTime.compareAndSet(currentMin, time)) {
            currentMin = minTime.get()
        }

        var currentMax = maxTime.get()
        while (currentMax < time && !maxTime.compareAndSet(currentMax, time)) {
            currentMax = maxTime.get()
        }
    }

    fun getStats(): TimeStats {
        val c = count.get()
        return TimeStats(
            count = c,
            averageTime = if (c > 0) totalTime.sum() / c else 0.0,
            minTime = if (minTime.get() == Long.MAX_VALUE) 0.0 else minTime.get().toDouble(),
            maxTime = maxTime.get().toDouble()
        )
    }

    fun reset() {
        count.set(0)
        totalTime.reset()
        minTime.set(Long.MAX_VALUE)
        maxTime.set(0)
    }
}

/**
 * Почасовая статистика
 */
class HourlyStats {
    private val requestCount = AtomicInteger(0)
    private val totalTime = DoubleAdder()

    fun addRequest(time: Long) {
        requestCount.incrementAndGet()
        totalTime.add(time)
    }

    fun getStats(): HourlyPerformanceStats {
        val count = requestCount.get()
        return HourlyPerformanceStats(
            requestCount = count,
            averageTime = if (count > 0) totalTime.sum() / count else 0.0
        )
    }
}

/**
 * Статистика производительности
 */
data class PerformanceStats(
    val totalRequests: Long,
    val averageProcessingTime: Double,
    val processingTimesByComplexity: Map<ComplexityLevel, TimeStats>,
    val cacheStats: CacheStats,
    val qualityStats: QualityStats,
    val errorStats: ErrorStats,
    val hourlyStats: Map<Int, HourlyPerformanceStats>
)

/**
 * Статистика времени
 */
data class TimeStats(
    val count: Long,
    val averageTime: Double,
    val minTime: Long,
    val maxTime: Long
)

/**
 * Статистика кэша
 */
data class CacheStats(
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val hitRate: Double
)

/**
 * Статистика качества
 */
data class QualityStats(
    val averageScore: Double,
    val totalEvaluations: Long
)

/**
 * Статистика ошибок
 */
data class ErrorStats(
    val totalErrors: Long,
    val errorsByType: Map<String, Long>
)

/**
 * Почасовая статистика производительности
 */
data class HourlyPerformanceStats(
    val requestCount: Int,
    val averageTime: Double
)

/**
 * Рекомендация по оптимизации
 */
data class PerformanceRecommendation(
    val type: RecommendationType,
    val priority: RecommendationPriority,
    val message: String,
    val suggestion: String,
    val impact: String
)

/**
 * Тип рекомендации
 */
enum class RecommendationType {
    PERFORMANCE_OPTIMIZATION,
    CACHE_OPTIMIZATION,
    QUALITY_IMPROVEMENT,
    ERROR_REDUCTION,
    COMPLEXITY_SPECIFIC
}

/**
 * Приоритет рекомендации
 */
enum class RecommendationPriority {
    HIGH,
    MEDIUM,
    LOW
}