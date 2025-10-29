package ru.marslab.ide.ride.service.rag

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import ru.marslab.ide.ride.model.embedding.FileChunkData
import ru.marslab.ide.ride.service.embedding.EmbeddingDatabaseService
import ru.marslab.ide.ride.service.embedding.EmbeddingService
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.util.TokenEstimator
import java.io.File
import java.nio.file.Paths

/**
 * Сервис для RAG (Retrieval-Augmented Generation) обогащения запросов
 *
 * Сервис выполняет поиск релевантных фрагментов в индексе эмбеддингов
 * и добавляет их в промпт перед обращением к LLM
 */
@Service(Service.Level.APP)
class RagEnrichmentService {

    private val logger = Logger.getInstance(RagEnrichmentService::class.java)
    private val settings = service<PluginSettings>()

    // Параметры берутся из PluginSettings (дефолты сконфигурированы в стейте)

    /**
     * Выполняет поиск релевантных фрагментов для запроса
     *
     * @param userQuery оригинальный запрос пользователя
     * @param maxTokens максимальное количество токенов для RAG контекста
     * @return RAGResult с найденными фрагментами или null если RAG отключен
     */
    suspend fun enrichQuery(userQuery: String, maxTokens: Int = 4000): RagResult? {
        if (!settings.enableRagEnrichment) {
            logger.debug("RAG enrichment is disabled")
            return null
        }

        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        if (project == null) {
            logger.warn("No open project found for RAG enrichment")
            return null
        }

        val projectPath = project.basePath
        if (projectPath == null) {
            logger.warn("Project path is null for RAG enrichment")
            return null
        }

        return try {
            // Проверяем наличие индекса
            val embeddingService = EmbeddingService.getInstance()
            if (!embeddingService.hasIndex(projectPath)) {
                logger.info("No embedding index found for project: $projectPath")
                return null
            }

            // Проверяем доступность Ollama
            if (!embeddingService.isOllamaAvailable()) {
                logger.warn("Ollama service is not available for RAG enrichment")
                return null
            }

            // Получаем эмбеддинг для запроса с таймаутом
            val queryEmbedding = withTimeoutOrNull(30_000) { // 30 секунд таймаут
                embeddingService.generateEmbedding(userQuery)
            }

            if (queryEmbedding == null || queryEmbedding.isEmpty()) {
                logger.warn("Failed to generate embedding for query: $userQuery (timeout or empty result)")
                return null
            }

            // Выполняем поиск похожих фрагментов с обработкой ошибок
            val candidateK = settings.ragCandidateK
            val similarChunks = try {
                findSimilarChunks(
                    projectPath,
                    queryEmbedding,
                    candidateK,
                    settings.ragSimilarityThreshold
                )
            } catch (e: Exception) {
                logger.error("Error finding similar chunks", e)
                return null
            }

            if (similarChunks.isEmpty()) {
                logger.info("No similar chunks found in embedding database")
                return null
            }

            // Фильтруем по порогу схожести
            val threshold = settings.ragSimilarityThreshold
            val filteredChunks = similarChunks.filter { (_, similarity) ->
                similarity >= threshold
            }

            if (filteredChunks.isEmpty()) {
                logger.info("No chunks found above similarity threshold $threshold")
                return null
            }

            // Получаем содержимое чанков с обработкой ошибок
            val chunksWithContent = filteredChunks.mapNotNull { (chunkId, similarity) ->
                try {
                    val chunk = getChunkContent(projectPath, chunkId)
                    if (chunk != null) {
                        val filePath = getFilePathFromChunk(projectPath, chunkId)
                        // Проверяем качество контента
                        if (chunk.content.isNotBlank() && chunk.content.length > 10) {
                            RagChunk(
                                content = chunk.content,
                                filePath = filePath,
                                startLine = chunk.startLine,
                                endLine = chunk.endLine,
                                similarity = similarity
                            )
                        } else {
                            logger.debug("Skipping chunk $chunkId: content too short or empty")
                            null
                        }
                    } else {
                        logger.warn("Failed to get chunk content for ID: $chunkId")
                        null
                    }
                } catch (e: Exception) {
                    logger.error("Error processing chunk $chunkId", e)
                    null
                }
            }

            if (chunksWithContent.isEmpty()) {
                logger.warn("No valid chunk content found after filtering")
                return null
            }

            // Сортируем по релевантности и ограничиваем по токенам
            val sortedChunks = chunksWithContent.sortedByDescending { it.similarity }
                .let { list ->
                    val topK = settings.ragTopK
                    if (list.size > topK) list.take(topK) else list
                }
            val limitedChunks = try {
                limitChunksByTokens(sortedChunks, maxTokens)
            } catch (e: Exception) {
                logger.error("Error limiting chunks by tokens", e)
                // В случае ошибки, берем первые 3 чанка
                sortedChunks.take(3)
            }

            if (limitedChunks.isEmpty()) {
                logger.warn("No chunks remaining after token limiting")
                return null
            }

            logger.info(
                "RAG enrichment successful: ${limitedChunks.size} chunks, ${estimateTokens(limitedChunks)} tokens (strategy=THRESHOLD, candidateK=$candidateK, topK=${settings.ragTopK}, threshold=$threshold)"
            )

            RagResult(
                chunks = limitedChunks,
                totalTokens = estimateTokens(limitedChunks),
                query = userQuery
            )

        } catch (e: TimeoutCancellationException) {
            logger.warn("RAG enrichment timed out for query: ${userQuery.take(50)}...")
            null
        } catch (e: Exception) {
            logger.error("Unexpected error during RAG enrichment", e)
            null
        }
    }

