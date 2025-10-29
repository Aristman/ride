package ru.marslab.ide.ride.service.embedding

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import ru.marslab.ide.ride.integration.llm.impl.OllamaConfig
import ru.marslab.ide.ride.integration.llm.impl.OllamaEmbeddingProvider
import ru.marslab.ide.ride.integration.llm.impl.OllamaEmbeddingModel
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.settings.PluginSettings
import java.io.File

/**
 * Сервис для работы с эмбеддингами
 *
 * Предоставляет унифицированный интерфейс для генерации эмбеддингов
 * и доступа к базе данных эмбеддингов
 */
@Service(Service.Level.APP)
class EmbeddingService {

    private val logger = Logger.getInstance(EmbeddingService::class.java)
    private val ollamaConfig = OllamaConfig()
    private val ollamaProvider = OllamaEmbeddingProvider(ollamaConfig)

    companion object {
        fun getInstance(): EmbeddingService = service()
    }

    /**
     * Генерирует эмбеддинг для текста
     *
     * @param text текст для эмбеддинга
     * @return список float или пустой список в случае ошибки
     */
    suspend fun generateEmbedding(text: String): List<Float> {
        if (text.isBlank()) {
            logger.warn("Cannot generate embedding for empty text")
            return emptyList()
        }

        return try {
            // Проверяем доступность Ollama с таймаутом
            if (!ollamaProvider.isAvailable()) {
                logger.warn("Ollama service is not available for embedding generation")
                emptyList()
            } else {
                val response = ollamaProvider.sendRequest(
                    systemPrompt = "",
                    userMessage = text.trim(),
                    conversationHistory = emptyList(),
                    LLMParameters.DEFAULT
                )

                if (response.success) {
                    @Suppress("UNCHECKED_CAST")
                    val embedding = response.metadata["embedding"] as? List<Float>

                    if (embedding != null && embedding.isNotEmpty()) {
                        // Проверяем размерность эмбеддинга
                        val expectedDimensions = OllamaEmbeddingModel.NOMIC_EMBED_TEXT.dimensions
                        if (embedding.size == expectedDimensions) {
                            logger.debug("Generated embedding with ${embedding.size} dimensions")
                            embedding
                        } else {
                            logger.warn("Embedding dimension mismatch: expected $expectedDimensions, got ${embedding.size}")
                            emptyList()
                        }
                    } else {
                        logger.warn("No embedding data found in response metadata")
                        emptyList()
                    }
                } else {
                    logger.error("Failed to generate embedding: ${response.error}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            logger.error("Error generating embedding for text: ${text.take(50)}...", e)
            emptyList()
        }
    }

    /**
     * Проверяет наличие индекса для проекта
     *
     * @param projectPath путь к проекту
     * @return true если индекс существует и доступен
     */
    fun hasIndex(projectPath: String): Boolean {
        return try {
            val dbPath = getEmbeddingDbPath(projectPath)
            val dbFile = File(dbPath)
            dbFile.exists() && dbFile.canRead()
        } catch (e: Exception) {
            logger.error("Error checking index existence", e)
            false
        }
    }

    /**
     * Получает сервис для работы с базой данных эмбеддингов
     *
     * @param projectPath путь к проекту
     * @return EmbeddingDatabaseService или null в случае ошибки
     */
    fun getDatabaseService(projectPath: String): EmbeddingDatabaseService? {
        if (projectPath.isBlank()) {
            logger.warn("Cannot create database service for empty project path")
            return null
        }

        return try {
            val dbPath = getEmbeddingDbPath(projectPath)
            val dbFile = File(dbPath)

            // Проверяем что директория существует или может быть создана
            val parentDir = dbFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                val created = parentDir.mkdirs()
                if (!created) {
                    logger.error("Failed to create database directory: ${parentDir.absolutePath}")
                    return null
                }
            }

            EmbeddingDatabaseService(dbPath)
        } catch (e: Exception) {
            logger.error("Error creating database service for project: $projectPath", e)
            null
        }
    }

    /**
     * Получает статистику индекса для проекта
     *
     * @param projectPath путь к проекту
     * @return статистика или null в случае ошибки
     */
    fun getIndexStatistics(projectPath: String): Map<String, Int>? {
        return try {
            val dbService = getDatabaseService(projectPath)
            dbService?.getStatistics()?.also {
                dbService.close()
            }
        } catch (e: Exception) {
            logger.error("Error getting index statistics", e)
            null
        }
    }

    /**
     * Проверяет доступность Ollama сервиса
     *
     * @return true если Ollama доступен
     */
    fun isOllamaAvailable(): Boolean {
        return ollamaProvider.isAvailable()
    }

    // Приватные методы

    private fun getEmbeddingDbPath(projectPath: String): String {
        // Создаем путь к базе данных эмбеддингов в директории .ride/embeddings
        val rideDir = File(projectPath, ".ride")
        val embeddingsDir = File(rideDir, "embeddings")
        if (!embeddingsDir.exists()) {
            embeddingsDir.mkdirs()
        }
        return File(embeddingsDir, "embeddings.db").absolutePath
    }
}