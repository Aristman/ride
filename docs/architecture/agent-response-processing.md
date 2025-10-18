# Обработка ответов агентов в чате

## Текущая архитектура

### Поток обработки сообщения

```
User Input → ChatService → Agent → AgentResponse → Message → MessageDisplayManager → UI
```

### 1. ChatService — координация запросов

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/service/ChatService.kt`

#### Основной метод: `sendMessage()`

```kotlin
fun sendMessage(
    userMessage: String,
    project: Project,
    onResponse: (Message) -> Unit,
    onError: (String) -> Unit
)
```

**Что делает:**
1. Создаёт `Message` с ролью `USER` и добавляет в историю
2. Формирует `ChatContext` с историей и проектом
3. Создаёт `AgentRequest` с параметрами LLM
4. Вызывает `agent.ask(agentRequest)` → получает `AgentResponse`
5. Преобразует `AgentResponse` в `Message` с ролью `ASSISTANT`
6. Добавляет метаданные: `isFinal`, `uncertainty`, `responseTimeMs`, `tokensUsed`
7. Сохраняет в историю и вызывает `onResponse(message)`

#### Специальные методы:
- `sendMessageWithTools()` — для агентов с MCP Tools
- `executeTerminalCommand()` — для TerminalAgent
- `sendMessageWithOrchestratorMode()` — для режима планирования

### 2. AgentResponse → Message

**Преобразование:**
```kotlin
val assistantMsg = Message(
    content = agentResponse.content,           // Текстовое содержимое
    role = MessageRole.ASSISTANT,
    metadata = agentResponse.metadata + mapOf(  // Метаданные агента + дополнительные
        "isFinal" to agentResponse.isFinal,
        "uncertainty" to (agentResponse.uncertainty ?: 0.0),
        "responseTimeMs" to responseTime,
        "tokensUsed" to tokensUsed,
        "tokenUsage" to tokenUsage
    )
)
```

**Текущая структура `AgentResponse`:**
```kotlin
data class AgentResponse(
    val content: String,                    // Текстовое содержимое (markdown)
    val success: Boolean,                   // Успешность выполнения
    val error: String? = null,              // Сообщение об ошибке
    val metadata: Map<String, Any> = emptyMap(),  // Метаданные
    val parsedContent: ParsedResponse? = null,    // Структурированные данные
    val isFinal: Boolean = true,            // Окончательный ответ?
    val uncertainty: Double? = null         // Уровень неопределённости
)
```

### 3. MessageDisplayManager — отображение

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/ui/manager/MessageDisplayManager.kt`

#### Метод: `displayMessage()`

```kotlin
fun displayMessage(message: Message, addToHistory: Boolean = true) {
    when (message.role) {
        MessageRole.USER -> displayUserMessage(message)
        MessageRole.ASSISTANT -> displayAssistantMessage(message)
        MessageRole.SYSTEM -> displaySystemMessage(message.content)
    }
}
```

#### Для сообщений ассистента: `displayAssistantMessage()`

**Что делает:**
1. Создаёт префикс с индикатором (✅/⚠️/❓) на основе `isFinal` и `uncertainty`
2. Рендерит контент через `ChatContentRenderer.renderContentToHtml()`
3. Создаёт статусную строку с метриками (время, токены, неопределённость)
4. Формирует HTML-блок сообщения
5. Добавляет в HTML-документ через `HtmlDocumentManager`

### 4. ChatContentRenderer — рендеринг контента

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/ui/renderer/ChatContentRenderer.kt`

#### Метод: `renderContentToHtml()`

**Что делает:**
1. Парсит markdown-контент
2. Обрабатывает блоки кода с подсветкой синтаксиса
3. Форматирует списки, ссылки, таблицы
4. Генерирует HTML с CSS-классами
5. Регистрирует блоки кода для копирования

---

## Новая архитектура (с форматированным выводом)

### Расширенный поток обработки

```
User Input → ChatService → Agent → AgentResponse (+ FormattedOutput) 
           → Message (+ formattedOutput) → MessageDisplayManager 
           → AgentOutputRenderer → HTML → UI
