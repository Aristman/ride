package ru.marslab.ide.ride.model

/**
 * Интерфейс для схемы структурированного ответа
 *
 * Определяет формат ответа, схему данных и описание для LLM.
 * Каждая реализация содержит свой дата класс для распарсенных данных.
 */
interface ResponseSchema {
    /**
     * Формат ответа (JSON, XML, TEXT)
     */
    val format: ResponseFormat

    /**
     * Определение схемы (JSON Schema, XML Schema или описание)
     */
    val schemaDefinition: String

    /**
     * Описание ожидаемого ответа для LLM
     */
    val description: String

    /**
     * Проверяет, является ли схема валидной
     */
    fun isValid(): Boolean

    /**
     * Парсит сырой ответ в структурированные данные
     *
     * @param rawContent Сырой ответ от LLM
     * @return Распарсенные данные или null в случае ошибки
     */
    fun parseResponse(rawContent: String): Any?
}

/**
 * Реализация схемы для JSON формата
 */
class JsonResponseSchema(
    override val schemaDefinition: String,
    override val description: String = ""
) : ResponseSchema {

    override val format: ResponseFormat = ResponseFormat.JSON

    override fun isValid(): Boolean = schemaDefinition.isNotBlank()

    override fun parseResponse(rawContent: String): JsonResponseData? {
        return try {
            JsonResponseData.fromJson(rawContent)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun create(schema: String, description: String = ""): JsonResponseSchema {
            return JsonResponseSchema(schema, description)
        }
    }
}

/**
 * Реализация схемы для XML формата
 */
class XmlResponseSchema(
    override val schemaDefinition: String,
    override val description: String = ""
) : ResponseSchema {

    override val format: ResponseFormat = ResponseFormat.XML

    override fun isValid(): Boolean = schemaDefinition.isNotBlank()

    override fun parseResponse(rawContent: String): XmlResponseData? {
        return try {
            XmlResponseData.fromXml(rawContent)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun create(schema: String, description: String = ""): XmlResponseSchema {
            return XmlResponseSchema(schema, description)
        }
    }
}

/**
 * Реализация схемы для текстового формата
 */
class TextResponseSchema(
    override val description: String = ""
) : ResponseSchema {

    override val format: ResponseFormat = ResponseFormat.TEXT
    override val schemaDefinition: String = ""

    override fun isValid(): Boolean = true

    override fun parseResponse(rawContent: String): TextResponseData {
        return TextResponseData(rawContent)
    }

    companion object {
        fun create(description: String = ""): TextResponseSchema {
            return TextResponseSchema(description)
        }
    }
}

/**
 * Дата класс для распарсенных JSON данных
 */
data class JsonResponseData(
    val isFinal: Boolean,
    val uncertainty: Double,
    val message: String,
    val clarifyingQuestions: List<String> = emptyList(),
    val reasoning: String? = null,
    val additionalFields: Map<String, Any> = emptyMap()
) {
    companion object {
        fun fromJson(jsonString: String): JsonResponseData {
            // Здесь будет парсинг JSON в объект
            // Временно упрощенная реализация
            return JsonResponseData(
                isFinal = true,
                uncertainty = 0.0,
                message = jsonString
            )
        }
    }
}

/**
 * Дата класс для распарсенных XML данных
 */
data class XmlResponseData(
    val isFinal: Boolean,
    val uncertainty: Double,
    val message: String,
    val clarifyingQuestions: List<String> = emptyList(),
    val reasoning: String? = null,
    val additionalElements: Map<String, String> = emptyMap()
) {
    companion object {
        fun fromXml(xmlString: String): XmlResponseData {
            // Извлекаем данные из XML с использованием регулярных выражений
            val isFinal = Regex("""<isFinal[^>]*>(true|false)</isFinal>""").find(xmlString)?.groupValues?.get(1) == "true"
            val uncertainty = Regex("""<uncertainty[^>]*>([\d.]+)</uncertainty>""").find(xmlString)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val message = Regex("""<message[^>]*>(.*?)</message>""", RegexOption.DOT_MATCHES_ALL).find(xmlString)?.groupValues?.get(1)?.trim() ?: ""
            val reasoning = Regex("""<reasoning[^>]*>([^<]+)</reasoning>""").find(xmlString)?.groupValues?.get(1)?.trim()

            // Извлекаем уточняющие вопросы
            val clarifyingQuestions = mutableListOf<String>()
            val questionMatches = Regex("""<question[^>]*>([^<]+)</question>""").findAll(xmlString)
            questionMatches.forEach { match ->
                clarifyingQuestions.add(match.groupValues[1].trim())
            }

            // Извлекаем дополнительные элементы
            val additionalElements = mutableMapOf<String, String>()
            val additionalMatches = Regex("""<([^>]+)>([^<]*)</\1>""").findAll(xmlString)
            additionalMatches.forEach { match ->
                val tag = match.groupValues[1]
                val value = match.groupValues[2].trim()
                if (tag !in listOf("isFinal", "uncertainty", "message", "reasoning", "question")) {
                    additionalElements[tag] = value
                }
            }

            return XmlResponseData(
                isFinal = isFinal,
                uncertainty = uncertainty,
                message = message,
                clarifyingQuestions = clarifyingQuestions,
                reasoning = reasoning,
                additionalElements = additionalElements
            )
        }
    }
}

/**
 * Дата класс для текстовых данных
 */
data class TextResponseData(
    val content: String
)
