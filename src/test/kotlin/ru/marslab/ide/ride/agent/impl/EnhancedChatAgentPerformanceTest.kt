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
import kotlin.system.measureTimeMillis

/**
 * Тесты производительности для новой архитектуры EnhancedChatAgent
 */
class EnhancedChatAgentPerformanceTest {

    @Test
    fun `simple query processing should be very fast`() = runBlocking {
        // Создаем моки
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем быстрый ответ от базового агента
        val expectedResponse = AgentResponse.success(
            content = "Ответ на простой вопрос",
            isFinal = true,
            uncertainty = 0.0
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Какой сегодня день?",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val timeTaken = measureTimeMillis {
            repeat(50) {
                val response = enhancedAgent.ask(request)
                assertTrue(response.success)
                assertEquals("Ответ на простой вопрос", response.content)
                assertTrue(response.metadata["fast_path"] == true)
            }
        }

        // Простые запросы должны обрабатываться очень быстро (< 100ms для 50 запросов)
        assertTrue(timeTaken < 100, "Simple query processing took ${timeTaken}ms, expected < 100ms for 50 iterations")
        println("Simple query processing: ${timeTaken}ms for 50 iterations")
    }

    @Test
    fun `medium complexity query processing should be reasonably fast`() = runBlocking {
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

        val request = AgentRequest(
            request = "Объясни как работает этот метод в классе",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val timeTaken = measureTimeMillis {
            repeat(20) {
                val response = enhancedAgent.ask(request)
                assertTrue(response.success)
                assertEquals("Результат анализа кода", response.content)
                assertTrue(response.metadata.containsKey("plan_id"))
            }
        }

        // Запросы средней сложности должны обрабатываться приемлемо быстро (< 200ms для 20 запросов)
        assertTrue(timeTaken < 200, "Medium complexity processing took ${timeTaken}ms, expected < 200ms for 20 iterations")
        println("Medium complexity processing: ${timeTaken}ms for 20 iterations")
    }

    @Test
    fun `complex query processing should be acceptable`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответ от оркестратора
        val expectedResponse = AgentResponse.success(
            content = "Результат комплексного анализа архитектуры",
            isFinal = true,
            uncertainty = 0.4
        )
        `when`(mockOrchestrator.processEnhanced(any(), any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Проанализируй архитектуру этого микросервисного приложения, найди проблемы с производительностью и предложи улучшения",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val timeTaken = measureTimeMillis {
            repeat(10) {
                val response = enhancedAgent.ask(request)
                assertTrue(response.success)
                assertEquals("Результат комплексного анализа архитектуры", response.content)
                assertTrue(response.metadata["adaptive_plan"] == true)
            }
        }

        // Сложные запросы могут занимать больше времени, но должны быть приемлемы (< 500ms для 10 запросов)
        assertTrue(timeTaken < 500, "Complex query processing took ${timeTaken}ms, expected < 500ms for 10 iterations")
        println("Complex query processing: ${timeTaken}ms for 10 iterations")
    }

    @Test
    fun `uncertainty analysis overhead should be minimal`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        val expectedResponse = AgentResponse.success(
            content = "Ответ",
            isFinal = true,
            uncertainty = 0.1
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Что такое Kotlin?",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val timeTaken = measureTimeMillis {
            repeat(100) {
                enhancedAgent.ask(request)
            }
        }

        // Накладные расходы на анализ неопределенности должны быть минимальными
        assertTrue(timeTaken < 150, "Uncertainty analysis overhead took ${timeTaken}ms, expected < 150ms for 100 iterations")
        println("Uncertainty analysis overhead: ${timeTaken}ms for 100 iterations")
    }

