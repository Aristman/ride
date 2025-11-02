# Design Document

## Overview

Данный документ описывает архитектурный дизайн для внедрения A2A (Agent-to-Agent) протокола в существующую мультиагентскую систему плагина Ride. Дизайн обеспечивает бесшовную интеграцию с существующими агентами через адаптер-паттерн, централизованную шину сообщений для надежной коммуникации, и расширенную систему типизированных payload'ов для различных доменных задач.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        A2A Protocol Layer                       │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐ │
│  │   A2AAgent  │    │  MessageBus  │    │  A2AAgentRegistry   │ │
│  │ Interface   │◄──►│   (Central   │◄──►│   (Lifecycle)       │ │
│  │             │    │   Router)    │    │                     │ │
│  └─────────────┘    └──────────────┘    └─────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                    Integration Layer                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐ │
│  │A2AAdapter   │    │ Enhanced     │    │   ChatService       │ │
│  │(Legacy      │◄──►│ Orchestrator │◄──►│   Integration       │ │
│  │Integration) │    │              │    │                     │ │
│  └─────────────┘    └──────────────┘    └─────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                    Existing Agent Layer                        │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐ │
│  │ ToolAgents  │    │ ChatAgent    │    │   PlannerAgent      │ │
│  │(Scanner,    │    │(Enhanced)    │    │   ExecutorAgent     │ │
│  │Quality,Bug) │    │              │    │                     │ │
│  └─────────────┘    └──────────────┘    └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Message Flow Architecture

```
Agent A                MessageBus              Agent B
   │                       │                      │
   │──── Request ──────────►│                      │
   │                       │──── Route ──────────►│
   │                       │                      │
   │                       │◄─── Response ────────│
   │◄─── Response ─────────│                      │
   │                       │                      │
   │──── Event ────────────►│                      │
   │                       │──── Broadcast ──────►│
   │                       │──── Broadcast ──────►│ (Other Agents)
```

## Components and Interfaces

### Core A2A Protocol Components

#### 1. AgentMessage Hierarchy

```kotlin
// Core message abstraction
sealed class AgentMessage {
    abstract val messageId: MessageId
    abstract val from: AgentId
    abstract val to: AgentId
    abstract val timestamp: Instant
    abstract val payload: MessagePayload
    abstract val version: String
    abstract val metadata: Map<String, Any>
}

// Specialized message types
data class AgentRequest(
    // ... AgentMessage fields
    val timeout: Duration = 30.seconds,
    val priority: Priority = Priority.NORMAL,
    override val payload: RequestPayload
) : AgentMessage()

data class AgentResponse(
    // ... AgentMessage fields
    val originalRequestId: MessageId,
    val status: ResponseStatus,
    val error: ErrorInfo? = null,
    override val payload: ResponsePayload
) : AgentMessage()

data class AgentEvent(
    // ... AgentMessage fields
    val broadcast: Boolean = false,
    val recipients: Set<AgentId> = emptySet(),
    override val payload: EventPayload
) : AgentMessage()
```

#### 2. MessageBus Interface

```kotlin
interface MessageBus {
    // Core messaging operations
    suspend fun send(message: AgentMessage)
    suspend fun sendToMultiple(message: AgentMessage, recipients: Set<AgentId>)
    suspend fun broadcast(event: AgentEvent)
    
    // Request-Response pattern
    suspend fun requestResponse(
        request: AgentRequest,
        timeout: Duration = 30.seconds
    ): AgentResponse
    
    // Subscription management
    fun subscribe(agentId: AgentId): Flow<AgentMessage>
    fun subscribeWithFilter(agentId: AgentId, filter: MessageFilter): Flow<AgentMessage>
    fun unsubscribe(agentId: AgentId)
    
    // Health and metrics
    val isHealthy: Boolean
    val metrics: MessageBusMetrics
    
    suspend fun shutdown()
}
```

#### 3. A2AAgent Interface

```kotlin
interface A2AAgent : Agent {
    val agentId: AgentId
    val messageBus: MessageBus
    
    // A2A-specific methods
    suspend fun handleA2AMessage(message: AgentMessage): AgentMessage?
    fun getSupportedMessageTypes(): Set<String>
    fun getMessageHandler(): suspend (AgentMessage) -> Unit
    
    // Lifecycle
    suspend fun startA2AListening()
    suspend fun stopA2AListening()
}
```

