package ru.marslab.ide.ride.agent.a2a

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.model.agent.AgentCapabilities
import ru.marslab.ide.ride.model.agent.AgentEvent
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.agent.AgentSettings
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.agent.ToolAgent
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.model.tool.StepInput

/**
 * Адаптер для преобразования legacy Agent в A2AAgent
 *
 * Позволяет существующим агентам участвовать в A2A коммуникации
 * без необходимости изменения их кода. Обеспечивает плавную миграцию.
 */
class A2AAgentAdapter(
    private val legacyAgent: Agent,
    private val adapterAgentType: AgentType = (legacyAgent as? ToolAgent)?.agentType
        ?: AgentType.PROJECT_SCANNER,
    override val a2aAgentId: String = legacyAgent.javaClass.simpleName,
    override val supportedMessageTypes: Set<String> = setOf(
        "AGENT_REQUEST",
        "AGENT_RESPONSE",
        "EXECUTION_STATUS",
        "TOOL_EXECUTION_REQUEST"
    ),
    override val publishedEventTypes: Set<String> = setOf(
        "EXECUTION_STARTED",
        "EXECUTION_COMPLETED",
        "EXECUTION_FAILED"
    ),
    override val messageProcessingPriority: Int = 0,
    override val maxConcurrentMessages: Int = 5
) : BaseA2AAgent(
    agentType = adapterAgentType,
    a2aAgentId = a2aAgentId,
    supportedMessageTypes = supportedMessageTypes,
    publishedEventTypes = publishedEventTypes,
    messageProcessingPriority = messageProcessingPriority,
    maxConcurrentMessages = maxConcurrentMessages
), Agent {

    // Сохраняем ссылку на legacy agent для делегирования
    val wrappedAgent: Agent get() = legacyAgent

    // Контекст выполнения для адаптера
    private var executionContext: ExecutionContext = ExecutionContext.Empty

    // Реализация методов Agent интерфейса
    override val capabilities: AgentCapabilities get() = legacyAgent.capabilities

    override suspend fun ask(req: AgentRequest): AgentResponse {
        return legacyAgent.ask(req)
    }

    override fun start(req: AgentRequest): Flow<AgentEvent>? {
        return legacyAgent.start(req)
    }

    override fun updateSettings(settings: AgentSettings) {
        legacyAgent.updateSettings(settings)
    }

    override fun dispose() {
        legacyAgent.dispose()
    }

    // Создает пустой ChatContext для legacy агентов
    private fun createEmptyChatContext(): ChatContext {
        // Используем фиктивный проект для совместимости
        return ChatContext(
            project = com.intellij.openapi.project.ProjectManager.getInstance().defaultProject,
            history = emptyList(),
            currentFile = null,
            selectedText = null,
            additionalContext = emptyMap()
        )
    }

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        return try {
            logger.info("Adapting A2A request for legacy agent: $a2aAgentId")

            // Публикуем событие о начале выполнения
            publishEvent(
                messageBus = messageBus,
                eventType = "EXECUTION_STARTED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "STARTED",
                    agentId = a2aAgentId,
                    requestId = request.id,
                    timestamp = System.currentTimeMillis()
                )
            )

            // Если это запрос на выполнение шага для ToolAgent
            val toolAgent = legacyAgent as? ToolAgent
            val maybeCustom = request.payload as? MessagePayload.CustomPayload
            if (toolAgent != null && request.messageType == "TOOL_EXECUTION_REQUEST" && maybeCustom != null) {
                // Формируем ToolPlanStep: допускаем как полную форму (step={...}), так и плоские поля
                val stepMap = (maybeCustom.data["step"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    (k as? String)?.let { it to v }
                }?.toMap()

                val description = (stepMap?.get("description") as? String)
                    ?: (maybeCustom.data["description"] as? String)
                    ?: "A2A Tool Execution"

                val inputMap: Map<String, Any> = when {
                    stepMap?.get("input") is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        (stepMap["input"] as Map<String, Any>)
                    }
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        (maybeCustom.data as Map<String, Any>)
                    }
                }

                val toolStep = ToolPlanStep(
                    description = description,
                    agentType = toolAgent.agentType,
                    input = StepInput(inputMap)
                )

                val stepResult = withContext(Dispatchers.Default) {
                    toolAgent.executeStep(toolStep, executionContext)
                }

                // Публикуем событие о завершении
                publishEvent(
                    messageBus = messageBus,
                    eventType = "EXECUTION_COMPLETED",
                    payload = MessagePayload.ExecutionStatusPayload(
                        status = if (stepResult.success) "COMPLETED" else "FAILED",
                        agentId = a2aAgentId,
                        requestId = request.id,
                        timestamp = System.currentTimeMillis(),
                        result = stepResult.output.data.toString(),
                        error = stepResult.error
                    )
                )

                return AgentMessage.Response(
                    senderId = a2aAgentId,
                    requestId = request.id,
                    success = stepResult.success,
                    payload = MessagePayload.CustomPayload(
                        type = "TOOL_EXECUTION_RESULT",
                        data = mapOf(
                            "output" to stepResult.output.data,
                            "error" to (stepResult.error ?: ""),
                            "metadata" to stepResult.metadata
                        )
                    ),
                    error = stepResult.error
                )
            }

            // Иначе: текстовый запрос к legacy Agent
            val agentRequest = when (val payload = request.payload) {
                is MessagePayload.TextPayload -> {
                    AgentRequest(
                        request = payload.text,
                        context = createEmptyChatContext(),
                        parameters = LLMParameters.DEFAULT
                    )
                }
                else -> {
                    AgentRequest(
                        request = payload.toString(),
                        context = createEmptyChatContext(),
                        parameters = LLMParameters.DEFAULT
                    )
                }
            }

            val response = withContext(Dispatchers.Default) {
                legacyAgent.ask(agentRequest)
            }

            publishEvent(
                messageBus = messageBus,
                eventType = "EXECUTION_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = a2aAgentId,
                    requestId = request.id,
                    timestamp = System.currentTimeMillis(),
                    result = response.content
                )
            )

            AgentMessage.Response(
                senderId = a2aAgentId,
                requestId = request.id,
                success = true,
                payload = MessagePayload.TextPayload(
                    text = response.content,
                    metadata = mapOf(
                        "legacy_agent_class" to legacyAgent.javaClass.simpleName,
                        "is_final_response" to response.isFinal,
                        "uncertainty_score" to (response.uncertainty ?: 0.0)
                    )
                )
            )
        } catch (e: Exception) {
            logger.error("Error processing A2A request in adapter", e)

            // Публикуем событие об ошибке
            publishEvent(
                messageBus = messageBus,
                eventType = "EXECUTION_FAILED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "FAILED",
                    agentId = a2aAgentId,
                    requestId = request.id,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            )

            AgentMessage.Response(
                senderId = a2aAgentId,
                requestId = request.id,
                success = false,
                payload = MessagePayload.ErrorPayload(
                    error = "Legacy agent execution failed: ${e.message}",
                    cause = e.javaClass.simpleName
                )
            )
        }
    }

    override suspend fun initializeA2A(
        messageBus: MessageBus,
        context: ExecutionContext
    ) {
        super.initializeA2A(messageBus, context)
        this.executionContext = context

        logger.info("Initialized A2A adapter for legacy agent: $a2aAgentId")

        // Публикуем событие об инициализации
        publishEvent(
            messageBus = messageBus,
            eventType = "AGENT_INITIALIZED",
            payload = MessagePayload.AgentInfoPayload(
                agentId = a2aAgentId,
                agentType = agentType.name,
                legacyAgentClass = legacyAgent.javaClass.simpleName,
                supportedMessageTypes = supportedMessageTypes,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun shutdownA2A(messageBus: MessageBus) {
        logger.info("Shutting down A2A adapter for legacy agent: $a2aAgentId")

        // Публикуем событие о завершении работы
        publishEvent(
            messageBus = messageBus,
            eventType = "AGENT_SHUTDOWN",
            payload = MessagePayload.AgentInfoPayload(
                agentId = a2aAgentId,
                agentType = agentType.name,
                legacyAgentClass = legacyAgent.javaClass.simpleName,
                timestamp = System.currentTimeMillis()
            )
        )

        super.shutdownA2A(messageBus)
    }

    companion object {
        /**
         * Создает адаптер для любого legacy агента
         */
        fun create(
            agent: Agent,
            a2aAgentId: String? = null,
            supportedMessageTypes: Set<String>? = null,
            publishedEventTypes: Set<String>? = null,
            agentType: AgentType? = null
        ): A2AAgentAdapter {
            return A2AAgentAdapter(
                legacyAgent = agent,
                adapterAgentType = agentType ?: (agent as? ToolAgent)?.agentType ?: AgentType.PROJECT_SCANNER,
                a2aAgentId = a2aAgentId ?: agent.javaClass.simpleName,
                supportedMessageTypes = (supportedMessageTypes ?: setOf(
                    "AGENT_REQUEST",
                    "AGENT_RESPONSE",
                    "EXECUTION_STATUS",
                    "TOOL_EXECUTION_REQUEST"
                )),
                publishedEventTypes = (publishedEventTypes ?: setOf(
                    "EXECUTION_STARTED",
                    "EXECUTION_COMPLETED",
                    "EXECUTION_FAILED"
                ))
            )
        }

        /**
         * Создает адаптер с минимальной конфигурацией
         */
        fun createMinimal(agent: Agent): A2AAgentAdapter {
            return A2AAgentAdapter(
                legacyAgent = agent,
                a2aAgentId = agent.javaClass.simpleName,
                supportedMessageTypes = setOf("AGENT_REQUEST"),
                publishedEventTypes = setOf("EXECUTION_COMPLETED")
            )
        }
    }
}