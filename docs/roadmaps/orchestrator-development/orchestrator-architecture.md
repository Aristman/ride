# Архитектура агента-оркестратора

## Обзор архитектуры

### High-level диаграмма

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Пользователь  │────│  ChatAgent UI    │────│  ChatService    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                                        │
                                                       ┌┴┐
                                                       │▼│
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ Tool Agents     │◄───│ Orchestrator     │◄───│ Enhanced        │
│ (CodeAnalysis,  │    │ Agent            │    │ ChatAgent       │
│ CodeFix, etc.)  │    │                  │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ LLM Providers   │    │ Plan Storage     │    │ Plugin Settings │
│ (Yandex, HF)    │    │ (Memory/DB)      │    │ & Configuration │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## Core компоненты

### 1. OrchestratorAgent

```kotlin
class OrchestratorAgentImpl(
    private val requestAnalyzer: RequestAnalyzer,
    private val planGenerator: PlanGenerator,
    private val planExecutor: PlanExecutor,
    private val planStorage: PlanStorage,
    private val progressTracker: ProgressTracker,
    private val toolAgentRegistry: ToolAgentRegistry
) : OrchestratorAgent {

    override suspend fun orchestrate(request: UserRequest): OrchestratedResponse {
        // 1. Анализируем запрос
        val analysis = requestAnalyzer.analyze(request)

        // 2. Генерируем план
        val plan = planGenerator.generate(analysis)

        // 3. Сохраняем план
        planStorage.save(plan)

        // 4. Начинаем выполнение
        return when {
            plan.requiresUserInput ->
                OrchestratedResponse.requiresInput(plan, plan.getNextUserPrompt())
            else ->
                executePlan(plan)
        }
    }

    override suspend fun resumePlan(planId: String, userInput: String): OrchestratedResponse {
        val plan = planStorage.load(planId)
        plan.updateWithUserInput(userInput)
        return executePlan(plan)
    }
}
```

### 2. RequestAnalyzer

```kotlin
class RequestAnalyzer(
    private val llmProvider: LLMProvider
) {
    suspend fun analyze(request: UserRequest): RequestAnalysis {
        val prompt = buildAnalysisPrompt(request)
        val response = llmProvider.sendRequest(prompt)

        return RequestAnalysis.fromLLMResponse(response).apply {
            // Валидация и обогащение анализа
            validateCapabilities()
            enrichWithProjectContext(request.context)
        }
    }

    private fun buildAnalysisPrompt(request: UserRequest): String {
        return """
            Проанализируй запрос пользователя и определи:
            1. Тип задачи (анализ кода, рефакторинг, генерация отчета)
            2. Необходимые инструменты (CodeAnalysis, FileOperations, etc.)
            3. Параметры и контекст (файлы, директории, критерии)
            4. Точки взаимодействия с пользователем
            5. Ожидаемый результат

            Запрос: "${request.text}"
            Контекст: ${request.context.description}
        """.trimIndent()
    }
}
```

### 3. PlanGenerator

