# Техническое задание (ТЗ): Внедрение унифицированного взаимодействия между агентами в мультиагентской системе Ride

## 1. Введение и цели

- **Цель**: внедрить единый протокол взаимодействия между агентами, систему оркестрации и координации, а также унифицированный контекст выполнения, обеспечив масштабируемость, отказоустойчивость и наблюдаемость в духе Koog, без жёсткой привязки к стороннему фреймворку.
- **Результат**: агенты проекта `Ride` взаимодействуют через типобезопасные сообщения, поддерживают параллельное/цепочное выполнение, retry/fallback, метрики и прогресс, с минимальными изменениями в существующем API `Agent` и интеграцией с `EnhancedAgentOrchestrator`.

## 2. Область работ (Scope)

- **Протокол сообщений**: `AgentMessage` + payloadы запросов/ответов.
- **Асинхронная шина**: `MessageBus` с request-response, broadcast и streaming (Flow).
- **Оркестрация**: расширение `EnhancedAgentOrchestrator` для выбора агентов, параллельных цепочек, retry-логики.
- **Координация**: `AgentCoordinator` для декомпозиции, назначения и исполнения с графом зависимостей.
- **Единый контекст**: `UnifiedContext` + `SharedState`.
- **Реестр агентов**: расширение `ToolAgentRegistry` → `EnhancedAgentRegistry` (метрики/доступность/поиск).
- **Мониторинг и метрики**: сбор производительности и статусов.
- **Тестирование**: модульные (JUnit4), интеграционные, нагрузочные.

Не входит: миграция на Koog. Поддерживается гибридная совместимость (адаптеры возможны отдельно).

## 3. Текущая архитектура и интеграция

Существующие компоненты:
- `ru.marslab.ide.ride.agent.Agent` — единый интерфейс агентов.
- `ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator` — многошаговое выполнение, прогресс, персистентность планов.
- `ru.marslab.ide.ride.agent.ToolAgentRegistry` — регистрация Tool Agents.
- UI-прогресс: `ChatService` + `JcefChatView`, `ToolAgentProgressListener`.

Интеграция ТЗ:
- Протокол сообщений и шина не ломают `Agent`, добавляют внутреннюю шину для межагентных коммуникаций в рамках оркестратора/координатора.
- `EnhancedAgentOrchestrator` получает новые возможности: выбор агентов, параллель/цепочки, retries через обобщённые шаги/сообщения.
- `ToolAgentRegistry` дополняется метриками/доступностью (новый интерфейс, совместимый адаптер).

## 4. Протокол сообщений

```kotlin
sealed class AgentMessage {
    abstract val messageId: String
    abstract val timestamp: Instant
    abstract val sender: AgentId
    abstract val recipient: AgentId
    abstract val context: MessageContext

    data class RequestMessage(
        override val messageId: String,
        override val sender: AgentId,
        override val recipient: AgentId,
        override val context: MessageContext,
        val payload: RequestPayload,
        val expectedResponseType: ResponseType,
        val timeout: Duration = Duration.seconds(30)
    ) : AgentMessage()

    data class ResponseMessage(
        override val messageId: String,
        override val sender: AgentId,
        override val recipient: AgentId,
        override val context: MessageContext,
        val payload: ResponsePayload,
        val requestMessageId: String
    ) : AgentMessage()

    data class EventMessage(
        override val messageId: String,
        override val sender: AgentId,
        override val recipient: AgentId,
        override val context: MessageContext,
        val eventType: EventType,
        val eventData: JsonElement
    ) : AgentMessage()
}
```

Payloadы (типобезопасные):

```kotlin
sealed class RequestPayload {
    data class TaskRequest(val taskType: TaskType, val input: TaskInput, val requirements: TaskRequirements) : RequestPayload()
    data class QueryRequest(val query: String, val searchScope: SearchScope, val format: ResponseFormat) : RequestPayload()
    data class CoordinationRequest(val plan: ExecutionPlan, val role: CoordinationRole) : RequestPayload()
}

sealed class ResponsePayload {
    data class TaskResult(val output: TaskOutput, val metadata: TaskMetadata, val nextSteps: List<TaskStep> = emptyList()) : ResponsePayload()
    data class QueryResult(val results: List<QueryResult>, val totalCount: Int, val hasMore: Boolean) : ResponsePayload()
    data class CoordinationResponse(val status: CoordinationStatus, val assignments: Map<AgentId, List<TaskStep>>) : ResponsePayload()
}
```

