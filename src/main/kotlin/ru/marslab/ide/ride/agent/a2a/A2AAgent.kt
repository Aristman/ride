package ru.marslab.ide.ride.agent.a2a

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.model.agent.AgentCapabilities
import ru.marslab.ide.ride.model.agent.AgentEvent
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.agent.AgentSettings
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext

/**
 * Расширенный интерфейс агента с поддержкой A2A коммуникации
 *
 * A2AAgent расширяет базовый Agent интерфейс, добавляя возможность:
 * - Прямой коммуникации с другими агентами через MessageBus
 * - Подписки на события от других агентов
 * - Публикации собственных результатов
 * - Асинхронной обработки сообщений
 */
interface A2AAgent : Agent {

    /**
     * Тип агента (из оркестратора)
     */
    val agentType: AgentType

    /**
     * Уникальный идентификатор агента в A2A системе
     */
    val a2aAgentId: String

    /**
     * Типы сообщений, которые агент может обрабатывать
     */
    val supportedMessageTypes: Set<String>

    /**
     * Типы событий, которые агент публикует
     */
    val publishedEventTypes: Set<String>

    /**
     * Обрабатывает входящее A2A сообщение
     *
     * @param message Входящее сообщение
     * @param MessageBus Шина сообщений для ответных действий
     * @return AgentMessage Ответное сообщение или null если ответ не требуется
     */
    suspend fun handleA2AMessage(
        message: AgentMessage,
        messageBus: MessageBus
    ): AgentMessage?

    /**
     * Инициализирует A2A коммуникацию агента
     *
     * @param messageBus Шина сообщений
     * @param context Контекст выполнения
     */
    suspend fun initializeA2A(
        messageBus: MessageBus,
        context: ExecutionContext
    ) {
        // По умолчанию пустая реализация
    }

    /**
     * Завершает A2A коммуникацию агента
     *
     * @param messageBus Шина сообщений
     */
    suspend fun shutdownA2A(messageBus: MessageBus) {
        // По умолчанию пустая реализация
    }

    /**
     * Проверяет, может ли агент обработать указанное сообщение
     */
    fun canHandleMessage(message: AgentMessage): Boolean {
        return when (message) {
            is AgentMessage.Request -> {
                message.messageType in supportedMessageTypes &&
                (message.targetId == null || message.targetId == a2aAgentId)
            }
            is AgentMessage.Event -> {
                message.eventType in supportedMessageTypes
            }
            else -> false
        }
    }

    /**
     * Возвращает приоритет обработки сообщений (для планировщика)
     */
    val messageProcessingPriority: Int get() = 0

    /**
     * Возвращает максимальное количество одновременных сообщений
     */
    val maxConcurrentMessages: Int get() = 10
}

/**
 * Базовая реализация A2AAgent для унаследования
 */
