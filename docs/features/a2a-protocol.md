# Feature: A2A Protocol (Agent-to-Agent Communication)

## Обзор

Фича A2A (Agent-to-Agent) Protocol определяет унифицированный протокол обмена данными между агентами в мультиагентской системе Ride. Протокол базируется на лучших практиках библиотеки Koog и обеспечивает надежную, масштабируемую и наблюдаемую коммуникацию между агентами.

## Проблема

Текущая архитектура агентов в Ride имеет следующие ограничения:

1. **Жесткие связи**: Агенты напрямую зависят друг от друга через оркестратор
2. **Отсутствие стандартизации**: Каждый агент использует собственный формат данных
3. **Ограниченная масштабируемость**: Сложно добавлять новые типы агентов
4. **Проблемы с отладкой**: Отсутствует единая система трассировки сообщений
5. **Фрагментация**: Разные паттерны взаимодействия (цепочки, параллелизм, оркестрация)

## Решение

Создание унифицированного A2A протокола, который обеспечивает:

- **Стандартизированные сообщения**: Единый формат для всех коммуникаций
- **Message Bus**: Централизованную шину для маршрутизации сообщений
- **Типизированные payloads**: Строгую типизацию передаваемых данных
- **Наблюдаемость**: Встроенную трассировку и метрики
- **Отказоустойчивость**: Retry механизмы и обработку ошибок
- **Масштабируемость**: Легкую регистрацию новых агентов

## Детальные требования

### 1. Core A2A Protocol

#### 1.1 AgentMessage

```kotlin
// Value objects для идентификации
@JvmInline
value class AgentId(val value: String) {
    init {
        require(value.isNotBlank()) { "AgentId cannot be blank" }
        require(value.length <= 64) { "AgentId too long" }
    }
}

@JvmInline
value class MessageId(val value: String) {
    init {
        require(value.isNotBlank()) { "MessageId cannot be blank" }
        require(UUID.fromString(value).let { true } catch { false }) { "MessageId must be valid UUID" }
    }
}

// Core message types
sealed class AgentMessage {
    abstract val messageId: MessageId
    abstract val from: AgentId
    abstract val to: AgentId
    abstract val timestamp: Instant
    abstract val payload: MessagePayload
    abstract val version: String
    abstract val metadata: Map<String, Any>

    data class Request(
        override val messageId: MessageId,
        override val from: AgentId,
        override val to: AgentId,
        override val timestamp: Instant,
        override val payload: RequestPayload,
        override val version: String = "1.0",
        override val metadata: Map<String, Any> = emptyMap(),
        val timeout: Duration = 30.seconds,
        val priority: Priority = Priority.NORMAL
    ) : AgentMessage()

    data class Response(
        override val messageId: MessageId,
        override val from: AgentId,
        override val to: AgentId,
        override val timestamp: Instant,
        override val payload: ResponsePayload,
        override val version: String = "1.0",
        override val metadata: Map<String, Any> = emptyMap(),
        val originalRequestId: MessageId,
        val status: ResponseStatus = ResponseStatus.SUCCESS,
        val error: ErrorInfo? = null
    ) : AgentMessage()

    data class Event(
        override val messageId: MessageId,
        override val from: AgentId,
        override val timestamp: Instant,
        override val payload: EventPayload,
        override val version: String = "1.0",
        override val metadata: Map<String, Any> = emptyMap(),
        val broadcast: Boolean = false,
        val recipients: Set<AgentId> = emptySet()
    ) : AgentMessage()
}

enum class Priority { LOW, NORMAL, HIGH, CRITICAL }
enum class ResponseStatus { SUCCESS, ERROR, TIMEOUT, CANCELLED }

data class ErrorInfo(
    val code: String,
    val message: String,
    val details: Map<String, Any> = emptyMap(),
    val cause: Throwable? = null
)
```

**Требования:**
- [ ] Уникальный messageId для каждого сообщения (UUID формат)
- [ ] Поддержка Request/Response/Event паттернов
- [ ] Метаданные для трассировки и контекста
- [ ] Валидация структуры сообщения
- [ ] Версионирование протокола
- [ ] Priority levels для critical сообщений
- [ ] Timeout handling для Request сообщений
- [ ] Error propagation в Response сообщениях
- [ ] Broadcast capability для Event сообщений

#### 1.2 MessagePayload Types

