# 🔍 Роадмап: Агент для анализа кода (Code Analysis Agent)

## 📊 Метаданные проекта

- **Статус**: 📋 Запланировано
- **Версия**: 1.0.0
- **Дата создания**: 2025-10-20
- **Приоритет**: Высокий
- **Оценка времени**: 3-4 недели

---

## 🎯 Цели фичи

### Основная задача
Создать специализированного агента для анализа кодовой базы проекта, способного:
- Находить очевидные баги и потенциальные проблемы
- Строить структуру проекта и визуализировать архитектуру
- Анализировать качество кода и соответствие best practices
- Предлагать рефакторинг и улучшения
- Генерировать документацию на основе кода

### Пользовательские проблемы
- **Проблема 1**: Сложно найти баги в большой кодовой базе вручную
- **Проблема 2**: Отсутствие автоматического анализа архитектуры проекта
- **Проблема 3**: Нет инструмента для быстрого понимания структуры незнакомого проекта
- **Проблема 4**: Трудоемкость code review и поиска code smells

### Бизнес-ценность
- Ускорение разработки за счет раннего обнаружения проблем
- Повышение качества кода
- Снижение технического долга
- Улучшение onboarding новых разработчиков

---

## 📋 Задачи (Checklist)

### Phase 1: Архитектура и проектирование
- [ ] Спроектировать интерфейс `CodeAnalysisAgent`
- [ ] Определить модели данных для результатов анализа
- [ ] Спроектировать систему сканирования файлов проекта
- [ ] Разработать стратегию chunking для больших проектов
- [ ] Определить форматы вывода результатов

### Phase 2: Базовая реализация
- [ ] Реализовать `CodeAnalysisAgent` с базовым функционалом
- [ ] Создать `ProjectScanner` для обхода файлов проекта
- [ ] Реализовать `CodeChunker` для разбиения больших файлов
- [ ] Создать `AnalysisResultFormatter` для форматирования результатов
- [ ] Интегрировать с существующей системой агентов

### Phase 3: Анализаторы
- [ ] Реализовать `BugDetectionAnalyzer` - поиск очевидных багов
- [ ] Реализовать `ArchitectureAnalyzer` - анализ структуры проекта
- [ ] Реализовать `CodeQualityAnalyzer` - анализ качества кода
- [ ] Реализовать `DependencyAnalyzer` - анализ зависимостей
- [ ] Реализовать `SecurityAnalyzer` - поиск уязвимостей

### Phase 4: UI и интеграция
- [ ] Создать UI для запуска анализа
- [ ] Добавить визуализацию результатов анализа
- [ ] Реализовать экспорт результатов (JSON, Markdown, HTML)
- [ ] Интегрировать с Tool Window
- [ ] Добавить прогресс-бар для длительных операций

### Phase 5: Оптимизация и тестирование
- [ ] Оптимизировать использование токенов
- [ ] Реализовать кэширование результатов
- [ ] Написать unit-тесты для всех компонентов
- [ ] Провести интеграционное тестирование
- [ ] Тестирование на реальных проектах

### Phase 6: Документация
- [ ] Написать API документацию
- [ ] Создать user guide
- [ ] Добавить примеры использования
- [ ] Обновить README.md

---

## 🏗️ Архитектура

### Компоненты системы

```
┌─────────────────────────────────────────────────────┐
│                 CodeAnalysisAgent                   │
│  (Orchestrates analysis workflow)                   │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ ProjectScanner│  │  CodeChunker │  │ LLMProvider│
│  └──────────────┘  └──────────────┘  └──────────┘ │
│                                                     │
│  ┌─────────────────────────────────────────────┐  │
│  │           Analyzers                         │  │
│  ├─────────────────────────────────────────────┤  │
│  │ • BugDetectionAnalyzer                      │  │
│  │ • ArchitectureAnalyzer                      │  │
│  │ • CodeQualityAnalyzer                       │  │
│  │ • DependencyAnalyzer                        │  │
│  │ • SecurityAnalyzer                          │  │
│  └─────────────────────────────────────────────┘  │
│                                                     │
│  ┌──────────────────────────────────────────────┐ │
│  │        AnalysisResultFormatter               │ │
│  │  (Formats results for display)               │ │
│  └──────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### Модели данных

```kotlin
// Запрос на анализ
data class CodeAnalysisRequest(
    val projectPath: String,
    val analysisTypes: Set<AnalysisType>,
    val filePatterns: List<String> = listOf("**/*.kt", "**/*.java"),
    val excludePatterns: List<String> = listOf("**/build/**", "**/test/**"),
    val maxFilesPerBatch: Int = 10,
    val parameters: LLMParameters = LLMParameters.BALANCED
)

