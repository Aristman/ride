package ru.marslab.ide.ride.agent.analyzer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import ru.marslab.ide.ride.settings.PluginSettings
import com.intellij.openapi.components.service

/**
 * Тесты для UncertaintyThresholds
 */
class UncertaintyThresholdsTest {

    @Test
    fun `should use orchestrator when score above threshold`() {
        // Mock настроек
        val mockSettings = mock(PluginSettings::class.java)
        `when`(mockSettings.uncertaintyOrchestratorThreshold).thenReturn(0.7)

        // Создаем mock service
        val mockService = mockStatic(service::class.java)
        mockService.`when`<PluginSettings> { service<PluginSettings>() }.thenReturn(mockSettings)

        val result = UncertaintyResult(
            score = 0.8,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Тест"
        )

        assertTrue(UncertaintyThresholds.shouldUseOrchestrator(result))
        mockService.close()
    }

    @Test
    fun `should use orchestrator for complex queries regardless of score`() {
        val mockSettings = mock(PluginSettings::class.java)
        `when`(mockSettings.uncertaintyOrchestratorThreshold).thenReturn(0.9)

        val mockService = mockStatic(service::class.java)
        mockService.`when`<PluginSettings> { service<PluginSettings>() }.thenReturn(mockSettings)

        val result = UncertaintyResult(
            score = 0.5,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план"),
            reasoning = "Сложный запрос"
        )

        assertTrue(UncertaintyThresholds.shouldUseOrchestrator(result))
        mockService.close()
    }

    @Test
    fun `should not use orchestrator when score below threshold and not complex`() {
        val mockSettings = mock(PluginSettings::class.java)
        `when`(mockSettings.uncertaintyOrchestratorThreshold).thenReturn(0.7)

        val mockService = mockStatic(service::class.java)
        mockService.`when`<PluginSettings> { service<PluginSettings>() }.thenReturn(mockSettings)

        val result = UncertaintyResult(
            score = 0.5,
            complexity = ComplexityLevel.SIMPLE,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Простой запрос"
        )

        assertFalse(UncertaintyThresholds.shouldUseOrchestrator(result))
        mockService.close()
    }

    @Test
    fun `should ask clarifying questions when score above threshold`() {
        val mockSettings = mock(PluginSettings::class.java)
        `when`(mockSettings.uncertaintyClarificationThreshold).thenReturn(0.2)

        val mockService = mockStatic(service::class.java)
        mockService.`when`<PluginSettings> { service<PluginSettings>() }.thenReturn(mockSettings)

        val result = UncertaintyResult(
            score = 0.3,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("контекстный_ответ"),
            reasoning = "Запрос средней сложности"
        )

        assertTrue(UncertaintyThresholds.shouldAskClarifyingQuestions(result))
        mockService.close()
    }

    @Test
    fun `should not ask clarifying questions when score below threshold`() {
        val mockSettings = mock(PluginSettings::class.java)
        `when`(mockSettings.uncertaintyClarificationThreshold).thenReturn(0.5)

        val mockService = mockStatic(service::class.java)
        mockService.`when`<PluginSettings> { service<PluginSettings>() }.thenReturn(mockSettings)

        val result = UncertaintyResult(
            score = 0.3,
            complexity = ComplexityLevel.SIMPLE,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Простой запрос"
        )

        assertFalse(UncertaintyThresholds.shouldAskClarifyingQuestions(result))
        mockService.close()
    }

    @Test
    fun `should identify simple query correctly`() {
        val mockSettings = mock(PluginSettings::class.java)
        `when`(mockSettings.uncertaintyComplexityThreshold).thenReturn(0.3)

        val mockService = mockStatic(service::class.java)
        mockService.`when`<PluginSettings> { service<PluginSettings>() }.thenReturn(mockSettings)

        val simpleResult = UncertaintyResult(
            score = 0.2,
            complexity = ComplexityLevel.SIMPLE,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Простой запрос"
        )

        assertTrue(UncertaintyThresholds.isSimpleQuery(simpleResult))

        val complexResult = UncertaintyResult(
            score = 0.4,
            complexity = ComplexityLevel.SIMPLE,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Запрос выше порога"
        )

        assertFalse(UncertaintyThresholds.isSimpleQuery(complexResult))
        mockService.close()
    }

    @Test
    fun `should use RAG enrichment when conditions met`() {
        val mockSettings = mock(PluginSettings::class.java)
        `when`(mockSettings.uncertaintyRagEnrichmentThreshold).thenReturn(0.5)

        val mockService = mockStatic(service::class.java)
        mockService.`when`<PluginSettings> { service<PluginSettings>() }.thenReturn(mockSettings)

        val result = UncertaintyResult(
            score = 0.6,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("поиск_контекста", "контекстный_ответ"),
            reasoning = "Запрос требует контекста"
        )

        assertTrue(UncertaintyThresholds.shouldUseRAGEnrichment(result))
        mockService.close()
    }

    @Test
    fun `should not use RAG enrichment when conditions not met`() {
        val mockSettings = mock(PluginSettings::class.java)
        `when`(mockSettings.uncertaintyRagEnrichmentThreshold).thenReturn(0.7)

        val mockService = mockStatic(service::class.java)
        mockService.`when`<PluginSettings> { service<PluginSettings>() }.thenReturn(mockSettings)

        val result = UncertaintyResult(
            score = 0.6,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("контекстный_ответ"), // Нет "поиск_контекста"
            reasoning = "Запрос без явного поиска контекста"
        )

        assertFalse(UncertaintyThresholds.shouldUseRAGEnrichment(result))
        mockService.close()
    }
}