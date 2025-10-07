package ru.marslab.ide.ride.agent

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTConfig
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTProvider
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceConfig
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceDeepSeekR1Provider
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema

/**
 * Фабрика для создания агентов
 */
object AgentFactory {
    
    /**
     * Создает ChatAgent с настроенным LLM провайдером из настроек плагина
     * 
     * @return Настроенный агент
     */
    fun createChatAgent(): Agent {
        val settings = service<PluginSettings>()
        val llmProvider: LLMProvider = when (settings.selectedProvider) {
            PluginSettings.PROVIDER_HF_DEEPSEEK -> {
                val hfToken = settings.getHuggingFaceToken()
                // Модель по умолчанию совпадает с UI и провайдером
                createHuggingFaceDeepSeekR1Provider(hfToken)
            }
            else -> {
                // Получаем настройки для Yandex GPT
                val apiKey = settings.getApiKey()
                val folderId = settings.folderId
                val modelId = settings.yandexModelId
                createYandexGPTProvider(apiKey, folderId, modelId)
            }
        }

        // Создаем агента с провайдером
        return ChatAgent(
            llmProvider = llmProvider,
//            systemPrompt = settings.systemPrompt
        )
    }
    
    /**
     * Создает агента с кастомным провайдером
     * 
     * Полезно для тестов или специальных случаев
     * 
     * @param llmProvider Провайдер для использования
     * @return Агент с указанным провайдером
     */
    fun createChatAgent(llmProvider: LLMProvider): Agent {
        return ChatAgent(llmProvider = llmProvider)
    }

    /**
     * Создает агента с предустановленным форматом ответа и опциональной схемой
     *
     * @param format Формат ответа (JSON, XML, TEXT)
     * @param schema Схема для структурированного ответа (опционально)
     */
    fun createChatAgent(
        format: ResponseFormat,
        schema: ResponseSchema? = null
    ): Agent {
        val agent = createChatAgent()
        agent.setResponseFormat(format, schema)
        return agent
    }

    /**
     * Создает агента с кастомным провайдером и предустановленным форматом
     */
    fun createChatAgent(
        llmProvider: LLMProvider,
        format: ResponseFormat,
        schema: ResponseSchema? = null
    ): Agent {
        val agent = ChatAgent(llmProvider = llmProvider)
        agent.setResponseFormat(format, schema)
        return agent
    }
    
    /**
     * Создает провайдер Yandex GPT с указанными настройками
     */
    private fun createYandexGPTProvider(apiKey: String, folderId: String, modelId: String): LLMProvider {
        val config = YandexGPTConfig(
            apiKey = apiKey,
            folderId = folderId,
            modelId = modelId
        )
        return YandexGPTProvider(config)
    }

    /**
     * Создает провайдер Hugging Face DeepSeek-R1
     *
     * @param apiKey Токен Hugging Face (Bearer)
     * @param model Идентификатор модели, по умолчанию deepseek-ai/DeepSeek-R1:fireworks-ai
     */
    fun createHuggingFaceDeepSeekR1Provider(
        apiKey: String,
        model: String = "deepseek-ai/DeepSeek-R1:fireworks-ai"
    ): LLMProvider {
        val config = HuggingFaceConfig(
            apiKey = apiKey,
            model = model
        )
        return HuggingFaceDeepSeekR1Provider(config)
    }
}
