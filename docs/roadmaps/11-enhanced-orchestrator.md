# 🎭 Роадмап: Расширенный агент-оркестратор

## 📊 Метаданные проекта

- **Статус**: 📋 Запланировано
- **Версия**: 2.0.0
- **Дата создания**: 2025-10-20
- **Приоритет**: Высокий
- **Оценка времени**: 10-15 недель
- **Зависимости**: CodeAnalysisAgent, AgentOrchestrator, UncertaintyAnalyzer

---

## 🎯 Цели фичи

### Основная задача

Развитие существующего `AgentOrchestrator` в полноценную систему управления мультиагентными флоу, способную:

- **Быть адаптером** между мультиагентной системой и пользователем
- **Определять общий план** работы (что нужно сделать)
- **Организовывать реализацию** в частности (как это сделать)
- **Управлять флоу** разработки с интерактивностью
- **Интегрировать агентов-исполнителей** для анализа кода и других задач

### Пользовательские проблемы

- **Проблема 1**: Текущий оркестратор выполняет задачи только последовательно
- **Проблема 2**: Отсутствует интерактивность - нельзя уточнить детали в процессе выполнения
- **Проблема 3**: Нет управления состояниями плана - невозможно приостановить/возобновить
- **Проблема 4**: Отсутствуют специализированные агенты-инструменты (Tool Agents)
- **Проблема 5**: Нет персистентности планов между сессиями

### Бизнес-ценность

- Выполнение сложных многошаговых задач с интерактивностью
- Повышение автономности системы агентов
- Улучшение пользовательского опыта через диалоги
- Масштабируемая архитектура для будущих расширений

---

## 📋 Задачи (Checklist)

> 💡 **Детальные планы по этапам:** Каждый этап разработки имеет отдельный документ с подробным описанием задач, примерами кода и критериями завершения. См. [папку с документацией](orchestrator-development/).

### Phase 1: Расширение базовой архитектуры (2-3 недели)

📄 **[Детальный план Phase 1](orchestrator-development/phase-1-base-architecture.md)**

#### 1.1 RequestAnalyzer
- [ ] Создать интерфейс `RequestAnalyzer`
- [ ] Реализовать `LLMRequestAnalyzer` с использованием LLM
- [ ] Добавить классификацию типов задач (CODE_ANALYSIS, REFACTORING, BUG_FIX, etc.)
- [ ] Реализовать извлечение контекста и параметров из запроса
- [ ] Определение необходимых Tool Agents для задачи
- [ ] Интеграция с `UncertaintyAnalyzer`

#### 1.2 PlanStateMachine
- [ ] Создать `PlanStateMachine` для управления состояниями
- [ ] Определить все возможные состояния плана (CREATED, ANALYZING, IN_PROGRESS, PAUSED, REQUIRES_INPUT, COMPLETED, FAILED)
- [ ] Реализовать валидацию переходов между состояниями
- [ ] Добавить обработку событий (Start, Pause, Resume, Error, etc.)
- [ ] Интегрировать с `AgentOrchestrator`
- [ ] Добавить listeners для уведомлений о смене состояний

#### 1.3 PlanStorage
- [ ] Разработать интерфейс `PlanStorage`
- [ ] Реализовать `InMemoryPlanStorage` для MVP
- [ ] Добавить сериализацию/десериализацию планов (JSON)
- [ ] Реализовать `PersistentPlanStorage` с сохранением на диск
- [ ] Добавить версионирование планов
- [ ] Реализовать очистку старых планов

#### 1.4 ProgressTracker
- [ ] Создать `ProgressTracker` для детального отслеживания
- [ ] Добавить расчет прогресса выполнения (%)
- [ ] Реализовать оценку времени завершения (ETA)
- [ ] Добавить события прогресса для UI
- [ ] Интегрировать с `OrchestratorStep`
- [ ] Добавить историю выполнения шагов

### Phase 2: Tool Agents на основе CodeAnalysisAgent (2-3 недели)

📄 **[Детальный план Phase 2](orchestrator-development/phase-2-tool-agents.md)**

#### 2.1 Базовый интерфейс ToolAgent
- [ ] Определить интерфейс `ToolAgent` (extends Agent)
- [ ] Создать базовые модели (`PlanStep`, `StepInput`, `StepOutput`, `StepResult`)
- [ ] Реализовать `ToolAgentRegistry` для регистрации агентов
- [ ] Добавить систему capabilities для агентов
- [ ] Создать enum `AgentType` для всех типов агентов
- [ ] Реализовать `StepStatus` для отслеживания состояния шага

