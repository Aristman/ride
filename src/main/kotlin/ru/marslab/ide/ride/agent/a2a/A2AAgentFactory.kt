package ru.marslab.ide.ride.agent.a2a

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.agent.impl.EnhancedChatAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.settings.PluginSettings

/**
 * Расширенная фабрика для создания A2A агентов
 *
 * Обеспечивает создание A2A совместимых агентов с автоматической адаптацией
 * legacy агентов и поддержкой feature flags для безопасной миграции.
 */
object A2AAgentFactory {

    /**
     * Создает A2A агент с автоматическим определением необходимости адаптации
     *
     * @param legacyAgent Legacy агент для адаптации
     * @param forceA2A Принудительно создавать A2A агент (игнорирует feature flags)
     * @return A2AAgent (оригинальный или адаптированный)
     */
    suspend fun createA2AAgent(
        legacyAgent: Agent,
        forceA2A: Boolean = false
    ): A2AAgent {
        // Проверяем feature flag
        if (!forceA2A && !A2AConfigUtil.shouldUseA2A(legacyAgent.agentType.name)) {
            throw IllegalStateException("A2A mode is disabled for agent type: ${legacyAgent.agentType}")
        }

        // Если агент уже поддерживает A2A, возвращаем его
        if (legacyAgent is A2AAgent) {
            return legacyAgent
        }

        // Создаем адаптер для legacy агента
        return A2AAgentAdapter.create(legacyAgent)
    }

    /**
     * Создает A2A совместимый ChatAgent
     *
     * @param forceA2A Принудительно использовать A2A режим
     * @return A2AAgent на основе ChatAgent
     */
    suspend fun createChatAgentA2A(forceA2A: Boolean = false): A2AAgent {
        val legacyAgent = AgentFactory.createChatAgent()
        return createA2AAgent(legacyAgent, forceA2A)
    }

    /**
     * Создает A2A совместимый EnhancedChatAgent
     *
     * @param forceA2A Принудительно использовать A2A режим
     * @return A2AAgent на основе EnhancedChatAgent
     */
    suspend fun createEnhancedChatAgentA2A(forceA2A: Boolean = false): A2AAgent {
        val legacyAgent = AgentFactory.createEnhancedChatAgent()
        return createA2AAgent(legacyAgent, forceA2A)
    }

    /**
     * Создает A2A агент с кастомным провайдером
     *
     * @param llmProvider LLM провайдер
     * @param forceA2A Принудительно использовать A2A режим
     * @return A2AAgent с указанным провайдером
     */
    suspend fun createChatAgentA2A(
        llmProvider: LLMProvider,
        forceA2A: Boolean = false
    ): A2AAgent {
        val legacyAgent = AgentFactory.createChatAgent(llmProvider)
        return createA2AAgent(legacyAgent, forceA2A)
    }

    /**
     * Создает и автоматически регистрирует A2A агент
     *
     * @param legacyAgent Legacy агент для адаптации
     * @param context Контекст выполнения для инициализации
     * @param forceA2A Принудительно использовать A2A режим
     * @return Зарегистрированный A2AAgent
     */
    suspend fun createAndRegisterA2AAgent(
        legacyAgent: Agent,
        context: ExecutionContext = ExecutionContext.Empty,
        forceA2A: Boolean = false
    ): A2AAgent {
        val a2aAgent = createA2AAgent(legacyAgent, forceA2A)

        // Регистрируем агент
        val registry = A2AAgentRegistry.getInstance()
        val registered = registry.registerAgent(a2aAgent)

        if (!registered) {
            throw IllegalStateException("Failed to register A2A agent: ${a2aAgent.a2aAgentId}")
        }

        return a2aAgent
    }