```kotlin
// Base payload hierarchy
sealed class MessagePayload {
    abstract val type: String
    abstract val metadata: Map<String, Any>
}

// Generic data payloads
data class TextData(
    val content: String,
    val format: TextFormat = TextFormat.PLAIN,
    override val metadata: Map<String, Any> = emptyMap()
) : MessagePayload() {
    override val type = "text"
}

enum class TextFormat { PLAIN, MARKDOWN, HTML, JSON }

data class StructuredData(
    val schema: String,  // JSON Schema or similar
    val data: Map<String, Any>,
    val format: DataFormat = DataFormat.JSON,
    override val metadata: Map<String, Any> = emptyMap()
) : MessagePayload() {
    override val type = "structured"
}

enum class DataFormat { JSON, XML, YAML }

data class FileData(
    val filePath: String,
    val content: ByteArray,
    val encoding: String = "UTF-8",
    val mimeType: String? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : MessagePayload() {
    override val type = "file"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FileData
        return filePath == other.filePath && content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}

// Domain-specific payloads
data class CodeAnalysisResult(
    val findings: List<CodeFinding>,
    val metrics: AnalysisMetrics,
    val analyzedFiles: List<String>,
    val analysisType: String,
    override val metadata: Map<String, Any> = emptyMap()
) : MessagePayload() {
    override val type = "code_analysis"
}

data class CodeFinding(
    val id: String,
    val severity: Severity,
    val message: String,
    val filePath: String,
    val lineRange: IntRange,
    val rule: String,
    val suggestion: String? = null
)

enum class Severity { ERROR, WARNING, INFO, SUGGESTION }

data class AnalysisMetrics(
    val totalFiles: Int,
    val analyzedFiles: Int,
    val issuesBySeverity: Map<Severity, Int>,
    val complexityMetrics: Map<String, Double>
)

data class ProjectStructure(
    val files: List<ProjectFile>,
    val dependencies: List<Dependency>,
    val modules: List<ProjectModule>,
    override val metadata: Map<String, Any> = emptyMap()
) : MessagePayload() {
    override val type = "project_structure"
}

data class ProjectFile(
    val path: String,
    val type: FileType,
    val size: Long,
    val lastModified: Instant,
    val language: String?
)

enum class FileType { SOURCE, RESOURCE, TEST, BUILD, CONFIG, OTHER }

data class Dependency(
    val group: String,
    val artifact: String,
    val version: String,
    val scope: DependencyScope,
    val configuration: String? = null
)

enum class DependencyScope { COMPILE, RUNTIME, TEST, PROVIDED, IMPORT }

data class ProjectModule(
    val name: String,
    val path: String,
    val type: ModuleType,
    val dependencies: Set<String> = emptySet()
)

enum class ModuleType { MAIN, TEST, LIBRARY, APPLICATION }

// Specialized Request payloads
sealed class RequestPayload : MessagePayload() {
    data class AnalysisRequest(
        val files: List<String>,
        val analysisType: String,
        val configuration: Map<String, Any> = emptyMap(),
        override val metadata: Map<String, Any> = emptyMap()
    ) : RequestPayload() {
        override val type = "analysis_request"
    }

    data class CodeGenerationRequest(
        val specification: String,
        val targetPath: String,
        val language: String,
        val template: String? = null,
        val context: Map<String, Any> = emptyMap(),
        override val metadata: Map<String, Any> = emptyMap()
    ) : RequestPayload() {
        override val type = "code_generation_request"
    }

    data class ReportGenerationRequest(
        val data: Map<String, Any>,
        val format: ReportFormat,
        val template: String? = null,
        val sections: List<String> = emptyList(),
        override val metadata: Map<String, Any> = emptyMap()
    ) : RequestPayload() {
        override val type = "report_generation_request"
    }
}

enum class ReportFormat { MARKDOWN, HTML, PDF, JSON }

// Specialized Response payloads
sealed class ResponsePayload : MessagePayload() {
    data class AnalysisResponse(
        val results: AnalysisResult,
        val executionTime: Duration,
        val agent: String,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ResponsePayload() {
        override val type = "analysis_response"
    }

    data class AnalysisResult(
        val summary: AnalysisSummary,
        val detailedFindings: List<CodeFinding>,
        val recommendations: List<String>,
        val qualityScore: Double
    )

    data class AnalysisSummary(
        val totalIssues: Int,
        val criticalIssues: Int,
        val warningIssues: Int,
        val infoIssues: Int,
        val overallHealth: String
    )

    data class CodeGenerationResponse(
        val generatedFiles: List<GeneratedFile>,
        val compilationStatus: CompilationStatus,
        val warnings: List<String> = emptyList(),
        val executionTime: Duration,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ResponsePayload() {
        override val type = "code_generation_response"
    }

    data class GeneratedFile(
        val path: String,
        val content: String,
        val size: Int,
        val type: FileType
    )

    enum class CompilationStatus { SUCCESS, FAILED, WARNINGS }

    data class ReportResponse(
        val report: String,
        val format: ReportFormat,
        val size: Int,
        val generationTime: Duration,
        val includedData: Set<String>,
        override val metadata: Map<String, Any> = emptyMap()
    ) : ResponsePayload() {
        override val type = "report_response"
    }
}

// Specialized Event payloads
sealed class EventPayload : MessagePayload() {
    data class StatusUpdate(
        val status: AgentStatus,
        val progress: Double? = null,
        val currentStep: String? = null,
        val estimatedTimeRemaining: Duration? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ) : EventPayload() {
        override val type = "status_update"
    }

    data class ErrorOccurred(
        val error: String,
        val severity: ErrorSeverity,
        val stackTrace: String? = null,
        val context: Map<String, Any> = emptyMap(),
        override val metadata: Map<String, Any> = emptyMap()
    ) : EventPayload() {
        override val type = "error_occurred"
    }

    data class CompletionNotification(
        val result: String,
        val success: Boolean,
        val executionTime: Duration,
        val outputFiles: List<String> = emptyList(),
        override val metadata: Map<String, Any> = emptyMap()
    ) : EventPayload() {
        override val type = "completion_notification"
    }
}

enum class AgentStatus { IDLE, BUSY, ERROR, COMPLETED, CANCELLED }
enum class ErrorSeverity { LOW, MEDIUM, HIGH, CRITICAL }
```

