package ru.marslab.ide.ride.agent.integration

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.*

/**
 * Интеграционные тесты для полного цикла работы с форматированием ответов
 */
class ResponseFormattingIntegrationTest {

    private fun createMockProvider(response: String): LLMProvider {
        return mockk<LLMProvider> {
            every { isAvailable() } returns true
            every { getProviderName() } returns "MockProvider"
            coEvery { sendRequest(any(), any()) } returns LLMResponse.success(response, tokensUsed = 10)
        }
    }

    private fun createMockContext(): ChatContext {
        return mockk<ChatContext> {
            every { getRecentHistory(any()) } returns emptyList()
            every { hasSelectedText() } returns false
            every { hasCurrentFile() } returns false
        }
    }

    @Test
    fun `full cycle JSON - request to parsed response`() = runTest {
        // Arrange
        val jsonResponse = """
        {
            "answer": "Kotlin is a modern programming language",
            "confidence": 0.95,
            "sources": ["official docs", "community"]
        }
        """.trimIndent()
        
        val provider = createMockProvider(jsonResponse)
        val schema = ResponseSchema.json("""
        {
            "answer": "string",
            "confidence": 0.0,
            "sources": ["string"]
        }
        """.trimIndent(), "Provide structured answer with confidence")
        
        val agent = AgentFactory.createChatAgent(provider, ResponseFormat.JSON, schema)
        val context = createMockContext()

        // Act
        val response = agent.processRequest("What is Kotlin?", context)

        // Assert
        assertTrue(response.success)
        assertNotNull(response.parsedContent)
        assertIs<ParsedResponse.JsonResponse>(response.parsedContent)
        
        val jsonParsed = response.parsedContent as ParsedResponse.JsonResponse
        assertTrue(jsonParsed.jsonElement.toString().contains("Kotlin"))
        assertEquals(ResponseFormat.JSON, jsonParsed.format)
    }

    @Test
    fun `full cycle XML - request to parsed response`() = runTest {
        // Arrange
        val xmlResponse = """
        <response>
            <answer>Kotlin is a modern programming language</answer>
            <confidence>0.95</confidence>
        </response>
        """.trimIndent()
        
        val provider = createMockProvider(xmlResponse)
        val schema = ResponseSchema.xml("""
        <response>
            <answer>string</answer>
            <confidence>number</confidence>
        </response>
        """.trimIndent())
        
        val agent = AgentFactory.createChatAgent(provider, ResponseFormat.XML, schema)
        val context = createMockContext()

        // Act
        val response = agent.processRequest("What is Kotlin?", context)

        // Assert
        assertTrue(response.success)
        assertNotNull(response.parsedContent)
        assertIs<ParsedResponse.XmlResponse>(response.parsedContent)
        
        val xmlParsed = response.parsedContent as ParsedResponse.XmlResponse
        assertTrue(xmlParsed.xmlContent.contains("Kotlin"))
        assertEquals(ResponseFormat.XML, xmlParsed.format)
    }

    @Test
    fun `full cycle TEXT - request to text response`() = runTest {
        // Arrange
        val textResponse = "Kotlin is a modern, concise programming language that runs on JVM."
        
        val provider = createMockProvider(textResponse)
        val agent = AgentFactory.createChatAgent(provider)
        val context = createMockContext()

        // Act
        val response = agent.processRequest("What is Kotlin?", context)

        // Assert
        assertTrue(response.success)
        assertEquals(textResponse, response.content)
        assertEquals(null, response.parsedContent) // No parsing for TEXT
    }

    @Test
    fun `changing format at runtime works correctly`() = runTest {
        // Arrange
        val jsonResponse = """{"answer": "JSON response"}"""
        val xmlResponse = "<response><answer>XML response</answer></response>"
        val textResponse = "Text response"
        
        val provider = mockk<LLMProvider> {
            every { isAvailable() } returns true
            every { getProviderName() } returns "MockProvider"
        }
        
        val agent = AgentFactory.createChatAgent(provider)
        val context = createMockContext()

        // Act & Assert - JSON
        coEvery { provider.sendRequest(any(), any()) } returns LLMResponse.success(jsonResponse)
        agent.setResponseFormat(ResponseFormat.JSON, ResponseSchema.json("""{"answer": "string"}"""))
        
        val response1 = agent.processRequest("Test", context)
        assertTrue(response1.success)
        assertIs<ParsedResponse.JsonResponse>(response1.parsedContent)

        // Act & Assert - XML
        coEvery { provider.sendRequest(any(), any()) } returns LLMResponse.success(xmlResponse)
        agent.setResponseFormat(ResponseFormat.XML, ResponseSchema.xml("<response><answer>string</answer></response>"))
        
        val response2 = agent.processRequest("Test", context)
        assertTrue(response2.success)
        assertIs<ParsedResponse.XmlResponse>(response2.parsedContent)

        // Act & Assert - TEXT
        coEvery { provider.sendRequest(any(), any()) } returns LLMResponse.success(textResponse)
        agent.clearResponseFormat()
        
        val response3 = agent.processRequest("Test", context)
        assertTrue(response3.success)
        assertEquals(null, response3.parsedContent)
    }

    @Test
    fun `changing provider at runtime works correctly`() = runTest {
        // Arrange
        val provider1 = createMockProvider("Response from provider 1")
        val provider2 = createMockProvider("Response from provider 2")
        
        val agent = AgentFactory.createChatAgent(provider1)
        val context = createMockContext()

        // Act & Assert - Provider 1
        val response1 = agent.processRequest("Test", context)
        assertTrue(response1.success)
        assertEquals("Response from provider 1", response1.content)
        assertEquals("MockProvider", response1.metadata["provider"])

        // Act & Assert - Provider 2
        agent.setLLMProvider(provider2)
        val response2 = agent.processRequest("Test", context)
        assertTrue(response2.success)
        assertEquals("Response from provider 2", response2.content)
    }
}
