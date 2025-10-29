# 🚀 Ride - AI-ассистент для IntelliJ IDEA

<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Ride Logo" width="120" height="120">
</p>

<p align="center">
  <a href="#"><img src="https://img.shields.io/badge/build-passing-brightgreen" alt="Build Status"></a>
  <a href="#"><img src="https://img.shields.io/badge/version-1.0--SNAPSHOT-blue" alt="Version"></a>
  <a href="#"><img src="https://img.shields.io/badge/platform-IntelliJ%20IDEA-orange" alt="Platform"></a>
  <a href="#"><img src="https://img.shields.io/badge/kotlin-2.1.0-purple" alt="Kotlin"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="License"></a>
</p>

<p align="center">
  <strong>AI-ассистент для разработчиков с интеграцией Yandex GPT</strong><br>
  Помогает с вопросами о коде, отладке и разработке прямо в IDE
</p>

<p align="center">
  <a href="#-возможности">Возможности</a> •
  <a href="#-установка">Установка</a> •
  <a href="#️-настройка">Настройка</a> •
  <a href="#-использование">Использование</a> •
  <a href="#️-архитектура">Архитектура</a> •
  <a href="#-вклад-в-проект">Вклад</a>
</p>

---

## 📸 Демонстрация

```
┌─────────────────────────────────────────────┐
│  Ride Chat                              [×] │
├─────────────────────────────────────────────┤
│                                             │
│  👤 Вы:                                     │
│  Объясни, что такое корутины в Kotlin       │
│                                             │
│  🤖 Ассистент:                              │
│  Корутины в Kotlin - это легковесные        │
│  потоки, которые позволяют писать           │
│  асинхронный код в последовательном         │
│  стиле...                                   │
│                                             │
├─────────────────────────────────────────────┤
│  [Введите ваш вопрос...]        [Отправить] │
│                                  [Очистить] │
└─────────────────────────────────────────────┘
```

## ✨ Возможности

### 🤖 Интеллектуальный AI-ассистент
- 💬 **Интерактивный чат** с AI-ассистентом прямо в IDE
- 🤖 **Мультипровайдерная поддержка**: Yandex GPT и HuggingFace
- 🧠 **Интеллектуальный анализ неопределенности** - автоматически задает уточняющие вопросы
- 📝 **Полная история диалогов** с запоминанием контекста

### 🎯 Продвинутые возможности
- 🔄 **Режим /plan** - система двух агентов (PlannerAgent + ExecutorAgent) для сложных задач
- 🔍 **Code Analysis Agent** - автоматический анализ кода с поиском багов и оценкой качества
- 🔢 **Управление токенами** - автоматический подсчёт и сжатие истории при превышении лимита
- 📊 **Детальная статистика** - время ответа и использование токенов под каждым сообщением
- 🧩 **Форматирование ответов** (JSON/XML/TEXT) с парсингом и валидацией по схеме

### 🎨 Современный UI
- 🎨 **Пузырьковые сообщения** с визуальными индикаторами статуса
- 🎯 **Подсветка синтаксиса** кода через JCEF
- 📋 **Компактное отображение** уточняющих вопросов
- 🔄 **Системные уведомления** о сжатии истории и других событиях
- 🎭 **Форматированный вывод агентов** - стилизованные блоки для разных типов контента
- 💻 **Терминальные окна** с отображением команд, кодов выхода и времени выполнения
- 📦 **Форматированные блоки кода** с кнопками копирования и подсветкой синтаксиса
- 🔧 **Визуализация результатов инструментов** с индикаторами успеха/ошибки

### 🔧 Гибкая настройка
- 🔒 **Безопасное хранение** API ключей через PasswordSafe
- ⚙️ **Настройка лимитов токенов** (по умолчанию 8000)
- 🔄 **Автосжатие истории** через SummarizerAgent
- 🌡️ **Настройка температуры** и других параметров генерации

