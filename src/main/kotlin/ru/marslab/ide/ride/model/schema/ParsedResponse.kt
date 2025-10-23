package ru.marslab.ide.ride.model.schema

import kotlinx.serialization.json.JsonElement

/**
 * Базовый класс для распарсенных ответов
 */
sealed class ParsedResponse {
    abstract val rawContent: String
    abstract val format: ResponseFormat

    /**
     * JSON ответ с распарсенной структурой
     *
     * @property rawContent Исходный текст ответа
     * @property jsonElement Распарсенный JSON элемент
     */
    data class JsonResponse(
        override val rawContent: String,
        val jsonElement: JsonElement
    ) : ParsedResponse() {
        override val format: ResponseFormat = ResponseFormat.JSON

        /**
         * Получает JSON как строку с форматированием
         */
        fun toFormattedString(): String = jsonElement.toString()
    }

    /**
     * XML ответ с распарсенной структурой
     *
     * @property rawContent Исходный текст ответа
     * @property xmlContent Распарсенный XML (пока как строка, можно расширить)
     */
    data class XmlResponse(
        override val rawContent: String,
        val xmlContent: String
    ) : ParsedResponse() {
        override val format: ResponseFormat = ResponseFormat.XML
    }

    /**
     * Текстовый ответ без структуры
     *
     * @property rawContent Текст ответа
     */
    data class TextResponse(
        override val rawContent: String
    ) : ParsedResponse() {
        override val format: ResponseFormat = ResponseFormat.TEXT
    }

    /**
     * Ответ с ошибкой парсинга
     *
     * @property rawContent Исходный текст ответа
     * @property error Описание ошибки
     * @property expectedFormat Ожидаемый формат
     */
    data class ParseError(
        override val rawContent: String,
        val error: String,
        val expectedFormat: ResponseFormat
    ) : ParsedResponse() {
        override val format: ResponseFormat = expectedFormat
    }
}
