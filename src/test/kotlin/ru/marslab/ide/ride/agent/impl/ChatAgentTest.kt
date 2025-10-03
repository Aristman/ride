package ru.marslab.ide.ride.agent.impl

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.*

class ChatAgentTest {

    private fun createMockProvider(response: String = "Test response"): LLMProvider {
        return mockk<LLMProvider> {
            every { isAvailable() } returns true
            every { getProviderName() } returns "MockProvider"
            coEvery { sendRequest(any(), any(), any(), any()) } returns LLMResponse.success(response, tokensUsed = 10)
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
    fun `processes request without format`() = runTest {
        val provider = createMockProvider("Plain text response")
        val agent = ChatAgent(provider)
        val context = createMockContext()

        val response = agent.processRequest("Test request", context)

        assertTrue(response.success)
        assertEquals("Plain text response", response.content)
        assertEquals(null, response.parsedContent)
    }

    @Test
    fun `processes request with JSON format`() = runTest {
        val jsonResponse = """{"answer": "Kotlin is great", "confidence": 0.95}"""
        val provider = createMockProvider(jsonResponse)
        val agent = ChatAgent(provider)
        val context = createMockContext()

        val schema = ResponseSchema.json("""{"answer": "string", "confidence": 0.0}""")
        agent.setResponseFormat(ResponseFormat.JSON, schema)

        val response = agent.processRequest("What is Kotlin?", context)

        assertTrue(response.success)
        assertNotNull(response.parsedContent)
        assertIs<ParsedResponse.JsonResponse>(response.parsedContent)
    }

    @Test
    fun `processes request with XML format`() = runTest {
        val xmlResponse = "<response><answer>Kotlin is great</answer></response>"
        val provider = createMockProvider(xmlResponse)
        val agent = ChatAgent(provider)
        val context = createMockContext()

        val schema = ResponseSchema.xml("<response><answer>string</answer></response>")
        agent.setResponseFormat(ResponseFormat.XML, schema)

        val response = agent.processRequest("What is Kotlin?", context)

        assertTrue(response.success)
        assertNotNull(response.parsedContent)
        assertIs<ParsedResponse.XmlResponse>(response.parsedContent)
    }

    @Test
    fun `returns error when JSON parsing fails`() = runTest {
        val invalidJson = "{invalid json"
        val provider = createMockProvider(invalidJson)
        val agent = ChatAgent(provider)
        val context = createMockContext()

        val schema = ResponseSchema.json("""{"answer": "string"}""")
        agent.setResponseFormat(ResponseFormat.JSON, schema)

        val response = agent.processRequest("Test", context)

        assertTrue(!response.success)
        assertNotNull(response.error)
        assertTrue(response.error!!.contains("парсинга"))
    }

    @Test
    fun `returns error when validation fails`() = runTest {
        val jsonResponse = """{"wrong_field": "value"}"""
        val provider = createMockProvider(jsonResponse)
        val agent = ChatAgent(provider)
        val context = createMockContext()

        val schema = ResponseSchema.json("""{"answer": "string", "confidence": 0.0}""")
        agent.setResponseFormat(ResponseFormat.JSON, schema)

        val response = agent.processRequest("Test", context)

        assertTrue(!response.success)
        assertNotNull(response.error)
        assertTrue(response.error!!.contains("схеме"))
    }

    @Test
    fun `changes provider at runtime`() = runTest {
        val provider1 = createMockProvider("Response 1")
        val provider2 = createMockProvider("Response 2")
        val agent = ChatAgent(provider1)
        val context = createMockContext()

        val response1 = agent.processRequest("Test", context)
        assertEquals("Response 1", response1.content)

        agent.setLLMProvider(provider2)
        val response2 = agent.processRequest("Test", context)
        assertEquals("Response 2", response2.content)
    }

    @Test
    fun `clears response format`() = runTest {
        val provider = createMockProvider("""{"answer": "test"}""")
        val agent = ChatAgent(provider)
        val context = createMockContext()

        val schema = ResponseSchema.json("""{"answer": "string"}""")
        agent.setResponseFormat(ResponseFormat.JSON, schema)
        assertEquals(ResponseFormat.JSON, agent.getResponseFormat())

        agent.clearResponseFormat()
        assertEquals(null, agent.getResponseFormat())

        val response = agent.processRequest("Test", context)
        assertEquals(null, response.parsedContent)
    }

    @Test
    fun `passes only assistant history to provider`() = runTest {
        val provider = mockk<LLMProvider> {
            every { isAvailable() } returns true
            every { getProviderName() } returns "MockProvider"
        }

        val assistantHistorySlot = slot<List<String>>()

        coEvery {
            provider.sendRequest(any(), any(), capture(assistantHistorySlot), any())
        } returns LLMResponse.success("History test", tokensUsed = 5)

        val agent = ChatAgent(provider)

        val history = listOf(
            Message(content = "Hello", role = MessageRole.USER),
            Message(content = "Answer 1", role = MessageRole.ASSISTANT),
            Message(content = "Instruction", role = MessageRole.SYSTEM),
            Message(content = "Answer 2", role = MessageRole.ASSISTANT)
        )

        val context = mockk<ChatContext> {
            every { getRecentHistory(any()) } returns history
        }

        agent.processRequest("Test request", context)

        assertTrue(assistantHistorySlot.isCaptured)
        assertEquals(listOf("Answer 1", "Answer 2"), assistantHistorySlot.captured)
    }

    @Test
    fun `extends system prompt with schema instructions via PromptFormatter`() = runTest {
        val provider = mockk<LLMProvider> {
            every { isAvailable() } returns true
            every { getProviderName() } returns "MockProvider"
        }

        val systemPromptSlot = slot<String>()

        coEvery {
            provider.sendRequest(capture(systemPromptSlot), any(), any(), any())
        } returns LLMResponse.success("Schema test", tokensUsed = 5)

        val agent = ChatAgent(provider)
        val context = createMockContext()

        val schema = ResponseSchema.json(
            """{"answer": "string"}""",
            "Предоставь ответ в JSON с ключом answer"
        )
        agent.setResponseFormat(ResponseFormat.JSON, schema)

        agent.processRequest("What is Kotlin?", context)

        assertTrue(systemPromptSlot.isCaptured)
        val capturedPrompt = systemPromptSlot.captured
        assertTrue(capturedPrompt.contains("Ты - AI-ассистент для разработчиков"))
        assertTrue(capturedPrompt.contains("ВАЖНО: Ответ должен быть в формате JSON."))
        assertTrue(capturedPrompt.contains("\"answer\""))

        coVerify {
            provider.sendRequest(systemPromptSlot.captured, any(), any(), any())
        }
    }
}
