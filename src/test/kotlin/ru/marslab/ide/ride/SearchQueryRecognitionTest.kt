package ru.marslab.ide.ride

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import ru.marslab.ide.ride.agent.analyzer.RequestComplexityAnalyzer
import ru.marslab.ide.ride.agent.planner.RequestPlanner
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.ComplexityLevel
import com.intellij.openapi.project.Project

/**
 * Тест для проверки распознавания поисковых запросов
 */
class SearchQueryRecognitionTest {

    private val complexityAnalyzer = RequestComplexityAnalyzer()
    private val planner = RequestPlanner()
    private val mockProject: Project = mock()

    @Test
    fun `test search query recognition for UI elements`() {
        // Given: поисковый запрос с элементами UI
        val searchQuery = "Найди в проекте экран создания записи"
        val context = ChatContext(project = mockProject, history = emptyList())

        // When: анализируем сложность запроса
        val uncertaintyResult = complexityAnalyzer.analyzeUncertainty(searchQuery, context)

        // Then: запрос должен быть распознан как сложный (из-за "найди" + "экран")
        assertEquals(
            ComplexityLevel.HIGH,
            uncertaintyResult.complexity,
            "Expected HIGH complexity for search query with 'найди', got ${uncertaintyResult.complexity}"
        )

        // Проверяем, что "найди" распознается как сложное ключевое слово
        assertTrue(
            uncertaintyResult.reasoning.contains("сложн") ||
            uncertaintyResult.reasoning.contains("планир"),
            "Expected planning reasoning for search query, got: ${uncertaintyResult.reasoning}"
        )
    }

    @Test
    fun `test search query complexity levels`() {
        // Given: различные поисковые запросы с ожидаемыми сложностями
        val searchQueries = mapOf(
            "Найди диалог ввода пароля" to ComplexityLevel.HIGH, // "найди" + технический термин
            "Где находится экран создания записи" to ComplexityLevel.LOW, // соответствует простому паттерну "Где находится"
            "Найди форму регистрации пользователя" to ComplexityLevel.HIGH // "найди" + "форма"
        )

        searchQueries.forEach { (query, expectedComplexity) ->
            val context = ChatContext(project = mockProject, history = emptyList())
            val uncertaintyResult = complexityAnalyzer.analyzeUncertainty(query, context)

            // Then: поисковые запросы должны иметь ожидаемую сложность
            assertEquals(
                expectedComplexity,
                uncertaintyResult.complexity,
                "Expected $expectedComplexity complexity for search query '$query', got ${uncertaintyResult.complexity}"
            )
        }
    }

    @Test
    fun `test find keyword detection`() {
        // Given: запросы с ключевым словом "найди"
        val findQueries = listOf(
            "Найди экран создания записи",
            "Найди диалог ввода данных",
            "Найди файл с настройками"
        )

        findQueries.forEach { query ->
            val context = ChatContext(project = mockProject, history = emptyList())
            val uncertaintyResult = complexityAnalyzer.analyzeUncertainty(query, context)

            // Then: запросы с "найди" должны быть распознаны как сложные
            assertTrue(
                uncertaintyResult.reasoning.contains("сложн") ||
                uncertaintyResult.reasoning.contains("планир") ||
                uncertaintyResult.reasoning.contains("анализ"),
                "Expected complexity reasoning for '$query', got: ${uncertaintyResult.reasoning}"
            )
        }
    }

    @Test
    fun `test non-search queries have lower complexity`() {
        // Given: простые не-поисковые запросы
        val simpleQueries = listOf(
            "Какая погода сегодня",
            "Который час",
            "Что такое Kotlin",
            "Привет"
        )

        simpleQueries.forEach { query ->
            val context = ChatContext(project = mockProject, history = emptyList())
            val uncertaintyResult = complexityAnalyzer.analyzeUncertainty(query, context)

            // Then: простые запросы должны иметь низкую сложность
            assertEquals(
                ComplexityLevel.LOW,
                uncertaintyResult.complexity,
                "Expected LOW complexity for simple query '$query', got ${uncertaintyResult.complexity}"
            )
        }
    }
}