    /**
     * Форматирует RAG результат в текстовый блок для добавления в промпт
     */
    fun formatRagContext(ragResult: RagResult): String {
        if (ragResult.chunks.isEmpty()) return ""

        val context = StringBuilder()
        context.appendLine("=== Retrieved Context ===")
        context.appendLine("Ниже приведены релевантные фрагменты из проекта, которые могут помочь ответить на ваш вопрос:")

        ragResult.chunks.forEachIndexed { index, chunk ->
            context.appendLine("\n${index + 1}. Фрагмент из: ${chunk.filePath}:${chunk.startLine}-${chunk.endLine} (сходство: ${(chunk.similarity * 100).toInt()}%)")
            context.appendLine("```")
            context.appendLine(chunk.content.trim())
            context.appendLine("```")
        }

        context.appendLine("=== End of Context ===")
        return context.toString()
    }

    /**
     * Создает обогащенный промпт с RAG контекстом
     */
    fun createEnrichedPrompt(
        systemPrompt: String,
        userQuery: String,
        ragResult: RagResult?
    ): String {
        if (ragResult == null || ragResult.chunks.isEmpty()) {
            return userQuery
        }

        val ragContext = formatRagContext(ragResult)

        return """
            $ragContext

            На основе предоставленного выше контекста, пожалуйста, ответьте на следующий вопрос:

            **Вопрос:** $userQuery

            **Инструкции:**
            - Используйте предоставленный контекст для информирования ответа
            - Если контекст не содержит достаточной информации, дополните ответ своими знаниями
            - Укажите на конкретные фрагменты кода из контекста, когда это релевантно
            - Если нужно уточнить детали вопроса на основе контекста, задайте уточняющие вопросы
        """.trimIndent()
    }

    // Приватные методы

    private suspend fun findSimilarChunks(
        projectPath: String,
        queryEmbedding: List<Float>,
        topK: Int,
        minSimilarity: Float? = null
    ): List<Pair<Long, Float>> {
        val embeddingService = EmbeddingService.getInstance()
        val dbService = embeddingService.getDatabaseService(projectPath)

        return try {
            val result = dbService?.findSimilarEmbeddings(queryEmbedding, topK, minSimilarity) ?: emptyList()
            dbService?.close()
            result
        } catch (e: Exception) {
            logger.error("Error finding similar chunks", e)
            emptyList()
        }
    }

