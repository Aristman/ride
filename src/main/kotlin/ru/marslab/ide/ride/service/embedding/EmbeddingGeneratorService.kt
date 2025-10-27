package ru.marslab.ide.ride.service.embedding

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import ru.marslab.ide.ride.agent.LLMProviderFactory
import ru.marslab.ide.ride.model.embedding.FileChunkData
import ru.marslab.ide.ride.model.embedding.IndexingConfig
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.settings.PluginSettings
import java.io.File
import java.security.MessageDigest

/**
 * Сервис для генерации эмбеддингов и разбивки файлов на чанки
 */
class EmbeddingGeneratorService(
    private val config: IndexingConfig,
    private val settings: PluginSettings
) {

    private val logger = Logger.getInstance(EmbeddingGeneratorService::class.java)

    /**
     * Разбивка файла на чанки
     */
    fun chunkFile(file: File, fileId: Long): List<FileChunkData> {
        if (!file.exists() || !file.isFile) {
            logger.warn("File does not exist or is not a file: ${file.absolutePath}")
            return emptyList()
        }

        val chunks = mutableListOf<FileChunkData>()
        var chunkIndex = 0
        var currentChunkChars = 0
        val currentChunkLines = mutableListOf<String>()
        var lineNumber = 0
        var startLineForChunk = 0

        val overlapLines = if (config.chunkOverlap > 0) (config.chunkOverlap / 50).coerceAtLeast(1) else 0

        try {
            file.bufferedReader().useLines { sequence ->
                sequence.forEach { line ->
                    val addLen = line.length + 1 // +1 for newline
                    // Если добавление строки превысит размер чанка и текущий чанк не пустой — финализируем чанк
                    if (currentChunkChars + addLen > config.chunkSize && currentChunkLines.isNotEmpty()) {
                        chunks.add(
                            FileChunkData(
                                fileId = fileId,
                                chunkIndex = chunkIndex,
                                content = currentChunkLines.joinToString("\n"),
                                startLine = startLineForChunk,
                                endLine = lineNumber - 1
                            )
                        )
                        chunkIndex++

                        // Перекрытие: переносим хвост последних overlapLines строк в начало следующего чанка
                        if (overlapLines > 0) {
                            val tail = currentChunkLines.takeLast(overlapLines)
                            currentChunkLines.clear()
                            currentChunkLines.addAll(tail)
                            currentChunkChars = tail.sumOf { it.length + 1 }
                            startLineForChunk = (lineNumber - overlapLines).coerceAtLeast(0)
                        } else {
                            currentChunkLines.clear()
                            currentChunkChars = 0
                            startLineForChunk = lineNumber
                        }
                    }

                    // Добавляем строку в текущий чанк
                    currentChunkLines.add(line)
                    currentChunkChars += addLen
                    if (currentChunkLines.size == 1) {
                        startLineForChunk = lineNumber
                    }

                    lineNumber++
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to read file: ${file.absolutePath}", e)
            return emptyList()
        }

        // Финализируем оставшийся чанк
        if (currentChunkLines.isNotEmpty()) {
            chunks.add(
                FileChunkData(
                    fileId = fileId,
                    chunkIndex = chunkIndex,
                    content = currentChunkLines.joinToString("\n"),
                    startLine = startLineForChunk,
                    endLine = (lineNumber - 1).coerceAtLeast(startLineForChunk)
                )
            )
        }

        return chunks
    }
    /**
     * Генерация эмбеддинга для текста через LLM провайдера.
     * Требования:
     * - Строгий JSON-массив длиной 100 (float числа)
     * - Сниженная точность чисел (не более 4 знаков после запятой) для уменьшения объёма ответа
     * - Используется отдельный провайдер/модель из Code Settings
     */
    suspend fun generateEmbedding(text: String): List<Float> {
        return try {
            val provider = LLMProviderFactory.createLLMProviderFor(
                settings.embeddingProvider,
                settings.embeddingModelId
            )

            if (!provider.isAvailable()) {
                logger.error("Embedding LLM provider is not configured")
                return emptyList()
            }

            // Урезаем вход, чтобы снизить латентность/таймауты
            val maxInputChars = 3000
            val inputText = if (text.length > maxInputChars) text.take(maxInputChars) else text

            val systemPrompt = buildString {
                appendLine("You are an embedding generator.")
                appendLine("Return ONLY a strict JSON array of 100 float numbers representing the embedding of the given text.")
                appendLine("Do not include any explanation, keys, or extra characters. Output must be like: [0.1234, -0.5678, ...].")
                appendLine("Array length must be exactly 100. Use at most 4 decimal places for each number.")
            }

            val response = provider.sendRequest(
                systemPrompt = systemPrompt,
                userMessage = inputText,
                conversationHistory = emptyList(),
                parameters = LLMParameters(temperature = 0.0, maxTokens = 4096)
            )

            val content = response.content.orEmpty().trim()
            parseEmbeddingArray(content, expectedDim = 100)
        } catch (e: Exception) {
            logger.error("Failed to generate embedding via LLM", e)
            emptyList()
        }
    }

    /**
     * Батч-генерация эмбеддингов. Возвращает список векторов той же длины, что и входной список.
     */
    suspend fun generateEmbeddings(texts: List<String>): List<List<Float>> {
        // Простейшая последовательная батч-обработка; при необходимости можно распараллелить
        val result = ArrayList<List<Float>>(texts.size)
        for (t in texts) {
            result.add(generateEmbedding(t))
        }
        return result
    }

    /**
     * Разбор строгого JSON массива чисел и нормализация до единичной нормы.
     */
    private fun parseEmbeddingArray(jsonText: String, expectedDim: Int): List<Float> {
        return try {
            val parsed = Json.parseToJsonElement(jsonText)
            val arr: JsonArray = parsed.jsonArray
            // Приводим размер к ожидаемому: обрезаем лишнее или дополняем нулями
            val rawValues = arr.map { elem ->
                val prim: JsonPrimitive = elem.jsonPrimitive
                val d = prim.content.toDouble()
                d.toFloat()
            }
            val vector = when {
                rawValues.size > expectedDim -> rawValues.take(expectedDim)
                rawValues.size < expectedDim -> rawValues + List(expectedDim - rawValues.size) { 0f }
                else -> rawValues
            }
            // L2-нормализация
            val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
            if (norm > 0f) vector.map { it / norm } else vector
        } catch (e: Exception) {
            logger.error("Failed to parse embedding JSON", e)
            emptyList()
        }
    }

    /**
     * Вычисление хеша файла
     */
    fun calculateFileHash(file: File): String {
        if (!file.exists()) return ""

        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = file.readBytes()
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.error("Failed to calculate file hash", e)
            ""
        }
    }

    /**
     * Проверка, должен ли файл быть проиндексирован
     */
    fun shouldIndexFile(file: File): Boolean {
        val path = file.absolutePath

        // Проверка exclude паттернов
        if (config.excludePatterns.any { pattern ->
                matchesPattern(path, pattern)
            }) {
            return false
        }

        // Проверка include паттернов
        return config.includePatterns.any { pattern ->
            matchesPattern(path, pattern)
        }
    }

    /**
     * Простое сопоставление с паттерном (поддержка ** и *)
     */
    private fun matchesPattern(path: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .toRegex()
        return regex.matches(path)
    }

    /**
     * Получение размерности эмбеддингов
     */
    fun getEmbeddingDimension(): Int {
        // Размерность эмбеддинга выбирается как 100 согласно требованиям
        return 100
    }
}