### Integration Components

#### 1. A2AAgentAdapter

```kotlin
class A2AAgentAdapter(
    private val legacyAgent: Agent,
    override val agentId: AgentId,
    override val messageBus: MessageBus
) : A2AAgent {
    
    // Conversion methods
    private suspend fun convertA2AToAgentRequest(a2aRequest: AgentRequest): AgentRequest
    private fun convertAgentResponseToA2A(response: AgentResponse, originalRequest: AgentRequest): AgentResponse
    
    // Message handling
    override suspend fun handleA2AMessage(message: AgentMessage): AgentMessage? {
        return when (message) {
            is AgentRequest -> handleA2ARequest(message)
            is AgentEvent -> handleA2AEvent(message)
            else -> null
        }
    }
}
```

#### 2. Enhanced Orchestrator Integration

```kotlin
class A2AEnhancedOrchestrator(
    private val messageBus: MessageBus,
    private val agentRegistry: A2AAgentRegistry,
    // ... existing dependencies
) : EnhancedAgentOrchestrator {
    
    // A2A-enhanced plan execution
    override suspend fun executePlan(plan: ExecutionPlan): AgentResponse {
        // Subscribe to agent events
        val eventFlow = messageBus.subscribeWithFilter(
            orchestratorId,
            MessageTypeFilter(setOf("status_update", "completion_notification", "error_occurred"))
        )
        
        // Execute steps via A2A
        for (step in plan.steps) {
            val agent = agentRegistry.get(step.agentType.toAgentId())
            val request = createA2ARequest(step)
            val response = messageBus.requestResponse(request)
            // Process response...
        }
    }
}
```

## Data Models

### Message Payload Types

#### 1. Domain-Specific Payloads

```kotlin
// Code analysis results
data class CodeAnalysisResult(
    val findings: List<CodeFinding>,
    val metrics: AnalysisMetrics,
    val analyzedFiles: List<String>,
    val analysisType: String,
    override val metadata: Map<String, Any> = emptyMap()
) : ResponsePayload() {
    override val type = "code_analysis_result"
}

// Project structure data
data class ProjectStructure(
    val files: List<ProjectFile>,
    val dependencies: List<Dependency>,
    val modules: List<ProjectModule>,
    val statistics: ProjectStatistics,
    override val metadata: Map<String, Any> = emptyMap()
) : ResponsePayload() {
    override val type = "project_structure"
}

// Status updates
data class StatusUpdate(
    val status: AgentStatus,
    val progress: Double? = null,
    val currentStep: String? = null,
    val estimatedTimeRemaining: Duration? = null,
    override val metadata: Map<String, Any> = emptyMap()
) : EventPayload() {
    override val type = "status_update"
}
```

#### 2. Request Payloads

```kotlin
// Analysis request
data class AnalysisRequest(
    val files: List<String>,
    val analysisType: String,
    val configuration: Map<String, Any> = emptyMap(),
    override val metadata: Map<String, Any> = emptyMap()
) : RequestPayload() {
    override val type = "analysis_request"
}

// File data request
data class FileDataRequest(
    val filePaths: List<String>,
    val includeContent: Boolean = false,
    val maxFileSize: Long = 1024 * 1024, // 1MB
    override val metadata: Map<String, Any> = emptyMap()
) : RequestPayload() {
    override val type = "file_data_request"
}
```

### Agent Registry Model

```kotlin
class A2AAgentRegistry(
    private val messageBus: MessageBus
) {
    private val agents = ConcurrentHashMap<AgentId, A2AAgent>()
    private val capabilities = ConcurrentHashMap<AgentId, Set<String>>()
    private val subscriptions = ConcurrentHashMap<AgentId, Job>()
    
    suspend fun register(agent: A2AAgent): AgentId {
        val agentId = agent.agentId
        agents[agentId] = agent
        capabilities[agentId] = agent.getSupportedMessageTypes()
        
        // Start message handling
        val subscription = startMessageHandling(agent)
        subscriptions[agentId] = subscription
        
        return agentId
    }
    
    private fun startMessageHandling(agent: A2AAgent): Job {
        return CoroutineScope(Dispatchers.Default).launch {
            messageBus.subscribe(agent.agentId).collect { message ->
                try {
                    agent.getMessageHandler()(message)
                } catch (e: Exception) {
                    handleAgentError(agent.agentId, e, message)
                }
            }
        }
    }
}
```

