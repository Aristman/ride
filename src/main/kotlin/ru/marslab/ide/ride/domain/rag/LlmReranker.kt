package ru.marslab.ide.ride.domain.rag

/**
 * Оценка релевантности кандидатов с помощью LLM.
 * На вход подаются исходный запрос и список кандидатов,
 * на выход — упорядоченный список идентификаторов чанков по релевантности.
 */
interface LlmReranker {
    /**
     * Возвращает список chunkId, отсортированный по релевантности убыв.
     * Если LLM недоступен — ожидается фоллбэк на исходный порядок.
     */
    suspend fun rerank(query: String, candidates: List<ChunkCandidate>, topN: Int): List<String>
}
