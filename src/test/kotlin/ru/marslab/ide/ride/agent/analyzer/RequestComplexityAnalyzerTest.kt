package ru.marslab.ide.ride.agent.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ru.marslab.ide.ride.model.chat.ChatContext
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock

/**
 * Тесты для RequestComplexityAnalyzer
 */
class RequestComplexityAnalyzerTest {

    private val analyzer = RequestComplexityAnalyzer()
    private val mockProject = mock(Project::class.java)

    @Test
    fun `simple question about time should have low uncertainty`() {
        val request = "Который час?"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertEquals(0.0, result.score, 0.01)
        assertEquals(ComplexityLevel.SIMPLE, result.complexity)
        assertTrue(result.suggestedActions.contains("прямой_ответ"))
        assertTrue(result.reasoning.contains("времени"))
        assertTrue(result.detectedFeatures.contains("вопрос_о_времени"))
    }

    @Test
    fun `simple weather question should have low uncertainty`() {
        val request = "Какая погода сегодня?"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertEquals(0.0, result.score, 0.01)
        assertEquals(ComplexityLevel.SIMPLE, result.complexity)
        assertTrue(result.suggestedActions.contains("прямой_ответ"))
        assertTrue(result.reasoning.contains("погоде"))
    }

    @Test
    fun `simple fact question should have low uncertainty`() {
        val request = "Что такое Kotlin?"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertTrue(result.score < 0.15)
        assertEquals(ComplexityLevel.SIMPLE, result.complexity)
        assertTrue(result.suggestedActions.contains("прямой_ответ"))
    }

    @Test
    fun `medium complexity question about code should have medium uncertainty`() {
        val request = "Объясни как работает этот метод?"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertTrue(result.score >= 0.3 && result.score < 0.7)
        assertEquals(ComplexityLevel.MEDIUM, result.complexity)
        assertTrue(result.suggestedActions.contains("контекстный_ответ"))
        assertTrue(result.detectedFeatures.contains("связан_с_кодом"))
    }

    @Test
    fun `complex analysis request should have high uncertainty`() {
        val request = "Проанализируй архитектуру этого проекта и найди потенциальные проблемы"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertTrue(result.score >= 0.7)
        assertEquals(ComplexityLevel.COMPLEX, result.complexity)
        assertTrue(result.suggestedActions.contains("создать_план"))
        assertTrue(result.suggestedActions.contains("использовать_оркестратор"))
        assertTrue(result.detectedFeatures.contains("требует_анализа"))
        assertTrue(result.detectedFeatures.contains("архитектурный"))
    }

    @Test
    fun `optimization request should have high uncertainty`() {
        val request = "Оптимизируй производительность этого кода"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertTrue(result.score >= 0.6)
        assertEquals(ComplexityLevel.COMPLEX, result.complexity)
        assertTrue(result.suggestedActions.contains("использовать_оркестратор"))
    }

    @Test
    fun `refactoring request should have high uncertainty`() {
        val request = "Сделай рефакторинг этого класса"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertTrue(result.score >= 0.7)
        assertEquals(ComplexityLevel.COMPLEX, result.complexity)
        assertTrue(result.suggestedActions.contains("создать_план"))
    }

    @Test
    fun `short request without clear question should have increased uncertainty`() {
        val request = "проблема"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        // Должно быть увеличено из-за неясности
        assertTrue(result.score > 0.2)
        assertTrue(result.reasoning.contains("короткий"))
    }

    @Test
    fun `long detailed request should have higher uncertainty`() {
        val longRequest = "У меня есть проблема с производительностью в моем приложении. " +
                "Когда я запускаю обработку больших объемов данных, все начинает работать очень медленно. " +
                "Я думал что это связано с базой данных, но проверил запросы и они выглядят нормально. " +
                "Помоги понять что может быть причиной."

        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(longRequest, context)

        assertTrue(result.score > 0.4)
        assertTrue(result.detectedFeatures.contains("подробный"))
    }

    @Test
    fun `request with context should have slightly higher uncertainty`() {
        val request = "Как работает этот метод?"
        val contextWithoutHistory = ChatContext(project = mockProject, history = emptyList())
        val contextWithHistory = ChatContext(
            project = mockProject,
            history = listOf(
                ru.marslab.ide.ride.model.chat.Message(
                    content = "Предыдущий вопрос",
                    role = ru.marslab.ide.ride.model.chat.MessageRole.USER
                )
            )
        )

        val resultWithoutHistory = analyzer.analyzeUncertainty(request, contextWithoutHistory)
        val resultWithHistory = analyzer.analyzeUncertainty(request, contextWithHistory)

        // С контекстом сложность должна быть немного выше
        assertTrue(resultWithHistory.score > resultWithoutHistory.score)
        assertTrue(resultWithHistory.detectedFeatures.contains("есть_контекст"))
    }

    @Test
    fun `technical terms should increase complexity`() {
        val request = "Как работает API с базой данных через этот алгоритм?"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertTrue(result.score > 0.3)
        assertTrue(result.reasoning.contains("код"))
    }

    @Test
    fun `multiple questions should increase complexity`() {
        val request = "Как работает этот метод? И где он используется? И почему он медленный?"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertTrue(result.score > 0.3)
        assertTrue(result.complexity != ComplexityLevel.SIMPLE)
    }

    @Test
    fun `very short common question should be simple`() {
        val request = "Как тебя зовут?"
        val context = ChatContext(project = mockProject, history = emptyList())

        val result = analyzer.analyzeUncertainty(request, context)

        assertTrue(result.score < 0.1)
        assertEquals(ComplexityLevel.SIMPLE, result.complexity)
        assertTrue(result.suggestedActions.contains("прямой_ответ"))
    }
}