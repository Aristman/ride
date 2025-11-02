package ru.marslab.ide.ride.agent.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator
import com.intellij.openapi.project.Project

/**
 * Регрессионные тесты для EnhancedChatAgent
 */
class EnhancedChatAgentRegressionTest {

    @Test
    fun `simple query should use base chat agent`() = runBlocking {
        // Создаем моки
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответ от базового агента
        val expectedResponse = AgentResponse.success(
            content = "Ответ на простой вопрос",
            isFinal = true,
            uncertainty = 0.0
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(expectedResponse)

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
        verify(mockBaseAgent, times(1)).ask(any())
        verify(mockOrchestrator, never()).processEnhanced(any())
    }

    @Test
    fun `complex query should use orchestrator`() = runBlocking {
        // Создаем моки
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответ от оркестратора
        val expectedResponse = AgentResponse.success(
            content = "Результат анализа кода",
            isFinal = true,
            uncertainty = 0.1
        )
        `when`(mockOrchestrator.processEnhanced(any(), any())).thenReturn(expectedResponse)

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
        verify(mockOrchestrator, times(1)).processEnhanced(any(), any())
        verify(mockBaseAgent, never()).ask(any())
    }

    @Test
    fun `plan resume should use orchestrator`() = runBlocking {
        // Создаем моки
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответ от оркестратора
        val expectedResponse = AgentResponse.success(
            content = "План возобновлен",
            isFinal = true,
            uncertainty = 0.0
        )
        `when`(mockOrchestrator.resumePlanWithCallback(any(), any(), any())).thenReturn(expectedResponse)

        // Создаем EnhancedChatAgent
        val enhancedAgent = EnhancedChatAgent(mockBaseAgent, mockOrchestrator)

        // Создаем запрос с ID плана для возобновления
        val context = ChatContext(
            project = mockProject,
            history = emptyList(),
            additionalContext = mapOf("resume_plan_id" to "test-plan-id")
        )
        val request = AgentRequest(
            request = "продолжить выполнение",
            context = context,
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем, что вернулся правильный ответ
        assertTrue(response.success)
        assertEquals("План возобновлен", response.content)

        // Проверяем, что был вызван метод возобновления плана
        verify(mockOrchestrator, times(1)).resumePlanWithCallback(eq("test-plan-id"), eq("продолжить выполнение"), any())
        verify(mockBaseAgent, never()).ask(any())
    }

    @Test
    fun `RAG enriched query without complex keywords should use base agent`() = runBlocking {
        // Создаем моки
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответ от базового агента
        val expectedResponse = AgentResponse.success(
            content = "Ответ на запрос с RAG контекстом",
            isFinal = true,
            uncertainty = 0.0
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(expectedResponse)

        // Создаем EnhancedChatAgent
        val enhancedAgent = EnhancedChatAgent(mockBaseAgent, mockOrchestrator)

        // Выполняем RAG обогащенный запрос без сложных ключевых слов
        val ragEnrichedRequest = """
            === Retrieved Context ===
            Ниже приведены релевантные фрагменты из проекта, которые могут помочь ответить на ваш вопрос:

            1. Фрагмент из: src/main/kotlin/Test.kt:10-15 (сходство: 95%)
            ```
            fun testFunction() {
                println("Hello")
            }
            ```
            === End of Context ===

            На основе предоставленного выше контекста, пожалуйста, ответьте на следующий вопрос:

            **Вопрос:** что делает testFunction?
        """.trimIndent()

        val request = AgentRequest(
            request = ragEnrichedRequest,
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем, что базовый агент был вызван
        verify(mockBaseAgent, times(1)).ask(any())
        verify(mockOrchestrator, never()).processEnhanced(any())
    }
}