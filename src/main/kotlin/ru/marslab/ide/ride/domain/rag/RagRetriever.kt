package ru.marslab.ide.ride.domain.rag

/**
 * Абстракция над слоем retrieval (БД эмбеддингов и т.п.).
 * Возвращает список кандидатов с метаданными для дальнейшего реранка.
 */
interface RagRetriever {
    /**
     * Выполняет поиск кандидатов по семантической близости.
     * @param query пользовательский запрос
     * @param candidateK количество кандидатов для первичного списка (30..100)
     */
    fun retrieveCandidates(query: String, candidateK: Int): List<ChunkCandidate>
}
