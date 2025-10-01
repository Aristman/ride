package ru.marslab.ide.ride.model

/**
 * Параметры для запроса к LLM провайдеру
 *
 * @property temperature Температура генерации (0.0 - 1.0). Выше = более креативно
 * @property maxTokens Максимальное количество токенов в ответе
 * @property topP Nucleus sampling параметр (0.0 - 1.0)
 * @property frequencyPenalty Штраф за частоту повторений (-2.0 - 2.0)
 * @property presencePenalty Штраф за присутствие токенов (-2.0 - 2.0)
 */
data class LLMParameters(
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null
) {
    init {
        require(temperature in 0.0..1.0) { "Temperature must be between 0.0 and 1.0" }
        require(maxTokens > 0) { "Max tokens must be positive" }
        topP?.let { require(it in 0.0..1.0) { "Top P must be between 0.0 and 1.0" } }
        frequencyPenalty?.let { require(it in -2.0..2.0) { "Frequency penalty must be between -2.0 and 2.0" } }
        presencePenalty?.let { require(it in -2.0..2.0) { "Presence penalty must be between -2.0 and 2.0" } }
    }
    
    companion object {
        /**
         * Параметры по умолчанию для сбалансированной генерации
         */
        val DEFAULT = LLMParameters()
        
        /**
         * Параметры для более креативной генерации
         */
        val CREATIVE = LLMParameters(temperature = 0.9, maxTokens = 2000)
        
        /**
         * Параметры для более точной и предсказуемой генерации
         */
        val PRECISE = LLMParameters(temperature = 0.3, maxTokens = 2000)
    }
}
