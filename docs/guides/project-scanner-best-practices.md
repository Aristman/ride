# 📚 Лучшие практики использования Project Scanner

## 🎯 Обзор

Этот документ описывает лучшие практики, рекомендации и паттерны использования Project Scanner Tool Agent для достижения максимальной производительности, надежности и эффективности.

## 🏗️ Архитектурные принципы

### 1. Единственная ответственность

**Хорошо:**
```kotlin
class CodeAnalysisAgent {
    // Агент только анализирует код
    private val scannerBridge = ProjectScannerAgentBridge()

    suspend fun analyzeCode(projectPath: String) {
        val sourceFiles = scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf("src/**/*.kt", "src/**/*.java")
        )

        analyzeSourceFiles(sourceFiles.files)
    }

    private fun analyzeSourceFiles(files: List<String>) {
        // Логика анализа кода
    }
}
```

**Плохо:**
```kotlin
class MixedAgent {
    // Агент делает слишком много
    suspend fun doEverything(projectPath: String) {
        // Сканирование + анализ + документация + безопасность
        // Нарушение принципа единственной ответственности
    }
}
```

### 2. Инъекция зависимостей

**Хорошо:**
```kotlin
class DocumentGenerationAgent(
    private val scannerBridge: ProjectScannerAgentBridge,
    private val templateEngine: TemplateEngine
) {
    suspend fun generateDocs(projectPath: String) {
        val files = scannerBridge.scanProject(projectPath)
        templateEngine.process(files)
    }
}
```

### 3. Конфигурация через параметры

**Хорошо:**
```kotlin
data class ScanConfig(
    val includePatterns: List<String>,
    val excludePatterns: List<String> = emptyList(),
    val maxDepth: Int? = null,
    val batchSize: Int = 500
)

class ConfigurableAgent {
    suspend fun scanWithConfig(projectPath: String, config: ScanConfig) {
        scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = config.includePatterns,
            excludePatterns = config.excludePatterns,
            maxDepth = config.maxDepth
        )
    }
}
```

## ⚡ Оптимизация производительности

### 1. Эффективные паттерны фильтрации

**Хорошо:**
```kotlin
val optimalPatterns = listOf(
    "src/**/*.kt",
    "src/**/*.java",
    "*.gradle.kts"
)
```

**Плохо:**
```kotlin
val inefficientPatterns = listOf(
    "**/*", // Слишком общий паттерн
    "*"
)
```

### 2. Кэширование результатов

```kotlin
class CachedAgent {
    private val scanCache = LRUCache<String, ProjectScanResult>(maxSize = 100)
    private val cacheValidityMs = 5 * 60 * 1000 // 5 минут

    suspend fun getCachedScanResult(projectPath: String): ProjectScanResult? {
        val cached = scanCache[projectPath]
        if (cached != null && isCacheValid(cached)) {
            return cached
        }

        val result = scannerBridge.scanProject(projectPath)
        if (result.success) {
            scanCache[projectPath] = result
        }
        return result
    }

    private fun isCacheValid(result: ProjectScanResult): Boolean {
        return (System.currentTimeMillis() - result.timestamp) < cacheValidityMs
    }
}
```

### 3. Пакетная обработка больших проектов

```kotlin
class BatchProcessingAgent {
    private val optimalBatchSize = 200

    suspend fun processLargeProject(projectPath: String) {
        var currentPage = 1
        var hasMore = true

        while (hasMore) {
            val batch = scanBatch(projectPath, currentPage, optimalBatchSize)
            processBatch(batch.files)
            hasMore = batch.hasMore
            currentPage++
        }
    }

    private suspend fun scanBatch(
        projectPath: String,
        page: Int,
        batchSize: Int
    ): ScanBatch {
        // Использование пагинации
    }
}
```

### 4. Ленивая загрузка

```kotlin
class LazyAgent {
    private val scannerBridge by lazy { ProjectScannerAgentBridge() }
    private var lastScanResult: ProjectScanResult? = null

    suspend fun getFiles(): List<String> {
        if (lastScanResult == null) {
            lastScanResult = scannerBridge.scanProject(projectPath)
        }
        return lastScanResult?.files ?: emptyList()
    }
}
```

## 🔄 Управление ресурсами

### 1. Автоматическая очистка подписок

