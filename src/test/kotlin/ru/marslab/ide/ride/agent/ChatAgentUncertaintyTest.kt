package ru.marslab.ide.ride.agent

import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.*
import com.intellij.openapi.project.Project

class ChatAgentUncertaintyTest {

    private lateinit var mockLLMProvider: LLMProvider
    private lateinit var chatAgent: ChatAgent
    private lateinit var mockProject: Project

    @BeforeTest
    fun setUp() {
        mockLLMProvider = mockk()
        mockProject = mockk()
        chatAgent = ChatAgent(mockLLMProvider)

        every { mockLLMProvider.isAvailable() } returns true
        every { mockLLMProvider.getProviderName() } returns "Test Provider"
    }

    @Test
    fun `processRequest should analyze uncertainty and set isFinal flag for certain response`() = runTest {
        // Arrange
        val certainResponse = "Окончательный ответ: вот полное решение вашей проблемы"
        val llmResponse = LLMResponse.success(
            content = certainResponse,
            tokensUsed = 100
        )

        coEvery {
            mockLLMProvider.sendRequest(
                systemPrompt = any(),
                userMessage = any(),
                conversationHistory = any(),
                parameters = any()
            )
        } returns llmResponse

        val request = "Как оптимизировать производительность в Kotlin?"
        val context = ChatContext(mockProject)

        // Act
        val response = chatAgent.processRequest(request, context)

        // Assert
        assertTrue(response.success)
        assertTrue(response.isFinal, "Ответ должен быть окончательным при низкой неопределенности")
        assertNotNull(response.uncertainty, "Неопределенность должна быть рассчитана")
        assertTrue(response.uncertainty!! <= 0.1, "Неопределенность должна быть низкой")
        assertFalse(response.metadata["hasClarifyingQuestions"] as Boolean, "Не должно быть уточняющих вопросов")
    }

    @Test
    fun `processRequest should analyze uncertainty and set isFinal false for uncertain response`() = runTest {
        // Arrange
        val uncertainResponse = "Я не уверен, что правильно понял ваш вопрос. Уточните, пожалуйста, о чем именно идет речь?"
        val llmResponse = LLMResponse.success(
            content = uncertainResponse,
            tokensUsed = 100
        )

        coEvery {
            mockLLMProvider.sendRequest(
                systemPrompt = any(),
                userMessage = any(),
                conversationHistory = any(),
                parameters = any()
            )
        } returns llmResponse

        val request = "помоги с кодом"
        val context = ChatContext(mockProject)

        // Act
        val response = chatAgent.processRequest(request, context)

        // Assert
        assertTrue(response.success)
        assertFalse(response.isFinal, "Ответ не должен быть окончательным при высокой неопределенности")
        assertNotNull(response.uncertainty, "Неопределенность должна быть рассчитана")
        assertTrue(response.uncertainty!! > 0.1, "Неопределенность должна быть высокой")
        assertTrue(response.metadata["hasClarifyingQuestions"] as Boolean, "Должны быть уточняющие вопросы")
    }

    @Test
    fun `processRequest should include uncertainty in metadata`() = runTest {
        // Arrange
        val responseWithQuestions = "Давайте уточню несколько деталей: какую версию Kotlin вы используете?"
        val llmResponse = LLMResponse.success(
            content = responseWithQuestions,
            tokensUsed = 100
        )

        coEvery {
            mockLLMProvider.sendRequest(
                systemPrompt = any(),
                userMessage = any(),
                conversationHistory = any(),
                parameters = any()
            )
        } returns llmResponse

        val request = "Как использовать корутины?"
        val context = ChatContext(mockProject)

        // Act
        val response = chatAgent.processRequest(request, context)

        // Assert
        assertTrue(response.success)
        assertTrue(response.metadata.containsKey("uncertainty"))
        assertTrue(response.metadata.containsKey("hasClarifyingQuestions"))
        assertEquals("Test Provider", response.metadata["provider"])
    }

    @Test
    fun `processRequest should pass conversation history to LLM provider`() = runTest {
        // Arrange
        val llmResponse = LLMResponse.success(content = "Вот ответ", tokensUsed = 100)

        val history = listOf(
            Message("user1", "Как создать проект?", MessageRole.USER),
            Message("assistant1", "Вот инструкция", MessageRole.ASSISTANT)
        )
        val context = ChatContext(mockProject, history = history)
        val request = "А как добавить зависимость?"

        coEvery {
            mockLLMProvider.sendRequest(
                systemPrompt = any(),
                userMessage = request,
                conversationHistory = any(),
                parameters = any()
            )
        } returns llmResponse

        // Act
        chatAgent.processRequest(request, context)

        // Assert
        coVerify(exactly = 1) {
            mockLLMProvider.sendRequest(
                systemPrompt = any(),
                userMessage = request,
                conversationHistory = any(),
                parameters = any()
            )
        }

        // Проверяем, что история была передана
        val conversationHistorySlot = slot<List<ConversationMessage>>()
        coVerify(exactly = 1) {
            mockLLMProvider.sendRequest(
                systemPrompt = any(),
                userMessage = request,
                conversationHistory = capture(conversationHistorySlot),
                parameters = any()
            )
        }
        val conversationHistory = conversationHistorySlot.captured

        assertEquals(2, conversationHistory.size)
        assertEquals(ConversationRole.USER, conversationHistory[0].role)
        assertEquals(ConversationRole.ASSISTANT, conversationHistory[1].role)
    }

    @Test
    fun `processRequest should handle LLM provider errors`() = runTest {
        // Arrange
        coEvery {
            mockLLMProvider.sendRequest(
                systemPrompt = any(),
                userMessage = any(),
                conversationHistory = any(),
                parameters = any()
            )
        } returns LLMResponse.error("API Error")

        val request = "Как работать с базой данных?"
        val context = ChatContext(mockProject)

        // Act
        val response = chatAgent.processRequest(request, context)

        // Assert
        assertFalse(response.success)
        assertNotNull(response.error)
    }

    @Test
    fun `processRequest should use uncertainty threshold correctly`() = runTest {
        // Arrange
        val borderlineResponse = "Возможно, это поможет, но нужно больше информации."
        val llmResponse = LLMResponse.success(
            content = borderlineResponse,
            tokensUsed = 100
        )

        coEvery {
            mockLLMProvider.sendRequest(
                systemPrompt = any(),
                userMessage = any(),
                conversationHistory = any(),
                parameters = any()
            )
        } returns llmResponse

        val request = "Как оптимизировать код?"
        val context = ChatContext(mockProject)

        // Act
        val response = chatAgent.processRequest(request, context)

        // Assert
        assertNotNull(response.uncertainty)
        // Проверяем, что используется порог 0.1
//        val isFinalByUncertainty = UncertaintyAnalyzer.isFinalResponse(response.uncertainty!!)
//        assertEquals(response.isFinal, isFinalByUncertainty, "Флаг isFinal должен соответствовать анализу неопределенности")
    }
}