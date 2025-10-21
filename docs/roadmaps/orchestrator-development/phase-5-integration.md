# Этап 5: Интеграция и оптимизация

**Длительность:** 2-3 недели  
**Приоритет:** Низкий  
**Статус:** 📋 Запланировано  
**Зависимости:** Phase 1, Phase 2, Phase 3, Phase 4

---

## 🎯 Цели этапа

Завершить разработку и подготовить систему к продакшену:
- Интеграция со всеми существующими компонентами
- Оптимизация производительности
- Полное тестирование
- Документация

---

## 📋 Задачи

### 5.1 Интеграция с существующими компонентами
- [ ] Интегрировать с `UncertaintyAnalyzer`
- [ ] Связать с `CodeAnalysisAgent`
- [ ] Обеспечить совместимость с `PlannerAgent` и `ExecutorAgent`
- [ ] Создать миграционный путь от старого к новому
- [ ] Добавить backward compatibility
- [ ] Обновить `ChatAgent` для использования нового оркестратора

### 5.2 Оптимизация производительности
- [ ] Реализовать кэширование результатов анализа запросов
- [ ] Оптимизировать работу с большими проектами
- [ ] Добавить ленивую загрузку компонентов
- [ ] Профилирование и устранение узких мест
- [ ] Оптимизировать использование памяти
- [ ] Добавить пулинг ресурсов

### 5.3 Тестирование
- [ ] Unit тесты для всех компонентов (>80% coverage)
- [ ] Интеграционные тесты для флоу
- [ ] Тесты стейт-машины (все переходы)
- [ ] Нагрузочное тестирование (100+ активных планов)
- [ ] Тесты параллельного выполнения
- [ ] Тесты восстановления после ошибок

### 5.4 Документация
- [ ] Обновить архитектурную документацию
- [ ] Создать примеры использования
- [ ] Написать гайд по созданию Tool Agents
- [ ] Обновить README.md
- [ ] Создать API документацию
- [ ] Добавить диаграммы последовательности

---

## 📚 Интеграция

### Интеграция с UncertaintyAnalyzer
```kotlin
class EnhancedAgentOrchestrator(
    private val requestAnalyzer: RequestAnalyzer,
    private val uncertaintyAnalyzer: UncertaintyAnalyzer,
    private val planGenerator: PlanGenerator,
    private val planExecutor: PlanExecutor,
    private val planStorage: PlanStorage,
    private val stateMachine: PlanStateMachine
) {
    suspend fun orchestrate(request: UserRequest): AgentResponse {
        // 1. Проверяем неопределенность
        val uncertaintyScore = uncertaintyAnalyzer.analyze(request.text)
        
        if (uncertaintyScore > 0.3) {
            // Запрашиваем уточнение
            val clarifyingQuestions = uncertaintyAnalyzer.generateQuestions(request.text)
            return AgentResponse.requiresClarification(clarifyingQuestions)
        }
        
        // 2. Анализируем запрос
        val analysis = requestAnalyzer.analyze(request)
        
        // 3. Генерируем план
        val plan = planGenerator.generate(analysis)
        
        // 4. Сохраняем план
        planStorage.save(plan)
        
        // 5. Выполняем план
        stateMachine.transition(PlanEvent.Start)
        val result = planExecutor.executePlan(plan)
        
        return AgentResponse.fromExecutionResult(result)
    }
}
```

### Миграционный путь
```kotlin
class OrchestratorMigration {
    // Адаптер для совместимости со старым API
    class LegacyOrchestratorAdapter(
        private val enhancedOrchestrator: EnhancedAgentOrchestrator
    ) : AgentOrchestrator {
        
        override suspend fun process(
            request: AgentRequest,
            onStepComplete: suspend (OrchestratorStep) -> Unit
        ): AgentResponse {
            // Конвертируем старый формат в новый
            val userRequest = UserRequest.fromAgentRequest(request)
            
            // Используем новый оркестратор
            val response = enhancedOrchestrator.orchestrate(userRequest)
            
            // Конвертируем обратно в старый формат
            return response.toAgentResponse()
        }
    }
    
    // Постепенная миграция
    fun migrateGradually() {
        // Фаза 1: Используем новый оркестратор для простых задач
        // Фаза 2: Используем для средних задач
        // Фаза 3: Полная миграция
    }
}
```

