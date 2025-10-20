ёё# Roadmap: Интеграция MCP Server в Ride Plugin

## Цель

Интегрировать MCP Server Rust в плагин Ride с автоматической установкой, запуском и подключением к Chat Agent через
Yandex GPT Tools API.

## Задачи

### 1. Автоматическая установка и запуск MCP Server

- [x] Создать `MCPServerManager` для управления жизненным циклом сервера
    - [x] Реализовать извлечение встроенного бинарника из ресурсов
    - [x] Добавить fallback на скачивание с GitHub Releases
    - [x] Добавить fallback на сборку из исходников
    - [x] Реализовать автоматический запуск сервера
    - [x] Добавить проверку health endpoint
    - [x] Реализовать graceful shutdown
- [ ] Настроить Gradle для сборки и упаковки бинарников
    - [ ] Создать задачу `buildMCPServer`
    - [ ] Настроить копирование бинарников в resources
    - [ ] Добавить зависимость в `buildPlugin`
- [x] Создать `RideStartupActivity` для автозапуска сервера
- [x] Создать `RideApplicationListener` для остановки сервера
- [x] Добавить регистрацию в `plugin.xml`

### 2. UI для управления MCP Server

- [x] Создать `MCPServerConfigurable` для настроек (уже существует)
    - [x] Добавить индикатор статуса сервера (Running/Stopped)
    - [x] Добавить кнопки Start/Stop
    - [ ] Добавить чекбокс "Auto-start on IDE startup"
    - [ ] Добавить кнопку "View Logs"
    - [x] Добавить секцию с доступными tools
- [x] Создать панель с разворачиваемым списком tools
    - [x] Показывать имя tool
    - [x] Показывать описание
    - [x] Показывать параметры
    - [ ] Добавить кнопку "Test Tool"
- [x] Зарегистрировать в Settings

### 3. MCP Client для взаимодействия с сервером

- [x] Создать `MCPClient` класс
    - [x] Реализовать методы для всех file operations
    - [x] Добавить обработку ошибок
    - [x] Реализовать retry логику (в HTTP client)
    - [x] Добавить timeout настройки
- [x] Создать модели данных
    - [x] `FileRequest`, `FileResponse`
    - [x] `DirectoryRequest`, `DirectoryResponse`
    - [x] `MCPError`, `MCPException`
- [ ] Добавить unit тесты для MCPClient

### 4. Интеграция с Yandex GPT Tools API

- [x] Создать `YandexGPTToolsProvider`
    - [x] Реализовать преобразование MCP tools в Yandex format
    - [x] Добавить поддержку `tools` поля в запросе
    - [x] Реализовать обработку `toolCallList` в ответе
    - [x] Реализовать отправку `toolResultList` обратно в LLM
- [x] Обновить `YandexGPTProvider` (создан новый YandexGPTToolsProvider)
    - [x] Добавить поле `tools` в запрос
    - [x] Добавить поле `toolChoice` для управления вызовами
    - [x] Добавить поле `parallelToolCalls`
    - [x] Обработать `toolCallList` в ответе
- [x] Создать модели для Tools API
    - [x] `Tool`, `FunctionTool`
    - [x] `ToolCall`, `FunctionCall`
    - [x] `ToolResult`, `FunctionResult`
    - [x] `ToolChoice`

### 5. Интеграция MCP Tools в ChatAgent

- [x] Создать `MCPToolsRegistry`
    - [x] Автоматическое обнаружение доступных tools
    - [x] Кеширование списка tools (статический объект)
    - [ ] Обновление при перезапуске сервера
- [x] Создать `MCPToolExecutor`
    - [x] Парсинг `FunctionCall` из LLM ответа
    - [x] Вызов соответствующего MCP endpoint
    - [x] Обработка результата
    - [x] Формирование `ToolResult` для LLM