```

### 1. Расширение AgentResponse

```kotlin
data class AgentResponse(
    val content: String,                          // Текстовое представление (fallback)
    val formattedOutput: FormattedOutput? = null, // ⭐ НОВОЕ: Форматированный вывод
    val success: Boolean,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val parsedContent: ParsedResponse? = null,
    val isFinal: Boolean = true,
    val uncertainty: Double? = null
)
```

### 2. Новые модели данных

```kotlin
// Тип вывода агента
enum class AgentOutputType {
    MARKDOWN,      // Обычный markdown
    TERMINAL,      // Терминальное окно
    CODE_BLOCKS,   // Форматированные блоки кода
    TOOL_RESULT,   // Результат MCP Tool
    STRUCTURED,    // Структурированные данные
    HTML           // Готовый HTML
}

// Отдельный блок форматированного вывода
data class FormattedOutputBlock(
    val type: AgentOutputType,
    val content: String,                          // Контент блока
    val htmlTemplate: String? = null,             // HTML-шаблон
    val cssClasses: List<String> = emptyList(),   // CSS-классы
    val metadata: Map<String, Any> = emptyMap(),  // Метаданные блока
    val order: Int = 0                            // Порядок отображения
)

// Контейнер для множественных блоков
data class FormattedOutput(
    val blocks: List<FormattedOutputBlock>,       // Список блоков
    val rawContent: String? = null                // Сырой контент для fallback
) {
    companion object {
        fun single(block: FormattedOutputBlock): FormattedOutput
        fun multiple(blocks: List<FormattedOutputBlock>): FormattedOutput
    }
}
```

### 3. Обновлённый ChatService

```kotlin
fun sendMessage(
    userMessage: String,
    project: Project,
    onResponse: (Message) -> Unit,
    onError: (String) -> Unit
) {
    scope.launch {
        // ... существующая логика ...
        
        val agentResponse = agent.ask(agentRequest)
        
        withContext(Dispatchers.EDT) {
            if (agentResponse.success) {
                val assistantMsg = Message(
                    content = agentResponse.content,
                    role = MessageRole.ASSISTANT,
                    metadata = agentResponse.metadata + mapOf(
                        "isFinal" to agentResponse.isFinal,
                        "uncertainty" to (agentResponse.uncertainty ?: 0.0),
                        "responseTimeMs" to responseTime,
                        "tokensUsed" to tokensUsed,
                        // ⭐ НОВОЕ: Передаём форматированный вывод через метаданные
                        "formattedOutput" to agentResponse.formattedOutput
                    )
                )
                getCurrentHistory().addMessage(assistantMsg)
                onResponse(assistantMsg)
            }
        }
    }
}
```

### 4. Обновлённый MessageDisplayManager

```kotlin
private fun displayAssistantMessage(message: Message) {
    val prefix = createAssistantPrefix(message)
    
    // ⭐ НОВОЕ: Проверяем наличие форматированного вывода
    val formattedOutput = message.metadata["formattedOutput"] as? FormattedOutput
    
    val bodyHtml = if (formattedOutput != null) {
        // Используем специализированный рендерер для форматированного вывода
        agentOutputRenderer.render(formattedOutput)
    } else {
        // Fallback на обычный markdown-рендеринг
        contentRenderer.renderContentToHtml(message.content, isJcefMode())
    }
    
    val statusHtml = createAssistantStatusHtml(message)
    val isAfterSystem = lastRole == MessageRole.SYSTEM

    val messageHtml = contentRenderer.createMessageBlock(
        role = ChatPanelConfig.RoleClasses.ASSISTANT,
        prefix = prefix,
        content = bodyHtml,
        statusHtml = statusHtml,
        isUser = false,
        isAfterSystem = isAfterSystem
    )

    htmlDocumentManager.appendHtml(messageHtml)
}
```

### 5. Новый AgentOutputRenderer

```kotlin
class AgentOutputRenderer {
    
