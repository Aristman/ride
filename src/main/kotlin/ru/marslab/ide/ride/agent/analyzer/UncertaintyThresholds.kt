package ru.marslab.ide.ride.agent.analyzer

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.settings.PluginSettings

/**
 * Конфигурируемые пороги для анализа неопределенности
 */
object UncertaintyThresholds {

    /** Порог для перехода от простых запросов к сложным */
    val complexityThreshold: Double get() = getSettings().uncertaintyComplexityThreshold

    /** Порог для использования оркестратора */
    val orchestratorThreshold: Double get() = getSettings().uncertaintyOrchestratorThreshold

    /** Порог для запроса уточняющих вопросов */
    val clarificationThreshold: Double get() = getSettings().uncertaintyClarificationThreshold

    /** Порог для RAG обогащения */
    val ragEnrichmentThreshold: Double get() = getSettings().uncertaintyRagEnrichmentThreshold

    /** Максимальная длина простого запроса */
    val maxSimpleQueryLength: Int get() = getSettings().uncertaintyMaxSimpleQueryLength

    /** Минимальная уверенность для прямого ответа */
    const val MIN_CONFIDENCE_FOR_DIRECT_ANSWER = 0.9

    private fun getSettings(): PluginSettings = service()

    /**
     * Проверяет, нужно ли использовать оркестратор для запроса
     */
    fun shouldUseOrchestrator(uncertaintyResult: UncertaintyResult): Boolean {
        return uncertaintyResult.score >= orchestratorThreshold ||
               uncertaintyResult.complexity == ComplexityLevel.COMPLEX
    }

    /**
     * Проверяет, нужно ли задавать уточняющие вопросы
     */
    fun shouldAskClarifyingQuestions(uncertaintyResult: UncertaintyResult): Boolean {
        return uncertaintyResult.score > clarificationThreshold
    }

    /**
     * Проверяет, является ли запрос простым
     */
    fun isSimpleQuery(uncertaintyResult: UncertaintyResult): Boolean {
        return uncertaintyResult.complexity == ComplexityLevel.SIMPLE &&
               uncertaintyResult.score < complexityThreshold
    }

    /**
     * Проверяет, нужно ли использовать RAG обогащение
     */
    fun shouldUseRAGEnrichment(uncertaintyResult: UncertaintyResult): Boolean {
        return uncertaintyResult.score >= ragEnrichmentThreshold &&
               uncertaintyResult.suggestedActions.contains("поиск_контекста")
    }
}