**Требования:**
- [ ] TextData - для текстовых сообщений с поддержкой форматов
- [ ] StructuredData - для JSON/XML/YAML данных со схемой
- [ ] FileData - для передачи файлов с metadata
- [ ] CodeAnalysisResult - для результатов анализа кода с findings и metrics
- [ ] ProjectStructure - для структуры проекта с файлами и зависимостями
- [ ] Специализированные Request/Response/Event payload'ы для доменных задач
- [ ] Type safety для всех payload вариантов
- [ ] Extensible design для будущих типов данных
- [ ] Validation constraints для critical данных
- [ ] Immutable data structures для thread-safety

#### 1.3 Message Serialization

**Требования:**
- [ ] Поддержка JSON сериализации
- [ ] Валидация схемы при десериализации
- [ ] Обработка ошибок сериализации
- [ ] Опциональная компрессия для больших сообщений
- [ ] Версионирование формата

### 2. Message Bus Architecture

#### 2.1 MessageBus Interface

```kotlin
// Core MessageBus interface
interface MessageBus {
    // Basic messaging
    suspend fun send(message: AgentMessage)
    suspend fun sendToMultiple(message: AgentMessage, recipients: Set<AgentId>)

    // Event broadcasting
    suspend fun broadcast(event: AgentMessage.Event)
    suspend fun broadcastTo(event: AgentMessage.Event, recipients: Set<AgentId>)

    // Request-Response pattern
    suspend fun requestResponse(
        message: AgentMessage.Request,
        timeout: Duration = 30.seconds
    ): AgentMessage.Response

    // Subscription management
    fun subscribe(agentId: AgentId): Flow<AgentMessage>
    fun subscribeWithFilter(
        agentId: AgentId,
        filter: MessageFilter
    ): Flow<AgentMessage>

    // Lifecycle
    fun unsubscribe(agentId: AgentId)
    suspend fun shutdown()

    // Health and monitoring
    val isHealthy: Boolean
    val metrics: MessageBusMetrics
}

// Message filter for selective subscription
interface MessageFilter {
    fun shouldAccept(message: AgentMessage): Boolean
}

class MessageTypeFilter(private val acceptedTypes: Set<String>) : MessageFilter {
    override fun shouldAccept(message: AgentMessage): Boolean {
        return message.payload.type in acceptedTypes
    }
}

class PriorityFilter(private val minPriority: Priority) : MessageFilter {
    override fun shouldAccept(message: AgentMessage): Boolean {
        return (message as? AgentMessage.Request)?.priority?.ordinal
            ?: Priority.NORMAL.ordinal >= minPriority.ordinal
    }
}

// Metrics collection
data class MessageBusMetrics(
    val messagesSent: AtomicLong = AtomicLong(0),
    val messagesReceived: AtomicLong = AtomicLong(0),
    val messagesDelivered: AtomicLong = AtomicLong(0),
    val messagesFailed: AtomicLong = AtomicLong(0),
    val averageLatency: AtomicLong = AtomicLong(0),
    val activeSubscriptions: AtomicInteger = AtomicInteger(0),
    val queueSizes: Map<AgentId, AtomicInteger> = emptyMap()
) {
    fun recordMessageSent() { messagesSent.incrementAndGet() }
    fun recordMessageReceived() { messagesReceived.incrementAndGet() }
    fun recordMessageDelivered() { messagesDelivered.incrementAndGet() }
    fun recordMessageFailed() { messagesFailed.incrementAndGet() }
    fun recordLatency(latencyMs: Long) {
        averageLatency.updateAndGet { current ->
            (current + latencyMs) / 2
        }
    }
    fun updateSubscriptionCount(count: Int) { activeSubscriptions.set(count) }
}

// Configuration
data class MessageBusConfig(
    val maxQueueSize: Int = 1000,
    val defaultTimeout: Duration = 30.seconds,
    val enableMetrics: Boolean = true,
    val enableTracing: Boolean = true,
    val compressionThreshold: Int = 10 * 1024, // 10KB
    val maxMessageSize: Int = 100 * 1024 * 1024 // 100MB
)

// Exception types
sealed class MessageBusException(message: String) : Exception(message) {
    class AgentNotRegisteredException(agentId: AgentId) :
        MessageBusException("Agent $agentId is not registered")
    class MessageTimeoutException(messageId: MessageId) :
        MessageBusException("Message $messageId timed out")
    class QueueFullException(agentId: AgentId) :
        MessageBusException("Queue for agent $agentId is full")
    class SerializationException(message: String, cause: Throwable?) :
        MessageBusException("Serialization failed: $message")
    class MessageTooLargeException(size: Int, maxSize: Int) :
        MessageBusException("Message size $size exceeds maximum $maxSize")
}
```

**Требования:**
- [ ] Асинхронная отправка сообщений
- [ ] Broadcast для событий с wildcard support
- [ ] Request-Response с configurable timeouts
- [ ] Flow-based подписка на сообщения с фильтрацией
- [ ] Гарантированная доставка (в рамках сессии)
- [ ] Порядок сообщений (FIFO) для каждого получателя
- [ ] Metrics collection для monitoring
- [ ] Type-safe filtering для selective subscription
- [ ] Proper exception handling для всех failure scenarios
- [ ] Configuration management для runtime tuning

#### 2.2 Message Router

**Требования:**
- [ ] Маршрутизация по AgentId
- [ ] Поддержка wildcard подписок
- [ ] Фильтрация сообщений по типу
- [ ] Балансировка нагрузки для инстансов агентов
- [ ] Dead letter queue для недоставленных сообщений

#### 2.3 InMemory Implementation