    /**
     * Рендерит форматированный вывод агента
     */
    fun render(formattedOutput: FormattedOutput): String {
        val htmlBuilder = StringBuilder()
        
        // Сортируем блоки по порядку
        val sortedBlocks = formattedOutput.blocks.sortedBy { it.order }
        
        // Рендерим каждый блок
        sortedBlocks.forEach { block ->
            val blockHtml = when (block.type) {
                AgentOutputType.TERMINAL -> renderTerminalBlock(block)
                AgentOutputType.CODE_BLOCKS -> renderCodeBlock(block)
                AgentOutputType.TOOL_RESULT -> renderToolResultBlock(block)
                AgentOutputType.MARKDOWN -> renderMarkdownBlock(block)
                AgentOutputType.STRUCTURED -> renderStructuredBlock(block)
                AgentOutputType.HTML -> block.htmlTemplate ?: block.content
            }
            
            htmlBuilder.append(blockHtml)
            
            // Добавляем разделитель между блоками (если не последний)
            if (block != sortedBlocks.last()) {
                htmlBuilder.append("""<div class="block-separator"></div>""")
            }
        }
        
        return htmlBuilder.toString()
    }
    
    /**
     * Рендерит терминальный блок
     */
    private fun renderTerminalBlock(block: FormattedOutputBlock): String {
        val command = block.metadata["command"] as? String ?: ""
        val exitCode = block.metadata["exitCode"] as? Int ?: 0
        val executionTime = block.metadata["executionTime"] as? Long ?: 0
        val stdout = block.metadata["stdout"] as? String ?: ""
        val stderr = block.metadata["stderr"] as? String ?: ""
        val success = exitCode == 0
        
        return """
            <div class="terminal-output">
                <div class="terminal-header">
                    <span class="terminal-icon">🖥️</span>
                    <span class="terminal-title">Terminal Output</span>
                </div>
                <div class="terminal-meta">
                    <div><strong>Command:</strong> <code>$command</code></div>
                    <div><strong>Exit Code:</strong> $exitCode</div>
                    <div><strong>Execution Time:</strong> ${executionTime}ms</div>
                    <div><strong>Status:</strong> ${if (success) "✅ Success" else "❌ Failed"}</div>
                </div>
                ${if (stdout.isNotEmpty()) """
                <div class="terminal-stdout">
                    <div class="terminal-label">Output:</div>
                    <pre><code>$stdout</code></pre>
                </div>
                """ else ""}
                ${if (stderr.isNotEmpty()) """
                <div class="terminal-stderr">
                    <div class="terminal-label">Errors:</div>
                    <pre><code>$stderr</code></pre>
                </div>
                """ else ""}
            </div>
        """.trimIndent()
    }
    
    /**
     * Рендерит блок кода
     */
    private fun renderCodeBlock(block: FormattedOutputBlock): String {
        val language = block.metadata["language"] as? String ?: "text"
        val code = block.content
        val codeId = registerCodeBlock(code)
        
        return """
            <div class="code-block-container">
                <div class="code-block-header">
                    <span class="code-language">$language</span>
                    <a href="${ChatPanelConfig.COPY_LINK_PREFIX}$codeId" class="copy-link">
                        ${ChatPanelConfig.Icons.COPY_CODE} Copy
                    </a>
                </div>
                <pre><code class="language-$language">$code</code></pre>
            </div>
        """.trimIndent()
    }
    
    /**
     * Рендерит результат MCP Tool
     */
    private fun renderToolResultBlock(block: FormattedOutputBlock): String {
        val toolName = block.metadata["toolName"] as? String ?: "Unknown"
        val success = block.metadata["success"] as? Boolean ?: false
        val operation = block.metadata["operation"] as? String ?: ""
        
        return """
            <div class="tool-result-block">
                <div class="tool-header">
                    <span class="tool-icon">🔧</span>
                    <span class="tool-name">$toolName</span>
                    <span class="tool-status">${if (success) "✅" else "❌"}</span>
                </div>
                <div class="tool-operation">$operation</div>
                <div class="tool-content">${block.content}</div>
            </div>
        """.trimIndent()
    }
    
