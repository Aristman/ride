# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Ride** - это IntelliJ IDEA плагин для AI чат-ассистента с интеграцией Yandex GPT. Проект написан на Kotlin с использованием IntelliJ Platform SDK и следует принципам чистой архитектуры с модульной структурой.

## Common Development Commands

### Building and Testing
```bash
# Сборка плагина
./gradlew buildPlugin

# Запуск unit тестов
./gradlew test

# Запуск A2A smoke тестов (изолированные headless)
./gradlew a2aTest

# Запуск плагина в development IDE
./gradlew runIde

# Сборка и верификация плагина
./gradlew verifyPlugin

# Генерация coverage отчета
./gradlew test jacocoTestReport
```

### Single Test Execution
```bash
# Запуск конкретного тест-класса
./gradlew test --tests "ru.marslab.ide.ride.service.MessageHistoryTest"

# Запуск конкретного A2A теста
./gradlew a2aTest --tests "A2AAgentsSmokeTest"

# Запуск тестов с фильтром по названию
./gradlew test --tests "*UncertaintyAnalyzer*"

# Запуск с конкретной IDE продуктом
./gradlew runIde -PideProduct=AI -PideVersion=252.25557.131

# Запуск MCP сервера (если используется)
cd mcp-server-rust && docker-compose up
```

## Architecture Overview

Плагин следует слоистой архитектуре с четким разделением ответственности:

### Core Layers (Bottom to Top)
1. **Configuration Layer** - Настройки плагина и управление состоянием
2. **Integration Layer** - Абстракции и реализации LLM провайдеров
3. **Agent Layer** - Бизнес-логика обработки запросов
4. **Service Layer** - Сервисы приложения и координация
5. **UI Layer** - Swing компоненты с паттерном композиции

### Key Design Patterns
- **Dependency Inversion**: Зависимости через абстракции (интерфейсы)
- **Factory Pattern**: AgentFactory и LLMProviderFactory для создания объектов
- **Service Layer**: ChatService как центральный координатор
- **Repository Pattern**: MessageHistory для управления данными
- **Composition Pattern**: UI компоненты строятся через композицию

## Critical Architecture Principles

### Interface-Based Design
- `Agent` интерфейс: `src/main/kotlin/ru/marslab/ide/ride/agent/Agent.kt`
- `LLMProvider` интерфейс: `src/main/kotlin/ru/marslab/ide/ride/integration/llm/LLMProvider.kt`
- Все реализации инжектируются через фабрики

### Agent System
Основной интерфейс агента поддерживает:
- Обработку запросов с контекстом чата
- Настройку форматов ответов (JSON/XML/TEXT с валидацией схемы)
- Динамическое переключение LLM провайдеров
- Структурированный парсинг и валидацию ответов
- Анализ неопределенности и генерацию уточняющих вопросов

### LLM Provider Abstraction
Интерфейс `LLMProvider` использует современный message-based API:
```kotlin
suspend fun sendRequest(
    systemPrompt: String,
    userMessage: String,
    conversationHistory: List<ConversationMessage>,
    parameters: LLMParameters
): LLMResponse
```

Это поддерживает полную историю диалогов и multi-turn conversation.

### Uncertainty Analysis System
Плагин реализует интеллектуальный анализ неопределенности для определения, когда AI должен задавать уточняющие вопросы:

- **UncertaintyAnalyzer**: Pattern-based detection с настраиваемым порогом (default: 0.1)
- **Russian Language Support**: Специализированные паттерны для русского языка
- **Threshold Logic**: > 0.1 → уточняющие вопросы; ≤ 0.1 → окончательный ответ

## Module Structure

