# Улучшения Terminal Agent

## Версия 1.0.1 - 2025-10-18

### ✨ Улучшения

#### 1. Показ результата даже при ошибке команды

**Проблема**: При ошибке выполнения команды (например, `git status` с exit code 128) пользователь не видел вывод команды, только текст ошибки.

**Решение**: Теперь результат выполнения команды всегда отображается в чате, даже если команда завершилась с ошибкой. Это позволяет пользователю видеть stderr и понимать причину ошибки.

**Изменения в `ChatService.kt`**:
```kotlin
// Всегда показываем результат выполнения команды (даже при ошибке)
val metadata = response.metadata + mapOf(
    "agentType" to "terminal",
    "responseTimeMs" to responseTime,
    "isFinal" to true,
    "uncertainty" to 0.0,
    "commandSuccess" to response.success  // Новое поле
)

val message = Message(
    content = response.content,  // Всегда показываем форматированный вывод
    role = MessageRole.ASSISTANT,
    metadata = metadata
)

getCurrentHistory().addMessage(message)
onResponse(message)

// Логируем ошибку, но не прерываем показ результата
if (!response.success) {
    logger.warn("Terminal command failed: ${response.error}")
}
```

#### 2. Автоматическая установка рабочей директории проекта

**Проблема**: Команды выполнялись в директории по умолчанию (обычно домашняя директория пользователя), что могло вызывать ошибки для команд, требующих контекста проекта (например, `git status`).

**Решение**: Теперь `TerminalAgent` автоматически использует базовую директорию проекта (`project.basePath`) как рабочую директорию для выполнения команд.

**Изменения в `TerminalAgent.kt`**:
```kotlin
override suspend fun ask(req: AgentRequest): AgentResponse {
    return withContext(Dispatchers.IO) {
        try {
            // Получаем рабочую директорию из проекта, если доступна
            val projectBasePath = req.context.project?.basePath
            val command = parseCommandFromRequest(req.request, projectBasePath)
            // ...
        }
    }
}

private fun parseCommandFromRequest(
    request: String, 
    projectBasePath: String? = null
): TerminalCommand {
    val trimmed = request.trim()
    
    val workingDir = if (trimmed.startsWith("cd ")) {
        // Явное указание директории
        // ...
    } else {
        // Используем базовую директорию проекта, если доступна
        projectBasePath
    }
    
    return TerminalCommand(
        command = command,
        workingDir = workingDir
    )
}
```

### 📊 Примеры

#### До улучшений

```
Пользователь: /exec git status
Ассистент: ❌ Ошибка: Command failed with exit code 128
```

Пользователь не видел, что именно пошло не так.

#### После улучшений

```
Пользователь: /exec git status
Ассистент:
🖥️ **Command Execution Result**

**Command:** `git status`
**Exit Code:** 128
**Execution Time:** 45ms
**Status:** ❌ Failed

**Errors:**
```
fatal: not a git repository (or any of the parent directories): .git
```
```

Теперь пользователь видит полный вывод и понимает причину ошибки.

### 🔧 Технические детали

#### Новые метаданные

Добавлено поле `commandSuccess` в метаданные ответа:
```kotlin
metadata = mapOf(
    "agentType" to "terminal",
    "command" to "git status",
    "exitCode" to 128,
    "executionTime" to 45L,
    "workingDir" to "/path/to/project",
    "responseTimeMs" to 123L,
    "isFinal" to true,
    "uncertainty" to 0.0,
    "commandSuccess" to false  // Новое поле
)
```

Это позволяет UI различать успешные и неуспешные команды для дополнительного форматирования.

#### Логика определения рабочей директории

1. Если команда начинается с `cd <dir> &&` - используется указанная директория
2. Иначе используется `project.basePath` из контекста
3. Если проект недоступен - используется `System.getProperty("user.dir")`

### 🎯 Преимущества

1. **Лучшая диагностика**: Пользователь всегда видит полный вывод команды
2. **Правильный контекст**: Команды выполняются в директории проекта
3. **Меньше ошибок**: Git и другие команды работают корректно в контексте проекта
4. **Прозрачность**: Понятно, что пошло не так при ошибке

### 🐛 Исправленные проблемы

- ✅ Git команды теперь работают корректно в проектах с git
- ✅ Пользователь видит stderr при ошибках
- ✅ Понятно, в какой директории выполнялась команда
- ✅ Логи содержат полную информацию об ошибках

### 📝 Совместимость

- Обратная совместимость сохранена
- Старые команды продолжают работать
- Новые метаданные опциональны

---

**Дата**: 2025-10-18  
**Версия**: 1.0.1
