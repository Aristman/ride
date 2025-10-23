package ru.marslab.ide.ride.integration.llm.impl

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.model.HFChatCompletionsRequest
import ru.marslab.ide.ride.integration.llm.model.HFChatCompletionsResponse
import ru.marslab.ide.ride.integration.llm.model.HFMessage
import ru.marslab.ide.ride.model.chat.ConversationMessage
import ru.marslab.ide.ride.model.chat.ConversationRole
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.llm.LLMResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Конфигурация для Hugging Face Provider
 */
data class HuggingFaceConfig(
    val apiKey: String,
    val model: String,
    val baseUrl: String = "https://router.huggingface.co/v1/chat/completions",
    val timeout: Long = 60_000
)

/**
 * Доступные модели HuggingFace
 */
enum class HuggingFaceModel(
    val modelId: String,
    val displayName: String
) {
    DEEPSEEK_R1(
        modelId = "deepseek-ai/DeepSeek-R1:fireworks-ai",
        displayName = "DeepSeek-R1"
    ),
    DEEPSEEK_TERMINUS(
        modelId = "deepseek-ai/DeepSeek-V3.1-Terminus:novita",
        displayName = "DeepSeek-V3.1-Terminus"
    ),
    OPENBUDDY_LLAMA3(
        modelId = "OpenBuddy/openbuddy-llama3-8b-v21.1-8k:featherless-ai",
        displayName = "OpenBuddy Llama3-8B"
    )
}

/**
 * Единый LLM провайдер для Hugging Face Router
 * Совместим с интерфейсом OpenAI Chat Completions
 * Поддерживает все доступные модели через выбор в конфигурации
 */
class HuggingFaceProvider(
    private val config: HuggingFaceConfig
) : LLMProvider {

    private val logger = Logger.getInstance(HuggingFaceProvider::class.java)

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    override suspend fun sendRequest(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        parameters: LLMParameters
    ): LLMResponse {
        logger.info(
            "Sending request to Hugging Face (${config.model}), systemPrompt length: ${systemPrompt.length}, userMessage length: ${userMessage.length}, conversationHistory size: ${conversationHistory.size}"
        )
        return try {
            val request = buildRequest(systemPrompt, userMessage, conversationHistory, parameters)
            val response = sendWithRetry(request)
            logger.info("Request successful, tokens used: ${response.tokensUsed}")
            response
        } catch (e: Exception) {
            logger.error("Error sending request to Hugging Face", e)
            LLMResponse.error("Ошибка при обращении к Hugging Face: ${e.message}")
        }
    }

    override fun isAvailable(): Boolean {
        return config.apiKey.isNotBlank()
    }

    override fun getProviderName(): String = "HuggingFace"

    /**
     * Возвращает отображаемое имя модели для UI
     */
    fun getModelDisplayName(): String {
        return HuggingFaceModel.entries.find { it.modelId == config.model }?.displayName ?: config.model
    }

    private fun buildRequest(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        parameters: LLMParameters
    ): HFChatCompletionsRequest {
        val messages = buildList {
            if (systemPrompt.isNotBlank()) {
                add(HFMessage(role = "system", content = systemPrompt))
            }
            conversationHistory.forEach { convMsg ->
                val role = when (convMsg.role) {
                    ConversationRole.USER -> "user"
                    ConversationRole.ASSISTANT -> "assistant"
                    ConversationRole.SYSTEM -> "system"
                }
                if (convMsg.content.isNotBlank()) {
                    add(HFMessage(role = role, content = convMsg.content))
                }
            }
            add(HFMessage(role = "user", content = userMessage))
        }
        return HFChatCompletionsRequest(
            model = config.model,
            messages = messages,
            stream = false,
            temperature = parameters.temperature,
            maxTokens = parameters.maxTokens
        )
    }

    private suspend fun sendWithRetry(
        request: HFChatCompletionsRequest,
        maxRetries: Int = 3
    ): LLMResponse {
        var lastException: Exception? = null
        var currentDelay = 1_000L

        repeat(maxRetries) { attempt ->
            try {
                val requestBody = json.encodeToString(request)
                val httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl))
                    .timeout(Duration.ofSeconds(config.timeout / 1000))
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()

                val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
                return when (httpResponse.statusCode()) {
                    200 -> {
                        val hfResponse =
                            json.decodeFromString(HFChatCompletionsResponse.serializer(), httpResponse.body())
                        parseSuccessResponse(hfResponse)
                    }

                    401 -> LLMResponse.error("Неверный API ключ Hugging Face. Проверьте настройки.")
                    429 -> {
                        if (attempt < maxRetries - 1) {
                            logger.warn("Rate limit exceeded (HF), retrying after delay...")
                            delay(currentDelay)
                            currentDelay *= 2
                            throw Exception("Rate limit, retry")
                        } else {
                            LLMResponse.error("Превышен лимит запросов Hugging Face. Попробуйте позже.")
                        }
                    }

                    else -> {
                        logger.error("HF API error: ${httpResponse.statusCode()}, body: ${httpResponse.body()}")
                        LLMResponse.error("Ошибка HF API: ${httpResponse.statusCode()}")
                    }
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1 && shouldRetry(e)) {
                    logger.warn("HF request failed, retrying... Attempt ${attempt + 1}/$maxRetries", e)
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("Unknown error")
    }

    private fun parseSuccessResponse(response: HFChatCompletionsResponse): LLMResponse {
        val choice = response.choices.firstOrNull()
            ?: return LLMResponse.error("Пустой ответ от Hugging Face")
        val content = choice.message?.content ?: ""
        val tokensUsed = response.usage?.totalTokens ?: 0
        val meta = buildMap<String, Any> {
            response.model?.let { put("model", it) }
            response.systemFingerprint?.let { put("systemFingerprint", it) }
        }
        return LLMResponse.success(
            content = content,
            tokensUsed = tokensUsed,
            metadata = meta
        )
    }

    private fun shouldRetry(exception: Exception): Boolean {
        return when (exception) {
            is java.net.http.HttpTimeoutException -> true
            is java.net.SocketTimeoutException -> true
            is java.io.IOException -> true
            else -> exception.message?.contains("Rate limit") == true
        }
    }
}