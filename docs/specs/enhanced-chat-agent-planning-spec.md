# Спецификация: Умная оценка неопределенности и адаптивное планирование EnhancedChatAgent

## 1. Обзор и цели

### 1.1 Проблема
Текущий EnhancedChatAgent выполняет RAG обогащение запросов на начальном этапе, что усложняет обработку простых запросов и увеличивает время ответа.

### 1.2 Решение
Внедрить умную систему оценки неопределенности с адаптивным планированием, где:
- Простые запросы обрабатываются напрямую без лишних шагов
- Сложные запросы проходят через оценку неопределенности → планирование → выполнение
- RAG используется только на этапе планирования для обогащения информацией из проекта
- Планы могут динамически модифицироваться в процессе выполнения

### 1.3 Цели
- **Сократить время обработки простых запросов на 60-70%**
- **Повысить точность планирования сложных задач**
- **Обеспечить адаптивность планов в runtime**
- **Уменьшить нагрузку на систему для базовых сценариев**

## 2. Архитектура решения

### 2.1 Новая цепочка взаимодействия
```
Запрос пользователя → Оценка неопределенности → Планирование (с RAG) → Динамическая модификация → Выполнение → Результат
```

### 2.2 Ключевые компоненты

#### 2.2.1 RequestComplexityAnalyzer
```kotlin
class RequestComplexityAnalyzer {
    suspend fun analyzeUncertainty(request: String, context: ChatContext): UncertaintyResult
}

data class UncertaintyResult(
    val score: Double,           // 0.0 - 1.0
    val complexity: ComplexityLevel,
    val suggestedActions: List<String>,
    val reasoning: String
)

enum class ComplexityLevel {
    SIMPLE,     // 0.0-0.2: прямой ответ
    MEDIUM,     // 0.2-0.5: простой план
    COMPLEX     // 0.5-1.0: полный план с RAG
}
```

#### 2.2.2 RequestPlanner
```kotlin
class RequestPlanner {
    suspend fun createPlan(
        request: String,
        uncertainty: UncertaintyResult,
        context: ChatContext
    ): ExecutionPlan
}

data class ExecutionPlan(
    val id: String,
    val steps: List<PlanStep>,
    val dependencies: Map<String, Set<String>>,
    val metadata: Map<String, Any>,
    val version: Int,
    val isAdaptive: Boolean
)
```

#### 2.2.3 RAGPlanEnricher
```kotlin
class RAGPlanEnricher {
    suspend fun enrichPlan(
        plan: ExecutionPlan,
        request: String,
        context: ChatContext
    ): ExecutionPlan
}
```

#### 2.2.4 DynamicPlanModifier (интеграция существующего)
```kotlin
class DynamicPlanModifier {
    suspend fun adaptPlan(
        plan: ExecutionPlan,
        executionResults: Map<String, StepResult>
    ): ExecutionPlan
}
```

### 2.3 Пороговые значения
```kotlin
object UncertaintyThresholds {
    const val SIMPLE_MAX = 0.2      // Прямой ответ
    const val MEDIUM_MAX = 0.5      // Простой план
    const val COMPLEX_MIN = 0.5     // Полный план с RAG
}
```

## 3. Потоки выполнения

### 3.1 Простой запрос (неопределенность < 0.2)
```kotlin
// Прямой вызов base ChatAgent
val uncertainty = analyzer.analyzeUncertainty(request, context)
if (uncertainty.score < SIMPLE_MAX) {
    return baseChatAgent.ask(request)
}
```

### 3.2 Запрос средней сложности (0.2-0.5)
```kotlin
// Создание простого плана без RAG
val plan = planner.createSimplePlan(request, uncertainty)
return orchestrator.executePlan(plan)
```

### 3.3 Сложный запрос (> 0.5)
```kotlin
// Полный цикл с RAG и динамической модификацией
val basePlan = planner.createBasePlan(request, uncertainty)
val enrichedPlan = ragEnricher.enrichPlan(basePlan, request, context)
val adaptivePlan = dynamicModifier.enableAdaptivity(enrichedPlan)
return orchestrator.executeAdaptivePlan(adaptivePlan)
```

## 4. Динамическая модификация планов

