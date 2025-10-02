package ru.marslab.ide.ride.model

/**
 * Ответ от агента на запрос пользователя
 *
 * @property content Содержимое ответа
 * @property success Флаг успешности обработки запроса
 * @property error Сообщение об ошибке (если success = false)
 * @property metadata Дополнительные метаданные ответа
 */
data class AgentResponse(
    val content: String,
    val success: Boolean,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
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
