# Архитектура плагина Ride - Краткое резюме

## 🎯 Основная концепция

Плагин для IntelliJ IDEA с AI-чатом, использующим Yandex GPT (с возможностью расширения на другие LLM).

## 🏗️ Архитектурные слои

```
┌─────────────────────────────────────┐
│         UI Layer                    │  ← ChatPanel, ChatToolWindow
├─────────────────────────────────────┤
│         Service Layer               │  ← ChatService, MessageHistory
├─────────────────────────────────────┤
│         Agent Layer                 │  ← Agent (interface), ChatAgent
├─────────────────────────────────────┤
│         Integration Layer           │  ← LLMProvider (interface), YandexGPTProvider
├─────────────────────────────────────┤
│         Configuration Layer         │  ← PluginSettings, SettingsConfigurable
└─────────────────────────────────────┘
```

## ✨ Ключевое архитектурное решение

### Agent НЕ привязан к конкретному LLM провайдеру!

**Было (неправильно):**
```
YandexGPTAgent → напрямую использует Yandex GPT API
```

**Стало (правильно):**
```
ChatAgent → использует LLMProvider (interface) → YandexGPTProvider
                                                → OpenAIProvider
                                                → любой другой провайдер
```

### Почему это важно?

1. **Гибкость**: Можно легко переключаться между LLM провайдерами
2. **Расширяемость**: Добавление нового провайдера не требует изменения агента
3. **Тестируемость**: Легко использовать mock провайдер для тестов
4. **SOLID принципы**: Dependency Inversion, Single Responsibility

## 📦 Основные компоненты

### Agent Layer

**Agent (interface)**
```kotlin
interface Agent {
    suspend fun processRequest(request: String, context: ChatContext): AgentResponse
    fun getName(): String
    fun getDescription(): String
}
```

**ChatAgent (implementation)**
- Универсальная реализация для чата
- Получает `LLMProvider` через конструктор (DI)
- Формирует промпты с учетом контекста
- Делегирует запросы в провайдер

### Integration Layer

**LLMProvider (interface)**
```kotlin
interface LLMProvider {
    suspend fun sendRequest(prompt: String, parameters: LLMParameters): LLMResponse
    fun isAvailable(): Boolean
    fun getProviderName(): String
}
```

**YandexGPTProvider (implementation)**
- HTTP клиент для Yandex GPT API
- Аутентификация через API ключ
- Retry логика и rate limiting

## 🔄 Поток данных

```
Пользователь вводит запрос
    ↓
ChatPanel
    ↓
ChatService
    ↓
ChatAgent.processRequest()
    ↓
ChatAgent.buildPrompt() (добавляет контекст)
    ↓
LLMProvider.sendRequest() (Yandex GPT или другой)
    ↓
Ответ возвращается через те же слои
    ↓
Отображается в ChatPanel
```

## 🚀 Точки расширения

### 1. Новый LLM провайдер
```kotlin
class OpenAIProvider : LLMProvider {
    override suspend fun sendRequest(prompt: String, parameters: LLMParameters): LLMResponse {
        // Интеграция с OpenAI API
    }
}
```

### 2. Новый тип агента
```kotlin
class CodeAnalysisAgent(private val llmProvider: LLMProvider) : Agent {
    override suspend fun processRequest(request: String, context: ChatContext): AgentResponse {
        // Специфичная логика для анализа кода
        val enhancedPrompt = buildCodeAnalysisPrompt(request, context)
        return llmProvider.sendRequest(enhancedPrompt, params)
    }
}
```

### 3. Новые фичи
- RAG для работы с кодовой базой
- Инструменты (tools) для агента
- Интеграция с IDE (рефакторинг, анализ)
- Streaming ответов
- История сессий

## 📁 Структура файлов

```
src/main/kotlin/ru/marslab/ide/ride/
├── agent/
│   ├── Agent.kt                    # Интерфейс
│   ├── AgentFactory.kt             # Фабрика
│   └── impl/
│       └── ChatAgent.kt            # Универсальная реализация
├── integration/llm/
│   ├── LLMProvider.kt              # Интерфейс
│   ├── LLMProviderFactory.kt       # Фабрика
│   └── impl/
│       └── YandexGPTProvider.kt    # Yandex GPT
├── model/
│   ├── Message.kt
│   ├── ChatContext.kt
│   ├── AgentResponse.kt
│   └── LLMParameters.kt
├── service/
│   ├── ChatService.kt
│   └── MessageHistory.kt
├── settings/
│   ├── PluginSettings.kt
│   └── SettingsConfigurable.kt
└── ui/
    ├── ChatToolWindowFactory.kt
    ├── ChatPanel.kt
    └── components/
```

## 🔒 Безопасность

- API ключи хранятся в `PasswordSafe` (IntelliJ Platform)
- Никогда не логируются чувствительные данные
- Валидация всех входных данных

## 📚 Документация

- **architecture.md** - Полная архитектура
- **project-structure.md** - Структура проекта
- **api-integration.md** - Интеграция с Yandex GPT API
- **sequence-diagrams.md** - Диаграммы последовательности
- **chat-agent-example.md** - Пример реализации ChatAgent
- **ARCHITECTURE_SUMMARY.md** - Этот документ

## ⚡ Технологический стек

- **Kotlin** 2.1.0
- **IntelliJ Platform** 2025.1.4.1
- **Kotlin Coroutines** для асинхронности
- **Ktor Client** для HTTP запросов
- **kotlinx.serialization** для JSON

## 🎯 Следующие шаги

1. ✅ Архитектура разработана и утверждена
2. ⏳ Реализация базовой инфраструктуры (интерфейсы, модели)
3. ⏳ Интеграция с Yandex GPT
4. ⏳ UI и полнофункциональный чат
5. ⏳ Расширенные возможности

---

**Дата создания:** 2025-10-01  
**Версия:** 1.0  
**Статус:** Готово к реализации