---

## ⚡ Оптимизация

### Кэширование результатов
```kotlin
class CachedRequestAnalyzer(
    private val delegate: RequestAnalyzer,
    private val cache: Cache<String, RequestAnalysis>
) : RequestAnalyzer {
    
    override suspend fun analyze(request: UserRequest): RequestAnalysis {
        val cacheKey = generateCacheKey(request)
        
        return cache.get(cacheKey) ?: run {
            val analysis = delegate.analyze(request)
            cache.put(cacheKey, analysis)
            analysis
        }
    }
    
    private fun generateCacheKey(request: UserRequest): String {
        return "${request.text.hashCode()}_${request.context.projectPath.hashCode()}"
    }
}
```

### Ленивая загрузка Tool Agents
```kotlin
class LazyToolAgentRegistry : ToolAgentRegistry {
    private val agentFactories = mutableMapOf<AgentType, () -> ToolAgent>()
    private val loadedAgents = ConcurrentHashMap<AgentType, ToolAgent>()
    
    fun registerFactory(type: AgentType, factory: () -> ToolAgent) {
        agentFactories[type] = factory
    }
    
    override fun get(agentType: AgentType): ToolAgent? {
        return loadedAgents.getOrPut(agentType) {
            agentFactories[agentType]?.invoke() ?: return null
        }
    }
}
```

### Пулинг ресурсов
```kotlin
class ExecutorPool(private val poolSize: Int = 10) {
    private val executors = ArrayBlockingQueue<PlanExecutor>(poolSize)
    
    init {
        repeat(poolSize) {
            executors.offer(createExecutor())
        }
    }
    
    suspend fun <T> use(block: suspend (PlanExecutor) -> T): T {
        val executor = executors.take()
        try {
            return block(executor)
        } finally {
            executors.offer(executor)
        }
    }
    
    private fun createExecutor(): PlanExecutor {
        return PlanExecutor(
            toolAgentRegistry = ToolAgentRegistry(),
            progressTracker = ProgressTracker()
        )
    }
}
```

---

## 🧪 Тестирование

### Интеграционные тесты
```kotlin
@IntegrationTest
class EnhancedOrchestratorIntegrationTest {
    
    @Test
    fun `should execute complete flow from request to completion`() = runBlocking {
        // Given
        val orchestrator = createOrchestrator()
        val request = UserRequest(
            text = "Проанализируй проект на баги и исправь критические",
            context = ProjectContext("/test/project", "TestProject", "Kotlin")
        )
        
        // When
        val response = orchestrator.orchestrate(request)
        
        // Then
        assertTrue(response.success)
        assertNotNull(response.content)
        
        // Verify plan was created and executed
        val plans = planStorage.listCompleted()
        assertEquals(1, plans.size)
        assertEquals(PlanState.COMPLETED, plans[0].state)
    }
    
    @Test
    fun `should handle user interaction flow`() = runBlocking {
        // Given
        val orchestrator = createOrchestrator()
        val request = UserRequest(
            text = "Найди баги и спроси перед исправлением",
            context = ProjectContext("/test/project", "TestProject", "Kotlin")
        )
        
        // When - первый запрос
        val response1 = orchestrator.orchestrate(request)
        
        // Then - ожидаем запрос на ввод
        assertTrue(response1.requiresUserInput)
        assertNotNull(response1.planId)
        
        // When - возобновляем с пользовательским вводом
        val response2 = orchestrator.resumePlan(response1.planId!!, "Да")
        
        // Then - план завершен
        assertTrue(response2.success)
    }
}
```

### Нагрузочные тесты
```kotlin
@LoadTest
class OrchestratorLoadTest {
    
    @Test
    fun `should handle 100 concurrent plans`() = runBlocking {
        val orchestrator = createOrchestrator()
        val requests = (1..100).map { i ->
            UserRequest(
                text = "Analyze project $i",
                context = ProjectContext("/test/project$i", "Project$i", "Kotlin")
            )
        }
        
        val startTime = System.currentTimeMillis()
        
        // Execute all requests concurrently
        val results = requests.map { request ->
            async {
                orchestrator.orchestrate(request)
            }
        }.awaitAll()
        
        val duration = System.currentTimeMillis() - startTime
        
        // Verify
        assertEquals(100, results.size)
        assertTrue(results.all { it.success })
        assertTrue(duration < 60000) // Less than 1 minute
        
        println("Executed 100 plans in ${duration}ms")
    }
}
```

