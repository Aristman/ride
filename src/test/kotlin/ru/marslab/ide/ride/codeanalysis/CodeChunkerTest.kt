package ru.marslab.ide.ride.codeanalysis

import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import ru.marslab.ide.ride.codeanalysis.chunker.CodeChunker
import ru.marslab.ide.ride.integration.llm.TokenCounter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для CodeChunker
 */
class CodeChunkerTest {

    @Test
    fun `should not chunk small file`() {
        val tokenCounter = mockk<TokenCounter>()
        every { tokenCounter.countTokens(any<String>()) } returns 10

        val chunker = CodeChunker(tokenCounter, maxTokensPerChunk = 1000)
        
        val code = """
            fun hello() {
                println("Hello, World!")
            }
        """.trimIndent()

        val chunks = chunker.chunkFile(code)
        
        assertEquals(1, chunks.size)
        assertEquals(1, chunks[0].startLine)
        assertEquals(3, chunks[0].endLine)
    }

    @Test
    fun `should chunk large file`() {
        val tokenCounter = mockk<TokenCounter>()
        // Симулируем, что каждая строка = 100 токенов
        every { tokenCounter.countTokens(any<String>()) } returns 100

        val chunker = CodeChunker(tokenCounter, maxTokensPerChunk = 250)
        
        // 5 строк кода
        val code = """
            line 1
            line 2
            line 3
            line 4
            line 5
        """.trimIndent()

        val chunks = chunker.chunkFile(code)
        
        // Должно получиться минимум 2 чанка (250 токенов на чанк, 100 на строку)
        assertTrue(chunks.size >= 2)
    }

    @Test
    fun `should detect if chunking is needed`() {
        val tokenCounter = mockk<TokenCounter>()
        every { tokenCounter.countTokens(any<String>()) } returns 5000

        val chunker = CodeChunker(tokenCounter, maxTokensPerChunk = 4000)
        
        val code = "some large code"
        
        assertTrue(chunker.needsChunking(code))
    }
}
