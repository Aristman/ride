package ru.marslab.ide.ride.agent

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatAgentTest {
    
    @Test
    fun `test successful request processing`() = runTest {
        // Arrange
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.isAvailable() } returns true
        every { mockProvider.getProviderName() } returns "Test Provider"
        coEvery { 
            mockProvider.sendRequest(any(), any()) 
        } returns LLMResponse.success("Test response", tokensUsed = 100)
        
        val agent = ChatAgent(mockProvider)
        val context = mockk<ChatContext>(relaxed = true)
        every { context.getRecentHistory(any()) } returns emptyList()
        every { context.hasCurrentFile() } returns false
        every { context.hasSelectedText() } returns false
        
        // Act
        val response = agent.processRequest("Test request", context)
        
        // Assert
        assertTrue(response.success)
        assertEquals("Test response", response.content)
        assertEquals(100, response.metadata["tokensUsed"])
        assertEquals("Test Provider", response.metadata["provider"])
    }
    
    @Test
    fun `test request when provider is not available`() = runTest {
        // Arrange
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.isAvailable() } returns false
        
        val agent = ChatAgent(mockProvider)
        val context = mockk<ChatContext>(relaxed = true)
        
        // Act
        val response = agent.processRequest("Test request", context)
        
        // Assert
        assertFalse(response.success)
        assertTrue(response.error?.contains("недоступен") == true)
    }
    
    @Test
    fun `test request when provider returns error`() = runTest {
        // Arrange
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.isAvailable() } returns true
        coEvery { 
            mockProvider.sendRequest(any(), any()) 
        } returns LLMResponse.error("API Error")
        
        val agent = ChatAgent(mockProvider)
        val context = mockk<ChatContext>(relaxed = true)
        every { context.getRecentHistory(any()) } returns emptyList()
        every { context.hasCurrentFile() } returns false
        every { context.hasSelectedText() } returns false
        
        // Act
        val response = agent.processRequest("Test request", context)
        
        // Assert
        assertFalse(response.success)
        assertEquals("API Error", response.error)
    }
    
    @Test
    fun `test agent name and description`() {
        val mockProvider = mockk<LLMProvider>()
        every { mockProvider.getProviderName() } returns "Test Provider"
        
        val agent = ChatAgent(mockProvider)
        
        assertEquals("Chat Agent", agent.getName())
        assertTrue(agent.getDescription().contains("Test Provider"))
    }
}
