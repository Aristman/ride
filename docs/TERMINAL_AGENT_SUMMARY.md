# Terminal Agent - Краткое резюме

## Что это?

**TerminalAgent** - специализированный агент для выполнения shell-команд в локальной среде IntelliJ IDEA. Реализует интерфейс `Agent` и может работать как самостоятельно, так и в составе системы агентов.

## Основные файлы

### Реализация
- **`src/main/kotlin/ru/marslab/ide/ride/agent/impl/TerminalAgent.kt`** - основная реализация агента
- **`src/main/kotlin/ru/marslab/ide/ride/model/terminal/TerminalModels.kt`** - модели данных
- **`src/main/kotlin/ru/marslab/ide/ride/agent/AgentFactory.kt`** - фабричный метод `createTerminalAgent()`

### Тесты
- **`src/test/kotlin/ru/marslab/ide/ride/agent/impl/TerminalAgentTest.kt`** - полное покрытие тестами

### Документация
- **`docs/features/terminal-agent.md`** - руководство по использованию
- **`docs/features/terminal-agent-integration.md`** - подробное описание интеграции
- **`docs/features/README.md`** - обновлен с информацией о Terminal Agent

## Как работает?

### 1. Создание агента
```kotlin
val terminalAgent = AgentFactory.createTerminalAgent()
```

### 2. Выполнение команды
```kotlin
val request = AgentRequest(
    request = "git status",
    context = context,
    parameters = LLMParameters.DEFAULT
)
val response = terminalAgent.ask(request)
```

### 3. Обработка результата
```kotlin
if (response.success) {
    println(response.content)  // Форматированный вывод
    println(response.metadata["exitCode"])  // 0
    println(response.metadata["executionTime"])  // время в ms
} else {
    println(response.error)  // Описание ошибки
}
```

## Возможности

### ✅ Реализовано
- Выполнение команд через `ProcessBuilder`
- Поддержка Windows (cmd) и Unix (sh)
- Захват stdout и stderr
- Измерение времени выполнения
- Коды завершения
- Настройка рабочей директории
- Переменные окружения
- Таймауты (по умолчанию 30 сек)
- Потоковое выполнение через `start()`
- Форматированный вывод результатов

### 🎯 Capabilities
```kotlin
stateful = false        // Не хранит состояние
streaming = true        // Поддерживает потоки
reasoning = false       // Не использует LLM
tools = ["terminal", "shell", "command-execution"]
```

## Интеграция с основным агентом

### Вариант 1: Прямое использование в ChatService
Добавить метод `executeTerminalCommand()` в `ChatService` для выполнения команд по запросу пользователя.

### Вариант 2: Через AgentOrchestrator
Расширить оркестратор для автоматического выбора агента в зависимости от типа задачи (терминальная команда или обычный запрос).

### Вариант 3: Гибридный агент
Создать `HybridChatAgent`, который использует LLM для анализа запроса и решает, нужно ли выполнить команду в терминале.

### Вариант 4: UI команды
Добавить специальные команды в `ChatPanel`:
- `/terminal <команда>` - выполнить команду
- `/exec <команда>` - альтернативный синтаксис

## Примеры команд

```bash
# Git операции
git status
git log -n 5
git diff

# Сборка проекта
./gradlew build
./gradlew test

# Поиск файлов
find . -name "*.kt" -type f
grep -r "TerminalAgent" src/

# Информация о системе
pwd
ls -la
echo $PATH
```

## Безопасность

### ⚠️ Текущие ограничения
- Команды выполняются с правами IDE
- Нет песочницы или изоляции
- Нет валидации команд
- Полный доступ к файловой системе

### 🔒 Рекомендации
1. Добавить белый список безопасных команд
2. Добавить черный список опасных команд (rm, del, format и т.д.)
3. Запрашивать подтверждение для потенциально опасных операций
4. Ограничить рабочие директории проектом
5. Логировать все выполненные команды

## Метаданные ответа

```kotlin
metadata = mapOf(
    "command" to "git status",           // Выполненная команда
    "exitCode" to 0,                     // Код завершения
    "executionTime" to 123L,             // Время в ms
    "workingDir" to "/path/to/project"   // Рабочая директория
)
```

## Потоковое выполнение

```kotlin
val flow = terminalAgent.start(request)
flow?.collect { event ->
    when (event) {
        is AgentEvent.Started -> showProgress()
        is AgentEvent.ContentChunk -> updateProgress(event.content)
        is AgentEvent.Completed -> hideProgress()
        is AgentEvent.Error -> showError(event.error)
    }
}
```

## Тестирование

Полное покрытие тестами в `TerminalAgentTest.kt`:
- ✅ Успешное выполнение команд
- ✅ Обработка ошибок
- ✅ Смена рабочей директории
- ✅ Метаданные ответа
- ✅ Потоковое выполнение
- ✅ Невалидные команды
- ✅ Пустые команды
- ✅ Вывод в stderr

## Архитектура

```
TerminalAgent
├── ask(request) -> AgentResponse
│   ├── parseCommandFromRequest()
│   ├── executeCommand()
│   └── formatCommandResult()
│
├── start(request) -> Flow<AgentEvent>
│   └── executeCommandStreaming()
│
└── capabilities: AgentCapabilities
```

### Модели данных

```kotlin
TerminalCommand(
    command: String,
    workingDir: String? = null,
    timeout: Long = 30000L,
    environmentVariables: Map<String, String> = emptyMap()
)

TerminalCommandResult(
    command: String,
    exitCode: Int,
    stdout: String,
    stderr: String,
    executionTime: Long,
    success: Boolean
)
```

## Следующие шаги

### Для интеграции
1. Выбрать подход интеграции (см. `terminal-agent-integration.md`)
2. Добавить метод в `ChatService` или расширить `AgentOrchestrator`
3. Обновить UI для поддержки терминальных команд
4. Добавить индикаторы выполнения команд

### Для безопасности
1. Реализовать валидацию команд
2. Добавить подтверждение опасных операций
3. Ограничить доступ к файловой системе
4. Добавить логирование

### Для расширения
1. Поддержка скриптов (несколько команд)
2. Макросы часто используемых команд
3. История выполненных команд
4. Автодополнение команд

## Ссылки

- [Руководство по использованию](features/terminal-agent.md)
- [Подробная интеграция](features/terminal-agent-integration.md)
- [Список фич](features/README.md)
- [Архитектура проекта](architecture/overview.md)

---

**Дата создания:** 2025-10-18  
**Версия:** 1.0.0  
**Статус:** ✅ Активна
