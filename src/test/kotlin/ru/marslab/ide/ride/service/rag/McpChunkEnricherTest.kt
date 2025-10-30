package ru.marslab.ide.ride.service.rag

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.marslab.ide.ride.integration.embedding.EmbeddingIndexClient

class McpChunkEnricherTest {

    private class StubIndexClient(
        private val chunks: Map<String, EmbeddingIndexClient.ChunkData>
    ) : EmbeddingIndexClient {
        override fun searchSimilar(query: String, limit: Int): List<EmbeddingIndexClient.EmbeddingMatch> = emptyList()
        override fun getChunkById(chunkId: String): EmbeddingIndexClient.ChunkData? = chunks[chunkId]
        override fun getFilePathByChunkId(chunkId: String): String? = chunks[chunkId]?.filePath
    }


    @Test
    fun `enrich adds surrounding context and anchors`() = runBlocking {
        val filePath = "src/Main.kt"
        val fileContent = """
            package demo
            
            fun hello() {
                println("Hello")
            }
            
            fun target() {
                val x = 1
                val y = 2
                println(x + y)
            }
            
            fun bye() {}
        """.trimIndent()
        val snippet = """
            fun target() {
                val x = 1
                val y = 2
                println(x + y)
            }
        """.trimIndent()

        val index = StubIndexClient(
            mapOf("c1" to EmbeddingIndexClient.ChunkData(content = snippet, filePath = filePath))
        )
        val files = mapOf(filePath to fileContent)
        val enricher = McpChunkEnricher(
            indexClient = index,
            mcpClientProvider = { error("unused in test") },
            readFileFn = { path -> files[path] ?: "" }
        )

        val res = enricher.enrich(listOf("c1"))
        assertEquals(1, res.size)
        val enriched = res.first()
        assertEquals("c1", enriched.chunkId)
        assertEquals(filePath, enriched.filePath)
        assertTrue(enriched.content.contains("fun target()"))
        assertTrue(enriched.anchors.isNotEmpty())
    }
}