## Error Handling

### Error Classification

```kotlin
sealed class A2AError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    // Message routing errors
    class AgentNotFound(agentId: AgentId) : A2AError("Agent $agentId not found")
    class MessageTimeout(messageId: MessageId) : A2AError("Message $messageId timed out")
    class QueueFull(agentId: AgentId) : A2AError("Queue for agent $agentId is full")
    
    // Serialization errors
    class SerializationError(message: String, cause: Throwable) : A2AError(message, cause)
    class InvalidPayload(payloadType: String) : A2AError("Invalid payload type: $payloadType")
    
    // Agent errors
    class AgentUnavailable(agentId: AgentId) : A2AError("Agent $agentId is unavailable")
    class ProcessingError(agentId: AgentId, cause: Throwable) : A2AError("Processing error in agent $agentId", cause)
}
```

### Error Recovery Strategies

```kotlin
class ErrorRecoveryManager {
    suspend fun handleError(error: A2AError, context: MessageContext): RecoveryAction {
        return when (error) {
            is A2AError.MessageTimeout -> RecoveryAction.Retry(maxAttempts = 3, backoff = ExponentialBackoff())
            is A2AError.AgentUnavailable -> RecoveryAction.RouteToAlternative
            is A2AError.QueueFull -> RecoveryAction.Backpressure(delay = 1.seconds)
            is A2AError.SerializationError -> RecoveryAction.Fail(error)
            else -> RecoveryAction.Fail(error)
        }
    }
}
```

## Testing Strategy

### Unit Testing Approach

```kotlin
class MessageBusTest {
    @Test
    fun `should deliver message to registered agent`() = runTest {
        val messageBus = InMemoryMessageBus()
        val agentId = AgentId("test-agent")
        val message = createTestRequest(to = agentId)
        
        val receivedMessages = mutableListOf<AgentMessage>()
        messageBus.subscribe(agentId).collect { receivedMessages.add(it) }
        
        messageBus.send(message)
        
        assertEquals(1, receivedMessages.size)
        assertEquals(message.messageId, receivedMessages.first().messageId)
    }
    
    @Test
    fun `should handle request-response pattern`() = runTest {
        val messageBus = InMemoryMessageBus()
        val request = createTestRequest()
        
        // Mock agent response
        launch {
            messageBus.subscribe(request.to).collect { message ->
                if (message is AgentRequest) {
                    val response = createTestResponse(originalRequestId = message.messageId)
                    messageBus.send(response)
                }
            }
        }
        
        val response = messageBus.requestResponse(request)
        assertEquals(request.messageId, response.originalRequestId)
    }
}
```

### Integration Testing

```kotlin
class A2AIntegrationTest {
    @Test
    fun `should integrate legacy agent through adapter`() = runTest {
        val legacyAgent = MockToolAgent()
        val messageBus = InMemoryMessageBus()
        val adapter = A2AAgentAdapter(legacyAgent, AgentId("legacy"), messageBus)
        
        val registry = A2AAgentRegistry(messageBus)
        registry.register(adapter)
        
        val request = AnalysisRequest(files = listOf("test.kt"), analysisType = "quality")
        val a2aRequest = AgentRequest(
            messageId = MessageId.generate(),
            from = AgentId("test"),
            to = adapter.agentId,
            payload = request
        )
        
        val response = messageBus.requestResponse(a2aRequest)
        assertTrue(response.status == ResponseStatus.SUCCESS)
    }
}
```

### Performance Testing

