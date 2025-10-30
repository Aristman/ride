package ru.marslab.ide.ride.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class PluginSettingsRagValidationTest {

    @Test
    fun `defaults are applied for RAG params`() {
        val settings = PluginSettings()
        // Проверяем дефолты согласно Phase 1: topK=5, candidateK=60
        assertEquals(PluginSettingsState.DEFAULT_RAG_TOP_K, settings.ragTopK)
        assertEquals(PluginSettingsState.DEFAULT_RAG_CANDIDATE_K, settings.ragCandidateK)
    }

    @Test
    fun `ragTopK is coerced to strict range`() {
        val settings = PluginSettings()
        settings.ragTopK = 0
        assertEquals(PluginSettingsState.RAG_TOP_K_MIN, settings.ragTopK)
        settings.ragTopK = 999
        assertEquals(PluginSettingsState.RAG_TOP_K_MAX, settings.ragTopK)
    }

    @Test
    fun `ragCandidateK is coerced to strict range`() {
        val settings = PluginSettings()
        settings.ragCandidateK = 1
        assertEquals(PluginSettingsState.RAG_CANDIDATE_K_MIN, settings.ragCandidateK)
        settings.ragCandidateK = 1000
        assertEquals(PluginSettingsState.RAG_CANDIDATE_K_MAX, settings.ragCandidateK)
    }
}
