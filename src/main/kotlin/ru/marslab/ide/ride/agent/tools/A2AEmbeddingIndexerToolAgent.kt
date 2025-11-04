package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.BaseA2AAgent
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.model.orchestrator.AgentType
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * A2A агент для индексации встраиваний (embeddings)
 *
 * Создает индекс встраиваний для файлов проекта, что позволяет
 * выполнять семантический поиск и контекстное обогащение.
 */
class A2AEmbeddingIndexerToolAgent : BaseA2AAgent(
    agentType = AgentType.EMBEDDING_INDEXER,
    a2aAgentId = "a2a-embedding-indexer-agent",
    supportedMessageTypes = setOf("EMBEDDING_INDEX_REQUEST", "EMBEDDING_SEARCH_REQUEST", "EMBEDDING_CLEAR_REQUEST"),
    publishedEventTypes = setOf("TOOL_EXECUTION_STARTED", "TOOL_EXECUTION_COMPLETED", "TOOL_EXECUTION_FAILED")
) {

    // Простая in-memory база данных для хранения встраиваний
    private val embeddingStore = ConcurrentHashMap<String, EmbeddingEntry>()
    private var indexInitialized = false

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        return try {
            when (request.messageType) {
                "EMBEDDING_INDEX_REQUEST" -> handleIndexRequest(request, messageBus)
                "EMBEDDING_SEARCH_REQUEST" -> handleSearchRequest(request, messageBus)
                "EMBEDDING_CLEAR_REQUEST" -> handleClearRequest(request, messageBus)
                else -> createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
            }
        } catch (e: Exception) {
            logger.error("Error in embedding indexer", e)
            createErrorResponse(request.id, "Embedding operation failed: ${e.message}")
        }
    }

    private suspend fun handleIndexRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val projectPath = data["project_path"] as? String ?: ""
        val forceReindex = data["force_reindex"] as? Boolean ?: false
        val includeTests = data["include_tests"] as? Boolean ?: false

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.IO) {
            if (forceReindex || !indexInitialized) {
                buildEmbeddingIndex(projectPath, includeTests)
            } else {
                mapOf(
                    "status" to "already_indexed",
                    "total_embeddings" to embeddingStore.size,
                    "message" to "Embedding index already exists. Use force_reindex=true to rebuild."
                )
            }
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Embedding indexing completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "EMBEDDING_INDEX_RESULT",
                data = mapOf(
                    "index_result" to result,
                    "metadata" to mapOf(
                        "agent" to "EMBEDDING_INDEXER",
                        "project_path" to projectPath,
                        "index_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleSearchRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val query = data["query"] as? String ?: ""
        val limit = data["limit"] as? Int ?: 10
        val threshold = data["threshold"] as? Double ?: 0.5

        if (!indexInitialized) {
            return createErrorResponse(
                request.id,
                "Embedding index not initialized. Please run indexing first."
            )
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val searchResults = withContext(Dispatchers.Default) {
            performSemanticSearch(query, limit, threshold)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Semantic search completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "EMBEDDING_SEARCH_RESULT",
                data = mapOf(
                    "query" to query,
                    "results" to searchResults,
                    "metadata" to mapOf(
                        "agent" to "EMBEDDING_INDEXER",
                        "limit" to limit,
                        "threshold" to threshold,
                        "search_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleClearRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val clearedCount = withContext(Dispatchers.Default) {
            val count = embeddingStore.size
            embeddingStore.clear()
            indexInitialized = false
            count
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Embedding index cleared"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "EMBEDDING_CLEAR_RESULT",
                data = mapOf(
                    "cleared_entries" to clearedCount,
                    "message" to "Embedding index successfully cleared",
                    "metadata" to mapOf(
                        "agent" to "EMBEDDING_INDEXER",
                        "clear_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private fun createErrorResponse(requestId: String, error: String): AgentMessage.Response {
        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = requestId,
            success = false,
            payload = MessagePayload.ErrorPayload(error = error),
            error = error
        )
    }

    private suspend fun buildEmbeddingIndex(
        projectPath: String,
        includeTests: Boolean
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        // Очистка предыдущего индекса
        embeddingStore.clear()

        val root = if (projectPath.isBlank()) File(".") else File(projectPath)
        if (!root.exists()) {
            return@withContext mapOf(
                "error" to "Project path does not exist: $projectPath",
                "total_embeddings" to 0
            )
        }

        // Ограничения для предотвращения переполнения памяти
        val maxFiles = 50 // Максимум файлов для обработки
        val maxFileSize = 1024 * 1024 // 1MB на файл
        val maxChunks = 500 // Максимум чанков
        val maxChunkSize = 200 // Уменьшаем размер чанка
        val overlap = 30

        val sourceFiles = findSourceFiles(root, includeTests, maxFiles, maxFileSize.toLong())
        var processedFiles = 0
        var failedFiles = 0
        var totalChunks = 0
        var skippedLargeFiles = 0

        sourceFiles.forEach { file ->
            try {
                // Проверяем размер файла
                if (file.length() > maxFileSize) {
                    logger.warn("Skipping large file: ${file.path} (${file.length()} bytes)")
                    skippedLargeFiles++
                    return@forEach
                }

                // Проверяем лимит чанков
                if (totalChunks >= maxChunks) {
                    logger.info("Reached chunk limit, stopping indexing")
                    return@forEach
                }

                val chunks = chunkFileContent(file, maxChunkSize, overlap)

                // Ограничиваем количество чанков от одного файла
                val chunksToProcess = chunks.take(maxChunks - totalChunks)

                chunksToProcess.forEach { chunk ->
                    // Создаем урезанную версию контента для экономии памяти
                    val truncatedContent = if (chunk.content.length > 5000) {
                        chunk.content.take(5000) + "..."
                    } else {
                        chunk.content
                    }

                    val embedding = generateEmbedding(truncatedContent)
                    embeddingStore[chunk.id] = EmbeddingEntry(
                        id = chunk.id,
                        filePath = file.path,
                        content = truncatedContent, // Храним урезанную версию
                        embedding = embedding,
                        metadata = mapOf(
                            "file_name" to file.name,
                            "file_type" to file.extension,
                            "chunk_index" to chunk.index,
                            "file_size" to file.length(),
                            "last_modified" to file.lastModified(),
                            "original_size" to chunk.content.length,
                            "truncated" to (truncatedContent.length < chunk.content.length)
                        )
                    )
                }
                processedFiles++
                totalChunks += chunksToProcess.size
            } catch (e: OutOfMemoryError) {
                logger.error("Out of memory error processing file: ${file.path}", e)
                // Останавливаем обработку при нехватке памяти
                return@withContext mapOf(
                    "status" to "partial_completed",
                    "total_embeddings" to embeddingStore.size,
                    "processed_files" to processedFiles,
                    "failed_files" to failedFiles,
                    "skipped_large_files" to skippedLargeFiles,
                    "total_chunks" to totalChunks,
                    "error" to "out_of_memory",
                    "index_size_mb" to calculateIndexSize(),
                    "message" to "Indexing stopped due to memory constraints"
                )
            } catch (e: Exception) {
                logger.warn("Failed to process file: ${file.path}", e)
                failedFiles++
            }
        }

        indexInitialized = true

        mapOf(
            "status" to if (totalChunks >= maxChunks) "limit_reached" else "completed",
            "total_embeddings" to embeddingStore.size,
            "processed_files" to processedFiles,
            "failed_files" to failedFiles,
            "skipped_large_files" to skippedLargeFiles,
            "total_chunks" to totalChunks,
            "max_files_limit" to maxFiles,
            "max_chunks_limit" to maxChunks,
            "index_size_mb" to calculateIndexSize(),
            "file_types" to sourceFiles.groupBy { it.extension }.mapValues { it.value.size }
        )
    }

    private fun findSourceFiles(root: File, includeTests: Boolean, maxFiles: Int, maxFileSize: Long): List<File> {
        val sourceExtensions = setOf(".kt", ".java", ".scala", ".groovy", ".py", ".js", ".ts", ".jsx", ".tsx")
        val sourceFiles = mutableListOf<File>()

        fun scan(dir: File, depth: Int = 0) {
            // Ограничиваем глубину сканирования
            if (depth > 5) return

            dir.listFiles()?.forEach { file ->
                if (sourceFiles.size >= maxFiles) return@forEach

                if (file.isDirectory &&
                    !file.name.startsWith(".") &&
                    file.name != "build" &&
                    file.name != "target" &&
                    file.name != "node_modules" &&
                    file.name != ".git" &&
                    file.name != "out" &&
                    (includeTests || !file.name.contains("test"))) {
                    scan(file, depth + 1)
                } else if (file.isFile &&
                          sourceExtensions.any { ext -> file.name.endsWith(ext) } &&
                          file.length() <= maxFileSize) {
                    sourceFiles.add(file)
                }
            }
        }

        scan(root)
        return sourceFiles.take(maxFiles)
    }

    private fun chunkFileContent(file: File, maxChunkSize: Int = 200, overlap: Int = 30): List<ContentChunk> {
        val content = file.readText()
        val lines = content.lines()
        val chunks = mutableListOf<ContentChunk>()

        var startLine = 0
        var chunkIndex = 0

        while (startLine < lines.size) {
            val endLine = minOf(startLine + maxChunkSize, lines.size)
            val chunkLines = lines.subList(startLine, endLine)
            val chunkContent = chunkLines.joinToString("\n")

            chunks.add(ContentChunk(
                id = "${file.path}_chunk_$chunkIndex",
                content = chunkContent,
                index = chunkIndex,
                startLine = startLine,
                endLine = endLine
            ))

            // Предотвращаем отрицательный индекс
            startLine = maxOf(0, endLine - overlap)
            chunkIndex++
        }

        return chunks
    }

    private fun generateEmbedding(content: String): List<Double> {
        // Простая эмуляция генерации встраиваний
        // В реальной реализации здесь был бы вызов модели для генерации embeddings
        val words = content.lowercase().split(Regex("\\s+"))
        val embedding = mutableListOf<Double>()

        // Генерируем pseudo-embedding на основе хешей слов
        repeat(128) { i ->
            val hash = words.sumOf { it.hashCode() * (i + 1) }
            embedding.add((hash % 1000).toDouble() / 1000.0)
        }

        // Нормализация
        val magnitude = embedding.map { it * it }.sum().let { Math.sqrt(it) }
        return embedding.map { it / magnitude }
    }

    private fun performSemanticSearch(
        query: String,
        limit: Int,
        threshold: Double
    ): List<SearchResult> {
        val queryEmbedding = generateEmbedding(query)

        return embeddingStore.values.map { entry ->
            val similarity = cosineSimilarity(queryEmbedding, entry.embedding)
            SearchResult(
                entry = entry,
                similarity = similarity,
                snippet = extractSnippet(entry.content, query)
            )
        }
        .filter { it.similarity >= threshold }
        .sortedByDescending { it.similarity }
        .take(limit)
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size) return 0.0

        val dotProduct = a.zip(b).sumOf { it.first * it.second }
        val magnitudeA = a.map { it * it }.sum().let { Math.sqrt(it) }
        val magnitudeB = b.map { it * it }.sum().let { Math.sqrt(it) }

        return if (magnitudeA > 0 && magnitudeB > 0) dotProduct / (magnitudeA * magnitudeB) else 0.0
    }

    private fun extractSnippet(content: String, query: String): String {
        val queryWords = query.lowercase().split(Regex("\\s+"))
        val lines = content.lines()

        // Находим наиболее релевантные строки
        val scoredLines: List<Pair<Int, Int>> = lines.mapIndexed { index, line ->
            val score: Long = queryWords.sumOf { word: String ->
                if (line.lowercase().contains(word)) 1L else 0L
            }
            Pair(index, score.toInt())
        }.filter { it.second > 0 }.sortedByDescending { it.second }

        if (scoredLines.isEmpty()) {
            return content.take(200) + if (content.length > 200) "..." else ""
        }

        // Берем несколько релевантных строк
        val bestLineIndices: List<Int> = scoredLines.take(3).map { it.first }
        val startLine = maxOf(0, (bestLineIndices.minOrNull() ?: 0) - 1)
        val endLine = minOf(lines.size - 1, (bestLineIndices.maxOrNull() ?: 0) + 1)

        return lines.subList(startLine, endLine + 1).joinToString("\n").take(300)
    }

    private fun calculateIndexSize(): Double {
        val totalChars = embeddingStore.values.sumOf {
            it.content.length + it.embedding.size * 8 // double = 8 bytes
        }
        return totalChars / (1024.0 * 1024.0) // MB
    }

    // Data classes
    private data class EmbeddingEntry(
        val id: String,
        val filePath: String,
        val content: String,
        val embedding: List<Double>,
        val metadata: Map<String, Any>
    )

    private data class ContentChunk(
        val id: String,
        val content: String,
        val index: Int,
        val startLine: Int,
        val endLine: Int
    )

    private data class SearchResult(
        val entry: EmbeddingEntry,
        val similarity: Double,
        val snippet: String
    )
}