**Хорошо:**
```kotlin
class ResourceManager {
    private val activeSubscriptions = mutableListOf<AutoCloseable>()

    fun subscribeToChanges(projectPath: String) {
        val subscription = scannerBridge.subscribeToFileChanges(
            agentId = "resource-manager",
            projectPath = projectPath
        ) { files ->
            handleChange(files)
        }

        activeSubscriptions.add(subscription)
    }

    fun cleanup() {
        activeSubscriptions.forEach { it.close() }
        activeSubscriptions.clear()
    }
}
```

**Лучше (с использованием use):**
```kotlin
class AutoCleanupAgent {
    fun processProject(projectPath: String) {
        use(scannerBridge.subscribeToFileChanges("agent", projectPath) { files ->
            processChanges(files)
        }) {
            // Работа с подпиской
            // Автоматическая очистка при выходе из use-блока
        }
    }
}
```

### 2. Пулы подключений

```kotlin
class ScannerPool {
    private val bridgePool = mutableMapOf<String, ProjectScannerAgentBridge>()
    private val maxConnections = 5

    fun getBridge(agentId: String): ProjectScannerAgentBridge {
        if (bridgePool.size >= maxConnections) {
            cleanupIdleConnections()
        }

        return bridgePool.getOrPut(agentId) {
            ProjectScannerAgentBridge()
        }
    }

    private fun cleanupIdleConnections() {
        // Очистка неактивных подключений
    }
}
```

## 🛡️ Обработка ошибок

### 1. Graceful degradation

```kotlin
class ResilientAgent {
    suspend fun scanWithFallback(projectPath: String): ScanResult {
        return try {
            // Основная попытка
            val result = scannerBridge.scanProject(
                projectPath = projectPath,
                includePatterns = listOf("src/**/*.kt", "src/**/*.java", "**/*.xml")
            )

            if (result.success) {
                result
            } else {
                // Fallback с ограниченными паттернами
                fallbackScan(projectPath)
            }
        } catch (e: Exception) {
            logger.error("Основное сканирование не удалось: ${e.message}")
            fallbackScan(projectPath)
        }
    }

    private suspend fun fallbackScan(projectPath: String): ScanResult {
        return try {
            scannerBridge.scanProject(
                projectPath = projectPath,
                includePatterns = listOf("src/**/*.kt") // Только основные файлы
            )
        } catch (e: Exception) {
            logger.error("Fallback сканирование не удалось: ${e.message}")
            ScanResult.failure(e.message ?: "Неизвестная ошибка")
        }
    }
}
```

### 2. Повторные попытки с экспоненциальной задержкой

```kotlin
class RetryAgent {
    private val maxRetries = 3
    private val baseDelayMs = 1000L

    suspend fun scanWithRetry(projectPath: String): ProjectScanResult? {
        repeat(maxRetries) { attempt ->
            try {
                val result = scannerBridge.scanProject(projectPath)
                if (result.success) {
                    return result
                }

                if (attempt < maxRetries - 1) {
                    val delay = baseDelayMs * (2.0.pow(attempt).toLong())
                    logger.warn("Попытка ${attempt + 1} не удалась, повтор через ${delay}ms")
                    delay(delay)
                }
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    throw e
                }
                val delay = baseDelayMs * (2.0.pow(attempt).toLong())
                delay(delay)
            }
        }
        return null
    }
}
```

### 3. Circuit Breaker паттерн

```kotlin
class CircuitBreakerAgent {
    private var failureCount = 0
    private var lastFailureTime = 0L
    private val maxFailures = 5
    private val timeoutMs = 60_000L // 1 минута

    suspend fun scanWithCircuitBreaker(projectPath: String): ProjectScanResult? {
        if (isCircuitOpen()) {
            logger.warn("Circuit breaker открыт, пропускаем вызов")
            return null
        }

        return try {
            val result = scannerBridge.scanProject(projectPath)
            if (result.success) {
                resetCircuit()
                result
            } else {
                recordFailure()
                null
            }
        } catch (e: Exception) {
            recordFailure()
            null
        }
    }

    private fun isCircuitOpen(): Boolean {
        return failureCount >= maxFailures &&
                (System.currentTimeMillis() - lastFailureTime) < timeoutMs
    }

    private fun resetCircuit() {
        failureCount = 0
    }

    private fun recordFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
    }
}
```

## 📊 Мониторинг и логирование

### 1. Структурированное логирование

