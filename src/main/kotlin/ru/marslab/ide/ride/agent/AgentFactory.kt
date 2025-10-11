package ru.marslab.ide.ride.agent

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTConfig
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTProvider
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceConfig
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceProvider
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceModel
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.model.AgentSettings
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

        // Создаем агента с провайдером
        val agent = ChatAgent(
            initialProvider = llmProvider,
//            systemPrompt = settings.systemPrompt
        )
        
        // Применяем настройки
        val agentSettings = AgentSettings(
            llmProvider = llmProvider.getProviderName(),
            defaultResponseFormat = ResponseFormat.XML,
            mcpEnabled = false
        )
        agent.updateSettings(agentSettings)
        
        return agent
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
        return ChatAgent(initialProvider = llmProvider)
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
        // Устанавливаем формат через настройки
        val agentSettings = AgentSettings(
            defaultResponseFormat = format
        )
        agent.updateSettings(agentSettings)
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
        val agent = ChatAgent(initialProvider = llmProvider)
        // Устанавливаем формат через настройки
        val agentSettings = AgentSettings(
            llmProvider = llmProvider.getProviderName(),
            defaultResponseFormat = format
        )
        agent.updateSettings(agentSettings)
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
     * Создает провайдер Hugging Face с указанной моделью
     *
     * @param apiKey Токен Hugging Face (Bearer)
     * @param modelId Идентификатор модели. Если не указан, используется модель по умолчанию
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
     * Создает провайдер Hugging Face с использованием enum модели
     *
     * @param apiKey Токен Hugging Face (Bearer)
     * @param model Модель из перечисления HuggingFaceModel
     */
    fun createHuggingFaceProvider(
        apiKey: String,
        model: HuggingFaceModel
    ): LLMProvider {
        return createHuggingFaceProvider(apiKey, model.modelId)
    }

    /**
     * Создаёт агента с провайдером Hugging Face
     * Использует токен и модель из настроек плагина
     */
    fun createChatAgentHuggingFace(): Agent {
        val settings = service<PluginSettings>()
        val hfToken = settings.getHuggingFaceToken()
        val modelId = settings.huggingFaceModelId
        val provider = createHuggingFaceProvider(hfToken, modelId)
        return ChatAgent(initialProvider = provider)
    }

    /**
     * Создает AgentOrchestrator с настроенным LLM провайдером из настроек плагина
     * 
     * @return Настроенный оркестратор
     */
    fun createAgentOrchestrator(): AgentOrchestrator {
        val settings = service<PluginSettings>()
        val llmProvider: LLMProvider = when (settings.selectedProvider) {
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
                val apiKey = settings.getApiKey()
                val folderId = settings.folderId
                val modelId = settings.yandexModelId
                createYandexGPTProvider(apiKey, folderId, modelId)
            }
        }
        
        return AgentOrchestrator(llmProvider, llmProvider)
    }
}
