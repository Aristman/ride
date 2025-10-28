package ru.marslab.ide.ride.agent

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.impl.*
import ru.marslab.ide.ride.settings.PluginSettings

/**
 * Фабрика для создания LLM провайдеров на основе настроек плагина
 *
 * Выносит дублирующийся код создания провайдеров в отдельный класс
 */
object LLMProviderFactory {

    /**
     * Создает LLM Provider на основе настроек плагина
     *
     * @return Настроенный LLM Provider
     */
    fun createLLMProvider(): LLMProvider {
        val settings = service<PluginSettings>()

        return when (settings.selectedProvider) {
            PluginSettings.PROVIDER_YANDEX -> {
                val apiKey = settings.getApiKey()
                val folderId = settings.folderId
                val modelId = settings.yandexModelId
                createYandexGPTProvider(apiKey, folderId, modelId)
            }

            PluginSettings.PROVIDER_HUGGINGFACE -> {
                val hfToken = settings.getHuggingFaceToken()
                val modelId = settings.huggingFaceModelId
                createHuggingFaceProvider(hfToken, modelId)
            }

            else -> {
                // По умолчанию Yandex
                val apiKey = settings.getApiKey()
                val folderId = settings.folderId
                val modelId = settings.yandexModelId
                createYandexGPTProvider(apiKey, folderId, modelId)
            }
        }
    }

    /**
     * Создает LLM Provider для заданного провайдера и модели.
     * Используется для выбора отдельной LLM под эмбеддинги (страница Code Settings).
     */
    fun createLLMProviderFor(provider: String, modelId: String): LLMProvider {
        val settings = service<PluginSettings>()
        return when (provider) {
            PluginSettings.PROVIDER_YANDEX -> {
                val apiKey = settings.getApiKey()
                val folderId = settings.folderId
                createYandexGPTProvider(apiKey, folderId, modelId)
            }
            PluginSettings.PROVIDER_HUGGINGFACE -> {
                val hfToken = settings.getHuggingFaceToken()
                createHuggingFaceProvider(hfToken, modelId)
            }
            else -> {
                // По умолчанию Yandex
                val apiKey = settings.getApiKey()
                val folderId = settings.folderId
                createYandexGPTProvider(apiKey, folderId, modelId)
            }
        }
    }

    /**
     * Создает провайдер Yandex GPT с указанными настройками
     *
     * @param apiKey API ключ для Yandex GPT
     * @param folderId ID папки в Yandex Cloud
     * @param modelId ID модели Yandex GPT
     * @return Настроенный YandexGPTProvider
     */
    fun createYandexGPTProvider(apiKey: String, folderId: String, modelId: String): LLMProvider {
        val config = YandexGPTConfig(
            apiKey = apiKey,
            folderId = folderId,
            modelId = modelId
        )
        return YandexGPTProvider(config)
    }

    /**
     * Создает провайдер Hugging Face с указанной моделью
     *
     * @param apiKey Токен Hugging Face (Bearer)
     * @param modelId Идентификатор модели. Если не указан, используется модель по умолчанию
     * @return Настроенный HuggingFaceProvider
     */
    fun createHuggingFaceProvider(
        apiKey: String,
        modelId: String = HuggingFaceModel.DEEPSEEK_R1.modelId
    ): LLMProvider {
        val config = HuggingFaceConfig(
            apiKey = apiKey,
            model = modelId
        )
        return HuggingFaceProvider(config)
    }

    /**
     * Создает LLM Provider для эмбеддингов с использованием локальной Ollama модели
     */
    fun createEmbeddingProvider(): LLMProvider {
        val config = OllamaConfig(
            baseUrl = "http://localhost:11434",
            model = "nomic-embed-text:latest",
            timeoutSeconds = 30
        )
        return OllamaEmbeddingProvider(config)
    }
}