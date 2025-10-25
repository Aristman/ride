package ru.marslab.ide.ride.agent.tools

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.scanner.ProjectType
import ru.marslab.ide.ride.model.scanner.ScanSettings
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.StepOutput
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.scanner.DependencyAnalyzer
import ru.marslab.ide.ride.scanner.LanguageAnalyzer
import ru.marslab.ide.ride.scanner.ProjectFilterConfigProvider
import ru.marslab.ide.ride.scanner.ProjectTypeDetector
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.collections.chunked

/**
 * Улучшенный агент для сканирования файловой системы проекта
 *
 * Основная задача:
 * - Предоставляет данные о файловой системе для всего флоу
 * - Кэширует список файлов, директорий и структуру проекта
 * - Отслеживает изменения в файловой системе и обновляет кэш
 * - Возвращает структуру директорий в виде дерева и список файлов
 * - Умно определяет тип проекта и применяет соответствующие фильтры
 * - Поддерживает расширенные настройки сканирования
 *
 * Capabilities:
 * - file_discovery - поиск файлов
 * - directory_tree - построение дерева директорий
 * - cache_management - управление кэшем
 * - file_monitoring - отслеживание изменений
 * - project_type_detection - определение типа проекта
 * - adaptive_filtering - адаптивная фильтрация
 * - file_analysis - анализ файлов (размер, хэш, строки кода)
 * - multi_agent_integration - интеграция с другими агентами
 */
