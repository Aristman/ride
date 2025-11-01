package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.components.service
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.agent.analyzer.RequestComplexityAnalyzer
import ru.marslab.ide.ride.agent.analyzer.UncertaintyThresholds
import ru.marslab.ide.ride.agent.analyzer.UncertaintyResult
import ru.marslab.ide.ride.agent.planner.RequestPlanner
import ru.marslab.ide.ride.agent.planner.AdaptiveRequestPlanner
import ru.marslab.ide.ride.agent.rag.RAGPlanEnricher
import ru.marslab.ide.ride.agent.tools.*
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentCapabilities
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.agent.AgentSettings
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator
import ru.marslab.ide.ride.settings.PluginSettings

/**
 * Улучшенный ChatAgent с интеллектуальным планированием и адаптацией
 *
 * Новая архитектура с умной оценкой неопределенности:
 * - Простые запросы → прямой ответ через ChatAgent (< 1 секунда)
 * - Средняя сложность → базовый план через RequestPlanner
 * - Сложные запросы → адаптивный план с RAG обогащением
 * - Динамическая модификация планов на основе результатов
 *
 * Ключевые улучшения:
 * - Убрано прямое RAG обогащение из начального этапа
 * - RAG используется только на этапе планирования
 * - Адаптивные планы с условными шагами
 * - Интеллектуальная оценка неопределенности
 */
