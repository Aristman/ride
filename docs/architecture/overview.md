# 🏗️ Архитектура Ride

## Обзор

Ride построен на модульной архитектуре с четким разделением ответственности, основанной на принципах Clean Architecture и Dependency Inversion. Это обеспечивает гибкость, тестируемость и расширяемость системы.

## 🏛️ Слои архитектуры

```
┌─────────────────────────────────────────────┐
│                 UI Layer                    │  Swing UI, Tool Windows
│  ChatPanel, ChatToolWindow, Actions        │
├─────────────────────────────────────────────┤
│               Service Layer                 │  Application Services
│  ChatService, MessageHistory, Coordination │
├─────────────────────────────────────────────┤
│                Agent Layer                  │  Business Logic
│  Agent (interface), ChatAgent, Processing  │
├─────────────────────────────────────────────┤
│             Integration Layer               │  External Integrations
│  LLMProvider, YandexGPTProvider, HTTP      │
├─────────────────────────────────────────────┤
│            Configuration Layer              │  Settings & State
│  PluginSettings, Persistence, PasswordSafe │
└─────────────────────────────────────────────┘
```

## 🧩 Компоненты архитектуры

### 1. UI Layer (`ui/`)

**Ответственность**: Пользовательский интерфейс и взаимодействие

**Ключевые компоненты:**
- `ChatPanel` - основной компонент чата
- `ChatToolWindow` - окно инструмента в IntelliJ
- `Actions` - действия пользователя и меню

**Принципы:**
- Использование IntelliJ UI компонентов
- Корректная работа с EDT (Event Dispatch Thread)
- Асинхронная обработка запросов

```kotlin
class ChatPanel {
    private val chatService = ServiceManager.getService(ChatService::class.java)

    fun sendMessage(message: String) {
        CoroutineUtils.launch {
            val response = chatService.processMessage(message)
            EDT.invokeLater { displayResponse(response) }
        }
    }
}
```

### 2. Service Layer (`service/`)

**Ответственность**: Координация между слоями, бизнес-логика приложения

**Ключевые компоненты:**
- `ChatService` - центральный сервис чата
- `MessageHistory` - управление историей сообщений

**Принципы:**
- Application Services паттерн
- Координация между UI и Agent слоями
- Управление состоянием сессий

```kotlin
class ChatService {
    private val agent = AgentFactory.createChatAgent()
    private val messageHistory = MessageHistory()

    suspend fun processMessage(message: String): AgentResponse {
        val context = ChatContext(project, messageHistory.getRecentMessages())
        val response = agent.processRequest(message, context)
        messageHistory.addMessage(message, response.content)
        return response
    }
}
```

### 3. Agent Layer (`agent/`)

**Ответственность**: Обработка запросов и взаимодействие с AI

**Ключевые компоненты:**
- `Agent` - интерфейс агента
- `ChatAgent` - универсальная реализация
- `UncertaintyAnalyzer` - анализ неопределенности

**Принципы:**
- Dependency Inversion через интерфейсы
- Стратегии обработки различных типов запросов
- Интеграция с системой неопределенности

```kotlin
interface Agent {
    suspend fun processRequest(request: String, context: ChatContext): AgentResponse
    fun setResponseFormat(format: ResponseFormat, schema: ResponseSchema?)
    fun setLLMProvider(provider: LLMProvider)
}

class ChatAgent(
    private val llmProvider: LLMProvider,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) : Agent {
    // Реализация с анализом неопределенности
}
```

### 4. Integration Layer (`integration/llm/`)

**Ответственность**: Интеграция с внешними AI сервисами

**Ключевые компоненты:**
- `LLMProvider` - интерфейс провайдера
- `YandexGPTProvider` - реализация для Yandex GPT
- HTTP клиенты и API интеграция

**Принципы:**
- Абстракция над конкретными LLM провайдерами
- Стратегия для различных API
- Обработка ошибок и retry логика

```kotlin
interface LLMProvider {
    suspend fun sendRequest(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        parameters: LLMParameters
    ): LLMResponse

    fun isAvailable(): Boolean
    fun getProviderName(): String
}
```

### 5. Configuration Layer (`settings/`)

**Ответственность**: Конфигурация и персистентность

**Ключевые компоненты:**
- `PluginSettings` - настройки плагина
- Хранение API ключей через PasswordSafe
- Управление состоянием приложения

**Принципы:**
- Использование IntelliJ PersistentStateComponent
- Безопасное хранение чувствительных данных
- Валидация конфигурации

```kotlin
@State(
    name = "RideSettings",
    storages = [Storage("RideSettings.xml")]
)
class PluginSettings : PersistentStateComponent<PluginSettings> {
    var apiKey: String = ""
    var folderId: String = ""
    var temperature: Float = 0.7f

    // Безопасное хранение API ключей
    fun getSecureApiKey(): String = PasswordSafe.getInstance().getPassword(null, "ride")
}
```

## 🔄 Поток данных

### 1. Отправка сообщения

```
UI → ChatService → ChatAgent → LLMProvider → External API
```