// Типы анализа
enum class AnalysisType {
    BUG_DETECTION,          // Поиск багов
    ARCHITECTURE,           // Анализ архитектуры
    CODE_QUALITY,           // Качество кода
    DEPENDENCIES,           // Зависимости
    SECURITY,               // Безопасность
    DOCUMENTATION,          // Документация
    ALL                     // Все типы
}

// Результат анализа
data class CodeAnalysisResult(
    val projectName: String,
    val analysisDate: LocalDateTime,
    val findings: List<Finding>,
    val projectStructure: ProjectStructure?,
    val metrics: CodeMetrics,
    val summary: String,
    val recommendations: List<String>
)

// Найденная проблема
data class Finding(
    val id: String,
    val type: FindingType,
    val severity: Severity,
    val file: String,
    val line: Int?,
    val title: String,
    val description: String,
    val suggestion: String?,
    val codeSnippet: String?
)

enum class FindingType {
    BUG, CODE_SMELL, SECURITY_ISSUE, ARCHITECTURE_VIOLATION, 
    PERFORMANCE_ISSUE, DOCUMENTATION_MISSING
}

enum class Severity {
    CRITICAL, HIGH, MEDIUM, LOW, INFO
}

// Структура проекта
data class ProjectStructure(
    val rootPackage: String,
    val modules: List<Module>,
    val layers: List<Layer>,
    val dependencies: List<Dependency>
)

data class Module(
    val name: String,
    val path: String,
    val type: ModuleType,
    val files: Int,
    val linesOfCode: Int
)

enum class ModuleType {
    DOMAIN, UI, SERVICE, INTEGRATION, UTIL, TEST
}

// Метрики кода
data class CodeMetrics(
    val totalFiles: Int,
    val totalLines: Int,
    val totalClasses: Int,
    val totalFunctions: Int,
    val averageComplexity: Double,
    val testCoverage: Double?
)
```

### Интерфейс агента

```kotlin
interface CodeAnalysisAgent : Agent {
    /**
     * Анализирует проект по указанному пути
     */
    suspend fun analyzeProject(request: CodeAnalysisRequest): CodeAnalysisResult
    
    /**
     * Анализирует конкретный файл
     */
    suspend fun analyzeFile(filePath: String, analysisTypes: Set<AnalysisType>): List<Finding>
    
    /**
     * Строит структуру проекта
     */
    suspend fun buildProjectStructure(projectPath: String): ProjectStructure
    
    /**
     * Генерирует отчет в указанном формате
     */
    fun generateReport(result: CodeAnalysisResult, format: ReportFormat): String
}

enum class ReportFormat {
    MARKDOWN, HTML, JSON, TEXT
}
```

---

## 🔧 Технические детали реализации

### 1. ProjectScanner

Сканирует файлы проекта с учетом паттернов включения/исключения:

```kotlin
class ProjectScanner(
    private val project: Project
) {
    fun scanProject(
        filePatterns: List<String>,
        excludePatterns: List<String>
    ): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        
        ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (shouldIncludeFile(file, filePatterns, excludePatterns)) {
                files.add(file)
            }
            true
        }
        
        return files
    }
    
    private fun shouldIncludeFile(
        file: VirtualFile,
        includes: List<String>,
        excludes: List<String>
    ): Boolean {
        // Проверка по glob паттернам
        return matchesAnyPattern(file.path, includes) && 
               !matchesAnyPattern(file.path, excludes)
    }
}
```

### 2. CodeChunker

Разбивает большие файлы на чанки для обработки:

```kotlin
class CodeChunker(
    private val tokenCounter: TokenCounter,
    private val maxTokensPerChunk: Int = 4000
) {
    fun chunkFile(content: String): List<CodeChunk> {
        val chunks = mutableListOf<CodeChunk>()
        val lines = content.lines()
        
        var currentChunk = StringBuilder()
        var currentTokens = 0
        var startLine = 1
        
        for ((index, line) in lines.withIndex()) {
            val lineTokens = tokenCounter.countTokens(line)
            
            if (currentTokens + lineTokens > maxTokensPerChunk && currentChunk.isNotEmpty()) {
                chunks.add(CodeChunk(
                    content = currentChunk.toString(),
                    startLine = startLine,
                    endLine = index,
                    tokens = currentTokens
                ))
                currentChunk = StringBuilder()
                currentTokens = 0
                startLine = index + 1
            }
            
            currentChunk.appendLine(line)
            currentTokens += lineTokens
        }
        
        if (currentChunk.isNotEmpty()) {
            chunks.add(CodeChunk(
                content = currentChunk.toString(),
                startLine = startLine,
                endLine = lines.size,
                tokens = currentTokens
            ))
        }
        
        return chunks
    }
}

