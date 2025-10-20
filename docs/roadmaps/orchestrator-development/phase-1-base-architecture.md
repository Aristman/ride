# Этап 1: Расширение базовой архитектуры

**Длительность:** 2-3 недели  
**Приоритет:** Высокий  
**Статус:** 📋 Запланировано

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
- [ ] Создать интерфейс `RequestAnalyzer`
- [ ] Реализовать `LLMRequestAnalyzer` с использованием LLM
- [ ] Добавить классификацию типов задач (CODE_ANALYSIS, REFACTORING, BUG_FIX, etc.)
- [ ] Реализовать извлечение контекста и параметров из запроса
- [ ] Определение необходимых Tool Agents для задачи
- [ ] Интеграция с `UncertaintyAnalyzer`

### 1.2 PlanStateMachine
- [ ] Создать `PlanStateMachine` для управления состояниями
- [ ] Определить все возможные состояния плана (CREATED, ANALYZING, IN_PROGRESS, PAUSED, REQUIRES_INPUT, COMPLETED, FAILED)
- [ ] Реализовать валидацию переходов между состояниями
- [ ] Добавить обработку событий (Start, Pause, Resume, Error, etc.)
- [ ] Интегрировать с `AgentOrchestrator`
- [ ] Добавить listeners для уведомлений о смене состояний

### 1.3 PlanStorage
- [ ] Разработать интерфейс `PlanStorage`
- [ ] Реализовать `InMemoryPlanStorage` для MVP
- [ ] Добавить сериализацию/десериализацию планов (JSON)
- [ ] Реализовать `PersistentPlanStorage` с сохранением на диск
- [ ] Добавить версионирование планов
- [ ] Реализовать очистку старых планов

### 1.4 ProgressTracker
- [ ] Создать `ProgressTracker` для детального отслеживания
- [ ] Добавить расчет прогресса выполнения (%)
- [ ] Реализовать оценку времени завершения (ETA)
- [ ] Добавить события прогресса для UI
- [ ] Интегрировать с `OrchestratorStep`
- [ ] Добавить историю выполнения шагов

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

- [ ] Все интерфейсы определены и задокументированы
- [ ] RequestAnalyzer корректно классифицирует типы задач
- [ ] PlanStateMachine валидирует все переходы состояний
- [ ] PlanStorage сохраняет и загружает планы
- [ ] ProgressTracker рассчитывает прогресс и ETA
- [ ] Unit тесты покрывают >80% кода
- [ ] Интеграционные тесты проверяют взаимодействие компонентов
- [ ] Документация обновлена

---

## 📖 Связанные документы

- [Техническая спецификация](enhanced-orchestrator-technical.md) - детальные примеры кода
- [Основной roadmap](../11-enhanced-orchestrator.md)
- [Phase 2: Tool Agents](phase-2-tool-agents.md)

---

*Создано: 2025-10-20*
