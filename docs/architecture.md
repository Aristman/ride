# Архитектура плагина Ride

## Обзор

Плагин Ride для IntelliJ IDEA предоставляет интерактивный чат с AI-агентом на базе Yandex GPT для помощи разработчикам.

## Основные компоненты

### 1. UI Layer (Слой пользовательского интерфейса)

#### 1.1 ChatToolWindow
- **Назначение**: Главное окно чата в IDE
- **Расположение**: `ui/ChatToolWindow.kt`
- **Ответственность**:
  - Отображение окна чата в Tool Window
  - Управление жизненным циклом UI компонентов
  - Интеграция с IntelliJ Platform

#### 1.2 ChatPanel
- **Назначение**: Панель с UI элементами чата
- **Расположение**: `ui/ChatPanel.kt`
- **Компоненты**:
  - `JBScrollPane` с историей сообщений
  - `JTextArea` для ввода запроса
  - `JButton` для отправки сообщения
- **Ответственность**:
  - Отображение истории чата
  - Обработка пользовательского ввода
  - Делегирование запросов в Service Layer

#### 1.3 MessageRenderer
- **Назначение**: Компонент для рендеринга сообщений
- **Расположение**: `ui/components/MessageRenderer.kt`
- **Ответственность**:
  - Форматирование сообщений пользователя и AI
  - Поддержка Markdown (для будущего расширения)
  - Визуальное разделение сообщений

### 2. Service Layer (Слой бизнес-логики)

#### 2.1 ChatService
- **Назначение**: Центральный сервис для управления чатом
- **Расположение**: `service/ChatService.kt`
- **Тип**: Application Service (Singleton)
- **Ответственность**:
  - Управление историей сообщений
  - Координация между UI и Agent
  - Управление состоянием чата

#### 2.2 MessageHistory
- **Назначение**: Хранение истории сообщений
- **Расположение**: `service/MessageHistory.kt`
- **Ответственность**:
  - Хранение сообщений в памяти
  - Предоставление API для доступа к истории
  - Возможность экспорта/импорта (для будущего расширения)

### 3. Agent Layer (Слой агентов)

#### 3.1 Agent (Interface)
- **Назначение**: Базовый интерфейс для всех агентов
- **Расположение**: `agent/Agent.kt`
- **Методы**:
  ```kotlin
  interface Agent {
      suspend fun processRequest(request: String, context: ChatContext): AgentResponse
      fun getName(): String
      fun getDescription(): String
  }
  ```

#### 3.2 ChatAgent
- **Назначение**: Универсальная реализация агента для общения с пользователем
- **Расположение**: `agent/impl/ChatAgent.kt`
- **Ответственность**:
  - Реализация интерфейса Agent
  - Получение запроса пользователя
  - Делегирование запроса в настроенный LLMProvider
  - Получение ответа от LLM и возврат в чат
  - НЕ привязан к конкретному провайдеру
- **Зависимости**:
  - `LLMProvider` (через конструктор/DI)

#### 3.3 AgentFactory
- **Назначение**: Фабрика для создания агентов
- **Расположение**: `agent/AgentFactory.kt`
- **Ответственность**:
  - Создание экземпляров агентов
  - Управление конфигурацией агентов
  - Возможность переключения между разными агентами

### 4. Integration Layer (Слой интеграции)

#### 4.1 LLMProvider (Interface)
- **Назначение**: Абстракция для работы с LLM провайдерами
- **Расположение**: `integration/llm/LLMProvider.kt`
- **Методы**:
  ```kotlin
  interface LLMProvider {
      suspend fun sendRequest(prompt: String, parameters: LLMParameters): LLMResponse
      fun isAvailable(): Boolean
      fun getProviderName(): String
  }
  ```

#### 4.2 YandexGPTProvider
- **Назначение**: Реализация провайдера для Yandex GPT
- **Расположение**: `integration/llm/impl/YandexGPTProvider.kt`
- **Ответственность**:
  - HTTP клиент для Yandex GPT API
  - Аутентификация и управление API ключами
  - Обработка rate limiting и retry логики

#### 4.3 LLMProviderFactory
- **Назначение**: Фабрика для создания LLM провайдеров
- **Расположение**: `integration/llm/LLMProviderFactory.kt`
- **Ответственность**:
  - Создание экземпляров провайдеров
  - Управление конфигурацией провайдеров

### 5. Configuration Layer (Слой конфигурации)

#### 5.1 PluginSettings
- **Назначение**: Хранение настроек плагина
- **Расположение**: `settings/PluginSettings.kt`
- **Тип**: Application Service (Persistent State Component)
- **Настройки**:
  - API ключ Yandex GPT
  - Выбранная модель
  - Параметры генерации (temperature, max_tokens)
  - Настройки UI

#### 5.2 SettingsConfigurable
- **Назначение**: UI для настроек плагина
- **Расположение**: `settings/SettingsConfigurable.kt`
- **Ответственность**:
  - Отображение настроек в IDE Settings
  - Валидация введенных данных
  - Сохранение настроек

### 6. Model Layer (Слой моделей данных)

#### 6.1 Message
- **Назначение**: Модель сообщения в чате
- **Расположение**: `model/Message.kt`
- **Поля**:
  - `id: String`
  - `content: String`
  - `role: MessageRole` (USER, ASSISTANT, SYSTEM)
  - `timestamp: Long`
  - `metadata: Map<String, Any>`

#### 6.2 ChatContext
- **Назначение**: Контекст для обработки запроса
- **Расположение**: `model/ChatContext.kt`
- **Поля**:
  - `project: Project`
  - `history: List<Message>`
  - `currentFile: VirtualFile?`
  - `selectedText: String?`