    // ... другие методы рендеринга
}
```

### 6. Примеры использования в агентах

#### TerminalAgent

```kotlin
override suspend fun ask(req: AgentRequest): AgentResponse {
    val result = executeCommand(command)
    
    // Создаём форматированный блок для терминала
    val terminalBlock = FormattedOutputBlock(
        type = AgentOutputType.TERMINAL,
        content = result.stdout,
        metadata = mapOf(
            "command" to result.command,
            "exitCode" to result.exitCode,
            "executionTime" to result.executionTime,
            "stdout" to result.stdout,
            "stderr" to result.stderr
        )
    )
    
    val formattedOutput = FormattedOutput.single(terminalBlock)
    
    return AgentResponse(
        content = formatCommandResult(result),  // Fallback текст
        formattedOutput = formattedOutput,      // ⭐ Форматированный вывод
        success = result.success,
        metadata = mapOf(...)
    )
}
```

#### ChatAgent (множественные блоки)

```kotlin
override suspend fun ask(req: AgentRequest): AgentResponse {
    val llmResponse = llmProvider.sendRequest(...)
    
    // Парсим ответ на блоки
    val blocks = chatOutputFormatter.extractBlocks(llmResponse.content)
    
    // Создаём форматированный вывод с множественными блоками
    val formattedOutput = FormattedOutput.multiple(blocks)
    
    return AgentResponse(
        content = llmResponse.content,          // Fallback текст
        formattedOutput = formattedOutput,      // ⭐ Множественные блоки
        success = true,
        metadata = mapOf(...)
    )
}
```

#### ChatAgentWithTools (файловая операция)

```kotlin
// После выполнения MCP Tool
val toolResultBlock = FormattedOutputBlock(
    type = AgentOutputType.TOOL_RESULT,
    content = fileContent,
    metadata = mapOf(
        "toolName" to "create_file",
        "operation" to "Created file: $filePath",
        "success" to true,
        "filePath" to filePath,
        "fileSize" to fileSize
    )
)

val codeBlock = FormattedOutputBlock(
    type = AgentOutputType.CODE_BLOCKS,
    content = fileContent,
    metadata = mapOf("language" to "kotlin"),
    order = 1
)

val formattedOutput = FormattedOutput.multiple(listOf(toolResultBlock, codeBlock))
```

---

## Преимущества новой архитектуры

### 1. Разделение ответственности
- **Агенты** отвечают за создание форматированного вывода
- **ChatService** передаёт данные без изменений
- **MessageDisplayManager** координирует отображение
- **AgentOutputRenderer** специализируется на рендеринге

### 2. Гибкость
- Каждый агент может создавать свой тип вывода
- Поддержка множественных блоков в одном ответе
- Легко добавлять новые типы вывода

### 3. Обратная совместимость
- Если `formattedOutput == null`, используется `content`
- Старые агенты продолжают работать
- Постепенная миграция

### 4. Типобезопасность
- Чёткие типы для каждого вида вывода
- Метаданные типизированы через `metadata: Map<String, Any>`
- Enum для типов вывода

### 5. Тестируемость
- Каждый компонент можно тестировать отдельно
- Моки для форматтеров и рендереров
- Unit-тесты для каждого типа блока

---

## Необходимые доработки ChatService и UI

### ChatService — минимальные изменения

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/service/ChatService.kt`

#### ✅ НЕ требует изменений:
- Метод `sendMessage()` — работает как есть
- Формирование `AgentRequest` — без изменений
- Вызов `agent.ask()` — без изменений
- Обработка ошибок — без изменений

#### ⚠️ Требует ОДНОГО изменения:

**Было:**
```kotlin
val assistantMsg = Message(
    content = agentResponse.content,
    role = MessageRole.ASSISTANT,
    metadata = agentResponse.metadata + mapOf(
        "isFinal" to agentResponse.isFinal,
        "uncertainty" to (agentResponse.uncertainty ?: 0.0),
        "responseTimeMs" to responseTime,
        "tokensUsed" to tokensUsed,
        "tokenUsage" to tokenUsage
    )
)
```

**Стало (добавить 1 строку):**
```kotlin
val assistantMsg = Message(
    content = agentResponse.content,
    role = MessageRole.ASSISTANT,
    metadata = agentResponse.metadata + mapOf(
        "isFinal" to agentResponse.isFinal,
        "uncertainty" to (agentResponse.uncertainty ?: 0.0),
        "responseTimeMs" to responseTime,
        "tokensUsed" to tokensUsed,
        "tokenUsage" to tokenUsage,
        "formattedOutput" to agentResponse.formattedOutput  // ⭐ ДОБАВИТЬ ЭТУ СТРОКУ
    )
)
```

**Итого для ChatService:** 1 строка кода в 3 методах:
- `sendMessage()` — основной метод
- `sendMessageWithTools()` — для MCP Tools
- `executeTerminalCommand()` — для терминала

---