### Source Organization
```
src/main/kotlin/ru/marslab/ide/ride/
├── agent/              # Интерфейсы и реализации агентов
│   ├── impl/           # Основные реализации (ChatAgent, ToolAgent)
│   ├── tools/          # Специализированные агенты (A2A поддерживается)
│   ├── planner/        # Агенты планирования запросов
│   ├── rag/           # RAG (Retrieval-Augmented Generation) агенты
│   └── a2a/           # Реализация A2A протокола
├── integration/llm/    # LLM провайдеры и Yandex GPT интеграция
│   └── impl/           # Дополнительные провайдеры (Ollama, HuggingFace)
├── model/              # Модели данных и доменные объекты
│   ├── task/           # Модели задач для оркестрации
│   ├── scanner/        # Модели сканера кода
│   └── tool/           # Модели инструментов
├── service/            # Application сервисы
│   ├── mcp/           # MCP (Model Context Protocol) интеграция
│   ├── rag/           # RAG сервисы обогащения
│   └── testing/       # Testing инфраструктура
├── stt/                # Speech-to-Text система (распознавание голоса)
│   ├── domain/        # Domain слой STT
│   ├── infrastructure/ # Реализации STT сервисов
│   └── app/           # Прикладной слой STT
├── orchestrator/       # Оркестрация агентов
│   ├── impl/          # Конкретные реализации оркестрации
│   └── a2a/           # A2A оркестрация
├── settings/           # Конфигурация плагина и персистентность
├── ui/                 # Рефакторенные UI компоненты
│   ├── config/         # Конфигурация и константы (ChatPanelConfig)
│   ├── processor/      # Обработчики контента (CodeBlockProcessor, MarkdownProcessor)
│   ├── renderer/       # Рендереры контента (ChatContentRenderer)
│   ├── manager/        # UI менеджеры (HtmlDocumentManager, MessageDisplayManager)
│   ├── builder/        # UI билдеры (ChatUiBuilder)
│   ├── templates/      # Шаблонизация контента (HtmlTemplate, CodeBlockTemplate)
│   └── chat/           # JCEF чат view
└── actions/            # IntelliJ platform actions
```

### Test Structure
```
src/test/kotlin/           # Стандартные unit тесты (48 файлов)
src/a2aTest/kotlin/        # Изолированные A2A smoke тесты (headless)
```

### Key Components
- **ChatAgent**: Универсальная реализация с анализом неопределенности
- **YandexGPTProvider**: HTTP клиент для Yandex GPT API с поддержкой диалогов
- **UncertaintyAnalyzer**: Pattern-based detection и извлечение вопросов
- **ChatService**: Центральный сервис координации UI, агентов и истории
- **MessageHistory**: In-memory хранилище с role-based сообщениями
- **PluginSettings**: Persistent конфигурация через PersistentStateComponent
- **ChatPanel**: Основной UI компонент (235 строк vs 958 до рефакторинга)
- **EnhancedAgentOrchestratorA2A**: Продвинутая оркестрация multi-agent workflows
- **RagEnrichmentService**: RAG обогащение с source links
- **MCPServerManager**: Управление MCP серверами
- **YandexSpeechSttService**: Speech-to-Text сервис с Yandex SpeechKit интеграцией
- **TestingAgentOrchestrator**: Оркестратор тестирования с агентами для Kotlin/Java/Dart
- **EmbeddingGeneratorService**: Генерация эмбеддингов для RAG системы
- **EmbeddingDatabaseService**: SQLite база данных для хранения эмбеддингов
- **ResponseFormatter**: Форматирование ответов с шаблонизацией
- **RulesService**: Управление настраиваемыми правилами агентов
- **AgentFactory**: Фабрика для создания агентов с разными провайдерами
- **LLMProviderFactory**: Фабрика для создания LLM провайдеров

## Technology Stack

- **Language**: Kotlin 2.1.0
- **Platform**: IntelliJ Platform 2024.2.5+
- **UI Framework**: Swing (IntelliJ UI компоненты) с composition pattern и JCEF
- **Async**: Kotlin Coroutines
- **HTTP**: Java HttpClient (JDK 21+) - *Избегать Ktor из-за конфликтов корутин*
- **JSON**: kotlinx.serialization 1.6.2
- **XML**: xmlutil для XML сериализации
- **Tokenization**: jtokkit (Tiktoken implementation)
- **Database**: SQLite для RAG embeddings storage
- **Testing**: JUnit 5 + JUnit 4 + MockK + Mockito (mixed test suite)
- **Build**: Gradle 8.14.3 с IntelliJ Platform Gradle Plugin 2.7.1
- **Docker**: Для MCP сервера (Rust implementation)
- **JCEF**: Для современного UI с HTML рендерингом

## Development Guidelines

### Adding New LLM Providers
1. Реализуйте `LLMProvider` интерфейс
2. Обновите `AgentFactory` для поддержки нового провайдера
3. Добавьте опции конфигурации в настройки при необходимости

### Adding New Agents
1. Реализуйте `Agent` интерфейс
2. Используйте dependency injection для LLM провайдера
3. Зарегистрируйте в `AgentFactory`

