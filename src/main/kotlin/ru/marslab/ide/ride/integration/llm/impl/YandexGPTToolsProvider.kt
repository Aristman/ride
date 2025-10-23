package ru.marslab.ide.ride.integration.llm.impl

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.marslab.ide.ride.model.llm.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Yandex GPT Provider с поддержкой Tools API
 */
class YandexGPTToolsProvider(
    private val config: YandexGPTConfig
) {

    private val logger = Logger.getInstance(YandexGPTToolsProvider::class.java)

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    /**
     * Отправить запрос с tools
     */
    suspend fun sendRequestWithTools(
        messages: List<YandexToolsMessage>,
        tools: List<Tool>,
        parameters: LLMParameters,
        parallelToolCalls: Boolean = false
    ): YandexGPTToolsResponse {
        logger.info("Sending request with ${tools.size} tools")
        println("🔧 YandexGPT: Sending request with ${tools.size} tools")

        // Логируем доступные tools
        tools.forEach { tool ->
            println("  📋 Tool: ${tool.function.name} - ${tool.function.description}")
        }

        val request = YandexGPTToolsRequest(
            modelUri = config.modelUri,
            completionOptions = CompletionOptions(
                stream = false,
                temperature = parameters.temperature,
                maxTokens = parameters.maxTokens.toString()
            ),
            messages = messages,
            tools = tools,
            parallelToolCalls = parallelToolCalls
        )

        return sendWithRetry(request)
    }

    /**
     * Отправить запрос с retry логикой
     */
    private suspend fun sendWithRetry(
        request: YandexGPTToolsRequest,
        maxRetries: Int = 3
    ): YandexGPTToolsResponse {
        var lastException: Exception? = null
        var currentDelay = 1000L

        repeat(maxRetries) { attempt ->
            try {
                val requestBody = json.encodeToString(request)

                println("📤 YandexGPT Request JSON:")
                println(requestBody)
                logger.debug("Request body: $requestBody")

                val httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_ENDPOINT))
                    .timeout(Duration.ofSeconds(config.timeout / 1000))
                    .header("Authorization", "Api-Key ${config.apiKey}")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()

                val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

                return when (httpResponse.statusCode()) {
                    200 -> {
                        val responseBody = httpResponse.body()
                        println("📥 YandexGPT Response JSON:")
                        println(responseBody)
                        logger.debug("Response body: $responseBody")
                        json.decodeFromString<YandexGPTToolsResponse>(responseBody)
                    }

                    401 -> {
                        throw Exception("Invalid API key")
                    }

                    429 -> {
                        if (attempt < maxRetries - 1) {
                            logger.warn("Rate limit exceeded, retrying after delay...")
                            delay(currentDelay)
                            currentDelay *= 2
                            throw Exception("Rate limit, retry")
                        } else {
                            throw Exception("Rate limit exceeded")
                        }
                    }

                    else -> {
                        logger.error("Yandex GPT API error: ${httpResponse.statusCode()}, body: ${httpResponse.body()}")
                        throw Exception("API error: ${httpResponse.statusCode()}")
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
