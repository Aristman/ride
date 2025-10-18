package ru.marslab.ide.ride.formatter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ru.marslab.ide.ride.model.agent.AgentOutputType
import ru.marslab.ide.ride.model.agent.FormattedOutputBlock

class TerminalOutputFormatterTest {

    private val formatter = TerminalOutputFormatter()

    @Test
    fun `should format successful command result`() {
        // Given
        val command = "git status"
        val exitCode = 0
        val executionTime = 145L
        val stdout = "On branch main\nYour branch is up to date\nChanges not staged for commit"
        val stderr = ""

        // When
        val result = formatter.formatAsHtml(command, exitCode, executionTime, stdout, stderr, true)

        // Then
        assertFalse(result.blocks.isEmpty())
        assertEquals(1, result.blocks.size)

        val block = result.blocks.first()
        assertEquals(AgentOutputType.TERMINAL, block.type)
        assertTrue(block.cssClasses.contains("terminal-output"))
        assertTrue(block.content.contains("Changes not staged for commit"))
        assertEquals(command, block.metadata["command"])
        assertEquals(exitCode, block.metadata["exitCode"])
        assertEquals(executionTime, block.metadata["executionTime"])
        assertEquals(true, block.metadata["success"])
    }

    @Test
    fun `should format failed command result`() {
        // Given
        val command = "invalid-command"
        val exitCode = 127
        val executionTime = 50L
        val stdout = ""
        val stderr = "command not found: invalid-command"

        // When
        val result = formatter.formatAsHtml(command, exitCode, executionTime, stdout, stderr, false)

        // Then
        assertFalse(result.blocks.isEmpty())
        assertEquals(1, result.blocks.size)

        val block = result.blocks.first()
        assertEquals(AgentOutputType.TERMINAL, block.type)
        assertTrue(block.content.contains("command not found"))
        assertEquals(false, block.metadata["success"])
    }

    @Test
    fun `should handle empty output`() {
        // Given
        val command = "echo"
        val exitCode = 0
        val executionTime = 10L
        val stdout = ""
        val stderr = ""

        // When
        val result = formatter.formatAsHtml(command, exitCode, executionTime, stdout, stderr, true)

        // Then
        assertFalse(result.blocks.isEmpty())
        assertTrue(result.blocks.first().content.contains("(No output)"))
    }

    @Test
    fun `should create terminal window HTML`() {
        // Given
        val command = "ls -la"
        val exitCode = 0
        val executionTime = 25L
        val stdout = "total 0\ndrwxr-xr-x  2 user user  64 Jan 1 12:00 ."
        val stderr = ""

        // When
        val html = formatter.createTerminalWindow(command, exitCode, executionTime, stdout, stderr, true)

        // Then
        assertTrue(html.contains("terminal-output"))
        assertTrue(html.contains("terminal-header"))
        assertTrue(html.contains("terminal-info"))
        assertTrue(html.contains("terminal-body"))
        assertTrue(html.contains(command))
        assertTrue(html.contains("$exitCode"))
        assertTrue(html.contains("${executionTime}ms"))
        assertTrue(html.contains("status-success"))
        assertTrue(html.contains("total 0"))
    }

    @Test
    fun `should escape HTML in output`() {
        // Given
        val command = "echo \"<script>alert('xss')</script>\""
        val exitCode = 0
        val executionTime = 10L
        val stdout = "<script>alert('xss')</script>"
        val stderr = ""

        // When
        val html = formatter.createTerminalWindow(command, exitCode, executionTime, stdout, stderr, true)

        // Then
        assertFalse(html.contains("<script>alert('xss')</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;"))
    }

    @Test
    fun `should format error result`() {
        // Given
        val command = "nonexistent"
        val errorMessage = "Command not found"
        val executionTime = 5L

        // When
        val result = formatter.formatError(command, errorMessage, executionTime)

        // Then
        assertFalse(result.blocks.isEmpty())
        assertEquals(1, result.blocks.size)

        val block = result.blocks.first()
        assertEquals(AgentOutputType.MARKDOWN, block.type)
        assertTrue(block.content.contains(command))
        assertTrue(block.content.contains(errorMessage))
        assertTrue(block.content.contains("Error"))
    }

    @Test
    fun `should format text fallback`() {
        // Given
        val command = "test"
        val exitCode = 0
        val executionTime = 10L
        val stdout = "test output"
        val stderr = ""

        // When
        val result = formatter.formatAsText(command, exitCode, executionTime, stdout, stderr, true)

        // Then
        assertFalse(result.blocks.isEmpty())
        assertEquals(1, result.blocks.size)

        val block = result.blocks.first()
        assertEquals(AgentOutputType.MARKDOWN, block.type)
        assertTrue(block.content.contains("Command: $command"))
        assertTrue(block.content.contains("Exit Code: $exitCode"))
        assertTrue(block.content.contains("Execution Time: ${executionTime}ms"))
        assertTrue(block.content.contains("Success ✅"))
        assertTrue(block.content.contains(stdout))
    }
}