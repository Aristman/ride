package ru.marslab.ide.ride.agent

import com.intellij.openapi.project.Project
import io.mockk.mockk
import ru.marslab.ide.ride.model.chat.ChatContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UncertaintyAnalyzerTest {

    private val mockProject = mockk<Project>(relaxed = true)
    private val testContext = ChatContext(
        project = mockProject,
        history = emptyList(),
        currentFile = null,
        selectedText = null
    )

    @Test
    fun `test analyze uncertainty with certain response`() {
        val response = "Вот решение вашей проблемы: используйте паттерн Factory для создания объектов."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertEquals(0.0, uncertainty, "Certain response should have zero uncertainty")
    }

    @Test
    fun `test analyze uncertainty with uncertain response`() {
        val response = "Я не уверен в правильном решении, нужна дополнительная информация о вашем проекте."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty > 0.0, "Uncertain response should have positive uncertainty")
        assertTrue(uncertainty > 0.1, "Response with explicit uncertainty should have high uncertainty")
    }

    @Test
    fun `test analyze uncertainty with questions`() {
        val response = "Какую версию IntelliJ IDEA вы используете? Какой у вас проект?"

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty > 0.0, "Response with questions should have positive uncertainty")
    }

    @Test
    fun `test is final response with low uncertainty`() {
        assertTrue(UncertaintyAnalyzer.isFinalResponse(0.0), "Zero uncertainty should be final")
        assertTrue(UncertaintyAnalyzer.isFinalResponse(0.05), "Low uncertainty should be final")
        assertTrue(UncertaintyAnalyzer.isFinalResponse(0.1), "Threshold uncertainty should be final")
    }

    @Test
    fun `test is final response with high uncertainty`() {
        assertFalse(UncertaintyAnalyzer.isFinalResponse(0.2), "High uncertainty should not be final")
        assertFalse(UncertaintyAnalyzer.isFinalResponse(0.5), "Very high uncertainty should not be final")
        assertFalse(UncertaintyAnalyzer.isFinalResponse(1.0), "Maximum uncertainty should not be final")
    }

    @Test
    fun `test has explicit uncertainty`() {
        assertTrue(
            UncertaintyAnalyzer.hasExplicitUncertainty("Я не уверен в ответе"),
            "Should detect explicit uncertainty"
        )
        assertTrue(
            UncertaintyAnalyzer.hasExplicitUncertainty("Нужна дополнительная информация"),
            "Should detect need for information"
        )
        assertTrue(
            UncertaintyAnalyzer.hasExplicitUncertainty("Уточните, пожалуйста детали"),
            "Should detect clarification request"
        )
        assertFalse(
            UncertaintyAnalyzer.hasExplicitUncertainty("Вот решение вашей задачи"),
            "Should not detect uncertainty in certain response"
        )
    }

    @Test
    fun `test extract clarifying questions`() {
        val response = """
            Чтобы помочь вам лучше, уточните несколько деталей:
            Какую версию технологии вы используете?
            Какие у вас требования к производительности?

            Давайте найдем решение вместе.
        """.trimIndent()

        val questions = UncertaintyAnalyzer.extractClarifyingQuestions(response)

        assertEquals(2, questions.size, "Should extract 2 questions")
        assertTrue(questions.contains("Какую версию технологии вы используете?"))
        assertTrue(questions.contains("Какие у вас требования к производительности?"))
    }

    @Test
    fun `test extract clarifying questions with mixed content`() {
        val response = """
            Я могу предложить несколько решений, но сначала:
            Какой у вас уровень опыта в программировании?
            Нужно ли учитывать особые требования?

            После уточнения этих деталей я дам более точный ответ.
        """.trimIndent()

        val questions = UncertaintyAnalyzer.extractClarifyingQuestions(response)

        assertEquals(2, questions.size, "Should extract questions from mixed content")
        assertTrue(questions.any { it.contains("уровень опыта") })
        assertTrue(questions.any { it.contains("особые требования") })
    }

    @Test
    fun `test short response uncertainty`() {
        val shortResponse = "Не уверен."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(shortResponse, testContext)

        assertTrue(uncertainty > 0.0, "Short response should have some uncertainty")
    }

    @Test
    fun `test uncertainty words detection`() {
        val response = "Возможно, вам поможет это решение. Вероятно, проблема в конфигурации."

        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(response, testContext)

        assertTrue(uncertainty > 0.0, "Response with uncertainty words should have positive uncertainty")
    }
}