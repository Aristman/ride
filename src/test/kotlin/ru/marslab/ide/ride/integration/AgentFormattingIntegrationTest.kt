package ru.marslab.ide.ride.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import ru.marslab.ide.ride.model.agent.*
import ru.marslab.ide.ride.formatter.*
import ru.marslab.ide.ride.ui.renderer.AgentOutputRenderer
import io.mockk.mockk

/**
 * Интеграционные тесты для проверки работы форматированного вывода агентов
 */
class AgentFormattingIntegrationTest {

    private lateinit var terminalOutputFormatter: TerminalOutputFormatter
    private lateinit var codeBlockFormatter: CodeBlockFormatter
    private lateinit var chatOutputFormatter: ChatOutputFormatter
    private lateinit var toolResultFormatter: ToolResultFormatter
    private lateinit var agentOutputRenderer: AgentOutputRenderer

    @BeforeEach
    fun setUp() {
        terminalOutputFormatter = TerminalOutputFormatter()
        codeBlockFormatter = CodeBlockFormatter()
        chatOutputFormatter = ChatOutputFormatter()
        toolResultFormatter = ToolResultFormatter()
        agentOutputRenderer = AgentOutputRenderer()
    }

    @Test
    fun `terminal agent should produce formatted output`() {
        // Given
        val command = "git status"
        val exitCode = 0
        val executionTime = 145L
        val stdout = "On branch main\nYour branch is up to date"
        val stderr = ""

        // When
        val formattedOutput = terminalOutputFormatter.formatAsHtml(
            command, exitCode, executionTime, stdout, stderr, true
        )
        val html = agentOutputRenderer.render(formattedOutput)

        // Then
        assertNotNull(formattedOutput)
        assertFalse(formattedOutput.blocks.isEmpty())
        assertEquals(1, formattedOutput.blocks.size)

        val block = formattedOutput.blocks.first()
        assertEquals(AgentOutputType.TERMINAL, block.type)
        assertEquals(command, block.metadata["command"])
        assertEquals(exitCode, block.metadata["exitCode"])

        // Проверяем HTML
        assertTrue(html.contains("terminal-output"))
        assertTrue(html.contains(command))
        assertTrue(html.contains("Your branch is up to date"))
        assertTrue(html.contains("status-success"))
    }

    @Test
    fun `code block formatter should handle complex markdown`() {
        // Given
        val complexMarkdown = """
            # Example Code

            Here's a simple function in Kotlin:

            ```kotlin
            fun greet(name: String): String {
                return "Hello, $name!"
            }
            ```

            And here's the same in Python:

            ```python
            def greet(name):
                return f"Hello, {name}!"
            ```

            This demonstrates multiple code blocks.
        """.trimIndent()

        // When
        val formattedOutput = codeBlockFormatter.formatAsHtml(complexMarkdown)
        val html = agentOutputRenderer.render(formattedOutput)

        // Then
        assertEquals(5, formattedOutput.blocks.size) // text, kotlin, text, python, text
        assertEquals(2, formattedOutput.getBlocksByType(AgentOutputType.CODE_BLOCKS).size)

        // Проверяем HTML
        assertTrue(html.contains("kotlin"))
        assertTrue(html.contains("python"))
        assertTrue(html.contains("Example Code"))
        assertTrue(html.contains("greet"))
        assertTrue(html.contains("code-block-container"))
    }

    @Test
    fun `chat output formatter should process mixed content`() {
        // Given
        val mixedContent = """
            ## Task Solution

            Here's how to solve the problem:

            1. First step
            2. Second step

            Implementation:

            ```java
            public class Solution {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
            ```

            This code prints a greeting message.
        """.trimIndent()

        // When
        val formattedOutput = chatOutputFormatter.formatAsHtml(mixedContent)
        val html = agentOutputRenderer.render(formattedOutput)

        // Then
        assertFalse(formattedOutput.blocks.isEmpty())
        assertTrue(formattedOutput.hasBlockType(AgentOutputType.CODE_BLOCKS))
        assertTrue(formattedOutput.hasBlockType(AgentOutputType.MARKDOWN))

        // Проверяем HTML
        assertTrue(html.contains("Task Solution"))
        assertTrue(html.contains("First step"))
        assertTrue(html.contains("Second step"))
        assertTrue(html.contains("java"))
        assertTrue(html.contains("System.out.println"))
    }

    @Test
    fun `tool result formatter should handle file operations`() {
        // Given
        val operations = listOf(
            ToolResultFormatter.FileOperation(
                type = "create",
                path = "src/main/kotlin/Example.kt",
                content = "fun main() = println(\"Hello\")",
                success = true
            ),
            ToolResultFormatter.FileOperation(
                type = "read",
                path = "src/main/kotlin/Example.kt",
                content = "fun main() = println(\"Hello\")",
                success = true
            )
        )

        // When
        val formattedOutput = toolResultFormatter.formatMultipleOperations(operations)
        val html = agentOutputRenderer.render(formattedOutput)

        // Then
        assertEquals(4, formattedOutput.blocks.size) // create, code, read, code
        assertTrue(formattedOutput.hasBlockType(AgentOutputType.TOOL_RESULT))
        assertTrue(formattedOutput.hasBlockType(AgentOutputType.CODE_BLOCKS))

        // Проверяем HTML
        assertTrue(html.contains("tool-result-block"))
        assertTrue(html.contains("Example.kt"))
        assertTrue(html.contains("create"))
        assertTrue(html.contains("read"))
        assertTrue(html.contains("code-block-container"))
    }

