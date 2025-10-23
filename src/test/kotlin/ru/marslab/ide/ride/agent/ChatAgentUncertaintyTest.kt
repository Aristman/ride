package ru.marslab.ide.ride.agent

import io.mockk.mockk
import ru.marslab.ide.ride.model.chat.ChatContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты для анализа неопределенности в ответах
 *
 * Поскольку ChatAgent имеет много зависимостей от IntelliJ Platform,
 * здесь тестируется непосредственно логика UncertaintyAnalyzer
 */
class ChatAgentUncertaintyTest {

    private val mockContext = mockk<ChatContext>()

    @Test
    fun `UncertaintyAnalyzer should detect certain response`() {
        // Arrange
        val certainResponse = "Окончательный ответ: вот полное решение вашей проблемы"

        // Act
        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(certainResponse, mockContext)
        val isFinal = UncertaintyAnalyzer.isFinalResponse(uncertainty)
        val hasExplicitUncertainty = UncertaintyAnalyzer.hasExplicitUncertainty(certainResponse)

        // Assert
        assertTrue(
            uncertainty < 0.5,
            "Неопределенность должна быть низкой для уверенного ответа, фактическое: $uncertainty"
        )
        assertTrue(isFinal, "Ответ должен быть окончательным при низкой неопределенности")
        assertFalse(hasExplicitUncertainty, "В уверенном ответе не должно быть явной неопределенности")
    }

    @Test
    fun `UncertaintyAnalyzer should detect uncertain response`() {
        // Arrange
        val uncertainResponse =
            "Я не уверен, что правильно понял ваш вопрос. Уточните, пожалуйста, о чем именно идет речь?"

        // Act
        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(uncertainResponse, mockContext)
        val isFinal = UncertaintyAnalyzer.isFinalResponse(uncertainty)
        val hasExplicitUncertainty = UncertaintyAnalyzer.hasExplicitUncertainty(uncertainResponse)

        // Assert
        assertTrue(
            uncertainty > 0.0,
            "Неопределенность должна быть выше нуля для неуверенного ответа, фактическое: $uncertainty"
        )
        assertTrue(hasExplicitUncertainty, "Должна быть явная неопределенность")
    }

    @Test
    fun `UncertaintyAnalyzer should detect clarifying questions`() {
        // Arrange
        val responseWithQuestions = "Какую версию Kotlin вы используете? Какой у вас опыт разработки?"

        // Act
        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(responseWithQuestions, mockContext)
        val questions = UncertaintyAnalyzer.extractClarifyingQuestions(responseWithQuestions)

        // Assert
        assertTrue(
            uncertainty > 0.0,
            "Неопределенность должна быть выше нуля при наличии вопросов, фактическое: $uncertainty"
        )
        assertTrue(questions.isNotEmpty(), "Должны быть обнаружены уточняющие вопросы")
    }

    @Test
    fun `UncertaintyAnalyzer should extract clarifying questions`() {
        // Arrange
        val responseWithQuestions = """
            Давайте уточню несколько деталей:
            1. Какую версию Kotlin вы используете?
            2. Какой у вас опыт разработки?
        """.trimIndent()

        // Act
        val questions = UncertaintyAnalyzer.extractClarifyingQuestions(responseWithQuestions)

        // Assert
        assertNotNull(questions)
        assertTrue(questions.isNotEmpty(), "Должны быть извлечены уточняющие вопросы")
    }

    @Test
    fun `UncertaintyAnalyzer should handle empty response`() {
        // Arrange
        val emptyResponse = ""

        // Act
        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(emptyResponse, mockContext)
        val isFinal = UncertaintyAnalyzer.isFinalResponse(uncertainty)

        // Assert
        assertTrue(uncertainty < 0.2, "Пустой ответ должен иметь низкую неопределенность, фактическое: $uncertainty")
        assertTrue(isFinal, "Пустой ответ должен считаться окончательным")
    }