abstract class BaseA2AAgent(
    override val agentType: AgentType,
    override val a2aAgentId: String,
    override val supportedMessageTypes: Set<String> = emptySet(),
    override val publishedEventTypes: Set<String> = emptySet(),
    override val messageProcessingPriority: Int = 0,
    override val maxConcurrentMessages: Int = 10
) : A2AAgent {

    protected val logger = com.intellij.openapi.diagnostic.Logger.getInstance(javaClass)
    private val a2aSupervisor: Job = SupervisorJob()
    private val a2aScope = CoroutineScope(Dispatchers.Default + a2aSupervisor)

    // Базовая реализация Agent интерфейса
    override val capabilities: AgentCapabilities = AgentCapabilities(
        stateful = false,
        streaming = false,
        reasoning = false,
        tools = emptySet(),
        systemPrompt = null,
        responseRules = emptyList()
    )

    override suspend fun ask(req: AgentRequest): AgentResponse {
        // По умолчанию преобразуем в A2A сообщение и обрабатываем
        return AgentResponse(
            content = "A2A agent ${a2aAgentId} received request: ${req.request}",
            success = true,
            isFinal = true,
            uncertainty = 0.0
        )
    }

    override fun start(req: AgentRequest): Flow<AgentEvent>? {
        // По умолчанию streaming не поддерживается
        return null
    }

    override fun updateSettings(settings: AgentSettings) {
        // По умолчанию игнорируем настройки
        logger.info("A2A agent $a2aAgentId received settings update (ignored)")
    }

    override fun dispose() {
        // По умолчанию ничего не делаем
        logger.info("A2A agent $a2aAgentId disposed")
    }

    override suspend fun handleA2AMessage(
        message: AgentMessage,
        messageBus: MessageBus
    ): AgentMessage? {
        return when (message) {
            is AgentMessage.Request -> handleRequest(message, messageBus)
            is AgentMessage.Event -> handleEvent(message, messageBus)
            else -> null
        }
    }

    override suspend fun initializeA2A(
        messageBus: MessageBus,
        context: ExecutionContext
    ) {
        // Публикуем событие инициализации агента
        try {
            val initEvent = AgentMessage.Event(
                senderId = a2aAgentId,
                eventType = "AGENT_INITIALIZED",
                payload = MessagePayload.AgentInfoPayload(
                    agentId = a2aAgentId,
                    agentType = agentType.name,
                    legacyAgentClass = this@BaseA2AAgent::class.java.name,
                    supportedMessageTypes = supportedMessageTypes,
                    timestamp = System.currentTimeMillis()
                )
            )
            messageBus.publish(initEvent)
        } catch (e: Exception) {
            logger.warn("Failed to publish AGENT_INITIALIZED event for $a2aAgentId", e)
        }

        // Подписываемся на запросы, которые данный агент может обработать
        a2aScope.launch {
            messageBus
                .subscribe(AgentMessage.Request::class) { req ->
                    // Фильтр по типу сообщения и целевому ID (если задан)
                    supportedMessageTypes.contains(req.messageType) &&
                        (req.targetId == null || req.targetId == a2aAgentId)
                }
                .collect { request ->
                    try {
                        val response = handleRequest(request, messageBus)
                        if (response != null) {
                            messageBus.publish(response)
                        }
                    } catch (ex: Exception) {
                        logger.error("Error handling A2A request ${request.messageType} in $a2aAgentId", ex)
                        // Публикуем ошибочный ответ, чтобы не допускать таймаута на стороне отправителя
                        val errorResponse = AgentMessage.Response(
                            senderId = a2aAgentId,
                            requestId = request.id,
                            success = false,
                            payload = MessagePayload.ErrorPayload(
                                error = ex.message ?: "Agent error",
                                cause = "Exception in $a2aAgentId"
                            ),
                            error = ex.message
                        )
                        messageBus.publish(errorResponse)
                    }
                }
        }
    }

    override suspend fun shutdownA2A(messageBus: MessageBus) {
        try {
            a2aSupervisor.cancel()
            val shutdownEvent = AgentMessage.Event(
                senderId = a2aAgentId,
                eventType = "AGENT_SHUTDOWN",
                payload = MessagePayload.AgentInfoPayload(
                    agentId = a2aAgentId,
                    agentType = agentType.name,
                    legacyAgentClass = this@BaseA2AAgent::class.java.name,
                    supportedMessageTypes = supportedMessageTypes,
                    timestamp = System.currentTimeMillis()
                )
            )
            messageBus.publish(shutdownEvent)
        } catch (e: Exception) {
            logger.warn("Error during shutdown of $a2aAgentId", e)
        }
    }

    /**
     * Обработка запроса (для переопределения в наследниках)
     */
    protected open suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        logger.warn("Unhandled request type: ${request.messageType} in agent $a2aAgentId")
        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = false,
            payload = MessagePayload.ErrorPayload(
                error = "Unsupported request type",
                cause = "Agent $a2aAgentId does not handle ${request.messageType}"
            )
        )
    }

    /**
     * Обработка события (для переопределения в наследниках)
     */
    protected open suspend fun handleEvent(
        event: AgentMessage.Event,
        messageBus: MessageBus
    ): AgentMessage? {
        logger.info("Processing event: ${event.eventType} in agent $a2aAgentId")
        return null // События обычно не требуют ответа
    }

    /**
     * Утилитарный метод для публикации событий
     */
    protected suspend fun publishEvent(
        messageBus: MessageBus,
        eventType: String,
        payload: MessagePayload,
        metadata: Map<String, Any> = emptyMap()
    ): Boolean {
        val event = AgentMessage.Event(
            senderId = a2aAgentId,
            eventType = eventType,
            payload = payload,
            metadata = metadata
        )
        return messageBus.publish(event)
    }

    /**
     * Утилитарный метод для отправки ответа на запрос
     */
    protected suspend fun sendResponse(
        messageBus: MessageBus,
        requestId: String,
        success: Boolean,
        payload: MessagePayload,
        error: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ): Boolean {
        val response = AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = requestId,
            success = success,
            payload = payload,
            error = error,
            metadata = metadata
        )
        return messageBus.publish(response)
    }
}