package ru.marslab.ide.ride.model.embedding

import kotlinx.serialization.Serializable

/**
 * Модель индексированного файла
 */
@Serializable
data class IndexedFile(
    val id: Long = 0,
    val filePath: String,
    val fileHash: String,
    val lastModified: Long,
    val indexedAt: Long
)

/**
 * Модель чанка файла
 */
@Serializable
data class FileChunkData(
    val id: Long = 0,
    val fileId: Long,
    val chunkIndex: Int,
    val content: String,
    val startLine: Int,
    val endLine: Int
)

/**
 * Модель эмбеддинга
 */
@Serializable
data class EmbeddingData(
    val id: Long = 0,
    val chunkId: Long,
    val embedding: List<Float>,
    val dimension: Int
)

/**
 * Конфигурация индексации
 */
@Serializable
data class IndexingConfig(
    val chunkSize: Int = 1000,
    val chunkOverlap: Int = 200,
    // Deprecated: indexer now relies on ProjectScannerToolAgent rules only
    val includePatterns: List<String> = listOf("**/*.kt", "**/*.java", "**/*.xml", "**/*.md"),
    // Deprecated: indexer now relies on ProjectScannerToolAgent rules only
    val excludePatterns: List<String> = listOf("**/build/**", "**/target/**", "**/.git/**", "**/.idea/**"),
    val embeddingModel: String = "text-embedding-ada-002"
)

/**
 * Результат индексации
 */
@Serializable
data class IndexingResult(
    val success: Boolean,
    val filesProcessed: Int,
    val chunksCreated: Int,
    val embeddingsGenerated: Int,
    val errors: List<String> = emptyList(),
    val durationMs: Long
)

/**
 * Прогресс индексации
 */
@Serializable
data class IndexingProgress(
    val currentFile: String,
    val filesProcessed: Int,
    val totalFiles: Int,
    val chunksCreated: Int,
    val embeddingsGenerated: Int,
    val percentComplete: Int
)
