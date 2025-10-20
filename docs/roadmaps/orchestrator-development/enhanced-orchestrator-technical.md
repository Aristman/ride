# Техническая спецификация: Расширенный агент-оркестратор

## 📐 Архитектурные компоненты

### 1. RequestAnalyzer

**Назначение:** Анализ пользовательских запросов и определение типа задачи.

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
    CODE_ANALYSIS,
    BUG_DETECTION,
    REFACTORING,
    REPORT_GENERATION,
    COMPLEX_MULTI_STEP,
    SIMPLE_QUERY
}

enum class ComplexityLevel {
    SIMPLE,      // 1-2 шага
    MODERATE,    // 3-5 шагов
    COMPLEX,     // 6-10 шагов
    VERY_COMPLEX // 10+ шагов
}
```

**Реализация с LLM:**

```kotlin
class LLMRequestAnalyzer(
    private val llmProvider: LLMProvider
) : RequestAnalyzer {
    
    override suspend fun analyze(request: UserRequest): RequestAnalysis {
        val prompt = buildAnalysisPrompt(request)
        val response = llmProvider.sendRequest(prompt)
        
        return parseAnalysisResponse(response).apply {
            validateCapabilities()
            enrichWithProjectContext(request.context)
        }
    }
    
    private fun buildAnalysisPrompt(request: UserRequest): String {
        return """
            Проанализируй запрос пользователя и определи:
            1. Тип задачи (CODE_ANALYSIS, BUG_DETECTION, REFACTORING, etc.)
            2. Необходимые инструменты (PROJECT_SCANNER, BUG_DETECTION, CODE_FIXER, etc.)
            3. Параметры и контекст (файлы, директории, критерии)
            4. Требуется ли пользовательский ввод
            5. Оценка сложности (SIMPLE, MODERATE, COMPLEX, VERY_COMPLEX)
            
            Запрос: "${request.text}"
            Контекст: ${request.context.description}
            
            Ответь в формате JSON:
            {
              "taskType": "...",
              "requiredTools": ["...", "..."],
              "parameters": {...},
              "requiresUserInput": true/false,
              "estimatedComplexity": "..."
            }
        """.trimIndent()
    }
}
```

### 2. PlanStateMachine

**Назначение:** Управление жизненным циклом плана выполнения.

```kotlin
class PlanStateMachine {
    private var currentState: PlanState = PlanState.CREATED
    private val listeners = mutableListOf<StateChangeListener>()
    
    fun transition(event: PlanEvent): PlanState {
        val newState = when (currentState) {
            PlanState.CREATED -> handleCreatedState(event)
            PlanState.ANALYZING -> handleAnalyzingState(event)
            PlanState.IN_PROGRESS -> handleInProgressState(event)
            PlanState.PAUSED -> handlePausedState(event)
            PlanState.REQUIRES_INPUT -> handleRequiresInputState(event)
            PlanState.COMPLETED -> handleCompletedState(event)
            PlanState.FAILED -> handleFailedState(event)
        }
        
        if (isValidTransition(currentState, newState)) {
            val oldState = currentState
            currentState = newState
            notifyListeners(oldState, newState, event)
            return newState
        } else {
            throw InvalidStateTransitionException(
                "Invalid transition from $currentState to $newState on event $event"
            )
        }
    }
    
    private fun handleCreatedState(event: PlanEvent): PlanState {
        return when (event) {
            is PlanEvent.Start -> PlanState.ANALYZING
            else -> currentState
        }
    }
    
    private fun handleAnalyzingState(event: PlanEvent): PlanState {
        return when (event) {
            is PlanEvent.AnalysisComplete -> PlanState.IN_PROGRESS
            is PlanEvent.RequiresInput -> PlanState.REQUIRES_INPUT
            is PlanEvent.Error -> PlanState.FAILED
            else -> currentState
        }
    }
    
    private fun handleInProgressState(event: PlanEvent): PlanState {
        return when (event) {
            is PlanEvent.Pause -> PlanState.PAUSED
            is PlanEvent.RequiresInput -> PlanState.REQUIRES_INPUT
            is PlanEvent.AllStepsCompleted -> PlanState.COMPLETED
            is PlanEvent.Error -> PlanState.FAILED
            else -> currentState
        }
    }
    
    private fun handleRequiresInputState(event: PlanEvent): PlanState {
        return when (event) {
            is PlanEvent.UserInputReceived -> PlanState.IN_PROGRESS
            is PlanEvent.Error -> PlanState.FAILED
            else -> currentState
        }
    }
    
