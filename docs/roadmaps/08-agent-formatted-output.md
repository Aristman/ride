# Roadmap: Форматированный вывод агентов

## Описание
Доработка системы агентов для возврата готовых, сверстанных данных для отображения в чате. Каждый агент будет отдавать на выходе готовый HTML-блок с форматированием, специфичным для типа агента.

## Цели
- **TerminalAgent**: отображение результата в стилизованном терминальном окне (как на скриншоте)
- **ExecutorAgent**: форматированный вывод с блоками кода и текстом
- **Унификация**: единый интерфейс для всех агентов через расширенный `AgentResponse`

## Анализ текущего состояния

### Что есть сейчас
1. **AgentResponse** (`model/agent/AgentResponse.kt`):
   - Содержит `content: String` - текстовое представление
   - Есть `metadata: Map<String, Any>` для дополнительных данных
   - Есть `parsedContent: ParsedResponse?` для структурированных данных

2. **TerminalAgent** (`agent/impl/TerminalAgent.kt`):
   - Метод `formatCommandResult()` возвращает Markdown-форматированный текст
   - Использует эмодзи и markdown для форматирования
   - Отдает простую строку в `AgentResponse.content`

3. **ExecutorAgent** (`agent/impl/ExecutorAgent.kt`):
   - Возвращает сырой ответ от LLM
   - Полагается на markdown в тексте ответа
   - Нет специального форматирования

4. **MessageDisplayManager** (`ui/manager/MessageDisplayManager.kt`):
   - Использует `ChatContentRenderer` для рендеринга markdown в HTML
   - Универсальный подход для всех типов сообщений
   - Нет специализированных рендереров для разных агентов

### Проблемы
- Агенты отдают markdown-текст, который потом рендерится универсально
- Нет возможности создать специализированное оформление (например, терминальное окно)
- Форматирование зависит от рендерера, а не от агента
- Нет типизации для разных видов контента

## Задачи

### 1. Расширение модели данных
- [ ] Создать `enum class AgentOutputType`:
  - `MARKDOWN` - обычный markdown (по умолчанию)
  - `TERMINAL` - вывод терминала
  - `CODE_BLOCKS` - форматированные блоки кода
  - `STRUCTURED` - структурированные данные
  - `HTML` - готовый HTML

- [ ] Создать `data class FormattedOutputBlock`:
  ```kotlin
  data class FormattedOutputBlock(
      val type: AgentOutputType,
      val content: String,           // Основной контент блока
      val htmlTemplate: String? = null,  // HTML-шаблон для рендеринга
      val cssClasses: List<String> = emptyList(),  // CSS-классы для стилизации
      val metadata: Map<String, Any> = emptyMap(),  // Дополнительные данные
      val order: Int = 0             // Порядок отображения блока
  )
  ```

- [ ] Создать `data class FormattedOutput`:
  ```kotlin
  data class FormattedOutput(
      val blocks: List<FormattedOutputBlock>,  // Список форматированных блоков
      val rawContent: String? = null  // Сырой контент для fallback
  ) {
      companion object {
          fun single(block: FormattedOutputBlock): FormattedOutput
          fun multiple(blocks: List<FormattedOutputBlock>): FormattedOutput
      }
  }
  ```

- [ ] Расширить `AgentResponse`:
  ```kotlin
  data class AgentResponse(
      val content: String,  // Текстовое представление (для обратной совместимости)
      val formattedOutput: FormattedOutput? = null,  // Форматированный вывод
      // ... остальные поля
  )
  ```

### 2. Создание HTML-шаблонов для агентов

- [ ] Создать `TerminalOutputTemplate` для терминального вывода:
  - Стилизованное окно терминала с заголовком
  - Отображение команды, кода выхода, времени выполнения
  - Разделение stdout/stderr с цветовым кодированием
  - Иконки статуса (✅/❌)
  - Монопространный шрифт для вывода

- [ ] Создать `CodeBlockTemplate` для ExecutorAgent:
  - Форматированные блоки кода с подсветкой синтаксиса
  - Кнопки копирования для каждого блока
  - Разделение текста и кода
  - Поддержка множественных блоков кода

