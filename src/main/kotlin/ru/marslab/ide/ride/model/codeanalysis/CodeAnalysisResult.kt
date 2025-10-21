package ru.marslab.ide.ride.model.codeanalysis

import java.time.LocalDateTime

/**
 * Результат анализа кода
 *
 * @property projectName Название проекта
 * @property analysisDate Дата и время анализа
 * @property findings Найденные проблемы
 * @property projectStructure Структура проекта (может быть null если не запрашивалась)
 * @property metrics Метрики кода
 * @property summary Краткое резюме анализа
 * @property recommendations Рекомендации по улучшению
 */
data class CodeAnalysisResult(
    val projectName: String,
    val analysisDate: LocalDateTime,
    val findings: List<Finding>,
    val projectStructure: ProjectStructure?,
    val metrics: CodeMetrics,
    val summary: String,
    val recommendations: List<String>
)
