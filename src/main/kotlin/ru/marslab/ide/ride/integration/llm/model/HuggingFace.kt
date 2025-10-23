package ru.marslab.ide.ride.integration.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==== OpenAI-compatible request/response models for HF Router ====
@Serializable
data class HFMessage(
    val role: String,
    val content: String
)

@Serializable
data class HFChatCompletionsRequest(
    val model: String,
    val messages: List<HFMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class HFChatCompletionsResponse(
    val id: String? = null,
    val object_: String? = null,
    val created: Long? = null,
    val model: String? = null,
    @SerialName("system_fingerprint") val systemFingerprint: String? = null,
    val choices: List<HFChoice> = emptyList(),
    val usage: HFUsage? = null
) {
    // Map the JSON field "object" which conflicts with Kotlin keyword
    @Suppress("unused")
    @SerialName("object")
    private val _object: String? = object_
}

@Serializable
data class HFChoice(
    val index: Int = 0,
    val message: HFChoiceMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class HFChoiceMessage(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class HFUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)