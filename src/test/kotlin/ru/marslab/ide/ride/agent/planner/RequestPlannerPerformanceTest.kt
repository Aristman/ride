package ru.marslab.ide.ride.agent.planner

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ru.marslab.ide.ride.agent.analyzer.*
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.*
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock
import kotlin.system.measureTimeMillis

/**
 * Тесты производительности для RequestPlanner
 */
class RequestPlannerPerformanceTest {

    private val planner = RequestPlanner()
    private val mockProject = mock(Project::class.java)
    private val context = ChatContext(project = mockProject, history = emptyList())

    @Test
    fun `simple plan creation should be fast`() {
        val request = "Что такое Kotlin?"
        val uncertainty = UncertaintyResult(
            score = 0.1,
            complexity = ComplexityLevel.SIMPLE,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Простой вопрос"
        )

        val timeTaken = measureTimeMillis {
            repeat(100) {
                val plan = planner.createPlan(request, uncertainty, context)
                assertNotNull(plan.id)
                assertEquals(1, plan.steps.size)
            }
        }

        // Простые планы должны создаваться очень быстро (< 50ms для 100 планов)
        assertTrue(timeTaken < 50, "Simple plan creation took ${timeTaken}ms, expected < 50ms for 100 iterations")
    }

    @Test
    fun `complex plan creation should be reasonably fast`() {
        val request = "Проанализируй архитектуру этого микросервисного приложения, найди все проблемы с производительностью, " +
                "предложи улучшения, создай подробный отчет и сгенерируй документацию"

        val uncertainty = UncertaintyResult(
            score = 0.9,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "использовать_оркестратор", "поиск_контекста"),
            reasoning = "Очень сложный архитектурный запрос",
            detectedFeatures = listOf("архитектурный", "подробный", "связан_с_кодом")
        )

        val timeTaken = measureTimeMillis {
            repeat(50) {
                val plan = planner.createPlan(request, uncertainty, context)
                assertNotNull(plan.id)
                assertTrue(plan.steps.size >= 4)
            }
        }

