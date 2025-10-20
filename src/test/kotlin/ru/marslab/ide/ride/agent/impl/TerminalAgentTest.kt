package ru.marslab.ide.ride.agent.impl

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerminalAgentTest {

    private lateinit var agent: TerminalAgent
    private lateinit var mockProject: com.intellij.openapi.project.Project
    private lateinit var mockContext: ChatContext

    @BeforeTest
    fun setUp() {
        agent = TerminalAgent()
        mockProject = mockk(relaxed = true) {
            every { basePath } returns System.getProperty("user.dir")
        }
        mockContext = ChatContext(
            project = mockProject,
            history = emptyList(),
            currentFile = null,
            selectedText = null
        )
    }

    @AfterTest
    fun tearDown() {
        agent.dispose()
    }

    @Test
    fun `test terminal agent capabilities`() {
        val capabilities = agent.capabilities

        assertFalse(capabilities.stateful, "Terminal should be stateless")
        assertTrue(capabilities.streaming, "Terminal should support streaming")
        assertFalse(capabilities.reasoning, "Terminal should not use reasoning")
        assertTrue(capabilities.tools.any { it.contains("terminal") })
        assertTrue(capabilities.tools.any { it.contains("shell") })
        assertEquals("Агент для выполнения команд в локальном терминале", capabilities.systemPrompt)
    }

    @Test
    fun `executes simple echo command successfully`() = runTest {
        val request = AgentRequest(
            request = "echo Hello World",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertTrue(response.success, "Echo command should succeed")
        assertTrue(response.content.contains("Hello World"), "Should contain output")
        assertTrue(response.content.contains("echo Hello World"), "Should contain command")
        assertTrue(response.metadata.containsKey("command"))
        assertTrue(response.metadata.containsKey("exitCode"))
        assertEquals(0, response.metadata["exitCode"])
    }

    @Test
    fun `handles failing command`() = runTest {
        val request = AgentRequest(
            request = "exit 1",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertFalse(response.success, "Command with exit 1 should fail")
        assertEquals(1, response.metadata["exitCode"])
        assertTrue(response.error?.contains("exit code 1") == true)
    }

    @Test
    fun `handles invalid command`() = runTest {
        val request = AgentRequest(
            request = "nonexistentcommand12345",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertFalse(response.success, "Invalid command should fail")
        assertNotNull(response.error, "Should have error message")
    }

    @Test
    fun `test update settings`() {
        val settings = ru.marslab.ide.ride.model.agent.AgentSettings(maxContextTokens = 5000)

        // Should not throw
        agent.updateSettings(settings)
    }

    @Test
    fun `test dispose`() {
        // Should not throw
        agent.dispose()
    }

    @Test
    fun `test metadata includes execution details`() = runTest {
        val request = AgentRequest(
            request = "echo test",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertTrue(response.success)
        assertTrue(response.metadata.containsKey("command"))
        assertTrue(response.metadata.containsKey("exitCode"))
        assertTrue(response.metadata.containsKey("executionTime"))
        assertTrue(response.metadata.containsKey("workingDir"))

        assertEquals("echo test", response.metadata["command"])
        assertEquals(0, response.metadata["exitCode"])
        assertTrue(response.metadata["executionTime"] != null, "Execution time should not be null")
    }

    @Test
    fun `test formatted output is provided`() = runTest {
        val request = AgentRequest(
            request = "echo formatted test",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertTrue(response.success)
        assertNotNull(response.formattedOutput, "Should provide formatted output")
        assertTrue(response.formattedOutput!!.isNotEmpty(), "Formatted output should not be empty")
        assertTrue(response.formattedOutput!!.isNotEmpty(), "Formatted output should contain command details")
    }

    @Test
    fun `test start method returns flow`() = runTest {
        val request = AgentRequest(
            request = "echo streaming test",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val flow = agent.start(request)
        assertNotNull(flow, "Should return flow for streaming")

        // Collect events
        val events = mutableListOf<ru.marslab.ide.ride.model.agent.AgentEvent>()
        flow?.collect { events.add(it) }

        assertTrue(events.isNotEmpty(), "Should emit at least one event")
        assertTrue(events.any { it::class.simpleName == "Started" })
        assertTrue(events.any { it::class.simpleName == "ContentChunk" })
    }

    @Test
    fun `test response content structure`() = runTest {
        val request = AgentRequest(
            request = "echo content structure test",
            context = mockContext,
            parameters = LLMParameters.DEFAULT
        )

        val response = agent.ask(request)

        assertTrue(response.success)
        assertTrue(response.content.contains("Command:"))
        assertTrue(response.content.contains("Exit Code:"))
        assertTrue(response.content.contains("Execution Time:"))
        assertTrue(response.content.contains("content structure test"))
    }
}