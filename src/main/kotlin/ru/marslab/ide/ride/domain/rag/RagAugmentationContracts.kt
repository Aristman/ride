package ru.marslab.ide.ride.domain.rag

import ru.marslab.ide.ride.model.chat.Message

/**
 * Входные данные для запроса RAG обогащения
 */
data class RagQuery(
    val userQuery: String,
    val history: List<Message>,
    val maxContextTokens: Int
)

/**
 * Кандидат на обогащение, полученный из retrieval слоя
 */
data class RagCandidate(
    val chunkId: Long,
    val similarity: Float,
    val snippet: String?,
    val filePath: String?,
    val startLine: Int,
    val endLine: Int
)

/**
 * Результат оценки релевантности LLM-реранкером
 */
data class RagRerankResult(
    val candidateId: Long,
    val score: Double
)

/**
 * Обогащённый чанк с дополнительными данными (например, из MCP)
 */
data class RagEnrichedChunk(
    val candidateId: Long,
    val content: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int
)

/**
 * Итоговое обогащение для передачи в LLM
 */
data class RagAugmentationResult(
    val enrichedChunks: List<RagEnrichedChunk>,
    val metadata: Map<String, Any?> = emptyMap()
)

/**
 * Поиск кандидатов по эмбеддингам
 */
fun interface RagRetriever {
    suspend fun retrieve(query: RagQuery, candidateCount: Int): List<RagCandidate>
}

/**
 * Реранкер, оценивающий релевантность кандидатов через LLM
 */
fun interface LlmReranker {
    suspend fun rerank(query: String, candidates: List<RagCandidate>, desiredCount: Int): List<RagRerankResult>
}

/**
 * Обогащение чанков через внешние источники (например, MCP)
 */
fun interface ChunkEnricher {
    suspend fun enrich(rankedCandidates: List<RagCandidate>): List<RagEnrichedChunk>
}

/**
 * Общая стратегия RAG обогащения
 */
fun interface RagAugmentationStrategy {
    suspend fun augment(query: RagQuery): RagAugmentationResult?
}
