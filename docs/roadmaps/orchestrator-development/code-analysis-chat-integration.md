# Роадмап: Интеграция анализа кода в основной чат

## Обзор

Документ описывает план по интеграции функциональности анализа рабочего пространства в основной интерфейс чата Ride
plugin через создание специализированных агентов-инструментов для агента-оркестратора.

## Текущее состояние

- ✅ Существующая система анализа кода (`CodeAnalysisAgent`, `ProjectScanner`, `CodeChunker`)
- ✅ Модульная архитектура с анализаторами (`BugDetectionAnalyzer`, `CodeQualityAnalyzer`, `ArchitectureAnalyzer`)
- ✅ Отдельный запуск через `AnalyzeCodeAction`
- ✅ Базовые компоненты для переиспользования
- ❌ Изолированность от основного чата
- ❌ Отсутствие контекстной интеграции
- ❌ Монолитный подход к анализу

## Цели интеграции

1. **Декомпозиция** - разбить монолитный `CodeAnalysisAgent` на специализированных tool agents
2. **Оркестрация** - интегрировать tool agents в систему оркестратора
3. **Контекстность** - анализ с учетом текущего диалога и пользовательского контекста
4. **Интерактивность** - пошаговое выполнение с уточняющими вопросами
5. **Гибкость** - возможность комбинировать различные типы анализа

## Архитектурный подход

### От монолита к инструментам

```
Было:                     Станет:
┌─────────────────┐       ┌─────────────────┐
│CodeAnalysisAgent│       │ Orchestrator    │
│   - Сканер      │       │   Agent         │
│   - Чанкер      │       │                 │
│   - Анализаторы │       └─────────┬───────┘
└─────────────────┘                 │
                                   ▼
                    ┌─────────────────────────┐
                    │      Tool Agents        │
                    │ ┌─────────────────────┐ │
                    │ │ProjectScannerAgent  │ │
                    │ └─────────────────────┘ │
                    │ ┌─────────────────────┐ │
                    │ │BugDetectionAgent    │ │
                    │ └─────────────────────┘ │
                    │ ┌─────────────────────┐ │
                    │ │CodeQualityAgent     │ │
                    │ └─────────────────────┘ │
                    │ ┌─────────────────────┐ │
                    │ │ArchitectureAgent    │ │
                    │ └─────────────────────┘ │
                    └─────────────────────────┘
```

## План реализации

### Этап 1: Создание Tool Agents на основе существующих компонентов (1-2 недели)

#### 1.1 Декомпозиция CodeAnalysisAgent

- [ ] Создать `ProjectScannerToolAgent` на основе `ProjectScanner`
- [ ] Создать `BugDetectionToolAgent` на основе `BugDetectionAnalyzer`
- [ ] Создать `CodeQualityToolAgent` на основе `CodeQualityAnalyzer`
- [ ] Создать `ArchitectureToolAgent` на основе `ArchitectureAnalyzer`
- [ ] Создать `CodeChunkerToolAgent` на основе `CodeChunker`

**Технические детали для Tool Agents:**

```kotlin
// Базовый интерфейс для всех tool agents
interface CodeAnalysisToolAgent : ToolAgent {
    val agentType: AgentType
    val supportedLanguages: Set<ProgrammingLanguage>
    val capabilities: Set<String>
}

// Пример реализации
class ProjectScannerToolAgent(
    private val projectScanner: ProjectScanner
) : CodeAnalysisToolAgent {

    override val agentType = AgentType.PROJECT_SCANNER
    override val supportedLanguages = ProgrammingLanguage.ALL
    override val capabilities = setOf("file_discovery", "pattern_matching", "exclusion_filtering")

    override suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult {
        val scanRequest = ScanRequest.fromStepInput(step.input)
        val scanResult = projectScanner.scan(scanRequest)
        return StepResult.success(scanResult.toStepOutput())
    }
}
```

#### 1.2 Регистрация Tool Agents в системе оркестратора

- [ ] Создать `CodeAnalysisAgentRegistry` для регистрации всех tool agents
- [ ] Реализовать автообнаружение и инициализацию агентов
- [ ] Интегрировать с `ToolAgentRegistry` оркестратора

```kotlin
class CodeAnalysisAgentRegistry {
    fun registerAllAgents(registry: ToolAgentRegistry) {
        registry.register(ProjectScannerToolAgent(projectScanner))
        registry.register(BugDetectionToolAgent(bugDetectionAnalyzer))
        registry.register(CodeQualityToolAgent(codeQualityAnalyzer))
        registry.register(ArchitectureToolAgent(architectureAnalyzer))
        registry.register(CodeChunkerToolAgent(codeChunker))
    }
}
```

