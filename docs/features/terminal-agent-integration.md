# Terminal Agent - Интеграция с основным агентом чата

## Обзор

`TerminalAgent` - это специализированный агент для выполнения shell-команд в локальной среде. Он реализует интерфейс `Agent` и может быть интегрирован с основным агентом чата для расширения функциональности выполнения команд.

## Архитектура

### Компоненты

1. **TerminalAgent** (`ru.marslab.ide.ride.agent.impl.TerminalAgent`)
   - Реализует интерфейс `Agent`
   - Выполняет команды через `ProcessBuilder`
   - Поддерживает Windows (cmd) и Unix-like системы (sh)
   - Возвращает форматированные результаты

2. **Модели данных** (`ru.marslab.ide.ride.model.terminal`)
   - `TerminalCommand` - команда для выполнения
   - `TerminalCommandResult` - результат выполнения
   - `CommandType` - типы команд (SINGLE, PIPELINE, SCRIPT)
   - `ExecutionMode` - режимы выполнения (SYNC, ASYNC)

3. **AgentFactory** (`ru.marslab.ide.ride.agent.AgentFactory`)
   - Фабричный метод `createTerminalAgent()` для создания экземпляра

### Возможности (Capabilities)

```kotlin
AgentCapabilities(
    stateful = false,           // Не хранит состояние между запросами
    streaming = true,           // Поддерживает потоковую передачу
    reasoning = false,          // Не использует LLM для рассуждений
    tools = setOf(
        "terminal",
        "shell", 
        "command-execution"
    ),
    systemPrompt = "Агент для выполнения команд в локальном терминале",
    responseRules = listOf(
        "Выполнять только безопасные команды",
        "Возвращать полный вывод команды включая stdout и stderr",
        "Форматировать результат в удобочитаемом виде"
    )
)
```

## Способы интеграции

### 1. Прямое использование в ChatService

Добавить метод для выполнения терминальных команд:

```kotlin
class ChatService {
    /**
     * Выполняет терминальную команду через TerminalAgent
     */
    fun executeTerminalCommand(
        command: String,
        project: Project,
        onResponse: (Message) -> Unit,
        onError: (String) -> Unit
    ) {
        if (command.isBlank()) {
            onError("Команда не может быть пустой")
            return
        }

        scope.launch {
            try {
                // Создаем терминальный агент
                val terminalAgent = AgentFactory.createTerminalAgent()
                
                // Формируем контекст
                val context = ChatContext(
                    project = project,
                    history = getCurrentHistory().getMessages()
                )
                
                // Создаем запрос
                val request = AgentRequest(
                    request = command,
                    context = context,
                    parameters = LLMParameters.DEFAULT
                )
                
                // Выполняем команду
                val startTime = System.currentTimeMillis()
                val response = terminalAgent.ask(request)
                val responseTime = System.currentTimeMillis() - startTime
                
                withContext(Dispatchers.EDT) {
                    if (response.success) {
                        val metadata = response.metadata + mapOf(
                            "agentType" to "terminal",
                            "responseTimeMs" to responseTime
                        )
                        
                        val message = Message(
                            content = response.content,
                            role = MessageRole.ASSISTANT,
                            metadata = metadata
                        )
                        
                        getCurrentHistory().addMessage(message)
                        onResponse(message)
                    } else {
                        onError(response.error ?: "Ошибка выполнения команды")
                    }
                }
                
                // Освобождаем ресурсы
                terminalAgent.dispose()
                
            } catch (e: Exception) {
                logger.error("Error executing terminal command", e)
                withContext(Dispatchers.EDT) {
                    onError("Ошибка: ${e.message}")
                }
            }
        }
    }
}
```

### 2. Интеграция через AgentOrchestrator

Расширить оркестратор для поддержки терминальных команд:

```kotlin
class AgentOrchestrator(
    private val planerLlmProvider: LLMProvider,
    private val executorLlmProvider: LLMProvider
) {
    private val plannerAgent = PlannerAgent(planerLlmProvider)
    private val executorAgent = ExecutorAgent(executorLlmProvider)
    private val terminalAgent = TerminalAgent()  // Добавляем терминальный агент
    
    /**
     * Определяет, является ли задача терминальной командой
     */
    private fun isTerminalTask(task: Task): Boolean {
        // Проверяем маркеры терминальных команд
        val terminalKeywords = listOf(
            "выполни команду",
            "запусти",
            "gradle",
            "git",
            "npm",
            "mvn"
        )
        
        return terminalKeywords.any { 
            task.prompt.lowercase().contains(it) 
        }
    }
    
    suspend fun process(
        request: AgentRequest,
        onStepComplete: suspend (OrchestratorStep) -> Unit
    ): AgentResponse {
        // ... планирование ...
        
        for (task in plan.tasks) {
            // Выбираем агента в зависимости от типа задачи
            val agent = if (isTerminalTask(task)) {
                logger.info("Using TerminalAgent for task ${task.id}")
                terminalAgent
            } else {
                logger.info("Using ExecutorAgent for task ${task.id}")
                executorAgent
            }
            
            val executorRequest = AgentRequest(
                request = task.prompt,
                context = request.context,
                parameters = request.parameters
            )
            
            val taskStartTime = System.currentTimeMillis()
            val executorResponse = agent.ask(executorRequest)
            val taskResponseTime = System.currentTimeMillis() - taskStartTime
            
            // ... обработка результата ...
        }
        
        // ... финализация ...
    }
    
    fun dispose() {
        plannerAgent.dispose()
        executorAgent.dispose()
        terminalAgent.dispose()  // Не забываем освободить ресурсы
    }
}
```

