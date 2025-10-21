package ru.marslab.ide.ride.formatter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ru.marslab.ide.ride.model.agent.AgentOutputType
import ru.marslab.ide.ride.model.agent.FormattedOutputBlock

class CodeBlockFormatterTest {

    private val formatter = CodeBlockFormatter()

    @Test
    fun `should extract single code block`() {
        // Given
        val content = """
            Here is some code:

            ```kotlin
            fun hello() {
                println("Hello, World!")
            }
            ```

            This is after the code block.
        """.trimIndent()

        // When
        val result = formatter.extractBlocks(content)

        // Then
        assertEquals(2, result.size)

        val textBlock = result[0]
        assertEquals(AgentOutputType.MARKDOWN, textBlock.type)
        assertTrue(textBlock.content.contains("Here is some code"))
        assertEquals(0, textBlock.order)

        val codeBlock = result[1]
        assertEquals(AgentOutputType.CODE_BLOCKS, codeBlock.type)
        assertEquals("kotlin", codeBlock.metadata["language"])
        assertTrue(codeBlock.content.contains("fun hello()"))
        assertEquals(1, codeBlock.order)
    }

    @Test
    fun `should extract multiple code blocks`() {
        // Given
        val content = """
            First block:
            ```kotlin
            fun test1() { }
            ```

            Middle text

            Second block:
            ```python
            def test2():
                pass
            ```

            Final text
        """.trimIndent()

        // When
        val result = formatter.extractBlocks(content)

        // Then
        assertEquals(5, result.size) // text, code1, text, code2, text

        assertEquals(AgentOutputType.CODE_BLOCKS, result[1].type)
        assertEquals("kotlin", result[1].metadata["language"])

        assertEquals(AgentOutputType.CODE_BLOCKS, result[3].type)
        assertEquals("python", result[3].metadata["language"])
    }

    @Test
    fun `should handle code block without language`() {
        // Given
        val content = """
            Some text

            ```
            no language specified
            ```

            More text
        """.trimIndent()

        // When
        val result = formatter.extractBlocks(content)

        // Then
        assertEquals(3, result.size)
        val codeBlock = result[1]
        assertEquals(AgentOutputType.CODE_BLOCKS, codeBlock.type)
        assertEquals("text", codeBlock.metadata["language"])
    }

    @Test
    fun `should create code block HTML`() {
        // Given
        val code = "fun hello() { println(\"Hello\") }"
        val language = "kotlin"

        // When
        val html = formatter.createCodeBlock(code, language)

        // Then
        assertTrue(html.contains("code-block-container"))
        assertTrue(html.contains("code-block-header"))
        assertTrue(html.contains("code-language"))
        assertTrue(html.contains("kotlin"))
        assertTrue(html.contains("code-copy-btn"))
        assertTrue(html.contains("code-content"))
        assertTrue(html.contains("language-$language"))
        assertTrue(html.contains("hello()"))
    }

    @Test
    fun `should escape HTML in code blocks`() {
        // Given
        val code = "<script>alert('xss')</script>"
        val language = "javascript"

        // When
        val html = formatter.createCodeBlock(code, language)

        // Then
        assertFalse(html.contains("<script>alert('xss')</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;"))
    }

    @Test
    fun `should extract code blocks correctly`() {
        // Given
        val content = """
            Text before

            ```kotlin
            fun main() {
                println("test")
            }
            ```

            Text after
        """.trimIndent()

        // When
        val codeBlocks = formatter.extractCodeBlocks(content)

        // Then
        assertEquals(1, codeBlocks.size)
        val block = codeBlocks[0]
        assertEquals("kotlin", block.language)
        assertTrue(block.content.contains("fun main()"))
    }

    @Test
    fun `should split text by code blocks`() {
        // Given
        val content = """
            Before first block
            ```kotlin
            code1
            ```
            Between blocks
            ```python
            code2
            ```
            After last block
        """.trimIndent()

        // When
        val parts = formatter.splitByCodeBlocks(content)

        // Then
        assertEquals(3, parts.size)
        assertTrue(parts[0].contains("Before first block"))
        assertTrue(parts[1].contains("Between blocks"))
        assertTrue(parts[2].contains("After last block"))
    }

    @Test
    fun `should handle content without code blocks`() {
        // Given
        val content = "Just plain text without any code blocks."

        // When
        val result = formatter.extractBlocks(content)

        // Then
        assertEquals(1, result.size)
        assertEquals(AgentOutputType.MARKDOWN, result[0].type)
        assertEquals(content, result[0].content)
    }

    @Test
    fun `should wrap multiple blocks in template`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("Intro text", 0),
            FormattedOutputBlock.codeBlock("fun test() {}", "kotlin", 1),
            FormattedOutputBlock.markdown("Outro text", 2)
        )

        // When
        val html = formatter.wrapInTemplate(blocks)

        // Then
        assertTrue(html.contains("multi-block-container"))
        assertTrue(html.contains("block-item"))
        assertTrue(html.contains("Intro text"))
        assertTrue(html.contains("code-block-container"))
        assertTrue(html.contains("Outro text"))
        assertTrue(html.contains("block-separator"))
    }

    @Test
    fun `should format as text fallback`() {
        // Given
        val content = "Plain text with `inline code` and normal text."

        // When
        val result = formatter.formatAsText(content)

        // Then
        assertEquals(1, result.blocks.size)
        assertEquals(AgentOutputType.MARKDOWN, result.blocks[0].type)
        assertEquals(content, result.blocks[0].content)
    }

    @Test
    fun `should handle empty content`() {
        // Given
        val content = ""

        // When
        val result = formatter.extractBlocks(content)

        // Then
        assertEquals(1, result.size)
        assertEquals(AgentOutputType.MARKDOWN, result[0].type)
        assertEquals(content, result[0].content)
    }
}