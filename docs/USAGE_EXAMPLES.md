# Примеры использования Ride

## 🚀 Быстрый старт

### 1. Первый запуск

После установки и настройки API ключа:

1. Откройте Tool Window **"Ride Chat"** (справа в IDE)
2. Вы увидите приветственное сообщение:
   ```
   ℹ️ 👋 Привет! Я AI-ассистент для разработчиков. Чем могу помочь?
   ```

### 2. Первый вопрос

Введите ваш вопрос и нажмите Enter:

```
👤 Вы: Привет! Что ты умеешь?

🤖 Ассистент: Привет! Я AI-ассистент для разработчиков. 
Я могу помочь вам с:
- Объяснением концепций программирования
- Анализом и отладкой кода
- Предложением лучших практик
- Ответами на вопросы о различных технологиях
- Помощью в решении проблем разработки

Просто задайте мне вопрос, и я постараюсь помочь!
```

## 💡 Примеры запросов

### Объяснение концепций

```
👤 Вы: Объясни, что такое корутины в Kotlin

🤖 Ассистент: Корутины в Kotlin - это легковесные потоки, которые 
позволяют писать асинхронный код в последовательном стиле.

Основные преимущества:
- Легковесность (можно создать тысячи корутин)
- Структурированная конкурентность
- Отмена и обработка ошибок
- Интеграция с suspend функциями

Пример использования:
```kotlin
suspend fun fetchData(): String {
    delay(1000) // Не блокирует поток
    return "Data"
}

fun main() = runBlocking {
    val data = fetchData()
    println(data)
}
```
```

### Помощь с отладкой

```
👤 Вы: Почему я получаю NullPointerException в этом коде?
val user = users.find { it.id == userId }
println(user.name)

🤖 Ассистент: NullPointerException возникает потому, что метод find() 
может вернуть null, если элемент не найден.

Решения:
1. Безопасный вызов:
```kotlin
println(user?.name)
```

2. Elvis оператор с значением по умолчанию:
```kotlin
val userName = user?.name ?: "Unknown"
```

3. Проверка на null:
```kotlin
if (user != null) {
    println(user.name)
}
```

4. Использование first() с обработкой исключения:
```kotlin
val user = users.firstOrNull { it.id == userId }
```
```

### Вопросы о лучших практиках

```
👤 Вы: Как правильно организовать архитектуру Android приложения?

🤖 Ассистент: Для Android приложений рекомендуется использовать 
Clean Architecture с MVVM паттерном:

Структура слоев:
1. Presentation Layer (UI)
   - Activity/Fragment
   - ViewModel
   - UI State

2. Domain Layer (Бизнес-логика)
   - Use Cases
   - Domain Models
   - Repository Interfaces

3. Data Layer (Данные)
   - Repository Implementations
   - Data Sources (Remote, Local)
   - Mappers

Преимущества:
- Разделение ответственности
- Легкость тестирования
- Независимость от фреймворков
- Масштабируемость
```

### Помощь с кодом

```
👤 Вы: Как сделать HTTP запрос в Kotlin?

🤖 Ассистент: Вот несколько способов сделать HTTP запрос в Kotlin:

1. С использованием Ktor Client:
```kotlin
val client = HttpClient(CIO)
val response: String = client.get("https://api.example.com/data")
```

2. С использованием Java HttpClient (JDK 11+):
```kotlin
val client = HttpClient.newHttpClient()
val request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/data"))
    .build()
val response = client.send(request, HttpResponse.BodyHandlers.ofString())
```

3. С использованием OkHttp:
```kotlin
val client = OkHttpClient()
val request = Request.Builder()
    .url("https://api.example.com/data")
    .build()
val response = client.newCall(request).execute()
```

Рекомендую использовать Ktor для Kotlin проектов - он идиоматичен 
и хорошо интегрируется с корутинами.
```

## 🔧 Продвинутые сценарии

### Работа с историей

История сохраняется автоматически в рамках сессии:

```
👤 Вы: Что такое sealed class в Kotlin?

🤖 Ассистент: Sealed class - это класс, который ограничивает иерархию 
наследования...

👤 Вы: Приведи пример использования

🤖 Ассистент: Вот пример sealed class для представления результата операции:
[Ассистент помнит контекст предыдущего вопроса о sealed class]
```

### Очистка истории

Если хотите начать новый диалог:

1. Нажмите кнопку **"Очистить"**
2. Подтвердите действие
3. История будет удалена

```
ℹ️ История чата очищена.
```

## ⚙️ Настройка поведения

### Изменение System Prompt

Вы можете настроить поведение ассистента через System Prompt:

1. Откройте **Settings → Tools → Ride**
2. Измените **System Prompt**, например:

```
Ты - эксперт по Kotlin и Android разработке.
Всегда предлагай современные подходы и лучшие практики.
Используй примеры кода с комментариями.
Будь кратким и конкретным.
```

