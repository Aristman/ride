# 📄 Форматы ответов API

## Обзор

Ride поддерживает структурированные ответы в различных форматах с возможностью валидации по схемам. Это позволяет получать от AI строго форматированные данные для дальнейшей обработки.

## 🎯 Поддерживаемые форматы

| Формат | Описание | Валидация | Парсинг |
|--------|----------|-----------|---------|
| **JSON** | Структурированные данные | ✅ По JSON схеме | ✅ Автоматический |
| **XML** | Иерархические данные | ✅ По XSD схеме | ✅ Автоматический |
| **TEXT** | Обычный текст | ❌ Нет | ❌ Не требуется |

## 🚀 Быстрый старт

### Настройка формата ответа

```kotlin
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema

val agent = AgentFactory.createChatAgent()

// Установка JSON формата с валидацией
val jsonSchema = ResponseSchema.json(
    """
    {
      "type": "object",
      "properties": {
        "answer": {"type": "string"},
        "confidence": {"type": "number", "minimum": 0, "maximum": 1},
        "sources": {
          "type": "array",
          "items": {"type": "string"}
        }
      },
      "required": ["answer", "confidence"]
    }
    """.trimIndent(),
    description = "Структурируй ответ, добавь confidence и источники"
)

agent.setResponseFormat(ResponseFormat.JSON, jsonSchema)
```

### Обработка ответа

```kotlin
val response = agent.processRequest("Что такое Kotlin?", context)

when (val parsed = response.parsedContent) {
    is ParsedResponse.JsonResponse -> {
        val json = parsed.jsonElement
        val answer = json["answer"]?.asString
        val confidence = json["confidence"]?.asDouble
        val sources = json["sources"]?.asJsonArray?.map { it.asString }

        println("Ответ: $answer")
        println("Уверенность: $confidence")
        println("Источники: $sources")
    }
    is ParsedResponse.XmlResponse -> {
        val xml = parsed.xmlDocument
        // Обработка XML
    }
    is ParsedResponse.ParseError -> {
        println("Ошибка парсинга: ${parsed.error}")
        println("Оригинальный ответ: ${parsed.originalContent}")
    }
    null -> {
        // TEXT формат или парсинг не настроен
        println(response.content)
    }
}
```

## 📋 JSON формат

### Настройка схемы

```kotlin
val userSchema = ResponseSchema.json(
    """
    {
      "type": "object",
      "properties": {
        "user": {
          "type": "object",
          "properties": {
            "name": {"type": "string"},
            "age": {"type": "integer", "minimum": 0},
            "skills": {
              "type": "array",
              "items": {"type": "string"}
            }
          },
          "required": ["name", "age"]
        },
        "recommendations": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "title": {"type": "string"},
              "priority": {"type": "string", "enum": ["high", "medium", "low"]}
            }
          }
        }
      },
      "required": ["user"]
    }
    """.trimIndent(),
    description = "Верни информацию о пользователе в JSON формате с рекомендациями"
)
```

### Пример ответа

```json
{
  "user": {
    "name": "John Doe",
    "age": 30,
    "skills": ["Kotlin", "Java", "JavaScript"]
  },
  "recommendations": [
    {
      "title": "Изучить Coroutines",
      "priority": "high"
    },
    {
      "title": "Практика с Spring Boot",
      "priority": "medium"
    }
  ]
}
```

## 📋 XML формат

### Настройка схемы

```kotlin
val xmlSchema = ResponseSchema.xml(
    """
    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
      <xs:element name="codeReview">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="file" maxOccurs="unbounded">
              <xs:complexType>
                <xs:sequence>
                  <xs:element name="path" type="xs:string"/>
                  <xs:element name="issues" type="xs:string"/>
                  <xs:element name="score" type="xs:integer" minOccurs="0"/>
                </xs:sequence>
              </xs:complexType>
            </xs:element>
          </xs:sequence>
        </xs:complexType>
      </xs:element>
    </xs:schema>
    """.trimIndent(),
    description = "Проанализируй код и верни результаты в XML формате"
)
```

### Пример ответа

```xml
<?xml version="1.0" encoding="UTF-8"?>
<codeReview>
  <file>
    <path>src/main/kotlin/UserService.kt</path>
    <issues>Нужно добавить валидацию входных данных</issues>
    <score>8</score>
  </file>
  <file>
    <path>src/main/kotlin/Repository.kt</path>
    <issues>Отсутствует обработка исключений</issues>
    <score>6</score>
  </file>
</codeReview>
```

## 📋 TEXT формат

TEXT формат используется по умолчанию и не требует настройки:

```kotlin
// TEXT формат по умолчанию
val agent = AgentFactory.createChatAgent()
val response = agent.processRequest("Объясни корутины", context)
println(response.content) // Обычный текстовый ответ
```

## 🔧 Расширенная конфигурация

### Динамическое переключение форматов

