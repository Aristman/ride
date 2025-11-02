package ru.marslab.ide.ride.agent.a2a

import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.request.AgentRequest
import ru.marslab.ide.ride.model.response.AgentResponse

/**
 * Адаптер для преобразования legacy Agent в A2AAgent
 *
 * Позволяет существующим агентам участвовать в A2A коммуникации
 * без необходимости изменения их кода. Обеспечивает плавную миграцию.
 */
class A2AAgentAdapter(
    private val legacyAgent: Agent,
    override val a2aAgentId: String = legacyAgent.javaClass.simpleName,
    override val supportedMessageTypes: Set<String> = setOf(
        "AGENT_REQUEST",
        "AGENT_RESPONSE",
        "EXECUTION_STATUS"
    ),
    override val publishedEventTypes: Set<String> = setOf(
        "EXECUTION_STARTED",
        "EXECUTION_COMPLETED",
        "EXECUTION_FAILED"
    ),
    override val messageProcessingPriority: Int = 0,
    override val maxConcurrentMessages: Int = 5
) : BaseA2AAgent(
    agentType = legacyAgent.agentType,
    a2aAgentId = a2aAgentId,
    supportedMessageTypes = supportedMessageTypes,
    publishedEventTypes = publishedEventTypes,
    messageProcessingPriority = messageProcessingPriority,
    maxConcurrentMessages = maxConcurrentMessages
) {

    // Сохраняем ссылку на legacy agent для делегирования
    val wrappedAgent: Agent get() = legacyAgent

    // Контекст выполнения для адаптера
    private var executionContext: ExecutionContext = ExecutionContext.Empty

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        return try {
            logger.info("Adapting A2A request for legacy agent: $a2aAgentId")

            // Преобразуем A2A сообщение в legacy запрос
            val agentRequest = when (request.messageType) {
                "AGENT_REQUEST" -> convertToAgentRequest(request)
                else -> {
                    logger.warn("Unsupported request type: ${request.messageType}")
                    return createErrorResponse(
                        requestId = request.id,
                        error = "Unsupported request type: ${request.messageType}"
                    )
                }
            }

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

            // Выполняем legacy agent
            val response = withContext(Dispatchers.Default) {
                legacyAgent.processRequest(agentRequest, executionContext)
            }

            // Публикуем событие о завершении выполнения
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

            // Преобразуем legacy ответ в A2A ответ
            convertToA2AResponse(
                requestId = request.id,
                agentResponse = response,
                metadata = mapOf(
                    "legacy_agent_class" to legacyAgent.javaClass.simpleName,
                    "execution_time_ms" to (System.currentTimeMillis() - request.timestamp)
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

            createErrorResponse(
                requestId = request.id,
                error = "Legacy agent execution failed: ${e.message}",
                cause = e.javaClass.simpleName
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

    /**
     * Преобразует A2A запрос в legacy AgentRequest
     */
    private suspend fun convertToAgentRequest(request: AgentMessage.Request): AgentRequest {
        return when (val payload = request.payload) {
            is MessagePayload.TextPayload -> {
                AgentRequest(
                    content = payload.text,
                    metadata = request.metadata + ("a2a_request_id" to request.id)
                )
            }
            is MessagePayload.JsonPayload -> {
                // Извлекаем текст из JSON если возможно
                val content = payload.jsonElement.primitive?.content
                    ?: payload.jsonElement.toString()
                AgentRequest(
                    content = content,
                    metadata = request.metadata + ("a2a_request_id" to request.id)
                )
            }
            else -> {
                AgentRequest(
                    content = payload.toString(),
                    metadata = request.metadata + ("a2a_request_id" to request.id)
                )
            }
        }
    }

    /**
     * Преобразует legacy AgentResponse в A2A Response
     */
    private suspend fun convertToA2AResponse(
        requestId: String,
        agentResponse: AgentResponse,
        metadata: Map<String, Any> = emptyMap()
    ): AgentMessage.Response {
        val payload = when (val content = agentResponse.parsedContent) {
            is ru.marslab.ide.ride.model.response.ParsedResponse.JsonResponse -> {
                MessagePayload.JsonPayload(
                    jsonElement = content.jsonElement,
                    metadata = mapOf(
                        "response_format" to "JSON",
                        "is_final" to agentResponse.isFinal,
                        "uncertainty" to agentResponse.uncertainty
                    )
                )
            }
            is ru.marslab.ide.ride.model.response.ParsedResponse.XmlResponse -> {
                MessagePayload.TextPayload(
                    text = content.xml,
                    metadata = mapOf(
                        "response_format" to "XML",
                        "is_final" to agentResponse.isFinal,
                        "uncertainty" to agentResponse.uncertainty
                    )
                )
            }
            is ru.marslab.ide.ride.model.response.ParsedResponse.TextResponse -> {
                MessagePayload.TextPayload(
                    text = content.text,
                    metadata = mapOf(
                        "response_format" to "TEXT",
                        "is_final" to agentResponse.isFinal,
                        "uncertainty" to agentResponse.uncertainty
                    )
                )
            }
            is ru.marslab.ide.ride.model.response.ParsedResponse.ParseError -> {
                MessagePayload.ErrorPayload(
                    error = content.error,
                    cause = "Parse error in legacy response",
                    metadata = mapOf(
                        "response_format" to "ERROR",
                        "original_content" to content.originalContent
                    )
                )
            }
        }

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = requestId,
            success = true,
            payload = payload,
            metadata = metadata + mapOf(
                "legacy_agent_class" to legacyAgent.javaClass.simpleName,
                "is_final_response" to agentResponse.isFinal,
                "uncertainty_score" to agentResponse.uncertainty
            )
        )
    }

    /**
     * Создает ответ с ошибкой
     */
    private suspend fun createErrorResponse(
        requestId: String,
        error: String,
        cause: String? = null
    ): AgentMessage.Response {
        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = requestId,
            success = false,
            payload = MessagePayload.ErrorPayload(
                error = error,
                cause = cause
            ),
            metadata = mapOf(
                "legacy_agent_class" to legacyAgent.javaClass.simpleName,
                "adapter_error" to true
            )
        )
    }

    companion object {
        /**
         * Создает адаптер для любого legacy агента
         */
        fun create(
            agent: Agent,
            a2aAgentId: String? = null,
            supportedMessageTypes: Set<String>? = null,
            publishedEventTypes: Set<String>? = null
        ): A2AAgentAdapter {
            return A2AAgentAdapter(
                legacyAgent = agent,
                a2aAgentId = a2aAgentId ?: agent.javaClass.simpleName,
                supportedMessageTypes = supportedMessageTypes ?: setOf(
                    "AGENT_REQUEST",
                    "AGENT_RESPONSE",
                    "EXECUTION_STATUS"
                ),
                publishedEventTypes = publishedEventTypes ?: setOf(
                    "EXECUTION_STARTED",
                    "EXECUTION_COMPLETED",
                    "EXECUTION_FAILED"
                )
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