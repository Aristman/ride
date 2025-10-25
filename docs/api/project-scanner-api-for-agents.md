# 🔌 Project Scanner API для других агентов

## 🎯 Обзор

Этот документ описывает API Project Scanner Tool Agent, предназначенный для интеграции с другими агентами в мультиагентской системе. API обеспечивает эффективный доступ к файловой системе проекта с поддержкой кэширования, подписок на изменения и производительных операций.

## 🏗️ Архитектура интеграции

### Основные компоненты

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Другой агент   │───▶│ ProjectScanner   │───▶│ Файловая система │
│ (Code Analysis)  │    │ AgentBridge      │    │ проекта        │
│                  │    │                  │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
  Business Logic         Convenience API        File Operations
         │                       │                       │
         ▼                       ▼                       ▼
  Результаты анализа   Управление ресурсами   Оптимизированный доступ
```

### Ключевые классы

- **ProjectScannerToolAgent** - основной агент сканирования
- **ProjectScannerAgentBridge** - удобный API для других агентов
- **DeltaSubscription** - подписки на изменения файлов
- **DeltaUpdate** - уведомления об изменениях

## 🚀 Быстрый старт

### Базовое использование через Bridge

```kotlin
import ru.marslab.ide.ride.agent.tools.ProjectScannerAgentBridge

class CodeAnalysisAgent {
    private val scannerBridge = ProjectScannerAgentBridge()

    suspend fun analyzeProject(projectPath: String) {
        // Сканирование проекта
        val scanResult = scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf("**/*.kt", "**/*.java"),
            excludePatterns = listOf("build/**", "**/generated/**")
        )

        if (scanResult.success) {
            val files = scanResult.files
            val stats = scanResult.statistics

            // Анализ найденных файлов
            analyzeFiles(files)

            // Использование статистики
            println("Проект содержит ${stats.languages?.keys} файлы")
        }
    }
}
```

### Подписка на изменения

```kotlin
class FileWatcherAgent {
    private val scannerBridge = ProjectScannerAgentBridge()
    private var subscription: AutoCloseable? = null

    fun startWatching(projectPath: String) {
        subscription = scannerBridge.subscribeToFileChanges(
            agentId = "file-watcher",
            projectPath = projectPath
        ) { changedFiles ->
            // Обработка изменений
            handleFileChanges(changedFiles)
        }

        println("Подписка на изменения файлов создана")
    }

    fun stopWatching() {
        subscription?.close()
        println("Подписка на изменения файлов отменена")
    }

    private fun handleFileChanges(changedFiles: List<String>) {
        println("Обнаружены изменения:")
        changedFiles.forEach { file ->
            println(" - $file")
        }

        // Запуск повторного анализа измененных файлов
        reanalyzeFiles(changedFiles)
    }
}
```

## 📋 API Reference

### ProjectScannerAgentBridge

Удобный wrapper для доступа к функциям ProjectScanner из других агентов.

#### Основные методы

##### scanProject()

```kotlin
fun scanProject(
    projectPath: String,
    includePatterns: List<String> = listOf("**/*"),
    excludePatterns: List<String> = emptyList(),
    maxDepth: Int? = null
): ProjectScanResult
```

**Параметры:**
- `projectPath`: Путь к проекту
- `includePatterns`: Паттерны включения файлов (glob)
- `excludePatterns`: Паттерны исключения файлов (glob)
- `maxDepth`: Максимальная глубина сканирования

**Возвращает:** `ProjectScanResult`

**Пример:**
```kotlin
val result = scannerBridge.scanProject(
    projectPath = "/home/user/my-project",
    includePatterns = listOf("src/**/*.kt", "src/**/*.java"),
    excludePatterns = listOf("build/**", "**/test/**"),
    maxDepth = 15
)

