
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Ride** is an IntelliJ IDEA plugin that provides an AI chat assistant integrated with Yandex GPT. It's a Kotlin-based project using the IntelliJ Platform SDK with a modular architecture following clean architecture principles.

## Common Development Commands

### Building and Testing
```bash
# Build the plugin
./gradlew buildPlugin

# Run unit tests
./gradlew test

# Run plugin in development IDE
./gradlew runIde

# Build and verify plugin
./gradlew verifyPlugin
```

### Single Test Execution
```bash
# Run specific test class
./gradlew test --tests "ru.marslab.ide.ride.service.MessageHistoryTest"

# Run tests with coverage
./gradlew test jacocoTestReport
```

## Architecture Overview

The plugin follows a layered architecture with clear separation of concerns:

### Core Layers (Bottom to Top)
1. **Configuration Layer** - Plugin settings and state management
2. **Integration Layer** - LLM provider abstractions and implementations
3. **Agent Layer** - Business logic for request processing
4. **Service Layer** - Application services and coordination
5. **UI Layer** - Swing-based user interface components

### Key Design Patterns
- **Dependency Inversion**: Dependencies flow through abstractions (interfaces)
- **Factory Pattern**: AgentFactory and LLMProviderFactory for object creation
- **Service Layer**: ChatService as central coordinator
- **Repository Pattern**: MessageHistory for data management

## Critical Architecture Principles

### Interface-Based Design
- `Agent` interface: `src/main/kotlin/ru/marslab/ide/ride/agent/Agent.kt`
- `LLMProvider` interface: `src/main/kotlin/ru/marslab/ide/ride/integration/llm/LLMProvider.kt`
- All implementations are dependency-injected through factories

### Agent System
The core agent interface supports:
- Request processing with chat context
- Response format configuration (JSON/XML/TEXT with schema validation)
- Dynamic LLM provider switching
- Structured response parsing and validation

### LLM Provider Abstraction
The `LLMProvider` interface uses a modern message-based API:
```kotlin
suspend fun sendRequest(
    systemPrompt: String,
    userMessage: String,
    assistantHistory: List<String>,
    parameters: LLMParameters
): LLMResponse
```

This design supports conversation history and multi-turn dialogues.

## Module Structure

### Source Organization
```
src/main/kotlin/ru/marslab/ide/ride/
├── agent/              # Agent interfaces and implementations
├── integration/llm/    # LLM provider abstractions and Yandex GPT
├── model/              # Data models and domain objects
├── service/            # Application services
├── settings/           # Plugin configuration and persistence
├── ui/                 # Swing UI components
└── actions/            # IntelliJ platform actions
```

### Key Components
- **ChatAgent**: Universal agent implementation that works with any LLM provider
- **YandexGPTProvider**: HTTP client for Yandex GPT API integration
- **ChatService**: Central service coordinating UI, agents, and message history
- **MessageHistory**: In-memory storage for chat conversations
- **PluginSettings**: Persistent configuration using IntelliJ's PersistentStateComponent

## Technology Stack

- **Language**: Kotlin 2.1.0
- **Platform**: IntelliJ Platform 2025.1.4.1
- **UI Framework**: Swing (IntelliJ UI components)
- **Async**: Kotlin Coroutines
- **HTTP**: Java HttpClient (JDK 11+) - *Note: Avoid Ktor due to coroutine conflicts*
- **JSON**: kotlinx.serialization 1.6.2
- **XML**: xmlutil for XML serialization
- **Testing**: JUnit + MockK

## Development Guidelines

### Adding New LLM Providers
1. Implement `LLMProvider` interface
2. Update `AgentFactory` to support the new provider
3. Add configuration options to settings if needed

### Adding New Agents
1. Implement `Agent` interface
2. Use dependency injection for LLM provider
3. Register in `AgentFactory`

### UI Development
- Use IntelliJ UI components (com.intellij.ui.*)
- Follow Swing threading rules - use `EDT` for UI operations
- Leverage `CoroutineUtils` for async operations

### Testing Strategy
- Unit tests for all core components
- Mock external dependencies (LLM providers)
- Integration tests for response formatting and parsing
- UI tests for critical user interactions

## Important Constraints

### Coroutines and HTTP
**CRITICAL**: Do not use Ktor Client - it causes coroutine conflicts with IntelliJ Platform. Use Java HttpClient or `com.intellij.util.io.HttpRequests` instead.

### API Security
- API keys stored in IntelliJ's PasswordSafe
- Never log API keys or sensitive data
- Validate API keys before use

### Memory Management
- Message history is stored in-memory only
- Consider implementing persistence for chat history
- Monitor memory usage with long conversations

## Plugin Configuration

The plugin is configured in `src/main/resources/META-INF/plugin.xml`:
- Tool Window: "Ride Chat" anchored to right side
- Settings: Available under Tools → Ride
- Application Services: ChatService and PluginSettings
- Actions: Response format configuration menu

## Response Format System

The plugin supports structured responses with validation:
- **JSON**: Schema validation with kotlinx.serialization
- **XML**: Schema validation with xmlutil
- **TEXT**: Plain text responses (default)

### Core Components
- **Response Models**: `ResponseFormat`, `ResponseSchema`, `ParsedResponse` (sealed class)
- **Processing Pipeline**: PromptFormatter → ResponseParser → ResponseValidator
- **Factory Pattern**: `ResponseParserFactory` and `ResponseValidatorFactory`

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

when (val parsed = response.parsedContent) {
    is ParsedResponse.JsonResponse -> println(parsed.jsonElement)
    is ParsedResponse.ParseError -> println("Ошибка парсинга: ${parsed.error}")
    else -> println(response.content)
}
```

Response format is configured per agent through the `setResponseFormat()` method with comprehensive error handling for parsing and validation failures.

## Advanced Features

### Response Format Actions
- **SetResponseFormatAction**: Unified dialog for format selection and schema input
- **Individual format actions**: Quick access to JSON/XML formats
- **ClearFormatAction**: Reset to default TEXT format
- **Integration with ChatService**: Centralized format management

### Error Handling
- **Parse errors**: Detailed error reporting with original content
- **Validation errors**: Schema validation with specific error messages
- **Provider errors**: Graceful fallback and user-friendly messages
- **Logging**: Comprehensive logging for debugging and monitoring

### Extensibility
- **Plugin architecture**: Easy addition of new formats and providers
- **Factory pattern**: Simplified extension of parsing and validation logic
- **Interface-based design**: Clean separation of concerns
- **Configuration-driven**: Runtime format switching without code changes