data class CodeChunk(
    val content: String,
    val startLine: Int,
    val endLine: Int,
    val tokens: Int
)
```

### 3. BugDetectionAnalyzer

Анализирует код на наличие багов:

```kotlin
class BugDetectionAnalyzer(
    private val llmProvider: LLMProvider
) {
    suspend fun analyze(code: String, filePath: String): List<Finding> {
        val prompt = buildBugDetectionPrompt(code, filePath)
        
        val response = llmProvider.sendRequest(
            systemPrompt = BUG_DETECTION_SYSTEM_PROMPT,
            userMessage = prompt,
            conversationHistory = emptyList(),
            parameters = LLMParameters.PRECISE
        )
        
        return parseFindingsFromResponse(response.content, FindingType.BUG)
    }
    
    private fun buildBugDetectionPrompt(code: String, filePath: String): String {
        return """
        Проанализируй следующий код на наличие очевидных багов и потенциальных проблем.
        
        Файл: $filePath
        
        ```kotlin
        $code
        ```
        
        Найди:
        - Null pointer exceptions
        - Resource leaks
        - Неправильная обработка ошибок
        - Race conditions
        - Логические ошибки
        - Неиспользуемый код
        
        Для каждой проблемы укажи:
        - Строку кода
        - Описание проблемы
        - Серьезность (CRITICAL/HIGH/MEDIUM/LOW)
        - Рекомендацию по исправлению
        """.trimIndent()
    }
    
    companion object {
        private val BUG_DETECTION_SYSTEM_PROMPT = """
        Ты - эксперт по анализу кода и поиску багов.
        Твоя задача - найти потенциальные проблемы в коде и предложить решения.
        Будь точным и конкретным. Указывай номера строк и фрагменты кода.
        Оценивай серьезность проблем объективно.
        """.trimIndent()
    }
}
```

### 4. ArchitectureAnalyzer

Анализирует архитектуру проекта:

```kotlin
class ArchitectureAnalyzer(
    private val llmProvider: LLMProvider
) {
    suspend fun analyze(projectFiles: List<ProjectFile>): ProjectStructure {
        // Группируем файлы по пакетам
        val packageStructure = groupFilesByPackage(projectFiles)
        
        // Анализируем зависимости
        val dependencies = analyzeDependencies(projectFiles)
        
        // Определяем слои архитектуры
        val layers = identifyLayers(packageStructure, dependencies)
        
        // Генерируем описание архитектуры через LLM
        val architectureDescription = generateArchitectureDescription(
            packageStructure, dependencies, layers
        )
        
        return ProjectStructure(
            rootPackage = findRootPackage(packageStructure),
            modules = identifyModules(packageStructure),
            layers = layers,
            dependencies = dependencies
        )
    }
    
    private suspend fun generateArchitectureDescription(
        packages: Map<String, List<ProjectFile>>,
        dependencies: List<Dependency>,
        layers: List<Layer>
    ): String {
        val prompt = """
        Проанализируй структуру проекта и опиши его архитектуру.
        
        Пакеты: ${packages.keys.joinToString(", ")}
        Количество файлов: ${packages.values.sumOf { it.size }}
        Зависимости: ${dependencies.size}
        Слои: ${layers.map { it.name }.joinToString(", ")}
        
        Определи:
        - Архитектурный паттерн (MVC, MVVM, Clean Architecture, etc.)
        - Соблюдение принципов SOLID
        - Проблемы в архитектуре
        - Рекомендации по улучшению
        """.trimIndent()
        
        val response = llmProvider.sendRequest(
            systemPrompt = ARCHITECTURE_SYSTEM_PROMPT,
            userMessage = prompt,
            conversationHistory = emptyList(),
            parameters = LLMParameters.BALANCED
        )
        
        return response.content
    }
    
    companion object {
        private val ARCHITECTURE_SYSTEM_PROMPT = """
        Ты - архитектор программного обеспечения с глубоким пониманием паттернов проектирования.
        Анализируй архитектуру проектов и давай конструктивные рекомендации.
        Ссылайся на принципы SOLID, Clean Architecture и best practices.
        """.trimIndent()
    }
}
```

### 5. Стратегия обработки больших проектов

Для больших проектов используется батчинг и приоритизация:

```kotlin
class CodeAnalysisAgentImpl(
    private val llmProvider: LLMProvider,
    private val projectScanner: ProjectScanner,
    private val codeChunker: CodeChunker
) : CodeAnalysisAgent {
    
    override suspend fun analyzeProject(request: CodeAnalysisRequest): CodeAnalysisResult {
        // 1. Сканируем проект
        val files = projectScanner.scanProject(
            request.filePatterns,
            request.excludePatterns
        )
        
        logger.info("Found ${files.size} files to analyze")
        
        // 2. Приоритизируем файлы (сначала важные)
        val prioritizedFiles = prioritizeFiles(files)
        
        // 3. Разбиваем на батчи
        val batches = prioritizedFiles.chunked(request.maxFilesPerBatch)
        
        val allFindings = mutableListOf<Finding>()
        var processedFiles = 0
        
        // 4. Обрабатываем батчами с прогрессом
        for ((index, batch) in batches.withIndex()) {
            logger.info("Processing batch ${index + 1}/${batches.size}")
            
            val batchFindings = analyzeBatch(batch, request.analysisTypes)
            allFindings.addAll(batchFindings)
            
            processedFiles += batch.size
            
            // Уведомляем о прогрессе
            notifyProgress(processedFiles, files.size)
        }
        
        // 5. Строим структуру проекта
        val structure = if (AnalysisType.ARCHITECTURE in request.analysisTypes) {
            buildProjectStructure(request.projectPath)
        } else null
        
        // 6. Вычисляем метрики
        val metrics = calculateMetrics(files, allFindings)
        
        // 7. Генерируем summary
        val summary = generateSummary(allFindings, metrics)
        
        return CodeAnalysisResult(
            projectName = File(request.projectPath).name,
            analysisDate = LocalDateTime.now(),
            findings = allFindings,
            projectStructure = structure,
            metrics = metrics,
            summary = summary,
            recommendations = generateRecommendations(allFindings, structure)
        )
    }
    
    private fun prioritizeFiles(files: List<VirtualFile>): List<VirtualFile> {
        // Приоритет: domain > service > ui > util > test
        return files.sortedBy { file ->
            when {
                file.path.contains("/domain/") -> 0
                file.path.contains("/service/") -> 1
                file.path.contains("/ui/") -> 2
                file.path.contains("/util/") -> 3
                file.path.contains("/test/") -> 4
                else -> 5
            }
        }
    }
}
```

---

## 📊 Использование токенов

### Оценка расхода токенов

Для проекта среднего размера (100 файлов, ~10K строк кода):

| Этап | Токены (примерно) | Описание |
|------|-------------------|----------|
| Сканирование структуры | 500 | Анализ структуры пакетов |
| Анализ файлов (батч 10) | 4000 × 10 = 40K | По 4K токенов на файл |
| Генерация summary | 2000 | Итоговый отчет |
| **ИТОГО** | **~42.5K токенов** | На весь проект |

### Оптимизации

1. **Кэширование**: Сохранять результаты анализа файлов, которые не изменились
2. **Инкрементальный анализ**: Анализировать только измененные файлы
3. **Приоритизация**: Сначала анализировать критичные файлы
4. **Chunking**: Разбивать большие файлы на части
5. **Батчинг**: Обрабатывать файлы группами

---

## 🎨 UI/UX

### Tool Window "Code Analysis"

```
┌─────────────────────────────────────────────────┐
│  Code Analysis                          [⚙️] [▶️] │
├─────────────────────────────────────────────────┤
│                                                 │
│  📊 Analysis Type:                              │
│  ☑ Bug Detection                                │
│  ☑ Architecture Analysis                        │
│  ☑ Code Quality                                 │
│  ☐ Security Scan                                │
│  ☐ Documentation Check                          │
│                                                 │
│  📁 Scope:                                       │
│  ⦿ Whole Project                                │
│  ○ Current Module                               │
│  ○ Selected Files                               │
│                                                 │
│  [Start Analysis]                               │
│                                                 │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                                 │
│  📈 Last Analysis: 2025-10-20 10:30             │
│  • Files analyzed: 127                          │
│  • Findings: 23 (🔴 2 🟡 8 🟢 13)              │
│  • Duration: 2m 15s                             │
│                                                 │
│  [View Report] [Export]                         │
│                                                 │
└─────────────────────────────────────────────────┘
```

### Результаты анализа

```
┌─────────────────────────────────────────────────┐
│  Analysis Results - MyProject                   │
├─────────────────────────────────────────────────┤
│                                                 │
│  🔴 CRITICAL (2)                                │
│  ├─ Potential NPE in UserService.kt:45         │
│  └─ Resource leak in FileHandler.kt:89         │
│                                                 │
│  🟡 HIGH (8)                                    │
│  ├─ Code smell: God class in MainController    │
│  ├─ Missing error handling in ApiClient:123    │
│  └─ ... (6 more)                                │
│                                                 │
│  🟢 MEDIUM (13)                                 │
│  └─ ... (show all)                              │
│                                                 │
│  📊 Project Structure:                          │
│  ├─ Architecture: Clean Architecture           │
│  ├─ Layers: 4 (UI, Service, Domain, Data)     │
│  ├─ Modules: 3                                  │
│  └─ Dependencies: 15 external                   │
│                                                 │
│  💡 Recommendations:                            │
│  1. Refactor MainController (too many resp.)   │
│  2. Add error handling in API layer            │
│  3. Improve test coverage (current: 45%)       │
│                                                 │
└─────────────────────────────────────────────────┘
```

---

## 🧪 Тестирование

### Unit тесты

```kotlin
class CodeAnalysisAgentTest {
    private val mockLLMProvider = mockk<LLMProvider>()
    private val agent = CodeAnalysisAgentImpl(mockLLMProvider, ...)
    
