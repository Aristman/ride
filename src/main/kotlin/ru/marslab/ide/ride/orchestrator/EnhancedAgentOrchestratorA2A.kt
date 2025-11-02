package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.agent.tools.A2AProjectScannerToolAgent
import ru.marslab.ide.ride.agent.tools.A2ABugDetectionToolAgent
import ru.marslab.ide.ride.agent.tools.A2ACodeQualityToolAgent
import ru.marslab.ide.ride.agent.tools.A2AReportGeneratorToolAgent
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.AgentOrchestrator
import ru.marslab.ide.ride.agent.OrchestratorStep
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.ToolPlanStep

/**
 * Расширение EnhancedAgentOrchestrator с поддержкой A2A коммуникации
 *
 * Добавляет возможность работы с A2A агентами, MessageBus коммуникацией
 * и автоматической адаптацией legacy агентов для A2A режима.
 */
class EnhancedAgentOrchestratorA2A(
    private val baseOrchestrator: EnhancedAgentOrchestrator,
    private val messageBus: MessageBus = MessageBusProvider.get(),
    private val a2aRegistry: A2AAgentRegistry = A2AAgentRegistry.getInstance(),
    private val a2aConfig: A2AConfig = A2AConfig.getInstance()
) {

    private val logger = Logger.getInstance(EnhancedAgentOrchestratorA2A::class.java)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // A2A кэши и состояния
    private val a2aAgents = mutableMapOf<String, A2AAgent>()
    private val activeExecutions = mutableMapOf<String, A2AExecutionContext>()

    init {
        // Подписываемся на A2A события
        subscribeToA2AEvents()
    }

    /**
     * Регистрирует базовых A2A-агентов в реестре на общей шине MessageBus
     */
    suspend fun registerCoreAgents(llmProvider: LLMProvider) {
        // Используем общий messageBus для всех агентов
        val scanner = A2AProjectScannerToolAgent(messageBus)
        val codeQuality = A2ACodeQualityToolAgent(messageBus, a2aRegistry)
        val bugDetection = A2ABugDetectionToolAgent(llmProvider, messageBus, a2aRegistry)
        val reportGenerator = A2AReportGeneratorToolAgent(llmProvider, messageBus, a2aRegistry)

        // Регистрируем
        a2aRegistry.registerAgent(scanner)
        a2aRegistry.registerAgent(codeQuality)
        a2aRegistry.registerAgent(bugDetection)
        a2aRegistry.registerAgent(reportGenerator)

        logger.info("Core A2A agents registered: ${listOf(scanner.a2aAgentId, codeQuality.a2aAgentId, bugDetection.a2aAgentId, reportGenerator.a2aAgentId)}")
    }

    /**
     * Обрабатывает запрос с поддержкой A2A режима
     *
     * @param request Входящий запрос
     * @param context Контекст выполнения
     * @param onStepComplete Callback для завершения шагов
     * @return AgentResponse с результатом
     */
    suspend fun processWithA2A(
        request: AgentRequest,
        context: ExecutionContext = ExecutionContext.Empty,
        onStepComplete: suspend (OrchestratorStep) -> Unit = {}
    ): AgentResponse {
        logger.info("Starting A2A enhanced orchestration for request")

        return try {
            // Проверяем включен ли A2A режим
            if (!a2aConfig.isA2AEnabled()) {
                logger.info("A2A mode disabled, falling back to legacy orchestration")
                return baseOrchestrator.processEnhanced(request, onStepComplete)
            }

            // Создаем A2A контекст выполнения
            val a2aContext = createA2AExecutionContext(request, context)

            // Публикуем событие о начале обработки
            publishA2AEvent(
                eventType = "ORCHESTRATION_STARTED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "STARTED",
                    agentId = "enhanced-orchestrator-a2a",
                    requestId = a2aContext.executionId,
                    timestamp = System.currentTimeMillis()
                )
            )

            // Анализируем запрос и получаем результат
            val result = analyzeRequestAndCreatePlan(request, a2aContext)

            // Публикуем событие о завершении
            publishA2AEvent(
                eventType = "ORCHESTRATION_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = "enhanced-orchestrator-a2a",
                    requestId = a2aContext.executionId,
                    timestamp = System.currentTimeMillis(),
                    result = result.content
                )
            )

            result

        } catch (e: Exception) {
            logger.error("Error in A2A orchestration", e)

            // Публикуем событие об ошибке
            publishA2AEvent(
                eventType = "ORCHESTRATION_FAILED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "FAILED",
                    agentId = "enhanced-orchestrator-a2a",
                    requestId = activeExecutions.keys.firstOrNull() ?: "unknown",
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            )

            // Fallback к legacy режиму
            if (a2aConfig.isA2AEnabled()) {
                logger.info("Falling back to legacy orchestration due to A2A error")
                baseOrchestrator.processEnhanced(request, onStepComplete)
            } else {
                throw e
            }
        } finally {
            cleanupA2AExecution()
        }
    }

    /**
     * Создает и регистрирует A2A агента для ToolAgent
     */
    suspend fun createA2AToolAgent(toolAgent: BaseToolAgent): A2AAgent {
        val a2aAgent = A2AAgentAdapter.create(
            agent = toolAgent,
            a2aAgentId = "${toolAgent.javaClass.simpleName}_${toolAgent.hashCode()}",
            supportedMessageTypes = setOf(
                "TOOL_EXECUTION_REQUEST",
                "TOOL_STATUS_UPDATE"
            ),
            publishedEventTypes = setOf(
                "TOOL_EXECUTION_STARTED",
                "TOOL_EXECUTION_COMPLETED",
                "TOOL_EXECUTION_FAILED"
            )
        )

        // Регистрируем агент
        val registered = a2aRegistry.registerAgent(a2aAgent)
        if (!registered) {
            throw IllegalStateException("Failed to register A2A tool agent: ${a2aAgent.a2aAgentId}")
        }

        // Инициализируем агент
        a2aAgent.initializeA2A(messageBus, ExecutionContext.Empty)

        a2aAgents[a2aAgent.a2aAgentId] = a2aAgent
        logger.info("Created and registered A2A tool agent: ${a2aAgent.a2aAgentId}")

        return a2aAgent
    }

    /**
     * Отправляет A2A сообщение агенту и ожидает ответ
     */
    suspend fun sendA2AMessage(
        agentId: String,
        message: AgentMessage.Request,
        timeoutMs: Long = a2aConfig.getDefaultTimeoutMs()
    ): AgentMessage.Response {
        return try {
            messageBus.requestResponse(message, timeoutMs)
        } catch (e: MessageBusException.TimeoutException) {
            logger.error("A2A message timeout for agent: $agentId", e)
            throw IllegalStateException("A2A communication timeout with agent: $agentId", e)
        } catch (e: Exception) {
            logger.error("A2A communication error with agent: $agentId", e)
            throw IllegalStateException("A2A communication failed with agent: $agentId", e)
        }
    }

    /**
     * Публикует A2A событие
     */
    suspend fun publishA2AEvent(
        eventType: String,
        payload: MessagePayload,
        metadata: Map<String, Any> = emptyMap()
    ): Boolean {
        val event = AgentMessage.Event(
            senderId = "enhanced-orchestrator-a2a",
            eventType = eventType,
            payload = payload,
            metadata = metadata
        )

        return messageBus.publish(event)
    }

    /**
     * Получает статистику A2A агентов
     */
    suspend fun getA2AStatistics(): A2AAgentFactory.A2AStatistics {
        return A2AAgentFactory.getA2AStatistics()
    }

    /**
     * Очищает A2A ресурсы
     */
    suspend fun cleanup() {
        logger.info("Cleaning up A2A orchestrator resources")

        try {
            // Очищаем активные выполнения
            activeExecutions.clear()

            // Останавливаем всех A2A агентов
            a2aAgents.values.forEach { agent ->
                try {
                    agent.shutdownA2A(messageBus)
                } catch (e: Exception) {
                    logger.error("Error shutting down A2A agent: ${agent.a2aAgentId}", e)
                }
            }
            a2aAgents.clear()

            // Очищаем реестр
            a2aRegistry.clear()

            logger.info("A2A orchestrator cleanup completed")
        } catch (e: Exception) {
            logger.error("Error during A2A orchestrator cleanup", e)
        }
    }

    // Private methods

    private fun createA2AExecutionContext(
        request: AgentRequest,
        context: ExecutionContext
    ): A2AExecutionContext {
        val executionId = "exec_${System.currentTimeMillis()}_${request.hashCode()}"
        val a2aContext = A2AExecutionContext(
            executionId = executionId,
            originalRequest = request,
            context = context,
            messageBus = messageBus,
            startTime = System.currentTimeMillis()
        )

        activeExecutions[executionId] = a2aContext
        return a2aContext
    }

    private suspend fun analyzeRequestAndCreatePlan(
        request: AgentRequest,
        a2aContext: A2AExecutionContext
    ): AgentResponse {
        // Публикуем событие анализа
        publishA2AEvent(
            eventType = "REQUEST_ANALYSIS_STARTED",
            payload = MessagePayload.ProgressPayload(
                stepId = "analysis",
                status = "started",
                progress = 0,
                message = "Analyzing request with A2A support"
            )
        )

        // Используем базовый оркестратор для анализа
        val response = baseOrchestrator.processEnhanced(request) { step ->
            // Публикуем прогресс через A2A
            publishA2AEvent(
                eventType = "PLAN_STEP_PROGRESS",
                payload = MessagePayload.ProgressPayload(
                    stepId = step.javaClass.simpleName,
                    status = "completed",
                    progress = 50,
                    message = "Step completed"
                )
            )
        }

        // Публикуем завершение анализа
        publishA2AEvent(
            eventType = "REQUEST_ANALYSIS_COMPLETED",
            payload = MessagePayload.ProgressPayload(
                stepId = "analysis",
                status = "completed",
                progress = 100,
                message = "Request analysis completed"
            )
        )

        return response
    }

    
    private fun subscribeToA2AEvents() {
        // Подписываемся на сообщения от A2A агентов
        coroutineScope.launch {
            messageBus.subscribeAll().collect { message ->
                when (message) {
                    is AgentMessage.Event -> handleA2AEvent(message)
                    else -> { /* Другие типы сообщений обрабатываются по необходимости */ }
                }
            }
        }
    }

    private suspend fun handleA2AEvent(event: AgentMessage.Event) {
        logger.debug("Received A2A event: ${event.eventType} from ${event.senderId}")

        when (event.eventType) {
            "TOOL_EXECUTION_FAILED" -> {
                logger.warn("Tool execution failed in A2A agent: ${event.senderId}")
                // Можно добавить логику retries или fallback
            }
            "AGENT_INITIALIZED" -> {
                logger.info("A2A agent initialized: ${event.senderId}")
            }
            "AGENT_SHUTDOWN" -> {
                logger.info("A2A agent shutdown: ${event.senderId}")
                a2aAgents.remove(event.senderId)
            }
        }
    }

    private fun cleanupA2AExecution() {
        // Очищаем контексты выполнения
        activeExecutions.clear()
    }

    /**
     * Контекст выполнения A2A
     */
    data class A2AExecutionContext(
        val executionId: String,
        val originalRequest: AgentRequest,
        val context: ExecutionContext,
        val messageBus: MessageBus,
        val startTime: Long
    )
}