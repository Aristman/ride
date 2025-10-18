package ru.marslab.ide.ride.model.agent

/**
 * Типы вывода агентов для форматирования
 */
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