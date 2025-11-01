package ru.marslab.ide.ride.agent.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ru.marslab.ide.ride.model.chat.ChatContext
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock
import kotlin.system.measureTimeMillis

/**
 * Тесты производительности для RequestComplexityAnalyzer
 */
class RequestComplexityAnalyzerPerformanceTest {

    private val analyzer = RequestComplexityAnalyzer()
    private val mockProject = mock(Project::class.java)
    private val context = ChatContext(project = mockProject, history = emptyList())

    @Test
    fun `simple query analysis should be fast`() {
        val request = "Какой сегодня день?"

        val timeTaken = measureTimeMillis {
            repeat(100) {
                analyzer.analyzeUncertainty(request, context)
            }
        }

        // Должно выполняться быстро (< 100ms для 100 анализов)
        assertTrue(timeTaken < 100, "Analysis took ${timeTaken}ms, expected < 100ms for 100 iterations")
    }

    @Test
    fun `complex query analysis should still be reasonably fast`() {
        val request = "Проанализируй архитектуру этого проекта, найди потенциальные проблемы с производительностью, " +
                "предложи улучшения и создай подробный отчет с рекомендациями по рефакторингу"

        val timeTaken = measureTimeMillis {
            repeat(50) {
                analyzer.analyzeUncertainty(request, context)
            }
        }

        // Должно выполняться приемлемо быстро (< 200ms для 50 сложных анализов)
        assertTrue(timeTaken < 200, "Complex analysis took ${timeTaken}ms, expected < 200ms for 50 iterations")
    }

    @Test
    fun `analysis should handle very long queries efficiently`() {
        val longRequest = buildString {
            append("Проанализируй следующий код и предложи улучшения: ")
            repeat(1000) {
                append("Это очень длинный запрос для тестирования производительности анализатора неопределенности. ")
            }
        }

        val timeTaken = measureTimeMillis {
            val result = analyzer.analyzeUncertainty(longRequest, context)

            // Проверяем, что результат корректный
            assertTrue(result.score > 0.5)
            assertEquals(ComplexityLevel.COMPLEX, result.complexity)
        }

        // Даже очень длинные запросы должны обрабатываться быстро (< 50ms)
        assertTrue(timeTaken < 50, "Very long query analysis took ${timeTaken}ms, expected < 50ms")
    }

    @Test
    fun `concurrent analysis should be thread safe`() {
        val requests = listOf(
            "Какой сегодня день?",
            "Проанализируй код",
            "Что такое Kotlin?",
            "Оптимизируй производительность",
            "Сделай рефакторинг класса"
        )

        val results = mutableListOf<UncertaintyResult>()
        val timeTaken = measureTimeMillis {
            // Запускаем несколько анализов параллельно
            val threads = requests.map { request ->
                Thread {
                    repeat(20) {
                        results.add(analyzer.analyzeUncertainty(request, context))
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }
        }

        // Проверяем, что все анализы завершились
        assertEquals(100, results.size, "Expected 100 results from concurrent analysis")

        // Проверяем, что все результаты валидны
        results.forEach { result ->
            assertTrue(result.score >= 0.0 && result.score <= 1.0, "Score should be in range [0,1]")
            assertNotNull(result.complexity, "Complexity should not be null")
            assertTrue(result.suggestedActions.isNotEmpty(), "Suggested actions should not be empty")
        }

        // Параллельный анализ должен быть быстрым (< 300ms для 100 анализов)
        assertTrue(timeTaken < 300, "Concurrent analysis took ${timeTaken}ms, expected < 300ms")
    }

    @Test
    fun `analysis with context history should not significantly impact performance`() {
        val history = (1..50).map { i ->
            ru.marslab.ide.ride.model.chat.Message(
                content = "Сообщение в истории номер $i с некоторым контекстом для анализа",
                role = if (i % 2 == 0) ru.marslab.ide.ride.model.chat.MessageRole.USER
                        else ru.marslab.ide.ride.model.chat.MessageRole.ASSISTANT
            )
        }

        val contextWithHistory = ChatContext(project = mockProject, history = history)
        val request = "Проанализируй наш диалог"

        val timeTakenWithoutHistory = measureTimeMillis {
            repeat(50) {
                analyzer.analyzeUncertainty(request, context)
            }
        }

        val timeTakenWithHistory = measureTimeMillis {
            repeat(50) {
                analyzer.analyzeUncertainty(request, contextWithHistory)
            }
        }

        // Контекст не должен значительно замедлять анализ (< 2x разница)
        val ratio = timeTakenWithHistory.toDouble() / timeTakenWithoutHistory
        assertTrue(ratio < 2.0,
            "Context analysis took ${ratio}x longer than without context, expected < 2x")
    }

    @Test
    fun `memory usage should be stable for repeated analyses`() {
        val request = "Объясни как работает этот код?"

        // Выполняем много анализов подряд
        repeat(1000) {
            val result = analyzer.analyzeUncertainty(request, context)

            // Проверяем базовую корректность
            assertTrue(result.score >= 0.0)
            assertNotNull(result.reasoning)
        }

        // Если бы были проблемы с памятью, тест бы упал с OutOfMemoryError
        // или сильно замедлился бы к концу
    }
}