```kotlin
class PlanGenerator {
    suspend fun generate(analysis: RequestAnalysis): ExecutionPlan {
        val steps = mutableListOf<PlanStep>()
        val planId = generatePlanId()

        when (analysis.taskType) {
            TaskType.CODE_ANALYSIS -> generateCodeAnalysisSteps(steps, analysis)
            TaskType.BUG_FIXING -> generateBugFixingSteps(steps, analysis)
            TaskType.REPORT_GENERATION -> generateReportSteps(steps, analysis)
            // ... другие типы задач
        }

        return ExecutionPlan(
            id = planId,
            description = analysis.description,
            steps = steps,
            status = PlanStatus.CREATED,
            context = analysis.context,
            metadata = PlanMetadata(
                estimatedDuration = estimateDuration(steps),
                requiredUserInputs = countUserInteractions(steps),
                riskLevel = calculateRiskLevel(steps)
            )
        )
    }

    private fun generateCodeAnalysisSteps(steps: MutableList<PlanStep>, analysis: RequestAnalysis) {
        // Шаг 1: Сканирование проекта
        steps.add(
            PlanStep(
                id = "scan-project",
                description = "Сканирование файловой системы проекта",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.mapOf(
                    "patterns" to analysis.inclusionPatterns,
                    "exclude_patterns" to analysis.exclusionPatterns
                ),
                dependencies = emptySet(),
                status = StepStatus.PENDING
            )
        )

        // Шаг 2: Анализ кода
        steps.add(
            PlanStep(
                id = "analyze-code",
                description = "Анализ кода на проблемы",
                agentType = AgentType.CODE_ANALYSIS,
                input = StepInput.dependsOn("scan-project"),
                dependencies = setOf("scan-project"),
                status = StepStatus.PENDING
            )
        )

        // Шаг 3: Взаимодействие с пользователем
        steps.add(
            PlanStep(
                id = "user-confirmation",
                description = "Подтверждение исправления найденных проблем",
                agentType = AgentType.USER_INTERACTION,
                input = StepInput.dependsOn("analyze-code"),
                dependencies = setOf("analyze-code"),
                status = StepStatus.PENDING
            )
        )
    }
}
```

### 4. PlanExecutor

```kotlin
class PlanExecutor(
    private val toolAgents: Map<AgentType, ToolAgent>,
    private val progressTracker: ProgressTracker
) {
    suspend fun executePlan(plan: ExecutionPlan): OrchestratedResponse {
        plan.status = PlanStatus.IN_PROGRESS
        progressTracker.startTracking(plan)

        return try {
            // Выполняем шаги с учетом зависимостей
            val completedSteps = executeStepsWithDependencies(plan)

            // Формируем финальный результат
            val result = compileFinalResult(completedSteps, plan)

            plan.status = PlanStatus.COMPLETED
            progressTracker.completePlan(plan)

            OrchestratedResponse.success(result, plan)
        } catch (e: Exception) {
            plan.status = PlanStatus.FAILED
            progressTracker.failPlan(plan, e)

            OrchestratedResponse.error(e.message ?: "Неизвестная ошибка", plan)
        }
    }

    private suspend fun executeStepsWithDependencies(plan: ExecutionPlan): List<CompletedStep> {
        val completedSteps = mutableListOf<CompletedStep>()
        val stepGraph = buildDependencyGraph(plan.steps)

        // Выполняем шаги в топологическом порядке
        for (stepBatch in stepGraph.executionOrder) {
            val batchResults = stepBatch.map { step ->
                executeStep(step, plan.context, completedSteps)
            }
            completedSteps.addAll(batchResults)

            // Проверяем необходимость пользовательского ввода
            val userInputStep = batchResults.find { it.requiresUserInput }
            if (userInputStep != null) {
                return completedSteps // Пауза для пользовательского ввода
            }
        }

        return completedSteps
    }

    private suspend fun executeStep(
        step: PlanStep,
        context: ExecutionContext,
        completedSteps: List<CompletedStep>
    ): CompletedStep {
        step.status = StepStatus.IN_PROGRESS
        progressTracker.updateStepProgress(step, 0.0)

        return try {
            val agent = toolAgents[step.agentType]
                ?: throw IllegalStateException("Agent not found: ${step.agentType}")

            // Подготавливаем входные данные с учетом результатов предыдущих шагов
            val enrichedInput = enrichStepInput(step, completedSteps)

            val result = agent.executeStep(step.copy(input = enrichedInput), context)

            step.status = StepStatus.COMPLETED
            step.output = result.output
            progressTracker.completeStep(step)

            CompletedStep(step, result)
        } catch (e: Exception) {
            step.status = StepStatus.FAILED
            step.error = e.message
            progressTracker.failStep(step, e)

            throw e
        }
    }
}
```

## Примеры использования

