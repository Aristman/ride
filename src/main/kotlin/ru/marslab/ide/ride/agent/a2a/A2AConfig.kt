package ru.marslab.ide.ride.agent.a2a

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage

/**
 * Конфигурация A2A режима с feature flags
 *
 * Позволяет динамически переключаться между A2A и legacy режимами,
 * что обеспечивает безопасную миграцию и откат при необходимости
 */
@Service(Service.Level.PROJECT)
@Storage("A2AConfig.xml")
class A2AConfig : PersistentStateComponent<A2AConfig.State> {

    data class State(
        var a2aEnabled: Boolean = false,
        var messageBusType: MessageBusType = MessageBusType.IN_MEMORY,
        var maxConcurrentMessages: Int = 100,
        var defaultTimeoutMs: Long = 30000L,
        var enableMetrics: Boolean = true,
        var enableDetailedLogging: Boolean = false,
        var allowedAgentTypes: Set<String> = emptySet(),
        var blockedAgentTypes: Set<String> = emptySet()
    ) {
        // Добавляем вспомогательные методы для удобства
        fun isAgentTypeAllowed(agentType: String): Boolean {
            return when {
                agentType in blockedAgentTypes -> false
                allowedAgentTypes.isEmpty() -> true // Если список разрешенных пуст, разрешены все
                else -> agentType in allowedAgentTypes
            }
        }

        companion object {
            fun forDevelopment(): State {
                return State(
                    a2aEnabled = true,
                    messageBusType = MessageBusType.IN_MEMORY,
                    enableMetrics = true,
                    enableDetailedLogging = true,
                    allowedAgentTypes = setOf(
                        "PROJECT_SCANNER",
                        "LLM_REVIEW",
                        "CODE_QUALITY",
                        "BUG_DETECTION",
                        "EMBEDDING_INDEXER",
                        "REPORT_GENERATOR"
                    )
                )
            }

            fun forProduction(): State {
                return State(
                    a2aEnabled = false, // Отключен по умолчанию в production
                    messageBusType = MessageBusType.IN_MEMORY,
                    maxConcurrentMessages = 50,
                    defaultTimeoutMs = 15000L,
                    enableMetrics = true,
                    enableDetailedLogging = false,
                    allowedAgentTypes = setOf(
                        "PROJECT_SCANNER",
                        "LLM_REVIEW",
                        "CODE_QUALITY"
                    ),
                    blockedAgentTypes = setOf(
                        "BUG_DETECTION" // Отключаем нестабильные агенты
                    )
                )
            }

            fun forTesting(): State {
                return State(
                    a2aEnabled = true,
                    messageBusType = MessageBusType.IN_MEMORY,
                    maxConcurrentMessages = 10,
                    defaultTimeoutMs = 5000L,
                    enableMetrics = true,
                    enableDetailedLogging = true
                )
            }
        }
    }

    enum class MessageBusType {
        IN_MEMORY,
        DISTRIBUTED, // Для будущей реализации
        EXTERNAL    // Для внешних message brokers
    }

    private var state: State = State.forProduction()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): A2AConfig {
            return ApplicationManager.getApplication().getService(A2AConfig::class.java)
        }
    }

    // Удобные методы для доступа к конфигурации
    fun isA2AEnabled(): Boolean = state.a2aEnabled
    fun getMessageBusType(): MessageBusType = state.messageBusType
    fun getMaxConcurrentMessages(): Int = state.maxConcurrentMessages
    fun getDefaultTimeoutMs(): Long = state.defaultTimeoutMs
    fun isMetricsEnabled(): Boolean = state.enableMetrics
    fun isDetailedLoggingEnabled(): Boolean = state.enableDetailedLogging

    fun isAgentTypeAllowed(agentType: String): Boolean = state.isAgentTypeAllowed(agentType)

    // Методы для изменения конфигурации (с сохранением состояния)
    fun setA2AEnabled(enabled: Boolean) {
        state = state.copy(a2aEnabled = enabled)
    }

    fun setMessageBusType(type: MessageBusType) {
        state = state.copy(messageBusType = type)
    }

    fun setMaxConcurrentMessages(max: Int) {
        state = state.copy(maxConcurrentMessages = max)
    }

    fun setDefaultTimeoutMs(timeout: Long) {
        state = state.copy(defaultTimeoutMs = timeout)
    }

    fun setMetricsEnabled(enabled: Boolean) {
        state = state.copy(enableMetrics = enabled)
    }

    fun setDetailedLoggingEnabled(enabled: Boolean) {
        state = state.copy(enableDetailedLogging = enabled)
    }

    fun setAllowedAgentTypes(types: Set<String>) {
        state = state.copy(allowedAgentTypes = types)
    }

    fun setBlockedAgentTypes(types: Set<String>) {
        state = state.copy(blockedAgentTypes = types)
    }

    // Предустановленные профили
    fun enableDevelopmentMode() {
        state = State.forDevelopment()
    }

    fun enableProductionMode() {
        state = State.forProduction()
    }

    fun enableTestingMode() {
        state = State.forTesting()
    }
}

/**
 * Утилитарный объект для удобной работы с A2A конфигурацией
 */
object A2AConfigUtil {
    private val config: A2AConfig get() = A2AConfig.getInstance()

    fun isA2AEnabled(): Boolean = config.isA2AEnabled()
    fun isAgentTypeAllowed(agentType: String): Boolean = config.isAgentTypeAllowed(agentType)
    fun getMessageBusType(): A2AConfig.MessageBusType = config.getMessageBusType()
    fun getMaxConcurrentMessages(): Int = config.getMaxConcurrentMessages()
    fun getDefaultTimeoutMs(): Long = config.getDefaultTimeoutMs()
    fun isMetricsEnabled(): Boolean = config.isMetricsEnabled()
    fun isDetailedLoggingEnabled(): Boolean = config.isDetailedLoggingEnabled()

    // Быстрые проверки
    fun shouldUseA2A(agentType: String): Boolean {
        return isA2AEnabled() && isAgentTypeAllowed(agentType)
    }

    fun shouldLogDetails(): Boolean {
        return isDetailedLoggingEnabled()
    }

    fun shouldCollectMetrics(): Boolean {
        return isMetricsEnabled()
    }
}