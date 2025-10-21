package ru.marslab.ide.ride.agent

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.agent.impl.EnhancedChatAgent
import ru.marslab.ide.ride.agent.impl.TerminalAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTConfig
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTProvider
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceConfig
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceProvider
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceModel
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.model.agent.AgentSettings
import ru.marslab.ide.ride.model.schema.ResponseFormat
import ru.marslab.ide.ride.model.schema.ResponseSchema

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
        val llmProvider = createLLMProvider()

        // Создаем агента с провайдером
        val agent = ChatAgent(
            initialProvider = llmProvider,
//            systemPrompt = settings.systemPrompt
        )

        // Применяем настройки
        val settings = service<PluginSettings>()
        val agentSettings = AgentSettings(
            llmProvider = llmProvider.getProviderName(),
            defaultResponseFormat = ResponseFormat.XML,
            mcpEnabled = false
        )
        agent.updateSettings(agentSettings)

        return agent
    }

    /**
     * Создает EnhancedChatAgent с автоматическим определением сложности задач
     * 
     * EnhancedChatAgent автоматически выбирает:
     * - Простые вопросы → базовый ChatAgent
     * - Сложные задачи → EnhancedAgentOrchestrator
     * 
     * @return Настроенный EnhancedChatAgent
     */
    fun createEnhancedChatAgent(): Agent {
        val llmProvider = createLLMProvider()

        // Создаем EnhancedChatAgent через фабричный метод
        val agent = EnhancedChatAgent.create(llmProvider)

        // Применяем настройки
        val settings = service<PluginSettings>()
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
        return LLMProviderFactory.createYandexGPTProvider(apiKey, folderId, modelId)
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
        return LLMProviderFactory.createHuggingFaceProvider(apiKey, modelId)
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
        return LLMProviderFactory.createHuggingFaceProvider(apiKey, model)
    }

    /**
     * Создаёт агента с провайдером Hugging Face
     * Использует токен и модель из настроек плагина
     */
    fun createChatAgentHuggingFace(): Agent {
        val settings = service<PluginSettings>()
        val hfToken = settings.getHuggingFaceToken()
        val modelId = settings.huggingFaceModelId
        val provider = LLMProviderFactory.createHuggingFaceProvider(hfToken, modelId)
        return ChatAgent(initialProvider = provider)
    }

    /**
     * Создает LLM Provider на основе настроек плагина
     *
     * @return Настроенный LLM Provider
     */
    fun createLLMProvider(): LLMProvider {
        return LLMProviderFactory.createLLMProvider()
    }

    /**
     * Создает AgentOrchestrator с настроенным LLM провайдером из настроек плагина
     *
     * @return Настроенный оркестратор
     */
    fun createAgentOrchestrator(): AgentOrchestrator {
        val llmProvider = createLLMProvider()
        return AgentOrchestrator(llmProvider, llmProvider)
    }

    /**
     * Создает терминальный агент для выполнения локальных команд
     *
     * @return TerminalAgent готовый к использованию
     */
    fun createTerminalAgent(): Agent {
        return TerminalAgent()
    }

    /**
     * Создает агента для анализа кода
     *
     * @param project Проект для анализа
     * @return CodeAnalysisAgent готовый к использованию
     */
    fun createCodeAnalysisAgent(project: com.intellij.openapi.project.Project): ru.marslab.ide.ride.codeanalysis.CodeAnalysisAgent {
        val llmProvider = createLLMProvider()
        return ru.marslab.ide.ride.codeanalysis.impl.CodeAnalysisAgentImpl(project, llmProvider)
    }
}
