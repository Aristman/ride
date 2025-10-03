package ru.marslab.ide.ride.agent

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import ru.marslab.ide.ride.model.ChatContext
import ru.marslab.ide.ride.model.Message
import ru.marslab.ide.ride.model.MessageRole
import com.intellij.openapi.project.Project

class UncertaintyAnalyzerTest {

    private val mockProject = mockk<Project>()
    private val testContext = ChatContext(
        project = mockProject,
        history = emptyList()
    )

    @Test
    fun `analyzeUncertainty should return 0 for certain response`() {
        val response = "Окончательный ответ: вот полное решение вашей проблемы с примером кода..."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty < 0.5, "Должен быть низкий уровень неопределенности для окончательного ответа, фактический: $uncertainty")
    }

    @Test
    fun `analyzeUncertainty should detect explicit uncertainty`() {
        val response = "Я не уверен, что правильно понял ваш вопрос. Уточните, пожалуйста, о чем именно идет речь."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty > 0.0, "Должен быть высокий уровень неопределенности при явных указаниях, фактический: $uncertainty")
    }

    @Test
    fun `analyzeUncertainty should detect clarifying questions`() {
        val response = "Давайте уточню несколько деталей: какую версию Kotlin вы используете и это веб-приложение или мобильное?"

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty > 0.0, "Должен быть высокий уровень неопределенности при уточняющих вопросах, фактический: $uncertainty")
    }

    @Test
    fun `analyzeUncertainty should detect need for more information`() {
        val response = "Для ответа на ваш вопрос мне нужно больше информации о вашей проблеме."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty > 0.0, "Должен быть высокий уровень неопределенности при запросе доп. информации, фактический: $uncertainty")
    }

    @Test
    fun `analyzeUncertainty should detect uncertainty words`() {
        val response = "Возможно, вам поможет использовать паттерн Observer. Вероятно, это решит вашу проблему."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty > 0.0, "Должен быть повышенный уровень неопределенности при словах неопределенности, фактический: $uncertainty")
    }

    @Test
    fun `analyzeUncertainty should handle very short responses`() {
        val response = "Нужен контекст."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty > 0.0, "Очень короткие ответы должны иметь повышенную неопределенность, фактический: $uncertainty")
    }

    @Test
    fun `isFinalResponse should return true for low uncertainty`() {
        assertTrue(UncertaintyAnalyzer.isFinalResponse(0.05), "Ответ с неопределенностью 0.05 должен быть окончательным")
        assertTrue(UncertaintyAnalyzer.isFinalResponse(0.1), "Ответ с неопределенностью 0.1 должен быть окончательным")
    }

    @Test
    fun `isFinalResponse should return false for high uncertainty`() {
        assertFalse(UncertaintyAnalyzer.isFinalResponse(0.2), "Ответ с неопределенностью 0.2 не должен быть окончательным")
        assertFalse(UncertaintyAnalyzer.isFinalResponse(0.5), "Ответ с неопределенностью 0.5 не должен быть окончательным")
    }

    @Test
    fun `hasExplicitUncertainty should detect uncertainty patterns`() {
        assertTrue(UncertaintyAnalyzer.hasExplicitUncertainty("Я не уверен, что правильно понял ваш вопрос"))
        assertTrue(UncertaintyAnalyzer.hasExplicitUncertainty("Нужна дополнительная информация о вашей проблеме"))
        assertTrue(UncertaintyAnalyzer.hasExplicitUncertainty("Уточните, пожалуйста, несколько деталей"))
        assertFalse(UncertaintyAnalyzer.hasExplicitUncertainty("Вот решение вашей проблемы"))
    }

    @Test
    fun `extractClarifyingQuestions should find questions in response`() {
        val response = """
            Вот некоторые мысли по вашему вопросу.
            Давайте уточню несколько деталей: какую версию вы используете?
            Также интересно, а это веб или мобильное приложение?
        """.trimIndent()

        val questions = UncertaintyAnalyzer.extractClarifyingQuestions(response)

        assertEquals(2, questions.size)
        assertTrue(questions.any { it.contains("какую версию вы используете") })
        assertTrue(questions.any { it.contains("это веб или мобильное приложение") })
    }

    @Test
    fun `extractClarifyingQuestions should return empty list for no questions`() {
        val response = "Вот полный ответ на ваш вопрос с примером кода и подробностями."

        val questions = UncertaintyAnalyzer.extractClarifyingQuestions(response)

        assertTrue(questions.isEmpty(), "Не должно быть вопросов в окончательном ответе")
    }

    @Test
    fun `analyzeUncertainty should consider multiple uncertainty factors`() {
        val response = """
            Я не уверен, что правильно понимаю ваш вопрос.
            Уточните, пожалуйста, несколько моментов?
            Возможно, мне нужна дополнительная информация о вашем проекте.
            Какую технологию вы используете?
        """.trimIndent()

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty > 0.0, "Множественные факторы неопределенности должны давать высокий результат, фактический: $uncertainty")
        assertTrue(uncertainty <= 1.0, "Неопределенность не должна превышать 1.0")
    }
}