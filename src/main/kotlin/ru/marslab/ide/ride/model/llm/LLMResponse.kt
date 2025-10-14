package ru.marslab.ide.ride.model.llm

/**
 * Детальная статистика использования токенов
 *
 * @property inputTokens Количество токенов в запросе (prompt)
 * @property outputTokens Количество токенов в ответе (completion)
 * @property totalTokens Общее количество токенов
 */
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = inputTokens + outputTokens
) {
    companion object {
        val EMPTY = TokenUsage(0, 0, 0)
    }
}

/**
 * Ответ от LLM провайдера
 *
 * @property content Содержимое ответа от LLM
 * @property success Флаг успешности запроса
 * @property error Сообщение об ошибке (если success = false)
 * @property tokensUsed Количество использованных токенов (deprecated, используйте tokenUsage)
 * @property tokenUsage Детальная статистика использования токенов
 * @property metadata Дополнительные метаданные от провайдера
 */
data class LLMResponse(
    val content: String,
    val success: Boolean,
    val error: String? = null,
    @Deprecated("Use tokenUsage instead", ReplaceWith("tokenUsage.totalTokens"))
    val tokensUsed: Int = 0,
    val tokenUsage: TokenUsage = TokenUsage.EMPTY,
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
                tokenUsage = TokenUsage(0, 0, tokensUsed),
                metadata = metadata
            )
        }

        /**
         * Создает успешный ответ с детальной статистикой токенов
         */
        fun success(
            content: String,
            tokenUsage: TokenUsage,
            metadata: Map<String, Any> = emptyMap()
        ): LLMResponse {
            return LLMResponse(
                content = content,
                success = true,
                tokensUsed = tokenUsage.totalTokens,
                tokenUsage = tokenUsage,
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
