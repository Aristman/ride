package ru.marslab.ide.ride.integration.llm.impl

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.TokenCounter
import ru.marslab.ide.ride.integration.llm.model.CompletionOptions
import ru.marslab.ide.ride.integration.llm.model.YandexGPTRequest
import ru.marslab.ide.ride.integration.llm.model.YandexGPTResponse
import ru.marslab.ide.ride.integration.llm.model.YandexMessage
import ru.marslab.ide.ride.model.LLMParameters
import ru.marslab.ide.ride.model.LLMResponse
import ru.marslab.ide.ride.model.TokenUsage
import ru.marslab.ide.ride.model.ConversationMessage
import ru.marslab.ide.ride.model.ConversationRole
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Доступные модели Yandex GPT
 */
enum class YandexModel(
    val modelId: String,
    val displayName: String
) {
    YANDEXGPT_LITE(
        modelId = "yandexgpt-lite",
        displayName = "YandexGPT Lite"
    ),
    YANDEXGPT(
        modelId = "yandexgpt",
        displayName = "YandexGPT"
    );

    companion object {
        fun fromModelId(modelId: String): YandexModel? {
            return entries.find { it.modelId == modelId }
        }

        fun getAll(): List<YandexModel> = entries.toList()
    }
}

/**
 * Конфигурация для Yandex GPT Provider
 */
data class YandexGPTConfig(
    val apiKey: String,
    val folderId: String,
    val modelId: String = YandexModel.YANDEXGPT_LITE.modelId,
    val modelUri: String = "gpt://$folderId/${modelId}/latest",
    val timeout: Long = 60000 // 60 секунд
)

/**
 * Реализация LLM провайдера для Yandex GPT
 * Использует Java HttpClient (JDK 11+) для избежания конфликтов корутин
 */
class YandexGPTProvider(
    private val config: YandexGPTConfig,
    private val tokenCounter: TokenCounter = TiktokenCounter.forGPT()
) : LLMProvider {
    
    private val logger = Logger.getInstance(YandexGPTProvider::class.java)
    
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
        logger.info("Sending request to Yandex GPT, systemPrompt length: ${systemPrompt.length}, userMessage length: ${userMessage.length}, conversationHistory size: ${conversationHistory.size}")

        return try {
            val request = buildRequest(systemPrompt, userMessage, conversationHistory, parameters)
            val response = sendWithRetry(request)

            logger.info("Request successful, tokens used: ${response.tokenUsage.totalTokens} (input: ${response.tokenUsage.inputTokens}, output: ${response.tokenUsage.outputTokens})")
            response

        } catch (e: Exception) {
            logger.error("Error sending request to Yandex GPT", e)
            LLMResponse.error("Ошибка при обращении к Yandex GPT: ${e.message}")
        }
    }
    
    override fun isAvailable(): Boolean {
        return config.apiKey.isNotBlank() && config.folderId.isNotBlank()
    }
    
    override fun getProviderName(): String = "Yandex"

    /**
     * Возвращает отображаемое имя модели для UI
     */
    fun getModelDisplayName(): String {
        return YandexModel.fromModelId(config.modelId)?.displayName ?: config.modelId
    }

    /**
     * Возвращает счётчик токенов для этого провайдера
     */
    fun getTokenCounter(): TokenCounter = tokenCounter
    
    /**
     * Строит запрос к Yandex GPT API
     */
    private fun buildRequest(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        parameters: LLMParameters
    ): YandexGPTRequest {
        val messages = buildList {
            if (systemPrompt.isNotBlank()) {
                add(YandexMessage(role = "system", text = systemPrompt))
            }

            // История диалога для сохранения контекста
            conversationHistory.forEach { convMsg ->
                val role = when (convMsg.role) {
                    ConversationRole.USER -> "user"
                    ConversationRole.ASSISTANT -> "assistant"
                    ConversationRole.SYSTEM -> "system"
                }
                if (convMsg.content.isNotBlank()) {
                    add(YandexMessage(role = role, text = convMsg.content))
                }
            }

            // Текущее сообщение пользователя
            add(YandexMessage(role = "user", text = userMessage))
        }
        return YandexGPTRequest(
            modelUri = config.modelUri,
            completionOptions = CompletionOptions(
                stream = false,
                temperature = parameters.temperature,
                maxTokens = parameters.maxTokens
            ),
            messages = messages
        )
    }
    
    /**
     * Отправляет запрос с retry логикой
     */
    private suspend fun sendWithRetry(
        request: YandexGPTRequest,
        maxRetries: Int = 3
    ): LLMResponse {
        var lastException: Exception? = null
        var currentDelay = 1000L
        
        repeat(maxRetries) { attempt ->
            try {
                // Сериализуем запрос в JSON
                val requestBody = json.encodeToString(request)
                
                // Создаем HTTP запрос
                val httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_ENDPOINT))
                    .timeout(Duration.ofSeconds(config.timeout / 1000))
                    .header("Authorization", "Api-Key ${config.apiKey}")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()
                
                // Отправляем запрос
                val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
                
                return when (httpResponse.statusCode()) {
                    200 -> {
                        val yandexResponse = json.decodeFromString<YandexGPTResponse>(httpResponse.body())
                        parseSuccessResponse(yandexResponse)
                    }
                    401 -> {
                        LLMResponse.error("Неверный API ключ. Проверьте настройки.")
                    }
                    429 -> {
                        if (attempt < maxRetries - 1) {
                            logger.warn("Rate limit exceeded, retrying after delay...")
                            delay(currentDelay)
                            currentDelay *= 2
                            throw Exception("Rate limit, retry")
                        } else {
                            LLMResponse.error("Превышен лимит запросов. Попробуйте позже.")
                        }
                    }
                    else -> {
                        logger.error("Yandex GPT API error: ${httpResponse.statusCode()}, body: ${httpResponse.body()}")
                        LLMResponse.error("Ошибка API: ${httpResponse.statusCode()}")
                    }
                }
                
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1 && shouldRetry(e)) {
                    logger.warn("Request failed, retrying... Attempt ${attempt + 1}/$maxRetries", e)
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    throw e
                }
            }
        }
        
        throw lastException ?: Exception("Unknown error")
    }
    
    /**
     * Парсит успешный ответ от Yandex GPT
     */
    private fun parseSuccessResponse(response: YandexGPTResponse): LLMResponse {
        val alternative = response.result.alternatives.firstOrNull()
            ?: return LLMResponse.error("Пустой ответ от Yandex GPT")
        
        val content = alternative.message.text
        val usage = response.result.usage
        
        val tokenUsage = TokenUsage(
            inputTokens = usage.inputTextTokens.toIntOrNull() ?: 0,
            outputTokens = usage.completionTokens.toIntOrNull() ?: 0,
            totalTokens = usage.totalTokens.toIntOrNull() ?: 0
        )
        
        return LLMResponse.success(
            content = content,
            tokenUsage = tokenUsage,
            metadata = mapOf(
                "modelVersion" to response.result.modelVersion,
                "status" to alternative.status
            )
        )
    }
    
    /**
     * Определяет, нужно ли повторить запрос при данной ошибке
     */
    private fun shouldRetry(exception: Exception): Boolean {
        return when (exception) {
            is java.net.http.HttpTimeoutException -> true
            is java.net.SocketTimeoutException -> true
            is java.io.IOException -> true
            else -> exception.message?.contains("Rate limit") == true
        }
    }
    
    companion object {
        private const val API_ENDPOINT = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
    }
}
