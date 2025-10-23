package ru.marslab.ide.ride.agent.parser

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.marslab.ide.ride.model.schema.ParsedResponse
import ru.marslab.ide.ride.model.schema.ResponseFormat
import ru.marslab.ide.ride.model.schema.ResponseSchema

/**
 * Парсер для JSON ответов
 */
class JsonResponseParser : ResponseParser {

    private val logger = Logger.getInstance(JsonResponseParser::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    override fun parse(rawContent: String, schema: ResponseSchema?): ParsedResponse {
        return try {
            // Извлекаем JSON из ответа (может быть обернут в markdown блок)
            val jsonContent = extractJsonFromResponse(rawContent)

            // Парсим JSON
            val jsonElement: JsonElement = json.parseToJsonElement(jsonContent)

            logger.debug("Successfully parsed JSON response")

            ParsedResponse.JsonResponse(
                rawContent = rawContent,
                jsonElement = jsonElement
            )

        } catch (e: Exception) {
            logger.warn("Failed to parse JSON response", e)
            ParsedResponse.ParseError(
                rawContent = rawContent,
                error = "Ошибка парсинга JSON: ${e.message}",
                expectedFormat = ResponseFormat.JSON
            )
        }
    }

    override fun supports(schema: ResponseSchema): Boolean {
        return schema.format == ResponseFormat.JSON
    }

    /**
     * Извлекает JSON из ответа, убирая markdown обертки если есть
     */
    private fun extractJsonFromResponse(content: String): String {
        val trimmed = content.trim()

        // Проверяем, обернут ли JSON в markdown блок ```json ... ```
        val jsonBlockRegex = Regex("```json\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = jsonBlockRegex.find(trimmed)

        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Проверяем обычный блок кода ``` ... ```
        val codeBlockRegex = Regex("```\\s*([\\s\\S]*?)```")
        val codeMatch = codeBlockRegex.find(trimmed)

        if (codeMatch != null) {
            val extracted = codeMatch.groupValues[1].trim()
            // Проверяем, что это похоже на JSON
            if (extracted.startsWith("{") || extracted.startsWith("[")) {
                return extracted
            }
        }

        // Если нет markdown блоков, возвращаем как есть
        return trimmed
    }
}