```kotlin
// Main implementation
class InMemoryMessageBus(
    private val config: MessageBusConfig = MessageBusConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : MessageBus {

    // Agent channels for message delivery
    private val agentChannels = ConcurrentHashMap<AgentId, Channel<AgentMessage>>()
    private val messageHandlers = ConcurrentHashMap<AgentId, suspend (AgentMessage) -> Unit>()

    // Request-response correlation
    private val pendingRequests = ConcurrentHashMap<MessageId, CompletableDeferred<AgentMessage.Response>>()

    // Metrics and monitoring
    private val metrics = MessageBusMetrics()
    private val queueSizes = ConcurrentHashMap<AgentId, AtomicInteger>()

    // Health and lifecycle
    @Volatile
    private var isShutdown = false

    override val isHealthy: Boolean
        get() = !isShutdown

    override val metrics: MessageBusMetrics
        get() = this.metrics

    override suspend fun send(message: AgentMessage) {
        checkNotShutdown()
        metrics.recordMessageSent()

        val startTime = System.currentTimeMillis()

        try {
            // Validate message size
            validateMessageSize(message)

            // Route to destination agent
            when (message) {
                is AgentMessage.Request -> handleRequestMessage(message)
                is AgentMessage.Response -> handleResponseMessage(message)
                is AgentMessage.Event -> handleEventMessage(message)
            }

            metrics.recordMessageDelivered()
            metrics.recordLatency(System.currentTimeMillis() - startTime)

        } catch (e: Exception) {
            metrics.recordMessageFailed()
            throw MessageBusException.MessageDeliveryException(
                "Failed to send message ${message.messageId}",
                e
            )
        }
    }

    override suspend fun sendToMultiple(
        message: AgentMessage,
        recipients: Set<AgentId>
    ) {
        recipients.forEach { recipient ->
            val targetedMessage = when (message) {
                is AgentMessage.Request -> message.copy(to = recipient)
                is AgentMessage.Response -> message.copy(to = recipient)
                is AgentMessage.Event -> message.copy(recipients = setOf(recipient))
            }
            send(targetedMessage)
        }
    }

    override suspend fun broadcast(event: AgentMessage.Event) {
        require(event.broadcast || event.recipients.isNotEmpty()) {
            "Event must have broadcast=true or specific recipients"
        }

        if (event.broadcast) {
            agentChannels.keys.forEach { agentId ->
                val broadcastEvent = event.copy(
                    recipients = if (event.recipients.isEmpty()) agentChannels.keys else event.recipients
                )
                send(broadcastEvent)
            }
        } else {
            event.recipients.forEach { recipientId ->
                val targetedEvent = event.copy(recipients = setOf(recipientId))
                send(targetedEvent)
            }
        }
    }

    override suspend fun requestResponse(
        message: AgentMessage.Request,
        timeout: Duration
    ): AgentMessage.Response {
        val deferred = CompletableDeferred<AgentMessage.Response>()
        pendingRequests[message.messageId] = deferred

        try {
            send(message)

            return withTimeout(timeout) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            throw MessageBusException.MessageTimeoutException(message.messageId)
        } finally {
            pendingRequests.remove(message.messageId)
        }
    }

    override fun subscribe(agentId: AgentId): Flow<AgentMessage> {
        val channel = agentChannels.getOrPut(agentId) {
            Channel(config.maxQueueSize).also { ch ->
                queueSizes[agentId] = AtomicInteger(0)
            }
        }

        metrics.updateSubscriptionCount(agentChannels.size)

        return channel.receiveAsFlow()
            .onEach { metrics.recordMessageReceived() }
            .catch { e ->
                logger.error("Error in message subscription for agent $agentId", e)
                metrics.recordMessageFailed()
            }
    }

    override fun subscribeWithFilter(
        agentId: AgentId,
        filter: MessageFilter
    ): Flow<AgentMessage> {
        return subscribe(agentId).filter { filter.shouldAccept(it) }
    }

    override fun unsubscribe(agentId: AgentId) {
        agentChannels.remove(agentId)?.close()
        queueSizes.remove(agentId)
        messageHandlers.remove(agentId)
        metrics.updateSubscriptionCount(agentChannels.size)
    }

    override suspend fun shutdown() {
        isShutdown = true

        // Close all channels
        agentChannels.values.forEach { it.close() }
        agentChannels.clear()

        // Cancel pending requests
        pendingRequests.values.forEach {
            it.cancelExceptionally(MessageBusException("MessageBus is shutting down"))
        }
        pendingRequests.clear()

        // Cleanup
        messageHandlers.clear()
        queueSizes.clear()
    }

    // Private message handling methods
    private suspend fun handleRequestMessage(message: AgentMessage.Request) {
        val channel = getOrCreateChannel(message.to)

        // Check queue size
        val queueSize = queueSizes[message.to]?.get() ?: 0
        if (queueSize >= config.maxQueueSize) {
            throw MessageBusException.QueueFullException(message.to)
        }

        channel.send(message)
        queueSizes[message.to]?.incrementAndGet()
    }

    private suspend fun handleResponseMessage(message: AgentMessage.Response) {
        // Complete pending request if exists
        pendingRequests[message.originalRequestId]?.complete(message)

        // Also send to subscriber if agent is listening
        getOrCreateChannel(message.to).send(message)
    }

    private suspend fun handleEventMessage(message: AgentMessage.Event) {
        if (message.broadcast) {
            // Send to all registered agents
            agentChannels.forEach { (agentId, channel) ->
                if (agentId != message.from) { // Don't send back to sender
                    channel.send(message)
                }
            }
        } else {
            // Send to specific recipients
            message.recipients.forEach { recipientId ->
                getOrCreateChannel(recipientId).send(message)
            }
        }
    }

    private fun getOrCreateChannel(agentId: AgentId): Channel<AgentMessage> {
        return agentChannels.getOrPut(agentId) {
            Channel(config.maxQueueSize).also { ch ->
                queueSizes[agentId] = AtomicInteger(0)
            }
        }
    }

    private fun validateMessageSize(message: AgentMessage) {
        val size = calculateMessageSize(message)
        if (size > config.maxMessageSize) {
            throw MessageBusException.MessageTooLargeException(size, config.maxMessageSize)
        }
    }

    private fun calculateMessageSize(message: AgentMessage): Int {
        // Simple size estimation - in real implementation would be more sophisticated
        return message.toString().length * 2 // Approximate byte size
    }

    private fun checkNotShutdown() {
        if (isShutdown) {
            throw MessageBusException("MessageBus is shutdown")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InMemoryMessageBus::class.java)
    }
}

// Extension for easy creation
fun MessageBus.Companion.createInMemory(
    config: MessageBusConfig = MessageBusConfig(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
): MessageBus = InMemoryMessageBus(config, scope)
```

