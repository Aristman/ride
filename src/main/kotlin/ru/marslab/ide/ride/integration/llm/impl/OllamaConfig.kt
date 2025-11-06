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

/**
 * Доступные модели Ollama для генерации текста
 */
enum class OllamaChatModel(
    val modelId: String,
    val displayName: String
) {
    LLAMA3_8B(
        modelId = "llama3:8b",
        displayName = "Llama 3 8B"
    ),
    LLAMA3_8B_INSTRUCT(
        modelId = "llama3:8b-instruct",
        displayName = "Llama 3 8B Instruct"
    ),
    MISTRAL_7B(
        modelId = "mistral:7b",
        displayName = "Mistral 7B"
    ),
    QWEN_7B(
        modelId = "qwen:7b",
        displayName = "Qwen 7B"
    );

    companion object {
        fun fromModelId(modelId: String): OllamaChatModel? {
            return entries.find { it.modelId == modelId }
        }

        fun getDefault(): OllamaChatModel = LLAMA3_8B
    }
}