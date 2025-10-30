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
    var ragMmrTopK: Int = DEFAULT_RAG_MMR_TOP_K
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
        const val DEFAULT_PROVIDER = "YandexGPT"
        const val DEFAULT_MAX_CONTEXT_TOKENS = 8000

        // RAG defaults (согласованы в roadmap)
        const val DEFAULT_RAG_TOP_K = 5
        const val DEFAULT_RAG_CANDIDATE_K = 30
        const val DEFAULT_RAG_RERANKER_STRATEGY = "THRESHOLD"
        const val DEFAULT_RAG_SIMILARITY_THRESHOLD = 0.25f
        // MMR defaults
        const val DEFAULT_RAG_MMR_LAMBDA = 0.5f
        const val DEFAULT_RAG_MMR_TOP_K = DEFAULT_RAG_TOP_K
    }
}