### Тесты восстановления
```kotlin
@Test
fun `should recover from failure and retry`() = runBlocking {
    // Given
    val failingAgent = mockk<ToolAgent>()
    var attemptCount = 0
    
    coEvery { failingAgent.executeStep(any(), any()) } answers {
        attemptCount++
        if (attemptCount < 3) {
            throw IOException("Temporary failure")
        } else {
            StepResult.success(StepOutput.empty())
        }
    }
    
    val retryPolicy = RetryPolicy(maxAttempts = 3)
    
    // When
    val result = executeWithRetry(retryPolicy) {
        failingAgent.executeStep(mockk(), mockk())
    }
    
    // Then
    assertEquals(3, attemptCount)
    assertTrue(result.success)
}
```

---

## 📊 Метрики производительности

### Целевые показатели
| Метрика | Целевое значение | Текущее |
|---------|------------------|---------|
| Время анализа запроса | < 2s | TBD |
| Время генерации плана | < 5s | TBD |
| Поддержка активных планов | 100+ | TBD |
| Память на план | < 1MB | TBD |
| Время восстановления | < 1s | TBD |
| Пропускная способность | 50 планов/мин | TBD |

### Профилирование
```kotlin
class PerformanceProfiler {
    private val metrics = ConcurrentHashMap<String, MutableList<Long>>()
    
    suspend fun <T> measure(name: String, block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block()
        } finally {
            val duration = System.currentTimeMillis() - start
            metrics.getOrPut(name) { mutableListOf() }.add(duration)
        }
    }
    
    fun getStats(name: String): Stats {
        val durations = metrics[name] ?: return Stats.empty()
        return Stats(
            count = durations.size,
            avg = durations.average(),
            min = durations.minOrNull() ?: 0,
            max = durations.maxOrNull() ?: 0,
            p95 = durations.sorted()[durations.size * 95 / 100]
        )
    }
}
```

---

## 📖 Документация

### API документация
- Все публичные интерфейсы задокументированы с KDoc
- Примеры использования для каждого компонента
- Диаграммы последовательности для основных флоу

### Гайд по созданию Tool Agents
```markdown
# Создание нового Tool Agent

## 1. Определите интерфейс
```kotlin
class MyCustomToolAgent : ToolAgent {
    override val agentType = AgentType.MY_CUSTOM
    override val capabilities = setOf("my_capability")
    
    override suspend fun executeStep(
        step: PlanStep, 
        context: ExecutionContext
    ): StepResult {
        // Ваша логика
    }
}
```

## 2. Зарегистрируйте агента
```kotlin
val registry = ToolAgentRegistry()
registry.register(MyCustomToolAgent())
```

## 3. Используйте в плане
```kotlin
val step = PlanStep(
    id = "my_step",
    description = "My custom step",
    agentType = AgentType.MY_CUSTOM,
    input = StepInput.mapOf("param" to "value")
)
```
```

---

## ✅ Критерии завершения

- [ ] Все компоненты интегрированы
- [ ] Backward compatibility обеспечена
- [ ] Производительность соответствует целевым показателям
- [ ] Unit тесты >80% покрытия
- [ ] Интеграционные тесты покрывают все флоу
- [ ] Нагрузочные тесты пройдены
- [ ] Документация полная и актуальная
- [ ] Примеры использования созданы
- [ ] Миграционный гайд написан
- [ ] Code review пройден
- [ ] Готово к деплою в продакшен

---

## 🚀 Следующие шаги после завершения

1. **Мониторинг в продакшене**
   - Метрики производительности
   - Логирование ошибок
   - Алерты

2. **Сбор обратной связи**
   - От пользователей
   - От разработчиков
   - Метрики использования

3. **Планирование следующих фич**
   - Расширение Tool Agents
   - Machine Learning интеграция
   - Визуализация планов

---

## 📖 Связанные документы

- [Phase 4: Advanced Execution](phase-4-advanced-execution.md)
- [Основной roadmap](../11-enhanced-orchestrator.md)
- [Техническая спецификация](enhanced-orchestrator-technical.md)

---

*Создано: 2025-10-20*
