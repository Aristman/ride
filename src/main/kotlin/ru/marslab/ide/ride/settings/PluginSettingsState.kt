package ru.marslab.ide.ride.settings

/**
 * State класс для хранения настроек плагина
 *
 * Используется для персистентности настроек между сессиями IDE
 */
data class PluginSettingsState(
    var folderId: String = "",
    var temperature: Double = 0.3,
    var maxTokens: Int = 2000,
    var chatFontSize: Int = DEFAULT_CHAT_FONT_SIZE,
    var chatPrefixColor: String = DEFAULT_PREFIX_COLOR,
    var chatCodeBackgroundColor: String = DEFAULT_CODE_BACKGROUND_COLOR,
    var chatCodeTextColor: String = DEFAULT_CODE_TEXT_COLOR,
    var chatCodeBorderColor: String = DEFAULT_CODE_BORDER_COLOR,
    var chatUserBackgroundColor: String = DEFAULT_USER_BACKGROUND_COLOR,
    var chatUserBorderColor: String = DEFAULT_USER_BORDER_COLOR,
    var yandexModelId: String = DEFAULT_YANDEX_MODEL_ID,
    var huggingFaceModelId: String = DEFAULT_HUGGINGFACE_MODEL_ID,
    var ollamaModelId: String = DEFAULT_OLLAMA_MODEL_ID,
    var ollamaBaseUrl: String = DEFAULT_OLLAMA_BASE_URL,
    var selectedProvider: String = DEFAULT_PROVIDER,
    var showProviderName: Boolean = false,
    var enableUncertaintyAnalysis: Boolean = true,
    var maxContextTokens: Int = DEFAULT_MAX_CONTEXT_TOKENS,
    var enableAutoSummarization: Boolean = true,
    var enableRagEnrichment: Boolean = false,
    // --- RAG Reranker/Filter (THRESHOLD) ---
    var ragTopK: Int = DEFAULT_RAG_TOP_K,
    var ragCandidateK: Int = DEFAULT_RAG_CANDIDATE_K,
    var ragSimilarityThreshold: Float = DEFAULT_RAG_SIMILARITY_THRESHOLD,
    var ragRerankerStrategy: String = DEFAULT_RAG_RERANKER_STRATEGY,
    // --- MMR parameters ---
    var ragMmrLambda: Float = DEFAULT_RAG_MMR_LAMBDA,
    var ragMmrTopK: Int = DEFAULT_RAG_MMR_TOP_K,
    // --- RAG Source Links ---
    var ragSourceLinksEnabled: Boolean = DEFAULT_RAG_SOURCE_LINKS_ENABLED,
    // --- Пороги анализа неопределенности ---
    var uncertaintyComplexityThreshold: Double = DEFAULT_UNCERTAINTY_COMPLEXITY_THRESHOLD,
    var uncertaintyOrchestratorThreshold: Double = DEFAULT_UNCERTAINTY_ORCHESTRATOR_THRESHOLD,
    var uncertaintyClarificationThreshold: Double = DEFAULT_UNCERTAINTY_CLARIFICATION_THRESHOLD,
    var uncertaintyRagEnrichmentThreshold: Double = DEFAULT_UNCERTAINTY_RAG_ENRICHMENT_THRESHOLD,
    var uncertaintyMaxSimpleQueryLength: Int = DEFAULT_UNCERTAINTY_MAX_SIMPLE_QUERY_LENGTH,
    // --- Custom Rules ---
    var enableCustomRules: Boolean = DEFAULT_ENABLE_CUSTOM_RULES,
    // Хранение активных правил (имя файла -> активность)
    var activeGlobalRules: Map<String, Boolean> = emptyMap(),
    var activeProjectRules: Map<String, Boolean> = emptyMap()
) {
    companion object {
        const val DEFAULT_CHAT_FONT_SIZE = 9
        const val DEFAULT_PREFIX_COLOR = "#6b6b6b"
        const val DEFAULT_CODE_BACKGROUND_COLOR = "#2b2b2b"
        const val DEFAULT_CODE_TEXT_COLOR = "#e6e6e6"
        const val DEFAULT_CODE_BORDER_COLOR = "#444444"
        const val DEFAULT_USER_BACKGROUND_COLOR = "#28292D"
        const val DEFAULT_USER_BORDER_COLOR = "#6d8fd8"
        const val DEFAULT_YANDEX_MODEL_ID = "yandexgpt"
        const val DEFAULT_HUGGINGFACE_MODEL_ID = "deepseek-ai/DeepSeek-R1:fireworks-ai"
        const val DEFAULT_OLLAMA_MODEL_ID = "llama3:8b"
        const val DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"
        const val DEFAULT_PROVIDER = "YandexGPT"
        const val DEFAULT_MAX_CONTEXT_TOKENS = 8000

        // RAG defaults (согласованы в roadmap)
        const val DEFAULT_RAG_TOP_K = 5
        const val DEFAULT_RAG_CANDIDATE_K = 60
        const val DEFAULT_RAG_RERANKER_STRATEGY = "THRESHOLD"
        const val DEFAULT_RAG_SIMILARITY_THRESHOLD = 0.25f
        // RAG ranges (Phase 1 strict validation)
        const val RAG_TOP_K_MIN = 1
        const val RAG_TOP_K_MAX = 10
        const val RAG_CANDIDATE_K_MIN = 30
        const val RAG_CANDIDATE_K_MAX = 100
        // MMR defaults
        const val DEFAULT_RAG_MMR_LAMBDA = 0.5f
        const val DEFAULT_RAG_MMR_TOP_K = DEFAULT_RAG_TOP_K
        // RAG Source Links defaults
        const val DEFAULT_RAG_SOURCE_LINKS_ENABLED = false

        // Пороги анализа неопределенности defaults
        const val DEFAULT_UNCERTAINTY_COMPLEXITY_THRESHOLD = 0.3
        const val DEFAULT_UNCERTAINTY_ORCHESTRATOR_THRESHOLD = 0.7
        const val DEFAULT_UNCERTAINTY_CLARIFICATION_THRESHOLD = 0.2
        const val DEFAULT_UNCERTAINTY_RAG_ENRICHMENT_THRESHOLD = 0.5
        const val DEFAULT_UNCERTAINTY_MAX_SIMPLE_QUERY_LENGTH = 100

        // Custom Rules defaults
        const val DEFAULT_ENABLE_CUSTOM_RULES = true
    }
}