```kotlin
class MonitoredAgent {
    private val logger = LoggerFactory.getLogger(MonitoredAgent::class.java)

    suspend fun scanWithMetrics(projectPath: String) {
        val startTime = System.currentTimeMillis()

        logger.info("Начало сканирования проекта: $projectPath")

        val result = scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf("src/**/*.kt", "src/**/*.java")
        )

        val duration = System.currentTimeMillis() - startTime

        if (result.success) {
            logger.info(
                "Сканирование успешно завершено: {} файлов за {}ms",
                result.files.size,
                duration
            )

            result.performanceMetrics?.let { metrics ->
                logger.info(
                    "Метрики производительности: hit rate={:.1%}, avg time={}μs",
                    metrics.cacheHitRate,
                    metrics.avgTimePerFileUs
                )
            }
        } else {
            logger.error("Сканирование не удалось: ${result.error}")
        }
    }
}
```

### 2. Метрики производительности

```kotlin
class MetricsAgent {
    private val scanCounter = Counter.Builder()
        .name("project_scanner_scans_total")
        .help("Общее количество сканирований")
        .register()

    private val scanDuration = Histogram.Builder()
        .name("project_scanner_scan_duration_seconds")
        .help("Длительность сканирования")
        .register()

    suspend fun scanWithMetrics(projectPath: String) {
        val timer = Timer.start()
        val result = scannerBridge.scanProject(projectPath)
        timer.observe(scanDuration)

        scanCounter.inc()

        if (result.success) {
            logger.info("Сканирование успешно: ${result.files.size} файлов")
        }
    }
}
```

## 🔧 Конфигурация и настройка

### 1. Конфигурационные классы

```kotlin
@Configuration
data class ProjectScannerConfig(
    val defaultIncludePatterns: List<String> = listOf("**/*.kt", "**/*.java"),
    val defaultExcludePatterns: List<String> = listOf(
        "build/**",
        ".gradle/**",
        "node_modules/**",
        "**/generated/**"
    ),
    val maxDepth: Int = 20,
    val batchSize: Int = 500,
    val cacheValidityMinutes: Int = 5,
    val maxRetries: Int = 3,
    val enableMetrics: Boolean = true
)

class ConfigurableAgent(
    private val config: ProjectScannerConfig
) {
    suspend fun scanProject(projectPath: String): ProjectScanResult {
        return scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = config.defaultIncludePatterns,
            excludePatterns = config.defaultExcludePatterns,
            maxDepth = config.maxDepth
        )
    }
}
```

### 2. Валидация конфигурации

```kotlin
fun ProjectScannerConfig.validate(): List<String> {
    val errors = mutableListOf<String>()

    if (defaultIncludePatterns.isEmpty()) {
        errors.add("Должен быть хотя бы один include паттерн")
    }

    if (maxDepth <= 0) {
        errors.add("Максимальная глубина должна быть положительной")
    }

    if (batchSize <= 0 || batchSize > 1000) {
        errors.add("Размер пакета должен быть в диапазоне 1-1000")
    }

    return errors
}
```

## 🔄 Паттерны интеграции

### 1. Observer паттерн для подписок

```kotlin
interface FileChangeObserver {
    fun onFilesChanged(changedFiles: List<String>)
}

class ScannerSubject {
    private val observers = mutableListOf<FileChangeObserver>()
    private var subscription: AutoCloseable? = null

    fun addObserver(observer: FileChangeObserver) {
        observers.add(observer)
    }

    fun removeObserver(observer: FileChangeObserver) {
        observers.remove(observer)
    }

    fun startObserving(projectPath: String) {
        subscription = scannerBridge.subscribeToFileChanges(
            agentId = "scanner-subject",
            projectPath = projectPath
        ) { changedFiles ->
            notifyObservers(changedFiles)
        }
    }

    private fun notifyObservers(changedFiles: List<String>) {
        observers.forEach { observer ->
            try {
                observer.onFilesChanged(changedFiles)
            } catch (e: Exception) {
                logger.error("Ошибка в observer: ${e.message}")
            }
        }
    }

    fun stopObserving() {
        subscription?.close()
    }
}
```

### 2. Strategy паттерн для разных типов проектов