```kotlin
val agent = AgentFactory.createChatAgent()

// Запрос в формате JSON
agent.setResponseFormat(ResponseFormat.JSON, userSchema)
val jsonUserResponse = agent.processRequest("Опиши пользователя", context)

// Запрос в формате XML
agent.setResponseFormat(ResponseFormat.XML, xmlSchema)
val xmlReviewResponse = agent.processRequest("Проанализируй код", context)

// Возврат к TEXT формату
agent.clearResponseFormat()
val textResponse = agent.processRequest("Простой вопрос", context)
```

### Обработка ошибок парсинга

```kotlin
val response = agent.processRequest("Сложный запрос", context)

if (!response.success) {
    println("Ошибка запроса: ${response.error}")
    return
}

when (val parsed = response.parsedContent) {
    is ParsedResponse.ParseError -> {
        // Обработка ошибки парсинга
        println("Не удалось распарсить ответ: ${parsed.error}")

        // Можно использовать оригинальный контент
        processFallbackContent(parsed.originalContent)
    }
    else -> {
        // Успешное распарсивание
        processParsedResponse(parsed)
    }
}
```

## 🎨 UI Integration

### Валидация в реальном времени

```kotlin
class ResponseFormatValidator {
    fun validateResponse(response: AgentResponse): ValidationResult {
        return when (val parsed = response.parsedContent) {
            is ParsedResponse.ParseError -> {
                ValidationResult.Error(
                    message = "Ошибка формата: ${parsed.error}",
                    suggestions = generateSuggestions(parsed.error)
                )
            }
            else -> ValidationResult.Success
        }
    }

    private fun generateSuggestions(error: String): List<String> {
        return when {
            error.contains("required") -> listOf("Добавьте обязательные поля")
            error.contains("type") -> listOf("Проверьте типы данных")
            else -> listOf("Переформулируйте запрос")
        }
    }
}
```

### Визуализация структурированных данных

```kotlin
fun displayStructuredResponse(response: AgentResponse) {
    when (val parsed = response.parsedContent) {
        is ParsedResponse.JsonResponse -> {
            val json = parsed.jsonElement
            displayJsonTree(json)
        }
        is ParsedResponse.XmlResponse -> {
            val xml = parsed.xmlDocument
            displayXmlTree(xml)
        }
        else -> {
            displayPlainText(response.content)
        }
    }
}
```

## 🧪 Тестирование

### Unit тесты для парсинга

```kotlin
@Test
fun `should parse JSON response correctly`() {
    val agent = AgentFactory.createChatAgent()
    val schema = ResponseSchema.json(
        """
        {
          "type": "object",
          "properties": {
            "name": {"type": "string"},
            "value": {"type": "number"}
          }
        }
        """.trimIndent()
    )

    agent.setResponseFormat(ResponseFormat.JSON, schema)

    val mockResponse = """{"name": "test", "value": 42}"""
    val result = ResponseParserFactory.getParser(schema).parse(mockResponse, schema)

    assertTrue(result is ParsedResponse.JsonResponse)
    assertEquals("test", (result as ParsedResponse.JsonResponse).jsonElement["name"]?.asString)
}
```

## 🗂️ Project Scanner: JSON ответ

Агента `ProjectScannerToolAgent` возвращает стандартизированный JSON-объект в поле `json` и указывает `format = "JSON"` в `StepOutput`.

Структура:

```json
{
  "project": {
    "path": "string",
    "type": "string"
  },
  "batch": {
    "page": 1,
    "batch_size": 500,
    "total": 1234,
    "has_more": true
  },
  "files": ["path/to/file1.kt", "path/to/file2.kt"],
  "stats": { "total_files": 1234, "language_distribution": {"kt": 800} },
  "directories_total": 150,
  "tree_included": true,
  "directory_tree": { "path": "/...", "children": [] }
}
```

Параметры пакетной выдачи:

- `page`: номер страницы (по умолчанию 1)
- `batch_size`: размер пачки (по умолчанию 500)

Замечания:

- На страницах `page > 1` поле `directory_tree` пустое (`{}`), а `tree_included = false`.
- Для обратной совместимости дублируются поля верхнего уровня: `files`, `directory_tree`, `project_type`, `file_statistics`, `total_files`, `total_directories`, `from_cache`, `scan_time_ms`.

## 📊 Ограничения и рекомендации

### Ограничения

- **Размер схемы**: Максимальный размер схемы 10KB
- **Вложенность**: Максимальная глубина вложенности 10 уровней
- **Производительность**: Парсинг добавляет ~50-100ms к времени ответа

### Рекомендации

- **Простые схемы**: Используйте простые схемы для лучшей производительности
- **Обработка ошибок**: Всегда обрабатывайте `ParseError`
- **Fallback**: Иметь запасной план для TEXT формата
- **Кэширование**: Кэшируйте схемы при повторном использовании

## 🔮 Будущие улучшения

- **YAML формат**: Поддержка YAML с валидацией
- **Кастомные парсеры**: Возможность добавления своих парсеров
- **Шаблоны схем**: Предопределенные шаблоны для常见 случаев
- **Авто-дедукция**: Автоматическое определение формата из контекста

---

*Документация обновлена: 2025-10-03*