#### 1.3 Создание планов анализа для оркестратора

- [ ] Разработать шаблоны планов для типовых сценариев анализа
- [ ] Создать `AnalysisPlanFactory` для генерации планов
- [ ] Определить точки взаимодействия с пользователем

**Примеры шаблонов планов:**

```kotlin
class AnalysisPlanFactory {
    fun createBugAnalysisPlan(request: AnalysisRequest): ExecutionPlan {
        return ExecutionPlan(
            description = "Анализ кода на наличие багов",
            steps = listOf(
                PlanStep("scan-files", "Сканирование файлов", AgentType.PROJECT_SCANNER),
                PlanStep("chunk-large-files", "Разбиение больших файлов", AgentType.CODE_CHUNKER),
                PlanStep("detect-bugs", "Поиск багов", AgentType.BUG_DETECTION),
                PlanStep("user-confirmation", "Подтверждение исправлений", AgentType.USER_INTERACTION)
            )
        )
    }

    fun createQualityAnalysisPlan(request: AnalysisRequest): ExecutionPlan {
        return ExecutionPlan(
            description = "Анализ качества кода",
            steps = listOf(
                PlanStep("scan-files", "Сканирование файлов", AgentType.PROJECT_SCANNER),
                PlanStep("analyze-quality", "Анализ качества", AgentType.CODE_QUALITY),
                PlanStep("generate-report", "Создание отчета", AgentType.REPORT_GENERATOR)
            )
        )
    }
}
```

#### 1.4 Интеграция с RequestAnalyzer оркестратора

- [ ] Расширить `RequestAnalyzer` для распознавания запросов анализа кода
- [ ] Добавить `AnalysisIntent` как специальный тип запроса
- [ ] Реализовать извлечение контекста (файлы, директории, тип анализа)
- [ ] Интегрировать с `AnalysisPlanFactory` для автоматической генерации планов

**Расширение RequestAnalyzer:**

```kotlin
class EnhancedRequestAnalyzer(
    private val baseAnalyzer: RequestAnalyzer,
    private val analysisPlanFactory: AnalysisPlanFactory
) : RequestAnalyzer {

    override suspend fun analyze(request: UserRequest): RequestAnalysis {
        val baseAnalysis = baseAnalyzer.analyze(request)

        // Проверяем, является ли запрос запросом анализа
        if (isCodeAnalysisRequest(request.text)) {
            val analysisIntent = parseAnalysisIntent(request.text, request.context)
            val plan = analysisPlanFactory.createPlan(analysisIntent)

            return baseAnalysis.copy(
                taskType = TaskType.CODE_ANALYSIS,
                requiredTools = plan.steps.map { it.agentType }.toSet(),
                executionPlan = plan,
                requiresUserInput = plan.hasUserInteractions()
            )
        }

        return baseAnalysis
    }

    private fun parseAnalysisIntent(text: String, context: ChatContext): AnalysisIntent {
        return when {
            text.contains("баг", ignoreCase = true) -> AnalysisIntent.BUG_DETECTION
            text.contains("качеств", ignoreCase = true) -> AnalysisIntent.QUALITY_ANALYSIS
            text.contains("архитектур", ignoreCase = true) -> AnalysisIntent.ARCHITECTURE_ANALYSIS
            text.contains("безопасн", ignoreCase = true) -> AnalysisIntent.SECURITY_ANALYSIS
            else -> AnalysisIntent.COMPREHENSIVE_ANALYSIS
        }
    }
}
```

**Примеры запросов и автоматическая генерация планов:**

```
"Проанализируй текущий файл на баги"
→ RequestAnalyzer определяет: BUG_DETECTION + current file
→ AnalysisPlanFactory создает план с соответствующими шагами

"Проверь качество кода в директории service/"
→ RequestAnalyzer определяет: QUALITY_ANALYSIS + service/ directory
→ AnalysisPlanFactory создает план с фильтрацией файлов

"Сделай полный анализ проекта"
→ RequestAnalyzer определяет: COMPREHENSIVE_ANALYSIS + all files
→ AnalysisPlanFactory создает комплексный план с несколькими анализаторами
```

### Этап 2: Интерактивный анализ через оркестратор (2-3 недели)

#### 2.1 Реализация интерактивных планов анализа

- [ ] Создать планы с точками взаимодействия для пользовательского ввода
- [ ] Реализовать паузы выполнения плана для уточнений
- [ ] Добавить возможность изменения параметров анализа в процессе выполнения

**Пример интерактивного плана:**

