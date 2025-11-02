
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

# Run A2A smoke tests (isolated headless)
./gradlew a2aTest

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

# Run specific A2A smoke test
./gradlew a2aTest --tests "A2AAgentsSmokeTest"
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
The `LLMProvider` interface uses a modern message-based API with full dialogue history support:
```kotlin
suspend fun sendRequest(
    systemPrompt: String,
    userMessage: String,
    conversationHistory: List<ConversationMessage>,
    parameters: LLMParameters
): LLMResponse
```

This design supports complete conversation history and multi-turn dialogues with role-based message representation.

### Uncertainty Analysis System
The plugin implements intelligent uncertainty analysis to determine when the AI should ask clarifying questions versus providing final answers:

#### Key Components
- **UncertaintyAnalyzer**: Pattern-based uncertainty detection with configurable threshold (default: 0.1)
- **ConversationMessage**: Structured representation of dialogue with roles (USER, ASSISTANT, SYSTEM)
- **AgentResponse**: Extended with uncertainty scores and final response flags
- **System Prompts**: Enhanced with uncertainty evaluation rules

#### Uncertainty Detection Logic
- **Pattern Matching**: Analyzes responses for uncertainty indicators in Russian
- **Question Detection**: Identifies clarifying questions in responses
- **Scoring Algorithm**: Normalized uncertainty calculation (0.0 - 1.0)
- **Threshold Logic**: If uncertainty > 0.1 → ask clarifying questions; ≤ 0.1 → provide final answer

#### Usage Example
```kotlin
val agent = AgentFactory.createChatAgent()
val response = agent.processRequest("Как оптимизировать код?", context)

if (response.isFinal) {
    // Окончательный ответ с высокой уверенностью
    println("Окончательный ответ: ${response.content}")
} else {
    // Требуются уточняющие вопросы
    println("Уточняющие вопросы: ${UncertaintyAnalyzer.extractClarifyingQuestions(response.content)}")
    println("Уровень неопределенности: ${response.uncertainty}")
}
```

## Module Structure

### Source Organization
```
src/main/kotlin/ru/marslab/ide/ride/
├── agent/              # Agent interfaces and implementations
│   ├── impl/           # Core agent implementations (ChatAgent, ToolAgent)
│   ├── tools/          # Specialized tool agents (A2A enabled)
│   ├── planner/        # Request planning agents
│   ├── rag/           # RAG (Retrieval-Augmented Generation) agents
│   └── a2a/           # A2A (Agent-to-Agent) protocol implementation
├── integration/llm/    # LLM provider abstractions and Yandex GPT
├── model/              # Data models and domain objects
├── service/            # Application services
│   ├── mcp/           # MCP (Model Context Protocol) integration
│   └── rag/           # RAG enrichment services
├── settings/           # Plugin configuration and persistence
├── ui/                 # Refactored UI components with composition pattern
│   ├── config/         # Configuration and constants (ChatPanelConfig)
│   ├── processor/      # Content processors (CodeBlockProcessor, MarkdownProcessor)
│   ├── renderer/       # Content renderers (ChatContentRenderer)
│   ├── manager/        # UI managers (HtmlDocumentManager, MessageDisplayManager)
│   ├── builder/        # UI builders (ChatUiBuilder)
│   └── chat/           # JCEF chat view
└── actions/            # IntelliJ platform actions
```

### Test Structure
```
src/test/kotlin/           # Standard unit tests
src/a2aTest/kotlin/        # Isolated A2A smoke tests (headless)
```

### Key Components
- **ChatAgent**: Universal agent implementation with uncertainty analysis and full dialogue history
- **YandexGPTProvider**: HTTP client for Yandex GPT API integration with conversation support
- **UncertaintyAnalyzer**: Pattern-based uncertainty detection and question extraction
- **ChatService**: Central service coordinating UI, agents, and message history
- **MessageHistory**: In-memory storage for chat conversations with role-based messages
- **PluginSettings**: Persistent configuration using IntelliJ's PersistentStateComponent
- **ChatPanel**: Main UI component with refactored architecture (235 lines vs 958)
- **EnhancedAgentOrchestratorA2A**: Advanced orchestration system for multi-agent workflows
- **MessageBus**: Event-driven communication system for agent-to-agent messaging
- **A2AAgentAdapter**: Universal adapter for integrating legacy agents with A2A protocol
- **RagEnrichmentService**: RAG (Retrieval-Augmented Generation) for context enrichment
- **MCPServerManager**: Management system for MCP (Model Context Protocol) servers

### Refactored UI Architecture (NEW)
The UI layer has been completely refactored following single responsibility principle:

#### Core UI Components
- **ChatPanelConfig**: Centralized configuration with constants, texts, icons, and language aliases
- **CodeBlockProcessor**: Handles code block processing (triple backticks, single backticks, inline code)
- **MarkdownProcessor**: Converts markdown to HTML (headers, lists, formatting)
- **ChatContentRenderer**: Unifies content rendering and HTML block creation
- **HtmlDocumentManager**: Manages HTML documents with JCEF and fallback support
- **MessageDisplayManager**: Coordinates message display and code block registration
- **ChatUiBuilder**: Builds UI components and handles input events