Ответы агентов (унифицированная обёртка):

```kotlin
sealed class AgentResponse<T> {
    abstract val metadata: ResponseMetadata
    abstract val timestamp: Instant

    data class Success<T>(val data: T, val confidence: ConfidenceLevel, val uncertainty: UncertaintyAnalysis,
                          override val metadata: ResponseMetadata, override val timestamp: Instant = Instant.now()) : AgentResponse<T>()
    data class Partial<T>(val data: T, val nextSteps: List<ActionStep>, val requiresClarification: Boolean,
                          override val metadata: ResponseMetadata, override val timestamp: Instant = Instant.now()) : AgentResponse<T>()
    data class Error<T>(val error: AgentError, val recoverySuggestions: List<RecoveryAction>,
                        override val metadata: ResponseMetadata, override val timestamp: Instant = Instant.now()) : AgentResponse<T>()
    data class Streaming<T>(val stream: Flow<StreamChunk<T>>, val estimatedCompletion: Instant?,
                            override val metadata: ResponseMetadata, override val timestamp: Instant = Instant.now()) : AgentResponse<T>()
}
```

Сериализация: `kotlinx-serialization-json` (имеется в проекте).

## 5. Асинхронная шина сообщений

API:

```kotlin
interface MessageBus {
    suspend fun send(message: AgentMessage): DeliveryResult
    suspend fun broadcast(message: AgentMessage, recipients: List<AgentId>)
    suspend fun subscribe(agentId: AgentId): Flow<AgentMessage>
    suspend fun requestResponse(message: AgentMessage.RequestMessage): AgentMessage.ResponseMessage
}
```

Реализация MVP: in-memory на `kotlinx.coroutines.channels.Channel`, request-response через `withTimeout`. Возможность последующей замены транспорта (Ktor WS/HTTP) без изменения интерфейса.

## 6. Оркестрация и координация

- `EnhancedAgentOrchestrator` дополняется методами:
  - `selectAgents(task: TaskDescription): List<AgentSelection>`
  - `executeParallel(tasks: List<TaskStep>): ParallelExecutionResult`
  - `executeChain(chain: TaskChain): ChainExecutionResult`
  - `executeWithRetry(task: TaskStep, maxRetries: Int = 3): TaskResult`
- `AgentCoordinator` отвечает за декомпозицию задач, назначения, построение графа зависимостей и исполнение с учетом зависимостей.
- Сигнализация прогресса и статусов — через существующий `ToolAgentProgressListener`/`ChatService`.

## 7. Единый контекст выполнения

```kotlin
data class UnifiedContext(
    val sessionId: String,
    val taskContext: TaskContext,
    val projectContext: ProjectContext,
    val conversationHistory: List<ContextMessage>,
    val sharedState: SharedState,
    val resources: ResourceRegistry
) {
    fun withNewMessage(message: ContextMessage): UnifiedContext = copy(conversationHistory = conversationHistory + message)
    fun withUpdatedState(updates: Map<String, Any>): UnifiedContext = copy(sharedState = sharedState.update(updates))
    fun getRelevantHistory(agentType: AgentType): List<ContextMessage> = conversationHistory.filter { it.isRelevantTo(agentType) }
}

data class SharedState(
    val variables: Map<String, JsonElement>,
    val artifacts: Map<String, Artifact>,
    val dependencies: Map<String, Set<String>>
) {
    fun update(updates: Map<String, Any>): SharedState {
        val newVariables = variables + updates.mapValues { Json.encodeToJsonElement(it.value) }
        return copy(variables = newVariables)
    }
}
```

## 8. Реестр агентов (расширение)

```kotlin
interface EnhancedAgentRegistry {
    fun register(agent: Agent, capabilities: AgentCapabilities): RegistrationResult
    fun unregister(agentId: AgentId): UnregistrationResult
    fun findAgents(criteria: SearchCriteria): List<AgentInfo>
    fun findBestAgent(requirements: AgentRequirements): AgentInfo?
    fun updateCapabilities(agentId: AgentId, capabilities: AgentCapabilities)
    fun getPerformanceMetrics(agentId: AgentId): PerformanceMetrics?
    fun getAvailabilityStatus(agentId: AgentId): AvailabilityStatus
}
```

Совместимость: реализовать адаптер поверх `ToolAgentRegistry` для возврата базовых метрик/доступности; постепенно расширять хранение метрик.

## 9. Наблюдаемость и метрики

