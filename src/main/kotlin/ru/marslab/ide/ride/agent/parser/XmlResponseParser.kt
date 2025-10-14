package ru.marslab.ide.ride.agent.parser

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.model.schema.ParsedResponse
import ru.marslab.ide.ride.model.schema.ResponseFormat
import ru.marslab.ide.ride.model.schema.ResponseSchema

/**
 * Парсер для XML ответов
 */
class XmlResponseParser : ResponseParser {
    
    private val logger = Logger.getInstance(XmlResponseParser::class.java)
    
    override fun parse(rawContent: String, schema: ResponseSchema?): ParsedResponse {
        return try {
            // Извлекаем XML из ответа (может быть обернут в markdown блок)
            val xmlContent = extractXmlFromResponse(rawContent)
            
            // Базовая валидация XML (проверка что есть открывающие и закрывающие теги)
            if (!isValidXml(xmlContent)) {
                throw IllegalArgumentException("Невалидный XML: отсутствуют корректные теги")
            }
            
            logger.debug("Successfully parsed XML response")
            
            ParsedResponse.XmlResponse(
                rawContent = rawContent,
                xmlContent = xmlContent
            )
            
        } catch (e: Exception) {
            logger.warn("Failed to parse XML response", e)
            ParsedResponse.ParseError(
                rawContent = rawContent,
                error = "Ошибка парсинга XML: ${e.message}",
                expectedFormat = ResponseFormat.XML
            )
        }
    }
    
    override fun supports(schema: ResponseSchema): Boolean {
        return schema.format == ResponseFormat.XML
    }
    
    /**
     * Извлекает XML из ответа, убирая markdown обертки если есть
     */
    private fun extractXmlFromResponse(content: String): String {
        val trimmed = content.trim()
        
        // Проверяем, обернут ли XML в markdown блок ```xml ... ```
        val xmlBlockRegex = Regex("```xml\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = xmlBlockRegex.find(trimmed)
        
        if (match != null) {
            return match.groupValues[1].trim()
        }
        
        // Проверяем обычный блок кода ``` ... ```
        val codeBlockRegex = Regex("```\\s*([\\s\\S]*?)```")
        val codeMatch = codeBlockRegex.find(trimmed)
        
        if (codeMatch != null) {
            val extracted = codeMatch.groupValues[1].trim()
            // Проверяем, что это похоже на XML
            if (extracted.startsWith("<") && extracted.contains(">")) {
                return extracted
            }
        }
        
        // Если нет markdown блоков, возвращаем как есть
        return trimmed
    }
    
    /**
     * Базовая проверка валидности XML
     */
    private fun isValidXml(content: String): Boolean {
        val trimmed = content.trim()
        
        // Должен начинаться с < и заканчиваться на >
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) {
            return false
        }
        
        // Должен содержать хотя бы один закрывающий тег
        if (!trimmed.contains("</")) {
            return false
        }
        
        return true
    }
}