### 🔌 MCP Integration (NEW!)
- 🌐 **Подключение MCP серверов** через stdio и HTTP
- 🛠️ **Расширение функциональности** через внешние инструменты
- 📋 **Управление серверами** через удобный UI
- 🎯 **Вызов методов** MCP серверов с параметрами
- 📊 **Визуальные индикаторы** статуса подключения

### 🏗️ Архитектура
- ⚡ **Асинхронная обработка** запросов без блокировки UI
- 🏗️ **Модульная архитектура** с четким разделением ответственности
- 🧪 **Покрытие тестами** - 40+ unit-тестов для критичной функциональности
- 🔌 **Легкое расширение** - простое добавление новых LLM провайдеров

## 🎯 Быстрый старт

1. **Установите плагин** (см. [Установка](#-установка))
2. **Получите API ключ** от [Yandex Cloud](https://console.cloud.yandex.ru)
3. **Настройте плагин** в `Settings → Tools → Ride`
4. **Откройте чат** через `View → Tool Windows → Ride Chat`
5. **Задайте вопрос** и получите ответ от AI! 🚀

## 📋 Требования

- **IntelliJ IDEA** 2025.1.4.1 или выше
- **JDK** 21
- **Yandex Cloud** аккаунт с доступом к Yandex GPT API

## 🚀 Установка

### Из исходников

1. Клонируйте репозиторий:

```bash
git clone https://github.com/yourusername/ride.git
cd ride
```

2. Соберите плагин:

```bash
./gradlew buildPlugin
```

3. Установите плагин в IntelliJ IDEA:
    - `File → Settings → Plugins → ⚙️ → Install Plugin from Disk...`
    - Выберите файл `build/distributions/ride-1.0-SNAPSHOT.zip`

### Для разработки

Запустите плагин в тестовой IDE:

```bash
./gradlew runIde
```

## ⚙️ Настройка

### 1. Получите API ключ Yandex GPT

1. Перейдите в [Yandex Cloud Console](https://console.cloud.yandex.ru)
2. Создайте сервисный аккаунт
3. Создайте API ключ для сервисного аккаунта
4. Скопируйте **API ключ** и **Folder ID** из настроек проекта

### 2. Настройте плагин

1. Откройте **Settings → Tools → Ride**
2. Введите:
    - **API Key** - ваш API ключ от Yandex Cloud
    - **Folder ID** - ID папки из Yandex Cloud
3. (Опционально) Настройте:
    - **System Prompt** - системный промпт для агента
    - **Temperature** - температура генерации (0.0 - 1.0)
    - **Max Tokens** - максимальное количество токенов в ответе

4. Нажмите **Apply** и **OK**

### 3. RAG (Retrieval-Augmented Generation)

Плагин поддерживает обогащение запросов через локальную БД эмбеддингов (RAG). Включение и параметры:

```
Settings → Tools → Ride → RAG Enrichment
├── ☑ Включить обогащение запросов через RAG
├── Reranker Strategy: [THRESHOLD]
├── Top K: [5]
├── Candidate K: [30]
└── Similarity threshold: [0.25]
```

- Top K — сколько фрагментов оставить после фильтрации.
- Candidate K — сколько кандидатов получать на первом этапе семантического поиска.
- Similarity threshold — порог релевантности (0..1). Ниже порога отбрасывается.
- Strategy — текущая стратегия: THRESHOLD (фильтрация порогом). 

Примечание: поиск оптимизирован по памяти (top-N heap, постраничное чтение), поэтому рекомендуется держать разумные значения `Candidate K` (по умолчанию 30).

## 📖 Использование

### Открытие чата

1. Откройте Tool Window **"Ride Chat"** (обычно справа в IDE)
2. Или используйте: `View → Tool Windows → Ride Chat`

### Отправка сообщений

1. Введите ваш вопрос в поле ввода внизу окна чата
2. Нажмите **Enter** или кнопку **"Отправить"**
3. Дождитесь ответа от AI-ассистента

### Форматированные ответы (JSON / XML / TEXT)

Вы можете запросить у агента строго структурированный ответ и получить его уже распарсенным с проверкой по схеме.

Пример JSON:

```kotlin
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema
import ru.marslab.ide.ride.model.ParsedResponse

val agent = AgentFactory.createChatAgent()
val schema = ResponseSchema.json(
    """
    {
      "answer": "string",
      "confidence": 0.0,
      "sources": ["string"]
    }
    """.trimIndent(),
    description = "Структурируй ответ, добавь confidence и источники"
)

agent.setResponseFormat(ResponseFormat.JSON, schema)
val response = agent.processRequest("Что такое Kotlin?", context)

when (val parsed = response.parsedContent) {
    is ParsedResponse.JsonResponse -> println(parsed.jsonElement)
    is ParsedResponse.ParseError -> println("Ошибка парсинга: ${parsed.error}")
    else -> println(response.content)
}
```

Также поддерживается XML (схема-пример) и TEXT (по умолчанию). См. подробные примеры в [`docs/api/response-formats.md`](docs/api/response-formats.md).

### 🧠 Интеллектуальный анализ неопределенности

AI-ассистент автоматически анализирует свою уверенность в ответах:

```
👤 Вы: Помоги с кодом

🤖 Ассистент: Я не уверен, что правильно понял ваш вопрос.
   Уточните, пожалуйста, о чем именно идет речь?

👤 Вы: Мне нужно оптимизировать производительность Kotlin приложения

🤖 Ассистент: Окончательный ответ: вот полное решение вашей проблемы...
```

**Как это работает:**
- AI оценивает свою уверенность (0.0 - 1.0)
- Если уверенность > 0.1 → задает уточняющие вопросы
- Если уверенность ≤ 0.1 → дает окончательный ответ
- Полная история диалога запоминается для контекста
- Визуальные индикаторы (❓⚠️✅) показывают статус ответа
- Вопросы отображаются компактно для удобного чтения

### Примеры запросов

```
👤 Вы: Объясни, что такое корутины в Kotlin

🤖 Ассистент: Корутины в Kotlin - это легковесные потоки...

👤 Вы: Как правильно обработать ошибки в корутинах?

🤖 Ассистент: Для обработки ошибок в корутинах используйте...
```

### Режим /plan - Система двух агентов

Для сложных задач используйте команду `/plan`:

```
/plan создай REST API для управления задачами с CRUD операциями
```

**Как это работает:**
1. **PlannerAgent** создаёт структурированный план задач
2. **ExecutorAgent** выполняет каждую задачу по порядку
3. Результаты каждого шага отображаются в чате
4. Итоговая сводка с статистикой выполнения

**Пример вывода:**
```
📋 План создан (3 задачи)
⏱️ 1.23s | 🔢 450 токенов (↑400 ↓50)

✅ Задача 1: Создать модель данных
[результат выполнения]
⏱️ 2.45s | 🔢 1200 токенов (↑1000 ↓200)

✅ Задача 2: Реализовать контроллер
[результат выполнения]
⏱️ 1.87s | 🔢 980 токенов (↑800 ↓180)

✅ Выполнение завершено
Статистика:
- Всего задач: 3
- Успешно выполнено: 3
- Общее время: 5.55s
- Всего токенов: 2630 (↑2200 ↓430)
```

Подробнее: [docs/features/agent-orchestrator.md](docs/features/agent-orchestrator.md)

### 🎭 Форматированный вывод агентов

Новый функционал предоставляет стилизованный вывод для разных типов контента:

#### Терминальный вывод
```
🖥️ Terminal Output
┌─────────────────────────────────────────────┐
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

#### Блоки кода
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

#### Результаты инструментов
```
🔧 Выполнена операция с файлом:

┌─────────────────────────────────────────────┐
│ 📝 Создание файла                           │
├─────────────────────────────────────────────┤
│ Путь: src/main/kotlin/Example.kt           │
│ Статус: ✅ Успешно                         │
│ Размер: 245 байт                            │
└─────────────────────────────────────────────┘
```

**Поддерживаемые типы вывода:**
- 🖥️ **TERMINAL** - терминальные окна с командами
- 📦 **CODE_BLOCKS** - форматированные блоки кода
- 🔧 **TOOL_RESULT** - результаты вызова инструментов
- 📝 **MARKDOWN** - обычный текст с разметкой
- 🏗️ **STRUCTURED** - JSON/XML данные
- 🌐 **HTML** - готовый HTML контент

### Управление токенами

Плагин автоматически управляет контекстом диалога:

- **Подсчёт токенов** перед каждым запросом
- **Автосжатие истории** при превышении лимита (8000 токенов по умолчанию)
- **Детальная статистика** под каждым сообщением

**Настройка:**
```
Settings → Tools → Ride → Token Management
├── Max Context Tokens: [8000]
└── ☑ Включить автоматическое сжатие истории
```

**Системные уведомления:**
```
🔄 История диалога была сжата для экономии токенов (было: 9200 токенов)
```

Подробнее: [docs/features/token-management.md](docs/features/token-management.md)

### Очистка истории

Нажмите кнопку **"Очистить"** для удаления истории чата.

## 🏗️ Архитектура

Плагин построен на модульной архитектуре с четким разделением ответственности:

```
┌─────────────────────────────────────┐
│         UI Layer                    │  ChatPanel, ChatToolWindow, FormattedOutput
├─────────────────────────────────────┤
│         Service Layer               │  ChatService, MessageHistory
├─────────────────────────────────────┤
│         Agent Layer                 │  Agent, Formatters, OutputRenderers
├─────────────────────────────────────┤
│         Integration Layer           │  LLMProvider, YandexGPTProvider
├─────────────────────────────────────┤
│         Configuration Layer         │  PluginSettings
└─────────────────────────────────────┘
```

### Ключевые принципы

- ✅ **Dependency Inversion** - зависимости через интерфейсы
- ✅ **Single Responsibility** - каждый компонент имеет одну задачу
- ✅ **Open/Closed** - открыто для расширения, закрыто для модификации
- ✅ **Agent не привязан к LLM** - легко добавить новые провайдеры

Подробнее: [`docs/architecture/overview.md`](docs/architecture/overview.md)

## 🧪 Тестирование

### Запуск unit тестов

```bash
./gradlew test
```

### Покрытие тестами

- ✅ Модели данных (Message, ChatContext, LLMParameters)
- ✅ MessageHistory
- ✅ ChatAgent с mock провайдером
- ✅ Валидация параметров
- ✅ UncertaintyAnalyzer (12 тестов)
- ✅ Форматтеры вывода (TerminalOutputFormatter, CodeBlockFormatter)
- ✅ AgentOutputRenderer и FormattedOutput модели
- ✅ Интеграционные тесты форматированного вывода
- ✅ Интеграция анализа неопределенности с ChatAgent (6 тестов)
- ✅ Обработка полного диалогового контекста

## 🔧 Разработка

### Технологический стек

- **Язык**: Kotlin 2.1.0
- **Platform**: IntelliJ Platform 2024.3
- **HTTP Client**: Java HttpClient (JDK 21)
- **JSON/XML**: kotlinx.serialization + xmlutil
- **Токенизация**: jtokkit (Tiktoken)
- **Async**: Kotlin Coroutines (из IntelliJ Platform)
- **Testing**: JUnit 5, MockK
- **Build**: Gradle 8.14.3

### Структура проекта

```
ride/
├── src/main/kotlin/ru/marslab/ide/ride/
│   ├── agent/              # Агенты для обработки запросов
│   ├── integration/llm/    # Интеграция с LLM провайдерами
│   ├── model/              # Модели данных
│   ├── service/            # Бизнес-логика
│   ├── settings/           # Настройки плагина
│   └── ui/                 # Пользовательский интерфейс
├── src/test/kotlin/        # Unit тесты
└── docs/                   # Документация
    ├── features/           # Фичи и их роадмапы
    ├── api/                # API документация
    ├── architecture/       # Архитектурные документы
    └── guides/             # Руководства
```

### Добавление нового LLM провайдера

1. Реализуйте интерфейс `LLMProvider`:

```kotlin
class OpenAIProvider : LLMProvider {
    override suspend fun sendRequest(prompt: String, parameters: LLMParameters): LLMResponse {
        // Ваша реализация
    }
}
```

2. Обновите `AgentFactory` для создания агента с новым провайдером

3. Агент автоматически будет работать с новым провайдером!

## 🐛 Известные проблемы

### Конфликт корутин

**Проблема**: Не используйте Ktor Client - он вызывает конфликт корутин с IntelliJ Platform.

**Решение**: Используйте Java HttpClient или `com.intellij.util.io.HttpRequests`.

Подробнее в [docs/api-integration.md](docs/api-integration.md)

## 📝 Changelog

См. [CHANGELOG.md](CHANGELOG.md) для истории изменений.

## 🤝 Вклад в проект

Мы приветствуем вклад в проект! Пожалуйста:

1. Форкните репозиторий
2. Создайте ветку для вашей фичи (`git checkout -b feature/amazing-feature`)
3. Закоммитьте изменения (`git commit -m 'feat: add amazing feature'`)
4. Запушьте в ветку (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

## 🛠️ Технологический стек

| Категория       | Технология            | Версия     |
|-----------------|-----------------------|------------|
| **Язык**        | Kotlin                | 2.1.0      |
| **Platform**    | IntelliJ Platform     | 2025.1.4.1 |
| **HTTP Client** | Java HttpClient       | JDK 11+    |
| **JSON**        | kotlinx.serialization | 1.6.2      |
| **Async**       | Kotlin Coroutines     | Platform   |
| **Testing**     | JUnit + MockK         | Latest     |
| **Build**       | Gradle                | 8.14.3     |

## 📊 Статус проекта

- ✅ **Базовая функциональность** - Завершена
- ✅ **Анализ неопределенности** - Реализован
- ✅ **Agent Orchestrator** - Реализован (режим /plan)
- ✅ **Управление токенами** - Реализовано (подсчёт + автосжатие)
- ✅ **Мультипровайдерная поддержка** - Yandex GPT + HuggingFace
- ✅ **Форматирование ответов** - JSON/XML/TEXT
- ✅ **MCP Integration** - Реализована (stdio + HTTP)
- 📝 **Документация**: Полная и систематизированная
- 🧪 **Тесты**: 40+ unit-тестов
- 🐛 **Известные проблемы**: Нет критических

## 🔌 MCP Integration

Ride поддерживает интеграцию с MCP (Model Context Protocol) серверами для расширения функциональности.

### Быстрый старт с MCP

1. Создайте файл `.ride/mcp.json` в корне проекта:
```json
{
  "servers": [
    {
      "name": "filesystem",
      "type": "STDIO",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/directory"],
      "enabled": true
    }
  ]
}
```

2. Откройте **Settings → Tools → Ride → MCP Servers**
3. Нажмите **"Test Connection"** для проверки
4. Откройте **Tool Window → MCP Methods** для просмотра доступных методов

Подробнее: [docs/features/mcp-integration.md](docs/features/mcp-integration.md)

## 📄 Лицензия

Этот проект распространяется под лицензией MIT. См. файл [LICENSE](LICENSE) для деталей.

## 🙏 Благодарности

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html) - за отличную документацию
- [Yandex Cloud](https://cloud.yandex.ru/) - за Yandex GPT API
- [Kotlin](https://kotlinlang.org/) - за прекрасный язык программирования

## 📧 Контакты и поддержка

- 🐛 **Нашли баг?** [Создайте issue](https://github.com/yourusername/ride/issues)
- 💡 **Есть идея?** [Обсудите в Discussions](https://github.com/yourusername/ride/discussions)
- 📧 **Email**: your.email@example.com
- 🌐 **GitHub**: [@yourusername](https://github.com/yourusername)

## ⭐ Поддержите проект

Если вам понравился Ride, поставьте ⭐ на GitHub!

---

<p align="center">
  <strong>Сделано с ❤️ для разработчиков</strong><br>
  <sub>Ride - ваш AI-помощник в мире кода</sub>
</p>
