package ru.marslab.ide.ride.agent.tools

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.model.embedding.*
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import ru.marslab.ide.ride.service.embedding.EmbeddingDatabaseService
import ru.marslab.ide.ride.service.embedding.EmbeddingGeneratorService
import ru.marslab.ide.ride.settings.PluginSettings
import java.io.File

/**
 * Агент для индексации файловой базы проекта с генерацией эмбеддингов
 */
class EmbeddingIndexerToolAgent(
    private val settings: PluginSettings? = null
) : BaseToolAgent(
    agentType = AgentType.EMBEDDING_INDEXER,
    toolCapabilities = setOf(
        "embedding_indexing",
        "file_chunking",
        "semantic_search",
        "index_management"
    )
) {

    // logger уже определен в BaseToolAgent
    private var dbService: EmbeddingDatabaseService? = null
    private var generatorService: EmbeddingGeneratorService? = null
    private var indexingConfig: IndexingConfig = IndexingConfig()

    // Callback для отслеживания прогресса
    private var progressCallback: ((IndexingProgress) -> Unit)? = null

    override fun getDescription(): String = "Агент для индексации файлов проекта с генерацией эмбеддингов"

    override fun validateInput(input: StepInput): ValidationResult {
        val action = input.get<String>("action") ?: "index"

        return when (action) {
            "index" -> {
                val projectPath = input.get<String>("project_path")
                if (projectPath.isNullOrBlank()) {
                    ValidationResult.failure("project_path is required for indexing")
                } else {
                    ValidationResult.success()
                }
            }
            "search" -> {
                val query = input.get<String>("query")
                if (query.isNullOrBlank()) {
                    ValidationResult.failure("query is required for search")
                } else {
                    ValidationResult.success()
                }
            }
            "clear" -> ValidationResult.success()
            "stats" -> ValidationResult.success()
            else -> ValidationResult.failure("Unknown action: $action")
        }
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val action = step.input.get<String>("action") ?: "index"

        return when (action) {
            "index" -> startIndexing(step, context)
            "search" -> performSearch(step, context)
            "clear" -> clearIndex(step, context)
            "stats" -> getStatistics(step, context)
            else -> StepResult.error("Unknown action: $action")
        }
    }

    /**
     * Запуск индексации проекта
     */
    private suspend fun startIndexing(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val projectPath = step.input.get<String>("project_path") ?: return StepResult.error("project_path is required")
        val forceReindex = step.input.get<Boolean>("force_reindex") ?: false
        val configOverride = step.input.get<IndexingConfig>("config")

        if (configOverride != null) {
            indexingConfig = configOverride
        }

        val startTime = System.currentTimeMillis()

        try {
            // Инициализация сервисов
            val dbPath = getDbPath(projectPath)
            dbService = EmbeddingDatabaseService(dbPath)
            generatorService = EmbeddingGeneratorService(
                indexingConfig,
                settings ?: com.intellij.openapi.components.service()
            )

            // Сканирование файлов через ProjectScannerAgentBridge
            val scanResult = ProjectScannerAgentBridge.scanProject(
                projectPath = projectPath,
                forceRescan = forceReindex,
                includePatterns = indexingConfig.includePatterns,
                excludePatterns = indexingConfig.excludePatterns
            )

            if (!scanResult.success) {
                return StepResult.error("Failed to scan project: ${scanResult.error}")
            }

            val files = scanResult.output.get<List<String>>("files") ?: emptyList()
            val result = indexFiles(files, forceReindex)

            val duration = System.currentTimeMillis() - startTime

            return StepResult.success(
                output = StepOutput.of(
                    "result" to result.copy(durationMs = duration),
                    "files_processed" to result.filesProcessed,
                    "chunks_created" to result.chunksCreated,
                    "embeddings_generated" to result.embeddingsGenerated
                ),
                metadata = mapOf(
                    "duration_ms" to duration,
                    "db_path" to dbPath
                )
            )

        } catch (e: Exception) {
            logger.error("Indexing failed", e)
            return StepResult.error("Indexing failed: ${e.message}")
        } finally {
            dbService?.close()
        }
    }

    /**
     * Индексация списка файлов
     */
    private suspend fun indexFiles(filePaths: List<String>, forceReindex: Boolean): IndexingResult {
        val db = dbService ?: return IndexingResult(
            success = false,
            filesProcessed = 0,
            chunksCreated = 0,
            embeddingsGenerated = 0,
            errors = listOf("Database service not initialized"),
            durationMs = 0
        )

        val generator = generatorService ?: return IndexingResult(
            success = false,
            filesProcessed = 0,
            chunksCreated = 0,
            embeddingsGenerated = 0,
            errors = listOf("Generator service not initialized"),
            durationMs = 0
        )

        var filesProcessed = 0
        var chunksCreated = 0
        var embeddingsGenerated = 0
        val errors = mutableListOf<String>()

        for ((index, filePath) in filePaths.withIndex()) {
            try {
                val file = File(filePath)
                if (!generator.shouldIndexFile(file)) {
                    continue
                }

                // Проверяем, нужно ли переиндексировать
                val existingFile = db.getIndexedFile(filePath)
                val fileHash = generator.calculateFileHash(file)

                if (!forceReindex && existingFile != null && existingFile.fileHash == fileHash) {
                    logger.debug("Skipping unchanged file: $filePath")
                    continue
                }

                // Удаляем старый индекс если есть
                if (existingFile != null) {
                    db.deleteFileIndex(filePath)
                }

                // Сохраняем файл в БД
                val indexedFile = IndexedFile(
                    filePath = filePath,
                    fileHash = fileHash,
                    lastModified = file.lastModified(),
                    indexedAt = System.currentTimeMillis()
                )
                val fileId = db.saveIndexedFile(indexedFile)

                // Разбиваем на чанки
                val chunks = generator.chunkFile(file, fileId)
                chunksCreated += chunks.size

                // Генерируем эмбеддинги для каждого чанка
                for (chunk in chunks) {
                    val chunkId = db.saveFileChunk(chunk)

                    val embedding = generator.generateEmbedding(chunk.content)
                    if (embedding.isNotEmpty()) {
                        val embeddingData = EmbeddingData(
                            chunkId = chunkId,
                            embedding = embedding,
                            dimension = generator.getEmbeddingDimension()
                        )
                        db.saveEmbedding(embeddingData)
                        embeddingsGenerated++
                    }
                }

                filesProcessed++

                // Отправляем прогресс
                progressCallback?.invoke(
                    IndexingProgress(
                        currentFile = filePath,
                        filesProcessed = filesProcessed,
                        totalFiles = filePaths.size,
                        chunksCreated = chunksCreated,
                        embeddingsGenerated = embeddingsGenerated,
                        percentComplete = ((index + 1) * 100) / filePaths.size
                    )
                )

            } catch (e: Exception) {
                logger.error("Failed to index file: $filePath", e)
                errors.add("$filePath: ${e.message}")
            }
        }

        return IndexingResult(
            success = errors.isEmpty(),
            filesProcessed = filesProcessed,
            chunksCreated = chunksCreated,
            embeddingsGenerated = embeddingsGenerated,
            errors = errors,
            durationMs = 0 // Будет установлено в вызывающем методе
        )
    }

    /**
     * Поиск по эмбеддингам
     */
    private suspend fun performSearch(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val query = step.input.get<String>("query") ?: return StepResult.error("query is required")
        val topK = step.input.get<Int>("top_k") ?: 10
        val projectPath = context.projectPath ?: return StepResult.error("project_path is required in context")

        try {
            val dbPath = getDbPath(projectPath)
            dbService = EmbeddingDatabaseService(dbPath)
            generatorService = EmbeddingGeneratorService(
                indexingConfig,
                settings ?: com.intellij.openapi.components.service()
            )

            val db = dbService ?: return StepResult.error("Database service not initialized")
            val generator = generatorService ?: return StepResult.error("Generator service not initialized")

            // Генерируем эмбеддинг для запроса
            val queryEmbedding = generator.generateEmbedding(query)
            if (queryEmbedding.isEmpty()) {
                return StepResult.error("Failed to generate query embedding")
            }

            // Ищем похожие эмбеддинги
            val similarChunks = db.findSimilarEmbeddings(queryEmbedding, topK)

            // Получаем детали чанков
            val results = similarChunks.mapNotNull { (chunkId, similarity) ->
                val chunk = db.getChunkById(chunkId)
                chunk?.let {
                    mapOf(
                        "chunk_id" to chunkId,
                        "similarity" to similarity,
                        "content" to it.content,
                        "file_id" to it.fileId,
                        "start_line" to it.startLine,
                        "end_line" to it.endLine
                    )
                }
            }

            return StepResult.success(
                output = StepOutput.of(
                    "results" to results,
                    "query" to query,
                    "results_count" to results.size
                )
            )

        } catch (e: Exception) {
            logger.error("Search failed", e)
            return StepResult.error("Search failed: ${e.message}")
        } finally {
            dbService?.close()
        }
    }

    /**
     * Очистка индекса
     */
    private suspend fun clearIndex(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val projectPath = step.input.get<String>("project_path") ?: context.projectPath
            ?: return StepResult.error("project_path is required")

        try {
            val dbPath = getDbPath(projectPath)
            dbService = EmbeddingDatabaseService(dbPath)
            dbService?.clearAll()

            return StepResult.success(
                output = StepOutput.of("message" to "Index cleared successfully")
            )
        } catch (e: Exception) {
            logger.error("Failed to clear index", e)
            return StepResult.error("Failed to clear index: ${e.message}")
        } finally {
            dbService?.close()
        }
    }

    /**
     * Получение статистики индекса
     */
    private suspend fun getStatistics(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val projectPath = step.input.get<String>("project_path") ?: context.projectPath
            ?: return StepResult.error("project_path is required")

        try {
            val dbPath = getDbPath(projectPath)
            dbService = EmbeddingDatabaseService(dbPath)
            val stats = dbService?.getStatistics() ?: emptyMap()

            return StepResult.success(
                output = StepOutput.of(
                    "statistics" to stats,
                    "db_path" to dbPath
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to get statistics", e)
            return StepResult.error("Failed to get statistics: ${e.message}")
        } finally {
            dbService?.close()
        }
    }

    /**
     * Установка callback для отслеживания прогресса
     */
    fun setProgressCallback(callback: (IndexingProgress) -> Unit) {
        this.progressCallback = callback
    }

    /**
     * Обновление конфигурации индексации
     */
    fun updateConfig(config: IndexingConfig) {
        this.indexingConfig = config
    }

    /**
     * Получение пути к БД для проекта
     */
    private fun getDbPath(projectPath: String): String {
        val projectDir = File(projectPath)
        val dbDir = File(projectDir, ".ride/embeddings")
        dbDir.mkdirs()
        return File(dbDir, "embeddings.db").absolutePath
    }

    override fun dispose() {
        super.dispose()
        dbService?.close()
    }
}
