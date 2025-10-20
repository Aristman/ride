package ru.marslab.ide.ride.agent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.mockk.*
import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.llm.TokenUsage
import ru.marslab.ide.ride.settings.PluginSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatAgentNewTest {

    private val mockProject = mockk<Project>(relaxed = true)
    private val mockPluginSettings = mockk<PluginSettings>(relaxed = true)
    private val mockApplication = mockk<com.intellij.openapi.application.Application>(relaxed = true)

    init {
        // Mock the ApplicationManager and Application
        mockkStatic("com.intellij.openapi.application.ApplicationManager")
        every { ApplicationManager.getApplication() } returns mockApplication
        every { mockApplication.getService(PluginSettings::class.java) } returns mockPluginSettings

        // Mock the PluginSettings service
        mockkStatic("com.intellij.openapi.components.ServiceKt")
        every { service<PluginSettings>() } returns mockPluginSettings
        every { mockPluginSettings.enableUncertaintyAnalysis } returns false
        every { mockPluginSettings.maxContextTokens } returns 8000
        every { mockPluginSettings.enableAutoSummarization } returns false // Disable to avoid SummarizerAgent
    }

    @Test
    fun `test successful request processing`() = runTest {
        // Arrange
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.isAvailable() } returns true
        every { mockProvider.getProviderName() } returns "Test Provider"
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns LLMResponse.success("Test response", TokenUsage(50, 50, 100))

        val agent = ChatAgent(mockProvider)
        val context = ChatContext(
            project = mockProject,
            history = emptyList(),
            currentFile = null,
            selectedText = null
        )

        val request = AgentRequest(
            request = "Test request",
            context = context,
            parameters = LLMParameters.DEFAULT
        )

        // Act
        val response = agent.ask(request)

        // Assert
        assertTrue(response.success)
        assertEquals("Test response", response.content)
        assertTrue(response.metadata.isNotEmpty())
        assertEquals("Test Provider", response.metadata["provider"])
    }

    @Test
    fun `test request when provider is not available`() = runTest {
        // Arrange
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.isAvailable() } returns false
        every { mockProvider.getProviderName() } returns "Test Provider"

        val agent = ChatAgent(mockProvider)
        val context = ChatContext(
            project = mockProject,
            history = emptyList(),
            currentFile = null,
            selectedText = null
        )

        val request = AgentRequest(
            request = "Test request",
            context = context,
            parameters = LLMParameters.DEFAULT
        )

        // Act
        val response = agent.ask(request)

        // Assert
        assertFalse(response.success)
        assertTrue(response.error?.contains("недоступен") == true)
    }

    @Test
    fun `test request when provider returns error`() = runTest {
        // Arrange
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.isAvailable() } returns true
        every { mockProvider.getProviderName() } returns "Test Provider"
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns LLMResponse.error("API Error")

        val agent = ChatAgent(mockProvider)
        val context = ChatContext(
            project = mockProject,
            history = emptyList(),
            currentFile = null,
            selectedText = null
        )

        val request = AgentRequest(
            request = "Test request",
            context = context,
            parameters = LLMParameters.DEFAULT
        )

        // Act
        val response = agent.ask(request)

        // Assert
        assertFalse(response.success)
        assertEquals("API Error", response.error)
    }

    @Test
    fun `test agent capabilities`() {
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.getProviderName() } returns "Test Provider"

        val agent = ChatAgent(mockProvider)

        assertEquals(true, agent.capabilities.stateful)
        assertEquals(false, agent.capabilities.streaming)
        assertEquals(true, agent.capabilities.reasoning)
        assertNotNull(agent.capabilities.systemPrompt)
        assertTrue(agent.capabilities.responseRules.isNotEmpty())
    }

    @Test
    fun `test request with context history`() = runTest {
        // Arrange
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.isAvailable() } returns true
        every { mockProvider.getProviderName() } returns "Test Provider"
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns LLMResponse.success("Context-aware response", TokenUsage(30, 20, 50))

        val agent = ChatAgent(mockProvider)
        val context = ChatContext(
            project = mockProject,
            history = listOf(
                ru.marslab.ide.ride.model.chat.Message(
                    content = "Previous question",
                    role = ru.marslab.ide.ride.model.chat.MessageRole.USER
                ),
                ru.marslab.ide.ride.model.chat.Message(
                    content = "Previous answer",
                    role = ru.marslab.ide.ride.model.chat.MessageRole.ASSISTANT
                )
            ),
            currentFile = null,
            selectedText = null
        )

        val request = AgentRequest(
            request = "Follow-up question",
            context = context,
            parameters = LLMParameters.DEFAULT
        )

        // Act
        val response = agent.ask(request)

        // Assert
        assertTrue(response.success)
        assertEquals("Context-aware response", response.content)
        assertTrue(response.metadata.isNotEmpty())
        assertEquals("Test Provider", response.metadata["provider"])
    }

    @Test
    fun `test request with file context`() = runTest {
        // Arrange
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.isAvailable() } returns true
        every { mockProvider.getProviderName() } returns "Test Provider"
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns LLMResponse.success("File-aware response", TokenUsage(25, 25, 50))

        val agent = ChatAgent(mockProvider)
        val mockFile = mockk<com.intellij.openapi.vfs.VirtualFile>(relaxed = true)
        every { mockFile.path } returns "/test/file.kt"
        every { mockFile.name } returns "file.kt"

        val context = ChatContext(
            project = mockProject,
            history = emptyList(),
            currentFile = mockFile,
            selectedText = "selected code"
        )

        val request = AgentRequest(
            request = "Analyze this code",
            context = context,
            parameters = LLMParameters.DEFAULT
        )

        // Act
        val response = agent.ask(request)

        // Assert
        assertTrue(response.success)
        assertEquals("File-aware response", response.content)
        assertTrue(response.metadata.isNotEmpty())
    }

    @Test
    fun `test agent dispose`() {
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.getProviderName() } returns "Test Provider"

        val agent = ChatAgent(mockProvider)

        // Should not throw exception
        agent.dispose()
    }
}