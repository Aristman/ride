package ru.marslab.ide.ride.agent.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.agent.analyzer.*
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator
import com.intellij.openapi.project.Project

/**
 * Тесты для новой архитектуры EnhancedChatAgent
 */
class EnhancedChatAgentNewArchitectureTest {

    @Test
    fun `simple query should use direct response path`() = runBlocking {
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

        // Создаем EnhancedChatAgent с новой архитектурой
        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        // Выполняем простой запрос
        val request = AgentRequest(
            request = "Какой сегодня день?",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем, что вернулся правильный ответ с новыми метаданными
        assertTrue(response.success)
        assertEquals("Ответ на простой вопрос", response.content)
        assertTrue(response.metadata.containsKey("uncertainty_analysis"))
        assertTrue(response.metadata.containsKey("fast_path"))
        assertEquals(true, response.metadata["fast_path"])

        // Проверяем, что базовый агент был вызван, а планировщик - нет
        verify(mockBaseAgent, times(1)).ask(any())
    }

    @Test
    fun `medium complexity query should use planning`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответ от оркестратора
        val expectedResponse = AgentResponse.success(
            content = "Результат анализа кода",
            isFinal = true,
            uncertainty = 0.3
        )
        `when`(mockOrchestrator.processEnhanced(any(), any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        // Выполняем запрос средней сложности
        val request = AgentRequest(
            request = "Объясни как работает этот метод",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем, что оркестратор был вызван
        verify(mockOrchestrator, times(1)).processEnhanced(any(), any())
        assertTrue(response.metadata.containsKey("uncertainty_analysis"))
        assertTrue(response.metadata.containsKey("plan_id"))
    }

    @Test
    fun `complex query should use adaptive planning`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответ от оркестратора
        val expectedResponse = AgentResponse.success(
            content = "Результат комплексного анализа",
            isFinal = true,
            uncertainty = 0.5
        )
        `when`(mockOrchestrator.processEnhanced(any(), any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        // Выполняем сложный запрос
        val request = AgentRequest(
            request = "Проанализируй архитектуру этого проекта и предложи улучшения",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем, что оркестратор был вызван
        verify(mockOrchestrator, times(1)).processEnhanced(any(), any())
        assertTrue(response.metadata.containsKey("uncertainty_analysis"))
        assertTrue(response.metadata.containsKey("adaptive_plan"))
        assertEquals(true, response.metadata["adaptive_plan"])
    }

    @Test
    fun `plan resume should work with new architecture`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответ от оркестратора
        val expectedResponse = AgentResponse.success(
            content = "План возобновлен",
            isFinal = true,
            uncertainty = 0.2
        )
        `when`(mockOrchestrator.resumePlanWithCallback(any(), any(), any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

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

        // Проверяем, что был вызван метод возобновления плана
        verify(mockOrchestrator, times(1)).resumePlanWithCallback(eq("test-plan-id"), eq("продолжить выполнение"), any())
        assertTrue(response.metadata.containsKey("resumed"))
        assertEquals(true, response.metadata["resumed"])
    }

    @Test
    fun `should include uncertainty analysis in response metadata`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        val expectedResponse = AgentResponse.success(
            content = "Тестовый ответ",
            isFinal = true,
            uncertainty = 0.1
        )
        `when`(mockOrchestrator.processEnhanced(any(), any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Проанализируй код",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем наличие метаданных анализа неопределенности
        assertTrue(response.metadata.containsKey("uncertainty_analysis"))

        val uncertaintyAnalysis = response.metadata["uncertainty_analysis"] as? Map<*, *>
        assertNotNull(uncertaintyAnalysis)
        assertTrue(uncertaintyAnalysis!!.containsKey("score"))
        assertTrue(uncertaintyAnalysis.containsKey("complexity"))
        assertTrue(uncertaintyAnalysis.containsKey("reasoning"))
        assertTrue(uncertaintyAnalysis.containsKey("processing_strategy"))
    }

    @Test
    fun `should fallback to base agent on error`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответ от базового агента
        val fallbackResponse = AgentResponse.success(
            content = "Fallback ответ",
            isFinal = true,
            uncertainty = 0.0
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(fallbackResponse)

        // Оркестратор выбрасывает исключение
        `when`(mockOrchestrator.processEnhanced(any(), any()))
            .thenThrow(RuntimeException("Test error"))

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Проанализируй код",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Должен быть fallback к базовому агенту
        assertEquals("Fallback ответ", response.content)
        verify(mockBaseAgent, times(1)).ask(any())
    }

    @Test
    fun `should measure processing time`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        val expectedResponse = AgentResponse.success(
            content = "Тестовый ответ",
            isFinal = true,
            uncertainty = 0.0
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Простой вопрос",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем, что было измерено время обработки
        assertTrue(response.metadata.containsKey("processing_time_ms"))
    }

    @Test
    fun `should create agent with new architecture`() {
        val mockLLMProvider = mock(LLMProvider::class.java)

        val enhancedAgent = EnhancedChatAgent.create(mockLLMProvider)

        assertNotNull(enhancedAgent)
        assertTrue(enhancedAgent.capabilities.tools.contains("adaptive_planning"))
        assertTrue(enhancedAgent.capabilities.tools.contains("rag_enrichment"))
        assertTrue(enhancedAgent.capabilities.tools.contains("uncertainty_analysis"))
        assertTrue(enhancedAgent.capabilities.tools.contains("dynamic_modification"))
    }

    @Test
    fun `should have enhanced capabilities with new tools`() {
        val mockLLMProvider = mock(LLMProvider::class.java)

        val enhancedAgent = EnhancedChatAgent.create(mockLLMProvider)

        val capabilities = enhancedAgent.capabilities

        // Проверяем новые инструменты
        assertTrue(capabilities.tools.contains("adaptive_planning"))
        assertTrue(capabilities.tools.contains("rag_enrichment"))
        assertTrue(capabilities.tools.contains("uncertainty_analysis"))
        assertTrue(capabilities.tools.contains("dynamic_modification"))

        // Проверяем новые правила ответа
        assertTrue(capabilities.responseRules.any { it.contains("интеллектуальную оценку неопределенности") })
        assertTrue(capabilities.responseRules.any { it.contains("RAG обогащение только на этапе планирования") })
        assertTrue(capabilities.responseRules.any { it.contains("адаптивные планы с условными шагами") })
    }

    @Test
    fun `should handle plan execution with metadata`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        val expectedResponse = AgentResponse.success(
            content = "Результат выполнения плана",
            isFinal = true,
            uncertainty = 0.2
        )
        `when`(mockOrchestrator.processEnhanced(any(), any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Создай отчет о коде",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем метаданные плана
        assertTrue(response.metadata.containsKey("plan_id"))
        assertTrue(response.metadata.containsKey("plan_steps"))
        assertTrue(response.metadata.containsKey("plan_version"))

        // Проверяем метаданные анализа
        val uncertaintyAnalysis = response.metadata["uncertainty_analysis"] as? Map<*, *>
        assertNotNull(uncertaintyAnalysis)
        assertEquals("planned_execution", uncertaintyAnalysis["processing_strategy"])
    }

    @Test
    fun `should handle adaptive plan execution`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        val expectedResponse = AgentResponse.success(
            content = "Результат адаптивного выполнения",
            isFinal = true,
            uncertainty = 0.3
        )
        `when`(mockOrchestrator.processEnhanced(any(), any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Проанализируй и оптимизируй производительность",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response = enhancedAgent.ask(request)

        // Проверяем метаданные адаптивного плана
        assertTrue(response.metadata.containsKey("adaptive_plan"))
        assertEquals(true, response.metadata["adaptive_plan"])

        val uncertaintyAnalysis = response.metadata["uncertainty_analysis"] as? Map<*, *>
        assertNotNull(uncertaintyAnalysis)
        assertEquals("adaptive_planned_execution", uncertaintyAnalysis["processing_strategy"])
    }
}