```kotlin
interface ScanStrategy {
    fun getIncludePatterns(): List<String>
    fun getExcludePatterns(): List<String>
    fun getMaxDepth(): Int?
}

class KotlinProjectStrategy : ScanStrategy {
    override fun getIncludePatterns() = listOf(
        "**/*.kt",
        "**/*.java",
        "*.gradle.kts",
        "gradle.properties"
    )

    override fun getExcludePatterns() = listOf(
        "build/**",
        ".gradle/**",
        "**/generated/**"
    )

    override fun getMaxDepth() = 20
}

class NodeProjectStrategy : ScanStrategy {
    override fun getIncludePatterns() = listOf(
        "src/**/*.js",
        "src/**/*.ts",
        "package.json",
        "tsconfig.json"
    )

    override fun getExcludePatterns() = listOf(
        "node_modules/**",
        "dist/**",
        "build/**"
    )

    override fun getMaxDepth() = 15
}

class StrategyAgent {
    fun detectStrategy(projectPath: String): ScanStrategy {
        return when {
            File(projectPath, "build.gradle.kts").exists() -> KotlinProjectStrategy()
            File(projectPath, "package.json").exists() -> NodeProjectStrategy()
            else -> GenericProjectStrategy()
        }
    }

    suspend fun scanWithStrategy(projectPath: String): ProjectScanResult {
        val strategy = detectStrategy(projectPath)

        return scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = strategy.getIncludePatterns(),
            excludePatterns = strategy.getExcludePatterns(),
            maxDepth = strategy.getMaxDepth()
        )
    }
}
```

### 3. Builder паттерн для сложных конфигураций

```kotlin
class ScanConfigBuilder {
    private var includePatterns: List<String> = emptyList()
    private var excludePatterns: List<String> = emptyList()
    private var maxDepth: Int? = null
    private var batchSize: Int = 500

    fun includeKotlin() = apply {
        includePatterns = includePatterns + listOf("**/*.kt", "*.kts")
    }

    fun includeJava() = apply {
        includePatterns = includePatterns + listOf("**/*.java")
    }

    fun includeJavaScript() = apply {
        includePatterns = includePatterns + listOf("**/*.js", "**/*.ts")
    }

    fun excludeBuildArtifacts() = apply {
        excludePatterns = excludePatterns + listOf(
            "build/**",
            "target/**",
            "dist/**",
            "node_modules/**"
        )
    }

    fun excludeGenerated() = apply {
        excludePatterns = excludePatterns + listOf("**/generated/**")
    }

    fun maxDepth(depth: Int) = apply {
        maxDepth = depth
    }

    fun batchSize(size: Int) = apply {
        batchSize = size
    }

    fun build(): ScanConfig {
        require(includePatterns.isNotEmpty()) {
            "Должен быть хотя бы один include паттерн"
        }

        return ScanConfig(
            includePatterns = includePatterns,
            excludePatterns = excludePatterns,
            maxDepth = maxDepth,
            batchSize = batchSize
        )
    }
}

// Использование:
val config = ScanConfigBuilder()
    .includeKotlin()
    .includeJava()
    .excludeBuildArtifacts()
    .excludeGenerated()
    .maxDepth(15)
    .batchSize(200)
    .build()
```

## 🚀 Продвинутые паттерны

### 1. Reactive подход с Flow

```kotlin
class ReactiveAgent {
    fun observeFileChanges(projectPath: String): Flow<List<String>> = flow {
        val subscription = scannerBridge.subscribeToFileChanges(
            agentId = "reactive-agent",
            projectPath = projectPath
        ) { changedFiles ->
            emit(changedFiles)
        }

        // Очистка при отмене flow
        awaitClose {
            subscription.close()
        }
    }.buffer(10) // Буферизация для обработки быстрых изменений
}

// Использование:
agent.observeFileChanges(projectPath)
    .debounce(1000) // Игнорировать быстрые изменения
    .distinctUntilChanged() // Избегать дубликатов
    .collect { changedFiles ->
        processChanges(changedFiles)
    }
```

### 2. Параллельная обработка

```kotlin
class ParallelAgent {
    suspend fun scanInParallel(projectPaths: List<String>): Map<String, ProjectScanResult> {
        return projectPaths.map { projectPath ->
            async {
                projectPath to scannerBridge.scanProject(
                    projectPath = projectPath,
                    includePatterns = listOf("src/**/*.kt", "src/**/*.java")
                )
            }
        }.awaitAll().toMap()
    }

    suspend fun processFilesInParallel(files: List<String>) {
        val chunkSize = 100
        files.chunked(chunkSize).forEach { chunk ->
            chunk.map { file ->
                async {
                    processFile(file)
                }
            }.awaitAll()
        }
    }
}
```