```kotlin
ExecutionPlan(
    description = "Интерактивный анализ кода на баги",
    steps = listOf(
        PlanStep("scope-selection", "Выбор области анализа", AgentType.USER_INTERACTION),
        PlanStep("scan-files", "Сканирование файлов", AgentType.PROJECT_SCANNER),
        PlanStep("analyze-bugs", "Поиск багов", AgentType.BUG_DETECTION),
        PlanStep("result-review", "Просмотр результатов", AgentType.USER_INTERACTION),
        PlanStep("fix-confirmation", "Подтверждение исправлений", AgentType.USER_INTERACTION)
    )
)
```

#### 2.2 Контекстная передача между шагами

- [ ] Реализовать передачу результатов анализа между tool agents
- [ ] Добавить возможность уточнения критериев на основе промежуточных результатов
- [ ] Создать систему рекомендаций на основе найденных проблем

**Пример флоу с контекстной передачей:**

```
Шаг 1: ProjectScannerToolAgent находит 50 файлов
↓ (передает список файлов)
Шаг 2: BugDetectionToolAgent находит 5 багов в 3 файлах
↓ (передает найденные проблемы)
Шаг 3: UserInteraction запрашивает уточнение
Пользователь: "Покажи только критические проблемы"
↓ (передает уточненные критерии)
Шаг 4: BugDetectionToolAgent фильтрует результаты
```

#### 2.3 Адаптивные планы анализа

- [ ] Реализовать изменение плана на основе промежуточных результатов
- [ ] Добавить возможность добавления дополнительных анализаторов
- [ ] Создать систему приоритизации проблем для пользователя

**Пример адаптивного плана:**

```kotlin
// Если найдено много критических проблем, добавить анализ безопасности
if (criticalBugs.size > 5) {
    plan.addStep(PlanStep("security-analysis", "Доп. анализ безопасности", AgentType.SECURITY_ANALYSIS))
}

// Если проект большой, добавить разбивку на части
if (fileCount > 100) {
    plan.insertStep("chunk-files", "Разбиение на части", AgentType.CODE_CHUNKER)
}
```

### Этап 3: Умная интеграция с оркестратором (2-3 недели)

#### 3.1 Проактивные предложения анализа

- [ ] Расширить RequestAnalyzer для автоматического обнаружения потребности в анализе
- [ ] Интегрировать с системой неопределенности для предложения анализа
- [ ] Реализовать контекстные подсказки в чате

**Пример проактивного предложения:**

```
Пользователь: "У меня падает приложение при запуске"
Ассистент: "Хотите проанализировать код на предмет потенциальных багов?
Это может помочь найти причину падения."
Пользователь: "Да, проанализируй"
→ Автоматически создается и выполняется план анализа багов
```

#### 3.2 Умные шаблоны планов

- [ ] Создать систему шаблонов для распространенных сценариев
- [ ] Реализовать адаптацию шаблонов под контекст проекта
- [ ] Добавить обучение на основе предыдущих анализов

**Примеры умных шаблонов:**

```kotlin
class SmartAnalysisTemplateEngine {
    fun createPlanForErrorContext(error: StackTraceError): ExecutionPlan {
        // Анализ стектрейса и создание плана
        val relevantFiles = extractFilesFromStacktrace(error)
        return ExecutionPlan(
            description = "Анализ кода по контексту ошибки",
            steps = listOf(
                PlanStep("analyze-error-location", "Анализ места ошибки", AgentType.CODE_ANALYSIS),
                PlanStep("check-related-code", "Проверка связанного кода", AgentType.BUG_DETECTION),
                PlanStep("suggest-fixes", "Предложения исправлений", AgentType.CODE_SUGGESTER)
            )
        )
    }
}
```

#### 3.3 Интеграция с другими tool agents

- [ ] Создать `CodeFixerToolAgent` для исправления найденных проблем
- [ ] Разработать `RefactoringToolAgent` для рефакторинга
- [ ] Интегрировать с VCS tool agents для анализа изменений

**Пример комплексного плана:**

```kotlin
ExecutionPlan(
    description = "Комплексный анализ и исправление кода",
    steps = listOf(
        PlanStep("analyze-code", "Анализ кода", AgentType.BUG_DETECTION),
        PlanStep("user-review", "Просмотр результатов", AgentType.USER_INTERACTION),
        PlanStep("fix-issues", "Исправление проблем", AgentType.CODE_FIXER),
        PlanStep("verify-fixes", "Проверка исправлений", AgentType.COMPILATION_CHECKER),
        PlanStep("git-commit", "Сохранение изменений", AgentType.VCS_AGENT)
    )
)
```

### Этап 4: Оптимизация и масштабирование (1-2 недели)

#### 4.1 Оптимизация производительности tool agents

