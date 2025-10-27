package ru.marslab.ide.ride.service.embedding

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.LLMProviderFactory
import ru.marslab.ide.ride.model.embedding.FileChunkData
import ru.marslab.ide.ride.model.embedding.IndexingConfig
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

        val content = try {
            file.readText()
        } catch (e: Exception) {
            logger.error("Failed to read file: ${file.absolutePath}", e)
            return emptyList()
        }

        val lines = content.lines()
        val chunks = mutableListOf<FileChunkData>()
        var chunkIndex = 0
        var currentPos = 0

        while (currentPos < lines.size) {
            val chunkLines = mutableListOf<String>()
            var chunkSize = 0
            var startLine = currentPos

            // Собираем строки до достижения размера чанка
            while (currentPos < lines.size && chunkSize < config.chunkSize) {
                val line = lines[currentPos]
                chunkLines.add(line)
                chunkSize += line.length + 1 // +1 для символа новой строки
                currentPos++
            }

            if (chunkLines.isNotEmpty()) {
                chunks.add(
                    FileChunkData(
                        fileId = fileId,
                        chunkIndex = chunkIndex,
                        content = chunkLines.joinToString("\n"),
                        startLine = startLine,
                        endLine = currentPos - 1
                    )
                )
                chunkIndex++
            }

            // Применяем перекрытие
            if (config.chunkOverlap > 0 && currentPos < lines.size) {
                val overlapLines = (config.chunkOverlap / 50).coerceAtLeast(1) // Примерно 50 символов на строку
                currentPos -= overlapLines.coerceAtMost(chunkLines.size)
            }
        }

        return chunks
    }

    /**
     * Генерация эмбеддинга для текста
     * 
     * Примечание: Это упрощенная реализация.
     * В реальности нужно использовать API провайдера эмбеддингов (OpenAI, Cohere и т.д.)
     * Для демонстрации используется простое хеширование + нормализация
     */
    suspend fun generateEmbedding(text: String): List<Float> {
        return try {
            // TODO: Интеграция с реальным API эмбеддингов
            // Пока используем простую хеш-функцию для демонстрации
            generateSimpleEmbedding(text)
        } catch (e: Exception) {
            logger.error("Failed to generate embedding", e)
            emptyList()
        }
    }

    /**
     * Простая генерация эмбеддинга на основе хеша
     * Это временное решение для демонстрации
     */
    private fun generateSimpleEmbedding(text: String, dimension: Int = 384): List<Float> {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(text.toByteArray())

        // Расширяем хеш до нужной размерности
        val embedding = mutableListOf<Float>()
        var hashIndex = 0

        for (i in 0 until dimension) {
            val byteValue = hash[hashIndex % hash.size].toInt() and 0xFF
            embedding.add((byteValue - 128) / 128f) // Нормализация в диапазон [-1, 1]
            hashIndex++
        }

        // Нормализуем вектор
        val norm = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0) embedding.map { it / norm } else embedding
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
        // TODO: Зависит от используемой модели
        return 384 // Стандартная размерность для многих моделей
    }
}