if (result.success) {
    println("Найдено файлов: ${result.files.size}")
    println("Тип проекта: ${result.projectType}")
}
```

##### subscribeToFileChanges()

```kotlin
fun subscribeToFileChanges(
    agentId: String,
    projectPath: String,
    includePatterns: List<String> = listOf("**/*"),
    excludePatterns: List<String> = emptyList(),
    callback: (List<String>) -> Unit
): AutoCloseable
```

**Параметры:**
- `agentId`: Уникальный идентификатор агента-подписчика
- `projectPath`: Путь к проекту для отслеживания
- `includePatterns`: Фильтры для отслеживаемых файлов
- `excludePatterns`: Исключения из отслеживания
- `callback`: Callback функция для обработки изменений

**Возвращает:** `AutoCloseable` для автоматической очистки

**Пример:**
```kotlin
val subscription = scannerBridge.subscribeToFileChanges(
    agentId = "code-analyzer",
    projectPath = "/home/user/project",
    includePatterns = listOf("src/**/*.kt"),
    excludePatterns = listOf("**/generated/**")
) { changedFiles ->
    // Пересбор индекса при изменениях
    rebuildIndex(changedFiles)
}

// Автоматическая отписка при выходе из scope
use(subscription) {
    // Работа с подпиской
}
```

##### getDeltaChanges()

```kotlin
suspend fun getDeltaChanges(
    projectPath: String,
    sinceTimestamp: Long,
    includePatterns: List<String> = listOf("**/*"),
    excludePatterns: List<String> = emptyList()
): List<String>
```

**Параметры:**
- `projectPath`: Путь к проекту
- `sinceTimestamp`: Временная метка для поиска изменений
- `includePatterns`: Фильтры файлов
- `excludePatterns`: Исключения

**Возвращает:** Список измененных файлов

**Пример:**
```kotlin
val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
val changedFiles = scannerBridge.getDeltaChanges(
    projectPath = "/home/user/project",
    sinceTimestamp = oneHourAgo,
    includePatterns = listOf("src/**/*.kt")
)

println("Файлы, измененные за последний час:")
changedFiles.forEach { file ->
    println(" - $file")
}
```

### ProjectScanResult

Результат сканирования проекта.

```kotlin
data class ProjectScanResult(
    val success: Boolean,
    val files: List<String>,
    val directories: List<String>,
    val projectType: String,
    val statistics: ProjectStatistics?,
    val error: String? = null,
    val performanceMetrics: PerformanceMetrics?
)
```

**Поля:**
- `success`: Успешность выполнения
- `files`: Список найденных файлов
- `directories`: Список директорий
- `projectType`: Тип проекта (GRADLE_KOTLIN, MAVEN, и т.д.)
- `statistics`: Статистика по проекту
- `error`: Ошибка, если неуспешно
- `performanceMetrics`: Метрики производительности

### ProjectStatistics

Статистическая информация о проекте.

```kotlin
data class ProjectStatistics(
    val totalFiles: Int,
    val totalLines: Int,
    val languages: Map<String, Int>,
    val fileAnalysis: FileAnalysis,
    val dependencies: DependencyInfo?
)
```

**Пример использования:**
```kotlin
val stats = result.statistics
if (stats != null) {
    println("Всего файлов: ${stats.totalFiles}")
    println("Всего строк кода: ${stats.totalLines}")

    println("Языки программирования:")
    stats.languages.forEach { (language, count) ->
        println("  $language: $count файлов")
    }

    println("Анализ размеров файлов:")
    println("  Маленькие (<1KB): ${stats.fileAnalysis.smallFiles}")
    println("  Средние (1-10KB): ${stats.fileAnalysis.mediumFiles}")
    println("  Большие (>10KB): ${stats.fileAnalysis.largeFiles}")
}
```

## 🔒 Управление ресурсами

### Автоматическая очистка

```kotlin
class MyAgent {
    private val scannerBridge = ProjectScannerAgentBridge()

    suspend fun processProject(projectPath: String) {
        // Автоматическая подписка с очисткой
        use(scannerBridge.subscribeToFileChanges("my-agent", projectPath) { files ->
            processChanges(files)
        }) {
            // Работа с подпиской
            // Автоматически отписаться при выходе из use-блока
        }
    }
}
```

### Ручное управление ресурсами

```kotlin
class AnotherAgent {
    private val scannerBridge = ProjectScannerAgentBridge()
    private val subscriptions = mutableListOf<AutoCloseable>()

