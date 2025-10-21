package ru.marslab.ide.ride.model.codeanalysis

/**
 * Найденная проблема в коде
 *
 * @property id Уникальный идентификатор проблемы
 * @property type Тип проблемы
 * @property severity Уровень серьезности
 * @property file Путь к файлу
 * @property line Номер строки (может быть null для проблем уровня файла)
 * @property title Краткое название проблемы
 * @property description Подробное описание проблемы
 * @property suggestion Рекомендация по исправлению
 * @property codeSnippet Фрагмент кода с проблемой
 */
data class Finding(
    val id: String,
    val type: FindingType,
    val severity: Severity,
    val file: String,
    val line: Int?,
    val title: String,
    val description: String,
    val suggestion: String?,
    val codeSnippet: String?
)
