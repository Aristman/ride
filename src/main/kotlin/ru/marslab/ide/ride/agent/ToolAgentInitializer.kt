package ru.marslab.ide.ride.agent

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.tools.ArchitectureToolAgent
import ru.marslab.ide.ride.agent.tools.CodeChunkerToolAgent
import ru.marslab.ide.ride.agent.tools.CodeQualityToolAgent
import ru.marslab.ide.ride.agent.tools.ProjectScannerToolAgent
import ru.marslab.ide.ride.agent.tools.UserInteractionAgent

/**
 * Инициализатор для регистрации всех Tool Agents
 */
object ToolAgentInitializer {
    private val logger = Logger.getInstance(ToolAgentInitializer::class.java)
    
    /**
     * Регистрирует все доступные Tool Agents в реестре
     */
    fun registerAllAgents(registry: ToolAgentRegistry) {
        logger.info("Registering all Tool Agents")
        
        try {
            // Регистрируем ProjectScannerToolAgent
            registry.register(ProjectScannerToolAgent())
            logger.info("Registered ProjectScannerToolAgent")
            
            // Регистрируем CodeChunkerToolAgent
            registry.register(CodeChunkerToolAgent())
            logger.info("Registered CodeChunkerToolAgent")
            
            // Регистрируем CodeQualityToolAgent
            registry.register(CodeQualityToolAgent())
            logger.info("Registered CodeQualityToolAgent")
            
            // Регистрируем ArchitectureToolAgent
            registry.register(ArchitectureToolAgent())
            logger.info("Registered ArchitectureToolAgent")
            
            // Регистрируем UserInteractionAgent
            registry.register(UserInteractionAgent())
            logger.info("Registered UserInteractionAgent")
            
            logger.info("All Tool Agents registered successfully. Total: ${registry.count()}")
            
            // Выводим статистику
            val stats = registry.getStatistics()
            logger.info("Registry statistics: $stats")
            
        } catch (e: Exception) {
            logger.error("Error registering Tool Agents", e)
            throw e
        }
    }
    
    /**
     * Регистрирует базовые агенты (минимальный набор)
     */
    fun registerBasicAgents(registry: ToolAgentRegistry) {
        logger.info("Registering basic Tool Agents")
        
        registry.register(ProjectScannerToolAgent())
        registry.register(UserInteractionAgent())
        
        logger.info("Basic Tool Agents registered. Total: ${registry.count()}")
    }
}