### MessageDisplayManager — одно изменение

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/ui/manager/MessageDisplayManager.kt`

#### ✅ НЕ требует изменений:
- `displayMessage()` — роутинг по ролям
- `displayUserMessage()` — отображение сообщений пользователя
- `displaySystemMessage()` — системные сообщения
- `createAssistantPrefix()` — создание префикса
- `createAssistantStatusHtml()` — статусная строка
- Все остальные методы

#### ⚠️ Требует ОДНОГО изменения:

**Было:**
```kotlin
private fun displayAssistantMessage(message: Message) {
    val prefix = createAssistantPrefix(message)
    val bodyHtml = contentRenderer.renderContentToHtml(message.content, isJcefMode())
    val statusHtml = createAssistantStatusHtml(message)
    val isAfterSystem = lastRole == MessageRole.SYSTEM

    val messageHtml = contentRenderer.createMessageBlock(
        role = ChatPanelConfig.RoleClasses.ASSISTANT,
        prefix = prefix,
        content = bodyHtml,
        statusHtml = statusHtml,
        isUser = false,
        isAfterSystem = isAfterSystem
    )

    htmlDocumentManager.appendHtml(messageHtml)
}
```

**Стало (добавить проверку):**
```kotlin
private fun displayAssistantMessage(message: Message) {
    val prefix = createAssistantPrefix(message)
    
    // ⭐ ДОБАВИТЬ ЭТИ 6 СТРОК
    val formattedOutput = message.metadata["formattedOutput"] as? FormattedOutput
    val bodyHtml = if (formattedOutput != null) {
        agentOutputRenderer.render(formattedOutput)
    } else {
        contentRenderer.renderContentToHtml(message.content, isJcefMode())
    }
    
    val statusHtml = createAssistantStatusHtml(message)
    val isAfterSystem = lastRole == MessageRole.SYSTEM

    val messageHtml = contentRenderer.createMessageBlock(
        role = ChatPanelConfig.RoleClasses.ASSISTANT,
        prefix = prefix,
        content = bodyHtml,
        statusHtml = statusHtml,
        isUser = false,
        isAfterSystem = isAfterSystem
    )

    htmlDocumentManager.appendHtml(messageHtml)
}
```

**Также добавить поле:**
```kotlin
class MessageDisplayManager(
    private val htmlDocumentManager: HtmlDocumentManager,
    private val contentRenderer: ChatContentRenderer,
    private val agentOutputRenderer: AgentOutputRenderer  // ⭐ ДОБАВИТЬ
) {
    // ...
}
```

**Итого для MessageDisplayManager:** 
- 1 новое поле в конструкторе
- 6 строк кода в методе `displayAssistantMessage()`

---

### ChatPanel — без изменений

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/ui/ChatPanel.kt`

#### ✅ НЕ требует изменений:
- Вся логика остаётся прежней
- `sendMessage()` — без изменений
- `executeTerminalCommand()` — без изменений
- Обработка команд `/plan`, `/terminal` — без изменений

**Почему?** 
`ChatPanel` работает с `Message`, а не с `AgentResponse`. Форматированный вывод передаётся через метаданные `Message`, которые `ChatPanel` не обрабатывает напрямую.

---

