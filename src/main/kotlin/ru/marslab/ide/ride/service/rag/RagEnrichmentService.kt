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

            // Ветвление по стратегии реранкера
            val strategy = settings.ragRerankerStrategy
            val selectedIdsWithSim: List<Pair<Long, Float>> = if (strategy == "MMR") {
                // Выполним MMR отбор поверх отфильтрованных кандидатов
                val mmrTopK = settings.ragMmrTopK
                val lambda = settings.ragMmrLambda
                val candidateIds = filteredChunks.map { it.first }

                // Загружаем эмбеддинги кандидатов
                val candidateEmbeddings: Map<Long, List<Float>> = candidateIds.mapNotNull { id ->
                    try {
                        getChunkEmbedding(projectPath, id)?.let { id to it }
                    } catch (e: Exception) {
                        logger.warn("Failed to load embedding for chunk id=$id", e)
                        null
                    }
                }.toMap()

                // Преобразуем queryEmbedding в FloatArray
                val queryArr = FloatArray(queryEmbedding.size) { i -> queryEmbedding[i] }

                val simsToQuery: Map<Long, Float> = filteredChunks.toMap()
                val selected = mmrSelect(candidateEmbeddings, simsToQuery, queryArr, mmrTopK, lambda)
                selected.map { id -> id to (simsToQuery[id] ?: 0f) }
            } else {
                // THRESHOLD: сортировка по similarity и урезание до topK выполнится ниже
                filteredChunks
            }

            // Получаем содержимое выбранных чанков
            val chunksWithContent = selectedIdsWithSim.mapNotNull { (chunkId, similarity) ->
                try {
                    val chunk = getChunkContent(projectPath, chunkId)
                    if (chunk != null) {
                        val filePath = getFilePathFromChunk(projectPath, chunkId)
                        if (chunk.content.isNotBlank() && chunk.content.length >= 5) {
                            RagChunk(
                                content = chunk.content,
                                filePath = filePath ?: "",
                                startLine = chunk.startLine,
                                endLine = chunk.endLine,
                                similarity = similarity
                            )
                        } else null
                    } else null
                } catch (e: Exception) {
                    logger.warn("Failed to load chunk content for id=$chunkId", e)
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
                    val topK = if (strategy == "MMR") settings.ragMmrTopK else settings.ragTopK
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

            val metaTopK = if (strategy == "MMR") settings.ragMmrTopK else settings.ragTopK
            logger.info(
                "RAG enrichment successful: ${limitedChunks.size} chunks, ${estimateTokens(limitedChunks)} tokens (strategy=$strategy, candidateK=$candidateK, topK=$metaTopK, threshold=$threshold)"
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

    // --- MMR helpers ---
    private fun mmrSelect(
        embeddings: Map<Long, List<Float>>,
        simsToQuery: Map<Long, Float>,
        query: FloatArray,
        k: Int,
        lambda: Float
    ): List<Long> {
        if (embeddings.isEmpty() || k <= 0) return emptyList()
        val selected = mutableListOf<Long>()
        val candidates = embeddings.keys.toMutableSet()

        // стартуем с лучшего по релевантности к запросу
        val first = candidates.maxByOrNull { simsToQuery[it] ?: 0f } ?: return emptyList()
        selected.add(first)
        candidates.remove(first)

        while (selected.size < k && candidates.isNotEmpty()) {
            var bestId: Long? = null
            var bestScore = Float.NEGATIVE_INFINITY
            for (cand in candidates) {
                val rel = simsToQuery[cand] ?: 0f
                var div = 0f
                val eCand = embeddings[cand]
                if (eCand != null) {
                    for (s in selected) {
                        val eSel = embeddings[s] ?: continue
                        val simDS = cosine(eCand, eSel)
                        if (simDS > div) div = simDS
                    }
                }
                val score = lambda * rel - (1 - lambda) * div
                if (score > bestScore) {
                    bestScore = score
                    bestId = cand
                }
            }
            if (bestId == null) break
            selected.add(bestId)
            candidates.remove(bestId)
        }
        return selected
    }

    private fun cosine(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            val av = a[i]
            val bv = b[i]
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    private fun getChunkEmbedding(projectPath: String, chunkId: Long): List<Float>? {
        val embeddingService = EmbeddingService.getInstance()
        val dbService = embeddingService.getDatabaseService(projectPath)
        return try {
            val res = dbService?.getEmbeddingByChunkId(chunkId)
            dbService?.close()
            res
        } catch (e: Exception) {
            logger.error("Error reading embedding for chunk=$chunkId", e)
            null
        }
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