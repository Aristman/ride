package ru.marslab.ide.ride.agent.a2a

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр A2A агентов для управления жизненным циклом и коммуникацией
 *
 * Обеспечивает:
 * - Регистрацию и удаление A2A агентов
 * - Управление подписками на сообщения
 * - Метрики активности агентов
 * - Автоматическую инициализацию и очистку
 */
@Service(Service.Level.PROJECT)
@Storage("A2AAgentRegistry.xml")
class A2AAgentRegistry : PersistentStateComponent<A2AAgentRegistry.State> {

    data class State(
        val registeredAgents: Map<String, AgentInfo> = emptyMap(),
        val subscriptions: Map<String, Set<String>> = emptyMap(), // agentId -> messageTypes
        val agentMetrics: Map<String, AgentMetrics> = emptyMap(),
        val lastActivity: Map<String, Long> = emptyMap() // agentId -> timestamp
    ) {
        companion object {
            fun empty(): State = State()
        }
    }

    data class AgentInfo(
        val agent: A2AAgent,
        val registeredAt: Long = System.currentTimeMillis(),
        val isActive: Boolean = true,
        val lastHeartbeat: Long = System.currentTimeMillis()
    )

    data class AgentMetrics(
        val messagesReceived: Long = 0,
        val messagesSent: Long = 0,
        val errorsCount: Long = 0,
        val averageResponseTimeMs: Double = 0.0,
        val lastActivity: Long = System.currentTimeMillis()
    )

    private val logger = Logger.getInstance(A2AAgentRegistry::class.java)

    // Runtime состояние
    private val agents: MutableMap<String, AgentInfo> = ConcurrentHashMap()
    private val subscriptions: MutableMap<String, MutableSet<String>> = ConcurrentHashMap()
    private val metrics: MutableMap<String, AgentMetrics> = ConcurrentHashMap()
    private val registryMutex = Mutex()

    // Фоновые задачи
    private val heartbeatJob = SupervisorJob()
    private val cleanupJob = SupervisorJob()

    private var state: State = State.empty()

    override fun getState(): State = State(
        registeredAgents = agents.toMap(),
        subscriptions = subscriptions.mapValues { it.value },
        agentMetrics = metrics.toMap(),
        lastActivity = agents.mapValues { System.currentTimeMillis() }
    )

    override fun loadState(state: State) {
        this.state = state
        // Восстанавливаем runtime состояние
        agents.clear()
        agents.putAll(state.registeredAgents)

        subscriptions.clear()
        state.subscriptions.forEach { (agentId, types) ->
            subscriptions[agentId] = types.toMutableSet()
        }

        metrics.clear()
        metrics.putAll(state.agentMetrics)
    }

    companion object {
        fun getInstance(): A2AAgentRegistry {
            return ApplicationManager.getApplication().getService(A2AAgentRegistry::class.java)
        }
    }

    init {
        // Запускаем фоновые задачи
        startHeartbeatMonitoring()
        startPeriodicCleanup()
    }

    /**
     * Регистрирует A2A агент в системе
     */
    suspend fun registerAgent(agent: A2AAgent): Boolean {
        return registryMutex.withLock {
            try {
                if (agents.containsKey(agent.a2aAgentId)) {
                    logger.warn("Agent ${agent.a2aAgentId} already registered")
                    return false
                }

                val agentInfo = AgentInfo(agent = agent)
                agents[agent.a2aAgentId] = agentInfo
                metrics[agent.a2aAgentId] = AgentMetrics()

                // Инициализируем A2A коммуникацию агента
                val messageBus = getMessageBus()
                agent.initializeA2A(messageBus, createExecutionContext(agent))

                logger.info("Registered A2A agent: ${agent.a2aAgentId} (${agent.agentType})")
                true

            } catch (e: Exception) {
                logger.error("Failed to register agent ${agent.a2aAgentId}", e)
                false
            }
        }
    }

    /**
     * Удаляет A2A агент из системы
     */
    suspend fun unregisterAgent(agentId: String): Boolean {
        return registryMutex.withLock {
            try {
                val agentInfo = agents.remove(agentId) ?: return false

                // Очищаем подписки
                subscriptions.remove(agentId)
                metrics.remove(agentId)

                // Завершаем A2A коммуникацию агента
                val messageBus = getMessageBus()
                agentInfo.agent.shutdownA2A(messageBus)

                logger.info("Unregistered A2A agent: $agentId")
                true

            } catch (e: Exception) {
                logger.error("Failed to unregister agent $agentId", e)
                false
            }
        }
    }

    /**
     * Получает зарегистрированный агент по ID
     */
    fun getAgent(agentId: String): A2AAgent? {
        return agents[agentId]?.agent
    }