### ChatContentRenderer — без изменений

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/ui/renderer/ChatContentRenderer.kt`

#### ✅ НЕ требует изменений:
- Все существующие методы остаются
- `renderContentToHtml()` — используется как fallback
- `createMessageBlock()` — без изменений
- `createStatusHtml()` — без изменений

**Почему?**
Новый `AgentOutputRenderer` работает параллельно, не заменяя существующий функционал.

---

### Новые компоненты (нужно создать)

#### 1. AgentOutputRenderer
**Файл:** `src/main/kotlin/ru/marslab/ide/ride/ui/renderer/AgentOutputRenderer.kt`

```kotlin
class AgentOutputRenderer(
    private val messageDisplayManager: MessageDisplayManager
) {
    fun render(formattedOutput: FormattedOutput): String
    private fun renderTerminalBlock(block: FormattedOutputBlock): String
    private fun renderCodeBlock(block: FormattedOutputBlock): String
    private fun renderToolResultBlock(block: FormattedOutputBlock): String
    private fun renderMarkdownBlock(block: FormattedOutputBlock): String
    private fun renderStructuredBlock(block: FormattedOutputBlock): String
}
```

**Размер:** ~200-300 строк кода

#### 2. Форматтеры для агентов

**TerminalOutputFormatter:**
```kotlin
class TerminalOutputFormatter {
    fun formatAsHtml(result: TerminalCommandResult): FormattedOutput
    fun createTerminalWindow(...): String
}
```

**CodeBlockFormatter:**
```kotlin
class CodeBlockFormatter {
    fun formatAsHtml(content: String): FormattedOutput
    fun extractCodeBlocks(markdown: String): List<CodeBlock>
    fun wrapInTemplate(blocks: List<CodeBlock>, text: String): String
}
```

**ChatOutputFormatter:**
```kotlin
class ChatOutputFormatter {
    fun formatAsHtml(content: String): FormattedOutput
    fun extractBlocks(markdown: String): List<FormattedOutputBlock>
    fun createTextBlock(text: String): FormattedOutputBlock
    fun createCodeBlock(code: String, language: String): FormattedOutputBlock
    fun createListBlock(items: List<String>): FormattedOutputBlock
}
```

**ToolResultFormatter:**
```kotlin
class ToolResultFormatter {
    fun formatToolCall(toolName: String, params: Map<String, Any>): FormattedOutputBlock
    fun formatToolResult(result: Any, success: Boolean): FormattedOutputBlock
    fun formatFileOperation(operation: String, path: String, result: String): FormattedOutputBlock
}
```

**Размер каждого:** ~100-150 строк кода

#### 3. CSS-стили

**Файл:** `src/main/resources/css/agent-output.css` (новый)

```css
/* Terminal output styles */
.terminal-output { ... }
.terminal-header { ... }
.terminal-meta { ... }
.terminal-stdout { ... }
.terminal-stderr { ... }

/* Code block styles */
.code-block-container { ... }
.code-block-header { ... }

/* Tool result styles */
.tool-result-block { ... }
.tool-header { ... }

