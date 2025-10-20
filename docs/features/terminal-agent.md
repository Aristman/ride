# Terminal Agent - Использование

> **См. также:** [Интеграция Terminal Agent с основным агентом чата](terminal-agent-integration.md)

## Обзор

`TerminalAgent` - это новый агент для выполнения shell-команд в локальном термінале. Он поддерживает:
- Выполнение команд с возвратом stdout, stderr, кода завершения и времени выполнения
- Поддержку разных ОС (Windows и Unix-like системы)
- Настройку рабочей директории
- Потоковое выполнение (streaming)
- Переменные окружения

## Создание агента

```kotlin
import ru.marslab.ide.ride.agent.AgentFactory

// Создание терминального агента
val terminalAgent = AgentFactory.createTerminalAgent()
```

## Использование

### Синхронное выполнение

```kotlin
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters

val context = mockk<ChatContext> {
    every { getRecentHistory(any()) } returns emptyList()
    every { hasSelectedText() } returns false
    every { hasCurrentFile() } returns false
}

// Простая команда
val request = AgentRequest(
    request = "echo Hello World",
    context = context,
    parameters = LLMParameters.DEFAULT
)

val response = terminalAgent.ask(request)
println(response.content)

// Вывод будет содержать:
// 🖥️ **Command Execution Result**
//
// **Command:** `echo Hello World`
// **Exit Code:** 0
// **Execution Time:** 45ms
// **Status:** ✅ Success
//
// **Output:**
// ```
// Hello World
// ```
```

### Смена рабочей директории

```kotlin
// Выполнение команды в другой директории
val request = AgentRequest(
    request = "cd /tmp && ls -la",
    context = context,
    parameters = LLMParameters.DEFAULT
)

val response = terminalAgent.ask(request)
```

### Потоковое выполнение

```kotlin
val request = AgentRequest(
    request = "find . -name '*.kt' | head -10",
    context = context,
    parameters = LLMParameters.DEFAULT
)

val flow = terminalAgent.start(request)
flow?.collect { event ->
    when (event) {
        is AgentEvent.Started -> println("Начало выполнения")
        is AgentEvent.ContentChunk -> println("Прогресс: ${event.content}")
        is AgentEvent.Completed -> println("Результат: ${event.response.content}")
        is AgentEvent.Error -> println("Ошибка: ${event.error}")
    }
}
```

## Формат ответа

Агент возвращает ответ в отформатированном виде:

```
🖥️ **Command Execution Result**

**Command:** `ls -la`
**Exit Code:** 0
**Execution Time:** 67ms
**Status:** ✅ Success

**Output:**
```
total 16
drwxr-xr-x  4 user  staff   128 Oct 18 10:30 .
drwxr-xr-x  6 user  staff   192 Oct 18 10:29 ..
-rw-r--r--  1 user  staff    42 Oct 18 10:30 file.txt
```
```

## Безопасность

- Агент выполняет команды в текущей среде IntelliJ IDEA
- Нет ограничений на выполняемые команды (пользователь должен быть осторожен)
- Поддерживаются только безопасные команды по умолчанию
- Встроенные правила безопасности в `capabilities.responseRules`

## Возможности

```kotlin
val capabilities = terminalAgent.capabilities

println(capabilities.stateful)      // false - не хранит состояние
println(capabilities.streaming)     // true - поддерживает потоковую передачу
println(capabilities.reasoning)     // false - не поддерживает рассуждения
println(capabilities.tools)         // [terminal, shell, command-execution]
```

## Метаданные ответа

```kotlin
val response = terminalAgent.ask(request)

// Метаданные содержат:
println(response.metadata["command"])        // выполненная команда
println(response.metadata["exitCode"])       // код завершения
println(response.metadata["executionTime"])  // время выполнения в ms
println(response.metadata["workingDir"])     // рабочая директория
```

## Интеграция с существующим кодом

```kotlin
// В сервисе чата
class ChatService {
    fun executeTerminalCommand(command: String): AgentResponse {
        val agent = AgentFactory.createTerminalAgent()
        val request = AgentRequest(
            request = command,
            context = getCurrentContext(),
            parameters = LLMParameters.DEFAULT
        )
        return agent.ask(request)
    }
}
```

## Примеры использования

1. **Проверка статуса Git:**
   ```
   git status
   ```

2. **Сборка проекта:**
   ```
   ./gradlew build
   ```

3. **Поиск файлов:**
   ```
   find . -name "*.kt" -type f | wc -l
   ```

4. **Проверка процессов:**
   ```
   ps aux | grep java
   ```

5. **Информация о системе:**
   ```
   uname -a
   ```