```kotlin
// 1. UI Layer
chatPanel.sendMessage("Вопрос")

// 2. Service Layer
chatService.processMessage("Вопрос")

// 3. Agent Layer
agent.processRequest("Вопрос", context)

// 4. Integration Layer
llmProvider.sendRequest(systemPrompt, userMessage, history, parameters)
```

### 2. Получение ответа

```
External API → LLMProvider → ChatAgent → UncertaintyAnalyzer → ChatService → UI
```

```kotlin
// 1. Интеграция с API
val llmResponse = llmProvider.sendRequest(...)

// 2. Обработка в агенте
val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(llmResponse.content, context)
val isFinal = UncertaintyAnalyzer.isFinalResponse(uncertainty)

// 3. Формирование ответа
AgentResponse.success(
    content = llmResponse.content,
    isFinal = isFinal,
    uncertainty = uncertainty,
    metadata = mapOf("provider" to llmProvider.getProviderName())
)

// 4. Отображение в UI
ui.displayResponse(response)
```

## 🎨 Паттерны проектирования

### 1. Dependency Inversion Principle

```kotlin
// Высокоуровневые модули не зависят от низкоуровневых
class ChatAgent(private val llmProvider: LLMProvider) // Зависимость от абстракции
class ChatService(private val agent: Agent)           // Зависимость от абстракции
```

### 2. Factory Pattern

```kotlin
object AgentFactory {
    fun createChatAgent(provider: LLMProvider = YandexGPTProvider()): Agent {
        return ChatAgent(provider)
    }
}
```

### 3. Strategy Pattern

```kotlin
// Разные стратегии форматирования ответов
interface ResponseParser {
    fun parse(content: String, schema: ResponseSchema): ParsedResponse
}

class JsonResponseParser : ResponseParser { /* ... */ }
class XmlResponseParser : ResponseParser { /* ... */ }
class TextResponseParser : ResponseParser { /* ... */ }
```

### 4. Repository Pattern

```kotlin
class MessageHistory {
    private val messages = mutableListOf<Message>()

    fun addMessage(userMessage: String, assistantMessage: String)
    fun getRecentMessages(limit: Int = 5): List<Message>
    fun clear()
}
```

## 🔧 Технические решения

### 1. Управление зависимостями

```kotlin
// Dependency Injection через ServiceManager
class ChatPanel {
    private val chatService = ServiceManager.getService(ChatService::class.java)
}
```

### 2. Асинхронная обработка

```kotlin
// Использование корутин с EDT
fun sendMessage(message: String) {
    CoroutineUtils.launch {
        val response = chatService.processMessage(message)
        EDT.invokeLater { displayResponse(response) }
    }
}
```

### 3. Обработка ошибок

```kotlin
// Многоуровневая обработка ошибок
class ChatAgent {
    suspend fun processRequest(request: String, context: ChatContext): AgentResponse {
        return try {
            val llmResponse = llmProvider.sendRequest(...)
            processSuccessResponse(llmResponse)
        } catch (e: Exception) {
            logger.error("Error processing request", e)
            AgentResponse.error("Ошибка обработки запроса")
        }
    }
}
```

## 🧪 Тестирование архитектуры

### 1. Unit тесты

```kotlin
class ChatAgentTest {
    private val mockProvider = mockk<LLMProvider>()
    private val agent = ChatAgent(mockProvider)

    @Test
    fun `should process request successfully`() {
        // Arrange
        coEvery { mockProvider.sendRequest(...) } returns LLMResponse.success("Ответ")

        // Act
        val response = agent.processRequest("Вопрос", context)

        // Assert
        assertTrue(response.success)
        assertEquals("Ответ", response.content)
    }
}
```

### 2. Интеграционные тесты

```kotlin
class ChatServiceIntegrationTest {
    @Test
    fun `should coordinate all layers correctly`() {
        // Тестирование взаимодействия всех слоев
    }
}
```

## 🚀 Расширяемость

### 1. Добавление нового LLM провайдера

```kotlin
class OpenAIProvider : LLMProvider {
    override suspend fun sendRequest(...) = /* реализация */
    override fun isAvailable() = /* проверка доступности */
    override fun getProviderName() = "OpenAI"
}

// Регистрация в Factory
AgentFactory.registerProvider("openai") { OpenAIProvider() }
```

### 2. Добавление нового агента

```kotlin
class CodeAnalysisAgent(llmProvider: LLMProvider) : Agent {
    override suspend fun processRequest(...) = /* специализация */
}

// Использование
val agent = AgentFactory.createCodeAnalysisAgent()
```

## 📊 Метрики архитектуры

| Метрика | Значение | Описание |
|---------|----------|----------|
| **Слоев** | 5 | UI, Service, Agent, Integration, Configuration |
| **Компонентов** | 15+ | Основных компонентов в архитектуре |
| **Интерфейсов** | 8 | Контрактов для расширяемости |
| **Тестов** | 25+ | Unit и интеграционных тестов |
| **Покрытие** | 85%+ | Кодовое покрытие |

## 🔮 Будущие улучшения

- **Event-driven архитектура**: Уведомления между компонентами
- **CQRS**: Разделение команд и запросов
- **Domain Events**: События доменной области
- **Plugin system**: Динамическая загрузка расширений

---

*Документация обновлена: 2025-10-03*