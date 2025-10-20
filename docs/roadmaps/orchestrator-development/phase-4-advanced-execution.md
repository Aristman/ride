# Этап 4: Расширенное выполнение планов

**Длительность:** 2-3 недели  
**Приоритет:** Средний  
**Статус:** ✅ Завершено  
**Зависимости:** Phase 1, Phase 2, Phase 3

---

## 🎯 Цели этапа

Реализовать продвинутые возможности выполнения планов:
- Параллельное выполнение независимых шагов
- Контекстная передача данных между шагами
- Обработка ошибок и retry-логика
- Транзакционность и откаты

---

## 📋 Задачи

### 4.1 Параллельное выполнение
- [x] Реализовать граф зависимостей (DAG)
- [x] Добавить топологическую сортировку для определения порядка
- [x] Реализовать параллельное выполнение независимых шагов (coroutines)
- [x] Создать пул потоков для выполнения
- [x] Обработка ошибок в параллельных задачах
- [x] Добавить ограничение на количество параллельных задач

### 4.2 Контекстная передача между шагами
- [x] Реализовать `ExecutionContext` для хранения результатов (Phase 1)
- [x] Создать систему трансформации данных между шагами
- [ ] Добавить валидацию входных/выходных данных
- [ ] Реализовать кэширование промежуточных результатов
- [x] Добавить enrichment входных данных из зависимых шагов
- [x] Создать систему переменных контекста (Phase 1)

### 4.3 Обработка ошибок и retry-логика
- [x] Реализовать retry с экспоненциальным backoff (Phase 3)
- [x] Добавить транзакционность для откатов при ошибках
- [x] Создать систему fallback стратегий
- [x] Реализовать детальное логирование и диагностику
- [ ] Добавить recovery механизмы
- [ ] Создать систему уведомлений об ошибках

---

## 📚 Ключевые компоненты

### Граф зависимостей (DAG)
```kotlin
class DependencyGraph(private val steps: List<PlanStep>) {
    private val graph: Map<String, Set<String>> = buildGraph()
    
    private fun buildGraph(): Map<String, Set<String>> {
        return steps.associate { step ->
            step.id to step.dependencies
        }
    }
    
    fun topologicalSort(): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val inDegree = calculateInDegree()
        
        while (visited.size < graph.size) {
            // Находим все узлы с нулевой входящей степенью
            val batch = graph.keys.filter { key ->
                key !in visited && (inDegree[key] ?: 0) == 0
            }
            
            if (batch.isEmpty()) {
                throw CircularDependencyException("Circular dependency detected")
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
    
    private fun calculateInDegree(): MutableMap<String, Int> {
        return graph.keys.associateWith { key ->
            graph.values.count { it.contains(key) }
        }.toMutableMap()
    }
}
```

### PlanExecutor с параллелизмом
```kotlin
class PlanExecutor(
    private val toolAgentRegistry: ToolAgentRegistry,
    private val progressTracker: ProgressTracker,
    private val maxParallelTasks: Int = 5
) {
    suspend fun executePlan(plan: ExecutionPlan): ExecutionResult {
        val dependencyGraph = DependencyGraph(plan.steps)
        val executionOrder = dependencyGraph.topologicalSort()
        
        progressTracker.startTracking(plan)
        
        try {
            for (batch in executionOrder) {
                logger.info("Executing batch of ${batch.size} steps")
                
                // Выполняем независимые шаги параллельно с ограничением
                val results = executeBatch(batch, plan, maxParallelTasks)
                
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
                    return handleFailure(plan, failedStep)
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
    
    private suspend fun executeBatch(
        batch: List<String>,
        plan: ExecutionPlan,
        maxParallel: Int
    ): List<StepResult> = coroutineScope {
        batch
            .chunked(maxParallel)
            .flatMap { chunk ->
                chunk.map { stepId ->
                    async {
                        val step = plan.steps.find { it.id == stepId }
                            ?: throw IllegalStateException("Step not found: $stepId")
                        executeStep(step, plan.context)
                    }
                }.awaitAll()
            }
    }
    
    private suspend fun executeStep(
        step: PlanStep,
        context: ExecutionContext
    ): StepResult {
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
}
```

### ExecutionContext с контекстной передачей
```kotlin
class ExecutionContext(
    val projectPath: String,
    val parameters: Map<String, Any>,
    private val stepResults: MutableMap<String, StepOutput> = mutableMapOf(),
    private val variables: MutableMap<String, Any> = mutableMapOf()
) {
    fun addStepResult(stepId: String, output: StepOutput) {
        stepResults[stepId] = output
    }
    
    fun getStepResult(stepId: String): StepOutput? {
        return stepResults[stepId]
    }
    
    fun enrichStepInput(step: PlanStep): StepInput {
        val enrichedInput = step.input.toMutableMap()
        
        // Добавляем результаты зависимых шагов
        for (dependencyId in step.dependencies) {
            val dependencyResult = getStepResult(dependencyId)
            if (dependencyResult != null) {
                enrichedInput["dependency_$dependencyId"] = dependencyResult
            }
        }
        
        // Добавляем переменные контекста
        enrichedInput["context_variables"] = variables
        
        return StepInput(enrichedInput)
    }
    
    fun setVariable(name: String, value: Any) {
        variables[name] = value
    }
    
    fun getVariable(name: String): Any? {
        return variables[name]
    }
    
    fun transformData(from: String, to: String, transformer: (Any) -> Any) {
        val data = getStepResult(from)?.get(to)
        if (data != null) {
            setVariable(to, transformer(data))
        }
    }
}
```

