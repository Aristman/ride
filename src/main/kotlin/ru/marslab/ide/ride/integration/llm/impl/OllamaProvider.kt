package ru.marslab.ide.ride.integration.llm.impl

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.put
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
 * Ollama Provider для генерации текста (не эмбеддингов)
 */
class OllamaProvider(
    private val config: OllamaConfig
) : LLMProvider {

    private val logger = Logger.getInstance(OllamaProvider::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /**
         * Получает список доступных моделей в Ollama
         */
        suspend fun getAvailableModels(baseUrl: String): List<String> {
            return try {
                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()

                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/api/tags"))
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) {
                    return emptyList()
                }

                val modelsResponse = Json { ignoreUnknownKeys = true }.parseToJsonElement(response.body())
                modelsResponse.jsonObject["models"]?.jsonArray?.mapNotNull { model ->
                    model.jsonObject["model"]?.jsonPrimitive?.content
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Проверяет доступность Ollama сервера
         */
        suspend fun isServerAvailable(baseUrl: String): Boolean {
            return try {
                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build()

                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/api/tags"))
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                response.statusCode() == 200
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun sendRequest(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        parameters: LLMParameters
    ): LLMResponse {
        return try {
            val requestBody = buildRequestBody(systemPrompt, userMessage, conversationHistory, parameters)

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                .build()

            val jsonBody = json.encodeToString(requestBody)

            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.baseUrl}/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                throw RuntimeException("Ollama API returned status ${response.statusCode()}: ${response.body()}")
            }

            val responseBody = response.body()
            logger.info("Ollama API response: $responseBody")
            val parsed = json.parseToJsonElement(responseBody)

            // Ollama chat API возвращает ответ в формате {"message": {"role": "assistant", "content": "..."}, "done": true, ...}
            val responseText = when (parsed) {
                is JsonObject -> {
                    // Для chat API используем message.content
                    val messageContent = parsed["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                    if (!messageContent.isNullOrBlank()) {
                        messageContent
                    } else {
                        // Fallback для generate API
                        val directResponse = parsed["response"]?.jsonPrimitive?.content
                        if (!directResponse.isNullOrBlank()) {
                            directResponse
                        } else if (parsed["done"]?.jsonPrimitive?.content == "true") {
                            // Если done=true но response пуст, пробуем другие поля
                            parsed["content"]?.jsonPrimitive?.content ?: ""
                        } else {
                            // Если streaming - нужно собрать все части
                            collectStreamingResponse(responseBody)
                        }
                    }
                }
                else -> ""
            }

            logger.info("Ollama parsed response text: '${responseText.take(200)}${if (responseText.length > 200) "..." else ""}'")

            val inputTokens = estimateTokens(systemPrompt + userMessage + conversationHistory.joinToString("\n") { it.content })
            val outputTokens = estimateTokens(responseText)

            LLMResponse(
                content = responseText,
                success = true,
                metadata = mapOf(
                    "model" to config.model,
                    "baseUrl" to config.baseUrl
                ),
                tokenUsage = TokenUsage(
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = inputTokens + outputTokens
                )
            )
        } catch (e: Exception) {
            logger.error("Error generating text via Ollama", e)
            LLMResponse(
                content = "",
                success = false,
                error = "Failed to generate text: ${e.message}",
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

            // Проверяем доступность сервера
            val tagsRequest = HttpRequest.newBuilder()
                .uri(URI.create("${config.baseUrl}/api/tags"))
                .GET()
                .build()

            val tagsResponse = client.send(tagsRequest, HttpResponse.BodyHandlers.ofString())
            if (tagsResponse.statusCode() != 200) {
                return false
            }

            // Проверяем наличие модели с гибким matching
            val modelsResponse = json.parseToJsonElement(tagsResponse.body())
            val models = modelsResponse.jsonObject["models"]?.jsonArray ?: return false

            val availableModels = models.mapNotNull { model ->
                model.jsonObject["model"]?.jsonPrimitive?.content
            }

            // Гибкий поиск модели
            val modelExists = availableModels.any { modelName ->
                // Точное совпадение
                modelName == config.model ||
                // С :latest суффиксом
                modelName == "${config.model}:latest" ||
                // Без суффикса если в config.model есть :latest
                config.model.endsWith(":latest") && modelName == config.model.substringBeforeLast(":") ||
                // Базовое имя совпадает
                modelName.startsWith(config.model.split(":").first()) ||
                config.model.split(":").first().startsWith(modelName.split(":").first())
            }

            if (!modelExists) {
                logger.warn("Model '${config.model}' not found in Ollama. Available models: ${availableModels.joinToString(", ")}")
                logger.info("Try installing: ollama pull ${config.model}")
            }

            modelExists
        } catch (e: Exception) {
            logger.warn("Ollama service not available at ${config.baseUrl}", e)
            false
        }
    }

    private fun buildRequestBody(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        parameters: LLMParameters
    ): JsonObject {
        val messages = mutableListOf<JsonObject>()

        // Добавляем системный промпт
        if (systemPrompt.isNotBlank()) {
            messages.add(
                buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
            )
        }

        // Добавляем историю диалога
        conversationHistory.forEach { message ->
            val role = when (message.role) {
                ConversationRole.USER -> "user"
                ConversationRole.ASSISTANT -> "assistant"
                ConversationRole.SYSTEM -> "system"
            }
            messages.add(
                buildJsonObject {
                    put("role", role)
                    put("content", message.content)
                }
            )
        }

        // Добавляем текущее сообщение пользователя
        messages.add(
            buildJsonObject {
                put("role", "user")
                put("content", userMessage)
            }
        )

        return buildJsonObject {
            put("model", config.model)
            put("messages", JsonArray(messages))
            put("stream", false)
            put("options", buildJsonObject {
                put("temperature", parameters.temperature)
                put("num_predict", parameters.maxTokens)
            })
        }
    }

    private fun collectStreamingResponse(responseBody: String): String {
        // Для простоты обработки, если ответ streaming, пытаемся извлечь все части response
        return try {
            val lines = responseBody.split("\n")
            lines.mapNotNull { line ->
                if (line.isNotBlank()) {
                    try {
                        val parsed = json.parseToJsonElement(line)
                        // Пробуем разные поля где может быть текст
                        parsed.jsonObject["response"]?.jsonPrimitive?.content
                            ?: parsed.jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                            ?: parsed.jsonObject["content"]?.jsonPrimitive?.content
                    } catch (e: Exception) {
                        logger.debug("Failed to parse streaming line: $line", e)
                        null
                    }
                } else null
            }.joinToString("")
        } catch (e: Exception) {
            logger.warn("Failed to parse streaming response", e)
            ""
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