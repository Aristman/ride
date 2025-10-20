package ru.marslab.ide.ride.model.agent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class FormattedOutputTest {

    @Test
    fun `should create single block output`() {
        // Given
        val block = FormattedOutputBlock.markdown("Test content")

        // When
        val output = FormattedOutput.single(block)

        // Then
        assertEquals(1, output.blocks.size)
        assertEquals(block, output.blocks.first())
    }

    @Test
    fun `should create multiple blocks output`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("First", 0),
            FormattedOutputBlock.codeBlock("code", "kotlin", 1),
            FormattedOutputBlock.markdown("Second", 2)
        )

        // When
        val output = FormattedOutput.multiple(blocks)

        // Then
        assertEquals(3, output.blocks.size)
        // Проверяем, что блоки отсортированы по order
        assertEquals(0, output.blocks[0].order)
        assertEquals(1, output.blocks[1].order)
        assertEquals(2, output.blocks[2].order)
    }

    @Test
    fun `should create markdown output`() {
        // Given
        val content = "# Title\n\nSome text"

        // When
        val output = FormattedOutput.markdown(content)

        // Then
        assertEquals(1, output.blocks.size)
        assertEquals(AgentOutputType.MARKDOWN, output.blocks.first().type)
        assertEquals(content, output.blocks.first().content)
    }

    @Test
    fun `should create empty output`() {
        // When
        val output = FormattedOutput.empty()

        // Then
        assertTrue(output.blocks.isEmpty())
        assertNull(output.rawContent)
        assertTrue(output.isEmpty())
        assertFalse(output.isNotEmpty())
    }

    @Test
    fun `should get blocks by type`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("Text", 0),
            FormattedOutputBlock.codeBlock("code", "kotlin", 1),
            FormattedOutputBlock.codeBlock("code2", "python", 2),
            FormattedOutputBlock.terminal("output", "ls", 0, 0L, true, 3)
        )
        val output = FormattedOutput.multiple(blocks)

        // When
        val codeBlocks = output.getBlocksByType(AgentOutputType.CODE_BLOCKS)
        val terminalBlocks = output.getBlocksByType(AgentOutputType.TERMINAL)
        val markdownBlocks = output.getBlocksByType(AgentOutputType.MARKDOWN)

        // Then
        assertEquals(2, codeBlocks.size)
        assertEquals(1, terminalBlocks.size)
        assertEquals(1, markdownBlocks.size)
    }

    @Test
    fun `should get first block by type`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("Text", 0),
            FormattedOutputBlock.codeBlock("code1", "kotlin", 1),
            FormattedOutputBlock.codeBlock("code2", "python", 2)
        )
        val output = FormattedOutput.multiple(blocks)

        // When
        val firstCodeBlock = output.getFirstBlockByType(AgentOutputType.CODE_BLOCKS)
        val firstTerminalBlock = output.getFirstBlockByType(AgentOutputType.TERMINAL)

        // Then
        assertNotNull(firstCodeBlock)
        assertEquals("code1", firstCodeBlock!!.content)
        assertNull(firstTerminalBlock)
    }

    @Test
    fun `should check if has block type`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("Text", 0),
            FormattedOutputBlock.codeBlock("code", "kotlin", 1)
        )
        val output = FormattedOutput.multiple(blocks)

        // When
        val hasMarkdown = output.hasBlockType(AgentOutputType.MARKDOWN)
        val hasCode = output.hasBlockType(AgentOutputType.CODE_BLOCKS)
        val hasTerminal = output.hasBlockType(AgentOutputType.TERMINAL)

        // Then
        assertTrue(hasMarkdown)
        assertTrue(hasCode)
        assertFalse(hasTerminal)
    }

    @Test
    fun `should get block count`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("Text", 0),
            FormattedOutputBlock.codeBlock("code", "kotlin", 1),
            FormattedOutputBlock.terminal("output", "ls", 0, 0L, true, 2)
        )
        val output = FormattedOutput.multiple(blocks)

        // When
        val count = output.getBlockCount()

        // Then
        assertEquals(3, count)
    }

    @Test
    fun `should handle empty and non-empty states`() {
        // Given
        val emptyOutput = FormattedOutput.empty()
        val nonEmptyOutput = FormattedOutput.markdown("Content")

        // When & Then
        assertTrue(emptyOutput.isEmpty())
        assertFalse(emptyOutput.isNotEmpty())

        assertFalse(nonEmptyOutput.isEmpty())
        assertTrue(nonEmptyOutput.isNotEmpty())
    }

    @Test
    fun `should handle raw content`() {
        // Given
        val rawContent = "Raw fallback content"
        val block = FormattedOutputBlock.markdown("Formatted content")

        // When
        val outputWithRaw = FormattedOutput(listOf(block), rawContent)
        val outputWithoutRaw = FormattedOutput(listOf(block))

        // Then
        assertEquals(rawContent, outputWithRaw.rawContent)
        assertNull(outputWithoutRaw.rawContent)
    }

    @Test
    fun `should preserve order in multiple blocks`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("Third", order = 3),
            FormattedOutputBlock.markdown("First", order = 1),
            FormattedOutputBlock.markdown("Second", order = 2),
            FormattedOutputBlock.markdown("Fourth", order = 4)
        )

        // When
        val output = FormattedOutput.multiple(blocks)

        // Then
        assertEquals(4, output.blocks.size)
        assertEquals("First", output.blocks[0].content)
        assertEquals("Second", output.blocks[1].content)
        assertEquals("Third", output.blocks[2].content)
        assertEquals("Fourth", output.blocks[3].content)
    }

    @Test
    fun `should handle blocks with same order`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("First", order = 1),
            FormattedOutputBlock.codeBlock("code", "kotlin", order = 1),
            FormattedOutputBlock.markdown("Second", order = 2)
        )

        // When
        val output = FormattedOutput.multiple(blocks)

        // Then
        assertEquals(3, output.blocks.size)
        // Блоки с одинаковым order сохраняют относительный порядок
        assertTrue(output.blocks[0].content.contains("First"))
        assertTrue(output.blocks[1].content.contains("code"))
        assertTrue(output.blocks[2].content.contains("Second"))
    }
}