package ru.marslab.ide.ride.service.rag

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.domain.rag.ChunkCandidate
import ru.marslab.ide.ride.domain.rag.RagRetriever
import ru.marslab.ide.ride.integration.embedding.EmbeddingIndexClient
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.settings.PluginSettingsState

/**
 * Реализация RagRetriever с использованием EmbeddingIndexClient.
 */
class DefaultRagRetriever(
    private val indexClient: EmbeddingIndexClient,
    private val settings: PluginSettings,
    private val logger: Logger = Logger.getInstance(DefaultRagRetriever::class.java)
) : RagRetriever {

    override fun retrieveCandidates(query: String, candidateK: Int): List<ChunkCandidate> {
        val started = System.currentTimeMillis()
        val limit = candidateK
            .coerceIn(
                PluginSettingsState.RAG_CANDIDATE_K_MIN,
                PluginSettingsState.RAG_CANDIDATE_K_MAX
            )
        val thr = settings.ragSimilarityThreshold.toDouble()
        logger.info("RAG Retrieval: query='${query.take(80)}...' candidateK=$candidateK -> limit=$limit, thr=$thr")
        return try {
            val matches = indexClient.searchSimilar(query, limit)
            val filtered = matches
                .asSequence()
                .filter { it.similarity >= thr }
                .map { match ->
                    val path = indexClient.getFilePathByChunkId(match.chunkId)
                    ChunkCandidate(
                        chunkId = match.chunkId,
                        similarity = match.similarity,
                        filePath = path,
                        preview = null
                    )
                }
                .toList()
            val took = System.currentTimeMillis() - started
            logger.info("RAG Retrieval: got=${filtered.size}, took=${took}ms")
            filtered
        } catch (t: Throwable) {
            val took = System.currentTimeMillis() - started
            logger.warn("RAG Retrieval failed after ${took}ms: ${t.message}", t)
            emptyList()
        }
    }
}