### 4.1 Условные шаги
```kotlin
data class ConditionalStep(
    val condition: (ExecutionContext, Map<String, StepResult>) -> Boolean,
    val thenStep: PlanStep,
    val elseStep: PlanStep? = null
)
```

### 4.2 Сценарии адаптации
- **Найдены критические проблемы** → добавить детальный анализ
- **Обнаружены ошибки компиляции** → добавить шаги исправления
- **Большой проект** → сегментировать анализ
- **Найдены новые файлы** → расширить план

### 4.3 Версионирование
```kotlin
data class ExecutionPlan(
    // ... поля
    val version: Int = 1,
    val modificationHistory: List<PlanModification> = emptyList()
)
```

## 5. Интеграция с существующими компонентами

### 5.1 EnhancedChatAgent
```kotlin
class EnhancedChatAgent {
    private val complexityAnalyzer = RequestComplexityAnalyzer()
    private val requestPlanner = RequestPlanner()
    private val ragEnricher = RAGPlanEnricher()
    private val dynamicModifier = DynamicPlanModifier()

    override suspend fun ask(request: AgentRequest): AgentResponse {
        val uncertainty = complexityAnalyzer.analyzeUncertainty(request.request, request.context)

        return when (uncertainty.complexity) {
            SIMPLE -> baseChatAgent.ask(request)
            MEDIUM -> executeSimplePlan(request, uncertainty)
            COMPLEX -> executeComplexAdaptivePlan(request, uncertainty)
        }
    }
}
```

### 5.2 Интеграция с MCPFileSystemAgent
- RAG обогащение только на этапе планирования
- Поиск файлов для составления плана
- Контекстное обогащение шагов

### 5.3 Интеграция с EnhancedAgentOrchestrator
- Поддержка адаптивных планов
- Callback'и для динамической модификации
- Прогресс выполнение с учетом изменений

## 6. Оптимизации производительности

### 6.1 Кэширование
```kotlin
interface UncertaintyCache {
    fun getCachedResult(requestHash: String): UncertaintyResult?
    fun cacheResult(requestHash: String, result: UncertaintyResult)
}
```

### 6.2 Быстрая оценка
- Эвристические паттерны для простых запросов
- Лексический анализ без вызова LLM
- Кэширование результатов оценки

### 6.3 Ленивая загрузка
- RAG обогащение только при необходимости
- Динамическое создание условных шагов
- Отложенная инициализация компонентов

## 7. Метрики и мониторинг

### 7.1 Ключевые метрики
- Время обработки простых запросов
- Точность оценки неопределенности
- Количество модификаций планов
- Эффективность RAG обогащения

### 7.2 Логирование
```kotlin
logger.info("Request processed", mapOf(
    "request_id" to requestId,
    "uncertainty_score" to uncertainty.score,
    "complexity_level" to uncertainty.complexity,
    "processing_time" to processingTime,
    "plan_modifications" to modificationsCount
))
```

## 8. Тестирование

### 8.1 Unit тесты
- RequestComplexityAnalyzerTest
- RequestPlannerTest
- RAGPlanEnricherTest
- DynamicPlanModifierTest

### 8.2 Интеграционные тесты
- Полная цепочка обработки для разных уровней сложности
- Динамическая модификация планов в runtime
- Взаимодействие с MCPFileSystemAgent

### 8.3 Performance тесты
- Время обработки простых запросов
- Масштабирование для больших проектов
- Нагрузка при множественных запросах

## 9. Риски и митигации

### 9.1 Риски
- Сложность оценки неопределенности
- Бесконечные циклы модификации планов
- Производительность для сложных запросов

### 9.2 Митигации
- Обширное тестирование и настройка порогов
- Ограничение количества модификаций
- Оптимизации и кэширование

## 10. Критерии приемки

### 10.1 Функциональные требования
- ✅ Простые запросы обрабатываются за < 1 секунды
- ✅ Оценка неопределенности точность > 80%
- ✅ Динамическая модификация работает без ошибок
- ✅ RAG обогащение только на этапе планирования

### 10.2 Нефункциональные требования
- ✅ Обратная совместимость с существующим API
- ✅ Производительность не хуже текущей
- ✅ Покрытие тестами > 80%
- ✅ Документация и примеры использования

---

**Версия:** 1.0
**Дата:** 2025-11-01
**Автор:** Claude AI Assistant