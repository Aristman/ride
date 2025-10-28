package ru.marslab.ide.ride.integration.llm.impl

import kotlinx.serialization.Serializable

/**
 * Конфигурация для Ollama Provider
 */
@Serializable
data class OllamaConfig(
    val baseUrl: String = "http://localhost:11434",
    val model: String = "nomic-embed-text",
    val timeoutSeconds: Int = 30
)

/**
 * Доступные модели Ollama для эмбеддингов
 */
enum class OllamaEmbeddingModel(
    val modelId: String,
    val displayName: String,
    val dimensions: Int
) {
    NOMIC_EMBED_TEXT(
        modelId = "nomic-embed-text",
        displayName = "Nomic Embed Text",
        dimensions = 768
    ),
    ALL_MINILM(
        modelId = "all-minilm",
        displayName = "All MiniLM",
        dimensions = 384
    );

    companion object {
        fun fromModelId(modelId: String): OllamaEmbeddingModel? {
            return entries.find { it.modelId == modelId }
        }

        fun getDefault(): OllamaEmbeddingModel = NOMIC_EMBED_TEXT
    }
}