- [x] Создать `ChatAgentWithTools`
    - [x] Добавить поддержку tools в контекст
    - [x] Реализовать цикл tool calling:
        1. Отправить запрос с tools
        2. Получить ответ с toolCallList
        3. Выполнить tool calls через MCPToolExecutor
        4. Отправить toolResultList обратно в LLM
        5. Получить финальный ответ
    - [x] Добавить ограничение на количество итераций (max 5)
    - [x] Добавить логирование tool calls

### 6. Маппинг MCP Operations на Yandex Tools

- [x] Создать tool definitions для file operations:
    - [x] `create_file` - создание файла
    - [x] `read_file` - чтение файла
    - [x] `update_file` - обновление файла
    - [x] `delete_file` - удаление файла
    - [x] `list_files` - список файлов
- [x] Создать tool definitions для directory operations:
    - [x] `create_directory` - создание директории
    - [x] `delete_directory` - удаление директории
    - [x] `list_directory` - список содержимого
- [x] Добавить JSON Schema для параметров каждого tool

### 7. Обработка Tool Calls

- [x] Создать `MCPToolExecutor` (объединен с ToolCallHandler)
    - [x] Валидация параметров tool call
    - [x] Маппинг на MCP API endpoints
    - [x] Выполнение HTTP запроса к MCP серверу
    - [x] Обработка ошибок MCP сервера
    - [x] Форматирование результата для LLM
- [ ] Добавить безопасность
    - [ ] Проверка разрешенных операций
    - [ ] Валидация путей (делается в MCP Server)
    - [ ] Ограничение размера файлов (делается в MCP Server)
    - [ ] Rate limiting для tool calls

### 8. UI индикация Tool Calls

- [x] Обновить `ChatPanel`
    - [x] Показывать индикатор "Executing tool..."
    - [x] Отображать имя вызываемого tool
    - [ ] Показывать параметры (опционально)
    - [x] Отображать результат tool call (в метаданных)
- [x] Добавить иконки для разных типов операций
    - [x] 🔧 для tool execution
    - [ ] 📄 для file operations
    - [ ] 📁 для directory operations
    - [ ] ⚠️ для ошибок

### 9. Тестирование

- [ ] Unit тесты
    - [ ] `MCPServerManagerTest` - тесты жизненного цикла
    - [ ] `MCPClientTest` - тесты API клиента
    - [ ] `YandexGPTToolsProviderTest` - тесты преобразования tools
    - [ ] `MCPToolExecutorTest` - тесты выполнения tools
    - [ ] `ToolCallHandlerTest` - тесты обработки calls
- [ ] Integration тесты
    - [ ] Тест полного цикла: запрос → tool call → результат
    - [ ] Тест множественных tool calls
    - [ ] Тест обработки ошибок
    - [ ] Тест timeout и retry
- [ ] UI тесты
    - [ ] Тест отображения статуса сервера
    - [ ] Тест разворачивания списка tools
    - [ ] Тест индикации tool calls в чате

### 10. Документация

- [ ] Обновить README.md
    - [ ] Добавить секцию "MCP Server Integration"
    - [ ] Описать автоматическую установку
    - [ ] Добавить примеры использования tools
- [ ] Создать docs/mcp-integration.md
    - [ ] Архитектура интеграции
    - [ ] Описание tool calling flow
    - [ ] Примеры запросов с tools
- [ ] Обновить CHANGELOG.md
- [ ] Добавить примеры в docs/examples/

### 11. CI/CD для MCP Server

- [ ] Создать GitHub Actions workflow
    - [ ] Сборка бинарников для всех платформ
    - [ ] Публикация в GitHub Releases
    - [ ] Автоматическое обновление версии
- [ ] Настроить автоматическую сборку при push в main

## Архитектура

```
┌─────────────────────────────────────────────┐
│              ChatAgent                      │
│  - Обработка user message                  │
│  - Формирование запроса с tools             │
│  - Обработка tool calls                     │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│         YandexGPTToolsProvider              │
│  - Преобразование MCP tools → Yandex format │
│  - Отправка запроса с tools                 │
│  - Парсинг toolCallList                     │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│            MCPToolExecutor                  │
│  - Выполнение tool calls                    │
│  - Вызов MCPClient                          │
│  - Формирование toolResultList              │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│              MCPClient                      │
│  - HTTP запросы к MCP Server                │
│  - Обработка ответов                        │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│           MCP Server (Rust)                 │
│  - File system operations                   │
│  - REST API                                 │
└─────────────────────────────────────────────┘
```

