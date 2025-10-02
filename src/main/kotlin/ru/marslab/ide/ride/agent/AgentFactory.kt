package ru.marslab.ide.ride.agent

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTConfig
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTProvider
import ru.marslab.ide.ride.settings.PluginSettings

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
        
        // Получаем настройки для Yandex GPT
        val apiKey = settings.getApiKey()
        val folderId = settings.folderId
        
        // Создаем провайдер
        val llmProvider = createYandexGPTProvider(apiKey, folderId)
        
        // Создаем агента с провайдером
        return ChatAgent(
            llmProvider = llmProvider,
            systemPrompt = settings.systemPrompt
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
     * Создает провайдер Yandex GPT с указанными настройками
     */
    private fun createYandexGPTProvider(apiKey: String, folderId: String): LLMProvider {
        val config = YandexGPTConfig(
            apiKey = apiKey,
            folderId = folderId
        )
        return YandexGPTProvider(config)
    }
}