    private fun isValidTransition(from: PlanState, to: PlanState): Boolean {
        return VALID_TRANSITIONS[from]?.contains(to) ?: false
    }
    
    companion object {
        private val VALID_TRANSITIONS = mapOf(
            PlanState.CREATED to setOf(PlanState.ANALYZING),
            PlanState.ANALYZING to setOf(PlanState.IN_PROGRESS, PlanState.REQUIRES_INPUT, PlanState.FAILED),
            PlanState.IN_PROGRESS to setOf(PlanState.PAUSED, PlanState.REQUIRES_INPUT, PlanState.COMPLETED, PlanState.FAILED),
            PlanState.PAUSED to setOf(PlanState.IN_PROGRESS, PlanState.FAILED),
            PlanState.REQUIRES_INPUT to setOf(PlanState.IN_PROGRESS, PlanState.FAILED),
            PlanState.COMPLETED to emptySet(),
            PlanState.FAILED to setOf(PlanState.IN_PROGRESS) // retry
        )
    }
}

enum class PlanState {
    CREATED,
    ANALYZING,
    IN_PROGRESS,
    PAUSED,
    REQUIRES_INPUT,
    COMPLETED,
    FAILED
}

sealed class PlanEvent {
    object Start : PlanEvent()
    object Pause : PlanEvent()
    object Resume : PlanEvent()
    object AnalysisComplete : PlanEvent()
    object RequiresInput : PlanEvent()
    data class UserInputReceived(val input: String) : PlanEvent()
    data class StepCompleted(val stepId: String) : PlanEvent()
    data class Error(val error: Throwable) : PlanEvent()
    object AllStepsCompleted : PlanEvent()
}
```

### 3. ToolAgent Interface

**Назначение:** Базовый интерфейс для всех агентов-инструментов.

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
    var output: StepOutput? = null,
    var error: String? = null
)

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class StepInput(
    private val data: Map<String, Any>
) {
    fun getString(key: String): String? = data[key] as? String
    fun getInt(key: String): Int? = data[key] as? Int
    fun getBoolean(key: String): Boolean? = data[key] as? Boolean
    
    @Suppress("UNCHECKED_CAST")
    fun <T> getList(key: String): List<T>? = data[key] as? List<T>
    
    @Suppress("UNCHECKED_CAST")
    fun <T> getObject(key: String): T? = data[key] as? T
    
    fun contains(key: String): Boolean = data.containsKey(key)
    
    fun toMutableMap(): MutableMap<String, Any> = data.toMutableMap()
    
    companion object {
        fun mapOf(vararg pairs: Pair<String, Any>): StepInput {
            return StepInput(pairs.toMap())
        }
        
        fun dependsOn(stepId: String): StepInput {
            return StepInput(mapOf("depends_on" to stepId))
        }
    }
}

data class StepOutput(
    private val data: Map<String, Any>
) {
    // Аналогично StepInput
    
    companion object {
        fun mapOf(vararg pairs: Pair<String, Any>): StepOutput {
            return StepOutput(pairs.toMap())
        }
        
        fun empty(): StepOutput = StepOutput(emptyMap())
    }
}

data class StepResult(
    val success: Boolean,
    val output: StepOutput,
    val error: String? = null,
    val requiresUserInput: Boolean = false,
    val userPrompt: String? = null
) {
    companion object {
        fun success(output: StepOutput) = StepResult(true, output)
        fun error(error: String) = StepResult(false, StepOutput.empty(), error)
        fun requiresInput(prompt: String, partialOutput: StepOutput = StepOutput.empty()) = 
            StepResult(false, partialOutput, null, true, prompt)
    }
}

enum class AgentType {
    // Code Analysis Tool Agents
    PROJECT_SCANNER,
    CODE_CHUNKER,
    BUG_DETECTION,
    CODE_QUALITY,
    ARCHITECTURE_ANALYSIS,
    SECURITY_ANALYSIS,
    
    // Code Modification Tool Agents
    CODE_FIXER,
    REFACTORING,
    CODE_GENERATOR,
    
    // Utility Tool Agents
    REPORT_GENERATOR,
    USER_INTERACTION,
    FILE_OPERATIONS,
    GIT_OPERATIONS,
    
    // Existing Agents
    PLANNER,
    EXECUTOR
}
```

### 4. Примеры Tool Agents

#### ProjectScannerToolAgent

