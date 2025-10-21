package ru.marslab.ide.ride.model.codeanalysis

/**
 * Метрики кода проекта
 *
 * @property totalFiles Общее количество файлов
 * @property totalLines Общее количество строк кода
 * @property totalClasses Общее количество классов
 * @property totalFunctions Общее количество функций
 * @property averageComplexity Средняя сложность кода
 * @property testCoverage Покрытие тестами (может быть null если не вычислено)
 */
data class CodeMetrics(
    val totalFiles: Int,
    val totalLines: Int,
    val totalClasses: Int,
    val totalFunctions: Int,
    val averageComplexity: Double,
    val testCoverage: Double?
)
