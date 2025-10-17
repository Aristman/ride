package ru.marslab.ide.ride.agent.impl

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerminalAgentTest {

    private lateinit var agent: TerminalAgent
    private lateinit var mockContext: ChatContext

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        agent = TerminalAgent()
        mockContext = mockk<ChatContext> {
            every { getRecentHistory(any()) } returns emptyList()
            every { hasSelectedText() } returns false
            every { hasCurrentFile() } returns false
        }
    }

    @AfterEach
    fun tearDown() {
        agent.dispose()
    }

    @Test
    fun `executes simple echo command successfully`() = runTest {
        val request = AgentRequest(
            request = "echo Hello World",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertTrue(response.success)
        assertNotNull(response.content)
        assertTrue(response.content.contains("Hello World"))
        assertTrue(response.content.contains("✅ Success"))
        assertTrue(response.content.contains("Exit Code: 0"))
    }

    @Test
    fun `executes command with directory listing`() = runTest {
        val request = AgentRequest(
            request = "ls -la",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertTrue(response.success)
        assertNotNull(response.content)
        assertTrue(response.content.contains("ls -la"))
    }

    @Test
    fun `handles failed command properly`() = runTest {
        val request = AgentRequest(
            request = "exit 1",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertFalse(response.success)
        assertNotNull(response.error)
        assertTrue(response.error!!.contains("exit code 1"))
        assertTrue(response.content.contains("❌ Failed"))
        assertTrue(response.content.contains("Exit Code: 1"))
    }

    @Test
    fun `executes command with working directory`() = runTest {
        // Create a test file in temp directory
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("test content")

        val request = AgentRequest(
            request = "cd ${tempDir.absolutePath} && ls",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertTrue(response.success)
        assertTrue(response.content.contains("test.txt"))
    }

    @Test
    fun `handles non-existent working directory gracefully`() = runTest {
        val request = AgentRequest(
            request = "cd /non/existent/directory && echo test",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        // Command should still execute, just without changing directory
        assertTrue(response.success || response.error != null)
    }

    @Test
    fun `returns proper metadata in response`() = runTest {
        val request = AgentRequest(
            request = "echo metadata test",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertTrue(response.success)
        assertEquals("echo metadata test", response.metadata["command"])
        assertEquals(0, response.metadata["exitCode"])
        assertNotNull(response.metadata["executionTime"])
        assertNotNull(response.metadata["workingDir"])
    }

    @Test
    fun `provides streaming execution with progress updates`() = runTest {
        val request = AgentRequest(
            request = "echo streaming test",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val flow = agent.start(request)
        assertNotNull(flow)

        val events = mutableListOf<ru.marslab.ide.ride.model.agent.AgentEvent>()
        flow.collect { events.add(it) }

        assertTrue(events.isNotEmpty())

        // Check for expected event types
        assertTrue(events.any { it is ru.marslab.ide.ride.model.agent.AgentEvent.Started })
        assertTrue(events.any { it is ru.marslab.ide.ride.model.agent.AgentEvent.Progress })
        assertTrue(events.any { it is ru.marslab.ide.ride.model.agent.AgentEvent.Content })
        assertTrue(events.any { it is ru.marslab.ide.ride.model.agent.AgentEvent.Completed })
    }

    @Test
    fun `handles invalid command gracefully`() = runTest {
        val request = AgentRequest(
            request = "nonexistentcommand12345",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertFalse(response.success)
        assertNotNull(response.error)
        assertTrue(response.content.contains("❌ Failed"))
    }

    @Test
    fun `handles empty command`() = runTest {
        val request = AgentRequest(
            request = "",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        // Should handle empty command gracefully
        assertTrue(response.success || response.error != null)
    }

    @Test
    fun `processes command with stderr output`() = runTest {
        val request = AgentRequest(
            request = "echo 'Error message' >&2",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertTrue(response.success) // echo command succeeds even with stderr
        assertTrue(response.content.contains("Errors:"))
        assertTrue(response.content.contains("Error message"))
    }

    @Test
    fun `can update settings`() = runTest {
        val settings = ru.marslab.ide.ride.model.agent.AgentSettings()

        // Should not throw exception
        agent.updateSettings(settings)
    }

    @Test
    fun `provides correct capabilities`() {
        val capabilities = agent.capabilities

        assertFalse(capabilities.stateful)
        assertTrue(capabilities.streaming)
        assertFalse(capabilities.reasoning)
        assertTrue(capabilities.tools.contains("terminal"))
        assertTrue(capabilities.tools.contains("shell"))
        assertTrue(capabilities.tools.contains("command-execution"))
        assertNotNull(capabilities.systemPrompt)
        assertTrue(capabilities.responseRules.isNotEmpty())
    }
}