    /**
     * Получает все зарегистрированные агенты
     */
    fun getAllAgents(): Map<String, A2AAgent> {
        return agents.mapValues { it.value.agent }
    }

    /**
     * Получает агенты указанного типа
     */
    fun getAgentsByType(agentType: AgentType): List<A2AAgent> {
        return agents.values
            .filter { it.agent.agentType == agentType }
            .map { it.agent }
            .toList()
    }

    /**
     * Получает агенты, которые могут обрабатывать указанный тип сообщения
     */
    fun getAgentsForMessageType(messageType: String): List<A2AAgent> {
        return agents.values
            .filter { it.agent.supportedMessageTypes.contains(messageType) }
            .map { it.agent }
            .toList()
    }

    /**
     * Проверяет, зарегистрирован ли агент
     */
    fun isAgentRegistered(agentId: String): Boolean {
        return agents.containsKey(agentId)
    }

    /**
     * Получает метрики агента
     */
    fun getAgentMetrics(agentId: String): AgentMetrics? {
        return metrics[agentId]
    }

    /**
     * Получает метрики всех агентов
     */
    fun getAllMetrics(): Map<String, AgentMetrics> {
        return metrics.toMap()
    }

    /**
     * Обновляет метрики агента
     */
    suspend fun updateAgentMetrics(
        agentId: String,
        update: (AgentMetrics) -> AgentMetrics
    ) {
        registryMutex.withLock {
            metrics[agentId] = update(metrics[agentId] ?: AgentMetrics())
            agents[agentId]?.let { info ->
                agents[agentId] = info.copy(lastHeartbeat = System.currentTimeMillis())
            }
        }
    }

    /**
     * Проверяет, активен ли агент
     */
    fun isAgentActive(agentId: String): Boolean {
        return agents[agentId]?.isActive ?: false
    }

    /**
     * Активирует/деактивирует агента
     */
    suspend fun setAgentActive(agentId: String, active: Boolean): Boolean {
        return registryMutex.withLock {
            agents[agentId]?.let { info ->
                agents[agentId] = info.copy(isActive = active)
                logger.info("Agent $agentId ${if (active) "activated" else "deactivated"}")
                true
            } ?: false
        }
    }

    /**
     * Получает количество зарегистрированных агентов
     */
    fun getRegisteredAgentsCount(): Int {
        return agents.size
    }

    /**
     * Очищает реестр (для тестов)
     */
    suspend fun clear() {
        registryMutex.withLock {
            val messageBus = getMessageBus()

            // Завершаем всех агентов
            agents.values.forEach { info ->
                try {
                    info.agent.shutdownA2A(messageBus)
                } catch (e: Exception) {
                    logger.error("Error shutting down agent ${info.agent.a2aAgentId}", e)
                }
            }

            // Очищаем все данные
            agents.clear()
            subscriptions.clear()
            metrics.clear()
        }
    }

    private fun startHeartbeatMonitoring() {
        CoroutineScope(Dispatchers.Default + heartbeatJob).launch {
            while (isActive) {
                delay(30000) // Проверяем каждые 30 секунд

                val now = System.currentTimeMillis()
                val inactiveAgents = mutableListOf<String>()

                agents.forEach { (agentId, info) ->
                    if (now - info.lastHeartbeat > 120000) { // 2 минуты без heartbeat
                        inactiveAgents.add(agentId)
                    }
                }

                if (inactiveAgents.isNotEmpty()) {
                    logger.warn("Marking agents as inactive: $inactiveAgents")
                    inactiveAgents.forEach { agentId ->
                        setAgentActive(agentId, false)
                    }
                }
            }
        }
    }

    private fun startPeriodicCleanup() {
        CoroutineScope(Dispatchers.Default + cleanupJob).launch {
            while (isActive) {
                delay(300000) // Очищаем каждые 5 минут

                // Удаляем неактивные агенты без heartbeat более 10 минут
                val now = System.currentTimeMillis()
                val toRemove = mutableListOf<String>()

                agents.forEach { (agentId, info) ->
                    if (!info.isActive && (now - info.lastHeartbeat > 600000)) {
                        toRemove.add(agentId)
                    }
                }

                if (toRemove.isNotEmpty()) {
                    logger.info("Removing inactive agents: $toRemove")
                    toRemove.forEach { agentId ->
                        unregisterAgent(agentId)
                    }
                }
            }
        }
    }

    private fun getMessageBus(): MessageBus {
        // TODO: Получать MessageBus из сервиса
        // Временно создаем инстанс
        return InMemoryMessageBus()
    }

    private fun createExecutionContext(agent: A2AAgent): ExecutionContext {
        // TODO: Создавать реальный контекст выполнения
        // Временно возвращаем пустой контекст
        return ExecutionContext.Empty
    }
}