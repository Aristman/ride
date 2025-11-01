package ru.marslab.ide.ride.agent.monitoring

import ru.marslab.ide.ride.agent.analyzer.ComplexityLevel

/**
 * Метрики запроса для отслеживания производительности
 */
data class RequestMetrics(
    val requestId: String,
    val startTime: Long,
    val complexity: ComplexityLevel
)