#### 6.3 AgentResponse
- **Назначение**: Ответ от агента
- **Расположение**: `model/AgentResponse.kt`
- **Поля**:
  - `content: String`
  - `success: Boolean`
  - `error: String?`
  - `metadata: Map<String, Any>`

## Диаграмма компонентов

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐   │
│  │ChatToolWindow│  │  ChatPanel   │  │MessageRenderer  │   │
│  └──────┬───────┘  └──────┬───────┘  └─────────────────┘   │
└─────────┼──────────────────┼──────────────────────────────┘
          │                  │
          ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                      Service Layer                          │
│  ┌──────────────┐  ┌──────────────────────────────────┐    │
│  │ ChatService  │  │      MessageHistory              │    │
│  └──────┬───────┘  └──────────────────────────────────┘    │
└─────────┼──────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                       Agent Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐   │
│  │Agent(I/F)    │◄─┤  ChatAgent   │  │  AgentFactory   │   │
│  └──────────────┘  └──────┬───────┘  └─────────────────┘   │
└─────────────────────────────┼──────────────────────────────┘
                              │ использует
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Integration Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐   │
│  │LLMProvider   │◄─┤YandexGPT     │  │LLMProvider      │   │
│  │  (I/F)       │  │Provider      │  │Factory          │   │
│  └──────────────┘  └──────────────┘  └─────────────────┘   │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                   Configuration Layer                       │
│  ┌──────────────┐  ┌──────────────────────────────────┐    │
│  │PluginSettings│  │    SettingsConfigurable          │    │
│  └──────────────┘  └──────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

## Поток данных

1. **Пользовательский запрос**:
   ```
   User Input → ChatPanel → ChatService → Agent → LLMProvider → Yandex GPT API
   ```

2. **Получение ответа**:
   ```
   Yandex GPT API → LLMProvider → Agent → ChatService → MessageHistory
                                                      → ChatPanel → UI Update
   ```

## Принципы архитектуры

### 1. Разделение ответственности (Separation of Concerns)
- Каждый слой имеет четко определенную ответственность
- UI не знает о деталях работы с API
- Agent не знает о деталях UI

### 2. Dependency Inversion
- Зависимости направлены на абстракции (интерфейсы)
- `Agent` и `LLMProvider` - это интерфейсы
- Легко заменить реализацию без изменения клиентского кода

### 3. Open/Closed Principle
- Система открыта для расширения, закрыта для модификации
- Новые агенты добавляются через реализацию интерфейса `Agent`
- Новые LLM провайдеры добавляются через `LLMProvider`

### 4. Single Responsibility
- Каждый класс имеет одну причину для изменения
- `ChatService` управляет чатом, но не знает о деталях UI или API

## Точки расширения

### 1. Добавление новых агентов
```kotlin
class CodeAnalysisAgent(private val llmProvider: LLMProvider) : Agent {
    override suspend fun processRequest(request: String, context: ChatContext): AgentResponse {
        // Специфичная логика для анализа кода
        val codeContext = extractCodeContext(context)
        val enhancedPrompt = "Analyze this code: $codeContext\n\nUser question: $request"
        
        val llmResponse = llmProvider.sendRequest(enhancedPrompt, LLMParameters())
        return AgentResponse(
            content = llmResponse.content,
            success = llmResponse.success
        )
    }
}
```

### 2. Добавление новых LLM провайдеров
```kotlin
class OpenAIProvider : LLMProvider {
    override suspend fun sendRequest(prompt: String, parameters: LLMParameters): LLMResponse {
        // Интеграция с OpenAI
    }
}
```

### 3. Расширение функциональности агента
- Добавление инструментов (tools) для агента
- Интеграция с IDE (анализ кода, рефакторинг)
- RAG (Retrieval-Augmented Generation) для работы с кодовой базой

### 4. Улучшение UI
- Поддержка Markdown в сообщениях
- Syntax highlighting для кода
- Кнопки быстрых действий (копировать, применить код)
- История сессий

### 5. Персистентность
- Сохранение истории чатов
- Экспорт/импорт диалогов
- Синхронизация между проектами

## Технологический стек

- **Язык**: Kotlin 2.1.0
- **Platform**: IntelliJ Platform 2025.1.4.1
- **UI**: Swing (IntelliJ UI Components)
- **Async**: Kotlin Coroutines
- **HTTP Client**: Ktor Client (рекомендуется) или OkHttp
- **JSON**: kotlinx.serialization
- **DI**: IntelliJ Platform Services (без внешних DI фреймворков)

## Безопасность

1. **API ключи**:
   - Хранение в `PasswordSafe` (IntelliJ Platform)
   - Никогда не логировать API ключи
   - Валидация перед использованием

2. **Данные пользователя**:
   - Опциональная отправка контекста кода
   - Настройка приватности в Settings
   - Предупреждение о передаче данных в облако

## Следующие шаги реализации

1. **Фаза 1**: Базовая инфраструктура
   - Создание интерфейсов и моделей данных
   - Настройка plugin.xml
   - Базовый UI с ChatToolWindow

2. **Фаза 2**: Интеграция с Yandex GPT
   - Реализация YandexGPTProvider
   - Реализация универсального ChatAgent
   - Настройки плагина

3. **Фаза 3**: Полнофункциональный чат
   - История сообщений
   - Улучшенный UI
   - Обработка ошибок

4. **Фаза 4**: Расширенные возможности
   - Интеграция с IDE
   - Дополнительные агенты
   - Персистентность
