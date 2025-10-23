package ru.marslab.ide.ride.integration.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модели данных для Yandex GPT API
 */

/**
 * Запрос к Yandex GPT API
 */
@Serializable
data class YandexGPTRequest(
    @SerialName("modelUri")
    val modelUri: String,
    @SerialName("completionOptions")
    val completionOptions: CompletionOptions,
    @SerialName("messages")
    val messages: List<YandexMessage>
)

/**
 * Опции генерации для Yandex GPT
 */
@Serializable
data class CompletionOptions(
    @SerialName("stream")
    val stream: Boolean = false,
    @SerialName("temperature")
    val temperature: Double = 0.6,
    @SerialName("maxTokens")
    val maxTokens: Int = 2000
)

/**
 * Сообщение в формате Yandex GPT
 */
@Serializable
data class YandexMessage(
    @SerialName("role")
    val role: String,
    @SerialName("text")
    val text: String
)

/**
 * Ответ от Yandex GPT API
 */
@Serializable
data class YandexGPTResponse(
    @SerialName("result")
    val result: Result
) {
    @Serializable
    data class Result(
        @SerialName("alternatives")
        val alternatives: List<Alternative>,
        @SerialName("usage")
        val usage: Usage,
        @SerialName("modelVersion")
        val modelVersion: String
    )

    @Serializable
    data class Alternative(
        @SerialName("message")
        val message: YandexMessage,
        @SerialName("status")
        val status: String
    )

    @Serializable
    data class Usage(
        @SerialName("inputTextTokens")
        val inputTextTokens: String,
        @SerialName("completionTokens")
        val completionTokens: String,
        @SerialName("totalTokens")
        val totalTokens: String
    )
}

/**
 * Ошибка от Yandex GPT API
 */
@Serializable
data class YandexGPTError(
    @SerialName("error")
    val error: ErrorDetails
) {
    @Serializable
    data class ErrorDetails(
        @SerialName("code")
        val code: Int,
        @SerialName("message")
        val message: String
    )
}