### UI Development (Post-Refactor)
- Используйте IntelliJ UI компоненты (com.intellij.ui.*)
- Следуйте Swing threading правилам - используйте `EDT` для UI операций
- Применяйте composition pattern для построения UI компонентов
- Используйте специализированные менеджеры для разных UI задач
- Следуйте single responsibility principle для UI компонентов

### A2A Agent Development
- Реализуйте A2A message handling для Request/Response паттернов
- Используйте `A2AAgentAdapter` для интеграции legacy агентов
- Следуйте unified `TOOL_EXECUTION_REQUEST` протоколу
- Добавьте proper error handling и retry policies
- Включите metadata tracking с `planId` для оркестрации

## Important Constraints

### Coroutines and HTTP
**CRITICAL**: Не используйте Ktor Client - он вызывает конфликты корутин с IntelliJ Platform. Используйте Java HttpClient или `com.intellij.util.io.HttpRequests`.

### API Security
- API ключи хранятся в IntelliJ PasswordSafe
- Никогда не логируйте API ключи или чувствительные данные
- Валидируйте API ключи перед использованием

### Memory Management
- История сообщений хранится только in-memory
- Рассмотрите реализацию персистентности для истории чатов
- Мониторьте использование памяти с длинными диалогами
- Component composition помогает предотвратить memory leaks

### Gradle and IDE Configuration
- **JCEF Support**: Используйте JetBrains Runtime с JCEF, отключите sandbox на Linux: `-Dide.browser.jcef.sandbox.enable=false`
- **Plugin Conflicts**: Gradle plugin может вызывать конфликты, отключен в sandbox конфигурации
- **Headless Testing**: A2A тесты запускаются в headless режиме с proper flags
- **Продукт и версия IDE**: Задаются через `-PideProduct` и `-PideVersion` (пример: `-PideProduct=AI -PideVersion=252.25557.131`)

## Speech-to-Text (STT) System

### Architecture
STT система следует чистой архитектуре с тремя слоями:
- **Domain Layer**: `stt/domain/` - бизнес-логика и интерфейсы
- **Infrastructure Layer**: `stt/infrastructure/` - реализации (Yandex SpeechKit)
- **Application Layer**: `stt/app/` - использование в UI

### Key Components
- **YandexSpeechSttService**: Основная реализация STT
- **AudioRecorder**: Запись аудио с микрофона
- **SttConfiguration**: Настройки STT (API ключи, языки)
- **VoiceRecognition**: Интеграция с чат интерфейсом

### Usage in Development
STT интегрирован в чат интерфейс для голосового ввода сообщений.

## RAG System (Retrieval-Augmented Generation)

### Overview
RAG предоставляет обогащение контекста через локальную embedding базу с семантическим поиском и source linking.

### Core Components
- **RagEnrichmentService**: Основной сервис обогащения с настраиваемыми стратегиями
- **SQLite Database**: Локальное хранилище эмбеддингов с оптимизированным retrieval
- **Source Links**: Клиентабельные ссылки на исходный код в ответах
- **Reranking Strategies**: THRESHOLD и MMR (Maximal Marginal Relevance)

### Configuration
```
Settings → Tools → Ride → RAG Enrichment
├── ☑ Enable RAG enrichment
├── ☑ Enable source links in responses
├── Reranker Strategy: [THRESHOLD|MMR]
├── Top K: [5] (final results)
├── Candidate K: [30] (initial candidates)
├── Similarity threshold: [0.25]
└── MMR lambda: [0.5] (if MMR selected)
```

## Testing Infrastructure

### Overview
Плагин включает полноценную инфраструктуру для тестирования кода с AI-ассистентом.

### Key Components
- **TestingAgentOrchestrator**: Центральный оркестратор тестирования
- **KotlinTestingAgent**: Специализированный агент для Kotlin тестов
- **JavaTestingAgent**: Специализированный агент для Java тестов
- **DartTestingAgent**: Специализированный агент для Dart/Flutter тестов
- **TestRunner**: Запуск тестов и анализ результатов
- **TestGeneration**: LLM-генерация тестов с системными промптами

### Supported Test Types
- Unit тесты для Kotlin/Java/Dart
- Интеграционные тесты
- A2A smoke тесты (headless)
- Тесты производительности

### Development Workflow
1. Agent анализирует код и определяет необходимые тесты
2. Генерирует тестовый код с учетом фреймворков проекта
3. Запускает тесты и анализирует результаты
4. Предоставляет рекомендации по исправлению ошибок

## MCP Integration (Model Context Protocol)

