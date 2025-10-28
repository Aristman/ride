package ru.marslab.ide.ride.service.rag

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ru.marslab.ide.ride.service.rag.RagEnrichmentService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RagEnrichmentServiceTest : BasePlatformTestCase() {

    private lateinit var ragService: RagEnrichmentService

    override fun setUp() {
        super.setUp()
        ragService = RagEnrichmentService()
    }

    fun testFormatRagContextShouldFormatChunksCorrectly() {
        // Given
        val ragResult = RagResult(
            chunks = listOf(
                RagChunk(
                    content = "Test content 1",
                    filePath = "test/file1.kt",
                    startLine = 1,
                    endLine = 5,
                    similarity = 0.8f
                ),
                RagChunk(
                    content = "Test content 2",
                    filePath = "test/file2.kt",
                    startLine = 10,
                    endLine = 15,
                    similarity = 0.6f
                )
            ),
            totalTokens = 100,
            query = "test query"
        )

        // When
        val formatted = ragService.formatRagContext(ragResult)

        // Then
        assertTrue(formatted.contains("=== Retrieved Context ==="))
        assertTrue(formatted.contains("Фрагмент из: test/file1.kt:1-5 (сходство: 80%)"))
        assertTrue(formatted.contains("Фрагмент из: test/file2.kt:10-15 (сходство: 60%)"))
        assertTrue(formatted.contains("Test content 1"))
        assertTrue(formatted.contains("Test content 2"))
        assertTrue(formatted.contains("=== End of Context ==="))
    }

    fun testCreateEnrichedPromptShouldReturnOriginalQueryWhenNoRAGResult() {
        // Given
        val originalQuery = "What is Kotlin?"

        // When
        val enriched = ragService.createEnrichedPrompt("", originalQuery, null)

        // Then
        assertEquals(originalQuery, enriched)
    }

    fun testCreateEnrichedPromptShouldIncludeRAGContextWhenResultProvided() {
        // Given
        val originalQuery = "What is Kotlin?"
        val ragResult = RagResult(
            chunks = listOf(
                RagChunk(
                    content = "Kotlin is a modern programming language",
                    filePath = "docs/kotlin.md",
                    startLine = 1,
                    endLine = 2,
                    similarity = 0.9f
                )
            ),
            totalTokens = 10,
            query = originalQuery
        )

        // When
        val enriched = ragService.createEnrichedPrompt("", originalQuery, ragResult)

        // Then
        assertTrue(enriched.contains("=== Retrieved Context ==="))
        assertTrue(enriched.contains("Kotlin is a modern programming language"))
        assertTrue(enriched.contains("Вопрос: What is Kotlin?"))
        assertTrue(enriched.contains("Инструкции:"))
    }

    fun testFormatRagContextShouldHandleEmptyChunks() {
        // Given
        val ragResult = RagResult(
            chunks = emptyList(),
            totalTokens = 0,
            query = "test query"
        )

        // When
        val formatted = ragService.formatRagContext(ragResult)

        // Then
        assertEquals("", formatted)
    }

    fun testRagChunkDataClass() {
        // Given
        val chunk = RagChunk(
            content = "Test content",
            filePath = "test/file.kt",
            startLine = 1,
            endLine = 5,
            similarity = 0.85f
        )

        // When & Then
        assertEquals("Test content", chunk.content)
        assertEquals("test/file.kt", chunk.filePath)
        assertEquals(1, chunk.startLine)
        assertEquals(5, chunk.endLine)
        assertEquals(0.85f, chunk.similarity)
    }

    fun testRagResultDataClass() {
        // Given
        val chunks = listOf(
            RagChunk("content1", "file1.kt", 1, 2, 0.8f),
            RagChunk("content2", "file2.kt", 3, 4, 0.6f)
        )
        val ragResult = RagResult(
            chunks = chunks,
            totalTokens = 100,
            query = "test query"
        )

        // When & Then
        assertEquals(chunks, ragResult.chunks)
        assertEquals(100, ragResult.totalTokens)
        assertEquals("test query", ragResult.query)
    }
}