    fun startWatching(paths: List<String>) {
        paths.forEach { path ->
            val subscription = scannerBridge.subscribeToFileChanges(
                agentId = "another-agent",
                projectPath = path
            ) { files ->
                handleChanges(files)
            }
            subscriptions.add(subscription)
        }
    }

    fun stopWatching() {
        subscriptions.forEach { it.close() }
        subscriptions.clear()
    }
}
```

## 🔄 Шаблоны интеграции

### 1. Code Analysis Agent

```kotlin
class CodeAnalysisAgent {
    private val scannerBridge = ProjectScannerAgentBridge()
    private var fileIndex: Map<String, FileInfo> = emptyMap()

    suspend fun analyzeProject(projectPath: String) {
        // Начальное сканирование
        val scanResult = scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf("src/**/*.kt", "src/**/*.java")
        )

        if (scanResult.success) {
            // Построение индекса
            buildIndex(scanResult.files)

            // Подписка на изменения
            scannerBridge.subscribeToFileChanges(
                agentId = "code-analyzer",
                projectPath = projectPath,
                includePatterns = listOf("src/**/*.kt", "src/**/*.java")
            ) { changedFiles ->
                updateIndex(changedFiles)
                runAnalysis(changedFiles)
            }
        }
    }

    private fun buildIndex(files: List<String>) {
        fileIndex = files.associateWith { path ->
            FileInfo(path = path, lastAnalyzed = 0L, metrics = null)
        }
    }

    private fun updateIndex(changedFiles: List<String>) {
        changedFiles.forEach { file ->
            fileIndex = fileIndex + (file to FileInfo(
                path = file,
                lastAnalyzed = System.currentTimeMillis(),
                metrics = null
            ))
        }
    }

    private suspend fun runAnalysis(files: List<String>) {
        // Запуск анализа для измененных файлов
        files.forEach { file ->
            analyzeFile(file)
        }
    }

    private fun analyzeFile(filePath: String) {
        // Реализация анализа файла
        println("Анализ файла: $filePath")
    }
}

data class FileInfo(
    val path: String,
    val lastAnalyzed: Long,
    val metrics: FileMetrics?
)

data class FileMetrics(
    val lines: Int,
    val complexity: Int,
    val dependencies: List<String>
)
```

### 2. Documentation Agent

```kotlin
class DocumentationAgent {
    private val scannerBridge = ProjectScannerAgentBridge()

    suspend fun generateDocumentation(projectPath: String) {
        // Поиск файлов с документацией
        val docResult = scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf(
                "**/*.md",
                "**/*.rst",
                "README*",
                "src/**/*.kt", // Для анализа docstrings
                "src/**/*.java"
            )
        )

        if (docResult.success) {
            // Классификация файлов
            val (docFiles, sourceFiles) = docResult.files.partition {
                it.endsWith(".md") || it.endsWith(".rst") || it.startsWith("README")
            }

            // Генерация документации
            generateApiDocumentation(sourceFiles)
            processMarkdownFiles(docFiles)
        }
    }

    private fun generateApiDocumentation(sourceFiles: List<String>) {
        sourceFiles.forEach { file ->
            if (file.endsWith(".kt") || file.endsWith(".java")) {
                extractDocStrings(file)
            }
        }
    }

    private fun extractDocStrings(filePath: String) {
        // Извлечение документации из исходного кода
        println("Анализ документации в: $filePath")
    }

    private fun processMarkdownFiles(docFiles: List<String>) {
        docFiles.forEach { file ->
            println("Обработка Markdown файла: $file")
        }
    }
}
```

### 3. Security Analysis Agent

```kotlin
class SecurityAnalysisAgent {
    private val scannerBridge = ProjectScannerAgentBridge()

    suspend fun performSecurityAnalysis(projectPath: String) {
        // Сканирование конфигурационных файлов
        val configResult = scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf(
                "*.properties",
                "*.yml",
                "*.yaml",
                "*.json",
                "*.gradle*",
                "pom.xml"
            )
        )

