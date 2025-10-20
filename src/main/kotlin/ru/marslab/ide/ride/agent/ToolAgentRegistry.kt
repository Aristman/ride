package ru.marslab.ide.ride.agent

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр Tool Agents для регистрации и управления агентами
 * 
 * Предоставляет централизованное управление всеми Tool Agents в системе:
 * - Регистрация и удаление агентов
 * - Поиск агентов по типу и capabilities
 * - Валидация совместимости агентов
 */
class ToolAgentRegistry {
    private val logger = Logger.getInstance(ToolAgentRegistry::class.java)
    private val agents = ConcurrentHashMap<AgentType, ToolAgent>()
    
    /**
     * Регистрирует агента в реестре
     * 
     * @param agent Агент для регистрации
     * @throws IllegalStateException если агент с таким типом уже зарегистрирован
     */
    fun register(agent: ToolAgent) {
        if (agents.containsKey(agent.agentType)) {
            logger.warn("Agent ${agent.agentType} is already registered, replacing")
        }
        agents[agent.agentType] = agent
        logger.info("Registered agent: ${agent.agentType} with capabilities: ${agent.toolCapabilities}")
    }
    
    /**
     * Удаляет агента из реестра
     * 
     * @param agentType Тип агента для удаления
     * @return true, если агент был удален
     */
    fun unregister(agentType: AgentType): Boolean {
        val removed = agents.remove(agentType)
        if (removed != null) {
            logger.info("Unregistered agent: $agentType")
            removed.dispose()
            return true
        }
        return false
    }
    
    /**
     * Получает агента по типу
     * 
     * @param agentType Тип агента
     * @return Агент или null, если не найден
     */
    fun get(agentType: AgentType): ToolAgent? {
        return agents[agentType]
    }
    
    /**
     * Находит агентов по capability
     * 
     * @param capability Требуемая возможность
     * @return Список агентов с данной возможностью
     */
    fun findByCapability(capability: String): List<ToolAgent> {
        return agents.values.filter { it.toolCapabilities.contains(capability) }
    }
    
    /**
     * Находит агента, способного обработать шаг
     * 
     * @param step Шаг для обработки
     * @return Агент или null, если подходящий не найден
     */
    fun findForStep(step: ToolPlanStep): ToolAgent? {
        // Сначала пробуем найти по типу
        val agentByType = agents[step.agentType]
        if (agentByType != null && agentByType.canHandle(step)) {
            return agentByType
        }
        
        // Если не найден, ищем среди всех агентов
        return agents.values.firstOrNull { it.canHandle(step) }
    }
    
    /**
     * Возвращает список всех зарегистрированных агентов
     * 
     * @return Список агентов
     */
    fun listAll(): List<ToolAgent> {
        return agents.values.toList()
    }
    
    /**
     * Проверяет, доступен ли агент данного типа
     * 
     * @param agentType Тип агента
     * @return true, если агент зарегистрирован
     */
    fun isAvailable(agentType: AgentType): Boolean {
        return agents.containsKey(agentType)
    }
    
    /**
     * Возвращает количество зарегистрированных агентов
     */
    fun count(): Int {
        return agents.size
    }
    
    /**
     * Валидирует, что все требуемые типы агентов доступны
     * 
     * @param requiredTypes Требуемые типы агентов
     * @return Результат валидации
     */
    fun validateAvailability(requiredTypes: Set<AgentType>): AvailabilityResult {
        val missing = requiredTypes.filter { !isAvailable(it) }
        return if (missing.isEmpty()) {
            AvailabilityResult(true)
        } else {
            AvailabilityResult(false, missing)
        }
    }
    
    /**
     * Очищает реестр и освобождает ресурсы всех агентов
     */
    fun clear() {
        logger.info("Clearing registry and disposing all agents")
        agents.values.forEach { it.dispose() }
        agents.clear()
    }
    
    /**
     * Возвращает статистику по агентам
     */
    fun getStatistics(): RegistryStatistics {
        val capabilitiesCount = agents.values.flatMap { it.toolCapabilities }.distinct().size
        val agentsByType = agents.keys.groupBy { it }.mapValues { it.value.size }
        
        return RegistryStatistics(
            totalAgents = agents.size,
            totalCapabilities = capabilitiesCount,
            agentsByType = agentsByType
        )
    }
}

/**
 * Результат проверки доступности агентов
 */
data class AvailabilityResult(
    val allAvailable: Boolean,
    val missingTypes: List<AgentType> = emptyList()
) {
    val isValid: Boolean get() = allAvailable
}

/**
 * Статистика реестра агентов
 */
data class RegistryStatistics(
    val totalAgents: Int,
    val totalCapabilities: Int,
    val agentsByType: Map<AgentType, Int>
)