class ProjectScannerToolAgent : BaseToolAgent(
    agentType = AgentType.PROJECT_SCANNER,
    toolCapabilities = setOf(
        "file_discovery",
        "directory_tree",
        "cache_management",
        "file_monitoring",
        "project_type_detection",
        "adaptive_filtering",
        "file_analysis",
        "multi_agent_integration"
    )
) {

    // Кэш данных о проекте
    private val cache = ConcurrentHashMap<String, ProjectScanCache>()

    // Флаг инициализации file listener
    private var fileListenerInitialized = false

    // Project будет получен из ExecutionContext при первом вызове
    private var project: Project? = null

    // Система подписок на дельты (для поддержки запросов от других агентов)
    private val deltaSubscriptions = ConcurrentHashMap<String, DeltaSubscription>()

    // Улучшенная система индексации для быстрого поиска
    private val fileIndex = ConcurrentHashMap<String, MutableSet<String>>()
    private val extensionIndex = ConcurrentHashMap<String, MutableSet<String>>()
    private val languageIndex = ConcurrentHashMap<String, MutableSet<String>>()
    private val sizeIndex = ConcurrentHashMap<String, MutableSet<String>>()

    // Метрики производительности
    private var totalScanTime = 0L
    private var totalFilesScanned = 0L
    private var cacheHits = 0L
    private var cacheMisses = 0L

    init {
        // File listener будет инициализирован при первом получении project
    }

    override fun getDescription(): String {
        return "Сканирует файловую систему проекта, кэширует данные и отслеживает изменения"
    }

    override fun validateInput(input: StepInput): ValidationResult {
        // Валидация не требуется - агент работает с дефолтными значениями
        return ValidationResult.success()
    }

    // ==================== МЕТОДЫ ДЛЯ ИНДЕКСАЦИИ И ПРОИЗВОДИТЕЛЬНОСТИ ====================

    /**
     * Построение индексов для быстрого поиска файлов
     */
    private fun buildFileIndexes(fileInfos: List<EnhancedFileInfo>) {
        val startTime = System.currentTimeMillis()

        // Очищаем старые индексы
        fileIndex.clear()
        extensionIndex.clear()
        languageIndex.clear()
        sizeIndex.clear()

        // Строим новые индексы
        fileInfos.forEach { fileInfo ->
            val path = fileInfo.path

            // Индекс по расширению
            val extension = fileInfo.extension
            val extSet = extensionIndex.computeIfAbsent(extension) { mutableSetOf() }
            (extSet as MutableSet<String>).add(path)

            // Индекс по языку программирования
            val language = detectLanguageFromPath(path)
            val langSet = languageIndex.computeIfAbsent(language) { mutableSetOf() }
            (langSet as MutableSet<String>).add(path)

            // Индекс по размеру (категории)
            val sizeCategory = categorizeFileSize(fileInfo.size)
            val sizeSet = sizeIndex.computeIfAbsent(sizeCategory) { mutableSetOf() }
            (sizeSet as MutableSet<String>).add(path)

            // Главный индекс файлов
            fileIndex[path] = mutableSetOf(
                "ext:$extension",
                "lang:$language",
                "size:$sizeCategory",
                "dir:${File(path).parent}"
            )
        }

        val indexTime = System.currentTimeMillis() - startTime
        logger.info("Built file indexes: ${fileInfos.size} files indexed in ${indexTime}ms")

        // Обновляем метрики
        totalFilesScanned += fileInfos.size
    }

    /**
     * Определение языка программирования по пути файла
     */
    private fun detectLanguageFromPath(path: String): String {
        val extension = File(path).extension.lowercase()
        return when (extension) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "cpp", "cc", "cxx" -> "cpp"
            "c" -> "c"
            "cs" -> "csharp"
            "go" -> "go"
            "rs" -> "rust"
            "php" -> "php"
            "rb" -> "ruby"
            "swift" -> "swift"
            "scala" -> "scala"
            "groovy" -> "groovy"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "html" -> "html"
            "css" -> "css"
            "scss", "sass" -> "scss"
            "md" -> "markdown"
            "gradle" -> "gradle"
            "properties" -> "properties"
            "sql" -> "sql"
            "sh", "bash", "zsh" -> "shell"
            else -> "unknown"
        }
    }

    /**
     * Категоризация размера файла
     */
    private fun categorizeFileSize(size: Long): String = when {
        size < 1024 -> "tiny"
        size < 10_240 -> "small"
        size < 102_400 -> "medium"
        size < 1_048_576 -> "large"
        else -> "huge"
    }

    /**
     * Поиск файлов по множественным критериям
     */
    private fun searchFilesByCriteria(
        extensions: Set<String>? = null,
        languages: Set<String>? = null,
        sizeCategories: Set<String>? = null,
        directories: Set<String>? = null
    ): Set<String> {
        var result = fileIndex.keys.toSet()

        // Фильтрация по расширениям
        extensions?.let { exts ->
            val filtered = exts.flatMap { ext ->
                extensionIndex[ext]?.toList() ?: emptyList()
            }.toSet()
            result = result.intersect(filtered)
        }

        // Фильтрация по языкам
        languages?.let { langs ->
            val filtered = langs.flatMap { lang ->
                languageIndex[lang]?.toList() ?: emptyList()
            }.toSet()
            result = result.intersect(filtered)
        }

        // Фильтрация по размеру
        sizeCategories?.let { sizes ->
            val filtered = sizes.flatMap { size ->
                sizeIndex[size]?.toList() ?: emptyList()
            }.toSet()
            result = result.intersect(filtered)
        }

        // Фильтрация по директориям
        directories?.let { dirs ->
            result = result.filter { path ->
                dirs.any { dir -> path.startsWith(dir) }
            }.toSet()
        }

        return result
    }

    /**
     * Получение статистики производительности
     */
    private fun getPerformanceMetrics(): Map<String, Any> {
        val avgScanTime = if (totalFilesScanned > 0) totalScanTime / totalFilesScanned else 0L
        val cacheHitRate = if (cacheHits + cacheMisses > 0) {
            (cacheHits.toDouble() / (cacheHits + cacheMisses) * 100).toInt()
        } else 0

        return mapOf(
            "total_files_scanned" to totalFilesScanned,
            "total_scan_time_ms" to totalScanTime,
            "average_scan_time_per_file_ms" to avgScanTime,
            "cache_hits" to cacheHits,
            "cache_misses" to cacheMisses,
            "cache_hit_rate_percent" to cacheHitRate,
            "indexed_files" to fileIndex.size,
            "extension_index_size" to extensionIndex.size,
            "language_index_size" to languageIndex.size,
            "memory_optimization_enabled" to true,
            "index_memory_usage_mb" to estimateIndexMemoryUsage()
        )
    }

    /**
     * Оценка использования памяти индексами
     */
    private fun estimateIndexMemoryUsage(): Double {
        val indexSize = fileIndex.size + extensionIndex.size + languageIndex.size + sizeIndex.size
        // Приблизительная оценка: 100 байт на одну запись индекса
        return (indexSize * 100) / (1024.0 * 1024.0) // MB
    }

    /**
     * Оптимизация памяти: очистка старых записей кэша и индексов
     */
    private fun optimizeMemory() {
        val maxSize = 100 // Максимальное количество записей в кэше

        if (cache.size > maxSize) {
            // Сортируем по времени последнего доступа и удаляем самые старые
            val sortedEntries = cache.entries.sortedBy { it.value.timestamp }
            val toRemove = sortedEntries.take(cache.size - maxSize)

            toRemove.forEach { entry ->
                cache.remove(entry.key)
                logger.info("Removed old cache entry for project: ${entry.value.projectPath}")
            }
        }

        // Принудительная сборка мусора для освобождения памяти
        if (estimateIndexMemoryUsage() > 50.0) { // Если индексы занимают > 50MB
            logger.info("High memory usage detected, triggering garbage collection")
            System.gc()
        }
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val startTime = System.currentTimeMillis()

        val inputProjectPath = step.input.get<String>("project_path")
        val projectPath = inputProjectPath ?: context.projectPath
            ?: return StepResult.error("Project path is not specified (neither in input `project_path` nor in context)")

        // Инициализируем file listener если еще не инициализирован
        if (!fileListenerInitialized) {
            initializeFileListener()
        }

        // Парсим настройки сканирования
        val scanSettings = parseScanSettings(step.input)

        logger.info("Enhanced scanning project at $projectPath (force_rescan=${scanSettings.forceRescan})")

        val projectDir = File(projectPath)
        println("projectDir -> $projectDir")
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return StepResult.error("Project path does not exist or is not a directory: $projectPath")
        }

        // Определяем тип проекта
        val projectType = ProjectTypeDetector.detectProjectType(projectDir.toPath())
        logger.info("Detected project type: ${projectType.displayName}")

        // Получаем конфигурацию фильтрации
        val filterConfig = ProjectFilterConfigProvider.getFilterConfig(projectType)

        // Проверяем кэш
        val cacheKey = generateCacheKey(projectPath, scanSettings, projectType)
        val cachedData = cache[cacheKey]

        if (!scanSettings.forceRescan && cachedData != null && cachedData.isValid()) {
            // Обновляем метрики производительности для кэша
            cacheHits++
            logger.info("Using cached data for $projectPath (age: ${cachedData.getAgeSeconds()}s)")

            // Batching params
            val page = step.input.getInt("page") ?: 1
            val pageSize = step.input.getInt("batch_size") ?: 500
            val sinceTs = step.input.get<Long>("since_ts")
            val total = cachedData.files.size
            val from = ((page - 1) * pageSize).coerceAtLeast(0).coerceAtMost(total)
            val to = kotlin.math.min(from + pageSize, total)
            val filesPage = cachedData.files.subList(from, to)
            val hasMore = to < total

            // Delta
            val changedFiles: List<String> = sinceTs?.let { ts ->
                cachedData.fileIndex.entries.filter { it.value.lastModified > ts }.map { it.key }
            } ?: emptyList()

            // Добавляем метрики производительности даже для кэшированного результата
            val performanceMetrics = getPerformanceMetrics()

            val jsonResult = mapOf(
                "project" to mapOf(
                    "path" to projectPath,
                    "type" to projectType.name
                ),
                "batch" to mapOf(
                    "page" to page,
                    "batch_size" to pageSize,
                    "total" to total,
                    "has_more" to hasMore
                ),
                "files" to filesPage,
                "stats" to (cachedData.statistics),
                "directories_total" to cachedData.directories.size,
                "tree_included" to (page == 1),
                "directory_tree" to if (page == 1) cachedData.directoryTree else emptyMap<String, Any>(),
                "delta" to mapOf(
                    "since_ts" to (sinceTs ?: 0L),
                    "changed_files" to changedFiles
                ),
                "performance_metrics" to performanceMetrics
            )

            return StepResult.success(
                output = StepOutput.of(
                    "format" to "JSON",
                    "json" to jsonResult,
                    // Backward-compatible fields
                    "files" to filesPage,
                    "directory_tree" to (if (page == 1) cachedData.directoryTree else emptyMap<String, Any>()),
                    "project_type" to projectType.name,
                    "total_files" to total,
                    "total_directories" to cachedData.directories.size,
                    "from_cache" to true,
                    "cache_age_seconds" to cachedData.getAgeSeconds(),
                    "scan_statistics" to cachedData.statistics,
                    "performance_metrics" to performanceMetrics
                ),
                metadata = mapOf(
                    "project_path" to projectPath,
                    "project_type" to projectType.name,
                    "cached" to true,
                    "page" to page,
                    "batch_size" to pageSize,
                    "since_ts" to (sinceTs ?: 0L)
                )
            )
        }

        // Сканируем файловую систему
        logger.info("Performing enhanced scan of $projectPath")

        val scanResult = scanProjectStructureEnhanced(
            projectDir.toPath(),
            scanSettings,
            filterConfig,
            projectType
        )

        // Строим индексы для быстрого поиска (улучшение производительности)
        buildFileIndexes(scanResult.fileInfos)

        // Оптимизация памяти
        optimizeMemory()

        // Сохраняем в кэш
        val scanCache = ProjectScanCache(
            projectPath = projectPath,
            files = scanResult.files,
            directories = scanResult.directories,
            directoryTree = scanResult.directoryTree,
            timestamp = System.currentTimeMillis(),
            projectType = projectType,
            statistics = scanResult.statistics,
            fileIndex = scanResult.fileInfos.associateBy({ it.path }) { FileMeta(
                lastModified = it.lastModified,
                size = it.size,
                hash = it.hash
            ) }
        )
        cache[cacheKey] = scanCache

        val scanTime = System.currentTimeMillis() - startTime

        // Обновляем метрики производительности
        totalScanTime += scanTime
        cacheMisses++

        logger.info("Enhanced scan completed: ${scanResult.files.size} files, ${scanResult.directories.size} directories in ${scanTime}ms")

        // Batching params
        val page = step.input.getInt("page") ?: 1
        val pageSize = step.input.getInt("batch_size") ?: 500
        val total = scanResult.files.size
        val from = ((page - 1) * pageSize).coerceAtLeast(0).coerceAtMost(total)
        val to = kotlin.math.min(from + pageSize, total)
        val filesPage = scanResult.files.subList(from, to)
        val hasMore = to < total

        val sinceTs = step.input.get<Long>("since_ts")
        val changedFiles: List<String> = sinceTs?.let { ts ->
            scanResult.fileInfos.filter { it.lastModified > ts }.map { it.path }
        } ?: emptyList()

        val jsonResult = mapOf(
            "project" to mapOf(
                "path" to projectPath,
                "type" to projectType.name
            ),
            "batch" to mapOf(
                "page" to page,
                "batch_size" to pageSize,
                "total" to total,
                "has_more" to hasMore
            ),
            "files" to filesPage,
            "stats" to scanResult.statistics,
            "directories_total" to scanResult.directories.size,
            "tree_included" to (page == 1),
            "directory_tree" to if (page == 1) scanResult.directoryTree else emptyMap<String, Any>(),
            "delta" to mapOf(
                "since_ts" to (sinceTs ?: 0L),
                "changed_files" to changedFiles
            )
        )

        // Добавляем метрики производительности в результат
        val performanceMetrics = getPerformanceMetrics()
        val enhancedJsonResult = jsonResult + ("performance_metrics" to performanceMetrics)

        return StepResult.success(
            output = StepOutput.of(
                "format" to "JSON",
                "json" to enhancedJsonResult,
                // Backward-compatible fields
                "files" to filesPage,
                "directory_tree" to (if (page == 1) scanResult.directoryTree else emptyMap<String, Any>()),
                "project_type" to projectType.name,
                "project_structure" to generateProjectStructure(scanResult, projectType),
                "file_statistics" to scanResult.statistics,
                "total_files" to total,
                "total_directories" to scanResult.directories.size,
                "from_cache" to false,
                "scan_time_ms" to scanTime,
                "performance_metrics" to performanceMetrics
            ),
            metadata = mapOf(
                "project_path" to projectPath,
                "project_type" to projectType.name,
                "scan_settings" to scanSettings,
                "filter_config" to filterConfig,
                "cached" to false,
                "page" to page,
                "batch_size" to pageSize,
                "since_ts" to (sinceTs ?: 0L)
            )
        )
    }

    private fun createGlobMatcher(pattern: String): PathMatcher {
        return FileSystems.getDefault().getPathMatcher("glob:$pattern")
    }

    /**
     * Инициализирует file listener для отслеживания изменений
     */
    private fun initializeFileListener() {
        if (fileListenerInitialized) return

        val currentProject = project
        if (currentProject == null) {
            logger.warn("Project is null, file listener not initialized")
            return
        }

        try {
            currentProject.messageBus.connect().subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        // Проверяем, есть ли изменения в отслеживаемых проектах
                        val affectedProjects = events.mapNotNull { event ->
                            event.file?.path?.let { path ->
                                cache.keys.find { cacheKey -> path.startsWith(cacheKey) }
                            }
                        }.toSet()

                        // Обрабатываем изменения для каждого проекта
                        affectedProjects.forEach { projectPath ->
                            logger.info("File system changed in $projectPath, invalidating cache")

                            // Собираем измененные файлы
                            val changedFiles = events.mapNotNull { event ->
                                event.file?.path?.takeIf { it.startsWith(projectPath) }
                            }

                            // Инвалидируем кэш
                            cache[projectPath]?.invalidate()

                            // Уведомляем подписчиков о дельтах
                            if (changedFiles.isNotEmpty()) {
                                notifyDeltaSubscribers(projectPath, changedFiles)
                            }
                        }
                    }
                }
            )
            fileListenerInitialized = true
            logger.info("File listener initialized with delta subscription support")
        } catch (e: Exception) {
            logger.error("Failed to initialize file listener", e)
        }
    }

    /**
     * Сканирует структуру проекта
     */
    private fun scanProjectStructure(
        root: Path,
        includeMatchers: List<PathMatcher>,
        excludeMatchers: List<PathMatcher>,
        maxDepth: Int
    ): ScanResult {
        val files = mutableListOf<String>()
        val directories = mutableListOf<String>()
        val directoryTree = buildDirectoryTree(root, includeMatchers, excludeMatchers, maxDepth, files, directories)

        return ScanResult(
            files = files,
            directories = directories,
            directoryTree = directoryTree
        )
    }

    /**
     * Строит дерево директорий
     */
    private fun buildDirectoryTree(
        path: Path,
        includeMatchers: List<PathMatcher>,
        excludeMatchers: List<PathMatcher>,
        maxDepth: Int,
        files: MutableList<String>,
        directories: MutableList<String>,
        currentDepth: Int = 0
    ): Map<String, Any> {
        if (currentDepth > maxDepth) {
            return emptyMap()
        }

        val tree = mutableMapOf<String, Any>()
        tree["path"] = path.pathString
        tree["name"] = path.fileName?.toString() ?: ""
        tree["type"] = "directory"

        val children = mutableListOf<Map<String, Any>>()

        try {
            Files.list(path).use { stream ->
                stream.forEach { child ->
                    val relativePath = path.relativize(child)

                    // Проверяем exclude паттерны
                    val excluded = excludeMatchers.any { matcher ->
                        try {
                            matcher.matches(relativePath)
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (excluded) return@forEach

                    if (child.isDirectory()) {
                        directories.add(child.pathString)
                        val subtree = buildDirectoryTree(
                            child,
                            includeMatchers,
                            excludeMatchers,
                            maxDepth,
                            files,
                            directories,
                            currentDepth + 1
                        )
                        if (subtree.isNotEmpty()) {
                            children.add(subtree)
                        }
                    } else if (child.isRegularFile()) {
                        // Проверяем include паттерны (учитываем и полное относительное имя, и только имя файла)
                        val included = includeMatchers.isEmpty() || includeMatchers.any { matcher ->
                            try {
                                matcher.matches(relativePath) || matcher.matches(relativePath.fileName)
                            } catch (e: Exception) {
                                false
                            }
                        }

                        if (included) {
                            files.add(child.pathString)
                            children.add(
                                mapOf(
                                    "path" to child.pathString,
                                    "name" to child.fileName.toString(),
                                    "type" to "file",
                                    "size" to Files.size(child)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error scanning directory ${path.pathString}", e)
        }

        if (children.isNotEmpty()) {
            tree["children"] = children
        }

        return tree
    }

    /**
     * Парсит настройки сканирования из входных данных
     */
    private fun parseScanSettings(input: StepInput): ScanSettings {
        return ScanSettings(
            forceRescan = input.getBoolean("force_rescan") ?: false,
            includeHiddenFiles = input.getBoolean("include_hidden_files") ?: false,
            maxFileSize = input.get<Long>("max_file_size"),
            maxDirectoryDepth = input.getInt("max_directory_depth"),
            excludePatterns = input.getList<String>("exclude_patterns")
                ?: input.getList<String>("excludePatterns")
                ?: emptyList(),
            includePatterns = input.getList<String>("include_patterns")
                ?: input.getList<String>("includePatterns")
                ?: emptyList(),
            modifiedAfter = input.get<Long>("modified_after"),
            modifiedBefore = input.get<Long>("modified_before"),
            followSymlinks = input.getBoolean("follow_symlinks") ?: false,
            calculateFileHashes = input.getBoolean("calculate_file_hashes") ?: false,
            countLinesOfCode = input.getBoolean("count_lines_of_code") ?: false
        )
    }

    /**
     * Генерирует ключ кэша на основе настроек
     */
    private fun generateCacheKey(projectPath: String, settings: ScanSettings, projectType: ProjectType): String {
        val keyBuilder = StringBuilder(projectPath)
        keyBuilder.append(":${projectType.name}")

        if (settings.includeHiddenFiles) keyBuilder.append(":hidden")
        settings.maxFileSize?.let { keyBuilder.append(":maxSize=$it") }
        settings.maxDirectoryDepth?.let { keyBuilder.append(":maxDepth=$it") }
        if (settings.excludePatterns.isNotEmpty()) keyBuilder.append(":excludes=${settings.excludePatterns.size}")
        if (settings.includePatterns.isNotEmpty()) keyBuilder.append(":includes=${settings.includePatterns.size}")
        settings.modifiedAfter?.let { keyBuilder.append(":after=$it") }
        settings.modifiedBefore?.let { keyBuilder.append(":before=$it") }
        if (settings.calculateFileHashes) keyBuilder.append(":hashes")
        if (settings.countLinesOfCode) keyBuilder.append(":loc")

        return keyBuilder.toString()
    }

    /**
     * Улучшенное сканирование структуры проекта с учетом настроек и параллельной обработкой
     */
    private fun scanProjectStructureEnhanced(
        root: Path,
        settings: ScanSettings,
        filterConfig: ru.marslab.ide.ride.model.scanner.ProjectFilterConfig,
        projectType: ProjectType
    ): EnhancedScanResult {
        val files = ConcurrentHashMap.newKeySet<EnhancedFileInfo>()
        val directories = ConcurrentHashMap.newKeySet<String>()
        val statistics = ConcurrentHashMap<String, Any>()

        // Объединяем паттерны из настроек и конфигурации
        val allExcludePatterns = (settings.excludePatterns + filterConfig.excludePatterns).distinct()
        val allIncludePatterns = if (settings.includePatterns.isNotEmpty()) {
            settings.includePatterns
        } else {
            ProjectFilterConfigProvider.getSourceFilePatterns(projectType)
        }

        val excludeMatchers = allExcludePatterns.map { createGlobMatcher(it) }
        // Расширяем include-паттерны: добавляем вариант без "**/" для матчей по имени файла на верхних уровнях
        val expandedIncludePatterns = allIncludePatterns.flatMap { pattern ->
            if (pattern.contains("**/")) listOf(pattern, pattern.replace("**/", "")) else listOf(pattern)
        }.distinct()
        val includeMatchers = expandedIncludePatterns.map { createGlobMatcher(it) }

        // Используем пул потоков для параллельной обработки
        val processorCount = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(processorCount.coerceAtMost(8)) // Ограничиваем до 8 потоков

        try {
            val scanStartTime = System.currentTimeMillis()

            val directoryTree = buildDirectoryTreeParallel(
                root,
                root,
                includeMatchers,
                excludeMatchers,
                settings.maxDirectoryDepth ?: filterConfig.maxDirectoryDepth,
                files,
                directories,
                settings,
                filterConfig,
                statistics,
                executor,
                currentDepth = 0
            )

            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.MINUTES)

            // Собираем статистику
            val totalFiles = files.size
            val totalDirectories = directories.size
            val totalSize = files.sumOf { it.size }
            val languageStats = mutableMapOf<String, Int>()
            var totalLinesOfCode = 0

            files.forEach { file ->
                // Статистика по языкам
                val extension = file.extension.lowercase()
                if (extension.isNotEmpty()) {
                    languageStats[extension] = languageStats.getOrDefault(extension, 0) + 1
                }

                if (file.linesOfCode != null) {
                    totalLinesOfCode += file.linesOfCode
                }
            }

            val scanTime = System.currentTimeMillis() - scanStartTime

            // Анализ зависимостей проекта
            val dependencyAnalysis = DependencyAnalyzer.analyzeDependencies(root, projectType)

            // Расширенная статистика по языкам
            val languageCategories = mutableMapOf<String, Int>()
            files.forEach { file ->
                val category = LanguageAnalyzer.getLanguageCategory(file.language)
                languageCategories[category] = languageCategories.getOrDefault(category, 0) + 1
            }

            // Статистика по сложности файлов
            val complexityStats = mutableMapOf<String, Int>()
            files.forEach { file ->
                file.complexity?.let { complexity ->
                    complexityStats[complexity] = complexityStats.getOrDefault(complexity, 0) + 1
                }
            }

            statistics["total_files"] = totalFiles
            statistics["total_directories"] = totalDirectories
            statistics["total_size_bytes"] = totalSize
            statistics["total_size_mb"] = totalSize / (1024 * 1024)
            statistics["language_distribution"] = languageStats
            statistics["language_categories"] = languageCategories
            statistics["complexity_distribution"] = complexityStats
            statistics["total_lines_of_code"] = totalLinesOfCode
            statistics["total_code_lines"] = files.sumOf { it.codeLines ?: 0 }
            statistics["total_comment_lines"] = files.sumOf { it.commentLines ?: 0 }
            statistics["total_blank_lines"] = files.sumOf { it.blankLines ?: 0 }
            statistics["project_type"] = projectType.name
            statistics["scan_timestamp"] = System.currentTimeMillis()
            statistics["scan_time_ms"] = scanTime
            statistics["parallel_processing"] = true
            statistics["threads_used"] = processorCount

            // Информация о зависимостях
            statistics["dependencies"] = dependencyAnalysis.dependencies
            statistics["total_dependencies"] = dependencyAnalysis.totalDependencies
            statistics["build_tools"] = dependencyAnalysis.buildTools
            statistics["frameworks"] = dependencyAnalysis.frameworks
            statistics["testing_frameworks"] = dependencyAnalysis.testingFrameworks

            logger.info("Parallel scan completed: $totalFiles files, $totalDirectories directories in ${scanTime}ms")

            return EnhancedScanResult(
                files = files.map { it.path },
                directories = directories.toList(),
                directoryTree = directoryTree,
                statistics = statistics.toMap(),
                fileInfos = files.toList()
            )
        } catch (e: Exception) {
            executor.shutdownNow()
            logger.error("Error during parallel scan", e)
            throw e
        }
    }

    /**
     * Параллельное построение дерева директорий с оптимизацией для больших проектов
     */
    private fun buildDirectoryTreeParallel(
        rootPath: Path,
        path: Path,
        includeMatchers: List<PathMatcher>,
        excludeMatchers: List<PathMatcher>,
        maxDepth: Int,
        files: MutableSet<EnhancedFileInfo>,
        directories: MutableSet<String>,
        settings: ScanSettings,
        filterConfig: ru.marslab.ide.ride.model.scanner.ProjectFilterConfig,
        statistics: MutableMap<String, Any>,
        executor: java.util.concurrent.ExecutorService,
        currentDepth: Int
    ): Map<String, Any> {
        if (currentDepth > maxDepth) {
            return emptyMap()
        }

        val tree = mutableMapOf<String, Any>()
        tree["path"] = path.pathString
        tree["name"] = path.fileName?.toString() ?: ""
        tree["type"] = "directory"

        val children = Collections.synchronizedList(mutableListOf<Map<String, Any>>())

        try {
            val childPaths = Files.list(path).use { stream ->
                stream.toList()
            }

            // Группируем директории и файлы для оптимальной обработки
            val directoriesToProcess = mutableListOf<Path>()
            val filesToProcess = mutableListOf<Path>()

            childPaths.forEach { child ->
                val relativePath = path.relativize(child)

                // Проверяем exclude паттерны
                val excluded = excludeMatchers.any { matcher ->
                    try {
                        matcher.matches(relativePath)
                    } catch (e: Exception) {
                        false
                    }
                }

                if (excluded) return@forEach

                // Проверяем скрытые файлы
                if (!settings.includeHiddenFiles && child.fileName.toString().startsWith(".")) {
                    return@forEach
                }

                if (child.isDirectory()) {
                    directories.add(child.pathString)
                    directoriesToProcess.add(child)
                } else if (child.isRegularFile()) {
                    // Проверяем include паттерны (учитываем и полное относительное имя, и только имя файла)
                    val included = includeMatchers.isEmpty() || includeMatchers.any { matcher ->
                        try {
                            matcher.matches(relativePath) || matcher.matches(relativePath.fileName)
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (included) {
                        filesToProcess.add(child)
                    }
                }
            }

            // Обрабатываем директории рекурсивно (можно параллельно для больших деревьев)
            val futures = mutableListOf<java.util.concurrent.Future<Map<String, Any>>>()

            if (currentDepth < 2 && directoriesToProcess.size > 2) {
                // Параллельно обрабатываем директории на верхних уровнях
                directoriesToProcess.forEach { dir ->
                    val future = executor.submit<Map<String, Any>>(java.util.concurrent.Callable {
                        buildDirectoryTreeParallel(
                            rootPath,
                            dir,
                            includeMatchers,
                            excludeMatchers,
                            maxDepth,
                            files,
                            directories,
                            settings,
                            filterConfig,
                            statistics,
                            executor,
                            currentDepth + 1
                        )
                    })
                    futures.add(future)
                }

                // Собираем результаты
                futures.forEach { future ->
                    try {
                        val subtree = future.get(30, java.util.concurrent.TimeUnit.SECONDS)
                        if (subtree.isNotEmpty()) {
                            children.add(subtree)
                        }
                    } catch (e: Exception) {
                        logger.warn("Error processing directory in parallel: ${e.message}")
                    }
                }
            } else {
                // Последовательно обрабатываем директории на глубоких уровнях
                directoriesToProcess.forEach { dir ->
                    val subtree = buildDirectoryTreeParallel(
                        rootPath,
                        dir,
                        includeMatchers,
                        excludeMatchers,
                        maxDepth,
                        files,
                        directories,
                        settings,
                        filterConfig,
                        statistics,
                        executor,
                        currentDepth + 1
                    )
                    if (subtree.isNotEmpty()) {
                        children.add(subtree)
                    }
                }
            }

            // Обрабатываем файлы параллельно
            if (filesToProcess.isNotEmpty()) {
                val batchSize = 10 // Обрабатываем файлы пакетами для балансировки
                filesToProcess.chunked(batchSize).forEach { batch ->
                    executor.submit {
                        batch.forEach { child ->
                            try {
                                val attrs = Files.readAttributes(child, BasicFileAttributes::class.java)
                                val fileSize = attrs.size()

                                // Проверяем размер файла
                                val maxFileSize = settings.maxFileSize ?: filterConfig.maxFileSize
                                if (fileSize > maxFileSize) return@forEach

                                // Проверяем дату модификации
                                val lastModified = attrs.lastModifiedTime().toMillis()
                                settings.modifiedAfter?.let { if (lastModified < it) return@forEach }
                                settings.modifiedBefore?.let { if (lastModified > it) return@forEach }

                                // Анализируем язык и метрики кода
                                val language = LanguageAnalyzer.detectLanguage(child)
                                val codeMetrics = if (settings.countLinesOfCode) {
                                    LanguageAnalyzer.analyzeFile(child)
                                } else null

                                // Создаем информацию о файле
                                val fileInfo = EnhancedFileInfo(
                                    path = rootPath.relativize(child).pathString,
                                    name = child.fileName.toString(),
                                    size = fileSize,
                                    lastModified = lastModified,
                                    extension = child.fileName.toString().substringAfterLast('.', ""),
                                    language = language,
                                    linesOfCode = codeMetrics?.totalLines,
                                    codeLines = codeMetrics?.codeLines,
                                    commentLines = codeMetrics?.commentLines,
                                    blankLines = codeMetrics?.blankLines,
                                    complexity = codeMetrics?.complexity?.name,
                                    hash = if (settings.calculateFileHashes) calculateFileHash(child) else null
                                )

                                files.add(fileInfo)

                                children.add(
                                    mapOf(
                                        "path" to child.pathString,
                                        "name" to child.fileName.toString(),
                                        "type" to "file",
                                        "size" to fileSize,
                                        "extension" to fileInfo.extension,
                                        "language" to fileInfo.language,
                                        "last_modified" to lastModified,
                                        "lines_of_code" to (fileInfo.linesOfCode ?: 0),
                                        "code_lines" to (fileInfo.codeLines ?: 0),
                                        "comment_lines" to (fileInfo.commentLines ?: 0),
                                        "blank_lines" to (fileInfo.blankLines ?: 0),
                                        "complexity" to (fileInfo.complexity ?: "LOW"),
                                        "hash" to (fileInfo.hash ?: "")
                                    )
                                )
                            } catch (e: Exception) {
                                logger.warn("Error processing file ${child.pathString}: ${e.message}")
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            logger.error("Error scanning directory ${path.pathString}", e)
        }

        if (children.isNotEmpty()) {
            tree["children"] = children.toList()
        }

        return tree
    }

    /**
     * Подсчет строк кода в файле
     */
    private fun countLinesOfCode(filePath: Path): Int {
        return try {
            val content = Files.readString(filePath)
            val lines = content.lines()
            // Исключаем пустые строки и комментарии (базовая реализация)
            lines.count { lineContent ->
                val trimmedLine = lineContent.trim()
                trimmedLine.isNotEmpty() &&
                !trimmedLine.startsWith("//") &&
                !trimmedLine.startsWith("#") &&
                !trimmedLine.startsWith("/*") &&
                !trimmedLine.startsWith("*")
            }
        } catch (e: Exception) {
            logger.warn("Error counting lines in ${filePath.pathString}: ${e.message}")
            0
        }
    }

    /**
     * Вычисление хэша файла
     */
    private fun calculateFileHash(filePath: Path): String? {
        return try {
            val bytes = Files.readAllBytes(filePath)
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.warn("Error calculating hash for ${filePath.pathString}: ${e.message}")
            null
        }
    }

    /**
     * Генерирует структуру проекта для других агентов
     */
    private fun generateProjectStructure(scanResult: EnhancedScanResult, projectType: ProjectType): Map<String, Any> {
        val files = scanResult.statistics["total_files"] as Int
        val directories = scanResult.statistics["total_directories"] as Int
        val languageStats = scanResult.statistics["language_distribution"] as Map<String, Int>

        // Классификация файлов
        val sourceFiles = scanResult.files.count { path ->
            val ext = path.substringAfterLast('.').lowercase()
            ext in getSourceFileExtensions(projectType)
        }

        val testFiles = scanResult.files.count { path ->
            path.contains("test") || path.contains("spec") || path.contains("Test")
        }

        val configFiles = scanResult.files.count { path ->
            val ext = path.substringAfterLast('.').lowercase()
            ext in getConfigFileExtensions(projectType)
        }

        // Получаем расширенную статистику
        val totalCodeLines = scanResult.statistics["total_code_lines"] as? Int ?: 0
        val totalCommentLines = scanResult.statistics["total_comment_lines"] as? Int ?: 0
        val totalBlankLines = scanResult.statistics["total_blank_lines"] as? Int ?: 0
        val languageCategories = scanResult.statistics["language_categories"] as? Map<String, Int> ?: emptyMap()
        val complexityDistribution = scanResult.statistics["complexity_distribution"] as? Map<String, Int> ?: emptyMap()
        val dependencies = scanResult.statistics["dependencies"] as? List<*> ?: emptyList<Any>()
        val buildTools = scanResult.statistics["build_tools"] as? List<String> ?: emptyList()
        val frameworks = scanResult.statistics["frameworks"] as? List<String> ?: emptyList()
        val testingFrameworks = scanResult.statistics["testing_frameworks"] as? List<String> ?: emptyList()
        val totalDependencies = scanResult.statistics["total_dependencies"] as? Int ?: 0

        return mapOf(
            "type" to projectType.name,
            "root_path" to scanResult.statistics.getOrDefault("root_path", ""),
            "total_files" to files,
            "source_files" to sourceFiles,
            "test_files" to testFiles,
            "config_files" to configFiles,
            "directories" to directories,
            "language_distribution" to languageStats,
            "language_categories" to languageCategories,
            "complexity_distribution" to complexityDistribution,
            "code_metrics" to mapOf(
                "total_lines" to (totalCodeLines + totalCommentLines + totalBlankLines),
                "code_lines" to totalCodeLines,
                "comment_lines" to totalCommentLines,
                "blank_lines" to totalBlankLines,
                "comment_ratio" to if (totalCodeLines > 0) String.format("%.1f", (totalCommentLines.toDouble() / totalCodeLines) * 100) + "%" else "0%"
            ),
            "dependencies" to mapOf(
                "total_count" to totalDependencies,
                "build_tools" to buildTools,
                "frameworks" to frameworks,
                "testing_frameworks" to testingFrameworks,
                "dependencies_list" to dependencies.take(20) // Top 20 зависимостей
            ),
            "estimated_complexity" to when {
                files > 1000 -> "high"
                files > 500 -> "medium"
                else -> "low"
            },
            "maintainability_index" to calculateMaintainabilityIndex(totalCodeLines, totalCommentLines, complexityDistribution),
            "key_files" to extractKeyFiles(scanResult.files, projectType),
            "file_categories" to mapOf(
                "source" to sourceFiles,
                "test" to testFiles,
                "config" to configFiles,
                "other" to files - sourceFiles - testFiles - configFiles
            )
        )
    }

    /**
     * Получает расширения исходных файлов для типа проекта
     */
    private fun getSourceFileExtensions(projectType: ProjectType): Set<String> {
        return when (projectType) {
            ProjectType.MAVEN, ProjectType.GRADLE, ProjectType.GRADLE_KOTLIN,
            ProjectType.SPRING_BOOT, ProjectType.ANDROID -> setOf("java", "kt", "scala", "groovy")
            ProjectType.PYTHON -> setOf("py", "pyx", "pyi")
            ProjectType.NODE_JS -> setOf("js", "ts", "jsx", "tsx")
            ProjectType.RUST -> setOf("rs")
            else -> setOf("java", "kt", "py", "js", "ts", "rs", "cpp", "c", "h", "go")
        }
    }

    /**
     * Получает расширения конфигурационных файлов для типа проекта
     */
    private fun getConfigFileExtensions(projectType: ProjectType): Set<String> {
        return when (projectType) {
            ProjectType.MAVEN, ProjectType.GRADLE, ProjectType.GRADLE_KOTLIN,
            ProjectType.SPRING_BOOT -> setOf("xml", "properties", "yml", "yaml", "gradle", "kts")
            ProjectType.PYTHON -> setOf("cfg", "ini", "toml", "yaml", "yml", "json")
            ProjectType.NODE_JS -> setOf("json", "js", "yaml", "yml", "toml")
            ProjectType.RUST -> setOf("toml")
            else -> setOf("xml", "json", "yaml", "yml", "properties", "toml")
        }
    }

    /**
     * Расчитывает индекс поддерживаемости кода
     */
    private fun calculateMaintainabilityIndex(
        codeLines: Int,
        commentLines: Int,
        complexityDistribution: Map<String, Int>
    ): Map<String, Any> {
        val totalLines = codeLines + commentLines
        val commentRatio = if (codeLines > 0) (commentLines.toDouble() / codeLines) else 0.0

        val highComplexityFiles = complexityDistribution["VERY_HIGH"] ?: 0
        val mediumComplexityFiles = complexityDistribution["HIGH"] ?: 0
        val totalFiles = complexityDistribution.values.sum()

        val complexityScore = if (totalFiles > 0) {
            ((highComplexityFiles * 3.0 + mediumComplexityFiles * 2.0) / totalFiles)
        } else 0.0

        // Простая формула индекса поддерживаемости (0-100)
        val maintainabilityIndex = (100 - (complexityScore * 20) - (commentRatio.coerceAtMost(0.5) * -50)).coerceIn(0.0, 100.0)

        return mapOf(
            "score" to maintainabilityIndex.toInt(),
            "grade" to when {
                maintainabilityIndex >= 85 -> "A"
                maintainabilityIndex >= 70 -> "B"
                maintainabilityIndex >= 55 -> "C"
                maintainabilityIndex >= 40 -> "D"
                else -> "F"
            },
            "comment_ratio" to String.format("%.1f", commentRatio * 100) + "%",
            "complexity_penalty" to String.format("%.1f", complexityScore * 20),
            "factors" to mapOf(
                "comment_coverage" to if (commentRatio > 0.2) "Good" else if (commentRatio > 0.1) "Fair" else "Poor",
                "complexity_risk" to when {
                    highComplexityFiles > 0 -> "High"
                    mediumComplexityFiles > totalFiles * 0.3 -> "Medium"
                    else -> "Low"
                }
            )
        )
    }

    /**
     * Извлекает ключевые файлы проекта
     */
    private fun extractKeyFiles(files: List<String>, projectType: ProjectType): List<String> {
        val keyPatterns = when (projectType) {
            ProjectType.MAVEN -> listOf("pom.xml", "README.md", "LICENSE")
            ProjectType.GRADLE, ProjectType.GRADLE_KOTLIN -> listOf("build.gradle", "build.gradle.kts", "settings.gradle", "README.md")
            ProjectType.PYTHON -> listOf("requirements.txt", "setup.py", "pyproject.toml", "README.md")
            ProjectType.NODE_JS -> listOf("package.json", "README.md", "yarn.lock", "package-lock.json")
            ProjectType.RUST -> listOf("Cargo.toml", "README.md")
            else -> listOf("README.md", "LICENSE", "build.gradle", "pom.xml", "package.json")
        }

        return files.filter { file ->
            keyPatterns.any { pattern ->
                file.endsWith(pattern) || file.contains(pattern)
            }
        }.take(10) // Ограничиваем количество ключевых файлов
    }

    /**
     * Создает подписку на дельты для других агентов
     */
    fun createDeltaSubscription(
        agentId: String,
        projectPath: String,
        callback: (DeltaUpdate) -> Unit
    ): String {
        val subscriptionId = UUID.randomUUID().toString()
        val subscription = DeltaSubscription(
            id = subscriptionId,
            agentId = agentId,
            projectPath = projectPath,
            callback = callback,
            lastTimestamp = System.currentTimeMillis()
        )
        deltaSubscriptions[subscriptionId] = subscription
        logger.info("Created delta subscription $subscriptionId for agent $agentId on project $projectPath")
        return subscriptionId
    }

    /**
     * Отменяет подписку на дельты
     */
    fun cancelDeltaSubscription(subscriptionId: String): Boolean {
        val removed = deltaSubscriptions.remove(subscriptionId) != null
        if (removed) {
            logger.info("Cancelled delta subscription $subscriptionId")
        }
        return removed
    }

    /**
     * Уведомляет подписчиков об изменениях
     */
    private fun notifyDeltaSubscribers(projectPath: String, changedFiles: List<String>) {
        val relevantSubscriptions = deltaSubscriptions.values.filter { it.projectPath == projectPath }

        relevantSubscriptions.forEach { subscription ->
            try {
                val deltaUpdate = DeltaUpdate(
                    subscriptionId = subscription.id,
                    projectPath = projectPath,
                    timestamp = System.currentTimeMillis(),
                    changedFiles = changedFiles
                )
                subscription.callback(deltaUpdate)
            } catch (e: Exception) {
                logger.warn("Error notifying delta subscription ${subscription.id}: ${e.message}")
            }
        }
    }

    companion object {
        private val DEFAULT_EXCLUDE_PATTERNS = listOf(
            "**/node_modules/**",
            "**/.git/**",
            "**/.idea/**",
            "**/build/**",
            "**/target/**",
            "**/dist/**",
            "**/.gradle/**",
            "**/out/**",
            "**/__pycache__/**",
            "**/.venv/**",
            "**/venv/**"
        )
    }
}

/**
 * Расширенная информация о файле с аналитикой
 */
private data class EnhancedFileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val language: String,
    val linesOfCode: Int? = null,
    val codeLines: Int? = null,
    val commentLines: Int? = null,
    val blankLines: Int? = null,
    val complexity: String? = null,
    val hash: String? = null
)

/**
 * Расширенный результат сканирования проекта
 */
private data class EnhancedScanResult(
    val files: List<String>,
    val directories: List<String>,
    val directoryTree: Map<String, Any>,
    val statistics: Map<String, Any>,
    val fileInfos: List<EnhancedFileInfo>
)

/**
 * Результат сканирования проекта (устаревший, для обратной совместимости)
 */
private data class ScanResult(
    val files: List<String>,
    val directories: List<String>,
    val directoryTree: Map<String, Any>
)

/**
 * Метаданные файла для дельта-выдачи
 */
private data class FileMeta(
    val lastModified: Long,
    val size: Long,
    val hash: String?
)

/**
 * Улучшенный кэш данных о проекте
 */
private data class ProjectScanCache(
    val projectPath: String,
    val files: List<String>,
    val directories: List<String>,
    val directoryTree: Map<String, Any>,
    val timestamp: Long,
    val projectType: ProjectType = ProjectType.UNKNOWN,
    val statistics: Map<String, Any> = emptyMap(),
    val fileIndex: Map<String, FileMeta> = emptyMap(),
    @Volatile var valid: Boolean = true
) {
    fun isValid(): Boolean = valid && (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS

    fun invalidate() {
        valid = false
    }

    fun getAgeSeconds(): Long = (System.currentTimeMillis() - timestamp) / 1000

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 минут
    }
}

/**
 * Подписка на дельты изменений файлов
 */
private data class DeltaSubscription(
    val id: String,
    val agentId: String,
    val projectPath: String,
    val callback: (DeltaUpdate) -> Unit,
    var lastTimestamp: Long
)

/**
 * Обновление с дельтой изменений
 */
data class DeltaUpdate(
    val subscriptionId: String,
    val projectPath: String,
    val timestamp: Long,
    val changedFiles: List<String>
)