**Требования:**
- [ ] Thread-safe реализация с ConcurrentHashMap
- [ ] Channel-based коммуникация с bounded queues
- [ ] Automatic cleanup при отключении агентов
- [ ] Memory usage лимиты с queue size validation
- [ ] Comprehensive metrics collection
- [ ] Proper resource cleanup в shutdown
- [ ] Request-response correlation с CompletableFuture
- [ ] Message size validation
- [ ] Error handling и propagation
- [ ] Graceful degradation под нагрузкой

### 3. Agent Registry and Lifecycle

#### 3.1 AgentRegistry

**Требования:**
- [ ] Регистрация агентов с уникальными AgentId
- [ ] Автоматическая подписка на сообщения
- [ ] Lifecycle management (register/unregister)
- [ ] Health checking для зарегистрированных агентов
- [ ] Capability discovery

#### 3.2 Agent Lifecycle

**Требования:**
- [ ] Автоматическая регистрация при инициализации
- [ ] Graceful shutdown с ожиданием завершения сообщений
- [ ] Restart capability для упавших агентов
- [ ] Configuration updates без перезапуска

### 4. Integration with Existing Architecture

#### 4.1 Agent Interface Extension

```kotlin
// Extended Agent interface with A2A support
interface A2AAgent : Agent {
    val agentId: AgentId
    val messageBus: MessageBus

    // A2A-specific methods
    suspend fun handleA2AMessage(message: AgentMessage): AgentMessage?
    fun getSupportedMessageTypes(): Set<String>
    fun getMessageHandler(): suspend (AgentMessage) -> Unit
}

// Adapter for existing agents
class A2AAgentAdapter(
    private val legacyAgent: Agent,
    private val agentId: AgentId,
    private val messageBus: MessageBus,
    private val capabilities: Set<String> = emptySet()
) : A2AAgent {

    override val agentId: AgentId = this.agentId
    override val messageBus: MessageBus = this.messageBus

    override val capabilities: AgentCapabilities = legacyAgent.capabilities

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
        // Unregister from message bus
        messageBus.unsubscribe(agentId)
    }

    override suspend fun handleA2AMessage(message: AgentMessage): AgentMessage? {
        return when (message) {
            is AgentMessage.Request -> handleA2ARequest(message)
            is AgentMessage.Event -> handleA2AEvent(message)
            else -> null // Ignore Response messages
        }
    }

    override fun getSupportedMessageTypes(): Set<String> {
        return capabilities.ifEmpty {
            setOf("analysis_request", "code_generation_request", "report_generation_request")
        }
    }

    override fun getMessageHandler(): suspend (AgentMessage) -> Unit {
        return { message ->
            try {
                handleA2AMessage(message)?.let { response ->
                    messageBus.send(response)
                }
            } catch (e: Exception) {
                logger.error("Error handling A2A message in agent $agentId", e)

                // Send error response
                val errorResponse = AgentMessage.Response(
                    messageId = MessageId(UUID.randomUUID().toString()),
                    from = agentId,
                    to = message.from,
                    timestamp = Clock.System.now(),
                    payload = ResponsePayload.AnalysisResponse(
                        results = AnalysisResult(
                            summary = AnalysisSummary(0, 0, 0, 0, "ERROR"),
                            detailedFindings = emptyList(),
                            recommendations = emptyList(),
                            qualityScore = 0.0
                        ),
                        executionTime = 0.seconds,
                        agent = agentId.value
                    ),
                    originalRequestId = (message as? AgentMessage.Request)?.messageId ?: MessageId(""),
                    status = ResponseStatus.ERROR,
                    error = ErrorInfo(
                        code = "AGENT_ERROR",
                        message = e.message ?: "Unknown error",
                        cause = e
                    )
                )
                messageBus.send(errorResponse)
            }
        }
    }

    private suspend fun handleA2ARequest(request: AgentMessage.Request): AgentMessage.Response {
        val startTime = System.currentTimeMillis()

        try {
            // Convert A2A Request to AgentRequest
            val agentRequest = convertA2AToAgentRequest(request)

            // Execute using legacy agent
            val agentResponse = legacyAgent.ask(agentRequest)

            // Convert AgentResponse to A2A Response
            return convertAgentResponseToA2A(agentResponse, request, startTime)

        } catch (e: Exception) {
            return AgentMessage.Response(
                messageId = MessageId(UUID.randomUUID().toString()),
                from = agentId,
                to = request.from,
                timestamp = Clock.System.now(),
                payload = ResponsePayload.AnalysisResponse(
                    results = AnalysisResult(
                        summary = AnalysisSummary(0, 0, 0, 0, "ERROR"),
                        detailedFindings = emptyList(),
                        recommendations = emptyList(),
                        qualityScore = 0.0
                    ),
                    executionTime = (System.currentTimeMillis() - startTime).milliseconds,
                    agent = agentId.value
                ),
                originalRequestId = request.messageId,
                status = ResponseStatus.ERROR,
                error = ErrorInfo(
                    code = "PROCESSING_ERROR",
                    message = e.message ?: "Processing failed",
                    cause = e
                )
            )
        }
    }

    private suspend fun handleA2AEvent(event: AgentMessage.Event) {
        // Handle events like status updates, cancellations, etc.
        when (val payload = event.payload) {
            is EventPayload.StatusUpdate -> {
                logger.info("Agent $agentId received status update: ${payload.status}")
            }
            is EventPayload.ErrorOccurred -> {
                logger.error("Agent $agentId received error notification: ${payload.error}")
            }
            else -> {
                logger.debug("Agent $agentId received event: ${payload.type}")
            }
        }
    }

    private fun convertA2AToAgentRequest(a2aRequest: AgentMessage.Request): AgentRequest {
        return when (val payload = a2aRequest.payload) {
            is RequestPayload.AnalysisRequest -> AgentRequest(
                request = "Analyze files: ${payload.files.joinToString()}",
                context = createChatContextFromPayload(payload),
                parameters = LLMParameters.DEFAULT,
                metadata = a2aRequest.metadata + mapOf(
                    "a2a_message_id" to a2aRequest.messageId.value,
                    "analysis_type" to payload.analysisType
                )
            )
            is RequestPayload.CodeGenerationRequest -> AgentRequest(
                request = "Generate code: ${payload.specification}",
                context = createChatContextFromPayload(payload),
                parameters = LLMParameters.DEFAULT,
                metadata = a2aRequest.metadata + mapOf(
                    "a2a_message_id" to a2aRequest.messageId.value,
                    "target_language" to payload.language,
                    "target_path" to payload.targetPath
                )
            )
            else -> AgentRequest(
                request = "Process request: ${payload.type}",
                context = getCurrentContext(),
                parameters = LLMParameters.DEFAULT,
                metadata = a2aRequest.metadata + mapOf(
                    "a2a_message_id" to a2aRequest.messageId.value
                )
            )
        }
    }

    private fun convertAgentResponseToA2A(
        agentResponse: AgentResponse,
        originalRequest: AgentMessage.Request,
        startTime: Long
    ): AgentMessage.Response {
        val executionTime = (System.currentTimeMillis() - startTime).milliseconds

        return if (agentResponse.success) {
            when {
                agentResponse.parsedContent != null -> {
                    AgentMessage.Response(
                        messageId = MessageId(UUID.randomUUID().toString()),
                        from = agentId,
                        to = originalRequest.from,
                        timestamp = Clock.System.now(),
                        payload = ResponsePayload.AnalysisResponse(
                            results = parseResultsFromParsedContent(agentResponse.parsedContent),
                            executionTime = executionTime,
                            agent = agentId.value,
                            metadata = agentResponse.metadata
                        ),
                        originalRequestId = originalRequest.messageId,
                        status = ResponseStatus.SUCCESS
                    )
                }
                agentResponse.formattedOutput != null -> {
                    AgentMessage.Response(
                        messageId = MessageId(UUID.randomUUID().toString()),
                        from = agentId,
                        to = originalRequest.from,
                        timestamp = Clock.System.now(),
                        payload = ResponsePayload.ReportResponse(
                            report = agentResponse.formattedOutput.content,
                            format = ReportFormat.MARKDOWN,
                            size = agentResponse.formattedOutput.content.length,
                            generationTime = executionTime,
                            includedData = setOf("analysis_results"),
                            metadata = agentResponse.metadata
                        ),
                        originalRequestId = originalRequest.messageId,
                        status = ResponseStatus.SUCCESS
                    )
                }
                else -> {
                    AgentMessage.Response(
                        messageId = MessageId(UUID.randomUUID().toString()),
                        from = agentId,
                        to = originalRequest.from,
                        timestamp = Clock.System.now(),
                        payload = ResponsePayload.AnalysisResponse(
                            results = AnalysisResult(
                                summary = AnalysisSummary(0, 0, 0, 0, "SUCCESS"),
                                detailedFindings = emptyList(),
                                recommendations = listOf(agentResponse.content),
                                qualityScore = 1.0
                            ),
                            executionTime = executionTime,
                            agent = agentId.value,
                            metadata = agentResponse.metadata
                        ),
                        originalRequestId = originalRequest.messageId,
                        status = ResponseStatus.SUCCESS
                    )
                }
            }
        } else {
            AgentMessage.Response(
                messageId = MessageId(UUID.randomUUID().toString()),
                from = agentId,
                to = originalRequest.from,
                timestamp = Clock.System.now(),
                payload = ResponsePayload.AnalysisResponse(
                    results = AnalysisResult(
                        summary = AnalysisSummary(0, 0, 0, 0, "ERROR"),
                        detailedFindings = emptyList(),
                        recommendations = emptyList(),
                        qualityScore = 0.0
                    ),
                    executionTime = executionTime,
                    agent = agentId.value,
                    metadata = agentResponse.metadata
                ),
                originalRequestId = originalRequest.messageId,
                status = ResponseStatus.ERROR,
                error = ErrorInfo(
                    code = "AGENT_ERROR",
                    message = agentResponse.error ?: "Unknown error"
                )
            )
        }
    }

    private fun createChatContextFromPayload(payload: MessagePayload): ChatContext {
        // Implementation depends on your current ChatContext structure
        return ChatContext(
            project = getCurrentProject(),
            selectedFiles = emptyList(),
            // Add other context fields as needed
        )
    }

    private fun parseResultsFromParsedContent(parsedContent: ParsedResponse): AnalysisResult {
        // Convert parsed response to AnalysisResult
        return AnalysisResult(
            summary = AnalysisSummary(0, 0, 0, 0, "SUCCESS"),
            detailedFindings = emptyList(),
            recommendations = listOf("Analysis completed"),
            qualityScore = 1.0
        )
    }
}

// Registry for A2A agents
class A2AAgentRegistry(
    private val messageBus: MessageBus
) {
    private val agents = ConcurrentHashMap<AgentId, A2AAgent>()

    suspend fun register(agent: A2AAgent): AgentId {
        val agentId = agent.agentId
        agents[agentId] = agent

        // Subscribe to message bus and start handling
        val messageFlow = messageBus.subscribe(agentId)

        // Start message handling in background
        CoroutineScope(Dispatchers.Default).launch {
            messageFlow.collect { message ->
                try {
                    agent.getMessageHandler()(message)
                } catch (e: Exception) {
                    logger.error("Error processing message in agent $agentId", e)
                }
            }
        }

        return agentId
    }

    suspend fun registerLegacy(agent: Agent, agentId: AgentId? = null): AgentId {
        val a2aAgent = A2AAgentAdapter(
            legacyAgent = agent,
            agentId = agentId ?: AgentId(agent.capabilities.name),
            messageBus = messageBus,
            capabilities = agent.capabilities.supportedFormats
        )

        return register(a2aAgent)
    }

    fun unregister(agentId: AgentId) {
        agents.remove(agentId)?.dispose()
        messageBus.unsubscribe(agentId)
    }

    fun get(agentId: AgentId): A2AAgent? = agents[agentId]
    fun getAll(): Map<AgentId, A2AAgent> = agents.toMap()

    suspend fun shutdown() {
        agents.values.forEach { it.dispose() }
        agents.clear()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(A2AAgentRegistry::class.java)
    }
}
```

