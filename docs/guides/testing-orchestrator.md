# Тестирование системы Agent Orchestrator

## Подготовка

1. Убедитесь, что настроен API ключ в Settings → Tools → Ride
2. Откройте панель чата в IDE

## Способ 1: Через ChatService (программно)

```kotlin
import com.intellij.openapi.components.service
import ru.marslab.ide.ride.service.ChatService
import ru.marslab.ide.ride.model.*

val chatService = service<ChatService>()

chatService.sendMessageWithOrchestrator(
    userMessage = "Создай простой калькулятор на Kotlin",
    project = project,
    onStepComplete = { message ->
        println("Шаг выполнен: ${message.content}")
    },
    onError = { error ->
        println("Ошибка: $error")
    }
)
```

## Способ 2: Через UI с помощью ChatPanelExtensions

Добавьте в `ChatPanel.kt` вызов функции из `ChatPanelExtensions.kt`:

```kotlin
// В методе sendMessage() добавьте проверку:
if (text.startsWith("/plan ")) {
    val actualMessage = text.removePrefix("/plan ").trim()
    sendMessageWithOrchestratorMode(
        project = project,
        text = actualMessage,
        onStepComplete = { message ->
            messageDisplayManager.removeLastSystemMessage()
            messageDisplayManager.displayMessage(message)
        },
        onError = { error ->
            messageDisplayManager.removeLastSystemMessage()
            messageDisplayManager.displaySystemMessage("Ошибка: $error")
            setUIEnabled(true)
        },
        onComplete = {
            setUIEnabled(true)
        }
    )
} else {
    // обычная отправка сообщения
}
```

## Примеры запросов для тестирования

### Простая задача
```
/plan создай функцию для вычисления факториала числа
```

Ожидаемый результат:
1. План с 2-3 задачами (определение функции, добавление проверок, тесты)
2. Выполнение каждой задачи
3. Итоговая сводка

### Средняя задача
```
/plan создай REST API для управления списком задач (CRUD операции)
```

Ожидаемый результат:
1. План с 5-7 задачами (модель данных, контроллер, сервис, репозиторий, тесты)
2. Последовательное выполнение
3. Код для каждого компонента

### Сложная задача
```
/plan создай веб-приложение с формой регистрации пользователей
```

Ожидаемый результат:
1. План с 8-10 задачами (HTML, CSS, JavaScript, валидация, backend)
2. Пошаговое выполнение
3. Полный рабочий код

## Что проверять

### 1. Создание плана (PlannerAgent)
- ✅ План создается в структурированном формате
- ✅ Задачи пронумерованы
- ✅ Каждая задача имеет title, description, prompt
- ✅ Промпты самодостаточны

### 2. Выполнение задач (ExecutorAgent)
- ✅ Задачи выполняются последовательно
- ✅ Каждая задача отображается отдельным сообщением
- ✅ Указано имя агента (ExecutorAgent)
- ✅ Результат содержит полезную информацию

### 3. Обработка ошибок
- ✅ Ошибки отображаются в чате
- ✅ Выполнение продолжается после ошибки
- ✅ Итоговая статистика показывает количество ошибок

### 4. Итоговая сводка
- ✅ Отображается после завершения всех задач
- ✅ Показывает статистику (всего/успешно/ошибок)
- ✅ UI разблокируется

## Известные проблемы

1. **ExecutorAgent не видит результаты предыдущих задач**
   - Это ожидаемое поведение по требованиям
   - Каждый промпт должен быть самодостаточным

2. **Задачи выполняются последовательно**
   - Нет параллельного выполнения
   - Для больших планов может занять время

3. **Нет возможности остановить выполнение**
   - После запуска план выполняется до конца
   - Можно только дождаться завершения

## Отладка

### Логи
Проверьте логи IDE для детальной информации:
```
DEBUG: PlannerAgent creating plan
DEBUG: ExecutorAgent executing task 1
DEBUG: Task 1 completed successfully
```

### Метаданные сообщений
Каждое сообщение содержит метаданные:
- `agentName` - имя агента (PlannerAgent/ExecutorAgent)
- `taskId` - ID задачи (для ExecutorAgent)
- `totalTasks` - общее количество задач (для AllComplete)
- `successfulTasks` - количество успешных задач

## Следующие шаги

После успешного тестирования:
1. Добавьте UI элемент для переключения режима (чекбокс/кнопка)
2. Реализуйте параллельное выполнение независимых задач
3. Добавьте возможность редактирования плана перед выполнением
4. Реализуйте передачу контекста между задачами