        // Сложные планы должны создаваться приемлемо быстро (< 200ms для 50 планов)
        assertTrue(timeTaken < 200, "Complex plan creation took ${timeTaken}ms, expected < 200ms for 50 iterations")
    }

    @Test
    fun `plan creation should handle high volume efficiently`() {
        val requests = listOf(
            "Какой сегодня день?" to ComplexityLevel.SIMPLE,
            "Объясни как работает этот код" to ComplexityLevel.MEDIUM,
            "Проанализируй качество кода" to ComplexityLevel.MEDIUM,
            "Найди и исправь баги" to ComplexityLevel.COMPLEX,
            "Сделай рефакторинг этого модуля" to ComplexityLevel.COMPLEX
        )

        val timeTaken = measureTimeMillis {
            val plans = mutableListOf<ExecutionPlan>()

            repeat(200) { iteration ->
                val (request, complexity) = requests[iteration % requests.size]
                val uncertainty = UncertaintyResult(
                    score = when (complexity) {
                        ComplexityLevel.SIMPLE -> 0.1
                        ComplexityLevel.MEDIUM -> 0.5
                        ComplexityLevel.COMPLEX -> 0.8
                    },
                    complexity = complexity,
                    suggestedActions = when (complexity) {
                        ComplexityLevel.SIMPLE -> listOf("прямой_ответ")
                        ComplexityLevel.MEDIUM -> listOf("контекстный_ответ")
                        ComplexityLevel.COMPLEX -> listOf("создать_план")
                    },
                    reasoning = "Тестовый запрос"
                )

                plans.add(planner.createPlan(request, uncertainty, context))
            }

            assertEquals(200, plans.size)

            // Проверяем что все планы корректны
            plans.forEach { plan ->
                assertNotNull(plan.id)
                assertTrue(plan.steps.isNotEmpty())
                assertEquals(PlanState.CREATED, plan.currentState)
            }
        }

        // 200 планов должны создаваться быстро (< 500ms)
        assertTrue(timeTaken < 500, "High volume plan creation took ${timeTaken}ms, expected < 500ms for 200 plans")
    }

    @Test
    fun `plan creation with context history should not significantly impact performance`() {
        val history = (1..100).map { i ->
            ru.marslab.ide.ride.model.chat.Message(
                content = "Сообщение в истории номер $i с контекстом для анализа производительности",
                role = if (i % 2 == 0) ru.marslab.ide.ride.model.chat.MessageRole.USER
                        else ru.marslab.ide.ride.model.chat.MessageRole.ASSISTANT
            )
        }

        val contextWithHistory = ChatContext(project = mockProject, history = history)
        val request = "Проанализируй наш диалог"
        val uncertainty = UncertaintyResult(
            score = 0.4,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("контекстный_ответ"),
            reasoning = "Запрос с контекстом"
        )

        val timeTakenWithoutHistory = measureTimeMillis {
            repeat(50) {
                planner.createPlan(request, uncertainty, context)
            }
        }

        val timeTakenWithHistory = measureTimeMillis {
            repeat(50) {
                planner.createPlan(request, uncertainty, contextWithHistory)
            }
        }

        // Контекст не должен значительно замедлять создание планов (< 2x разница)
        val ratio = timeTakenWithHistory.toDouble() / timeTakenWithoutHistory
        assertTrue(ratio < 2.0,
            "Plan creation with history took ${ratio}x longer, expected < 2x")
    }

    @Test
    fun `concurrent plan creation should be thread safe`() {
        val requestTypes = listOf(
            "Простой запрос" to UncertaintyResult(
                score = 0.1, complexity = ComplexityLevel.SIMPLE,
                suggestedActions = listOf("прямой_ответ"), reasoning = "Простой"
            ),
            "Анализ кода" to UncertaintyResult(
                score = 0.5, complexity = ComplexityLevel.MEDIUM,
                suggestedActions = listOf("контекстный_ответ"), reasoning = "Средний"
            ),
            "Архитектурный анализ" to UncertaintyResult(
                score = 0.8, complexity = ComplexityLevel.COMPLEX,
                suggestedActions = listOf("создать_план"), reasoning = "Сложный"
            )
        )

        val results = mutableListOf<ExecutionPlan>()
        val timeTaken = measureTimeMillis {
            val threads = requestTypes.map { (request, uncertainty) ->
                Thread {
                    repeat(20) {
                        val plan = planner.createPlan(request, uncertainty, context)
                        synchronized(results) {
                            results.add(plan)
                        }
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }
        }

        // Проверяем что все планы созданы
        assertEquals(60, results.size, "Expected 60 plans from concurrent creation")

        // Проверяем корректность планов
        results.forEach { plan ->
            assertNotNull(plan.id)
            assertTrue(plan.steps.isNotEmpty())
            assertEquals(PlanState.CREATED, plan.currentState)
            assertTrue(plan.analysis.taskType != TaskType.SIMPLE_QUERY || plan.steps.size == 1)
        }

        // Параллельное создание должно быть быстрым (< 300ms для 60 планов)
        assertTrue(timeTaken < 300, "Concurrent plan creation took ${timeTaken}ms, expected < 300ms")
    }

    @Test
    fun `plan creation should be memory efficient`() {
        val request = "Проанализируй производительность этого кода"
        val uncertainty = UncertaintyResult(
            score = 0.6,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план"),
            reasoning = "Запрос на анализ производительности"
        )

        // Создаем много планов подряд для проверки утечек памяти
        repeat(1000) { iteration ->
            val plan = planner.createPlan(request, uncertainty, context, "request-$iteration")

            // Проверяем базовые свойства
            assertEquals("request-$iteration", plan.userRequestId)
            assertTrue(plan.steps.size >= 2)

            // Проверяем что шаги имеют корректную структуру
            plan.steps.forEach { step ->
                assertNotNull(step.id)
                assertTrue(step.estimatedDurationMs > 0)
                assertEquals(StepStatus.PENDING, step.status)
            }
        }

        // Если бы были проблемы с памятью, тест бы упал с OutOfMemoryError
    }

    @Test
    fun `plan creation should handle very long requests efficiently`() {
        val veryLongRequest = buildString {
            append("Проанализируй следующий код и предложи комплексные улучшения: ")
            repeat(500) {
                append("Это очень длинный запрос для тестирования производительности планировщика. ")
                append("Он содержит много технических деталей и специфику. ")
            }
            append("Требуется архитектурный анализ, рефакторинг, оптимизация производительности и документирование.")
        }

        val uncertainty = UncertaintyResult(
            score = 0.9,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "использовать_оркестратор"),
            reasoning = "Очень длинный и сложный запрос",
            detectedFeatures = listOf("подробный", "архитектурный", "связан_с_кодом")
        )

        val timeTaken = measureTimeMillis {
            val plan = planner.createPlan(veryLongRequest, uncertainty, context)

            // Проверяем что план корректный
            assertNotNull(plan.id)
            assertTrue(plan.steps.size >= 3)
            assertEquals(TaskType.COMPLEX_MULTI_STEP, plan.analysis.taskType)
            assertEquals(ComplexityLevel.HIGH, plan.analysis.estimatedComplexity)
        }

        // Даже очень длинные запросы должны обрабатываться быстро (< 100ms)
        assertTrue(timeTaken < 100, "Very long request processing took ${timeTaken}ms, expected < 100ms")
    }

    @Test
    fun `plan step generation should be efficient`() {
        val uncertainty = UncertaintyResult(
            score = 0.7,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "поиск_контекста"),
            reasoning = "Сложный запрос"
        )

        val timeTaken = measureTimeMillis {
            repeat(100) {
                val plan = planner.createPlan("Тест", uncertainty, context)

                // Проверяем что шаги генерируются корректно
                assertTrue(plan.steps.isNotEmpty())

                // Проверяем структуру шагов
                plan.steps.forEach { step ->
                    assertNotNull(step.id)
                    assertTrue(step.id.matches(Regex("[a-z_]+_\\d+")), "Step ID should follow pattern")
                    assertTrue(step.title.isNotBlank())
                    assertTrue(step.description.isNotBlank())
                    assertTrue(step.estimatedDurationMs > 0)
                    assertEquals(StepStatus.PENDING, step.status)
                }

                // Проверяем уникальность ID
                val stepIds = plan.steps.map { it.id }
                assertEquals(stepIds.size, stepIds.distinct().size)
            }
        }

        // Генерация шагов должна быть быстрой (< 100ms для 100 планов)
        assertTrue(timeTaken < 100, "Step generation took ${timeTaken}ms, expected < 100ms for 100 plans")
    }
}