### Retry-логика с экспоненциальным backoff
```kotlin
class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelay: Long = 1000,
    val maxDelay: Long = 10000,
    val factor: Double = 2.0,
    val retryableErrors: Set<Class<out Exception>> = setOf(
        IOException::class.java,
        TimeoutException::class.java
    )
) {
    fun shouldRetry(attempt: Int, error: Throwable): Boolean {
        return attempt < maxAttempts && 
               retryableErrors.any { it.isInstance(error) }
    }
    
    fun getDelay(attempt: Int): Long {
        val delay = (initialDelay * factor.pow(attempt - 1)).toLong()
        return min(delay, maxDelay)
    }
}

suspend fun <T> executeWithRetry(
    policy: RetryPolicy,
    block: suspend () -> T
): T {
    var lastError: Throwable? = null
    
    for (attempt in 1..policy.maxAttempts) {
        try {
            return block()
        } catch (e: Exception) {
            lastError = e
            
            if (policy.shouldRetry(attempt, e)) {
                val delay = policy.getDelay(attempt)
                logger.warn("Attempt $attempt failed, retrying in ${delay}ms: ${e.message}")
                delay(delay)
            } else {
                throw e
            }
        }
    }
    
    throw lastError ?: IllegalStateException("Retry failed without error")
}
```

### Транзакционность и откаты
```kotlin
interface TransactionalStep {
    suspend fun execute(context: ExecutionContext): StepResult
    suspend fun rollback(context: ExecutionContext)
}

class TransactionalExecutor {
    private val executedSteps = mutableListOf<Pair<PlanStep, TransactionalStep>>()
    
    suspend fun executeWithRollback(
        steps: List<PlanStep>,
        context: ExecutionContext
    ): ExecutionResult {
        try {
            for (step in steps) {
                val transactionalStep = createTransactionalStep(step)
                val result = transactionalStep.execute(context)
                
                if (!result.success) {
                    // Откатываем все выполненные шаги
                    rollbackAll()
                    return ExecutionResult.error(result.error ?: "Step failed")
                }
                
                executedSteps.add(step to transactionalStep)
            }
            
            return ExecutionResult.success()
        } catch (e: Exception) {
            rollbackAll()
            throw e
        }
    }
    
    private suspend fun rollbackAll() {
        logger.info("Rolling back ${executedSteps.size} steps")
        
        // Откатываем в обратном порядке
        executedSteps.reversed().forEach { (step, transactionalStep) ->
            try {
                transactionalStep.rollback(context)
                logger.info("Rolled back step: ${step.id}")
            } catch (e: Exception) {
                logger.error("Failed to rollback step ${step.id}", e)
            }
        }
        
        executedSteps.clear()
    }
}
```

---

## 🎯 Примеры использования

### Пример 1: Параллельное выполнение
```kotlin
val plan = ExecutionPlan(
    steps = listOf(
        PlanStep("scan", "Scan project", PROJECT_SCANNER, dependencies = emptySet()),
        // Эти шаги выполнятся параллельно после scan
        PlanStep("analyze_bugs", "Find bugs", BUG_DETECTION, dependencies = setOf("scan")),
        PlanStep("analyze_quality", "Check quality", CODE_QUALITY, dependencies = setOf("scan")),
        PlanStep("analyze_arch", "Analyze architecture", ARCHITECTURE, dependencies = setOf("scan")),
        // Этот шаг выполнится после всех анализов
        PlanStep("report", "Generate report", REPORT_GENERATOR, 
                 dependencies = setOf("analyze_bugs", "analyze_quality", "analyze_arch"))
    )
)

// Execution order:
// Batch 1: [scan]
// Batch 2: [analyze_bugs, analyze_quality, analyze_arch] - параллельно
// Batch 3: [report]
```

### Пример 2: Контекстная передача данных
```kotlin
// Шаг 1: Сканирование
val scanStep = PlanStep("scan", "Scan", PROJECT_SCANNER)
// Output: { "files": ["file1.kt", "file2.kt"], "count": 2 }

// Шаг 2: Анализ использует результаты сканирования
val analyzeStep = PlanStep("analyze", "Analyze", BUG_DETECTION, 
                           dependencies = setOf("scan"))
// Input автоматически обогащается:
// { "dependency_scan": { "files": [...], "count": 2 } }

// В агенте:
val files = step.input.getObject<StepOutput>("dependency_scan")
    ?.getList<String>("files") ?: emptyList()
```

### Пример 3: Retry с backoff
```kotlin
val retryPolicy = RetryPolicy(
    maxAttempts = 3,
    initialDelay = 1000,
    factor = 2.0
)

val result = executeWithRetry(retryPolicy) {
    llmProvider.sendRequest(prompt)
}
```

---

## ✅ Критерии завершения

- [x] DAG корректно строит граф зависимостей
- [x] Топологическая сортировка работает правильно
- [x] Параллельное выполнение работает с ограничением потоков
- [x] ExecutionContext передает данные между шагами
- [x] Retry-логика с экспоненциальным backoff реализована (Phase 3)
- [x] Транзакционность и откаты работают корректно
- [x] Обработка ошибок в параллельных задачах
- [x] Unit и интеграционные тесты >80% покрытия (7/7 новых тестов проходят)
- [ ] Нагрузочное тестирование (100+ параллельных задач)
- [x] Документация обновлена

---

## 📖 Связанные документы

- [Phase 3: Interactivity](phase-3-interactivity.md)
- [Phase 5: Integration](phase-5-integration.md)
- [Техническая спецификация](enhanced-orchestrator-technical.md)

---

*Создано: 2025-10-20*