- [ ] Создать CSS-стили для шаблонов:
  - `.terminal-output` - стили терминального окна
  - `.terminal-header` - заголовок терминала
  - `.terminal-body` - тело вывода
  - `.terminal-stdout` - стандартный вывод
  - `.terminal-stderr` - вывод ошибок
  - `.code-block-container` - контейнер блока кода
  - `.code-block-header` - заголовок блока кода

### 3. Классификация агентов

#### Исполнительные агенты (возвращают форматированный вывод)
- [ ] **ChatAgent** - основной агент для общения с пользователем
  - Форматирует ответы с блоками кода, текстом, списками
  - Поддерживает множественные блоки разных типов
  - Тип вывода: `MARKDOWN`, `CODE_BLOCKS`, `STRUCTURED`

- [ ] **ChatAgentWithTools** - агент с поддержкой MCP Tools
  - Форматирует результаты вызова инструментов
  - Показывает статус выполнения операций
  - Тип вывода: `MARKDOWN`, `CODE_BLOCKS`, `TOOL_RESULT`

- [ ] **ExecutorAgent** - агент выполнения задач из плана
  - Форматирует ответы с блоками кода и пояснениями
  - Структурированный вывод результата задачи
  - Тип вывода: `MARKDOWN`, `CODE_BLOCKS`

- [ ] **TerminalAgent** - агент выполнения команд терминала
  - Терминальное окно с выводом команды
  - Разделение stdout/stderr
  - Тип вывода: `TERMINAL`

#### Вспомогательные агенты (НЕ возвращают форматированный вывод)
- **PlannerAgent** - создание плана задач
  - Возвращает структурированный JSON/XML
  - Не требует визуального форматирования
  - Используется внутри системы

- **SummarizerAgent** - сжатие истории диалога
  - Возвращает текстовое резюме
  - Не отображается пользователю напрямую
  - Используется для оптимизации контекста

### 4. Доработка TerminalAgent

- [ ] Создать `TerminalOutputFormatter`:
  ```kotlin
  class TerminalOutputFormatter {
      fun formatAsHtml(result: TerminalCommandResult): FormattedOutput
      fun createTerminalWindow(
          command: String,
          exitCode: Int,
          executionTime: Long,
          stdout: String,
          stderr: String,
          success: Boolean
      ): String
  }
  ```

- [ ] Обновить `TerminalAgent.formatCommandResult()`:
  - Использовать `TerminalOutputFormatter`
  - Возвращать `FormattedOutput` с типом `TERMINAL`
  - Генерировать готовый HTML с терминальным окном

- [ ] Обновить `TerminalAgent.ask()`:
  - Добавить `formattedOutput` в `AgentResponse`
  - Сохранить обратную совместимость через `content`

### 5. Доработка ExecutorAgent

- [ ] Создать `CodeBlockFormatter`:
  ```kotlin
  class CodeBlockFormatter {
      fun formatAsHtml(content: String): FormattedOutput
      fun extractCodeBlocks(markdown: String): List<CodeBlock>
      fun wrapInTemplate(blocks: List<CodeBlock>, text: String): String
  }
  ```

- [ ] Обновить `ExecutorAgent.ask()`:
  - Парсить ответ LLM на наличие блоков кода
  - Использовать `CodeBlockFormatter` для форматирования
  - Добавить `formattedOutput` в `AgentResponse`

### 6. Доработка ChatAgent

- [ ] Создать `ChatOutputFormatter`:
  ```kotlin
  class ChatOutputFormatter {
      fun formatAsHtml(content: String): FormattedOutput
      fun extractBlocks(markdown: String): List<FormattedOutputBlock>
      fun createTextBlock(text: String): FormattedOutputBlock
      fun createCodeBlock(code: String, language: String): FormattedOutputBlock
      fun createListBlock(items: List<String>): FormattedOutputBlock
  }
  ```

- [ ] Обновить `ChatAgent.ask()`:
  - Парсить ответ LLM на различные типы контента
  - Создавать множественные блоки для сложных ответов
  - Добавить `formattedOutput` в `AgentResponse`
  - Поддержка вложенных блоков кода в текст

- [ ] Обработка специальных случаев:
  - Множественные блоки кода с разными языками
  - Смешанный контент (текст + код + списки)
  - Таблицы и диаграммы в markdown