    private fun getChunkContent(projectPath: String, chunkId: Long): FileChunkData? {
        val embeddingService = EmbeddingService.getInstance()
        val dbService = embeddingService.getDatabaseService(projectPath)

        return try {
            val result = dbService?.getChunkById(chunkId)
            dbService?.close()
            result
        } catch (e: Exception) {
            logger.error("Error getting chunk content for ID: $chunkId", e)
            null
        }
    }

    private fun getFilePathFromChunk(projectPath: String, chunkId: Long): String {
        return try {
            val embeddingService = EmbeddingService.getInstance()
            val dbService = embeddingService.getDatabaseService(projectPath)

            if (dbService != null) {
                val filePath = dbService.getFilePathByChunkId(chunkId)
                dbService.close()

                filePath ?: "unknown_file"
            } else {
                "unknown_file"
            }
        } catch (e: Exception) {
            logger.error("Error getting file path for chunk $chunkId", e)
            "unknown_file"
        }
    }

    private fun limitChunksByTokens(chunks: List<RagChunk>, maxTokens: Int): List<RagChunk> {
        if (chunks.isEmpty()) return emptyList()

        val result = mutableListOf<RagChunk>()
        var currentTokens = 0
        val minChunkTokens = 50 // Минимальное количество токенов на чанк

        for (chunk in chunks) {
            val chunkTokens = TokenEstimator.estimateTokens(chunk.content)

            // Пропускаем слишком маленькие чанки (шум)
            if (chunkTokens < minChunkTokens) {
                continue
            }

            // Если добавление чанка превысит лимит, пытаемся обрезать чанк
            if (currentTokens + chunkTokens > maxTokens) {
                val remainingTokens = maxTokens - currentTokens

                // Если осталось достаточно места для усеченного чанка
                if (remainingTokens >= minChunkTokens) {
                    val truncatedChunk = truncateChunk(chunk, remainingTokens)
                    if (truncatedChunk != null) {
                        result.add(truncatedChunk)
                        logger.debug("Truncated chunk to fit token limit: $remainingTokens tokens")
                    }
                }
                break
            }

            result.add(chunk)
            currentTokens += chunkTokens
        }

        logger.debug("Selected ${result.size} chunks with $currentTokens tokens (limit: $maxTokens)")
        return result
    }

    /**
     * Усекает чанк до указанного количества токенов, пытаясь сохранить целостность
     */
    private fun truncateChunk(chunk: RagChunk, maxTokens: Int): RagChunk? {
        val content = chunk.content
        val estimatedTokens = TokenEstimator.estimateTokens(content)

        if (estimatedTokens <= maxTokens) {
            return chunk
        }

        // Простая усечка по символам (примерно 4 символа на токен)
        val targetChars = (maxTokens * 4).coerceAtMost(content.length)
        val truncatedContent = content.take(targetChars)

        // Пытаемся найти подходящее место для разрыва (конец предложения или строки)
        val sentenceEnds = listOf('.', '!', '?', '\n')
        val lastGoodEnd = truncatedContent.indexOfLast { it in sentenceEnds }

        val finalContent = if (lastGoodEnd > truncatedContent.length * 0.7) {
            truncatedContent.take(lastGoodEnd + 1) + "..."
        } else {
            truncatedContent + "..."
        }

        return chunk.copy(
            content = finalContent,
            similarity = chunk.similarity * 0.8f // Незначительно снижаем релевантность
        )
    }

    private fun estimateTokens(chunks: List<RagChunk>): Int {
        return chunks.sumOf { TokenEstimator.estimateTokens(it.content) }
    }
}

/**
 * Результат RAG обогащения
 */
data class RagResult(
    val chunks: List<RagChunk>,
    val totalTokens: Int,
    val query: String
)

/**
 * Фрагмент кода, найденный через RAG
 */
data class RagChunk(
    val content: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val similarity: Float
)