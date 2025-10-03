package ru.marslab.ide.ride.model

/**
 * Ответ от агента на запрос пользователя
 *
 * @property content Содержимое ответа (текстовое представление)
 * @property success Флаг успешности обработки запроса
 * @property error Сообщение об ошибке (если success = false)
 * @property metadata Дополнительные метаданные ответа
 * @property parsedContent Распарсенное содержимое (если задан формат ответа)
 */
data class AgentResponse(
    val content: String,
    val success: Boolean,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val parsedContent: ParsedResponse? = null
) {
    companion object {
        /**
         * Создает успешный ответ
         */
        fun success(content: String, metadata: Map<String, Any> = emptyMap()): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                metadata = metadata
            )
        }
        
        /**
         * Создает успешный ответ с распарсенным содержимым
         */
        fun success(
            content: String,
            parsedContent: ParsedResponse,
            metadata: Map<String, Any> = emptyMap()
        ): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                metadata = metadata,
                parsedContent = parsedContent
            )
        }
        
        /**
         * Создает ответ с ошибкой
         */
        fun error(error: String, content: String = ""): AgentResponse {
            return AgentResponse(
                content = content,
                success = false,
                error = error
            )
        }
    }
}