**Требования:**
- [ ] Расширение существующего Agent интерфейса без breaking changes
- [ ] Backward compatibility через A2AAgentAdapter
- [ ] Seamless conversion между A2A и legacy message formats
- [ ] Automatic registration и subscription management
- [ ] Proper error handling и error response generation
- [ ] Resource cleanup при agent disposal
- [ ] Support для всех legacy agent capabilities

#### 4.2 EnhancedAgentOrchestrator Integration

**Требования:**
- [ ] Интеграция MessageBus в оркестратор
- [ ] A2A коммуникация для ToolAgent'ов
- [ ] Прогресс трекинг через A2A события
- [ ] Error propagation через A2A

#### 4.3 ChatService Integration

**Требования:**
- [ ] A2A запросы от ChatService к оркестратору
- [ ] UI обновления через A2A события
- [ ] Streaming поддержка через A2A
- [ ] User input routing через A2A

### 5. Advanced Features

#### 5.1 Message Patterns

**Требования:**
- [ ] Request-Response pattern
- [ ] Fire-and-Forget pattern
- [ ] Publish-Subscribe pattern
- [ ] Request-Response с агрегацией
- [ ] Fan-out pattern

#### 5.2 Error Handling and Retries

**Требования:**
- [ ] Exponential backoff retry mechanism
- [ ] Circuit breaker pattern
- [ ] Dead letter queue
- [ ] Error classification (retriable/non-retriable)
- [ ] Fallback mechanisms