    @Test
    fun `should handle multiple different block types`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("## Analysis\n\nHere's the analysis:", 0),
            FormattedOutputBlock.codeBlock("analysis result", "json", 1),
            FormattedOutputBlock.terminal("command output", "ls", 0, 50L, true, 2),
            FormattedOutputBlock.toolResult("Tool executed", "analyzer", "analyze", true, 3),
            FormattedOutputBlock.structured("structured data", "xml", 4)
        )
        val formattedOutput = FormattedOutput.multiple(blocks)

        // When
        val html = agentOutputRenderer.render(formattedOutput)

        // Then
        assertEquals(5, formattedOutput.blocks.size)
        assertTrue(formattedOutput.hasBlockType(AgentOutputType.MARKDOWN))
        assertTrue(formattedOutput.hasBlockType(AgentOutputType.CODE_BLOCKS))
        assertTrue(formattedOutput.hasBlockType(AgentOutputType.TERMINAL))
        assertTrue(formattedOutput.hasBlockType(AgentOutputType.TOOL_RESULT))
        assertTrue(formattedOutput.hasBlockType(AgentOutputType.STRUCTURED))

        // Проверяем HTML содержит все типы блоков
        assertTrue(html.contains("Analysis"))
        assertTrue(html.contains("code-block-container"))
        assertTrue(html.contains("terminal-output"))
        assertTrue(html.contains("tool-result-block"))
        assertTrue(html.contains("structured-block"))
        assertTrue(html.contains("block-separator"))
    }

    @Test
    fun `should handle agent response with formatted output`() {
        // Given
        val formattedOutput = chatOutputFormatter.formatAsHtml("Check this code:\n```kotlin\nfun test() {}\n```")
        val agentResponse = AgentResponse.success(
            content = "Check this code:\n```kotlin\nfun test() {}\n```",
            formattedOutput = formattedOutput,
            metadata = mapOf("test" to "value")
        )

        // When
        val html = agentOutputRenderer.render(agentResponse.formattedOutput!!)

        // Then
        assertNotNull(agentResponse.formattedOutput)
        assertTrue(html.contains("code-block-container"))
        assertTrue(html.contains("kotlin"))
        assertTrue(html.contains("fun test()"))
    }

    @Test
    fun `should handle fallback to raw content`() {
        // Given
        val rawContent = "Raw fallback content when formatting fails"
        val formattedOutput = FormattedOutput(emptyList(), rawContent)

        // When
        val html = agentOutputRenderer.render(formattedOutput)

        // Then
        assertEquals(rawContent, html)
        assertTrue(formattedOutput.isEmpty())
        assertFalse(formattedOutput.isNotEmpty())
    }

    @Test
    fun `should preserve block order across different types`() {
        // Given
        val blocks = listOf(
            FormattedOutputBlock.markdown("Step 1", order = 3),
            FormattedOutputBlock.codeBlock("code1", "kotlin", order = 1),
            FormattedOutputBlock.terminal("output", "ls", 0, 10L, true, order = 4),
            FormattedOutputBlock.markdown("Step 2", order = 2),
            FormattedOutputBlock.toolResult("result", "tool", "op", true, order = 0)
        )

        // When
        val formattedOutput = FormattedOutput.multiple(blocks)
        val html = agentOutputRenderer.render(formattedOutput)

        // Then
        assertEquals(5, formattedOutput.blocks.size)
        // Проверяем порядок (tool, code, step2, step1, terminal)
        assertEquals(0, formattedOutput.blocks[0].order) // tool
        assertEquals(1, formattedOutput.blocks[1].order) // code
        assertEquals(2, formattedOutput.blocks[2].order) // step2
        assertEquals(3, formattedOutput.blocks[3].order) // step1
        assertEquals(4, formattedOutput.blocks[4].order) // terminal

        // В HTML должен быть порядок 0,1,2,3,4
        val firstToolIndex = html.indexOf("result")
        val firstCodeIndex = html.indexOf("code1")
        val step2Index = html.indexOf("Step 2")
        val step1Index = html.indexOf("Step 1")
        val terminalIndex = html.indexOf("output")

        // Проверяем, что блоки идут в правильном порядке
        assertTrue(firstToolIndex < firstCodeIndex)
        assertTrue(firstCodeIndex < step2Index)
        assertTrue(step2Index < step1Index)
        assertTrue(step1Index < terminalIndex)
    }
}