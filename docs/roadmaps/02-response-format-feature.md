# Роадмап: Добавление функциональности форматирования ответов (Задача 2)

## Цель
Добавить в агента возможность задавать формат ответа (JSON, XML, Text) с заданной структурой. Агент должен формировать соответствующий промпт для LLM и возвращать распарсенный ответ в указанном формате.

## Архитектурные решения

### Ключевые изменения:
1. **LLM Provider как обязательный параметр** - уже реализовано через DI в конструкторе
2. **Методы для изменения провайдера** - добавить в интерфейс Agent
3. **Формат ответа** - новый enum и модели для описания структуры
4. **Парсинг ответов** - отдельный компонент для парсинга JSON/XML/Text

## Этапы реализации

### 1. Модели данных для форматирования
- [x] Создать enum `ResponseFormat` (JSON, XML, TEXT)
- [x] Создать data class `ResponseSchema` для описания структуры ответа
- [x] Создать sealed class `ParsedResponse` с подтипами (JsonResponse, XmlResponse, TextResponse)
- [x] Обновить `AgentResponse` для поддержки `ParsedResponse`

### 2. Расширение интерфейса Agent
- [x] Добавить метод `setLLMProvider(provider: LLMProvider)` для изменения провайдера
- [x] Добавить метод `getLLMProvider(): LLMProvider` для получения текущего провайдера
- [x] Добавить метод `setResponseFormat(format: ResponseFormat, schema: ResponseSchema?)` 
- [x] Добавить метод `getResponseFormat(): ResponseFormat`
- [x] Добавить метод `clearResponseFormat()` для сброса к дефолтному формату

### 3. Компонент для парсинга ответов
- [x] Создать интерфейс `ResponseParser`
- [x] Реализовать `JsonResponseParser` с использованием kotlinx.serialization
- [x] Реализовать `XmlResponseParser` с использованием подходящей библиотеки
- [x] Реализовать `TextResponseParser` для обычного текста
- [x] Создать `ResponseParserFactory` для получения нужного парсера

### 4. Компонент для генерации промптов
- [x] Создать `PromptFormatter` для формирования промптов с учетом формата
- [x] Добавить шаблоны промптов для JSON формата
- [x] Добавить шаблоны промптов для XML формата
- [x] Добавить шаблоны промптов для TEXT формата
- [x] Реализовать валидацию схемы перед отправкой

### 5. Обновление ChatAgent
- [x] Добавить поле `var llmProvider: LLMProvider` (изменяемое)
- [x] Добавить поле `responseFormat: ResponseFormat?` и `responseSchema: ResponseSchema?`
- [x] Реализовать методы для изменения провайдера
- [x] Реализовать методы для установки формата ответа
- [x] Обновить метод `buildPrompt()` для учета формата ответа
- [x] Добавить парсинг ответа в `processRequest()` в зависимости от формата
- [x] Обработать ошибки парсинга

### 6. Обновление AgentFactory
- [x] Обновить методы создания агентов с учетом новых параметров
- [x] Добавить методы для создания агентов с предустановленным форматом

### 7. Зависимости
- [x] Добавить kotlinx.serialization (если еще не добавлено)
- [x] Добавить библиотеку для парсинга XML (например, Jackson XML или kotlinx-serialization-xml)
- [x] Обновить build.gradle.kts

### 8. Unit тесты
- [x] Тесты для `ResponseFormat` и `ResponseSchema`
- [x] Тесты для `JsonResponseParser`
- [x] Тесты для `XmlResponseParser`
- [x] Тесты для `TextResponseParser`
- [x] Тесты для `PromptFormatter`
- [x] Тесты для обновленного `ChatAgent` с разными форматами
- [x] Тесты для изменения провайдера в runtime

### 9. Интеграционные тесты
- [x] Тест полного цикла: запрос → форматирование промпта → парсинг ответа (JSON)
- [x] Тест полного цикла для XML формата
- [x] Тест полного цикла для TEXT формата
- [x] Тест изменения провайдера в процессе работы
- [x] Тест изменения формата ответа в процессе работы

### 10. Документация
- [x] Обновить README с примерами использования форматирования
- [x] Создать USAGE_EXAMPLES.md с примерами для каждого формата
- [x] Добавить примеры схем для JSON и XML
- [x] Обновить CHANGELOG.md

### 11. Финализация
- [x] Прогнать все тесты
- [ ] Ручное тестирование в IDE
- [ ] Code review
- [x] Финальный коммит

## Примеры использования (для документации)

### JSON формат
```kotlin
val agent = AgentFactory.createChatAgent()
val schema = ResponseSchema.json("""
{
  "answer": "string",
  "confidence": "number",
  "sources": [{"string"}]
}

{
  "title": "string",
  "items": [
    { 
      "name": "string",
      "value_name": "String",
      "value": "number" 
    }
  ]
}
""")
agent.setResponseFormat(ResponseFormat.JSON, schema)
val response = agent.processRequest("Что такое Kotlin?", context)
val jsonResponse = response.parsedContent as JsonResponse
```

### XML формат
```kotlin
agent.setResponseFormat(ResponseFormat.XML, ResponseSchema.xml("""
<response>
  <answer>string</answer>
  <confidence>number</confidence>
</response>
"""))
```

### TEXT формат (по умолчанию)
```kotlin
agent.clearResponseFormat() // или setResponseFormat(ResponseFormat.TEXT, null)
```

## Критерии готовности

- [x] Интерфейс Agent расширен методами для работы с провайдером и форматом
- [x] ChatAgent поддерживает изменение провайдера в runtime
- [x] ChatAgent корректно формирует промпты для JSON формата
- [x] ChatAgent корректно формирует промпты для XML формата
- [x] ChatAgent корректно формирует промпты для TEXT формата
- [x] Ответы корректно парсятся в соответствующие типы
- [x] Обработка ошибок парсинга работает корректно
- [x] Все unit тесты проходят успешно
- [x] Все интеграционные тесты проходят успешно
- [ ] Документация обновлена с примерами

## Технические детали

### Библиотеки для парсинга:
- **JSON**: kotlinx.serialization-json (уже используется)
- **XML**: kotlinx-serialization-xml или Jackson XML
- **TEXT**: встроенные средства Kotlin

### Обработка ошибок:
- Валидация схемы перед отправкой запроса
- Обработка ошибок парсинга с понятными сообщениями
- Fallback к текстовому формату при ошибке парсинга

### Производительность:
- Кэширование скомпилированных схем
- Ленивая инициализация парсеров

---

**Дата создания:** 2025-10-02  
**Статус:** Завершено  
**Приоритет:** Высокий
