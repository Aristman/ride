package ru.marslab.ide.ride.agent.impl

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.TokenCounter
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTProvider
import ru.marslab.ide.ride.model.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты для управления контекстом и сжатия в ChatAgent
 */
class ChatAgentContextManagementTest {
    
    private lateinit var mockProvider: LLMProvider
    private lateinit var mockTokenCounter: TokenCounter
    private lateinit var mockProject: com.intellij.openapi.project.Project
    private lateinit var chatAgent: ChatAgent
    
    @Before
    fun setup() {
        mockProvider = mockk<YandexGPTProvider>(relaxed = true)
        mockTokenCounter = mockk()
        mockProject = mockk(relaxed = true)
        
        every { (mockProvider as YandexGPTProvider).getTokenCounter() } returns mockTokenCounter
        every { mockProvider.isAvailable() } returns true
        every { mockProvider.getProviderName() } returns "TestProvider"
        
        chatAgent = ChatAgent(mockProvider)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `test context within limit - no compression`() = runBlocking {
        // Arrange
        val history = listOf(
            Message(content = "Hello", role = MessageRole.USER),
            Message(content = "Hi", role = MessageRole.ASSISTANT)
        )
        
        val context = ChatContext(
            project = mockProject,
            history = history
        )
        
        val request = AgentRequest(
            request = "How are you?",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        // Mock token counting - within limit
        every { mockTokenCounter.countRequestTokens(any(), any(), any()) } returns 1000
        
        val mockResponse = LLMResponse.success(
            content = "I'm fine, thanks!",
            tokenUsage = TokenUsage(inputTokens = 1000, outputTokens = 50, totalTokens = 1050)
        )
        
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse
        
        // Act
        val response = chatAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Request should succeed")
        
        // Verify no summarization occurred (history sent as-is)
        coVerify {
            mockProvider.sendRequest(
                systemPrompt = any(),
                userMessage = "How are you?",
                conversationHistory = match { it.size == 2 }, // Original history
                parameters = any()
            )
        }
        
        // No system message about compression
        val systemMessage = response.metadata["systemMessage"]
        assertEquals(null, systemMessage, "Should not have system message when within limit")
    }
    
    @Test
    fun `test context exceeds limit - auto summarization enabled`() = runBlocking {
        // Arrange
        val longHistory = (1..10).flatMap { i ->
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
            request = "New question",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        // Update settings to enable auto summarization
        chatAgent.updateSettings(AgentSettings(
            maxContextTokens = 1000,
            enableAutoSummarization = true
        ))
        
        // Mock token counting - exceeds limit
        every { mockTokenCounter.countRequestTokens(any(), any(), any()) } returns 5000
        
        // Mock summarization response
        val summaryResponse = LLMResponse.success(
            content = "Summary of previous conversation",
            tokenUsage = TokenUsage(inputTokens = 100, outputTokens = 30, totalTokens = 130)
        )
        
        // Mock final response
        val finalResponse = LLMResponse.success(
            content = "Answer to new question",
            tokenUsage = TokenUsage(inputTokens = 200, outputTokens = 50, totalTokens = 250)
        )
        
        coEvery {
            mockProvider.sendRequest(
                systemPrompt = match { it.contains("сжатия") },
                userMessage = any(),
                conversationHistory = emptyList(),
                parameters = match { it.temperature == 0.3 }
            )
        } returns summaryResponse
        
        coEvery {
            mockProvider.sendRequest(
                systemPrompt = not(match { it.contains("сжатия") }),
                userMessage = "New question",
                conversationHistory = match { it.isNotEmpty() },
                parameters = any()
            )
        } returns finalResponse
        
        // Act
        val response = chatAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Request should succeed after summarization")
        
        // Verify summarization was called
        coVerify {
            mockProvider.sendRequest(
                systemPrompt = match { it.contains("сжатия") },
                userMessage = any(),
                conversationHistory = emptyList(),
                parameters = any()
            )
        }
        
        // Should have system message about compression
        val systemMessage = response.metadata["systemMessage"] as? String
        assertNotNull(systemMessage, "Should have system message")
        assertTrue(systemMessage.contains("сжата"), "Message should mention compression")
    }
    
    @Test
    fun `test context exceeds limit - auto summarization disabled - truncation`() = runBlocking {
        // Arrange
        val longHistory = (1..10).flatMap { i ->
            listOf(
                Message(content = "Message $i", role = MessageRole.USER),
                Message(content = "Response $i", role = MessageRole.ASSISTANT)
            )
        }
        
        val context = ChatContext(
            project = mockProject,
            history = longHistory
        )
        
        val request = AgentRequest(
            request = "Question",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        // Update settings to disable auto summarization
        chatAgent.updateSettings(AgentSettings(
            maxContextTokens = 1000,
            enableAutoSummarization = false
        ))
        
        // Mock token counting - exceeds limit
        every { mockTokenCounter.countRequestTokens(any(), any(), any()) } returns 5000
        every { mockTokenCounter.countTokens(any<String>()) } returns 50
        
        val mockResponse = LLMResponse.success(
            content = "Answer",
            tokenUsage = TokenUsage(inputTokens = 100, outputTokens = 50, totalTokens = 150)
        )
        
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse
        
        // Act
        val response = chatAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Request should succeed with truncation")
        
        // Should have system message about truncation
        val systemMessage = response.metadata["systemMessage"] as? String
        assertNotNull(systemMessage, "Should have system message about truncation")
        assertTrue(systemMessage.contains("обрезана"), "Message should mention truncation")
        
        // Verify no summarization was called
        coVerify(exactly = 0) {
            mockProvider.sendRequest(
                systemPrompt = match { it.contains("сжатия") },
                userMessage = any(),
                conversationHistory = any(),
                parameters = any()
            )
        }
    }
    
    @Test
    fun `test summarization fails - fallback to truncation`() = runBlocking {
        // Arrange
        val longHistory = (1..5).flatMap { i ->
            listOf(
                Message(content = "User $i", role = MessageRole.USER),
                Message(content = "Assistant $i", role = MessageRole.ASSISTANT)
            )
        }
        
        val context = ChatContext(
            project = mockProject,
            history = longHistory
        )
        
        val request = AgentRequest(
            request = "Question",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        chatAgent.updateSettings(AgentSettings(
            maxContextTokens = 500,
            enableAutoSummarization = true
        ))
        
        // Mock token counting - exceeds limit
        every { mockTokenCounter.countRequestTokens(any(), any(), any()) } returns 2000
        every { mockTokenCounter.countTokens(any<String>()) } returns 50
        
        // Mock summarization failure
        val summaryError = LLMResponse.error("Summarization failed")
        
        // Mock final response
        val finalResponse = LLMResponse.success(
            content = "Answer",
            tokenUsage = TokenUsage(inputTokens = 100, outputTokens = 50, totalTokens = 150)
        )
        
        coEvery {
            mockProvider.sendRequest(
                systemPrompt = match { it.contains("сжатия") },
                userMessage = any(),
                conversationHistory = emptyList(),
                parameters = any()
            )
        } returns summaryError
        
        coEvery {
            mockProvider.sendRequest(
                systemPrompt = not(match { it.contains("сжатия") }),
                userMessage = any(),
                conversationHistory = any(),
                parameters = any()
            )
        } returns finalResponse
        
        // Act
        val response = chatAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Request should succeed with fallback")
        
        // Should have system message about failed summarization
        val systemMessage = response.metadata["systemMessage"] as? String
        assertNotNull(systemMessage, "Should have system message")
        assertTrue(
            systemMessage.contains("обрезана") || systemMessage.contains("сжать"),
            "Message should mention fallback"
        )
    }
    
    @Test
    fun `test empty history - no compression needed`() = runBlocking {
        // Arrange
        val context = ChatContext(
            project = mockProject,
            history = emptyList()
        )
        
        val request = AgentRequest(
            request = "First question",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        every { mockTokenCounter.countRequestTokens(any(), any(), any()) } returns 100
        
        val mockResponse = LLMResponse.success(
            content = "First answer",
            tokenUsage = TokenUsage(inputTokens = 100, outputTokens = 50, totalTokens = 150)
        )
        
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse
        
        // Act
        val response = chatAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Request should succeed")
        
        coVerify {
            mockProvider.sendRequest(
                systemPrompt = any(),
                userMessage = "First question",
                conversationHistory = emptyList(),
                parameters = any()
            )
        }
        
        assertEquals(null, response.metadata["systemMessage"], "No system message needed")
    }
    
    @Test
    fun `test token usage in response metadata`() = runBlocking {
        // Arrange
        val context = ChatContext(
            project = mockProject,
            history = emptyList()
        )
        
        val request = AgentRequest(
            request = "Test",
            context = context,
            parameters = LLMParameters.DEFAULT
        )
        
        every { mockTokenCounter.countRequestTokens(any(), any(), any()) } returns 50
        
        val tokenUsage = TokenUsage(inputTokens = 50, outputTokens = 30, totalTokens = 80)
        val mockResponse = LLMResponse.success(
            content = "Response",
            tokenUsage = tokenUsage
        )
        
        coEvery {
            mockProvider.sendRequest(any(), any(), any(), any())
        } returns mockResponse
        
        // Act
        val response = chatAgent.ask(request)
        
        // Assert
        assertTrue(response.success, "Request should succeed")
        
        val metadataTokenUsage = response.metadata["tokenUsage"] as? TokenUsage
        assertNotNull(metadataTokenUsage, "Token usage should be in metadata")
        assertEquals(50, metadataTokenUsage.inputTokens, "Input tokens should match")
        assertEquals(30, metadataTokenUsage.outputTokens, "Output tokens should match")
        assertEquals(80, metadataTokenUsage.totalTokens, "Total tokens should match")
    }
}
