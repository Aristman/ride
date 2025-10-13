# Система двух агентов (Agent Orchestrator)

## Описание

Реализована система координации двух агентов:
- **PlannerAgent** - создает структурированный план задач
- **ExecutorAgent** - выполняет каждую задачу из плана

## Архитектура

### Компоненты

1. **Модели данных** (`model/`):
   - `TaskPlan` - план с задачами
   - `TaskItem` - отдельная задача
   - `ExecutionResult` - результат выполнения
   - `TaskPlanSchema` - схемы для парсинга планов (JSON/XML)

2. **Агенты** (`agent/impl/`):
   - `PlannerAgent` - анализирует запрос и создает план
   - `ExecutorAgent` - выполняет задачи независимо

3. **Оркестратор** (`agent/`):
   - `AgentOrchestrator` - координирует работу агентов
   - `OrchestratorStep` - типы шагов выполнения

### Поток работы

```
Пользователь → ChatService.sendMessageWithOrchestrator()
                    ↓
              AgentOrchestrator.process()
                    ↓
              PlannerAgent.ask() → создает TaskPlan
                    ↓
              Для каждой задачи:
                ExecutorAgent.ask() → выполняет задачу
                    ↓
                onStepComplete() → отправка в чат
```

## Использование

### В коде

```kotlin
// Создание оркестратора
val orchestrator = AgentFactory.createAgentOrchestrator()

// Запрос
val request = AgentRequest(
    request = "Создай простое веб-приложение",
    context = chatContext,
    parameters = llmParameters
)

// Обработка с callback для каждого шага
orchestrator.process(request) { step ->
    when (step) {
        is OrchestratorStep.PlanningComplete -> {
            // План создан
            println(step.content)
        }
        is OrchestratorStep.TaskComplete -> {
            // Задача выполнена
            println("Задача ${step.taskId}: ${step.taskTitle}")
            println(step.content)
        }
        is OrchestratorStep.AllComplete -> {
            // Все задачи завершены
            println("Выполнено: ${step.successfulTasks}/${step.totalTasks}")
        }
        is OrchestratorStep.Error -> {
            // Ошибка
            println("Ошибка: ${step.error}")
        }
    }
}
```

### Через ChatService

```kotlin
chatService.sendMessageWithOrchestrator(
    userMessage = "Создай REST API для управления пользователями",
    project = project,
    onStepComplete = { message ->
        // Каждый шаг отображается как отдельное сообщение
        displayMessage(message)
    },
    onError = { error ->
        showError(error)
    }
)
```

### Через UI (команда /plan)

В поле ввода чата используйте префикс `/plan`:

```
/plan создай простое веб-приложение с формой регистрации
```

Система автоматически:
1. Создаст план задач
2. Выполнит каждую задачу
3. Отобразит результаты каждого шага в чате

## Особенности реализации

### PlannerAgent

- **Системный промпт**: инструктирует создавать структурированные планы
- **Формат ответа**: JSON или XML с четкой схемой
- **Валидация**: проверяет корректность плана перед выполнением

Пример плана (JSON):
```json
{
  "description": "Создание веб-приложения",
  "tasks": [
    {
      "id": 1,
      "title": "Создать HTML структуру",
      "description": "Базовая HTML разметка",
      "prompt": "Создай HTML файл с формой регистрации..."
    },
    {
      "id": 2,
      "title": "Добавить стили CSS",
      "description": "Стилизация формы",
      "prompt": "Создай CSS файл для стилизации формы..."
    }
  ]
}
```

### ExecutorAgent

- **Изоляция**: НЕ видит другие задачи и результаты
- **Самодостаточность**: каждый промпт содержит весь необходимый контекст
- **Простота**: фокусируется только на выполнении текущей задачи

### AgentOrchestrator

- **Последовательное выполнение**: задачи выполняются по порядку
- **Обработка ошибок**: продолжает выполнение при ошибке в задаче
- **Прозрачность**: каждый шаг отправляется через callback

## Интеграция в UI

### ChatService

Метод `sendMessageWithOrchestrator()` интегрирован в `ChatService` и работает параллельно с обычным `sendMessage()`.

### Отображение в чате

Каждый шаг отображается как отдельное сообщение:

1. **PlanningComplete**: 
   ```
   📋 План задач создан
   
   Цель: Создание веб-приложения
   
   Задачи (3):
   1. Создать HTML структуру
   2. Добавить стили CSS
   3. Реализовать JavaScript логику
   ```

2. **TaskComplete**:
   ```
   ✅ Задача 1: Создать HTML структуру
   
   [Результат выполнения задачи]
   ```

3. **AllComplete**:
   ```
   ✅ Выполнение завершено
   
   Статистика:
   - Всего задач: 3
   - Успешно выполнено: 3
   ```

## Расширение функциональности

### Добавление нового типа агента

1. Создайте класс, реализующий `Agent`
2. Добавьте метод в `AgentFactory`
3. Используйте в `AgentOrchestrator`

### Модификация схемы плана

Измените `TaskPlanSchema` для добавления новых полей:

```kotlin
data class TaskItem(
    val id: Int,
    val title: String,
    val description: String,
    val prompt: String,
    val priority: Int = 0,  // новое поле
    val dependencies: List<Int> = emptyList()  // новое поле
)
```

### Параллельное выполнение задач

Модифицируйте `AgentOrchestrator.process()` для использования `async`:

```kotlin
val results = plan.tasks.map { task ->
    async {
        executorAgent.ask(createRequest(task))
    }
}.awaitAll()
```

## Тестирование

Для тестирования системы:

1. Настройте API ключ в Settings → Tools → Ride
2. Откройте чат
3. Введите команду: `/plan создай простой калькулятор`
4. Наблюдайте за выполнением каждого шага

## Известные ограничения

1. ExecutorAgent не видит результаты предыдущих задач
2. Задачи выполняются последовательно (не параллельно)
3. Нет возможности редактировать план перед выполнением
4. Нет механизма отмены выполнения

## Будущие улучшения

- [ ] Параллельное выполнение независимых задач
- [ ] Передача контекста между задачами
- [ ] Интерактивное редактирование плана
- [ ] Возможность остановки выполнения
- [ ] Сохранение и повторное использование планов
- [ ] Визуализация прогресса выполнения
