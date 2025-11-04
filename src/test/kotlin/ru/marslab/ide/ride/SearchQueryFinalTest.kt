package ru.marslab.ide.ride

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import ru.marslab.ide.ride.agent.analyzer.RequestComplexityAnalyzer
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.ComplexityLevel
import com.intellij.openapi.project.Project

/**
 * Финальный тест для проверки, что система распознает поисковые запросы правильно
 */
class SearchQueryFinalTest {

    private val complexityAnalyzer = RequestComplexityAnalyzer()
    private val mockProject: Project = mock()

    @Test
    fun `test main search query is properly recognized`() {
        // Given: оригинальный проблемный запрос
        val searchQuery = "Найди в проекте экран создания записи"
        val context = ChatContext(project = mockProject, history = emptyList())

        // When: анализируем запрос
        val result = complexityAnalyzer.analyzeUncertainty(searchQuery, context)

        // Then: запрашиваем детальную информацию и выводим в консоль
        println("=== ANALYSIS RESULTS ===")
        println("Query: '$searchQuery'")
        println("Complexity: ${result.complexity}")
        println("Score: ${result.score}")
        println("Reasoning: '${result.reasoning}'")
        println("Suggested actions: ${result.suggestedActions}")
        println("Features: ${result.detectedFeatures}")
        println("========================")

        // Базовая проверка - система должна работать без ошибок
        assertNotNull(result)
        assertTrue(result.score >= 0.0)
        assertTrue(result.score <= 1.0)
        assertTrue(result.reasoning.isNotEmpty())
        assertTrue(result.suggestedActions.isNotEmpty())
    }

    @Test
    fun `test find keyword increases complexity`() {
        // Given: запросы с разной сложностью
        val queries = mapOf(
            "Создай класс" to ComplexityLevel.LOW,
            "Найди класс" to ComplexityLevel.HIGH,
            "Экран ввода" to ComplexityLevel.MEDIUM,
            "Найди экран ввода" to ComplexityLevel.HIGH
        )

        queries.forEach { (query, expectedComplexity) ->
            val context = ChatContext(project = mockProject, history = emptyList())
            val result = complexityAnalyzer.analyzeUncertainty(query, context)

            assertEquals(
                expectedComplexity,
                result.complexity,
                "Query '$query' should have complexity $expectedComplexity, got ${result.complexity}"
            )
        }
    }

    @Test
    fun `test UI elements are recognized in search queries`() {
        // Given: запросы с UI элементами
        val uiQueries = listOf(
            "Найди диалог ввода пароля",
            "Найди экран создания записи",
            "Найди форму регистрации",
            "Найди окно настроек"
        )

        uiQueries.forEach { query ->
            val context = ChatContext(project = mockProject, history = emptyList())
            val result = complexityAnalyzer.analyzeUncertainty(query, context)

            // Then: запросы с "найди" и UI элементами должны иметь HIGH сложность
            assertEquals(
                ComplexityLevel.HIGH,
                result.complexity,
                "UI search query '$query' should have HIGH complexity, got ${result.complexity}"
            )

            assertTrue(
                result.score > 0.7,
                "UI search query '$query' should have high score, got ${result.score}"
            )
        }
    }

    @Test
    fun `test original problematic query works correctly`() {
        // This is the exact query from the original issue
        val originalQuery = "Найди в проекте экран создания записи"
        val context = ChatContext(project = mockProject, history = emptyList())
        val result = complexityAnalyzer.analyzeUncertainty(originalQuery, context)

        // Verify the fix works for the original problem
        assertEquals(ComplexityLevel.HIGH, result.complexity)
        assertTrue(result.score > 0.7)
        assertTrue(result.reasoning.isNotEmpty())
        assertTrue(result.suggestedActions.isNotEmpty())

        // Print results for manual verification
        println("Original query: '$originalQuery'")
        println("Complexity: ${result.complexity}")
        println("Score: ${result.score}")
        println("Reasoning: ${result.reasoning}")
        println("Suggested actions: ${result.suggestedActions}")
        println("Features: ${result.detectedFeatures}")
    }
}