### 7. Доработка ChatAgentWithTools

- [ ] Создать `ToolResultFormatter`:
  ```kotlin
  class ToolResultFormatter {
      fun formatToolCall(toolName: String, params: Map<String, Any>): FormattedOutputBlock
      fun formatToolResult(result: Any, success: Boolean): FormattedOutputBlock
      fun formatFileOperation(operation: String, path: String, result: String): FormattedOutputBlock
  }
  ```

- [ ] Обновить обработку результатов MCP Tools:
  - Визуализация вызова инструмента
  - Форматированный вывод результата операции
  - Индикаторы успеха/ошибки
  - Множественные операции в одном ответе

- [ ] Специальные блоки для файловых операций:
  - Создание файла: показать путь и содержимое
  - Чтение файла: форматированный вывод с подсветкой
  - Изменение файла: diff-представление
  - Удаление файла: подтверждение операции

### 8. Обновление ChatService

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/service/ChatService.kt`

- [ ] Обновить метод `sendMessage()`:
  ```kotlin
  // Добавить 1 строку в metadata:
  val assistantMsg = Message(
      content = agentResponse.content,
      role = MessageRole.ASSISTANT,
      metadata = agentResponse.metadata + mapOf(
          "isFinal" to agentResponse.isFinal,
          "uncertainty" to (agentResponse.uncertainty ?: 0.0),
          "responseTimeMs" to responseTime,
          "tokensUsed" to tokensUsed,
          "tokenUsage" to tokenUsage,
          "formattedOutput" to agentResponse.formattedOutput  // ⭐ ДОБАВИТЬ
      )
  )
  ```

- [ ] Обновить метод `sendMessageWithTools()`:
  - Добавить `"formattedOutput" to agentResponse.formattedOutput` в metadata
  - Аналогично `sendMessage()`

- [ ] Обновить метод `executeTerminalCommand()`:
  - Добавить `"formattedOutput" to agentResponse.formattedOutput` в metadata
  - Аналогично `sendMessage()`

**Объём изменений:** 3 строки кода (по 1 в каждом методе)

**Риски:** Минимальные. Простое добавление в map, не ломает существующий функционал.

---

### 9. Обновление UI-слоя

#### 9.1. Создание AgentOutputRenderer

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/ui/renderer/AgentOutputRenderer.kt` (новый)

- [ ] Создать класс `AgentOutputRenderer`:
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

- [ ] Реализовать метод `render()`:
  - Сортировать блоки по `order`
  - Рендерить каждый блок в зависимости от типа
  - Добавлять разделители между блоками
  - Обрабатывать ошибки с fallback на `rawContent`

- [ ] Реализовать `renderTerminalBlock()`:
  - Создавать HTML с классом `.terminal-output`
  - Отображать метаданные команды (command, exitCode, executionTime)
  - Разделять stdout/stderr с разными стилями
  - Добавлять иконки статуса (✅/❌)

- [ ] Реализовать `renderCodeBlock()`:
  - Создавать HTML с классом `.code-block-container`
  - Добавлять заголовок с языком программирования
  - Регистрировать код для копирования
  - Добавлять кнопку "Copy"

- [ ] Реализовать `renderToolResultBlock()`:
  - Создавать HTML с классом `.tool-result-block`
  - Отображать название инструмента и статус
  - Форматировать результат операции
  - Поддержка файловых операций (создание, чтение, изменение, удаление)

- [ ] Реализовать `renderMarkdownBlock()`:
  - Использовать существующий `ChatContentRenderer`
  - Обрабатывать markdown-разметку
  - Поддержка вложенных элементов

- [ ] Реализовать `renderStructuredBlock()`:
  - Форматирование JSON/XML данных
  - Подсветка синтаксиса
  - Сворачиваемые секции для больших данных

**Объём работы:** ~300 строк кода

**Риски:** Низкие. Новый класс, не затрагивает существующий код.