#### 2.2 Декомпозиция CodeAnalysisAgent
- [ ] Создать `ProjectScannerToolAgent` (сканирование файловой системы)
- [ ] Создать `CodeChunkerToolAgent` (разбиение больших файлов)
- [ ] Создать `BugDetectionToolAgent` (поиск багов)
- [ ] Создать `CodeQualityToolAgent` (анализ качества кода)
- [ ] Создать `ArchitectureToolAgent` (анализ архитектуры)
- [ ] Создать `ReportGeneratorToolAgent` (генерация отчетов)

#### 2.3 ToolAgentRegistry
- [ ] Реализовать регистрацию и управление Tool Agents
- [ ] Добавить автообнаружение доступных агентов
- [ ] Создать фабрику для создания агентов
- [ ] Реализовать поиск агентов по capabilities
- [ ] Интегрировать с `EnhancedAgentOrchestrator`
- [ ] Добавить валидацию совместимости агентов

### Phase 3: Интерактивность и пользовательское взаимодействие (2-3 недели)

📄 **[Детальный план Phase 3](orchestrator-development/phase-3-interactivity.md)**

#### 3.1 UserInteractionAgent
- [ ] Разработать `UserInteractionAgent` для диалогов
- [ ] Реализовать паузы выполнения для пользовательского ввода
- [ ] Добавить валидацию пользовательского ввода
- [ ] Создать типовые шаблоны вопросов (confirmation, choice, input)
- [ ] Реализовать форматирование данных для отображения
- [ ] Добавить timeout для пользовательского ввода

#### 3.2 Интеграция с ChatAgent
- [ ] Расширить `ChatAgent` для обработки интерактивных планов
- [ ] Добавить UI элементы для пользовательского ввода
- [ ] Реализовать возобновление плана после ввода (resumePlan)
- [ ] Создать историю взаимодействий
- [ ] Добавить отображение прогресса выполнения в чате
- [ ] Реализовать кнопки быстрых действий

#### 3.3 Адаптивные планы выполнения
- [ ] Реализовать изменение плана на основе промежуточных результатов
- [ ] Добавить условное выполнение шагов (if/else)
- [ ] Создать систему приоритизации задач
- [ ] Интегрировать с `UncertaintyAnalyzer` для проактивных вопросов
- [ ] Реализовать добавление/удаление шагов в процессе выполнения
- [ ] Добавить поддержку циклов (retry, loop)

### Phase 4: Расширенное выполнение планов (2-3 недели)

📄 **[Детальный план Phase 4](orchestrator-development/phase-4-advanced-execution.md)**

#### 4.1 Параллельное выполнение
- [ ] Реализовать граф зависимостей (DAG)
- [ ] Добавить топологическую сортировку для определения порядка
- [ ] Реализовать параллельное выполнение независимых шагов (coroutines)
- [ ] Создать пул потоков для выполнения
- [ ] Обработка ошибок в параллельных задачах
- [ ] Добавить ограничение на количество параллельных задач

#### 4.2 Контекстная передача между шагами
- [ ] Реализовать `ExecutionContext` для хранения результатов
- [ ] Создать систему трансформации данных между шагами
- [ ] Добавить валидацию входных/выходных данных
- [ ] Реализовать кэширование промежуточных результатов
- [ ] Добавить enrichment входных данных из зависимых шагов
- [ ] Создать систему переменных контекста

#### 4.3 Обработка ошибок и retry-логика
- [ ] Реализовать retry с экспоненциальным backoff
- [ ] Добавить транзакционность для откатов при ошибках
- [ ] Создать систему fallback стратегий
- [ ] Реализовать детальное логирование и диагностику
- [ ] Добавить recovery механизмы
- [ ] Создать систему уведомлений об ошибках

### Phase 5: Интеграция и оптимизация (2-3 недели)

📄 **[Детальный план Phase 5](orchestrator-development/phase-5-integration.md)**

#### 5.1 Интеграция с существующими компонентами
- [ ] Интегрировать с `UncertaintyAnalyzer`
- [ ] Связать с `CodeAnalysisAgent`
- [ ] Обеспечить совместимость с `PlannerAgent` и `ExecutorAgent`
- [ ] Создать миграционный путь от старого к новому
- [ ] Добавить backward compatibility
- [ ] Обновить `ChatAgent` для использования нового оркестратора