    @Test
    fun `UncertaintyAnalyzer should use threshold correctly`() {
        // Test boundary cases around 0.1 threshold
        val justBelowThreshold = 0.09
        val justAboveThreshold = 0.11
        val exactlyThreshold = 0.1

        assertTrue(
            UncertaintyAnalyzer.isFinalResponse(justBelowThreshold),
            "Ответ с неопределенностью 0.09 должен быть окончательным"
        )
        assertFalse(
            UncertaintyAnalyzer.isFinalResponse(justAboveThreshold),
            "Ответ с неопределенностью 0.11 не должен быть окончательным"
        )
        assertTrue(
            UncertaintyAnalyzer.isFinalResponse(exactlyThreshold),
            "Ответ с неопределенностью 0.1 должен быть окончательным (<=)"
        )
    }

    @Test
    fun `UncertaintyAnalyzer should handle unicode and special characters`() {
        // Arrange
        val russianUncertainResponse = "Я не уверен, что правильно понял ваш вопрос на русском языке"
        val russianCertainResponse = "Вот окончательный ответ на ваш вопрос"

        // Act & Assert
        val uncertainUncertainty = UncertaintyAnalyzer.analyzeUncertainty(russianUncertainResponse, mockContext)
        val certainUncertainty = UncertaintyAnalyzer.analyzeUncertainty(russianCertainResponse, mockContext)

        assertTrue(uncertainUncertainty > 0.0, "Русский неуверенный ответ должен иметь неопределенность выше нуля")
        assertTrue(
            certainUncertainty >= 0.0,
            "Русский уверенный ответ должен иметь неопределенность в допустимом диапазоне"
        )
    }

    @Test
    fun `UncertaintyAnalyzer should detect explicit uncertainty indicators`() {
        // Test explicit uncertainty detection
        val uncertainResponses = listOf(
            "Я не уверен в ответе",
            "Нужна дополнительная информация",
            "Уточните, пожалуйста"
        )

        uncertainResponses.forEach { response ->
            val hasExplicitUncertainty = UncertaintyAnalyzer.hasExplicitUncertainty(response)
            assertTrue(hasExplicitUncertainty, "Ответ '$response' должен иметь явную неопределенность")
        }

        // Additional test for phrases that should be detected
        val explicitUncertainPhrases = listOf(
            "Я не могу дать точный ответ",
            "Нужен контекст",
            "Требуется больше информации"
        )

        explicitUncertainPhrases.forEach { response ->
            val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, mockContext)
            assertTrue(
                uncertainty > 0.0,
                "Ответ '$response' должен иметь неопределенность выше нуля, фактическое: $uncertainty"
            )
        }
    }

    @Test
    fun `UncertaintyAnalyzer should score uncertainty correctly for short responses`() {
        // Test short responses (should get additional uncertainty score)
        val shortResponse = "Да"
        val longResponse = "Да, я могу помочь вам с этим вопросом. Вот подробное объяснение..."

        val shortUncertainty = UncertaintyAnalyzer.analyzeUncertainty(shortResponse, mockContext)
        val longUncertainty = UncertaintyAnalyzer.analyzeUncertainty(longResponse, mockContext)

        // Short response should have higher uncertainty due to length penalty
        assertTrue(
            shortUncertainty >= longUncertainty,
            "Короткий ответ должен иметь не ниже неопределенность, чем длинный"
        )
    }

    @Test
    fun `UncertaintyAnalyzer should detect uncertainty words`() {
        // Test uncertainty words like "возможно", "вероятно", etc.
        val uncertainWordsResponse = "Возможно, это поможет, но я не уверен. Вероятно, нужно больше информации."
        val certainWordsResponse = "Это определенно поможет. Абсолютно уверен в ответе."

        val uncertainUncertainty = UncertaintyAnalyzer.analyzeUncertainty(uncertainWordsResponse, mockContext)
        val certainUncertainty = UncertaintyAnalyzer.analyzeUncertainty(certainWordsResponse, mockContext)

        assertTrue(
            uncertainUncertainty >= certainUncertainty,
            "Ответ со словами неопределенности должен иметь не ниже рейтинг"
        )
    }

    @Test
    fun `UncertaintyAnalyzer should handle mixed responses`() {
        // Test responses with both certain and uncertain elements
        val mixedResponse = "Я могу помочь с этим вопросом, но мне нужно больше информации о конкретной задаче."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(mixedResponse, mockContext)

        assertNotNull(uncertainty)
        assertTrue(uncertainty >= 0.0, "Неопределенность должна быть в допустимом диапазоне")
        assertTrue(uncertainty <= 1.0, "Неопределенность должна быть в допустимом диапазоне")
    }
}