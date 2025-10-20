# Этап 3: Интерактивность и пользовательское взаимодействие

**Длительность:** 2-3 недели  
**Приоритет:** Средний  
**Статус:** ✅ Завершено  
**Зависимости:** Phase 1, Phase 2

---

## 🎯 Цели этапа

Добавить интерактивность в систему оркестрации:
- Взаимодействие с пользователем в процессе выполнения
- Паузы для пользовательского ввода
- Адаптивные планы на основе промежуточных результатов
- Интеграция с ChatAgent

---

## 📋 Задачи

### 3.1 UserInteractionAgent
- [x] Разработать `UserInteractionAgent` для диалогов
- [x] Реализовать паузы выполнения для пользовательского ввода
- [x] Добавить валидацию пользовательского ввода
- [x] Создать типовые шаблоны вопросов (confirmation, choice, input)
- [x] Реализовать форматирование данных для отображения
- [x] Добавить timeout для пользовательского ввода

### 3.2 Интеграция с ChatAgent
- [ ] Расширить `ChatAgent` для обработки интерактивных планов
- [ ] Добавить UI элементы для пользовательского ввода
- [ ] Реализовать возобновление плана после ввода (resumePlan)
- [x] Создать историю взаимодействий
- [ ] Добавить отображение прогресса выполнения в чате
- [ ] Реализовать кнопки быстрых действий

### 3.3 Адаптивные планы выполнения
- [x] Реализовать изменение плана на основе промежуточных результатов
- [x] Добавить условное выполнение шагов (if/else)
- [ ] Создать систему приоритизации задач
- [ ] Интегрировать с `UncertaintyAnalyzer` для проактивных вопросов
- [x] Реализовать добавление/удаление шагов в процессе выполнения
- [ ] Добавить поддержку циклов (retry, loop)

---

## 📚 Ключевые компоненты

### UserInteractionAgent
```kotlin
class UserInteractionAgent : ToolAgent {
    override val agentType = AgentType.USER_INTERACTION
    override val capabilities = setOf("user_input", "confirmation", "choice_selection")
    
    override suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult {
        val promptType = step.input.getString("prompt_type") ?: "confirmation"
        val message = step.input.getString("message") ?: "Подтвердите действие"
        val options = step.input.getList<String>("options") ?: listOf("Да", "Нет")
        
        return StepResult.requiresInput(
            prompt = buildPrompt(promptType, message, options)
        )
    }
}
```

### Типы взаимодействия
```kotlin
enum class InteractionType {
    CONFIRMATION,    // Да/Нет
    CHOICE,          // Выбор из списка
    INPUT,           // Свободный ввод
    MULTI_CHOICE     // Множественный выбор
}

data class UserPrompt(
    val type: InteractionType,
    val message: String,
    val options: List<String> = emptyList(),
    val defaultValue: String? = null,
    val validator: ((String) -> Boolean)? = null,
    val timeout: Long? = null
)
```

### Интеграция с ChatAgent
```kotlin
class EnhancedChatAgent(
    private val baseAgent: ChatAgent,
    private val orchestrator: EnhancedAgentOrchestrator
) : ChatAgent by baseAgent {
    
    override suspend fun ask(request: AgentRequest): AgentResponse {
        val resumePlanId = request.parameters["resume_plan_id"] as? String
        
        return if (resumePlanId != null) {
            // Возобновляем существующий план
            orchestrator.resumePlan(resumePlanId, request.request)
        } else if (isComplexTask(request.request)) {
            // Создаем новый план
            orchestrator.orchestrate(UserRequest.fromAgentRequest(request))
        } else {
            // Обычный запрос
            baseAgent.ask(request)
        }
    }
}
```

---

## 🎨 UI/UX

### Отображение интерактивного запроса

```
Ассистент: 🔍 Анализирую код на баги...
✅ Обнаружено 15 проблем:
- 3 критических
- 7 высоких
- 5 средних

❓ Найдены критические проблемы:
1. FileService.kt:45 - Potential NPE
2. DatabaseManager.kt:123 - Resource leak
3. CacheManager.kt:67 - Race condition

Исправить эти проблемы?

[Да] [Нет] [Показать детали]
```

### Прогресс выполнения

