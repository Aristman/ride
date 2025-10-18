package ru.marslab.ide.ride.ui.renderer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import ru.marslab.ide.ride.model.agent.*
import ru.marslab.ide.ride.ui.manager.MessageDisplayManager
import io.mockk.mockk

class AgentOutputRendererTest {

    private lateinit var renderer: AgentOutputRenderer

    @BeforeEach
    fun setUp() {
        // Создаем renderer с моком MessageDisplayManager
        val mockMessageDisplayManager = mockk<MessageDisplayManager>(relaxed = true)
        renderer = AgentOutputRenderer()
    }

    @Test
    fun `should render terminal block correctly`() {
        // Given
        val terminalBlock = FormattedOutputBlock.terminal(
            content = "Command output",
            command = "git status",
            exitCode = 0,
            executionTime = 100L,
            success = true,
            order = 0
        )
        val formattedOutput = FormattedOutput.single(terminalBlock)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertTrue(html.contains("agent-output-container"))
        assertTrue(html.contains("terminal-output"))
        assertTrue(html.contains("terminal-header"))
        assertTrue(html.contains("git status"))
        assertTrue(html.contains("Exit Code: 0"))
        assertTrue(html.contains("Execution Time: 100ms"))
        assertTrue(html.contains("status-success"))
        assertTrue(html.contains("Command output"))
    }

    @Test
    fun `should render failed terminal block correctly`() {
        // Given
        val terminalBlock = FormattedOutputBlock.terminal(
            content = "Error output",
            command = "invalid-command",
            exitCode = 127,
            executionTime = 50L,
            success = false,
            order = 0
        )
        val formattedOutput = FormattedOutput.single(terminalBlock)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertTrue(html.contains("status-error"))
        assertTrue(html.contains("Exit Code: 127"))
        assertTrue(html.contains("Error output"))
    }

    @Test
    fun `should render code block correctly`() {
        // Given
        val codeBlock = FormattedOutputBlock.codeBlock(
            content = "fun hello() { println(\"Hello\") }",
            language = "kotlin",
            order = 0
        )
        val formattedOutput = FormattedOutput.single(codeBlock)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertTrue(html.contains("code-block-container"))
        assertTrue(html.contains("code-block-header"))
        assertTrue(html.contains("code-language"))
        assertTrue(html.contains("kotlin"))
        assertTrue(html.contains("code-copy-btn"))
        assertTrue(html.contains("language-kotlin"))
        assertTrue(html.contains("fun hello()"))
    }

    @Test
    fun `should render tool result block correctly`() {
        // Given
        val toolBlock = FormattedOutputBlock.toolResult(
            content = "Operation completed successfully",
            toolName = "file-create",
            operationType = "create",
            success = true,
            order = 0
        )
        val formattedOutput = FormattedOutput.single(toolBlock)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertTrue(html.contains("tool-result-block"))
        assertTrue(html.contains("tool-result-header"))
        assertTrue(html.contains("tool-info"))
        assertTrue(html.contains("file-create"))
        assertTrue(html.contains("create"))
        assertTrue(html.contains("status-success"))
        assertTrue(html.contains("Operation completed successfully"))
    }

    @Test
    fun `should render structured block correctly`() {
        // Given
        val structuredBlock = FormattedOutputBlock.structured(
            content = """{"key": "value", "number": 123}""",
            format = "json",
            order = 0
        )
        val formattedOutput = FormattedOutput.single(structuredBlock)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertTrue(html.contains("structured-block"))
        assertTrue(html.contains("structured-header"))
        assertTrue(html.contains("format-label"))
        assertTrue(html.contains("json"))
        assertTrue(html.contains("toggle-structured"))
        assertTrue(html.contains("structured-content"))
        assertTrue(html.contains("language-json"))
        assertTrue(html.contains("{\"key\": \"value\", \"number\": 123}"))
    }

    @Test
    fun `should render HTML block correctly`() {
        // Given
        val htmlBlock = FormattedOutputBlock.html(
            content = "<div class='custom'>Custom HTML content</div>",
            cssClasses = listOf("custom-class"),
            order = 0
        )
        val formattedOutput = FormattedOutput.single(htmlBlock)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertTrue(html.contains("custom-class"))
        assertTrue(html.contains("<div class='custom'>Custom HTML content</div>"))
    }

    @Test
    fun `should render multiple blocks with separators`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("First block", 0),
            FormattedOutputBlock.codeBlock("fun test() {}", "kotlin", 1),
            FormattedOutputBlock.markdown("Last block", 2)
        )
        val formattedOutput = FormattedOutput.multiple(blocks)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertTrue(html.contains("block-item"))
        assertTrue(html.contains("First block"))
        assertTrue(html.contains("code-block-container"))
        assertTrue(html.contains("Last block"))
        assertTrue(html.contains("block-separator"))
    }

    @Test
    fun `should render multiple blocks in correct order`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("Third", order = 2),
            FormattedOutputBlock.markdown("First", order = 0),
            FormattedOutputBlock.markdown("Second", order = 1)
        )
        val formattedOutput = FormattedOutput.multiple(blocks)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        val firstIndex = html.indexOf("First")
        val secondIndex = html.indexOf("Second")
        val thirdIndex = html.indexOf("Third")

        assertTrue(firstIndex < secondIndex)
        assertTrue(secondIndex < thirdIndex)
    }

    @Test
    fun `should escape HTML in content blocks`() {
        // Given
        val terminalBlock = FormattedOutputBlock.terminal(
            content = "<script>alert('xss')</script>",
            command = "test",
            exitCode = 0,
            executionTime = 0L,
            success = true,
            order = 0
        )
        val formattedOutput = FormattedOutput.single(terminalBlock)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertFalse(html.contains("<script>alert('xss')</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;"))
    }

    @Test
    fun `should handle empty formatted output`() {
        // Given
        val formattedOutput = FormattedOutput.empty()

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertTrue(html.contains("agent-output-container"))
        assertEquals("<div class=\"agent-output-container\"></div>", html)
    }

    @Test
    fun `should fallback to raw content on error`() {
        // Given
        val formattedOutput = FormattedOutput(
            blocks = emptyList(),
            rawContent = "Fallback content"
        )

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertEquals("Fallback content", html)
    }

    @Test
    fun `should create interaction scripts`() {
        // When
        val scripts = renderer.createInteractionScripts()

        // Then
        assertTrue(scripts.contains("function copyCodeBlock"))
        assertTrue(scripts.contains("function toggleStructured"))
        assertTrue(scripts.contains("function toggleMetadata"))
        assertTrue(scripts.contains("function viewFullContent"))
        assertTrue(scripts.contains("navigator.clipboard.writeText"))
    }

    @Test
    fun `should render mixed content blocks`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("# Title\n\nSome intro text.", 0),
            FormattedOutputBlock.codeBlock("code example", "python", 1),
            FormattedOutputBlock.toolResult("File created", "file-write", "write", true, 2),
            FormattedOutputBlock.terminal("output", "ls", 0, 10L, true, 3)
        )
        val formattedOutput = FormattedOutput.multiple(blocks)

        // When
        val html = renderer.render(formattedOutput)

        // Then
        assertTrue(html.contains("# Title"))
        assertTrue(html.contains("code-block-container"))
        assertTrue(html.contains("tool-result-block"))
        assertTrue(html.contains("terminal-output"))
        assertTrue(html.contains("block-separator"))
    }
}