- Метрики: среднее время ответа, успех/ошибка, пропускная способность, использование ресурсов.
- Трассировка оркестратора: переходы состояний плана, статусы шагов, retry/loop.
- Интеграция с UI: прогресс-ивенты уже показаны через `ChatService`.
- В перспективе: OpenTelemetry-совместимый экспорт (опционально).

## 10. Нефункциональные требования

- **Производительность**: параллельное выполнение шагов там, где нет зависимостей; среднее время ответа не ухудшается против текущей реализации.
- **Надёжность**: retry с экспоненциальной паузой, fallback сообщений об ошибках, контроль таймаутов.
- **Масштабируемость**: независимость транспорта шины, возможность вынесения в процесс/сеть.
- **Безопасность**: строгая типизация payloadов, проверка прав на операции с файлами (через существующие агенты/слой доступа).
- **Совместимость**: не ломать публичный интерфейс `Agent` и текущую интеграцию UI.

## 11. Требования к тестированию

- **Фреймворк**: JUnit4 (добавить зависимости в Gradle), `kotlinx-coroutines-test`, `mockk`.
- **Юнит-тесты**:
  - `MessageBus`: отправка, подписка, request-response, таймауты.
  - Сериализация/десериализация `AgentMessage`/payloadов.
  - Оркестратор: выбор агентов, параллель, цепочки, retry.
  - Координатор: построение графа зависимостей, корректное исполнение.
- **Интеграционные**:
  - Взаимодействие `EnhancedAgentOrchestrator` + `AgentCoordinator` + реестр.
  - Прогресс-ивенты в `ChatService`.
- **Нагрузочные**: параллель до N агентов/шагов, измерение метрик.
- **Покрытие**: ≥80% по ключевым модулям шины/оркестратора.

## 12. Критерии приёмки (DoD)

- Реализован и задокументирован `MessageBus` с unit-тестами.
- Поддержаны `RequestMessage`/`ResponseMessage`/`EventMessage` и сериализация JSON.
- Доработан `EnhancedAgentOrchestrator`: выбор агентов, параллель/цепочки, retry.
- Реализован `AgentCoordinator` с декомпозицией и графом зависимостей.
- Добавлен `UnifiedContext` и интегрирован в оркестратор/агентов.
- Расширен реестр агентов до `EnhancedAgentRegistry` (адаптер + метрики).
- Метрики и прогресс отображаются в UI.
- Написаны тесты (JUnit4), покрытие и стабильность подтверждены.
- Документация в `docs/` обновлена.

## 13. План работ и артефакты

- Этап 1 (2–3 недели): модели сообщений, `MessageBus`, адаптация `Agent`/`EnhancedAgentOrchestrator` API.
- Этап 2 (3–4 недели): `AgentCoordinator`, `UnifiedContext`, базовая параллель/цепочки.
- Этап 3 (2–3 недели): retry/fallback, расширенные метрики, кэширование результатов.
- Этап 4 (1–2 недели): оптимизация, нагрузочные тесты, документация.

Артефакты: исходники модулей, тесты, документация, примеры использования, отчёт о метриках.

## 14. Архитектурные принципы

- **SOLID**: разделение ответственности между протоколом, шиной, оркестратором, координатором, реестром.
- **Clean Architecture**: доменные модели/интерфейсы изолированы от транспорта/инфраструктуры.
- Расширяемость интерфейсов, инъекция зависимостей, минимизация связности.

## 15. Риски и меры

- Конкурентные гонки в шине/оркестраторе → корутины + тщательные тесты, акторно-канальная модель.
- Увеличение сложности → поэтапная поставка, чёткие интерфейсы.
- Производительность при параллели → ограничители, пулы, профилирование.
- Совместимость с IntelliJ Platform → in-memory транспорт, контроль версий корутин, отсутствие тяжёлых HTTP зависимостей в MVP.

## 16. Открытые вопросы (на утверждение)

1. Транспорт в MVP: закрепляем in-memory (Channel) или сразу закладываем Ktor WS как альтернативу в build.gradle?
2. Требуется ли persistence для шины (журнал/повторная доставка) на первом этапе?
3. Нужны ли кросс-сессионные взаимодействия агентов (между чатами) на старте?
4. Детали метрик: где хранить и как отображать агрегаты (локально/экспорт)?
5. Ограничения параллельности по умолчанию (глобальные/на план/на агента)?

— После утверждения вопросов начнём реализацию согласно роадмапу `docs/roadmaps/12-multi-agent-interaction.md`.
