package ru.marslab.ide.ride.integration.embedding

/**
 * Клиент доступа к индексу эмбеддингов. Инфраструктурный интерфейс.
 */
interface EmbeddingIndexClient {
    data class EmbeddingMatch(
        val chunkId: String,
        val similarity: Double
    )

    data class ChunkData(
        val content: String,
        val filePath: String?
    )

    /**
     * Возвращает до [limit] ближайших чанков к запросу.
     */
    fun searchSimilar(query: String, limit: Int): List<EmbeddingMatch>

    /**
     * Возвращает содержимое чанка по id.
     */
    fun getChunkById(chunkId: String): ChunkData?

    /**
     * Возвращает путь к файлу по chunkId (если доступен).
     */
    fun getFilePathByChunkId(chunkId: String): String?
}
