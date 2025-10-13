package ru.marslab.ide.ride.agent.impl

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Тесты для SummarizerAgent
 */
class SummarizerAgentTest {
    
    private lateinit var mockProvider: LLMProvider
    private lateinit var summarizerAgent: SummarizerAgent
    private lateinit var mockProject: com.intellij.openapi.project.Project
    
    @Before
    fun setup() {
        mockProvider = mockk()
        mockProject = mockk(relaxed = true)
        summarizerAgent = SummarizerAgent(mockProvider)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `test summarizer agent capabilities`() {
        val capabilities = summarizerAgent.capabilities
        
        assertFalse(capabilities.stateful, "Summarizer should be stateless")
        assertFalse(capabilities.streaming, "Summarizer should not support streaming")
        assertTrue(capabilities.reasoning, "Summarizer should support reasoning")
    }
    
    @Test
    fun `test successful summarization`() = runBlocking {
        // Arrange
        val history = listOf(
            Message(content = "Hello, how are you?", role = MessageRole.USER),
            Message(content = "I'm fine, thanks!", role = MessageRole.ASSISTANT),
            Message(content = "Can you help me with Kotlin?", role = MessageRole.USER),
            Message(content = "Of course! What do you need help with?", role = MessageRole.ASSISTANT)
        )
        
        val context = ChatContext(
            project = mockProject,
            history = history
        )
        
        val request = AgentRequest(
            request = "Summarize the conversation",
            context = context,
            parameters = LLMParameters.PRECISE
        )
        
        val mockResponse = LLMResponse.success(
            content = "User asked for help with Kotlin. Assistant agreed to help.",
            tokenUsage = TokenUsage(inputTokens = 50, outputTokens = 20, totalTokens = 70)
        )
        
        coEvery {
            mockProvider.sendRequest(
                any(), any(), any(), any()
            )
        } returns mockResponse
        
        // Act
        val response = summarizerAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Summarization should succeed")
        assertTrue(response.isFinal, "Summary should be final")
        assertEquals(0.0, response.uncertainty, "Summary should have zero uncertainty")
        assertTrue(response.content.contains("Kotlin"), "Summary should contain key information")
        
        coVerify {
            mockProvider.sendRequest(
                systemPrompt = match { it.contains("сжатия истории") },
                userMessage = match { it.contains("резюме") },
                conversationHistory = emptyList(),
                parameters = match { it.temperature == 0.3 && it.maxTokens == 1000 }
            )
        }
    }
    
    @Test
    fun `test summarization with empty history`() = runBlocking {
        // Arrange
        val context = ChatContext(
            project = mockProject,
            history = emptyList()
        )
        
        val request = AgentRequest(
            request = "Summarize the conversation",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        val mockResponse = LLMResponse.success(
            content = "История диалога пуста.",
            tokenUsage = TokenUsage(inputTokens = 10, outputTokens = 5, totalTokens = 15)
        )
        
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse
        
        // Act
        val response = summarizerAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Summarization should succeed even with empty history")
        
        coVerify {
            mockProvider.sendRequest(
                systemPrompt = any(),
                userMessage = match { it.contains("пуста") },
                conversationHistory = emptyList(),
                parameters = any()
            )
        }
    }
    
    @Test
    fun `test summarization failure`() = runBlocking {
        // Arrange
        val history = listOf(
            Message(content = "Test message", role = MessageRole.USER)
        )
        
        val context = ChatContext(
            project = mockProject,
            history = history
        )
        
        val request = AgentRequest(
            request = "Summarize",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        val mockResponse = LLMResponse.error("API error")
        
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse
        
        // Act
        val response = summarizerAgent.ask(request)
        
        // Assert
        assertFalse(response.success, "Summarization should fail when LLM fails")
        assertTrue(response.error?.contains("резюме") == true, "Error should mention summary")
    }
    
    @Test
    fun `test summarization with long history`() = runBlocking {
        // Arrange
        val longHistory = (1..20).flatMap { i ->
            listOf(
                Message(content = "User message $i", role = MessageRole.USER),
                Message(content = "Assistant response $i", role = MessageRole.ASSISTANT)
            )
        }
        
        val context = ChatContext(
            project = mockProject,
            history = longHistory
        )
        
        val request = AgentRequest(
            request = "Summarize",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        val mockResponse = LLMResponse.success(
            content = "Summary of 20 exchanges between user and assistant.",
            tokenUsage = TokenUsage(inputTokens = 200, outputTokens = 50, totalTokens = 250)
        )
        
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse
        
        // Act
        val response = summarizerAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Should handle long history")
        
        // Verify that all messages were included in the request
        coVerify {
            mockProvider.sendRequest(
                systemPrompt = any(),
                userMessage = match { message ->
                    longHistory.all { msg -> message.contains(msg.content) }
                },
                conversationHistory = emptyList(),
                parameters = any()
            )
        }
    }
    
    @Test
    fun `test summarization uses low temperature`() = runBlocking {
        // Arrange
        val context = ChatContext(
            project = mockProject,
            history = listOf(Message(content = "Test", role = MessageRole.USER))
        )
        
        val request = AgentRequest(
            request = "Summarize",
            context = context,
            parameters = LLMParameters(temperature = 0.9) // High temperature in request
        )
        
        val mockResponse = LLMResponse.success(
            content = "Summary",
            tokenUsage = TokenUsage.EMPTY
        )
        
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse
        
        // Act
        summarizerAgent.ask(request)
        
        // Assert - should override with low temperature
        coVerify {
            mockProvider.sendRequest(
                systemPrompt = any(),
                userMessage = any(),
                conversationHistory = any(),
                parameters = match { it.temperature == 0.3 }
            )
        }
    }
    
    @Test
    fun `test update settings does nothing`() {
        // SummarizerAgent doesn't use settings
        val settings = AgentSettings(maxContextTokens = 5000)
        
        // Should not throw
        summarizerAgent.updateSettings(settings)
    }
    
    @Test
    fun `test dispose does nothing`() {
        // Should not throw
        summarizerAgent.dispose()
    }
    
    @Test
    fun `test summarization with system messages`() = runBlocking {
        // Arrange
        val history = listOf(
            Message(content = "System initialized", role = MessageRole.SYSTEM),
            Message(content = "Hello", role = MessageRole.USER),
            Message(content = "Hi", role = MessageRole.ASSISTANT)
        )
        
        val context = ChatContext(
            project = mockProject,
            history = history
        )
        
        val request = AgentRequest(
            request = "Summarize",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        val mockResponse = LLMResponse.success(
            content = "Summary including system message",
            tokenUsage = TokenUsage.EMPTY
        )
        
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse
        
        // Act
        val response = summarizerAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Should handle system messages")
        
        coVerify {
            mockProvider.sendRequest(
                systemPrompt = any(),
                userMessage = match { it.contains("Система:") },
                conversationHistory = emptyList(),
                parameters = any()
            )
        }
    }
}
