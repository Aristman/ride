# Этап 2: Tool Agents на основе CodeAnalysisAgent

**Длительность:** 2-3 недели  
**Приоритет:** Высокий  
**Статус:** 📋 Запланировано  
**Зависимости:** Phase 1

---

## 🎯 Цели этапа

Создать систему специализированных агентов-инструментов (Tool Agents) на основе декомпозиции `CodeAnalysisAgent`:
- Базовый интерфейс и модели для Tool Agents
- Специализированные агенты для анализа кода
- Система регистрации и управления агентами

---

## 📋 Задачи

### 2.1 Базовый интерфейс ToolAgent
- [ ] Определить интерфейс `ToolAgent` (extends Agent)
- [ ] Создать базовые модели (`PlanStep`, `StepInput`, `StepOutput`, `StepResult`)
- [ ] Реализовать `ToolAgentRegistry` для регистрации агентов
- [ ] Добавить систему capabilities для агентов
- [ ] Создать enum `AgentType` для всех типов агентов
- [ ] Реализовать `StepStatus` для отслеживания состояния шага

### 2.2 Декомпозиция CodeAnalysisAgent
- [ ] Создать `ProjectScannerToolAgent` (сканирование файловой системы)
- [ ] Создать `CodeChunkerToolAgent` (разбиение больших файлов)
- [ ] Создать `BugDetectionToolAgent` (поиск багов)
- [ ] Создать `CodeQualityToolAgent` (анализ качества кода)
- [ ] Создать `ArchitectureToolAgent` (анализ архитектуры)
- [ ] Создать `ReportGeneratorToolAgent` (генерация отчетов)

### 2.3 ToolAgentRegistry
- [ ] Реализовать регистрацию и управление Tool Agents
- [ ] Добавить автообнаружение доступных агентов
- [ ] Создать фабрику для создания агентов
- [ ] Реализовать поиск агентов по capabilities
- [ ] Интегрировать с `EnhancedAgentOrchestrator`
- [ ] Добавить валидацию совместимости агентов

---

## 📚 Ключевые интерфейсы

### ToolAgent
```kotlin
interface ToolAgent : Agent {
    val agentType: AgentType
    val capabilities: Set<String>
    
    suspend fun executeStep(step: PlanStep, context: ExecutionContext): StepResult
    fun canHandle(step: PlanStep): Boolean
}

enum class AgentType {
    PROJECT_SCANNER, CODE_CHUNKER, BUG_DETECTION,
    CODE_QUALITY, ARCHITECTURE_ANALYSIS, CODE_FIXER,
    REPORT_GENERATOR, USER_INTERACTION, FILE_OPERATIONS
}
```

### PlanStep
```kotlin
data class PlanStep(
    val id: String,
    val description: String,
    val agentType: AgentType,
    val input: StepInput,
    val dependencies: Set<String>,
    var status: StepStatus,
    var output: StepOutput? = null
)

enum class StepStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
}
```

### StepResult
```kotlin
data class StepResult(
    val success: Boolean,
    val output: StepOutput,
    val error: String? = null,
    val requiresUserInput: Boolean = false,
    val userPrompt: String? = null
) {
    companion object {
        fun success(output: StepOutput) = StepResult(true, output)
        fun error(error: String) = StepResult(false, StepOutput.empty(), error)
        fun requiresInput(prompt: String) = StepResult(false, StepOutput.empty(), null, true, prompt)
    }
}
```

---

## 🔧 Tool Agents

### ProjectScannerToolAgent
**Назначение:** Сканирование файловой системы проекта с фильтрацией

**Capabilities:**
- `file_discovery` - поиск файлов
- `pattern_matching` - фильтрация по glob паттернам
- `exclusion_filtering` - исключение файлов

**Входные данные:**
- `patterns`: List<String> - паттерны включения (например, `**/*.kt`)
- `exclude_patterns`: List<String> - паттерны исключения

**Выходные данные:**
- `files`: List<String> - список найденных файлов
- `total_count`: Int - количество файлов
- `scan_time`: Long - время сканирования

### BugDetectionToolAgent
**Назначение:** Поиск багов и потенциальных проблем в коде

**Capabilities:**
- `bug_detection` - поиск багов
- `null_pointer_analysis` - анализ NPE
- `resource_leak_detection` - поиск утечек ресурсов

**Входные данные:**
- `files`: List<String> - файлы для анализа
- `severity_threshold`: Severity - минимальная серьезность

**Выходные данные:**
- `findings`: List<Finding> - найденные проблемы
- `critical_count`: Int - количество критических
- `high_count`: Int - количество высоких

### CodeQualityToolAgent
**Назначение:** Анализ качества кода и code smells

**Capabilities:**
- `code_quality_analysis` - анализ качества
- `code_smell_detection` - поиск code smells
- `complexity_analysis` - анализ сложности

### ArchitectureToolAgent
**Назначение:** Анализ архитектуры проекта

**Capabilities:**
- `architecture_analysis` - анализ архитектуры
- `dependency_analysis` - анализ зависимостей
- `layer_detection` - определение слоев

### CodeChunkerToolAgent
**Назначение:** Разбиение больших файлов на чанки

**Capabilities:**
- `file_chunking` - разбиение файлов
- `token_counting` - подсчет токенов

### ReportGeneratorToolAgent
**Назначение:** Генерация отчетов в различных форматах

**Capabilities:**
- `markdown_generation` - генерация Markdown
- `html_generation` - генерация HTML
- `json_export` - экспорт в JSON

---

## 🏗️ ToolAgentRegistry

```kotlin
class ToolAgentRegistry {
    private val agents = ConcurrentHashMap<AgentType, ToolAgent>()
    
    fun register(agent: ToolAgent) {
        agents[agent.agentType] = agent
    }
    
    fun get(agentType: AgentType): ToolAgent? {
        return agents[agentType]
    }
    
    fun findByCapability(capability: String): List<ToolAgent> {
        return agents.values.filter { it.capabilities.contains(capability) }
    }
    
    fun listAll(): List<ToolAgent> {
        return agents.values.toList()
    }
    
    fun isAvailable(agentType: AgentType): Boolean {
        return agents.containsKey(agentType)
    }
}
```

---

## ✅ Критерии завершения

- [ ] Интерфейс ToolAgent определен и задокументирован
- [ ] Все 6 Tool Agents реализованы и протестированы
- [ ] ToolAgentRegistry управляет регистрацией агентов
- [ ] Агенты корректно обрабатывают входные данные
- [ ] Агенты возвращают структурированные результаты
- [ ] Unit тесты покрывают >80% кода каждого агента
- [ ] Интеграционные тесты проверяют взаимодействие
- [ ] Документация по созданию новых Tool Agents

---

## 📖 Связанные документы

- [Phase 1: Base Architecture](phase-1-base-architecture.md)
- [Phase 3: Interactivity](phase-3-interactivity.md)
- [Code Analysis Agent](../09-code-analysis-agent.md)
- [Техническая спецификация](enhanced-orchestrator-technical.md)

---

*Создано: 2025-10-20*