#### 5.3 Observability

**Требования:**
- [ ] Message tracing (distributed tracing)
- [ ] Metrics collection (message count, latency, errors)
- [ ] Structured logging
- [ ] Performance monitoring
- [ ] Debug mode с детальной информацией

#### 5.4 Security

**Требования:**
- [ ] Message authentication
- [ ] Access control по AgentId
- [ ] Rate limiting per agent
- [ ] Message size limits
- [ ] Audit logging

### 6. Configuration and Management

#### 6.1 A2A Protocol Configuration

**Требования:**
- [ ] Configurable timeouts
- [ ] Retry policies configuration
- [ ] Message size limits
- [ ] Serialization format selection
- [ ] Feature toggles для advanced functionality

#### 6.2 Runtime Management

**Требования:**
- [ ] Runtime agent registration/deregistration
- [ ] Message flow control
- [ ] Memory usage monitoring
- [ ] Performance tuning parameters
- [ ] Hot configuration updates

## Нефункциональные требования

### Performance

- **Latency**: < 10ms для in-memory сообщений
- **Throughput**: > 1000 messages/sec
- **Memory overhead**: < 1% от общего потребления памяти
- **CPU overhead**: < 2% от общего потребления CPU

### Reliability

- **Availability**: 99.9% uptime для MessageBus
- **Message loss**: 0% для in-memory коммуникации
- **Recovery time**: < 1s для agent restart
- **Data consistency**: Strong consistency в рамках сессии