### 3. Гибридный подход с ChatAgent

Создать специальный агент, который комбинирует LLM и терминал:

```kotlin
class HybridChatAgent(
    private val llmProvider: LLMProvider
) : Agent {
    private val chatAgent = ChatAgent(llmProvider)
    private val terminalAgent = TerminalAgent()
    
    override suspend fun ask(req: AgentRequest): AgentResponse {
        // Анализируем запрос через LLM
        val analysisPrompt = """
            Проанализируй запрос пользователя и определи, требуется ли выполнение команды в терминале.
            
            Запрос: ${req.request}
            
            Ответь в формате JSON:
            {
                "needsTerminal": true/false,
                "command": "команда для выполнения" или null,
                "explanation": "объяснение"
            }
        """.trimIndent()
        
        val analysisRequest = req.copy(request = analysisPrompt)
        val analysisResponse = chatAgent.ask(analysisRequest)
        
        // Парсим ответ LLM
        val needsTerminal = parseNeedsTerminal(analysisResponse.content)
        
        return if (needsTerminal) {
            // Выполняем через терминальный агент
            val command = extractCommand(analysisResponse.content)
            val terminalRequest = req.copy(request = command)
            val terminalResponse = terminalAgent.ask(terminalRequest)
            
            // Можем дополнительно попросить LLM интерпретировать результат
            val interpretationPrompt = """
                Команда: $command
                Результат:
                ${terminalResponse.content}
                
                Объясни результат пользователю простым языком.
            """.trimIndent()
            
            val interpretationRequest = req.copy(request = interpretationPrompt)
            chatAgent.ask(interpretationRequest)
        } else {
            // Обычный ответ через LLM
            chatAgent.ask(req)
        }
    }
    
    override val capabilities: AgentCapabilities
        get() = AgentCapabilities(
            stateful = true,
            streaming = true,
            reasoning = true,
            tools = setOf("chat", "terminal", "hybrid"),
            systemPrompt = "Гибридный агент с поддержкой LLM и терминала"
        )
    
    // ... остальные методы ...
}
```

### 4. Интеграция через UI

Добавить специальную команду в `ChatPanel`:

```kotlin
class ChatPanel(private val project: Project) : JPanel() {
    
    private fun handleUserInput(text: String) {
        when {
            text.startsWith("/terminal ") -> {
                // Специальная команда для терминала
                val command = text.removePrefix("/terminal ").trim()
                executeTerminalCommand(command)
            }
            text.startsWith("/exec ") -> {
                // Альтернативный синтаксис
                val command = text.removePrefix("/exec ").trim()
                executeTerminalCommand(command)
            }
            else -> {
                // Обычное сообщение в чат
                sendNormalMessage(text)
            }
        }
    }
    
    private fun executeTerminalCommand(command: String) {
        val chatService = service<ChatService>()
        
        // Добавляем сообщение пользователя
        val userMessage = Message(
            content = "🖥️ Выполнить команду: `$command`",
            role = MessageRole.USER
        )
        addMessageToUI(userMessage)
        
        // Выполняем команду
        chatService.executeTerminalCommand(
            command = command,
            project = project,
            onResponse = { message ->
                addMessageToUI(message)
            },
            onError = { error ->
                val errorMessage = Message(
                    content = "❌ Ошибка: $error",
                    role = MessageRole.SYSTEM
                )
                addMessageToUI(errorMessage)
            }
        )
    }
}
```

## Примеры использования

### Пример 1: Проверка статуса Git

```kotlin
// Пользователь вводит: /terminal git status
// TerminalAgent выполняет команду и возвращает:

🖥️ **Command Execution Result**

**Command:** `git status`
**Exit Code:** 0
**Execution Time:** 45ms
**Status:** ✅ Success

**Output:**
```
On branch main
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean
```
```

### Пример 2: Сборка проекта

```kotlin
// Пользователь вводит: /terminal ./gradlew build
// TerminalAgent выполняет сборку и возвращает результат
```

### Пример 3: Поиск файлов

```kotlin
// Пользователь вводит: /terminal find . -name "*.kt" -type f | wc -l
// TerminalAgent возвращает количество Kotlin файлов
```

## Безопасность

### Текущие ограничения

