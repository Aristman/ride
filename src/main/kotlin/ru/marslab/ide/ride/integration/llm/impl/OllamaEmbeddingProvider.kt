package ru.marslab.ide.ride.integration.llm.impl

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.TokenCounter
import ru.marslab.ide.ride.model.chat.ConversationMessage
import ru.marslab.ide.ride.model.chat.ConversationRole
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.llm.TokenUsage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Ollama Provider для генерации эмбеддингов
 */
class OllamaEmbeddingProvider(
    private val config: OllamaConfig
) : LLMProvider {

    private val logger = Logger.getInstance(OllamaEmbeddingProvider::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendRequest(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        parameters: LLMParameters
    ): LLMResponse {
        return try {
            val embedding = generateEmbedding(userMessage)
            LLMResponse(
                content = "[${embedding.joinToString(", ")}]",
                success = true,
                metadata = mapOf(
                    "embedding" to embedding,
                    "dimensions" to embedding.size,
                    "model" to config.model
                ),
                tokenUsage = TokenUsage(
                    inputTokens = estimateTokens(userMessage),
                    outputTokens = embedding.size,
                    totalTokens = estimateTokens(userMessage) + embedding.size
                )
            )
        } catch (e: Exception) {
            logger.error("Error generating embedding via Ollama", e)
            LLMResponse(
                content = "",
                success = false,
                error = "Failed to generate embedding: ${e.message}",
                metadata = emptyMap(),
                tokenUsage = TokenUsage.EMPTY
            )
        }
    }

    override fun getProviderName(): String = "Ollama"

    override fun isAvailable(): Boolean {
        return try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.baseUrl}/api/tags"))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            logger.warn("Ollama service not available at ${config.baseUrl}", e)
            false
        }
    }

    /**
     * Генерирует эмбеддинг для текста через Ollama API
     */
    private suspend fun generateEmbedding(text: String): List<Float> {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
            .build()

        try {
            val requestBody = mapOf(
                "model" to config.model,
                "prompt" to text
            )

            val jsonBody = json.encodeToString(requestBody)

            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.baseUrl}/api/embeddings"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                throw RuntimeException("Ollama API returned status ${response.statusCode()}: ${response.body()}")
            }

            val responseBody = response.body()
            val parsed = json.parseToJsonElement(responseBody)
            val embeddingElement = parsed.jsonObject["embedding"]

            if (embeddingElement == null) {
                throw RuntimeException("No embedding field in response")
            }

            val embedding = when (embeddingElement) {
                is JsonArray -> embeddingElement.map { element ->
                    when (element) {
                        is JsonPrimitive -> element.content.toFloat()
                        else -> 0f
                    }
                }
                else -> throw RuntimeException("Invalid embedding format in response")
            }

            return embedding
        } catch (e: java.net.http.HttpTimeoutException) {
            throw RuntimeException("Ollama API request timed out", e)
        } catch (e: Exception) {
            throw RuntimeException("Failed to call Ollama API: ${e.message}", e)
        } finally {
            client.close()
        }
    }

    private fun estimateTokens(text: String): Int {
        // Простая оценка токенов: ~4 символа на токен
        return (text.length / 4).coerceAtLeast(1)
    }

    /**
     * Простой счетчик токенов
     */
    private class SimpleTokenCounter : TokenCounter {
        override fun countTokens(text: String): Int {
            return (text.length / 4).coerceAtLeast(1)
        }

        override fun getTokenizerName(): String = "SimpleCharCounter"
    }
}