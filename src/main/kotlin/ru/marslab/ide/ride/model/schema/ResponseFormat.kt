package ru.marslab.ide.ride.model.schema

/**
 * Формат ответа от агента
 */
enum class ResponseFormat {
    /**
     * JSON формат - структурированные данные в JSON
     */
    JSON,
    
    /**
     * XML формат - структурированные данные в XML
     */
    XML,
    
    /**
     * Обычный текст без структуры
     */
    TEXT
}