        // Сканирование исходного кода
        val sourceResult = scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf(
                "src/**/*.kt",
                "src/**/*.java",
                "src/**/*.js",
                "src/**/*.ts"
            ),
            excludePatterns = listOf("**/test/**")
        )

        if (configResult.success && sourceResult.success) {
            analyzeConfigurations(configResult.files)
            analyzeSourceCode(sourceResult.files)
        }

        // Подписка на изменения для непрерывного мониторинга
        startContinuousMonitoring(projectPath)
    }

    private fun analyzeConfigurations(configFiles: List<String>) {
        configFiles.forEach { file ->
            when {
                file.endsWith(".properties") -> analyzePropertiesFile(file)
                file.endsWith(".yml") || file.endsWith(".yaml") -> analyzeYamlFile(file)
                file.contains("gradle") -> analyzeGradleFile(file)
            }
        }
    }

    private fun analyzeSourceCode(sourceFiles: List<String>) {
        sourceFiles.forEach { file ->
            // Поиск потенциальных уязвимостей
            scanForVulnerabilities(file)
        }
    }

    private fun startContinuousMonitoring(projectPath: String) {
        scannerBridge.subscribeToFileChanges(
            agentId = "security-analyzer",
            projectPath = projectPath,
            includePatterns = listOf(
                "src/**/*",
                "*.properties",
                "*.yml",
                "*.yaml"
            )
        ) { changedFiles ->
            // Повторный анализ измененных файлов
            changedFiles.forEach { file ->
                when {
                    file.endsWith(".properties") -> analyzePropertiesFile(file)
                    file.endsWith(".yml") || file.endsWith(".yaml") -> analyzeYamlFile(file)
                    file.contains("src/") -> scanForVulnerabilities(file)
                }
            }
        }
    }

    private fun analyzePropertiesFile(filePath: String) {
        // Анализ security настроек в properties файлах
    }

    private fun analyzeYamlFile(filePath: String) {
        // Анализ security настроек в YAML файлах
    }

    private fun analyzeGradleFile(filePath: String) {
        // Анализ зависимостей и security настроек Gradle
    }

    private fun scanForVulnerabilities(filePath: String) {
        // Поиск потенциальных уязвимостей в исходном коде
    }
}
```

## ⚡ Оптимизации производительности

### 1. Кэширование результатов

```kotlin
class OptimizedAgent {
    private val scannerBridge = ProjectScannerAgentBridge()
    private val scanCache = mutableMapOf<String, ProjectScanResult>()

    suspend fun getProjectFiles(projectPath: String): List<String> {
        // Проверка кэша
        val cached = scanCache[projectPath]
        if (cached != null && isCacheValid(cached)) {
            return cached.files
        }

        // Сканирование при отсутствии в кэше
        val result = scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf("src/**/*.kt", "src/**/*.java")
        )

        if (result.success) {
            scanCache[projectPath] = result
        }

        return result.files
    }

    private fun isCacheValid(result: ProjectScanResult): Boolean {
        // Проверка актуальности кэша
        return (System.currentTimeMillis() - result.timestamp) < 5 * 60 * 1000 // 5 минут
    }
}
```

### 2. Пакетная обработка

```kotlin
class BatchProcessingAgent {
    private val scannerBridge = ProjectScannerAgentBridge()

    suspend fun processLargeProject(projectPath: String) {
        val batchSize = 200
        var currentPage = 1
        var hasMore = true

        while (hasMore) {
            // Используем прямой доступ к ProjectScanner для пагинации
            val scanStep = ToolPlanStep(
                description = "Пакетная обработка",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", projectPath)
                    .set("include_patterns", listOf("src/**/*.kt", "src/**/*.java"))
                    .set("page", currentPage)
                    .set("batch_size", batchSize)
            )

            val result = scannerAgent.executeStep(scanStep, context)
            val json = result.output.get<Map<String, Any>>("json")
            val files = json?.get("files") as? List<String>
            val batch = json?.get("batch") as? Map<String, Any>

            files?.let { processBatch(it) }
            hasMore = batch?.get("has_more") as? Boolean ?: false
            currentPage++
        }
    }

    private fun processBatch(files: List<String>) {
        // Обработка пакета файлов
        files.forEach { file ->
            processFile(file)
        }
    }

    private fun processFile(filePath: String) {
        // Обработка отдельного файла
    }
}
```

## 🚨 Обработка ошибок

### Базовая обработка ошибок

```kotlin
class RobustAgent {
    private val scannerBridge = ProjectScannerAgentBridge()

