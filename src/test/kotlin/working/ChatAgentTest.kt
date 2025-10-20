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
import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.model.chat.MessageRole
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.llm.TokenUsage
import ru.marslab.ide.ride.settings.PluginSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatAgentTest {

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
        val mockProject = mockk<Project>(relaxed = true)
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
        assertTrue(response.metadata.containsKey("tokenUsage"))
        assertEquals("Test Provider", response.metadata["provider"])
    }
    
    @Test
    fun `test request when provider is not available`() = runTest {
        // Arrange
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.isAvailable() } returns false
        every { mockProvider.getProviderName() } returns "Test Provider"

        val agent = ChatAgent(mockProvider)
        val mockProject = mockk<Project>(relaxed = true)
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
        val mockProject = mockk<Project>(relaxed = true)
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
        assertTrue(agent.capabilities.systemPrompt?.contains("AI-ассистент") == true)
    }
}