```kotlin
class ProjectScannerToolAgent(
    private val project: Project
) : ToolAgent {
    
    override val agentType = AgentType.PROJECT_SCANNER
    override val capabilities = setOf("file_discovery", "pattern_matching", "exclusion_filtering")
    
    override val capabilities: AgentCapabilities
        get() = AgentCapabilities(
            name = "ProjectScanner",
            description = "Сканирование файловой системы проекта",
            supportedOperations = capabilities
        )
    
    override suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult {
        val patterns = step.input.getList<String>("patterns") ?: listOf("**/*.kt", "**/*.java")
        val excludePatterns = step.input.getList<String>("exclude_patterns") ?: 
            listOf("**/build/**", "**/target/**", "**/.git/**")
        
        try {
            val files = scanProject(patterns, excludePatterns)
            
            return StepResult.success(
                StepOutput.mapOf(
                    "files" to files,
                    "total_count" to files.size,
                    "scan_time" to System.currentTimeMillis(),
                    "patterns_used" to patterns
                )
            )
        } catch (e: Exception) {
            return StepResult.error("Ошибка сканирования проекта: ${e.message}")
        }
    }
    
    private fun scanProject(patterns: List<String>, excludePatterns: List<String>): List<String> {
        val projectPath = project.basePath ?: return emptyList()
        val result = mutableListOf<String>()
        
        VirtualFileManager.getInstance().findFileByUrl("file://$projectPath")?.let { root ->
            VfsUtil.iterateChildrenRecursively(root, null) { file ->
                if (!file.isDirectory && matchesPatterns(file.path, patterns, excludePatterns)) {
                    result.add(file.path)
                }
                true
            }
        }
        
        return result
    }
    
    private fun matchesPatterns(path: String, includes: List<String>, excludes: List<String>): Boolean {
        // Проверка на исключения
        if (excludes.any { pattern -> matchesGlob(path, pattern) }) {
            return false
        }
        // Проверка на включения
        return includes.any { pattern -> matchesGlob(path, pattern) }
    }
    
    private fun matchesGlob(path: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("**/", ".*")
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .replace("?", "[^/]")
        return path.matches(Regex(regex))
    }
    
    override fun canHandle(step: PlanStep): Boolean {
        return step.agentType == AgentType.PROJECT_SCANNER
    }
    
    override suspend fun ask(req: AgentRequest): AgentResponse {
        throw UnsupportedOperationException("ToolAgent не поддерживает прямые запросы")
    }
    
    override fun start(req: AgentRequest): Flow<AgentEvent>? = null
    
    override fun updateSettings(settings: AgentSettings) {}
    
    override fun dispose() {}
}
```

#### BugDetectionToolAgent

```kotlin
class BugDetectionToolAgent(
    private val codeAnalysisAgent: CodeAnalysisAgent
) : ToolAgent {
    
    override val agentType = AgentType.BUG_DETECTION
    override val capabilities = setOf("bug_detection", "null_pointer_analysis", "resource_leak_detection")
    
    override val capabilities: AgentCapabilities
        get() = AgentCapabilities(
            name = "BugDetection",
            description = "Поиск багов и потенциальных проблем в коде",
            supportedOperations = capabilities
        )
    
    override suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult {
        val files = step.input.getList<String>("files") ?: run {
            // Если файлы не указаны, берем из зависимого шага
            val scanResult = step.input.getObject<StepOutput>("dependency_scan")
            scanResult?.getList<String>("files") ?: emptyList()
        }
        
        if (files.isEmpty()) {
            return StepResult.error("Не указаны файлы для анализа")
        }
        
        try {
            val findings = mutableListOf<Finding>()
            
            for (file in files) {
                val fileFindings = codeAnalysisAgent.analyzeFile(
                    filePath = file,
                    analysisTypes = setOf(AnalysisType.BUG_DETECTION)
                )
                findings.addAll(fileFindings)
            }
            
            val criticalFindings = findings.filter { it.severity == Severity.CRITICAL }
            val highFindings = findings.filter { it.severity == Severity.HIGH }
            
            return StepResult.success(
                StepOutput.mapOf(
                    "findings" to findings,
                    "total_count" to findings.size,
                    "critical_count" to criticalFindings.size,
                    "high_count" to highFindings.size,
                    "critical_findings" to criticalFindings,
                    "high_findings" to highFindings
                )
            )
        } catch (e: Exception) {
            return StepResult.error("Ошибка анализа багов: ${e.message}")
        }
    }
    
    override fun canHandle(step: PlanStep): Boolean {
        return step.agentType == AgentType.BUG_DETECTION
    }
    
    // ... остальные методы Agent
}
```

#### UserInteractionAgent

