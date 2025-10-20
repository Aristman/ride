package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentCapabilities
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.agent.AgentSettings
import ru.marslab.ide.ride.formatter.ChatOutputFormatter
import ru.marslab.ide.ride.agent.tools.LLMCodeReviewToolAgent
import ru.marslab.ide.ride.model.orchestrator.TaskType
import ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator

/**
 * Расширенный ChatAgent с поддержкой интерактивных планов
 * 
 * Определяет, когда использовать простой ChatAgent, а когда - EnhancedAgentOrchestrator:
 * - Простые вопросы → ChatAgent
 * - Сложные задачи → EnhancedAgentOrchestrator
 * - Возобновление планов → EnhancedAgentOrchestrator.resumePlan
 */
class EnhancedChatAgent(
    private val baseChatAgent: ChatAgent,
    private val orchestrator: EnhancedAgentOrchestrator,
    private val uncertaintyAnalyzer: UncertaintyAnalyzer = UncertaintyAnalyzer
) : Agent {

    private val logger = Logger.getInstance(EnhancedChatAgent::class.java)

    override val capabilities: AgentCapabilities = AgentCapabilities(
        stateful = true,
        streaming = false,
        reasoning = true,
        tools = setOf("orchestration", "user_interaction", "plan_management"),
        systemPrompt = baseChatAgent.capabilities.systemPrompt,
        responseRules = baseChatAgent.capabilities.responseRules + listOf(
            "Использовать оркестратор для сложных многошаговых задач",
            "Поддерживать интерактивные планы с паузами для пользовательского ввода",
            "Возобновлять приостановленные планы по запросу пользователя"
        )
    )

    override suspend fun ask(request: AgentRequest): AgentResponse {
        logger.info("EnhancedChatAgent processing request")

        // Проверяем, это возобновление плана?
        val resumePlanId = request.context.additionalContext["resume_plan_id"] as? String
        if (resumePlanId != null) {
            logger.info("Resuming plan: $resumePlanId")
            return resumePlanWithInput(resumePlanId, request.request)
        }

        // Анализируем сложность задачи
        val taskComplexity = analyzeTaskComplexity(request.request, request.context)

        return when {
            taskComplexity.isComplex -> {
                logger.info("Complex task detected, using orchestrator")
                useOrchestrator(request)
            }
            else -> {
                logger.info("Simple task, using base ChatAgent")
                baseChatAgent.ask(request)
            }
        }
    }

    override fun updateSettings(settings: AgentSettings) {
        baseChatAgent.updateSettings(settings)
    }

    override fun dispose() {
        baseChatAgent.dispose()
    }

    /**
     * Анализирует сложность задачи
     */
    private suspend fun analyzeTaskComplexity(
        request: String,
        context: ru.marslab.ide.ride.model.chat.ChatContext
    ): TaskComplexity {
        // Ключевые слова для сложных задач
        val complexKeywords = listOf(
            "проанализируй", "найди баги", "оптимизируй", "рефактор",
            "создай отчет", "проверь качество", "архитектур",
            "сканируй", "исследуй", "улучши"
        )

        val requestLower = request.lowercase()
        val hasComplexKeywords = complexKeywords.any { requestLower.contains(it) }

        // Проверяем упоминание файлов или проекта
        val mentionsFiles = requestLower.contains("файл") || 
                           requestLower.contains("проект") ||
                           requestLower.contains("код")

        // Длинный запрос обычно означает сложную задачу
        val isLongRequest = request.length > 100

        val isComplex = hasComplexKeywords && mentionsFiles || isLongRequest && mentionsFiles

        val taskType = when {
            requestLower.contains("баг") || requestLower.contains("ошибк") -> TaskType.BUG_FIX
            requestLower.contains("качеств") || requestLower.contains("code smell") -> TaskType.CODE_ANALYSIS
            requestLower.contains("архитектур") -> TaskType.ARCHITECTURE_ANALYSIS
            requestLower.contains("рефактор") -> TaskType.REFACTORING
            else -> TaskType.CODE_ANALYSIS
        }

        return TaskComplexity(
            isComplex = isComplex,
            estimatedSteps = if (isComplex) 3 else 1,
            taskType = taskType,
            requiresOrchestration = isComplex
        )
    }

    /**
     * Использует оркестратор для выполнения сложной задачи
     */
    private suspend fun useOrchestrator(request: AgentRequest): AgentResponse {
        val steps = mutableListOf<String>()
        
        val result = orchestrator.processEnhanced(request) { step ->
            // Собираем информацию о шагах
            val stepInfo = when (step) {
                is ru.marslab.ide.ride.agent.OrchestratorStep.PlanningComplete -> 
                    "📋 Планирование: ${step.content}"
                is ru.marslab.ide.ride.agent.OrchestratorStep.TaskComplete -> 
                    "🔍 Задача ${step.taskId}: ${step.taskTitle}"
                is ru.marslab.ide.ride.agent.OrchestratorStep.AllComplete -> 
                    "✅ Все задачи выполнены: ${step.content}"
                is ru.marslab.ide.ride.agent.OrchestratorStep.Error -> 
                    "❌ Ошибка: ${step.error}"
            }
            steps.add(stepInfo)
        }

        // Формируем итоговый ответ
        val content = buildString {
            appendLine("## Результат выполнения задачи")
            appendLine()
            if (steps.isNotEmpty()) {
                appendLine("### Выполненные шаги:")
                steps.forEach { step ->
                    appendLine("- $step")
                }
                appendLine()
            }
            appendLine(result.content)
        }

        return result.copy(content = content)
    }

    /**
     * Возобновляет выполнение плана с пользовательским вводом
     */
    private suspend fun resumePlanWithInput(planId: String, userInput: String): AgentResponse {
        logger.info("Resuming plan $planId with user input")

        val steps = mutableListOf<String>()
        
        // Используем новый метод с callback
        val result = orchestrator.resumePlanWithCallback(planId, userInput) { step ->
            val stepInfo = when (step) {
                is ru.marslab.ide.ride.agent.OrchestratorStep.PlanningComplete -> 
                    "📋 Планирование: ${step.content}"
                is ru.marslab.ide.ride.agent.OrchestratorStep.TaskComplete -> 
                    "🔍 Задача ${step.taskId}: ${step.taskTitle}"
                is ru.marslab.ide.ride.agent.OrchestratorStep.AllComplete -> 
                    "✅ Все задачи выполнены: ${step.content}"
                is ru.marslab.ide.ride.agent.OrchestratorStep.Error -> 
                    "❌ Ошибка: ${step.error}"
            }
            steps.add(stepInfo)
        }

        // Формируем итоговый ответ
        val content = buildString {
            appendLine("## ✅ План возобновлён")
            appendLine()
            if (steps.isNotEmpty()) {
                appendLine("### Выполненные шаги:")
                steps.forEach { step ->
                    appendLine("- $step")
                }
                appendLine()
            }
            appendLine(result.content)
        }

        return result.copy(
            content = content,
            metadata = result.metadata + mapOf(
                "plan_id" to planId,
                "resumed" to true
            )
        )
    }

    /**
     * Результат анализа сложности задачи
     */
    private data class TaskComplexity(
        val isComplex: Boolean,
        val estimatedSteps: Int,
        val taskType: TaskType,
        val requiresOrchestration: Boolean
    )

    companion object {
        /**
         * Создаёт EnhancedChatAgent с базовым ChatAgent и оркестратором
         */
        fun create(llmProvider: LLMProvider): EnhancedChatAgent {
            val baseChatAgent = ChatAgent(llmProvider)
            val orchestrator = EnhancedAgentOrchestrator(llmProvider)
            
            // Регистрируем все доступные ToolAgents
            registerToolAgents(orchestrator, llmProvider)
            
            return EnhancedChatAgent(baseChatAgent, orchestrator)
        }
        
        /**
         * Регистрирует все ToolAgents в оркестраторе
         */
        private fun registerToolAgents(
            orchestrator: EnhancedAgentOrchestrator,
            llmProvider: LLMProvider
        ) {
            val registry = orchestrator.getToolAgentRegistry()
            
            // Регистрируем все Tool Agents из Phase 2
            registry.register(
                ru.marslab.ide.ride.agent.tools.ProjectScannerToolAgent()
            )
            registry.register(
                ru.marslab.ide.ride.agent.tools.CodeChunkerToolAgent()
            )
            registry.register(
                ru.marslab.ide.ride.agent.tools.BugDetectionToolAgent()
            )
            registry.register(
                ru.marslab.ide.ride.agent.tools.CodeQualityToolAgent()
            )
            registry.register(
                ru.marslab.ide.ride.agent.tools.ArchitectureToolAgent()
            )
            registry.register(
                ru.marslab.ide.ride.agent.tools.ReportGeneratorToolAgent()
            )

            // LLM review agent (multi-language)
            registry.register(
                LLMCodeReviewToolAgent(llmProvider)
            )
        }
    }
}