### Настройка параметров генерации

**Temperature** (0.0 - 1.0):
- `0.3` - более точные и предсказуемые ответы
- `0.7` - сбалансированный режим (по умолчанию)
- `0.9` - более креативные ответы

**Max Tokens**:
- `1000` - короткие ответы
- `2000` - средние ответы (по умолчанию)
- `4000` - развернутые ответы

## 🐛 Обработка ошибок

### Ошибка: "Плагин не настроен"

```
❌ Ошибка: Плагин не настроен. Перейдите в Settings → Tools → Ride
```

**Решение**: Настройте API ключ и Folder ID в настройках.

### Ошибка: "Неверный API ключ"

```
❌ Ошибка: Неверный API ключ. Проверьте настройки.
```

**Решение**: Проверьте правильность API ключа в Yandex Cloud Console.

### Ошибка: "Превышен лимит запросов"

```
❌ Ошибка: Превышен лимит запросов. Попробуйте позже.
```

**Решение**: Подождите несколько минут или увеличьте лимиты в Yandex Cloud.

## 💡 Советы по использованию

### 1. Формулируйте вопросы четко

✅ **Хорошо**: "Как правильно обработать ошибки в Kotlin корутинах?"

❌ **Плохо**: "Ошибки"

### 2. Используйте контекст

Ассистент помнит предыдущие сообщения в рамках сессии:

```
👤 Вы: Расскажи про MVVM
🤖 Ассистент: [объяснение MVVM]

👤 Вы: А как это применить в Android?
🤖 Ассистент: [конкретно про Android, используя контекст]
```

### 3. Просите примеры кода

```
👤 Вы: Покажи пример использования Flow в Kotlin
```

### 4. Уточняйте детали

Если ответ недостаточно подробный:

```
👤 Вы: Объясни подробнее про StateFlow
```

## 🎯 Типичные сценарии использования

### Изучение нового фреймворка

```
1. Что такое Compose в Android?
2. Покажи простой пример Compose UI
3. Как управлять состоянием в Compose?
4. Какие есть лучшие практики для Compose?
```

### Отладка проблемы

```
1. Почему мое приложение крашится при повороте экрана?
2. Как правильно сохранять состояние Activity?
3. Покажи пример с ViewModel
```

### Рефакторинг кода

```
1. Как улучшить этот код? [вставить код]
2. Какие паттерны можно применить?
3. Как сделать код более тестируемым?
```

## 🧩 Форматирование ответов (JSON / XML / TEXT)

Ниже показано, как запросить у агента структурированный ответ и получить распарсенный объект с валидацией по схеме.

### JSON

```kotlin
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema
import ru.marslab.ide.ride.model.ParsedResponse

val agent = AgentFactory.createChatAgent()
val jsonSchema = ResponseSchema.json(
    """
    {
      "answer": "string",
      "confidence": 0.0,
      "sources": ["string"]
    }
    """.trimIndent(),
    description = "Структурируй ответ с оценкой уверенности и источниками"
)

agent.setResponseFormat(ResponseFormat.JSON, jsonSchema)
val response = agent.processRequest("Что такое Kotlin?", context)

when (val parsed = response.parsedContent) {
    is ParsedResponse.JsonResponse -> println(parsed.jsonElement)
    is ParsedResponse.ParseError -> println("Ошибка парсинга: ${parsed.error}")
    else -> println(response.content)
}
```

### XML

```kotlin
val xmlSchema = ResponseSchema.xml(
    """
    <response>
      <answer>string</answer>
      <confidence>number</confidence>
    </response>
    """.trimIndent(),
    description = "Ответ в виде XML"
)

agent.setResponseFormat(ResponseFormat.XML, xmlSchema)
val xmlResponse = agent.processRequest("Опиши Kotlin в 1-2 предложениях", context)
```

### TEXT (по умолчанию)

```kotlin
agent.clearResponseFormat() // Вернёмся к неструктурированному ответу
val textResponse = agent.processRequest("Приведи список ключевых преимуществ Kotlin", context)
println(textResponse.content)
```

## 📊 Ограничения

- История сохраняется только в рамках текущей сессии IDE
- Максимальная длина ответа ограничена настройкой Max Tokens
- Ассистент не имеет доступа к вашему коду (пока)
- Ответы генерируются на основе обучающих данных до определенной даты

## 🔮 Будущие возможности

В следующих версиях планируется:

- 📝 Автоматический анализ открытого файла
- 🔍 Работа с выделенным кодом
- 💾 Сохранение истории между сессиями
- 🎨 Markdown рендеринг в сообщениях
- 🤖 Специализированные агенты (Code Review, Refactoring)

---

**Есть вопросы?** Просто спросите ассистента! 😊
