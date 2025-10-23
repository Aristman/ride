package ru.marslab.ide.ride.integration.llm.impl

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import ru.marslab.ide.ride.integration.llm.TokenCounter

/**
 * Реализация TokenCounter на основе Tiktoken (OpenAI токенизатор)
 *
 * Используется для провайдеров, которые не предоставляют API для подсчёта токенов
 * или для предварительной оценки количества токенов перед отправкой запроса
 *
 * @param encodingType Тип кодировки (по умолчанию CL100K_BASE для GPT-3.5/GPT-4)
 */
class TiktokenCounter(
    private val encodingType: EncodingType = EncodingType.CL100K_BASE
) : TokenCounter {

    private val encoding: Encoding by lazy {
        val registry = Encodings.newDefaultEncodingRegistry()
        registry.getEncoding(encodingType)
    }

    override fun countTokens(text: String): Int {
        if (text.isBlank()) return 0
        return try {
            encoding.countTokens(text)
        } catch (e: Exception) {
            // Fallback: примерная оценка (1 токен ≈ 4 символа для английского текста)
            // Для русского текста это соотношение хуже, но лучше чем ничего
            (text.length / 3).coerceAtLeast(1)
        }
    }

    override fun getTokenizerName(): String = "Tiktoken (${encodingType.name})"

    companion object {
        /**
         * Создаёт счётчик для GPT-3.5 и GPT-4
         */
        fun forGPT(): TiktokenCounter = TiktokenCounter(EncodingType.CL100K_BASE)

        /**
         * Создаёт счётчик для GPT-3 (davinci, curie, babbage, ada)
         */
        fun forGPT3(): TiktokenCounter = TiktokenCounter(EncodingType.P50K_BASE)
    }
}