class EnhancedChatAgent(
    private val baseChatAgent: ChatAgent,
    private val orchestrator: EnhancedAgentOrchestrator,
    private val uncertaintyAnalyzer: UncertaintyAnalyzer = UncertaintyAnalyzer,
    private val complexityAnalyzer: RequestComplexityAnalyzer = RequestComplexityAnalyzer(),
    private val requestPlanner: RequestPlanner = RequestPlanner(),
    private val adaptivePlanner: AdaptiveRequestPlanner = AdaptiveRequestPlanner(),
    private val ragPlanEnricher: RAGPlanEnricher = RAGPlanEnricher()
) : Agent {

    private val logger = Logger.getInstance(EnhancedChatAgent::class.java)

    override val capabilities: AgentCapabilities = AgentCapabilities(
        stateful = true,
        streaming = false,
        reasoning = true,
        tools = setOf(
            "orchestration",
            "user_interaction",
            "plan_management",
            "adaptive_planning",
            "rag_enrichment",
            "uncertainty_analysis",
            "dynamic_modification"
        ),
        systemPrompt = baseChatAgent.capabilities.systemPrompt,
        responseRules = baseChatAgent.capabilities.responseRules + listOf(
            "Использовать интеллектуальную оценку неопределенности для выбора стратегии",
            "Простые запросы обрабатывать напрямую без планирования",
            "Использовать RAG обогащение только на этапе планирования",
            "Создавать адаптивные планы с условными шагами",
            "Динамически модифицировать планы на основе результатов",
            "Поддерживать интерактивные планы с паузами для пользовательского ввода",
            "Возобновлять приостановленные планы по запросу пользователя"
        )
    )

    override suspend fun ask(request: AgentRequest): AgentResponse {
        logger.info("EnhancedChatAgent processing request with new architecture")

        val startTime = System.currentTimeMillis()

        // Проверяем, это возобновление плана?
        val resumePlanId = request.context.additionalContext["resume_plan_id"] as? String
        if (resumePlanId != null) {
            logger.info("Resuming plan: $resumePlanId")
            return resumePlanWithInput(resumePlanId, request.request, request.context)
        }

        try {
            // Этап 1: Интеллектуальная оценка неопределенности
            val uncertaintyResult = complexityAnalyzer.analyzeUncertainty(request.request, request.context)
            logger.info("Uncertainty analysis completed: score=${uncertaintyResult.score}, complexity=${uncertaintyResult.complexity}")

            // Этап 2: Выбор стратегии обработки
            return when {
                UncertaintyThresholds.isSimpleQuery(uncertaintyResult) -> {
                    logger.info("Simple query detected, using direct response")
                    handleSimpleQuery(request, uncertaintyResult)
                }

                UncertaintyThresholds.shouldUseOrchestrator(uncertaintyResult) -> {
                    logger.info("Complex task detected, using adaptive planning")
                    handleComplexQueryWithPlanning(request, uncertaintyResult)
                }

                else -> {
                    logger.info("Medium complexity task, using base planning")
                    handleMediumQueryWithPlanning(request, uncertaintyResult)
                }
            }
        } catch (e: Exception) {
            logger.error("Error in enhanced request processing", e)
            // Fallback к базовому агенту
            return baseChatAgent.ask(request)
        } finally {
            val totalTime = System.currentTimeMillis() - startTime
            logger.info("Request processing completed in ${totalTime}ms")
        }
    }

    override fun updateSettings(settings: AgentSettings) {
        baseChatAgent.updateSettings(settings)
    }

    override fun dispose() {
        baseChatAgent.dispose()
    }

    /**
     * Обрабатывает простые запросы напрямую через базовый агент
     */
    private suspend fun handleSimpleQuery(
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult
    ): AgentResponse {
        logger.info("Processing simple query with uncertainty: ${uncertaintyResult.score}")

        // Прямой ответ через базовый агент без планирования
        val response = baseChatAgent.ask(request)

        // Добавляем метаданные об анализе
        return response.copy(
            metadata = response.metadata + mapOf(
                "uncertainty_analysis" to mapOf(
                    "score" to uncertaintyResult.score,
                    "complexity" to uncertaintyResult.complexity.name,
                    "reasoning" to uncertaintyResult.reasoning,
                    "processing_strategy" to "direct_response"
                ),
                "processing_time_ms" to System.currentTimeMillis(),
                "fast_path" to true
            )
        )
    }

    /**
     * Обрабатывает запросы средней сложности с базовым планированием
     */
    private suspend fun handleMediumQueryWithPlanning(
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult
    ): AgentResponse {
        logger.info("Processing medium complexity query with planning")

        try {
            // Этап 1: Создание базового плана
            val plan = requestPlanner.createPlan(
                request = request.request,
                uncertainty = uncertaintyResult,
                context = request.context,
                userRequestId = request.context.additionalContext["user_request_id"] as? String
            )

            logger.info("Created plan with ${plan.steps.size} steps")

            // Этап 2: Проверяем, нужно ли RAG обогащение
            val enrichedPlan = if (UncertaintyThresholds.shouldUseRAGEnrichment(uncertaintyResult)) {
                logger.info("Applying RAG enrichment to plan")
                ragPlanEnricher.enrichPlan(plan, request.request, request.context)
            } else {
                plan
            }

            // Этап 3: Выполнение плана через оркестратор
            return executePlan(enrichedPlan, request, uncertaintyResult)

        } catch (e: Exception) {
            logger.error("Error in medium complexity planning", e)
            // Fallback к базовому агенту
            return baseChatAgent.ask(request)
        }
    }

    /**
     * Обрабатывает сложные запросы с адаптивным планированием
     */
    private suspend fun handleComplexQueryWithPlanning(
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult
    ): AgentResponse {
        logger.info("Processing complex query with adaptive planning")

        try {
            // Этап 1: Создание адаптивного плана с условными шагами
            val adaptivePlan = adaptivePlanner.createAdaptivePlan(
                request = request.request,
                uncertainty = uncertaintyResult,
                context = request.context,
                userRequestId = request.context.additionalContext["user_request_id"] as? String
            )

            logger.info("Created adaptive plan with ${adaptivePlan.steps.size} steps")

            // Этап 2: RAG обогащение (почти всегда нужно для сложных запросов)
            val enrichedPlan = if (UncertaintyThresholds.shouldUseRAGEnrichment(uncertaintyResult)) {
                logger.info("Applying RAG enrichment to adaptive plan")
                ragPlanEnricher.enrichPlan(adaptivePlan, request.request, request.context)
            } else {
                adaptivePlan
            }

            // Этап 3: Выполнение плана с поддержкой динамической модификации
            return executeAdaptivePlan(enrichedPlan, request, uncertaintyResult)

        } catch (e: Exception) {
            logger.error("Error in complex adaptive planning", e)
            // Fallback к базовому планированию
            return handleMediumQueryWithPlanning(request, uncertaintyResult)
        }
    }

    /**
     * Выполняет план через оркестратор
     */
    private suspend fun executePlan(
        plan: ExecutionPlan,
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult
    ): AgentResponse {
        logger.info("Executing plan ${plan.id} with ${plan.steps.size} steps")

        val steps = mutableListOf<String>()

        val result = orchestrator.processEnhanced(request) { step ->
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

        return result.copy(
            content = content,
            metadata = result.metadata + mapOf(
                "uncertainty_analysis" to mapOf(
                    "score" to uncertaintyResult.score,
                    "complexity" to uncertaintyResult.complexity.name,
                    "reasoning" to uncertaintyResult.reasoning,
                    "processing_strategy" to "planned_execution"
                ),
                "plan_id" to plan.id,
                "plan_steps" to plan.steps.size,
                "plan_version" to plan.version
            )
        )
    }

    /**
     * Выполняет адаптивный план с поддержкой динамической модификации
     */
    private suspend fun executeAdaptivePlan(
        plan: ExecutionPlan,
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult
    ): AgentResponse {
        logger.info("Executing adaptive plan ${plan.id} with ${plan.steps.size} steps")

        val steps = mutableListOf<String>()
        var currentPlan = plan

        val result = orchestrator.processEnhanced(request) { step ->
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

        // Здесь можно добавить логику динамической модификации на основе результатов
        // Но это потребует более глубокой интеграции с оркестратором

        // Формируем итоговый ответ
        val content = buildString {
            appendLine("## Результат адаптивного выполнения")
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
                "uncertainty_analysis" to mapOf(
                    "score" to uncertaintyResult.score,
                    "complexity" to uncertaintyResult.complexity.name,
                    "reasoning" to uncertaintyResult.reasoning,
                    "processing_strategy" to "adaptive_planned_execution"
                ),
                "plan_id" to plan.id,
                "plan_steps" to plan.steps.size,
                "plan_version" to plan.version,
                "adaptive_plan" to true
            )
        )
    }

    /**
     * Возобновляет выполнение плана с пользовательским вводом
     */
    private suspend fun resumePlanWithInput(
        planId: String,
        userInput: String,
        context: ru.marslab.ide.ride.model.chat.ChatContext
    ): AgentResponse {
        logger.info("Resuming plan $planId with user input")

        val steps = mutableListOf<String>()

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
                "resumed" to true,
                "user_input" to userInput
            )
        )
    }

    /**
     * Возвращает текущий LLM провайдер (для отображения в UI)
     */
    fun getProvider(): LLMProvider {
        return baseChatAgent.getProvider()
    }

    companion object {
        /**
         * Создаёт EnhancedChatAgent с новым архитектурой планирования
         */
        fun create(llmProvider: LLMProvider): EnhancedChatAgent {
            val baseChatAgent = ChatAgent(llmProvider)
            val orchestrator = EnhancedAgentOrchestrator(llmProvider)

            // Регистрируем все доступные ToolAgents
            registerToolAgents(orchestrator, llmProvider)

            return EnhancedChatAgent(
                baseChatAgent = baseChatAgent,
                orchestrator = orchestrator
            )
        }

        /**
         * Регистрирует все ToolAgents в оркестраторе
         */
        private fun registerToolAgents(
            orchestrator: EnhancedAgentOrchestrator,
            llmProvider: LLMProvider
        ) {
            val registry = orchestrator.getToolAgentRegistry()

            // Регистрируем все Tool Agents
            registry.register(
                ProjectScannerToolAgent()
            )
            registry.register(
                CodeChunkerToolAgent()
            )
            registry.register(
                BugDetectionToolAgent(llmProvider)
            )
            registry.register(
                CodeQualityToolAgent()
            )
            registry.register(
                ArchitectureToolAgent(llmProvider)
            )
            registry.register(
                ReportGeneratorToolAgent(llmProvider)
            )

            // LLM review agent (multi-language)
            registry.register(
                LLMCodeReviewToolAgent(llmProvider)
            )

            // File operations: открытие исходников по команде
            registry.register(
                OpenSourceFileToolAgent()
            )
        }
    }
}
