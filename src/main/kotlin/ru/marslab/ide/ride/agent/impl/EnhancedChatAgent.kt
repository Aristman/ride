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
import ru.marslab.ide.ride.agent.cache.UncertaintyAnalysisCache
import ru.marslab.ide.ride.agent.cache.PredictiveCacheManager
import ru.marslab.ide.ride.agent.optimizer.PromptOptimizer
import ru.marslab.ide.ride.agent.monitoring.PerformanceMonitor
import ru.marslab.ide.ride.agent.monitoring.RequestMetrics
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentCapabilities
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.agent.AgentSettings
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator
import ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestratorA2A
import ru.marslab.ide.ride.settings.PluginSettings
import java.util.UUID

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
    private val orchestrator: EnhancedAgentOrchestratorA2A,
    private val uncertaintyAnalyzer: UncertaintyAnalyzer = UncertaintyAnalyzer,
    private val complexityAnalyzer: RequestComplexityAnalyzer = RequestComplexityAnalyzer(),
    private val requestPlanner: RequestPlanner = RequestPlanner(),
    private val adaptivePlanner: AdaptiveRequestPlanner = AdaptiveRequestPlanner(),
    private val ragPlanEnricher: RAGPlanEnricher = RAGPlanEnricher(),
    private val uncertaintyCache: UncertaintyAnalysisCache = UncertaintyAnalysisCache(),
    private val predictiveCacheManager: PredictiveCacheManager = PredictiveCacheManager(uncertaintyCache, complexityAnalyzer),
    private val performanceMonitor: PerformanceMonitor = PerformanceMonitor()
) : Agent {

    private val logger = Logger.getInstance(EnhancedChatAgent::class.java)

    private var lastExecutionResult: String = ""

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
            "dynamic_modification",
            "caching",
            "predictive_optimization",
            "performance_monitoring"
        ),
        systemPrompt = baseChatAgent.capabilities.systemPrompt,
        responseRules = baseChatAgent.capabilities.responseRules + listOf(
            "Использовать интеллектуальную оценку неопределенности для выбора стратегии",
            "Простые запросы обрабатывать напрямую без планирования",
            "Использовать RAG обогащение только на этапе планирования",
            "Создавать адаптивные планы с условными шагами",
            "Динамически модифицировать планы на основе результатов",
            "Поддерживать интерактивные планы с паузами для пользовательского ввода",
            "Возобновлять приостановленные планы по запросу пользователя",
            "Использовать кэширование для ускорения повторных запросов",
            "Применять предиктивное кэширование на основе паттернов",
            "Оптимизировать системные промпты под сложность запроса",
            "Мониторить производительность и автоматически оптимизировать"
        )
    )

    override suspend fun ask(request: AgentRequest): AgentResponse {
        val requestId = UUID.randomUUID().toString()
        logger.info("EnhancedChatAgent processing request $requestId with new architecture")

        // Проверяем, это возобновление плана?
        val resumePlanId = request.context.additionalContext["resume_plan_id"] as? String
        if (resumePlanId != null) {
            logger.info("Resuming plan: $resumePlanId")
            return resumePlanWithInput(resumePlanId, request.request, request.context)
        }

        // Этап 1: Проверяем кэш для анализа неопределенности
        val uncertaintyResult = uncertaintyCache.get(request.request, request.context)
        val (finalUncertaintyResult, cacheHit) = if (uncertaintyResult != null) {
            logger.debug("Using cached uncertainty analysis for request $requestId")
            uncertaintyResult to true
        } else {
            // Выполняем анализ неопределенности
            val result = complexityAnalyzer.analyzeUncertainty(request.request, request.context)
            uncertaintyCache.put(request.request, request.context, result)
            result to false
        }

        // Регистрируем запрос в системе мониторинга
        val metrics = performanceMonitor.startRequest(requestId, finalUncertaintyResult.complexity)

        // Регистрируем паттерн запроса для предиктивного кэширования
        predictiveCacheManager.registerRequest(request.request, request.context, finalUncertaintyResult)

        try {
            // Этап 2: Выбор стратегии обработки
            val response = when {
                UncertaintyThresholds.isSimpleQuery(finalUncertaintyResult) -> {
                    logger.info("Simple query detected, using direct response")
                    handleSimpleQuery(request, finalUncertaintyResult, metrics)
                }

                UncertaintyThresholds.shouldUseOrchestrator(finalUncertaintyResult) -> {
                    logger.info("Complex task detected, using adaptive planning")
                    handleComplexQueryWithPlanning(request, finalUncertaintyResult, metrics)
                }

                else -> {
                    logger.info("Medium complexity task, using base planning")
                    handleMediumQueryWithPlanning(request, finalUncertaintyResult, metrics)
                }
            }

            // Предсказываем и кэшируем следующие запросы
            predictiveCacheManager.predictAndCache(request.request, request.context)

            // Завершаем мониторинг
            performanceMonitor.finishRequest(metrics, cacheHit)

            return response

        } catch (e: Exception) {
            logger.error("Error in enhanced request processing", e)
            performanceMonitor.recordError("processing_error", e.message)

            // Fallback к базовому агенту
            val fallbackResponse = baseChatAgent.ask(request)
            performanceMonitor.finishRequest(metrics, false, 0.5) // Низкое качество при fallback
            return fallbackResponse
        }
    }

    override fun updateSettings(settings: AgentSettings) {
        baseChatAgent.updateSettings(settings)
    }

    override fun dispose() {
        try {
            // Останавливаем предиктивное кэширование
            predictiveCacheManager.shutdown()
            logger.info("EnhancedChatAgent disposed successfully")
        } catch (e: Exception) {
            logger.warn("Error during EnhancedChatAgent disposal", e)
        } finally {
            baseChatAgent.dispose()
        }
    }

    /**
     * Обрабатывает простые запросы напрямую через базовый агент
     */
    private suspend fun handleSimpleQuery(
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult,
        metrics: RequestMetrics
    ): AgentResponse {
        logger.info("Processing simple query with uncertainty: ${uncertaintyResult.score}")

        // Оптимизируем промпт для простого запроса
        val optimizedPrompt = PromptOptimizer.getFastPathPrompt(baseChatAgent.capabilities.systemPrompt ?: "")
        val optimizedRequest = request.copy(
            context = request.context.copy(
                additionalContext = request.context.additionalContext + ("optimized_prompt" to (optimizedPrompt ?: ""))
            )
        )

        // Прямой ответ через базовый агент без планирования
        val response = baseChatAgent.ask(optimizedRequest)

        // Добавляем метаданные об анализе
        return response.copy(
            metadata = response.metadata + mapOf(
                "uncertainty_analysis" to mapOf(
                    "score" to uncertaintyResult.score,
                    "complexity" to uncertaintyResult.complexity.name,
                    "reasoning" to uncertaintyResult.reasoning,
                    "processing_strategy" to "direct_response_optimized"
                ),
                "processing_time_ms" to System.currentTimeMillis(),
                "fast_path" to true,
                "prompt_optimization" to true,
                "cache_hit" to true
            )
        )
    }

    /**
     * Обрабатывает запросы средней сложности с базовым планированием
     */
    private suspend fun handleMediumQueryWithPlanning(
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult,
        metrics: RequestMetrics
    ): AgentResponse {
        logger.info("Processing medium complexity query with planning")

        try {
            // Оптимизируем промпт для запроса средней сложности
            val optimizedPrompt = PromptOptimizer.getOptimizedSystemPrompt(
                baseChatAgent.capabilities.systemPrompt ?: "",
                uncertaintyResult,
                request.context
            )

            // Этап 1: Создание базового плана
            val plan = requestPlanner.createPlan(
                request = request.request,
                uncertainty = uncertaintyResult,
                context = request.context,
                userRequestId = request.context.additionalContext["user_request_id"] as? String ?: ""
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
            return executePreparedPlan(enrichedPlan, request, uncertaintyResult, optimizedPrompt)

        } catch (e: Exception) {
            logger.error("Error in medium complexity planning", e)
            performanceMonitor.recordError("medium_planning_error", e.message)
            // Fallback к базовому агенту
            return baseChatAgent.ask(request)
        }
    }

    /**
     * Обрабатывает сложные запросы с адаптивным планированием
     */
    private suspend fun handleComplexQueryWithPlanning(
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult,
        metrics: RequestMetrics
    ): AgentResponse {
        logger.info("Processing complex query with adaptive planning")

        try {
            // Оптимизируем промпт для сложного запроса
            val optimizedPrompt = PromptOptimizer.getOptimizedSystemPrompt(
                baseChatAgent.capabilities.systemPrompt ?: "",
                uncertaintyResult,
                request.context
            )

            // Этап 1: Создание адаптивного плана с условными шагами
            val adaptivePlan = adaptivePlanner.createAdaptivePlan(
                request = request.request,
                uncertainty = uncertaintyResult,
                context = request.context,
                userRequestId = request.context.additionalContext["user_request_id"] as? String ?: ""
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
            return executeAdaptivePlan(enrichedPlan, request, uncertaintyResult, optimizedPrompt)

        } catch (e: Exception) {
            logger.error("Error in complex adaptive planning", e)
            performanceMonitor.recordError("complex_planning_error", e.message)
            // Fallback к базовому планированию
            return handleMediumQueryWithPlanning(request, uncertaintyResult, metrics)
        }
    }

    /**
     * Выполняет готовый план через оркестратор
     */
    private suspend fun executePreparedPlan(
        plan: ExecutionPlan,
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult,
        optimizedPrompt: String
    ): AgentResponse {
        logger.info("Executing prepared plan ${plan.id} with ${plan.steps.size} steps")

        // Получаем baseOrchestrator из A2A
        val baseOrchestrator = if (orchestrator is ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestratorA2A) {
            val field = orchestrator::class.java.getDeclaredField("baseOrchestrator")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(orchestrator) as? ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator
        } else {
            orchestrator as? ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator
        }

        val result = baseOrchestrator?.executePreparedPlan(plan) ?: run {
            logger.warn("Base orchestrator not found, using fallback")
            baseChatAgent.ask(request)
        }

        // Формируем итоговый ответ
        val content = buildString {
            appendLine(result.content)
        }

        return result.copy(
            content = content,
            metadata = result.metadata + mapOf(
                "uncertainty_analysis" to mapOf(
                    "score" to uncertaintyResult.score,
                    "complexity" to uncertaintyResult.complexity.name,
                    "reasoning" to uncertaintyResult.reasoning,
                    "processing_strategy" to "prepared_plan_execution"
                ),
                "plan_id" to plan.id,
                "plan_steps" to plan.steps.size,
                "plan_version" to plan.version,
                "prompt_optimization" to true
            )
        )
    }

    /**
     * Выполняет адаптивный план с поддержкой динамической модификации
     */
    private suspend fun executeAdaptivePlan(
        plan: ExecutionPlan,
        request: AgentRequest,
        uncertaintyResult: UncertaintyResult,
        optimizedPrompt: String
    ): AgentResponse {
        logger.info("Executing adaptive plan ${plan.id} with ${plan.steps.size} steps")

        val steps = mutableListOf<String>()

        // Выполняем план через A2A оркестратор
        val executionSuccess = try {
            // Используем A2A метод processWithA2A
            val executionContext = ru.marslab.ide.ride.model.orchestrator.ExecutionContext(
                additionalContext = mapOf(
                    "uncertaintyResult" to uncertaintyResult,
                    "optimizedPrompt" to optimizedPrompt,
                    "planId" to plan.id
                )
            )
            val a2aResult = orchestrator.processWithA2A(request, executionContext) { step ->
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

            // Сохраняем результат для использования ниже
            lastExecutionResult = a2aResult.content

            // Проверяем успешность выполнения
            !a2aResult.content.contains("ошибка") && !a2aResult.content.contains("Error")
        } catch (e: Exception) {
            logger.error("Error executing plan", e)
            lastExecutionResult = "Ошибка выполнения: ${e.message}"
            false
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
            appendLine(lastExecutionResult)
        }

        // Создаем новый AgentResponse с результатами выполнения
        return AgentResponse(
            content = content,
            success = executionSuccess,
            uncertainty = uncertaintyResult.score,
            isFinal = true,
            parsedContent = null,
            metadata = mapOf(
                "uncertainty_analysis" to mapOf(
                    "score" to uncertaintyResult.score,
                    "complexity" to uncertaintyResult.complexity.name,
                    "reasoning" to uncertaintyResult.reasoning,
                    "processing_strategy" to "adaptive_planned_execution_optimized"
                ),
                "plan_id" to plan.id,
                "plan_steps" to plan.steps.size,
                "plan_version" to plan.version,
                "adaptive_plan" to true,
                "prompt_optimization" to true,
                "execution_result" to lastExecutionResult
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

        // Получаем baseOrchestrator из A2A
        val baseOrchestrator = if (orchestrator is ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestratorA2A) {
            val field = orchestrator::class.java.getDeclaredField("baseOrchestrator")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(orchestrator) as? ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator
        } else {
            orchestrator as? ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator
        }

        val result = baseOrchestrator?.resumePlanWithCallback(planId, userInput) { step ->
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
        } ?: run {
            logger.warn("Base orchestrator not found, using fallback")
            baseChatAgent.ask(AgentRequest(
                request = userInput,
                context = ru.marslab.ide.ride.model.chat.ChatContext(
                    project = context.project,
                    history = context.history,
                    selectedText = context.selectedText,
                    additionalContext = context.additionalContext + mapOf(
                        "chat_history" to context.history.map { it.content },
                        "selected_text" to (context.selectedText ?: ""),
                        "current_file" to (context.currentFile?.path ?: "")
                    )
                )
            ))
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

    /**
     * Возвращает статистику производительности
     */
    fun getPerformanceStats() = performanceMonitor.getCurrentStats()

    /**
     * Возвращает статистику кэша
     */
    fun getCacheStats() = uncertaintyCache.getStats()

    /**
     * Возвращает статистику предиктивного кэширования
     */
    fun getPredictiveCacheStats() = predictiveCacheManager.getPredictiveStats()

    /**
     * Возвращает рекомендации по оптимизации
     */
    fun getOptimizationRecommendations() = performanceMonitor.analyzePerformance()

    /**
     * Сбрасывает всю статистику
     */
    fun resetStats() {
        performanceMonitor.reset()
        uncertaintyCache.clear()
    }

    companion object {
        /**
         * Создаёт EnhancedChatAgent с новым архитектурой планирования
         */
        fun create(llmProvider: LLMProvider): EnhancedChatAgent {
            val baseChatAgent = ChatAgent(llmProvider)

            // Создаем старый оркестратор как основу для A2A
            val baseOrchestrator = EnhancedAgentOrchestrator(llmProvider)
            // Создаем новый A2A оркестратор на основе старого
            val orchestrator = ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestratorA2A(baseOrchestrator)

            // Инициализируем A2A-орчестратор и регистрируем A2A-агентов на общей шине
            try {
                // Регистрируем базовых агентов без LLM, затем LLM-зависимых
                kotlinx.coroutines.runBlocking {
                    orchestrator.registerCoreAgentsBasic()
                    orchestrator.registerLLMBasedAgents(llmProvider)
                }
            } catch (e: Exception) {
                Logger.getInstance(EnhancedChatAgent::class.java).warn("Failed to initialize A2A orchestrator: ${e.message}", e)
            }

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