### Пример 1: Анализ и исправление багов

**Запрос пользователя:**
> "Проанализируй мой проект на баги и исправь все критические проблемы"

**Процесс выполнения:**

1. **Анализ запроса:**

```
TaskType: COMPLEX_ANALYSIS_AND_FIX
RequiredTools: [PROJECT_SCANNER, CODE_ANALYSIS, CODE_FIXER, USER_INTERACTION]
Scope: весь проект
UserInteractions: подтверждение исправлений
```

2. **Генерация плана:**

```kotlin
ExecutionPlan(
    id = "plan-123",
    description = "Анализ проекта на баги и исправление критических проблем",
    steps = [
        PlanStep(
            id = "scan-project",
            description = "Сканирование файловой системы",
            agentType = PROJECT_SCANNER,
            dependencies = []
        ),
        PlanStep(
            id = "analyze-bugs",
            description = "Поиск багов в коде",
            agentType = CODE_ANALYSIS,
            dependencies = ["scan-project"]
        ),
        PlanStep(
            id = "classify-issues",
            description = "Классификация найденных проблем",
            agentType = ISSUE_CLASSIFIER,
            dependencies = ["analyze-bugs"]
        ),
        PlanStep(
            id = "user-confirmation",
            description = "Показать найденные проблемы и запросить подтверждение",
            agentType = USER_INTERACTION,
            dependencies = ["classify-issues"]
        ),
        PlanStep(
            id = "fix-critical",
            description = "Исправление критических проблем",
            agentType = CODE_FIXER,
            dependencies = ["user-confirmation"]
        ),
        PlanStep(
            id = "generate-report",
            description = "Создание отчета о проделанной работе",
            agentType = REPORT_GENERATOR,
            dependencies = ["fix-critical"]
        )
    ]
)
```

3. **Интерактивное выполнение в чате:**

```
Ассистент: Начинаю анализ вашего проекта на наличие багов...

🔍 Сканирую файловую систему...
✅ Найдено 127 файлов для анализа

🔍 Анализирую код на наличие проблем...
✅ Обнаружено 15 потенциальных проблем:
- 3 критических (null pointer exceptions)
- 7 высоких (resource leaks)
- 5 средних (code smells)

❓ Найдены критические проблемы. Показать детали и исправить?
[Показать детали] [Исправить всё] [Отмена]

Пользователь: Показать детали

Ассистент: Критические проблемы:
1. FileService.kt:45 - Potential NPE when user is null
2. DatabaseManager.kt:123 - Resource leak: connection not closed
3. CacheManager.kt:67 - Race condition in concurrent access

Исправить эти 3 проблемы?
[Да] [Нет] [Показать больше]

Пользователь: Да

Ассистент: 🔧 Исправляю критические проблемы...
✅ Исправлена проблема в FileService.kt:45
✅ Исправлена проблема в DatabaseManager.kt:123
✅ Исправлена проблема в CacheManager.kt:67

📋 Создаю отчет...
✅ Отчет сохранен в reports/bug-fix-2024-01-15.md

Готово! Исправил 3 критические проблемы в вашем коде.
```

### Пример 2: Рефакторинг архитектуры

**Запрос пользователя:**
> "Перенеси бизнес-логику из UI контроллеров в сервисный слой"

**Процесс выполнения:**

1. **Анализ запроса:**

```
TaskType: ARCHITECTURE_REFACTORING
RequiredTools: [ARCHITECTURE_ANALYZER, CODE_EXTRACTOR, SERVICE_GENERATOR, FILE_MANAGER]
Scope: UI контроллеры
UserInteractions: подтверждение extracted логики, выбор имен сервисов
```

2. **Генерация плана:**