    @Test
    fun `should detect null pointer exception`() = runBlocking {
        val code = """
            fun process(user: User?) {
                println(user.name) // NPE here
            }
        """.trimIndent()
        
        coEvery { mockLLMProvider.sendRequest(...) } returns LLMResponse.success(
            """
            Найдена проблема:
            - Строка 2: Potential NullPointerException
            - Severity: CRITICAL
            - user может быть null, но используется без проверки
            """.trimIndent()
        )
        
        val findings = agent.analyzeFile(code, setOf(AnalysisType.BUG_DETECTION))
        
        assertEquals(1, findings.size)
        assertEquals(FindingType.BUG, findings[0].type)
        assertEquals(Severity.CRITICAL, findings[0].severity)
    }
}
```

---

## 📈 Метрики успеха

- ✅ Агент находит минимум 80% очевидных багов в тестовых проектах
- ✅ Время анализа проекта (100 файлов) < 5 минут
- ✅ Точность определения архитектурного паттерна > 90%
- ✅ Расход токенов < 50K на средний проект
- ✅ Покрытие тестами > 80%

---

## 🔄 Следующие шаги после реализации

1. **Интеграция с CI/CD**: Автоматический анализ при коммитах
2. **Кастомные правила**: Возможность добавлять свои правила анализа
3. **Machine Learning**: Обучение на истории проектов
4. **Collaborative features**: Sharing результатов анализа в команде
5. **IDE Actions**: Quick fixes для найденных проблем

---

## 📚 Связанные документы

- [Architecture Overview](../architecture/overview.md)
- [Agent System](../architecture/agents.md)
- [Token Management](../features/token-management.md)

---

**Дата создания**: 2025-10-20  
**Автор**: AI Assistant  
**Статус**: Готов к утверждению