    suspend fun safeScanProject(projectPath: String): ProjectScanResult? {
        return try {
            val result = scannerBridge.scanProject(
                projectPath = projectPath,
                includePatterns = listOf("src/**/*.kt", "src/**/*.java")
            )

            if (!result.success) {
                logger.error("Ошибка сканирования: ${result.error}")
                // Попытка повторного сканирования с более мягкими параметрами
                retryWithFallbackParameters(projectPath)
            } else {
                result
            }
        } catch (e: Exception) {
            logger.error("Исключение при сканировании: ${e.message}")
            null
        }
    }

    private suspend fun retryWithFallbackParameters(projectPath: String): ProjectScanResult? {
        return try {
            scannerBridge.scanProject(
                projectPath = projectPath,
                includePatterns = listOf("src/**/*.kt"), // Только Kotlin файлы
                maxDepth = 10 // Ограниченная глубина
            )
        } catch (e: Exception) {
            logger.error("Повторная попытка не удалась: ${e.message}")
            null
        }
    }
}
```

### Обработка ошибок подписок

```kotlin
class SubscriptionAgent {
    private val scannerBridge = ProjectScannerAgentBridge()

    fun createRobustSubscription(projectPath: String) {
        try {
            val subscription = scannerBridge.subscribeToFileChanges(
                agentId = "robust-agent",
                projectPath = projectPath,
                includePatterns = listOf("src/**/*.kt", "src/**/*.java")
            ) { changedFiles ->
                try {
                    processChangedFiles(changedFiles)
                } catch (e: Exception) {
                    logger.error("Ошибка при обработке изменений: ${e.message}")
                }
            }

            // Хранение подписки для очистки
            subscriptions.add(subscription)

        } catch (e: Exception) {
            logger.error("Не удалось создать подписку: ${e.message}")
            // Использование альтернативного подхода
            startPolling(projectPath)
        }
    }

    private fun startPolling(projectPath: String) {
        // Fallback к опросу изменений
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkForChanges(projectPath)
            }
        }, 0, 30 * 1000) // Каждые 30 секунд
    }
}
```

## 📊 Метрики и мониторинг

### Мониторинг производительности

```kotlin
class MonitoredAgent {
    private val scannerBridge = ProjectScannerAgentBridge()

    suspend fun scanWithMetrics(projectPath: String): ProjectScanResult? {
        val startTime = System.currentTimeMillis()

        val result = scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf("src/**/*.kt", "src/**/*.java")
        )

        val duration = System.currentTimeMillis() - startTime

        if (result.success) {
            // Логирование метрик
            logger.info("Сканирование завершено за ${duration}ms")
            logger.info("Найдено файлов: ${result.files.size}")

            result.performanceMetrics?.let { metrics ->
                logger.info("Hit rate кэша: ${metrics.cacheHitRate}")
                logger.info("Среднее время на файл: ${metrics.avgTimePerFileUs}μs")
            }
        }

        return result
    }
}
```

## 🔗 Интеграция с оркестратором

### Использование в планах оркестрации

```kotlin
class OrchestratorIntegrationAgent {
    private val scannerBridge = ProjectScannerAgentBridge()

    suspend fun executeWithOrchestrator(projectPath: String) {
        // Автоматическая интеграция с оркестратором
        val scanStep = ToolPlanStep(
            description = "Подготовка анализа проекта",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", projectPath)
                .set("include_patterns", listOf("src/**/*.kt", "src/**/*.java"))
        )

        // Оркестратор автоматически создаст подписку для долгих планов
        val context = ExecutionContext(projectPath = projectPath)
        val result = scannerAgent.executeStep(scanStep, context)

        // Результаты будут автоматически закэшированы для следующих шагов
    }
}
```

---

## 📚 Дополнительные ресурсы

- [Руководство по использованию](../guides/project-scanner-usage-guide.md)
- [Примеры конфигурации](../guides/project-scanner-configuration-examples.md)
- [Roadmap Project Scanner](../roadmaps/16-enhanced-project-scanner.md)

---

<p align="center">
  <sub>API документация обновлена: 2025-10-25</sub>
</p>