```kotlin
class UserInteractionAgent : ToolAgent {
    
    override val agentType = AgentType.USER_INTERACTION
    override val capabilities = setOf("user_input", "confirmation", "choice_selection")
    
    override val capabilities: AgentCapabilities
        get() = AgentCapabilities(
            name = "UserInteraction",
            description = "Взаимодействие с пользователем",
            supportedOperations = capabilities
        )
    
    override suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult {
        val promptType = step.input.getString("prompt_type") ?: "confirmation"
        val message = step.input.getString("message") ?: "Подтвердите действие"
        val options = step.input.getList<String>("options") ?: listOf("Да", "Нет")
        val data = step.input.getObject<Any>("data")
        
        // Возвращаем результат, требующий пользовательского ввода
        return StepResult.requiresInput(
            prompt = buildPrompt(promptType, message, options, data),
            partialOutput = StepOutput.mapOf(
                "prompt_type" to promptType,
                "message" to message,
                "options" to options
            )
        )
    }
    
    private fun buildPrompt(type: String, message: String, options: List<String>, data: Any?): String {
        return when (type) {
            "confirmation" -> {
                buildString {
                    appendLine(message)
                    if (data != null) {
                        appendLine()
                        appendLine(formatData(data))
                        appendLine()
                    }
                    append(options.joinToString(" / "))
                }
            }
            "choice" -> {
                buildString {
                    appendLine(message)
                    if (data != null) {
                        appendLine()
                        appendLine(formatData(data))
                        appendLine()
                    }
                    appendLine("Выберите один из вариантов:")
                    options.forEachIndexed { i, opt ->
                        appendLine("${i + 1}. $opt")
                    }
                }
            }
            "input" -> {
                buildString {
                    appendLine(message)
                    if (data != null) {
                        appendLine()
                        appendLine(formatData(data))
                        appendLine()
                    }
                    append("Введите значение:")
                }
            }
            else -> message
        }
    }
    
    private fun formatData(data: Any): String {
        return when (data) {
            is List<*> -> data.joinToString("\n") { "- $it" }
            is Map<*, *> -> data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            else -> data.toString()
        }
    }
    
    override fun canHandle(step: PlanStep): Boolean {
        return step.agentType == AgentType.USER_INTERACTION
    }
    
    // ... остальные методы Agent
}
```

### 5. PlanExecutor с параллелизмом

```kotlin
class PlanExecutor(
    private val toolAgentRegistry: ToolAgentRegistry,
    private val progressTracker: ProgressTracker
) {
    private val logger = Logger.getInstance(PlanExecutor::class.java)
    
    suspend fun executePlan(plan: ExecutionPlan): ExecutionResult {
        val dependencyGraph = buildDependencyGraph(plan.steps)
        val executionOrder = topologicalSort(dependencyGraph)
        
        progressTracker.startTracking(plan)
        
        try {
            for (batch in executionOrder) {
                logger.info("Executing batch of ${batch.size} steps")
                
                // Выполняем независимые шаги параллельно
                val results = coroutineScope {
                    batch.map { stepId ->
                        async {
                            val step = plan.steps.find { it.id == stepId }
                                ?: throw IllegalStateException("Step not found: $stepId")
                            executeStep(step, plan.context)
                        }
                    }.awaitAll()
                }
                
                // Проверяем, требуется ли пользовательский ввод
                val userInputStep = results.find { it.requiresUserInput }
                if (userInputStep != null) {
                    return ExecutionResult.requiresInput(
                        userInputStep.userPrompt!!,
                        plan.id
                    )
                }
                
                // Проверяем ошибки
                val failedStep = results.find { !it.success }
                if (failedStep != null) {
                    return ExecutionResult.error(failedStep.error ?: "Unknown error")
                }
            }
            
            progressTracker.completePlan(plan.id)
            return ExecutionResult.success(
                StepOutput.mapOf(
                    "completed_steps" to plan.steps.size,
                    "total_time" to (System.currentTimeMillis() - plan.startTime)
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error executing plan", e)
            progressTracker.failPlan(plan.id, e)
            return ExecutionResult.error(e.message ?: "Unknown error")
        }
    }
    
    private suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult {
        step.status = StepStatus.IN_PROGRESS
        progressTracker.updateStepProgress(step.id, 0.0)
        
        return try {
            val agent = toolAgentRegistry.get(step.agentType)
                ?: throw IllegalStateException("Agent not found: ${step.agentType}")
            
            // Обогащаем входные данные результатами зависимых шагов
            val enrichedInput = context.enrichStepInput(step)
            
            val result = agent.executeStep(step.copy(input = enrichedInput), context)
            
            step.status = if (result.success) StepStatus.COMPLETED else StepStatus.FAILED
            step.output = result.output
            step.error = result.error
            
            // Сохраняем результат в контексте
            if (result.success) {
                context.addStepResult(step.id, result.output)
            }
            
            progressTracker.completeStep(step.id)
            
            result
        } catch (e: Exception) {
            step.status = StepStatus.FAILED
            step.error = e.message
            progressTracker.failStep(step.id, e)
            
            StepResult.error(e.message ?: "Unknown error")
        }
    }
    
    private fun buildDependencyGraph(steps: List<PlanStep>): Map<String, Set<String>> {
        return steps.associate { step ->
            step.id to step.dependencies
        }
    }
    
    private fun topologicalSort(graph: Map<String, Set<String>>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val inDegree = graph.keys.associateWith { key ->
            graph.values.count { it.contains(key) }
        }.toMutableMap()
        
        while (visited.size < graph.size) {
            // Находим все узлы с нулевой входящей степенью
            val batch = graph.keys.filter { key ->
                key !in visited && (inDegree[key] ?: 0) == 0
            }
            
            if (batch.isEmpty()) {
                throw IllegalStateException("Circular dependency detected")
            }
            
            result.add(batch)
            visited.addAll(batch)
            
            // Уменьшаем входящую степень для зависимых узлов
            batch.forEach { node ->
                graph[node]?.forEach { dependent ->
                    inDegree[dependent] = (inDegree[dependent] ?: 0) - 1
                }
            }
        }
        
        return result
    }
}

data class ExecutionResult(
    val success: Boolean,
    val output: StepOutput? = null,
    val error: String? = null,
    val requiresUserInput: Boolean = false,
    val userPrompt: String? = null,
    val planId: String? = null
) {
    companion object {
        fun success(output: StepOutput) = ExecutionResult(true, output)
        fun error(error: String) = ExecutionResult(false, null, error)
        fun requiresInput(prompt: String, planId: String) = 
            ExecutionResult(false, null, null, true, prompt, planId)
    }
}
```