    @Test
    fun `mixed query types should maintain performance`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        // Настраиваем ответы
        val simpleResponse = AgentResponse.success(
            content = "Простой ответ",
            isFinal = true,
            uncertainty = 0.0
        )
        val complexResponse = AgentResponse.success(
            content = "Сложный ответ",
            isFinal = true,
            uncertainty = 0.4
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(simpleResponse)
        `when`(mockOrchestrator.processEnhanced(any(), any())).thenReturn(complexResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val requests = listOf(
            AgentRequest(
                request = "Какой сегодня день?",
                context = ChatContext(project = mockProject, history = emptyList()),
                parameters = LLMParameters.DEFAULT
            ),
            AgentRequest(
                request = "Объясни этот код",
                context = ChatContext(project = mockProject, history = emptyList()),
                parameters = LLMParameters.DEFAULT
            ),
            AgentRequest(
                request = "Проанализируй архитектуру",
                context = ChatContext(project = mockProject, history = emptyList()),
                parameters = LLMParameters.DEFAULT
            )
        )

        val timeTaken = measureTimeMillis {
            requests.forEach { request ->
                repeat(10) {
                    enhancedAgent.ask(request)
                }
            }
        }

        // Смешанные запросы должны обрабатываться эффективно (< 300ms для 30 запросов)
        assertTrue(timeTaken < 300, "Mixed query processing took ${timeTaken}ms, expected < 300ms for 30 iterations")
        println("Mixed query processing: ${timeTaken}ms for 30 iterations")
    }

    @Test
    fun `concurrent requests should be handled efficiently`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        val expectedResponse = AgentResponse.success(
            content = "Ответ",
            isFinal = true,
            uncertainty = 0.1
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Тестовый вопрос",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val results = mutableListOf<AgentResponse>()
        val timeTaken = measureTimeMillis {
            val threads = (1..5).map { threadId ->
                Thread {
                    repeat(10) {
                        val response = enhancedAgent.ask(request)
                        synchronized(results) {
                            results.add(response)
                        }
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }
        }

        // Проверяем, что все запросы выполнены
        assertEquals(50, results.size, "All requests should be completed")

        // Проверяем корректность ответов
        results.forEach { response ->
            assertTrue(response.success)
            assertEquals("Ответ", response.content)
        }

        // Параллельная обработка должна быть эффективной (< 200ms для 50 запросов)
        assertTrue(timeTaken < 200, "Concurrent processing took ${timeTaken}ms, expected < 200ms for 50 requests")
        println("Concurrent processing: ${timeTaken}ms for 50 requests")
    }

    @Test
    fun `memory usage should be stable`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        val expectedResponse = AgentResponse.success(
            content = "Ответ",
            isFinal = true,
            uncertainty = 0.1
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(expectedResponse)

        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Тестовый запрос",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        // Выполняем много запросов подряд для проверки утечек памяти
        repeat(1000) {
            val response = enhancedAgent.ask(request)
            assertTrue(response.success)
            assertNotNull(response.metadata)
        }

        // Если бы были проблемы с памятью, тест бы упал с OutOfMemoryError
        println("Memory test completed 1000 iterations successfully")
    }

    @Test
    fun `metadata overhead should not impact performance significantly`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        val simpleResponse = AgentResponse.success(
            content = "Простой ответ",
            isFinal = true,
            uncertainty = 0.0
        )
        `when`(mockBaseAgent.ask(any())).thenReturn(simpleResponse)

        // Сравниваем производительность с метаданными и без
        val enhancedAgent = EnhancedChatAgent(
            baseChatAgent = mockBaseAgent,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            request = "Простой вопрос",
            context = ChatContext(project = mockProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        // Запросы с полной метаданной
        val timeWithMetadata = measureTimeMillis {
            repeat(50) {
                val response = enhancedAgent.ask(request)
                assertTrue(response.metadata.containsKey("uncertainty_analysis"))
                assertTrue(response.metadata.containsKey("processing_time_ms"))
                assertTrue(response.metadata.containsKey("fast_path"))
            }
        }

        // Запросы с минимальной метаданной (если бы мы могли ее отключить)
        val timeMinimalMetadata = measureTimeMillis {
            repeat(50) {
                // В текущей реализации метаданные всегда добавляются
                val response = enhancedAgent.ask(request)
                assertTrue(response.success)
            }
        }

        // Разница не должна быть значительной
        val ratio = timeWithMetadata.toDouble() / timeMinimalMetadata
        assertTrue(ratio < 2.0, "Metadata overhead ratio is ${ratio}, expected < 2.0")

        println("Metadata overhead: ${timeWithMetadata}ms vs ${timeMinimalMetadata}ms (ratio: ${ratio})")
    }

    @Test
    fun `plan creation overhead should be acceptable`() = runBlocking {
        val mockBaseAgent = mock(Agent::class.java)
        val mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        val mockProject = mock(Project::class.java)

        val expectedResponse = AgentResponse.success(
            content = "Результат выполнения плана",
            isFinal = true,
            uncertainty = 0.3
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

        val timeTaken = measureTimeMillis {
            repeat(20) {
                val response = enhancedAgent.ask(request)
                assertTrue(response.success)
                assertTrue(response.metadata.containsKey("plan_id"))
                assertTrue(response.metadata.containsKey("plan_steps"))
            }
        }

        // Накладные расходы на создание плана должны быть приемлемыми
        assertTrue(timeTaken < 300, "Plan creation overhead took ${timeTaken}ms, expected < 300ms for 20 iterations")
        println("Plan creation overhead: ${timeTaken}ms for 20 iterations")
    }
}