### Scalability

- **Agent capacity**: До 100 одновременных агентов
- **Message queue**: Неограниченная очередь в рамках memory limits
- **Concurrent connections**: Поддержка множественных agent instances
- **Horizontal scaling**: Возможность распределения по JVM процессам

### Security

- **Message integrity**: Checksums для критических сообщений
- **Access control**: Role-based доступ к агентам
- **Audit trail**: Полный лог всех коммуникаций
- **Memory safety**: Предотвращение memory leaks через сообщения

## Критерии приемки (Acceptance Criteria)

### Core Functionality

- [ ] Все типы сообщений (Request/Response/Event) корректно обрабатываются
- [ ] MessageBus обеспечивает доставку сообщений между зарегистрированными агентами
- [ ] Request-Response pattern работает с configurable timeouts
- [ ] Broadcast события доставляются всем подписчикам
- [ ] Сериализация/десериализация работает без потерь данных

### Integration

- [ ] Существующие агенты работают без изменений (backward compatibility)
- [ ] EnhancedAgentOrchестратор использует A2A для ToolAgent коммуникации
- [ ] ChatService интегрирован с A2A протоколом
- [ ] ReportGenerator агент собирает результаты через A2A
- [ ] UI обновления приходят через A2A события

### Performance and Reliability

- [ ] Latency тесты показывают < 10ms для in-memory сообщений
- [ ] Load тесты подтверждают > 1000 messages/sec throughput
- [ ] Memory usage остается стабильным при длительной работе
- [ ] Failed messages корректно обрабатываются и логируются
- [ ] System восстанавливается после agent failures

### Observability

- [ ] Все сообщения трассируются с уникальными ID
- [ ] Metrics доступны для monitoring (count, latency, errors)
- [ ] Debug mode предоставляет детальную информацию о flow
- [ ] Error scenarios корректно отображаются в логах
- [ ] Performance bottlenecks выявляются через monitoring

## Риски и митигации

### Technical Risks

1. **Memory leaks в message queues**
   - Митигация: Automatic cleanup, size limits, monitoring

2. **Deadlocks в agent коммуникации**
   - Митигация: Timeout механизмы, circuit breakers

3. **Performance degradation при большом количестве агентов**
   - Митигация: Efficient routing, lazy subscription, batching

4. **Breaking changes для существующих агентов**
   - Митигация: Adapter pattern, gradual migration path

### Operational Risks

1. **Message loss при system failures**
   - Митигация: Persistence для критических сообщений, replay mechanisms

2. **Agent failures affecting system stability**
   - Митигация: Isolation patterns, health checking, graceful degradation

3. **Complexity в debugging distributed issues**
   - Митигация: Comprehensive tracing, correlation IDs, structured logging

## Зависимости

### Dependencies

- **Kotlin Coroutines**: Асинхронная обработка сообщений
- **Kotlinx Serialization**: JSON/XML сериализация
- **IntelliJ Platform**: Integration с существующей архитектурой
- **Joda-Time**: Timestamp handling
- **Existing Agent System**: Base interfaces и registry

### Prerequisites

1. **Enhanced Agent Orchestrator**: Должен быть реализован
2. **ToolAgent Registry**: Существующая система регистрации агентов
3. **ChatService**: Текущая интеграция с UI
4. **LLM Provider Integration**: Существующие LLM абстракции

## Testing Strategy

### Unit Tests

- **Message serialization/deserialization**: Все типы payload
- **MessageBus functionality**: Send, receive, broadcast, request-response
- **Agent registration/unregistration**: Lifecycle management
- **Error handling**: Invalid messages, timeouts, failures

### Integration Tests

- **End-to-end agent communication**: Полный flow через A2A
- **Orchestrator integration**: ToolAgent execution через A2A
- **ChatService integration**: User request processing через A2A
- **UI updates**: Event propagation через A2A

### Performance Tests

- **Latency benchmarks**: Various message sizes и agent loads
- **Throughput tests**: Maximum messages per second
- **Memory usage**: Long-running stability tests
- **Concurrent access**: Multiple agents communication

### Chaos Tests

- **Agent failures**: Random agent crashes
- **Message corruption**: Invalid data handling
- **Resource exhaustion**: Memory/CPU pressure
- **Network partitions**: Simulated communication failures

## Документация

### Developer Documentation

- **A2A Protocol Specification**: Детальное описание протокола
- **Agent Development Guide**: Как создавать A2A-compatible агентов
- **Migration Guide**: Переход существующих агентов на A2A
- **Configuration Reference**: Все параметры конфигурации
- **Troubleshooting Guide**: Common issues и решения

### Operational Documentation

- **Monitoring Guide**: Метрики и алерты
- **Performance Tuning**: Оптимизация производительности
- **Security Guidelines**: Безопасность коммуникаций
- **Backup and Recovery**: Procedures для critical scenarios

## Timeline и Milestones

Реализация фичи планируется в рамках phased approach:

1. **Phase 1**: Core A2A Protocol и MessageBus (2 недели)
2. **Phase 2**: Agent Registry и Integration (1 неделя)
3. **Phase 3**: Advanced Features и Observability (1 неделя)
4. **Phase 4**: Testing, Documentation и Rollout (1 неделя)

Подробный план в отдельном roadmap документе.