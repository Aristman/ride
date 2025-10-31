# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Ride** is an advanced IntelliJ IDEA plugin that provides an AI-powered development assistant with multi-agent orchestration, RAG (Retrieval-Augmented Generation), and MCP (Model Context Protocol) integration. It's a Kotlin-based project using the IntelliJ Platform SDK with a modular architecture following clean architecture principles, evolved from a simple chat assistant into a comprehensive AI development platform.

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
2. **Integration Layer** - LLM providers, MCP servers, and external integrations
3. **Agent Layer** - Multi-agent system with orchestration and tool agents
4. **Service Layer** - Application services, RAG, and coordination
5. **UI Layer** - Swing-based user interface with JCEF support

### Key Design Patterns
- **Dependency Inversion**: Dependencies flow through abstractions (interfaces)
- **Factory Pattern**: AgentFactory, LLMProviderFactory, and MCPClientFactory for object creation
- **Service Layer**: ChatService as central coordinator with RAG integration
- **Repository Pattern**: MessageHistory for data management
- **Orchestrator Pattern**: EnhancedAgentOrchestrator for multi-agent task coordination
- **Tool Agent Pattern**: Specialized agents for specific development tasks

## Critical Architecture Principles

### Interface-Based Design
- `Agent` interface: `src/main/kotlin/ru/marslab/ide/ride/agent/Agent.kt`
- `LLMProvider` interface: `src/main/kotlin/ru/marslab/ide/ride/integration/llm/LLMProvider.kt`
- All implementations are dependency-injected through factories

### Agent System
The core agent interface supports:
- Request processing with chat context and RAG enrichment
- Response format configuration (JSON/XML/TEXT with schema validation)
- Dynamic LLM provider switching
- Structured response parsing and validation
- Uncertainty analysis for clarifying question detection

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
│   ├── orchestrator/   # Multi-agent orchestration system
│   └── tool/           # Specialized tool agents
├── integration/llm/    # LLM provider abstractions and implementations
├── integration/mcp/    # MCP client and server management
├── model/              # Data models and domain objects
├── service/            # Application services and RAG system
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

### Key Components
- **EnhancedAgentOrchestrator**: Multi-agent orchestration system with plan state management
- **ChatAgent**: Universal agent implementation with uncertainty analysis and RAG integration
- **Tool Agents**: Specialized agents (ProjectScanner, CodeAnalysis, BugDetection, UserInteraction)
- **RagEnrichmentService**: RAG pipeline with retrieval → LLM reranking → MCP enrichment
- **MCPServerManager**: MCP client and server management with STDIO/HTTP support
- **YandexGPTProvider**: HTTP client for Yandex GPT API integration with conversation support
- **UncertaintyAnalyzer**: Pattern-based uncertainty detection and question extraction
- **ChatService**: Central service coordinating UI, agents, RAG, and orchestration
- **MessageHistory**: In-memory storage for chat conversations with role-based messages
- **PluginSettings**: Persistent configuration using IntelliJ's PersistentStateComponent
- **ChatPanel**: Main UI component with refactored architecture (235 lines vs 958)

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
- **Platform**: IntelliJ Platform 2024.2.5
- **UI Framework**: Swing (IntelliJ UI components) with JCEF support and composition pattern
- **Async**: Kotlin Coroutines
- **HTTP**: Java HttpClient (JDK 21+) - *Note: Avoid Ktor due to coroutine conflicts*
- **JSON**: kotlinx.serialization 1.6.2
- **XML**: xmlutil for XML serialization
- **Database**: SQLite for embedding index
- **Tokenization**: jtokkit for token counting
- **MCP**: Model Context Protocol client implementation
- **Testing**: JUnit + MockK

## Development Guidelines

### Adding New LLM Providers
1. Implement `LLMProvider` interface
2. Update `AgentFactory` to support the new provider
3. Add configuration options to settings if needed

### Adding New Tool Agents
1. Extend `ToolAgent` base class or implement `Agent` interface
2. Register in `ToolAgentRegistry`
3. Define tool capabilities and execution logic
4. Add to orchestrator's available tools

### Adding MCP Servers
1. Configure server in `.ride/mcp.json` or through UI
2. Server automatically appears in MCPServerManager
3. Tools are registered and available to agents
4. Supports both STDIO (local) and HTTP (remote) servers

### UI Development (Post-Refactor)
- Use IntelliJ UI components (com.intellij.ui.*)
- Follow Swing threading rules - use `EDT` for UI operations
- Leverage composition pattern for building UI components
- Use specialized managers for different UI concerns
- Follow single responsibility principle for UI components

### Testing Strategy
- Unit tests for all core components
- Mock external dependencies (LLM providers)
- Integration tests for response formatting and parsing
- UI tests for critical user interactions
- Uncertainty analysis tests with comprehensive coverage (12 tests)
- Conversation history validation tests
- Pattern matching validation for Russian language uncertainty indicators
- UI component tests for refactored architecture
- RAG pipeline integration tests
- MCP server integration tests

