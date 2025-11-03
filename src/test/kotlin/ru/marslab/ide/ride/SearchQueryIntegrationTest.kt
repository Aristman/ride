package ru.marslab.ide.ride

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import ru.marslab.ide.ride.agent.analyzer.RequestComplexityAnalyzer
import ru.marslab.ide.ride.agent.planner.RequestPlanner
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.*
import com.intellij.openapi.project.Project

/**
 * Интеграционный тест для проверки работы системы распознавания поисковых запросов
 * вместе с планировщиком и созданием планов выполнения
 */
class SearchQueryIntegrationTest {

    private val complexityAnalyzer = RequestComplexityAnalyzer()
    private val planner = RequestPlanner()
    private val mockProject: Project = mock()
    private val mockLLM: LLMProvider = mock()

    @Test
    fun `test search query triggers proper planning with RAG`() {
        // Given: поисковый запрос "Найди в проекте экран создания записи"
        val searchQuery = "Найди в проекте экран создания записи"
        val context = ChatContext(project = mockProject, history = emptyList())

        // When: анализируем запрос и создаем план
        val uncertaintyResult = complexityAnalyzer.analyzeUncertainty(searchQuery, context)
        val plan = planner.createPlan(searchQuery, uncertaintyResult, context)

        // Then: запрос должен иметь HIGH сложность и содержать RAG шаг
        assertEquals(ComplexityLevel.HIGH, uncertaintyResult.complexity)
        assertTrue(uncertaintyResult.reasoning.contains("сложн"))

        // Проверяем, что план содержит шаг поиска
        val hasSearchStep = plan.steps.any { step ->
            step.description.contains("поиск", ignoreCase = true) ||
            step.description.contains("контекст", ignoreCase = true) ||
            step.agentType == AgentType.EMBEDDING_INDEXER
        }

        assertTrue(
            hasSearchStep,
            "Expected plan to contain search/RAG step for query: '$searchQuery'. Plan steps: ${plan.steps.map { it.description }}"
        )

        // Проверяем, что план не пустой и содержит осмысленные шаги
        assertTrue(plan.steps.isNotEmpty(), "Plan should contain steps")
        assertTrue(plan.steps.size <= 5, "Plan should not contain too many steps")
    }

    @Test
    fun `test various search queries create appropriate plans`() {
        // Given: различные поисковые запросы
        val searchQueries = listOf(
            "Найди диалог ввода пароля",
            "Найди экран создания записи",
            "Найди форму регистрации пользователя",
            "Найди окно настроек приложения"
        )

        searchQueries.forEach { query ->
            val context = ChatContext(project = mockProject, history = emptyList())
            val uncertaintyResult = complexityAnalyzer.analyzeUncertainty(query, context)
            val plan = planner.createPlan(query, uncertaintyResult, context)

            // Then: каждый поисковый запрос должен иметь HIGH сложность и план
            assertEquals(
                ComplexityLevel.HIGH,
                uncertaintyResult.complexity,
                "Query '$query' should have HIGH complexity"
            )

            assertTrue(
                plan.steps.isNotEmpty(),
                "Query '$query' should have a plan with steps"
            )

            // Проверяем, что в плане есть соответствующие шаги
            val planDescription = plan.steps.joinToString(" ") { it.description.lowercase() }

            assertTrue(
                planDescription.contains("поиск") ||
                planDescription.contains("найди") ||
                planDescription.contains("контекст") ||
                planDescription.contains("индекс") ||
                planDescription.contains("сканир"),
                "Plan for '$query' should contain search-related steps. Plan: $planDescription"
            )
        }
    }

    @Test
    fun `test non-search queries have different patterns`() {
        // Given: не-поисковые запросы
        val nonSearchQueries = listOf(
            "Создай новый класс",
            "Объясни как работает паттерн Singleton",
            "Какая погода сегодня"
        )

        nonSearchQueries.forEach { query ->
            val context = ChatContext(project = mockProject, history = emptyList())
            val uncertaintyResult = complexityAnalyzer.analyzeUncertainty(query, context)

            // Then: не-поисковые запросы должны иметь другую сложность
            assertNotEquals(
                ComplexityLevel.HIGH,
                uncertaintyResult.complexity,
                "Non-search query '$query' should not have HIGH complexity"
            )
        }
    }

    @Test
    fun `test key find keyword weight in complexity calculation`() {
        // Given: запросы с ключевым словом "найди"
        val queriesWithFind = listOf(
            "найди файл",
            "найди класс",
            "найди метод",
            "найди ошибку"
        )

        queriesWithFind.forEach { query ->
            val context = ChatContext(project = mockProject, history = emptyList())
            val uncertaintyResult = complexityAnalyzer.analyzeUncertainty(query, context)

            // Then: запросы с "найди" должны иметь повышенную сложность
            assertTrue(
                uncertaintyResult.complexity == ComplexityLevel.HIGH ||
                uncertaintyResult.complexity == ComplexityLevel.MEDIUM,
                "Query '$query' with 'найди' should have at least MEDIUM complexity, got ${uncertaintyResult.complexity}"
            )

            // Проверяем, что "найди" влияет на оценку
            assertTrue(
                uncertaintyResult.score > 0.3,
                "Query '$query' should have significant uncertainty score (> 0.3), got ${uncertaintyResult.score}"
            )
        }
    }
}