#### 5.2 Оптимизация производительности
- [ ] Реализовать кэширование результатов анализа запросов
- [ ] Оптимизировать работу с большими проектами
- [ ] Добавить ленивую загрузку компонентов
- [ ] Профилирование и устранение узких мест
- [ ] Оптимизировать использование памяти
- [ ] Добавить пулинг ресурсов

#### 5.3 Тестирование
- [ ] Unit тесты для всех компонентов (>80% coverage)
- [ ] Интеграционные тесты для флоу
- [ ] Тесты стейт-машины (все переходы)
- [ ] Нагрузочное тестирование (100+ активных планов)
- [ ] Тесты параллельного выполнения
- [ ] Тесты восстановления после ошибок

#### 5.4 Документация
- [ ] Обновить архитектурную документацию
- [ ] Создать примеры использования
- [ ] Написать гайд по созданию Tool Agents
- [ ] Обновить README.md
- [ ] Создать API документацию
- [ ] Добавить диаграммы последовательности

---

## 🏗️ Архитектура

### Текущая vs Целевая архитектура

**Текущая:**
```
ChatAgent → AgentOrchestrator → PlannerAgent → ExecutorAgent
                                      ↓
                                  TaskPlan
                                      ↓
                                  TaskItem[]
```

**Целевая:**
```
┌─────────────────────────────────────────────────────────────┐
│                        ChatAgent                            │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              EnhancedAgentOrchestrator                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Request      │  │ Plan         │  │ State        │     │
│  │ Analyzer     │→ │ Generator    │→ │ Machine      │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                              │               │
│  ┌──────────────┐  ┌──────────────┐        │               │
│  │ Plan         │  │ Progress     │        │               │
│  │ Storage      │  │ Tracker      │        │               │
│  └──────────────┘  └──────────────┘        │               │
└────────────────────────────────────────────┼───────────────┘
                                              │
                         ┌────────────────────┴────────────────────┐
                         │          Plan Executor                  │
                         │  (с поддержкой параллелизма)            │
                         └────────────────────┬────────────────────┘
                                              │
                ┌─────────────────────────────┼─────────────────────────────┐
                │                             │                             │
                ▼                             ▼                             ▼
    ┌───────────────────┐       ┌───────────────────┐       ┌───────────────────┐
    │  Tool Agents      │       │  Code Analysis    │       │  User Interaction │
    │  Registry         │       │  Tool Agents      │       │  Agent            │
    └───────────────────┘       └───────────────────┘       └───────────────────┘
            │                            │
            │                            ├─ ProjectScannerToolAgent
            │                            ├─ BugDetectionToolAgent
            │                            ├─ CodeQualityToolAgent
            │                            ├─ ArchitectureToolAgent
            │                            └─ CodeChunkerToolAgent
            │
            └─ Другие Tool Agents (FileOperations, Git, etc.)
```

### Стейт-машина плана выполнения

```
    CREATED
       │
       ▼
   ANALYZING ──────────► REQUIRES_INPUT
       │                      │
       ▼                      │
  IN_PROGRESS ◄───────────────┘
       │
       ├──────► PAUSED ────► RESUMED ──┐
       │                                │
       ├────────────────────────────────┘
       │
       ├──────► COMPLETED
       │
       └──────► FAILED ──────► RETRY ──┐
                                        │
                                        └──► IN_PROGRESS
```

### Ключевые компоненты

#### 1. RequestAnalyzer
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

enum class TaskType {
    CODE_ANALYSIS, BUG_DETECTION, REFACTORING,
    REPORT_GENERATION, COMPLEX_MULTI_STEP, SIMPLE_QUERY
}
```

#### 2. PlanStateMachine
```kotlin
class PlanStateMachine {
    private var currentState: PlanState = PlanState.CREATED
    
    fun transition(event: PlanEvent): PlanState {
        val newState = when (currentState) {
            PlanState.CREATED -> handleCreatedState(event)
            PlanState.ANALYZING -> handleAnalyzingState(event)
            PlanState.IN_PROGRESS -> handleInProgressState(event)
            // ... другие состояния
        }
        
        if (isValidTransition(currentState, newState)) {
            currentState = newState
            return newState
        } else {
            throw InvalidStateTransitionException(currentState, newState)
        }
    }
}

