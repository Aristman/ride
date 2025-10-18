package ru.marslab.ide.ride.agent.impl

import io.mockk.*
import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.model.chat.MessageRole
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.llm.TokenUsage
import ru.marslab.ide.ride.model.chat.ConversationRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты для SummarizerAgent
 */
class SummarizerAgentTest {

    private val mockProvider = mockk<LLMProvider>()
    private val mockProject = mockk<com.intellij.openapi.project.Project>(relaxed = true)
    private val summarizerAgent = SummarizerAgent(mockProvider)

    @Test
    fun `test summarizer agent capabilities`() {
        val capabilities = summarizerAgent.capabilities

        assertFalse(capabilities.stateful, "Summarizer should be stateless")
        assertFalse(capabilities.streaming, "Summarizer should not support streaming")
        assertTrue(capabilities.reasoning, "Summarizer should support reasoning")
        assertEquals("Агент для сжатия истории диалога", capabilities.systemPrompt)
        assertTrue(capabilities.responseRules.isNotEmpty())
    }

    @Test
    fun `test successful summarization`() = runTest {
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
            parameters = LLMParameters.DEFAULT
        )

        val mockResponse = LLMResponse.success(
            "User asked for help with Kotlin. Assistant agreed to help.",
            TokenUsage(50, 20, 70)
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
    fun `test summarization with empty history`() = runTest {
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
            "История диалога пуста.",
            TokenUsage(10, 5, 15)
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
    fun `test summarization failure`() = runTest {
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
        assertNotNull(response.error, "Should have error message")
    }

    @Test
    fun `test summarization with long history`() = runTest {
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
            "Summary of 20 exchanges between user and assistant.",
            TokenUsage(200, 50, 250)
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
    fun `test summarization uses low temperature`() = runTest {
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
            "Summary",
            TokenUsage(10, 5, 15)
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
        val settings = ru.marslab.ide.ride.model.agent.AgentSettings(maxContextTokens = 5000)

        // Should not throw
        summarizerAgent.updateSettings(settings)
    }

    @Test
    fun `test dispose does nothing`() {
        // Should not throw
        summarizerAgent.dispose()
    }

    @Test
    fun `test summarization with system messages`() = runTest {
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
            "Summary including system message",
            TokenUsage(15, 10, 25)
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

    @Test
    fun `test createSummaryMessage`() {
        val summaryContent = "User asked about Kotlin. Assistant offered help."

        val summaryMessage = summarizerAgent.createSummaryMessage(summaryContent)

        assertEquals(ConversationRole.SYSTEM, summaryMessage.role)
        assertTrue(summaryMessage.content.contains("[РЕЗЮМЕ ПРЕДЫДУЩЕГО ДИАЛОГА]"))
        assertTrue(summaryMessage.content.contains(summaryContent))
    }

    @Test
    fun `test summarization with provider unavailable`() = runTest {
        // Arrange
        every { mockProvider.isAvailable() } returns false

        val context = ChatContext(
            project = mockProject,
            history = listOf(Message(content = "Test", role = MessageRole.USER))
        )

        val request = AgentRequest(
            request = "Summarize",
            context = context,
            parameters = LLMParameters.DEFAULT
        )

        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns LLMResponse.error("Provider unavailable")

        // Act
        val response = summarizerAgent.ask(request)

        // Assert
        assertFalse(response.success, "Should fail when provider is unavailable")
    }

    @Test
    fun `test summarization response structure`() = runTest {
        // Arrange
        val context = ChatContext(
            project = mockProject,
            history = listOf(Message(content = "Test message", role = MessageRole.USER))
        )

        val request = AgentRequest(
            request = "Summarize",
            context = context,
            parameters = LLMParameters.DEFAULT
        )

        val mockResponse = LLMResponse.success(
            "Test summary",
            TokenUsage(20, 10, 30)
        )

        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse

        // Act
        val response = summarizerAgent.ask(request)

        // Assert
        assertTrue(response.success)
        assertTrue(response.isFinal)
        assertEquals("Test summary", response.content)
        assertNotNull(response.metadata)
    }
}