## Important Constraints

### Coroutines and HTTP
**CRITICAL**: Do not use Ktor Client - it causes coroutine conflicts with IntelliJ Platform. Use Java HttpClient or `com.intellij.util.io.HttpRequests` instead.

### API Security
- API keys stored in IntelliJ's PasswordSafe
- Never log API keys or sensitive data
- Validate API keys before use

### Memory Management
- Message history is stored in-memory only with automatic compression
- RAG system uses top-N heap and pagination for memory efficiency
- Component composition helps prevent memory leaks
- Automatic token counting and history compression (default 8000 tokens)
- Consider implementing persistence for chat history

## Plugin Configuration

The plugin is configured in `src/main/resources/META-INF/plugin.xml`:
- Tool Window: "Ride Chat" anchored to right side
- Settings: Available under Tools → Ride
- Application Services: ChatService, PluginSettings, MCPServerManager
- Actions: Response format configuration, MCP server management, DevTools
- Commands: `/plan` for orchestrator-initiated tasks

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

### RAG (Retrieval-Augmented Generation) System
- **Complete Pipeline**: Retrieval → LLM Reranking → MCP Enrichment
- **Semantic Search**: Embedding-based similarity search with configurable thresholds
- **Source Links**: Clickable links in chat responses for quick navigation to source code
- **Configuration**: `ragTopK` (1-10), `ragCandidateK` (30-100), `ragSimilarityThreshold`
- **Integration**: Automatic enrichment of user queries with project context

### Multi-Agent Orchestration
- **EnhancedAgentOrchestrator**: Coordinates multiple specialized agents
- **Tool Agents**: ProjectScanner, CodeAnalysis, BugDetection, UserInteraction
- **Plan State Machine**: Tracks execution progress and manages task decomposition
- **Interactive Execution**: Real-time progress updates and user feedback
- **Command Integration**: Use `/plan` in chat to initiate orchestrated tasks

### MCP (Model Context Protocol) Integration
- **Server Management**: MCPServerManager handles multiple MCP servers
- **Client Types**: STDIO (local) and HTTP (remote) server support
- **Configuration**: `.ride/mcp.json` file or UI-based configuration
- **Tool Registry**: Automatic registration and availability of MCP tools
- **Headless Support**: Safe operation in environments without UI

### Response Format System
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
- **JCEF Support**: Enhanced HTML rendering with clickable source links
- **Theme System**: Dynamic theme switching with proper CSS variable management
- **Session Management**: Clean separation of session handling in UI components
- **Interactive Elements**: Progress indicators and status updates for tool agents

### Extensibility
- **Plugin architecture**: Easy addition of new formats, providers, and tool agents
- **Factory pattern**: Simplified extension of parsing and validation logic
- **Interface-based design**: Clean separation of concerns
- **Configuration-driven**: Runtime format switching without code changes
- **Component composition**: Easy to extend UI with new features
- **MCP Protocol**: Extensible through external MCP servers

## Recent Development Highlights

### Completed Major Features (2024-2025)

**Advanced RAG System (Phase 5 - Completed)**
- Complete RAG pipeline with semantic search, LLM reranking, and MCP enrichment
- Clickable source links for quick navigation to relevant code
- Configurable parameters for fine-tuning retrieval quality
- Integration with embedding index for project context awareness

**Multi-Agent Orchestration System**
- EnhancedAgentOrchestrator for complex task decomposition
- Specialized tool agents (ProjectScanner, CodeAnalysis, BugDetection)
- Interactive plan execution with real-time progress tracking
- Command integration via `/plan` in chat interface

**MCP Integration**
- Full Model Context Protocol client implementation
- Support for both STDIO (local) and HTTP (remote) servers
- Automatic tool registration and management
- Headless-safe operations for CI/CD environments

**UI Architecture Refactoring**
- 75% reduction in ChatPanel code size (958 → 235 lines)
- Component composition pattern with single responsibility principle
- Enhanced JCEF support with interactive elements
- Improved maintainability and testability

### Feature Roadmaps

**Uncertainty Analysis System (COMPLETED)**
Документация по реализованной системе анализа неопределенности доступна в файле:
- **[UNCERTAINTY_ANALYSIS_ROADMAP.md](docs/features/uncertainty-analysis-roadmap.md)** - Полный роудмап разработки

**Quick Usage Reference:**
```kotlin
// Simple query - handled by ChatAgent with RAG
val response = chatService.sendMessage("How does the authentication work?")

// Complex task - handled by Orchestrator
val planResponse = chatService.sendMessageWithOrchestrator("/plan Analyze the performance bottlenecks")

// Check uncertainty and finality
if (response.isFinal) {
    displayFinalAnswer(response.content)
} else {
    val questions = UncertaintyAnalyzer.extractClarifyingQuestions(response.content)
    displayClarifyingQuestions(questions, response.uncertainty)
}
```