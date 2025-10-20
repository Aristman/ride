package ru.marslab.ide.ride.model.codeanalysis

/**
 * Модуль проекта
 *
 * @property name Название модуля
 * @property path Путь к модулю
 * @property type Тип модуля
 * @property files Количество файлов
 * @property linesOfCode Количество строк кода
 */
data class Module(
    val name: String,
    val path: String,
    val type: ModuleType,
    val files: Int,
    val linesOfCode: Int
)