#### Component Composition Pattern
```kotlin
ChatPanel (coordinator)
├── ChatUiBuilder (UI components)
├── HtmlDocumentManager (HTML document)
├── MessageDisplayManager (message display)
└── ChatContentRenderer (content rendering)
    ├── CodeBlockProcessor (code processing)
    └── MarkdownProcessor (markdown processing)
```

## Technology Stack

- **Language**: Kotlin 2.1.0
- **Platform**: IntelliJ Platform 2024.2.5+
- **UI Framework**: Swing (IntelliJ UI components) with composition pattern
- **Async**: Kotlin Coroutines
- **HTTP**: Java HttpClient (JDK 21+) - *Note: Avoid Ktor due to coroutine conflicts*
- **JSON**: kotlinx.serialization 1.6.2
- **XML**: xmlutil for XML serialization
- **Tokenization**: jtokkit (Tiktoken implementation) for token counting
- **Database**: SQLite for RAG embeddings storage
- **Testing**: JUnit 5 + MockK + Mockito (mixed test suite)
- **Build**: Gradle 8.14.3 with IntelliJ Platform Gradle Plugin 2.7.1

## Development Guidelines

### Adding New LLM Providers
1. Implement `LLMProvider` interface
2. Update `AgentFactory` to support the new provider
3. Add configuration options to settings if needed

### Adding New Agents
1. Implement `Agent` interface
2. Use dependency injection for LLM provider
3. Register in `AgentFactory`

### UI Development (Post-Refactor)
- Use IntelliJ UI components (com.intellij.ui.*)
- Follow Swing threading rules - use `EDT` for UI operations
- Leverage composition pattern for building UI components
- Use specialized managers for different UI concerns
- Follow single responsibility principle for UI components

### A2A Agent Development
- Implement A2A message handling for Request/Response patterns
- Use `A2AAgentAdapter` for legacy agent integration
- Follow unified `TOOL_EXECUTION_REQUEST` protocol
- Include proper error handling and retry policies
- Add metadata tracking with `planId` for orchestration

### Testing Strategy
- Unit tests for all core components
- Mock external dependencies (LLM providers)
- Integration tests for response formatting and parsing
- UI tests for critical user interactions
- Uncertainty analysis tests with comprehensive coverage (12 tests)
- Conversation history validation tests
- Pattern matching validation for Russian language uncertainty indicators
- UI component tests for refactored architecture
- **A2A smoke tests**: Isolated headless testing with `./gradlew a2aTest`

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
- Component composition helps prevent memory leaks

### Gradle and IDE Configuration
- **JCEF Support**: Use JetBrains Runtime with JCEF, disable sandbox on Linux: `-Dide.browser.jcef.sandbox.enable=false`
- **Plugin Conflicts**: Gradle plugin may cause conflicts, disabled in sandbox config
- **Headless Testing**: A2A tests run in headless mode with proper flags

## RAG System (Retrieval-Augmented Generation)

### Overview
RAG provides context enrichment through local embedding database with semantic search and source linking capabilities.

### Core Components
- **RagEnrichmentService**: Main enrichment service with configurable strategies
- **SQLite Database**: Local storage for embeddings with optimized retrieval
- **Source Links**: Clickable links to source code in responses
- **Reranking Strategies**: THRESHOLD and MMR (Maximal Marginal Relevance)

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

### Usage
- Automatic enrichment when enabled
- Source links displayed as clickable "🔗 Открыть" elements
- Memory-optimized retrieval with top-N heap and pagination
- Detailed logging of strategy and metrics

## Plugin Configuration

The plugin is configured in `src/main/resources/META-INF/plugin.xml`:
- Tool Window: "Ride Chat" anchored to right side
- Settings: Available under Tools → Ride
- Application Services: ChatService, PluginSettings, MCPServerManager
- Actions: Response format configuration menu

## MCP Integration (Model Context Protocol)

### Overview
MCP enables extending plugin functionality through external servers via stdio and HTTP protocols.

### Core Components
- **MCPServerManager**: Management system for MCP servers
- **UI Integration**: Server management through settings interface
- **Method Calling**: Direct MCP server method invocation with parameters
- **Status Indicators**: Visual connection status in UI

### Configuration
Create `.ride/mcp.json` in project root:
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

### Usage
- Configure servers in Settings → Tools → Ride → MCP Servers
- Test connection and view available methods in MCP Methods Tool Window
- Direct method calling from agents with parameter passing
- Real-time status indicators for server connections

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

### UI Features (Post-Refactor)
- **Modular Design**: Each UI component has a single responsibility
- **Code Block Processing**: Advanced markdown and code block rendering with syntax highlighting
- **JCEF Support**: Enhanced HTML rendering with JCEF integration
- **Theme System**: Dynamic theme switching with proper CSS variable management
- **Session Management**: Clean separation of session handling in UI components

