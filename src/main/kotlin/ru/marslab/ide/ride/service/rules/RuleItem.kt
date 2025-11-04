package ru.marslab.ide.ride.service.rules

/**
 * Элемент правила для отображения в UI
 */
data class RuleItem(
    val fileName: String,       // Имя файла без расширения .md
    val isActive: Boolean = true, // Активность правила
    val isGlobal: Boolean       // true - глобальное, false - проектное
) {
    val fullFileName: String = "$fileName.md"
    val displayName: String = fileName
}