package ru.marslab.ide.ride.model.agent

import kotlinx.serialization.Serializable

/**
 * Типы вывода агентов для форматирования
 */
@Serializable
enum class AgentOutputType {
    /**
     * Обычный markdown (по умолчанию)
     */
    MARKDOWN,

    /**
     * Вывод терминала
     */
    TERMINAL,

    /**
     * Форматированные блоки кода
     */
    CODE_BLOCKS,

    /**
     * Структурированные данные
     */
    STRUCTURED,

    /**
     * Готовый HTML
     */
    HTML,

    /**
     * Результат вызова инструмента
     */
    TOOL_RESULT
}