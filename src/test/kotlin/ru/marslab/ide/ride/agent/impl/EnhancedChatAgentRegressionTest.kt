package ru.marslab.ide.ride.agent.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.*
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.orchestrator.StandaloneA2AOrchestrator
import ru.marslab.ide.ride.orchestrator.StandaloneA2AOrchestrator.A2AStepResult
import com.intellij.openapi.project.Project

/**
 * Регрессионные тесты для EnhancedChatAgent
 */
class EnhancedChatAgentRegressionTest {

    @Test
    fun `simple query should use base chat agent`() = runBlocking {
        // Создаем моки
        val mockBaseAgent = mock<ChatAgent>()
        val mockOrchestrator = mock<StandaloneA2AOrchestrator>()
        val mockProject = mock<Project>()

        // Настраиваем ответ от базового агента
        val expectedResponse = AgentResponse.success(
            content = "Ответ на простой вопрос",
            isFinal = true,
            uncertainty = 0.0
        )
        whenever(mockBaseAgent.ask(any<AgentRequest>())).thenReturn(expectedResponse)

        // Создаем EnhancedChatAgent
        val enhancedAgent = EnhancedChatAgent(mockBaseAgent, mockOrchestrator)

        // Выполняем простой запрос
        val request = AgentRequest(
            request = "Какой сегодня день?",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем, что вернулся правильный ответ
        assertTrue(response.success)
        assertEquals("Ответ на простой вопрос", response.content)

        // Проверяем, что базовый агент был вызван, а оркестратор - нет
        verify(mockBaseAgent, times(1)).ask(any<AgentRequest>())
        verify(mockOrchestrator, never()).processRequest(any(), any())
    }

    @Test
    fun `complex query should use orchestrator`() = runBlocking {
        // Создаем моки
        val mockBaseAgent = mock<ChatAgent>()
        val mockOrchestrator = mock<StandaloneA2AOrchestrator>()
        val mockProject = mock<Project>()

        // Настраиваем ответ от оркестратора
        val expectedResponse = AgentResponse.success(
            content = "Результат анализа кода",
            isFinal = true,
            uncertainty = 0.1
        )
        whenever(
            mockOrchestrator.processRequest(
                any<AgentRequest>(),
                any<suspend (A2AStepResult) -> Unit>()
            )
        ).thenReturn(expectedResponse)

        // Создаем EnhancedChatAgent
        val enhancedAgent = EnhancedChatAgent(mockBaseAgent, mockOrchestrator)

        // Выполняем сложный запрос
        val request = AgentRequest(
            request = "проанализируй качество кода в проекте",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем, что вернулся правильный ответ
        assertTrue(response.success)
        assertEquals("Результат анализа кода", response.content)

        // Проверяем, что оркестратор был вызван, а базовый агент - нет
        verify(mockOrchestrator, times(1)).processRequest(any<AgentRequest>(), any())
        verify(mockBaseAgent, never()).ask(any<AgentRequest>())
    }
}
