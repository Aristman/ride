# Этап 1: Расширение базовой архитектуры

**Длительность:** 2-3 недели
**Приоритет:** Высокий
**Статус:** ✅ Выполнено

---

## 🎯 Цели этапа

Создать фундаментальные компоненты для расширенного оркестратора:
- Анализ и классификация пользовательских запросов
- Управление состояниями плана выполнения
- Персистентность планов
- Детальное отслеживание прогресса

---

## 📋 Задачи

### 1.1 RequestAnalyzer
- [x] Создать интерфейс `RequestAnalyzer`
- [x] Реализовать `LLMRequestAnalyzer` с использованием LLM
- [x] Добавить классификацию типов задач (CODE_ANALYSIS, REFACTORING, BUG_FIX, etc.)
- [x] Реализовать извлечение контекста и параметров из запроса
- [x] Определение необходимых Tool Agents для задачи
- [x] Интеграция с `UncertaintyAnalyzer`

### 1.2 PlanStateMachine
- [x] Создать `PlanStateMachine` для управления состояниями
- [x] Определить все возможные состояния плана (CREATED, ANALYZING, IN_PROGRESS, PAUSED, REQUIRES_INPUT, COMPLETED, FAILED)
- [x] Реализовать валидацию переходов между состояниями
- [x] Добавить обработку событий (Start, Pause, Resume, Error, etc.)
- [x] Интегрировать с `AgentOrchestrator`
- [x] Добавить listeners для уведомлений о смене состояний

### 1.3 PlanStorage
- [x] Разработать интерфейс `PlanStorage`
- [x] Реализовать `InMemoryPlanStorage` для MVP
- [x] Добавить сериализацию/десериализацию планов (JSON)
- [x] Реализовать `PersistentPlanStorage` с сохранением на диск
- [x] Добавить версионирование планов
- [x] Реализовать очистку старых планов

### 1.4 ProgressTracker
- [x] Создать `ProgressTracker` для детального отслеживания
- [x] Добавить расчет прогресса выполнения (%)
- [x] Реализовать оценку времени завершения (ETA)
- [x] Добавить события прогресса для UI
- [x] Интегрировать с `OrchestratorStep`
- [x] Добавить историю выполнения шагов

---

## 📚 Ключевые интерфейсы

### RequestAnalyzer
```kotlin
interface RequestAnalyzer {
    suspend fun analyze(request: UserRequest): RequestAnalysis
}

data class RequestAnalysis(
    val taskType: TaskType,
    val requiredTools: Set<AgentType>,
    val context: ExecutionContext,
    val parameters: Map<String, Any>,
    val requiresUserInput: Boolean,
    val estimatedComplexity: ComplexityLevel
)
```

### PlanStateMachine
```kotlin
enum class PlanState {
    CREATED, ANALYZING, IN_PROGRESS, PAUSED,
    REQUIRES_INPUT, COMPLETED, FAILED
}

sealed class PlanEvent {
    object Start : PlanEvent()
    object Pause : PlanEvent()
    object Resume : PlanEvent()
    data class UserInputReceived(val input: String) : PlanEvent()
    // ... другие события
}
```

### PlanStorage
```kotlin
interface PlanStorage {
    suspend fun save(plan: ExecutionPlan): String
    suspend fun load(planId: String): ExecutionPlan?
    suspend fun update(plan: ExecutionPlan)
    suspend fun delete(planId: String)
    suspend fun listActive(): List<ExecutionPlan>
    suspend fun listCompleted(limit: Int = 100): List<ExecutionPlan>
}
```

### ProgressTracker
```kotlin
class ProgressTracker {
    fun startTracking(plan: ExecutionPlan)
    fun updateStepProgress(planId: String, stepId: String, progress: Double)
    fun completeStep(planId: String, stepId: String)
    fun getETA(planId: String): Long
    fun getProgress(planId: String): Double
}
```

---

## ✅ Критерии завершения

- [x] Все интерфейсы определены и задокументированы
- [x] RequestAnalyzer корректно классифицирует типы задач
- [x] PlanStateMachine валидирует все переходы состояний
- [x] PlanStorage сохраняет и загружает планы
- [x] ProgressTracker рассчитывает прогресс и ETA
- [x] Unit тесты покрывают >80% кода
- [x] Интеграционные тесты проверяют взаимодействие компонентов
- [x] Документация обновлена

---

## 📖 Связанные документы

- [Техническая спецификация](enhanced-orchestrator-technical.md) - детальные примеры кода
- [Основной roadmap](../11-enhanced-orchestrator.md)
- [Phase 2: Tool Agents](phase-2-tool-agents.md)

---

*Создано: 2025-10-20*