### Configuration
Создайте `.ride/mcp.json` в корне проекта:
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

### MCP Server (Rust Implementation)
Отдельный MCP сервер написан на Rust:
- Расположен в `mcp-server-rust/`
- Собирается через Docker Compose
- Поддерживает stdio и HTTP коммуникацию
- Расширяет функциональность базового MCP протокола

## Response Format System

Поддержка структурированных ответов с валидацией:
- **JSON**: Schema validation с kotlinx.serialization
- **XML**: Schema validation с xmlutil
- **TEXT**: Plain text ответы (default)

### Usage Example
```kotlin
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
```

## A2A Protocol (Agent-to-Agent Communication)

### Overview
A2A протокол позволяет создавать сложные multi-agent workflows через event-driven коммуникацию с использованием MessageBus архитектуры.

### Core Protocol Messages
- **TOOL_EXECUTION_REQUEST**: Унифицированный протокол выполнения инструментов
- **TOOL_EXECUTION_RESULT**: Ответ выполнения инструмента
- **Event Types**: STEP_STARTED, STEP_COMPLETED, STEP_FAILED, PLAN_EXECUTION_*

### A2A-Enabled Agents
- **A2AArchitectureToolAgent**: Архитектурный анализ
- **A2ALLMReviewToolAgent**: LLM code review
- **A2AEmbeddingIndexerToolAgent**: Embedding индексация
- **A2ACodeChunkerToolAgent**: Чанкинг кода
- **A2AOpenSourceFileToolAgent**: Файловые операции
- **A2AAgentAdapter**: Универсальный адаптер для legacy агентов

### Testing
- **Isolated Tests**: `./gradlew a2aTest` для headless smoke тестирования
- **Coverage**: Комплексные A2A smoke тесты в `src/a2aTest/kotlin/`

## Advanced Features

### Rules Engine System
Плагин поддерживает настраиваемые правила для кастомизации поведения AI:
- **Global Rules**: `~/.ride/rules/` применяются ко всем проектам
- **Project Rules**: `<PROJECT>/.ride/rules/` применяются только к текущему проекту
- **UI Configuration**: Управление правилами через `Settings → Tools → Ride → Rules`
- **Priority System**: Проектные правила имеют высший приоритет
- **Template System**: Автоматическая генерация шаблонов правил

### Voice Input Integration
STT (Speech-to-Text) система интегрирована в чат интерфейс:
- Поддержка Yandex SpeechKit для распознавания речи
- Запись аудио直接 с микрофона
- Автоматическая конвертация речи в текст
- Поддержка русского и английского языков

### Template-Based UI Rendering
Система шаблонизации для различных типов контента:
- **HtmlTemplate**: Базовые HTML шаблоны
- **CodeBlockTemplate**: Форматированные блоки кода
- **TerminalOutputTemplate**: Терминальный вывод
- **StructuredBlockTemplate**: Структурированные данные
- **InteractionScriptsTemplate**: Скрипты взаимодействия

## Current Project Status

### Recently Completed (2025)
- ✅ **Uncertainty Analysis System** - Интеллектуальный анализ неопределенности
- ✅ **Agent Orchestrator** - Multi-agent workflow система с /plan режимом
- ✅ **Token Management** - Автоматический подсчет и сжатие истории
- ✅ **Response Format System** - JSON/XML/TEXT с валидацией схемы
- ✅ **MCP Integration** - Подключение внешних серверов
- ✅ **UI Architecture Refactoring** - Модульный component-based design
- ✅ **A2A Protocol Phase 0** - Инфраструктура и messaging система
- ✅ **RAG System** - Обогащение контекста с source links
- ✅ **STT System** - Speech-to-Text с Yandex SpeechKit
- ✅ **Testing Infrastructure** - AI-ассистент для тестирования
- ✅ **Rules Engine** - Настраиваемые правила поведения
- ✅ **Template System** - UI шаблонизация контента

### Active Development
- 🔄 **A2A Protocol Phase 1** - Специализированные tool агенты (70% complete)
- 🔄 **Enhanced Agent Orchestration** - Продвинутое управление workflow
- 🔄 **Advanced RAG Features** - Улучшение релевантности и производительности

### Testing Coverage
- **40+ unit тестов** для ключевой функциональности
- **12 uncertainty analysis тестов** с comprehensive pattern coverage
- **A2A smoke тесты** для валидации протокола
- **Интеграционные тесты** для end-to-end workflows
- **Testing Agent тесты** для валидации генерации тестов