## Tool Calling Flow

```
1. User: "Создай файл test.txt с содержимым Hello"
   ↓
2. ChatAgent формирует запрос с tools:
   {
     "messages": [...],
     "tools": [
       {
         "function": {
           "name": "create_file",
           "description": "Create a new file",
           "parameters": {...}
         }
       }
     ]
   }
   ↓
3. Yandex GPT возвращает:
   {
     "toolCallList": {
       "toolCalls": [
         {
           "functionCall": {
             "name": "create_file",
             "arguments": {
               "path": "test.txt",
               "content": "Hello"
             }
           }
         }
       ]
     }
   }
   ↓
4. MCPToolExecutor выполняет:
   POST http://localhost:3000/files
   {
     "path": "test.txt",
     "content": "Hello",
     "overwrite": false
   }
   ↓
5. MCP Server возвращает:
   {
     "path": "test.txt",
     "size": 5,
     "checksum": "..."
   }
   ↓
6. ChatAgent отправляет toolResultList:
   {
     "messages": [
       ...,
       {
         "role": "assistant",
         "toolCallList": {...}
       },
       {
         "role": "tool",
         "toolResultList": {
           "toolResults": [
             {
               "functionResult": {
                 "name": "create_file",
                 "content": "File created successfully"
               }
             }
           ]
         }
       }
     ]
   }
   ↓
7. Yandex GPT возвращает финальный ответ:
   "Файл test.txt создан успешно с содержимым 'Hello'"
```

## Пример Tool Definition

```json
{
  "function": {
    "name": "create_file",
    "description": "Create a new file with specified content",
    "parameters": {
      "type": "object",
      "properties": {
        "path": {
          "type": "string",
          "description": "Relative path to the file"
        },
        "content": {
          "type": "string",
          "description": "Content of the file"
        },
        "overwrite": {
          "type": "boolean",
          "description": "Whether to overwrite existing file",
          "default": false
        }
      },
      "required": [
        "path",
        "content"
      ]
    },
    "strict": true
  }
}
```

## Приоритет задач

1. **High Priority** (Критично для работы)
    - Задачи 1, 3, 4, 5 - Основная функциональность

2. **Medium Priority** (Важно для UX)
    - Задачи 2, 8 - UI и индикация

3. **Low Priority** (Можно отложить)
    - Задачи 9, 10, 11 - Тестирование и документация

## Оценка времени

- Задачи 1-3: ~8 часов
- Задачи 4-5: ~6 часов
- Задачи 6-7: ~4 часа
- Задача 8: ~2 часа
- Задачи 9-11: ~4 часа

**Итого: ~24 часа**

## Риски и митигация

| Риск                             | Вероятность | Влияние     | Митигация                      |
|----------------------------------|-------------|-------------|--------------------------------|
| MCP Server не запускается        | Средняя     | Высокое     | Fallback на скачивание/сборку  |
| Yandex GPT не поддерживает tools | Низкая      | Критическое | Проверить документацию заранее |
| Tool calls работают медленно     | Средняя     | Среднее     | Добавить кеширование, async    |
| Проблемы с безопасностью         | Средняя     | Высокое     | Валидация, rate limiting       |

## Критерии успеха

- ✅ MCP Server автоматически устанавливается и запускается
- ✅ Статус сервера отображается в настройках
- ✅ Список tools доступен и разворачивается
- ✅ ChatAgent успешно вызывает tools через Yandex GPT
- ✅ Tool calls отображаются в UI
- ✅ Все тесты проходят
- ✅ Документация обновлена

## Следующие шаги после завершения

1. Добавить больше tools (search, git operations)
2. Реализовать кеширование результатов
3. Добавить метрики и мониторинг
4. Оптимизировать производительность
5. Добавить поддержку custom tools от пользователя
