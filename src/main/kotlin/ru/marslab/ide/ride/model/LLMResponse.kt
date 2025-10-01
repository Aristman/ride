package ru.marslab.ide.ride.model

/**
 * Ответ от LLM провайдера
 *
 * @property content Содержимое ответа от LLM
 * @property success Флаг успешности запроса
 * @property error Сообщение об ошибке (если success = false)
 * @property tokensUsed Количество использованных токенов
 * @property metadata Дополнительные метаданные от провайдера
 */
data class LLMResponse(
    val content: String,
    val success: Boolean,
    val error: String? = null,
    val tokensUsed: Int = 0,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * Создает успешный ответ
         */
        fun success(
            content: String,
            tokensUsed: Int = 0,
            metadata: Map<String, Any> = emptyMap()
        ): LLMResponse {
            return LLMResponse(
                content = content,
                success = true,
                tokensUsed = tokensUsed,
                metadata = metadata
            )
        }
        
        /**
         * Создает ответ с ошибкой
         */
        fun error(error: String): LLMResponse {
            return LLMResponse(
                content = "",
                success = false,
                error = error
            )
        }
    }
}