### 3. Композиция операций

```kotlin
class ComposableAgent {
    suspend fun analyzeProject(projectPath: String): ProjectAnalysis {
        // Композиция нескольких операций
        val sourceFiles = scanSourceFiles(projectPath)
        val configFiles = scanConfigFiles(projectPath)
        val dependencies = analyzeDependencies(projectPath)

        return ProjectAnalysis(
            sourceFiles = sourceFiles,
            configFiles = configFiles,
            dependencies = dependencies
        )
    }

    private suspend fun scanSourceFiles(projectPath: String): List<String> {
        return scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf("src/**/*.kt", "src/**/*.java")
        ).files
    }

    private suspend fun scanConfigFiles(projectPath: String): List<String> {
        return scannerBridge.scanProject(
            projectPath = projectPath,
            includePatterns = listOf("*.gradle*", "*.xml", "*.properties")
        ).files
    }

    private suspend fun analyzeDependencies(projectPath: String): DependencyAnalysis {
        // Анализ зависимостей
        return DependencyAnalysis(emptyList())
    }
}

data class ProjectAnalysis(
    val sourceFiles: List<String>,
    val configFiles: List<String>,
    val dependencies: DependencyAnalysis
)
```

## 🧪 Тестирование

### 1. Unit тестирование с моками

```kotlin
@Test
fun `should scan project with correct parameters`() = runTest {
    val mockBridge = mockk<ProjectScannerAgentBridge>()
    val agent = MyAgent(mockBridge)

    every {
        mockBridge.scanProject(
            projectPath = "/test/project",
            includePatterns = listOf("src/**/*.kt"),
            excludePatterns = listOf("build/**")
        )
    } returns ProjectScanResult.success(emptyList(), emptyList(), "KOTLIN")

    agent.analyzeProject("/test/project")

    verify {
        mockBridge.scanProject(
            projectPath = "/test/project",
            includePatterns = listOf("src/**/*.kt"),
            excludePatterns = listOf("build/**")
        )
    }
}
```

### 2. Интеграционные тесты

```kotlin
@Test
fun `should handle real project scanning`() = runTest {
    val tempDir = createTempDirectory("test-project")
    createTestProject(tempDir)

    try {
        val agent = RealAgent()
        val result = agent.analyzeProject(tempDir.toString())

        assertTrue(result.success)
        assertTrue(result.files.isNotEmpty())
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}
```

## 📋 Чек-лист лучших практик

### ✅ Обязательно к выполнению

- [ ] Использовать конкретные include паттерны вместо "**/*"
- [ ] Всегда исключать build artifacts (build/**, node_modules/**)
- [ ] Использовать пагинацию для проектов >1000 файлов
- [ ] Автоматически очищать подписки и ресурсы
- [ ] Обрабатывать ошибки gracefully с fallback стратегиями
- [ ] Логировать производительность и метрики
- [ ] Валидировать конфигурацию перед использованием

### ✅ Рекомендуется

- [ ] Использовать кэширование для повторных сканирований
- [ ] Реализовать retry логику для временных ошибок
- [ ] Использовать structured logging
- [ ] Применять circuit breaker для защиты от каскадных отказов
- [ ] Использовать dependency injection для тестирования
- [ ] Настраивать timeout'ы для долгих операций

### ✅ Для продвинутых

- [ ] Реализовывать reactive паттерны с Flow
- [ ] Использовать параллельную обработку для независимых операций
- [ ] Применять strategy паттерн для разных типов проектов
- [ ] Использовать builder паттерн для сложных конфигураций
- [ ] Реализовывать circuit breaker для устойчивости
- [ ] Использовать метрики для мониторинга производительности

---

## 📚 Дополнительные ресурсы

- [Руководство по использованию](project-scanner-usage-guide.md)
- [Примеры конфигурации](project-scanner-configuration-examples.md)
- [API документация](../api/project-scanner-api-for-agents.md)
- [Roadmap Project Scanner](../roadmaps/16-enhanced-project-scanner.md)

---

<p align="center">
  <sub>Лучшие практики обновлены: 2025-10-25</sub>
</p>