#### 9.2. Обновление MessageDisplayManager

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/ui/manager/MessageDisplayManager.kt`

- [ ] Добавить поле `agentOutputRenderer` в конструктор:
  ```kotlin
  class MessageDisplayManager(
      private val htmlDocumentManager: HtmlDocumentManager,
      private val contentRenderer: ChatContentRenderer,
      private val agentOutputRenderer: AgentOutputRenderer  // ⭐ ДОБАВИТЬ
  ) {
      // ...
  }
  ```

- [ ] Обновить метод `displayAssistantMessage()`:
  ```kotlin
  private fun displayAssistantMessage(message: Message) {
      val prefix = createAssistantPrefix(message)
      
      // ⭐ ДОБАВИТЬ ПРОВЕРКУ formattedOutput
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

- [ ] Обновить инициализацию в `ChatPanel`:
  - Создать экземпляр `AgentOutputRenderer`
  - Передать в конструктор `MessageDisplayManager`

**Объём изменений:** 
- 1 новое поле в конструкторе
- 6 строк кода в методе `displayAssistantMessage()`
- 2 строки для инициализации в `ChatPanel`

**Риски:** Низкие. Fallback на старый рендеринг гарантирует совместимость.

#### 9.3. ChatContentRenderer — без изменений

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/ui/renderer/ChatContentRenderer.kt`

- [x] Оставить без изменений
  - Все существующие методы продолжают работать
  - Используется как fallback для старых агентов
  - Используется для рендеринга markdown-блоков

**Объём изменений:** 0 строк

**Риски:** Нет.

### 10. Стилизация и темы

- [ ] Добавить CSS для терминального окна:
  - Темная тема (по умолчанию)
  - Светлая тема
  - Адаптация под IntelliJ IDEA темы

- [ ] Добавить CSS для блоков кода:
  - Улучшенная подсветка синтаксиса
  - Hover-эффекты
  - Кнопки действий (копировать, развернуть)

- [ ] Обеспечить responsive-дизайн:
  - Адаптация под разные размеры панели
  - Скроллинг для длинного вывода

- [ ] Добавить CSS для блоков инструментов:
  - `.tool-call-block` - блок вызова инструмента
  - `.tool-result-block` - блок результата
  - `.file-operation-block` - блок файловой операции
  - `.diff-block` - блок с diff-представлением

- [ ] Добавить CSS для множественных блоков:
  - `.multi-block-container` - контейнер для нескольких блоков
  - `.block-separator` - разделитель между блоками
  - Анимации переходов между блоками

### 11. Тестирование и документация

- [ ] Написать unit-тесты:
  - `TerminalOutputFormatterTest`
  - `CodeBlockFormatterTest`
  - `AgentOutputRendererTest`

- [ ] Написать интеграционные тесты:
  - Тест полного цикла: TerminalAgent → формат → рендеринг
  - Тест полного цикла: ExecutorAgent → формат → рендеринг

- [ ] Обновить документацию:
  - Примеры использования форматированного вывода
  - Руководство по созданию новых типов вывода
  - API-документация для `FormattedOutput`

- [ ] Создать примеры:
  - Скриншоты терминального вывода
  - Скриншоты форматированных блоков кода
  - Примеры кастомных шаблонов

- [ ] Добавить интеграционные тесты для каждого агента:
  - `ChatAgentFormattingTest` - тестирование форматирования ChatAgent
  - `ChatAgentWithToolsFormattingTest` - тестирование форматирования с tools
  - `ExecutorAgentFormattingTest` - тестирование форматирования ExecutorAgent
  - `TerminalAgentFormattingTest` - тестирование форматирования TerminalAgent

- [ ] Тесты для множественных блоков:
  - Корректный порядок отображения
  - Обработка вложенных блоков
  - Fallback на сырой контент при ошибках

### 12. Обратная совместимость

- [ ] Обеспечить работу существующего кода:
  - `formattedOutput` опционален в `AgentResponse`
  - Fallback на `content` если `formattedOutput == null`
  - Старые агенты продолжают работать без изменений

- [ ] Миграция существующих агентов:
  - Постепенный переход на новый формат
  - Документация по миграции

## Приоритеты

### Высокий приоритет
1. Расширение модели данных (задача 1)
2. Создание HTML-шаблонов (задача 2)
3. Классификация агентов (задача 3)
4. Доработка TerminalAgent (задача 4)
5. Обновление ChatService (задача 8) ⭐
6. Обновление UI-слоя (задача 9) ⭐

### Средний приоритет
7. Доработка ExecutorAgent (задача 5)
8. Доработка ChatAgent (задача 6)
9. Доработка ChatAgentWithTools (задача 7)
10. Стилизация и темы (задача 10)

### Низкий приоритет
11. Тестирование и документация (задача 11)
12. Обратная совместимость (задача 12)

## Ожидаемый результат

### TerminalAgent
```
┌─────────────────────────────────────────────┐
│ 🖥️ Terminal Output                          │
├─────────────────────────────────────────────┤
│ Command: git status                         │
│ Exit Code: 0                                │
│ Execution Time: 145ms                       │
│ Status: ✅ Success                          │
├─────────────────────────────────────────────┤
│ On branch main                              │
│ Your branch is up to date with 'origin/main'│
│                                             │
│ Changes not staged for commit:              │
│   modified:   src/main/kotlin/...           │
└─────────────────────────────────────────────┘
```

### ExecutorAgent
```
📝 Вот решение задачи:

Для реализации функции используйте следующий код:

┌─────────────────────────────────────────────┐
│ kotlin                              [Copy]  │
├─────────────────────────────────────────────┤
│ fun calculateSum(a: Int, b: Int): Int {    │
│     return a + b                            │
│ }                                           │
└─────────────────────────────────────────────┘

Эта функция принимает два параметра и возвращает их сумму.
```

### ChatAgent (множественные блоки)
```
📝 Вот пример работы с файлами в Kotlin:

1. Чтение файла:
┌─────────────────────────────────────────────┐
│ kotlin                              [Copy]  │
├─────────────────────────────────────────────┤
│ val content = File("data.txt").readText()  │
└─────────────────────────────────────────────┘

2. Запись в файл:
┌─────────────────────────────────────────────┐
│ kotlin                              [Copy]  │
├─────────────────────────────────────────────┤
│ File("output.txt").writeText("Hello!")     │
└─────────────────────────────────────────────┘

3. Построчное чтение:
┌─────────────────────────────────────────────┐
│ kotlin                              [Copy]  │
├─────────────────────────────────────────────┤
│ File("data.txt").forEachLine { line ->     │
│     println(line)                           │
│ }                                           │
└─────────────────────────────────────────────┘
```

### ChatAgentWithTools (результат MCP операции)
```
🔧 Выполнена операция с файлом:

┌─────────────────────────────────────────────┐
│ 📝 Создание файла                           │
├─────────────────────────────────────────────┤
│ Путь: src/main/kotlin/Example.kt           │
│ Статус: ✅ Успешно                         │
│ Размер: 245 байт                            │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ kotlin                              [View]  │
├─────────────────────────────────────────────┤
│ package com.example                         │
│                                             │
│ fun main() {                                │
│     println("Hello, World!")                │
│ }                                           │
└─────────────────────────────────────────────┘
```

## Связанные компоненты

### Модели данных
- `AgentResponse` - модель ответа агента
- `FormattedOutput` - модель форматированного вывода
- `FormattedOutputBlock` - модель отдельного блока вывода
- `AgentOutputType` - типы вывода агентов

### Агенты (исполнительные)
- `ChatAgent` - основной агент для общения
- `ChatAgentWithTools` - агент с MCP Tools
- `ExecutorAgent` - агент выполнения задач
- `TerminalAgent` - агент выполнения команд

### Агенты (вспомогательные)
- `PlannerAgent` - создание плана (без форматирования)
- `SummarizerAgent` - сжатие истории (без форматирования)

### UI компоненты
- `MessageDisplayManager` - менеджер отображения сообщений
- `ChatContentRenderer` - рендерер контента чата
- `AgentOutputRenderer` - рендерер форматированного вывода агентов

### Форматтеры
- `TerminalOutputFormatter` - форматирование вывода терминала
- `CodeBlockFormatter` - форматирование блоков кода
- `ChatOutputFormatter` - форматирование вывода чата
- `ToolResultFormatter` - форматирование результатов инструментов

## Примечания
- Сохранить обратную совместимость со старым форматом
- Использовать CSS-переменные для легкой кастомизации тем
- Предусмотреть возможность добавления новых типов вывода в будущем
- Оптимизировать производительность рендеринга для больших выводов