enum class PlanState {
    CREATED, ANALYZING, IN_PROGRESS, PAUSED,
    REQUIRES_INPUT, COMPLETED, FAILED
}
```

#### 3. ToolAgent Interface
```kotlin
interface ToolAgent : Agent {
    val agentType: AgentType
    val capabilities: Set<String>
    
    suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult
    fun canHandle(step: PlanStep): Boolean
}

data class PlanStep(
    val id: String,
    val description: String,
    val agentType: AgentType,
    val input: StepInput,
    val dependencies: Set<String>,
    var status: StepStatus,
    var output: StepOutput? = null
)

enum class AgentType {
    PROJECT_SCANNER, CODE_CHUNKER, BUG_DETECTION,
    CODE_QUALITY, ARCHITECTURE_ANALYSIS, CODE_FIXER,
    REPORT_GENERATOR, USER_INTERACTION, FILE_OPERATIONS
}
```

---

## 🎯 Примеры использования

### Пример 1: Анализ и исправление багов

**Запрос пользователя:**
```
Проанализируй проект на баги и исправь все критические проблемы
```

**Процесс выполнения:**

1. **RequestAnalyzer** определяет:
   - TaskType: `COMPLEX_ANALYSIS_AND_FIX`
   - RequiredTools: `[PROJECT_SCANNER, BUG_DETECTION, CODE_FIXER, USER_INTERACTION]`

2. **PlanGenerator** создает план:
```
ExecutionPlan(
    steps = [
        PlanStep("scan", "Сканирование проекта", PROJECT_SCANNER),
        PlanStep("analyze", "Поиск багов", BUG_DETECTION, dependencies = ["scan"]),
        PlanStep("confirm", "Подтверждение исправлений", USER_INTERACTION, dependencies = ["analyze"]),
        PlanStep("fix", "Исправление багов", CODE_FIXER, dependencies = ["confirm"]),
        PlanStep("report", "Создание отчета", REPORT_GENERATOR, dependencies = ["fix"])
    ]
)
```

3. **StateMachine** управляет состояниями:
```
CREATED → ANALYZING → IN_PROGRESS → REQUIRES_INPUT → IN_PROGRESS → COMPLETED
```

4. **Интерактивный диалог:**
```
Ассистент: 🔍 Сканирую проект...
✅ Найдено 127 файлов

Ассистент: 🔍 Анализирую код на баги...
✅ Обнаружено 15 проблем:
- 3 критических
- 7 высоких
- 5 средних

Ассистент: ❓ Найдены критические проблемы:
1. FileService.kt:45 - Potential NPE
2. DatabaseManager.kt:123 - Resource leak
3. CacheManager.kt:67 - Race condition

Исправить эти проблемы? [Да / Нет / Показать детали]

Пользователь: Да

Ассистент: 🔧 Исправляю проблемы...
✅ Исправлено 3 критических проблемы