    /**
     * Создает A2A агент с полной конфигурацией и настройками
     *
     * @param agentType Тип агента для создания
     * @param llmProvider LLM провайдер (опционально)
     * @param a2aAgentId Идентификатор A2A агента (опционально)
     * @param supportedMessageTypes Поддерживаемые типы сообщений (опционально)
     * @param forceA2A Принудительно использовать A2A режим
     * @return Сконфигурированный A2AAgent
     */
    suspend fun createConfiguredA2AAgent(
        agentType: ru.marslab.ide.ride.model.orchestrator.AgentType,
        llmProvider: LLMProvider? = null,
        a2aAgentId: String? = null,
        supportedMessageTypes: Set<String>? = null,
        publishedEventTypes: Set<String>? = null,
        forceA2A: Boolean = false
    ): A2AAgent {
        // Создаем legacy агент в зависимости от типа
        val legacyAgent = when (agentType) {
            ru.marslab.ide.ride.model.orchestrator.AgentType.CHAT -> {
                llmProvider?.let { AgentFactory.createChatAgent(it) }
                    ?: AgentFactory.createChatAgent()
            }
            ru.marslab.ide.ride.model.orchestrator.AgentType.ENHANCED_CHAT -> {
                llmProvider?.let { AgentFactory.createChatAgent(it) }
                    ?: AgentFactory.createEnhancedChatAgent()
            }
            ru.marslab.ide.ride.model.orchestrator.AgentType.TERMINAL -> {
                AgentFactory.createTerminalAgent()
            }
            else -> {
                throw IllegalArgumentException("Unsupported agent type for A2A: $agentType")
            }
        }

        // Создаем адаптер с кастомной конфигурацией
        val adapter = A2AAgentAdapter(
            legacyAgent = legacyAgent,
            a2aAgentId = a2aAgentId ?: "${agentType.name}_${legacyAgent.hashCode()}",
            supportedMessageTypes = supportedMessageTypes ?: setOf(
                "AGENT_REQUEST",
                "AGENT_RESPONSE",
                "EXECUTION_STATUS"
            ),
            publishedEventTypes = publishedEventTypes ?: setOf(
                "EXECUTION_STARTED",
                "EXECUTION_COMPLETED",
                "EXECUTION_FAILED"
            )
        )

        // Проверяем feature flag
        if (!forceA2A && !A2AConfigUtil.shouldUseA2A(agentType.name)) {
            throw IllegalStateException("A2A mode is disabled for agent type: $agentType")
        }

        return adapter
    }

    /**
     * Создает A2A агент и сразу инициализирует его
     *
     * @param agentType Тип агента
     * @param context Контекст выполнения
     * @param llmProvider LLM провайдер (опционально)
     * @param forceA2A Принудительно использовать A2A режим
     * @return Проинициализированный A2AAgent
     */
    suspend fun createAndInitializeA2AAgent(
        agentType: ru.marslab.ide.ride.model.orchestrator.AgentType,
        context: ExecutionContext = ExecutionContext.Empty,
        llmProvider: LLMProvider? = null,
        forceA2A: Boolean = false
    ): A2AAgent {
        val a2aAgent = createConfiguredA2AAgent(
            agentType = agentType,
            llmProvider = llmProvider,
            forceA2A = forceA2A
        )

        // Создаем MessageBus
        val messageBus = InMemoryMessageBus()

        // Инициализируем агент
        a2aAgent.initializeA2A(messageBus, context)

        return a2aAgent
    }

    /**
     * Проверяет, может ли агент быть автоматически преобразован в A2A
     *
     * @param agentType Тип агента для проверки
     * @return true если агент поддерживает A2A преобразование
     */
    fun canConvertToA2A(agentType: ru.marslab.ide.ride.model.orchestrator.AgentType): Boolean {
        return A2AConfigUtil.shouldUseA2A(agentType.name) && when (agentType) {
            ru.marslab.ide.ride.model.orchestrator.AgentType.CHAT,
            ru.marslab.ide.ride.model.orchestrator.AgentType.ENHANCED_CHAT,
            ru.marslab.ide.ride.model.orchestrator.AgentType.TERMINAL -> true
            else -> false
        }
    }

    /**
     * Получает статистику по A2A агентам
     */
    suspend fun getA2AStatistics(): A2AStatistics {
        val registry = A2AAgentRegistry.getInstance()
        val config = A2AConfig.getInstance()

        return A2AStatistics(
            a2aEnabled = config.isA2AEnabled(),
            totalAgents = registry.getRegisteredAgentsCount(),
            activeAgents = registry.getAllAgents().count {
                registry.isAgentActive(it.a2aAgentId)
            },
            metrics = registry.getAllMetrics(),
            allowedTypes = config.state.allowedAgentTypes,
            blockedTypes = config.state.blockedAgentTypes
        )
    }

    /**
     * Статистика A2A системы
     */
    data class A2AStatistics(
        val a2aEnabled: Boolean,
        val totalAgents: Int,
        val activeAgents: Int,
        val metrics: Map<String, A2AAgentRegistry.AgentMetrics>,
        val allowedTypes: Set<String>,
        val blockedTypes: Set<String>
    )
}