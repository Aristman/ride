package ru.marslab.ide.ride.model.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Модели для Yandex GPT Tools API
 * https://yandex.cloud/ru/docs/ai-studio/text-generation/api-ref/TextGenerationAsync/completion
 */

@Serializable
data class YandexGPTToolsRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<YandexToolsMessage>,
    val tools: List<Tool>? = null,
    val parallelToolCalls: Boolean? = null,
    val toolChoice: ToolChoice? = null
)

@Serializable
data class CompletionOptions(
    val stream: Boolean = false,
    val temperature: Double? = null,
    val maxTokens: String? = null
)

@Serializable
data class YandexToolsMessage(
    val role: String,
    val text: String? = null,
    val toolCallList: ToolCallList? = null,
    val toolResultList: ToolResultList? = null
)

@Serializable
data class Tool(
    val function: FunctionTool
)

@Serializable
data class FunctionTool(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val strict: Boolean? = null
)

@Serializable
data class ToolCallList(
    val toolCalls: List<ToolCall>
)

@Serializable
data class ToolCall(
    val functionCall: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: JsonObject
)

@Serializable
data class ToolResultList(
    val toolResults: List<ToolResult>
)

@Serializable
data class ToolResult(
    val functionResult: FunctionResult
)

@Serializable
data class FunctionResult(
    val name: String,
    val content: String
)

@Serializable
data class ToolChoice(
    val mode: String? = null,
    val functionName: String? = null
)

@Serializable
data class YandexGPTToolsResponse(
    val result: YandexGPTResult
)

@Serializable
data class YandexGPTResult(
    val alternatives: List<Alternative>,
    val usage: Usage,
    val modelVersion: String
)

@Serializable
data class Alternative(
    val message: YandexToolsMessage,
    val status: String
)

@Serializable
data class Usage(
    val inputTextTokens: String,
    val completionTokens: String,
    val totalTokens: String
)
