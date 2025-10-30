package ru.marslab.ide.ride.domain.rag

/**
 * Базовые модели для RAG пайплайна
 */

data class ChunkCandidate(
    val chunkId: String,
    val similarity: Double,
    val preview: String? = null,
    val filePath: String? = null
)

data class EnrichedChunk(
    val chunkId: String,
    val content: String,
    val filePath: String? = null,
    val anchors: List<IntRange> = emptyList()
)
