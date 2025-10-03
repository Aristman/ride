package ru.marslab.ide.ride.agent.parser

import ru.marslab.ide.ride.model.ParsedResponse
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema

/**
 * Парсер для текстовых ответов (без структуры)
 */
class TextResponseParser : ResponseParser {
    
    override fun parse(rawContent: String, schema: ResponseSchema?): ParsedResponse {
        // Для текстового формата просто возвращаем содержимое как есть
        return ParsedResponse.TextResponse(rawContent = rawContent)
    }
    
    override fun supports(schema: ResponseSchema): Boolean {
        return schema.format == ResponseFormat.TEXT
    }
}
