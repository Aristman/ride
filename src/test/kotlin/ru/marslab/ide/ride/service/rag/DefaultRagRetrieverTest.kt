package ru.marslab.ide.ride.service.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.marslab.ide.ride.integration.embedding.EmbeddingIndexClient
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.settings.PluginSettingsState

class DefaultRagRetrieverTest {

    private class StubIndexClient(
        private val matches: List<EmbeddingIndexClient.EmbeddingMatch>,
        private val paths: Map<String, String?>
    ) : EmbeddingIndexClient {
        var lastQuery: String? = null
        var lastLimit: Int = -1
        override fun searchSimilar(query: String, limit: Int): List<EmbeddingIndexClient.EmbeddingMatch> {
            lastQuery = query
            lastLimit = limit
            return matches.take(limit)
        }

        override fun getChunkById(chunkId: String): EmbeddingIndexClient.ChunkData? {
            return EmbeddingIndexClient.ChunkData("content-$chunkId", paths[chunkId])
        }

        override fun getFilePathByChunkId(chunkId: String): String? = paths[chunkId]
    }

    @Test
    fun `filters by threshold and maps metadata`() {
        val index = StubIndexClient(
            matches = listOf(
                EmbeddingIndexClient.EmbeddingMatch("c1", 0.9),
                EmbeddingIndexClient.EmbeddingMatch("c2", 0.2),
                EmbeddingIndexClient.EmbeddingMatch("c3", 0.7),
                EmbeddingIndexClient.EmbeddingMatch("c4", 0.1),
                EmbeddingIndexClient.EmbeddingMatch("c5", 0.8)
            ),
            paths = mapOf(
                "c1" to "/path/file1", "c2" to "/path/file2", "c3" to "/path/file3", "c4" to null, "c5" to "/path/file5"
            )
        )
        val settings = PluginSettings()
        settings.ragSimilarityThreshold = 0.5f
        val retriever = DefaultRagRetriever(index, settings)

        val res = retriever.retrieveCandidates("q", candidateK = 5)

        // Ожидаем c1, c3, c5 (>= 0.5)
        assertEquals(3, res.size)
        val ids = res.map { it.chunkId }
        assertTrue(ids.containsAll(listOf("c1", "c3", "c5")))
        // Метаданные пути должны быть проставлены
        assertEquals("/path/file1", res.first { it.chunkId == "c1" }.filePath)
    }

    @Test
    fun `candidateK is coerced to max range`() {
        val index = StubIndexClient(matches = emptyList(), paths = emptyMap())
        val settings = PluginSettings()
        val retriever = DefaultRagRetriever(index, settings)

        retriever.retrieveCandidates("q", candidateK = 1000)

        assertEquals(PluginSettingsState.RAG_CANDIDATE_K_MAX, index.lastLimit)
    }
}
