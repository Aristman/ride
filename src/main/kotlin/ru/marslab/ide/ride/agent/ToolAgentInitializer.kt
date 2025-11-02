package ru.marslab.ide.ride.agent

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.tools.*

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
            // Создаем LLM Provider для агентов, которые его требуют
            val llmProvider = createLLMProvider()

            // Регистрируем ProjectScannerToolAgent
            registry.register(ProjectScannerToolAgent())
            logger.info("Registered ProjectScannerToolAgent")

            // Регистрируем CodeChunkerToolAgent
            registry.register(CodeChunkerToolAgent())
            logger.info("Registered CodeChunkerToolAgent")

            // Регистрируем CodeQualityToolAgent
            registry.register(CodeQualityToolAgent())
            logger.info("Registered CodeQualityToolAgent")

            // Регистрируем ArchitectureToolAgent с LLM Provider
            registry.register(ArchitectureToolAgent(llmProvider))
            logger.info("Registered ArchitectureToolAgent")

            // Регистрируем UserInteractionAgent
            registry.register(UserInteractionAgent())
            logger.info("Registered UserInteractionAgent")

            // Регистрируем EmbeddingIndexerToolAgent
            registry.register(EmbeddingIndexerToolAgent())
            logger.info("Registered EmbeddingIndexerToolAgent")

            // Регистрируем CodeGeneratorToolAgent
            registry.register(CodeGeneratorToolAgent(llmProvider))
            logger.info("Registered CodeGeneratorToolAgent")

            // Регистрируем LLMReviewToolAgent
            registry.register(LLMReviewToolAgent(llmProvider))
            logger.info("Registered LLMReviewToolAgent")

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

        // Создаем LLM Provider для ArchitectureToolAgent
        val llmProvider = createLLMProvider()

        registry.register(ProjectScannerToolAgent())
        registry.register(ArchitectureToolAgent(llmProvider))
        registry.register(UserInteractionAgent())
        registry.register(CodeGeneratorToolAgent(llmProvider))
        registry.register(LLMReviewToolAgent(llmProvider))

        logger.info("Basic Tool Agents registered. Total: ${registry.count()}")
    }

    /**
     * Создает LLM Provider на основе настроек плагина
     */
    private fun createLLMProvider() = LLMProviderFactory.createLLMProvider()
}