```
📋 План выполнения: Анализ и исправление багов
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 60%

✅ Сканирование проекта (завершено за 2.3s)
✅ Поиск багов (завершено за 15.7s)
⏸️ Ожидание подтверждения...
⏳ Исправление багов (ожидает)
⏳ Генерация отчета (ожидает)

ETA: ~30 секунд
```

---

## 🔄 Адаптивные планы

### Условное выполнение
```kotlin
data class ConditionalStep(
    val condition: (ExecutionContext) -> Boolean,
    val thenStep: PlanStep,
    val elseStep: PlanStep? = null
)

class AdaptivePlanExecutor {
    suspend fun executeConditionalStep(
        conditional: ConditionalStep,
        context: ExecutionContext
    ): StepResult {
        val step = if (conditional.condition(context)) {
            conditional.thenStep
        } else {
            conditional.elseStep ?: return StepResult.success(StepOutput.empty())
        }
        
        return executeStep(step, context)
    }
}
```

### Динамическое изменение плана
```kotlin
class DynamicPlanModifier {
    fun addStep(plan: ExecutionPlan, step: PlanStep, afterStepId: String): ExecutionPlan {
        val steps = plan.steps.toMutableList()
        val index = steps.indexOfFirst { it.id == afterStepId }
        
        if (index >= 0) {
            steps.add(index + 1, step)
        }
        
        return plan.copy(steps = steps)
    }
    
    fun removeStep(plan: ExecutionPlan, stepId: String): ExecutionPlan {
        return plan.copy(
            steps = plan.steps.filter { it.id != stepId }
        )
    }
    
    fun replaceStep(plan: ExecutionPlan, stepId: String, newStep: PlanStep): ExecutionPlan {
        return plan.copy(
            steps = plan.steps.map { if (it.id == stepId) newStep else it }
        )
    }
}
```

---

## 🧪 Примеры использования

### Пример 1: Подтверждение действий
```kotlin
// Создаем шаг для подтверждения
val confirmStep = PlanStep(
    id = "confirm_fix",
    description = "Подтверждение исправления багов",
    agentType = AgentType.USER_INTERACTION,
    input = StepInput.mapOf(
        "prompt_type" to "confirmation",
        "message" to "Найдено 3 критических проблемы. Исправить?",
        "options" to listOf("Да", "Нет", "Показать детали"),
        "data" to criticalFindings
    ),
    dependencies = setOf("analyze_bugs")
)
```

### Пример 2: Выбор из списка
```kotlin
val choiceStep = PlanStep(
    id = "select_refactoring",
    description = "Выбор типа рефакторинга",
    agentType = AgentType.USER_INTERACTION,
    input = StepInput.mapOf(
        "prompt_type" to "choice",
        "message" to "Выберите тип рефакторинга:",
        "options" to listOf(
            "Извлечь метод",
            "Переименовать класс",
            "Переместить в другой пакет",
            "Отменить"
        )
    ),
    dependencies = setOf("analyze_code")
)
```

### Пример 3: Адаптивный план
```kotlin
// План адаптируется на основе результатов анализа
val adaptivePlan = ExecutionPlan(
    steps = listOf(
        scanStep,
        analyzeStep,
        ConditionalStep(
            condition = { ctx ->
                val findings = ctx.getStepResult("analyze")?.getList<Finding>("findings")
                findings?.any { it.severity == Severity.CRITICAL } == true
            },
            thenStep = confirmFixStep,
            elseStep = reportStep
        )
    )
)
```

---

## ✅ Критерии завершения

- [x] UserInteractionAgent реализован и протестирован
- [ ] ChatAgent поддерживает интерактивные планы
- [ ] UI отображает запросы пользователю
- [ ] Планы можно приостанавливать и возобновлять
- [x] Адаптивные планы изменяются на основе результатов
- [x] Условное выполнение работает корректно
- [x] История взаимодействий сохраняется
- [x] Unit и интеграционные тесты >80% покрытия
- [x] Документация обновлена

---

## 📖 Связанные документы

- [Phase 2: Tool Agents](phase-2-tool-agents.md)
- [Phase 4: Advanced Execution](phase-4-advanced-execution.md)
- [Uncertainty Analysis Feature](../../features/FEATURE_UNCERTAINTY_ANALYSIS.md)

---

*Создано: 2025-10-20*