```kotlin
ExecutionPlan(
    steps = [
        PlanStep("analyze-architecture", "Анализ текущей архитектуры", ARCHITECTURE_ANALYZER),
        PlanStep("identify-business-logic", "Поиск бизнес-логики в UI", CODE_ANALYZER),
        PlanStep("extract-logic", "Извлечение бизнес-логики", CODE_EXTRACTOR),
        PlanStep("user-confirm", "Подтверждение извлеченной логики", USER_INTERACTION),
        PlanStep("generate-services", "Генерация сервисных классов", SERVICE_GENERATOR),
        PlanStep("update-controllers", "Обновление UI контроллеров", CODE_MODIFIER),
        PlanStep("verify-compilation", "Проверка компиляции", COMPILATION_CHECKER)
    ]
)
```

## Интеграция с существующей системой

### Адаптеры для существующих агентов

```kotlin
class CodeAnalysisToolAdapter(
    private val codeAnalysisAgent: CodeAnalysisAgent
) : ToolAgent {

    override val agentType = AgentType.CODE_ANALYSIS
    override val capabilities = setOf("bug_detection", "quality_analysis", "architecture_analysis")

    override suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult {
        val analysisType = when (step.input.getString("analysis_type")) {
            "bugs" -> AnalysisType.BUG_DETECTION
            "quality" -> AnalysisType.CODE_QUALITY
            "architecture" -> AnalysisType.ARCHITECTURE
            else -> AnalysisType.ALL
        }

        val files = when {
            step.input.contains("files") -> step.input.getList<String>("files")
            step.input.contains("scan_result") -> {
                val scanResult = step.input.getObject<ScanResult>("scan_result")
                scanResult.files
            }
            else -> emptyList()
        }

        val result = codeAnalysisAgent.analyze(
            type = analysisType,
            files = files,
            context = context
        )

        return StepResult.success(
            output = StepOutput.mapOf(
                "analysis_result" to result,
                "issues_found" to result.issues.size,
                "critical_issues" to result.issues.count { it.severity == Severity.CRITICAL }
            )
        )
    }
}
```

### Расширение ChatAgent

```kotlin
class EnhancedChatAgent(
    private val baseAgent: ChatAgent,
    private val orchestrator: OrchestratorAgent
) : ChatAgent by baseAgent {

    override suspend fun ask(request: AgentRequest): AgentResponse {
        // Проверяем, нужно ли использовать оркестратор
        return when {
            isComplexTask(request.request) -> handleWithOrchestrator(request)
            else -> baseAgent.ask(request)
        }
    }

    private suspend fun handleWithOrchestrator(request: AgentRequest): AgentResponse {
        val userRequest = UserRequest.fromAgentRequest(request)
        val orchestratedResponse = orchestrator.orchestrate(userRequest)

        return when (orchestratedResponse.type) {
            OrchestratedResponseType.SUCCESS -> formatSuccessResponse(orchestratedResponse)
            OrchestratedResponseType.REQUIRES_INPUT -> formatInputRequest(orchestratedResponse)
            OrchestratedResponseType.IN_PROGRESS -> formatProgressResponse(orchestratedResponse)
            OrchestratedResponseType.ERROR -> formatErrorResponse(orchestratedResponse)
        }
    }

    private fun isComplexTask(request: String): Boolean {
        val complexKeywords = listOf("проанализируй", "исправь", "рефакторинг", "перенеси", "создай отчет")
        return complexKeywords.any { keyword ->
            request.lowercase().contains(keyword)
        } && request.length > 20
    }
}
```

## Преимущества архитектуры

1. **Модульность** - каждый компонент имеет четкую ответственность
2. **Расширяемость** - легко добавлять новые tool agents
3. **Интерактивность** - естественное взаимодействие с пользователем
4. **Отслеживание** - полный прогресс выполнения сложных задач
5. **Отказоустойчивость** - обработка ошибок и восстановление
6. **Переиспользование** - компоненты можно использовать в разных контекстах

Эта архитектура обеспечивает мощную основу для выполнения сложных многошаговых задач, сохраняя при этом простой и
интуитивный пользовательский интерфейс через чат.