/* Multi-block styles */
.multi-block-container { ... }
.block-separator { ... }
```

**Размер:** ~200-300 строк CSS

---

## Сводная таблица изменений

| Компонент | Тип изменения | Объём работы | Критичность |
|-----------|---------------|--------------|-------------|
| **AgentResponse** | Добавить поле | 1 строка | Высокая |
| **FormattedOutput** (новый) | Создать модель | ~50 строк | Высокая |
| **FormattedOutputBlock** (новый) | Создать модель | ~30 строк | Высокая |
| **AgentOutputType** (новый) | Создать enum | ~10 строк | Высокая |
| **ChatService** | Добавить в metadata | 3 строки (3 метода) | Высокая |
| **MessageDisplayManager** | Добавить проверку | 7 строк | Высокая |
| **AgentOutputRenderer** (новый) | Создать класс | ~300 строк | Высокая |
| **TerminalOutputFormatter** (новый) | Создать класс | ~150 строк | Средняя |
| **CodeBlockFormatter** (новый) | Создать класс | ~150 строк | Средняя |
| **ChatOutputFormatter** (новый) | Создать класс | ~200 строк | Средняя |
| **ToolResultFormatter** (новый) | Создать класс | ~150 строк | Средняя |
| **agent-output.css** (новый) | Создать стили | ~300 строк | Средняя |
| **ChatPanel** | Без изменений | 0 строк | - |
| **ChatContentRenderer** | Без изменений | 0 строк | - |
| **HtmlDocumentManager** | Без изменений | 0 строк | - |

### Итого:
- **Изменений в существующем коде:** ~10 строк
- **Нового кода:** ~1300 строк
- **Критичных изменений:** 2 файла (ChatService, MessageDisplayManager)

---

## Миграционный путь

### Этап 1: Добавление новых моделей (1-2 часа)
- [ ] Создать `AgentOutputType` enum
- [ ] Создать `FormattedOutputBlock` data class
- [ ] Создать `FormattedOutput` data class
- [ ] Расширить `AgentResponse` с полем `formattedOutput`
- [ ] Убедиться, что всё компилируется

**Риски:** Нет. Обратная совместимость через опциональность.

### Этап 2: Создание рендереров (4-6 часов)
- [ ] Реализовать `AgentOutputRenderer`
- [ ] Добавить методы для каждого типа блока
- [ ] Создать CSS-стили `agent-output.css`
- [ ] Протестировать рендеринг с моками

**Риски:** Низкие. Новый код, не затрагивает существующий.

### Этап 3: Обновление ChatService (15 минут)
- [ ] Добавить `formattedOutput` в metadata в `sendMessage()`
- [ ] Добавить в `sendMessageWithTools()`
- [ ] Добавить в `executeTerminalCommand()`
- [ ] Протестировать, что metadata передаётся

**Риски:** Минимальные. Простое добавление в map.

### Этап 4: Обновление MessageDisplayManager (30 минут)
- [ ] Добавить `AgentOutputRenderer` в конструктор
- [ ] Добавить проверку `formattedOutput` в `displayAssistantMessage()`
- [ ] Добавить fallback на `contentRenderer`
- [ ] Протестировать с существующими агентами (должны работать через fallback)

**Риски:** Низкие. Fallback гарантирует работу старого кода.

### Этап 5: Создание форматтеров (6-8 часов)
- [ ] Реализовать `TerminalOutputFormatter`
- [ ] Реализовать `CodeBlockFormatter`
- [ ] Реализовать `ChatOutputFormatter`
- [ ] Реализовать `ToolResultFormatter`
- [ ] Unit-тесты для каждого

**Риски:** Низкие. Новый код, изолированный.

### Этап 6: Миграция агентов (8-10 часов)
- [ ] Обновить `TerminalAgent` (самый простой)
- [ ] Обновить `ExecutorAgent`
- [ ] Обновить `ChatAgent`
- [ ] Обновить `ChatAgentWithTools`
- [ ] Интеграционные тесты

**Риски:** Средние. Нужно тщательно тестировать каждый агент.

### Этап 7: Тестирование и доработка (4-6 часов)
- [ ] UI-тесты с реальными сценариями
- [ ] Проверка всех типов вывода
- [ ] Проверка fallback на старый формат
- [ ] Проверка множественных блоков
- [ ] Фикс багов

**Риски:** Средние. Возможны edge cases.

### Этап 8: Документация (2-3 часа)
- [ ] Обновить архитектурную документацию
- [ ] Примеры использования для разработчиков
- [ ] Руководство по созданию новых типов вывода
- [ ] API-документация

**Риски:** Нет.

---

## Оценка трудозатрат

| Этап | Время | Риски |
|------|-------|-------|
| Модели данных | 1-2 ч | Нет |
| Рендереры | 4-6 ч | Низкие |
| ChatService | 0.25 ч | Минимальные |
| MessageDisplayManager | 0.5 ч | Низкие |
| Форматтеры | 6-8 ч | Низкие |
| Миграция агентов | 8-10 ч | Средние |
| Тестирование | 4-6 ч | Средние |
| Документация | 2-3 ч | Нет |
| **ИТОГО** | **26-36 часов** | **Низкие-Средние** |

**Реальная оценка с запасом:** 3-5 рабочих дней (1 разработчик)

---

## Ключевые преимущества подхода

### 1. Минимальные изменения в существующем коде
- **ChatService:** 3 строки в 3 методах = 9 строк
- **MessageDisplayManager:** 7 строк в 1 методе
- **Остальное:** 0 изменений

### 2. Полная обратная совместимость
- Старые агенты работают через fallback
- Если `formattedOutput == null`, используется `content`
- Можно мигрировать агенты постепенно

### 3. Изолированность нового кода
- Новые классы не влияют на существующие
- Можно разрабатывать и тестировать параллельно
- Легко откатить изменения

### 4. Расширяемость
- Легко добавлять новые типы вывода
- Каждый агент может иметь свой форматтер
- Поддержка множественных блоков из коробки

### 5. Тестируемость
- Каждый компонент тестируется отдельно
- Моки для форматтеров и рендереров
- Интеграционные тесты не ломают существующий функционал

---

## Итого

**Текущая архитектура:**
- Агенты возвращают markdown-текст в `content`
- `ChatContentRenderer` универсально рендерит всё
- Нет специализированного форматирования

**Новая архитектура:**
- Агенты возвращают `FormattedOutput` с типизированными блоками
- `AgentOutputRenderer` специализированно рендерит каждый тип
- Поддержка множественных блоков
- Обратная совместимость через fallback на `content`
