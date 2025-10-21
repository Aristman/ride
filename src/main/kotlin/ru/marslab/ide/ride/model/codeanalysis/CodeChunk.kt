package ru.marslab.ide.ride.model.codeanalysis

/**
 * Чанк кода для обработки
 *
 * @property content Содержимое чанка
 * @property startLine Начальная строка
 * @property endLine Конечная строка
 * @property tokens Количество токенов в чанке
 */
data class CodeChunk(
    val content: String,
    val startLine: Int,
    val endLine: Int,
    val tokens: Int
)