- [ ] Оптимизация работы `ProjectScannerToolAgent` для больших проектов
- [ ] Кэширование результатов анализа в `PlanStorage`
- [ ] Реализация incremental анализа (только измененные файлы)
- [ ] Параллельное выполнение независимых анализаторов

**Пример оптимизации:**

```kotlin
class OptimizedBugDetectionToolAgent : BugDetectionToolAgent {
    private val analysisCache = AnalysisCache()

    override suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult {
        val files = step.input.getFiles("files")

        // Проверяем кэш для каждого файла
        val uncachedFiles = files.filter { !analysisCache.isCached(it) }
        val cachedResults = files.filter { analysisCache.isCached(it) }

        // Анализируем только незакэшированные файлы
        val newResults = if (uncachedFiles.isNotEmpty()) {
            super.executeStep(step.copy(
                input = step.input.withFiles("files", uncachedFiles)
            ), context)
        } else null

        // Объединяем результаты
        val combinedResults = combineResults(cachedResults, newResults)
        return StepResult.success(combinedResults)
    }
}
```

#### 4.2 Масштабирование системы анализа

- [ ] Создание пулов tool agents для параллельной обработки
- [ ] Реализация очередей выполнения для больших планов
- [ ] Оптимизация использования памяти при анализе больших проектов
- [ ] Добавление мониторинга производительности

#### 4.3 Интеграция с существующим функционалом

- [ ] Обеспечение обратной совместимости с `AnalyzeCodeAction`
- [ ] Постепенная миграция пользователей на новый интерфейс
- [ ] Создание моста между старым и новым подходами

**Миграционная стратегия:**

```kotlin
class BackwardCompatibleAnalysisOrchestrator {
    fun migrateFromLegacy(legacyRequest: LegacyAnalysisRequest): ExecutionPlan {
        return when (legacyRequest.type) {
            AnalysisType.BUG_DETECTION -> analysisPlanFactory.createBugAnalysisPlan(
                AnalysisRequest.fromLegacy(legacyRequest)
            )
            AnalysisType.CODE_QUALITY -> analysisPlanFactory.createQualityAnalysisPlan(
                AnalysisRequest.fromLegacy(legacyRequest)
            )
            // ... другие типы
        }
    }
}
```

#### 4.4 Тестирование и мониторинг

- [ ] Комплексное тестирование всех tool agents
- [ ] Нагрузочное тестирование системы оркестрации
- [ ] Создание метрик производительности анализа
- [ ] Мониторинг успешности выполнения планов

## Технические требования

### Архитектурные изменения

- **Декомпозиция** - разбить `CodeAnalysisAgent` на специализированные tool agents
- **Оркестрация** - интеграция tool agents в систему оркестратора
- **Совместимость** - сохранение работы существующего `AnalyzeCodeAction`
- **Модульность** - каждый tool agent должен быть независимым и переиспользуемым

### Зависимости

### Структура Tool Agents

```
CodeAnalysis Tool Agents:
├── ProjectScannerToolAgent (на основе ProjectScanner)
├── CodeChunkerToolAgent (на основе CodeChunker)
├── BugDetectionToolAgent (на основе BugDetectionAnalyzer)
├── CodeQualityToolAgent (на основе CodeQualityAnalyzer)
├── ArchitectureToolAgent (на основе ArchitectureAnalyzer)
└── ReportGeneratorToolAgent (новый агент для генерации отчетов)
```

### Интеграция с оркестратором

- **RequestAnalyzer** - расширение для распознавания запросов анализа
- **PlanGenerator** - шаблоны планов для типовых сценариев анализа
- **ToolAgentRegistry** - регистрация и управление tool agents
- **PlanExecutor** - выполнение и координация анализа

### Обратная совместимость

- Сохранение существующего функционала `AnalyzeCodeAction`
- Постепенный миграция пользователей на новый интерфейс
- Возможность отключения интеграции через настройки

## Риски и митигация

### Риски

1. **Производительность** - анализ может быть медленным в контексте чата
2. **Сложность** - увеличение сложности `ChatAgent`
3. **UX** - потенциальное ухудшение пользовательского опыта

### Митигация

1. **Асинхронность** - фоновый анализ с прогрессом
2. **Модульность** - четкое разделение ответственности
3. **Тестирование** - постоянное UX-тестирование

## Критерии успеха

- [ ] Пользователи могут запускать анализ через чат
- [ ] Анализ контекстуален и интерактивен
- [ ] Время отклика приемлемо (< 30 сек для типичных запросов)
- [ ] Положительная обратная связь от пользователей
- [ ] Стабильная работа без падений

## Следующие шаги

1. Утвердить роадмап
2. Начать с Этапа 1: Базовая интеграция
3. Создать MVP для быстрой проверки гипотез
4. Итеративно улучшать функциональность