```kotlin
class A2APerformanceTest {
    @Test
    fun `should achieve target latency`() = runTest {
        val messageBus = InMemoryMessageBus()
        val agentId = AgentId("perf-agent")
        
        val latencies = mutableListOf<Long>()
        
        repeat(1000) {
            val startTime = System.nanoTime()
            val message = createTestRequest(to = agentId)
            messageBus.send(message)
            val endTime = System.nanoTime()
            
            latencies.add((endTime - startTime) / 1_000_000) // Convert to ms
        }
        
        val averageLatency = latencies.average()
        assertTrue("Average latency $averageLatency ms should be < 10ms", averageLatency < 10.0)
    }
    
    @Test
    fun `should achieve target throughput`() = runTest {
        val messageBus = InMemoryMessageBus()
        val agentId = AgentId("throughput-agent")
        
        val messageCount = 10000
        val startTime = System.currentTimeMillis()
        
        repeat(messageCount) {
            val message = createTestRequest(to = agentId)
            messageBus.send(message)
        }
        
        val endTime = System.currentTimeMillis()
        val throughput = messageCount / ((endTime - startTime) / 1000.0)
        
        assertTrue("Throughput $throughput msg/sec should be > 1000", throughput > 1000.0)
    }
}
```

## Migration Strategy

### Phase 1: Core Infrastructure

1. **MessageBus Implementation**
   - InMemoryMessageBus with basic routing
   - Message serialization/deserialization
   - Basic error handling

2. **A2AAgent Interface**
   - Core interface definition
   - Basic message handling patterns
   - Integration with existing Agent interface

### Phase 2: Legacy Integration

1. **A2AAgentAdapter**
   - Conversion between A2A and legacy formats
   - Backward compatibility preservation
   - Error handling and logging

2. **Registry Integration**
   - A2AAgentRegistry implementation
   - Automatic agent discovery
   - Lifecycle management

### Phase 3: Enhanced Features

1. **Advanced Message Patterns**
   - Request-Response with correlation
   - Broadcast events
   - Message filtering and routing

2. **Observability**
   - Metrics collection
   - Distributed tracing
   - Debug mode and logging

### Phase 4: Orchestrator Integration

1. **Enhanced Orchestrator**
   - A2A-based plan execution
   - Event-driven progress tracking
   - Error propagation and recovery

2. **ChatService Integration**
   - UI updates through A2A events
   - Real-time status streaming
   - User interaction routing

## Security Considerations

### Message Authentication

```kotlin
interface MessageAuthenticator {
    fun authenticate(message: AgentMessage, sender: AgentId): Boolean
    fun sign(message: AgentMessage, sender: AgentId): String
    fun verify(message: AgentMessage, signature: String): Boolean
}
```

### Access Control

```kotlin
class A2AAccessController {
    private val permissions = ConcurrentHashMap<AgentId, Set<Permission>>()
    
    fun canSendTo(from: AgentId, to: AgentId, messageType: String): Boolean {
        val senderPermissions = permissions[from] ?: return false
        return senderPermissions.any { it.allows(to, messageType) }
    }
}
```

### Rate Limiting

```kotlin
class RateLimiter {
    private val buckets = ConcurrentHashMap<AgentId, TokenBucket>()
    
    suspend fun checkLimit(agentId: AgentId): Boolean {
        val bucket = buckets.getOrPut(agentId) { TokenBucket(capacity = 100, refillRate = 10) }
        return bucket.tryConsume()
    }
}
```

## Monitoring and Observability

### Metrics Collection

```kotlin
data class A2AMetrics(
    val messagesSent: Counter,
    val messagesReceived: Counter,
    val messageLatency: Histogram,
    val activeAgents: Gauge,
    val queueSizes: Gauge,
    val errorRate: Counter
)
```

### Distributed Tracing

```kotlin
class A2ATracer {
    fun startTrace(message: AgentMessage): TraceContext {
        return TraceContext(
            traceId = message.metadata["trace_id"] as? String ?: generateTraceId(),
            spanId = generateSpanId(),
            parentSpanId = message.metadata["parent_span_id"] as? String
        )
    }
    
    fun finishTrace(context: TraceContext, result: TraceResult) {
        // Send trace data to monitoring system
    }
}
```

Данный дизайн обеспечивает полную интеграцию A2A протокола с существующей системой агентов, сохраняя обратную совместимость и добавляя мощные возможности для прямой коммуникации между агентами.