1. **Нет песочницы** - команды выполняются в реальной среде
2. **Полные права** - команды имеют те же права, что и IDE
3. **Нет валидации** - любая команда может быть выполнена

### Рекомендации по безопасности

1. **Добавить белый список команд**:
```kotlin
private val SAFE_COMMANDS = setOf(
    "git", "ls", "pwd", "echo", "cat", "grep",
    "find", "wc", "head", "tail", "gradlew"
)

private fun validateCommand(command: String): Boolean {
    val firstWord = command.trim().split(" ").firstOrNull() ?: return false
    return SAFE_COMMANDS.contains(firstWord)
}
```

2. **Добавить черный список опасных команд**:
```kotlin
private val DANGEROUS_COMMANDS = setOf(
    "rm", "del", "format", "dd", "mkfs",
    "shutdown", "reboot", "kill", "killall"
)

private fun isDangerous(command: String): Boolean {
    val firstWord = command.trim().split(" ").firstOrNull() ?: return false
    return DANGEROUS_COMMANDS.contains(firstWord)
}
```

3. **Запрашивать подтверждение для потенциально опасных команд**:
```kotlin
suspend fun executeWithConfirmation(
    command: String,
    onConfirm: suspend () -> Unit
): AgentResponse {
    if (isDangerous(command)) {
        // Показать диалог подтверждения
        val confirmed = showConfirmationDialog(
            "Выполнить потенциально опасную команду?\n$command"
        )
        if (!confirmed) {
            return AgentResponse.error("Команда отменена пользователем")
        }
    }
    
    return executeCommand(command)
}
```

4. **Ограничить рабочие директории**:
```kotlin
private fun validateWorkingDir(dir: String): Boolean {
    val projectPath = project.basePath ?: return false
    val dirFile = File(dir)
    return dirFile.canonicalPath.startsWith(projectPath)
}
```

## Метаданные и статистика

TerminalAgent возвращает следующие метаданные:

```kotlin
metadata = mapOf(
    "command" to "выполненная команда",
    "exitCode" to 0,                    // Код завершения
    "executionTime" to 123L,            // Время выполнения в ms
    "workingDir" to "/path/to/dir",     // Рабочая директория
    "agentType" to "terminal"           // Тип агента
)
```

Эти метаданные можно использовать для:
- Отображения статистики в UI
- Логирования
- Анализа производительности
- Отладки

## Потоковое выполнение

TerminalAgent поддерживает потоковое выполнение через метод `start()`:

```kotlin
val flow = terminalAgent.start(request)
flow?.collect { event ->
    when (event) {
        is AgentEvent.Started -> {
            // Команда начала выполняться
            showProgressIndicator()
        }
        is AgentEvent.ContentChunk -> {
            // Промежуточный прогресс
            updateProgress(event.content)
        }
        is AgentEvent.Completed -> {
            // Команда завершена
            hideProgressIndicator()
            displayResult(event.response)
        }
        is AgentEvent.Error -> {
            // Ошибка выполнения
            hideProgressIndicator()
            showError(event.error)
        }
    }
}
```

## Расширенные возможности

### Поддержка конвейеров команд

```kotlin
// Уже поддерживается через shell
val command = "find . -name '*.kt' | grep 'Agent' | wc -l"
```

### Переменные окружения

```kotlin
val command = TerminalCommand(
    command = "echo $MY_VAR",
    environmentVariables = mapOf("MY_VAR" to "Hello")
)
```

### Таймауты

```kotlin
val command = TerminalCommand(
    command = "long-running-task",
    timeout = 60000L  // 60 секунд
)
```

## Тестирование

Примеры тестов из `TerminalAgentTest.kt`:

```kotlin
@Test
fun `executes simple echo command successfully`() = runTest {
    val request = AgentRequest(
        request = "echo Hello World",
        context = mockContext,
        parameters = LLMParameters.DEFAULT
    )
    
    val response = agent.ask(request)
    
    assertTrue(response.success)
    assertTrue(response.content.contains("Hello World"))
    assertTrue(response.content.contains("✅ Success"))
}

@Test
fun `handles failed command properly`() = runTest {
    val request = AgentRequest(
        request = "exit 1",
        context = mockContext,
        parameters = LLMParameters.DEFAULT
    )
    
    val response = agent.ask(request)
    
    assertFalse(response.success)
    assertTrue(response.content.contains("❌ Failed"))
}
```

## Заключение

TerminalAgent предоставляет мощный инструмент для выполнения локальных команд. Основные преимущества интеграции:

1. **Расширение возможностей** - чат может выполнять реальные действия
2. **Автоматизация** - можно создавать цепочки команд
3. **Гибкость** - поддержка разных ОС и типов команд
4. **Простота** - единый интерфейс `Agent` для всех типов агентов

Рекомендуется начать с простой интеграции через `ChatService` и постепенно добавлять более сложные сценарии через `AgentOrchestrator` или гибридный подход.