📋 Отчет сохранен в reports/bug-fix-2024-10-20.md
```

### Пример 2: Рефакторинг архитектуры

**Запрос пользователя:**
```
Перенеси бизнес-логику из UI контроллеров в сервисный слой
```

**Процесс выполнения:**

1. **RequestAnalyzer** → TaskType: `ARCHITECTURE_REFACTORING`
2. **План**: анализ → извлечение → генерация → обновление
3. **Параллельное выполнение** независимых шагов
4. **Интерактивное подтверждение** на каждом этапе
5. **Адаптация плана** на основе промежуточных результатов

---

## 🔧 Технические детали

### Параллельное выполнение с DAG

```kotlin
class PlanExecutor(
    private val toolAgentRegistry: ToolAgentRegistry,
    private val progressTracker: ProgressTracker
) {
    suspend fun executePlan(plan: ExecutionPlan): ExecutionResult {
        val dependencyGraph = buildDependencyGraph(plan.steps)
        val executionOrder = topologicalSort(dependencyGraph)
        
        for (batch in executionOrder) {
            // Выполняем независимые шаги параллельно
            val results = coroutineScope {
                batch.map { stepId ->
                    async {
                        val step = plan.steps.find { it.id == stepId }!!
                        executeStep(step, plan.context)
                    }
                }.awaitAll()
            }
            
            // Проверяем, требуется ли пользовательский ввод
            val userInputStep = results.find { it.requiresUserInput }
            if (userInputStep != null) {
                return ExecutionResult.requiresInput(userInputStep.userPrompt!!)
            }
        }
        
        return ExecutionResult.success()
    }
}
```

### Контекстная передача результатов

```kotlin
class ExecutionContext(
    val projectPath: String,
    val parameters: Map<String, Any>,
    private val stepResults: MutableMap<String, StepOutput> = mutableMapOf()
) {
    fun addStepResult(stepId: String, output: StepOutput) {
        stepResults[stepId] = output
    }
    
    fun enrichStepInput(step: PlanStep): StepInput {
        val enrichedInput = step.input.toMutableMap()
        
        // Добавляем результаты зависимых шагов
        for (dependencyId in step.dependencies) {
            val dependencyResult = stepResults[dependencyId]
            if (dependencyResult != null) {
                enrichedInput["dependency_$dependencyId"] = dependencyResult
            }
        }
        
        return StepInput(enrichedInput)
    }
}
```

---

## 📊 Оценка ресурсов

### Использование токенов

Для типичного сценария анализа и исправления багов:

| Этап | Токены | Описание |
|------|--------|----------|
| Анализ запроса | 500 | RequestAnalyzer классифицирует задачу |
| Генерация плана | 1000 | PlanGenerator создает план |
| Сканирование | 200 | ProjectScannerAgent |
| Анализ багов | 30K | BugDetectionAgent (10 файлов × 3K) |
| Генерация отчета | 2K | ReportGeneratorAgent |
| **ИТОГО** | **~33.7K** | На весь флоу |

### Производительность

| Метрика | Целевое значение |
|---------|------------------|
| Время анализа запроса | < 2 секунды |
| Время генерации плана | < 5 секунд |
| Поддержка активных планов | 100+ одновременно |
| Память на план | < 1MB |
| Время восстановления после сбоя | < 1 секунда |

---

## ✅ Критерии успеха

### Функциональные
- [ ] Успешное выполнение сложных многошаговых задач
- [ ] Интерактивность с пользователем в процессе выполнения
- [ ] Надежное восстановление после ошибок
- [ ] Интеграция с существующими агентами
- [ ] Параллельное выполнение независимых шагов
- [ ] Персистентность планов между сессиями

### Нефункциональные
- [ ] Время отклика < 2 секунд на типичных задачах
- [ ] Стабильная работа без падений
- [ ] Положительный UX фидбек от пользователей
- [ ] Покрытие тестами > 80%
- [ ] Поддержка 100+ активных планов
- [ ] Документация всех компонентов

---

## 📈 Метрики успеха

- ✅ Агент успешно выполняет 90%+ сложных многошаговых задач
- ✅ Время выполнения типичного плана < 5 минут
- ✅ Пользователи могут приостановить/возобновить план
- ✅ Система восстанавливается после ошибок в 95%+ случаев
- ✅ Расход токенов оптимизирован (< 50K на сложную задачу)

---

## 🔄 Следующие шаги после реализации

1. **Расширение Tool Agents**: Добавить агентов для Git, тестирования, деплоя
2. **Machine Learning**: Обучение на истории выполнения планов
3. **Визуализация**: Графическое отображение планов и прогресса
4. **Collaborative features**: Sharing планов между пользователями
5. **Cloud sync**: Синхронизация планов через облако
6. **Marketplace**: Возможность создавать и делиться Tool Agents

---

## 📚 Связанные документы

- [Техническая спецификация](orchestrator-development/enhanced-orchestrator-technical.md)
- [Краткое резюме](orchestrator-development/ORCHESTRATOR_SUMMARY.md)
- [Детальный план разработки](orchestrator-development/enhanced-orchestrator-development.md)
- [Интеграция анализа кода](orchestrator-development/code-analysis-chat-integration.md)
- [Существующая архитектура оркестратора](orchestrator-development/orchestrator-architecture.md)
- [Оригинальный план разработки оркестратора](orchestrator-development/orchestrator-agent-development.md)
- [Резюме интеграции](orchestrator-development/INTEGRATION_SUMMARY.md)
- [Обновление интеграции](orchestrator-development/INTEGRATION_UPDATE.md)
- [Code Analysis Agent](09-code-analysis-agent.md)
- [Agent Orchestrator Feature](../features/agent-orchestrator.md)

---

**Дата создания**: 2025-10-20  
**Автор**: AI Assistant  
**Статус**: Готов к утверждению  
**Следующий шаг**: Утверждение roadmap и начало Phase 1