## 📊 Диаграммы последовательности

### Сценарий 1: Простой анализ кода

```
User → ChatAgent: "Проанализируй проект на баги"
ChatAgent → EnhancedOrchestrator: orchestrate(request)
EnhancedOrchestrator → RequestAnalyzer: analyze(request)
RequestAnalyzer → EnhancedOrchestrator: RequestAnalysis(CODE_ANALYSIS)
EnhancedOrchestrator → PlanGenerator: generate(analysis)
PlanGenerator → EnhancedOrchestrator: ExecutionPlan
EnhancedOrchestrator → StateMachine: transition(Start)
StateMachine → EnhancedOrchestrator: IN_PROGRESS
EnhancedOrchestrator → PlanExecutor: executePlan(plan)
PlanExecutor → ProjectScannerAgent: executeStep(scan)
ProjectScannerAgent → PlanExecutor: StepResult(files)
PlanExecutor → BugDetectionAgent: executeStep(analyze)
BugDetectionAgent → PlanExecutor: StepResult(findings)
PlanExecutor → EnhancedOrchestrator: ExecutionResult(success)
EnhancedOrchestrator → StateMachine: transition(AllStepsCompleted)
StateMachine → EnhancedOrchestrator: COMPLETED
EnhancedOrchestrator → ChatAgent: AgentResponse(result)
ChatAgent → User: "Найдено 5 багов..."
```

### Сценарий 2: Интерактивный анализ с подтверждением

```
User → ChatAgent: "Проанализируй и исправь баги"
... (аналогично сценарию 1 до BugDetectionAgent)
PlanExecutor → UserInteractionAgent: executeStep(confirm)
UserInteractionAgent → PlanExecutor: StepResult(requiresInput)
PlanExecutor → EnhancedOrchestrator: ExecutionResult(requiresInput)
EnhancedOrchestrator → StateMachine: transition(RequiresInput)
StateMachine → EnhancedOrchestrator: REQUIRES_INPUT
EnhancedOrchestrator → ChatAgent: AgentResponse(requiresInput)
ChatAgent → User: "Найдено 5 багов. Исправить? [Да/Нет]"
User → ChatAgent: "Да"
ChatAgent → EnhancedOrchestrator: resumePlan(planId, "Да")
EnhancedOrchestrator → StateMachine: transition(UserInputReceived)
StateMachine → EnhancedOrchestrator: IN_PROGRESS
EnhancedOrchestrator → PlanExecutor: resumeExecution(plan)
PlanExecutor → CodeFixerAgent: executeStep(fix)
CodeFixerAgent → PlanExecutor: StepResult(fixed)
... (завершение как в сценарии 1)
```

---

*Документ создан: 2025-10-20*
*Версия: 1.0*
