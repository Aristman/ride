package ru.marslab.ide.ride.integration.llm.impl

import com.intellij.openapi.diagnostic.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.LLMParameters
import ru.marslab.ide.ride.model.LLMResponse

/**
 * Конфигурация для Yandex GPT Provider
 */
data class YandexGPTConfig(
    val apiKey: String,
    val folderId: String,
    val modelUri: String = "gpt://$folderId/yandexgpt-lite/latest",
    val timeout: Long = 60000 // 60 секунд
)

/**
 * Реализация LLM провайдера для Yandex GPT
 */
class YandexGPTProvider(
    private val config: YandexGPTConfig
) : LLMProvider {
    
    private val logger = Logger.getInstance(YandexGPTProvider::class.java)
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    this@YandexGPTProvider.logger.debug(message)
                }
            }
            level = LogLevel.INFO
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeout
            connectTimeoutMillis = 10000
            socketTimeoutMillis = config.timeout
        }
        
        defaultRequest {
            header("Authorization", "Api-Key ${config.apiKey}")
            header("Content-Type", "application/json")
        }
    }
    
    override suspend fun sendRequest(prompt: String, parameters: LLMParameters): LLMResponse {
        logger.info("Sending request to Yandex GPT, prompt length: ${prompt.length}")
        
        return try {
            val request = buildRequest(prompt, parameters)
            val response = sendWithRetry(request)
            
            logger.info("Request successful, tokens used: ${response.tokensUsed}")
            response
            
        } catch (e: Exception) {
            logger.error("Error sending request to Yandex GPT", e)
            LLMResponse.error("Ошибка при обращении к Yandex GPT: ${e.message}")
        }
    }
    
    override fun isAvailable(): Boolean {
        return config.apiKey.isNotBlank() && config.folderId.isNotBlank()
    }
    
    override fun getProviderName(): String = "Yandex GPT"
    
    /**
     * Строит запрос к Yandex GPT API
     */
    private fun buildRequest(prompt: String, parameters: LLMParameters): YandexGPTRequest {
        return YandexGPTRequest(
            modelUri = config.modelUri,
            completionOptions = CompletionOptions(
                stream = false,
                temperature = parameters.temperature,
                maxTokens = parameters.maxTokens
            ),
            messages = listOf(
                YandexMessage(
                    role = "user",
                    text = prompt
                )
            )
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
                val response: io.ktor.client.statement.HttpResponse = httpClient.post(API_ENDPOINT) {
                    setBody(request)
                }
                
                return when (response.status) {
                    HttpStatusCode.OK -> {
                        val yandexResponse = response.body<YandexGPTResponse>()
                        parseSuccessResponse(yandexResponse)
                    }
                    HttpStatusCode.Unauthorized -> {
                        LLMResponse.error("Неверный API ключ. Проверьте настройки.")
                    }
                    HttpStatusCode.TooManyRequests -> {
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
                        val errorText = response.body<String>()
                        logger.error("Yandex GPT API error: ${response.status}, body: $errorText")
                        LLMResponse.error("Ошибка API: ${response.status}")
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
        val tokensUsed = response.result.usage.totalTokens.toIntOrNull() ?: 0
        
        return LLMResponse.success(
            content = content,
            tokensUsed = tokensUsed,
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
            is HttpRequestTimeoutException -> true
            is java.net.SocketTimeoutException -> true
            is java.io.IOException -> true
            else -> exception.message?.contains("Rate limit") == true
        }
    }
    
    companion object {
        private const val API_ENDPOINT = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
    }
}