### Extensibility
- **Plugin architecture**: Easy addition of new formats and providers
- **Factory pattern**: Simplified extension of parsing and validation logic
- **Interface-based design**: Clean separation of concerns
- **Configuration-driven**: Runtime format switching without code changes
- **Component composition**: Easy to extend UI with new features

## A2A Protocol (Agent-to-Agent Communication)

### Overview
The A2A protocol enables sophisticated multi-agent workflows through event-driven communication using MessageBus architecture. It supports Request/Response/Event patterns with type-safe messaging.

### Core Protocol Messages
- **TOOL_EXECUTION_REQUEST**: Unified tool execution protocol
  ```json
  {
    "type": "TOOL_EXECUTION_REQUEST",
    "data": {
      "stepId": "<uuid>",
      "description": "<string>",
      "agentType": "<AgentType>",
      "input": {...},
      "dependencies": ["<stepId>"]
    }
  }
  ```
- **TOOL_EXECUTION_RESULT**: Tool execution response
- **Event Types**: STEP_STARTED, STEP_COMPLETED, STEP_FAILED, PLAN_EXECUTION_*

### A2A-Enabled Agents
- **A2AArchitectureToolAgent**: Architecture analysis
- **A2ALLMReviewToolAgent**: LLM code review
- **A2AEmbeddingIndexerToolAgent**: Embedding indexing
- **A2ACodeChunkerToolAgent**: Code chunking
- **A2AOpenSourceFileToolAgent**: File operations
- **A2AAgentAdapter**: Universal adapter for legacy agents

### Orchestration
- **EnhancedAgentOrchestratorA2A**: Advanced orchestration with retry policies, dependency management, and planId tracking
- **Data Flow**: Automatic step input enrichment from previous results
- **Error Handling**: Configurable retry policies with exponential backoff

### Testing
- **Isolated Tests**: `./gradlew a2aTest` for headless smoke testing
- **Coverage**: Comprehensive A2A smoke tests in `src/a2aTest/kotlin/`

### Current Status (Phase 1 Active)
Phase 0 infrastructure is complete. Phase 1 focuses on specialized A2A tool agents with unified TOOL_EXECUTION protocol support.

## Feature Roadmaps

### Uncertainty Analysis System
Документация по реализованной системе анализа неопределенности доступна в файле:
- **[UNCERTAINTY_ANALYSIS_ROADMAP.md](UNCERTAINTY_ANALYSIS_ROADMAP.md)** - Полный роадмап разработки с техническими деталями

**Краткая справка по использованию:**
```kotlin
// Проверка типа ответа
if (response.isFinal) {
    // Окончательный ответ - можно визуально выделить в UI
    displayFinalAnswer(response.content)
} else {
    // Уточняющие вопросы - показать индикатор неопределенности
    val questions = UncertaintyAnalyzer.extractClarifyingQuestions(response.content)
    displayClarifyingQuestions(questions, response.uncertainty)
}
```

### UI Refactoring Roadmap (COMPLETED)
UI refactoring was completed in 2025 with the following improvements:
- **75% reduction in code size**: ChatPanel reduced from 958 to 235 lines
- **Single Responsibility Principle**: Each class has one clear purpose
- **Improved Testability**: Smaller, focused components are easier to test
- **Enhanced Maintainability**: Changes in one area don't affect others
- **Better Reusability**: Components can be used in other parts of the application
- **Cleaner Architecture**: Clear separation between UI concerns
- **Component Composition**: Flexible building block approach for UI development

## Current Project Status

### Recently Completed (2025)
- ✅ **Uncertainty Analysis System** - Intelligent clarifying question detection
- ✅ **Agent Orchestrator** - Multi-agent workflow system with /plan mode
- ✅ **Token Management** - Automatic counting and history compression
- ✅ **Response Format System** - JSON/XML/TEXT with schema validation
- ✅ **MCP Integration** - External server connectivity
- ✅ **UI Architecture Refactoring** - Modular component-based design
- ✅ **A2A Protocol Phase 0** - Infrastructure and messaging system
- ✅ **RAG System** - Context enrichment with source links

### Active Development
- 🔄 **A2A Protocol Phase 1** - Specialized tool agents rollout (70% complete)
- 🔄 **Enhanced Agent Orchestration** - Advanced workflow management
- 📝 **Documentation Updates** - Comprehensive API and architecture guides

### Technology Maturity
- **Stable**: Core chat functionality, UI components, basic agent system
- **Mature**: Uncertainty analysis, token management, response formatting
- **Advanced**: A2A protocol, RAG system, MCP integration, orchestration

### Testing Coverage
- **40+ unit tests** covering core functionality
- **12 uncertainty analysis tests** with comprehensive pattern coverage
- **A2A smoke tests** for protocol validation
- **Integration tests** for end-to-end workflows