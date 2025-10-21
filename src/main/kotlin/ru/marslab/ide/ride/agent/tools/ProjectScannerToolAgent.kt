package ru.marslab.ide.ride.agent.tools

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.streams.toList

/**
 * Агент для сканирования файловой системы проекта
 * 
 * Основная задача:
 * - Предоставляет данные о файловой системе для всего флоу
 * - Кэширует список файлов, директорий и структуру проекта
 * - Отслеживает изменения в файловой системе и обновляет кэш
 * - Возвращает структуру директорий в виде дерева и список файлов
 * 
 * Capabilities:
 * - file_discovery - поиск файлов
 * - directory_tree - построение дерева директорий
 * - cache_management - управление кэшем
 * - file_monitoring - отслеживание изменений
 */
class ProjectScannerToolAgent : BaseToolAgent(
    agentType = AgentType.PROJECT_SCANNER,
    toolCapabilities = setOf(
        "file_discovery",
        "directory_tree",
        "cache_management",
        "file_monitoring"
    )
) {
    
    // Кэш данных о проекте
    private val cache = ConcurrentHashMap<String, ProjectScanCache>()
    
    // Флаг инициализации file listener
    private var fileListenerInitialized = false
    
    // Project будет получен из ExecutionContext при первом вызове
    private var project: Project? = null
    
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
    
    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val startTime = System.currentTimeMillis()
        
        val projectPath = context.projectPath 
            ?: return StepResult.error("Project path is not specified in context")
        
        // Инициализируем file listener если еще не инициализирован
        if (!fileListenerInitialized) {
            initializeFileListener()
        }
        
        // Флаг принудительного пересканирования
        val forceRescan = step.input.getBoolean("force_rescan") ?: false
        
        val patterns = step.input.getList<String>("patterns") ?: listOf("**/*.kt", "**/*.java", "**/*.py", "**/*.js", "**/*.ts")
        val excludePatterns = step.input.getList<String>("exclude_patterns") ?: DEFAULT_EXCLUDE_PATTERNS
        val maxDepth = step.input.getInt("max_depth") ?: Int.MAX_VALUE
        
        logger.info("Scanning project at $projectPath (force_rescan=$forceRescan)")
        
        val projectDir = File(projectPath)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return StepResult.error("Project path does not exist or is not a directory: $projectPath")
        }
        
        // Проверяем кэш
        val cacheKey = projectPath
        val cachedData = cache[cacheKey]
        
        if (!forceRescan && cachedData != null && cachedData.isValid()) {
            logger.info("Using cached data for $projectPath (age: ${cachedData.getAgeSeconds()}s)")
            
            return StepResult.success(
                output = StepOutput.of(
                    "files" to cachedData.files,
                    "directory_tree" to cachedData.directoryTree,
                    "total_files" to cachedData.files.size,
                    "total_directories" to cachedData.directories.size,
                    "from_cache" to true,
                    "cache_age_seconds" to cachedData.getAgeSeconds()
                ),
                metadata = mapOf(
                    "project_path" to projectPath,
                    "cached" to true
                )
            )
        }
        
        // Сканируем файловую систему
        logger.info("Performing full scan of $projectPath")
        
        val includeMatchers = patterns.map { createGlobMatcher(it) }
        val excludeMatchers = excludePatterns.map { createGlobMatcher(it) }
        
        val scanResult = scanProjectStructure(
            projectDir.toPath(),
            includeMatchers,
            excludeMatchers,
            maxDepth
        )
        
        // Сохраняем в кэш
        val scanCache = ProjectScanCache(
            projectPath = projectPath,
            files = scanResult.files,
            directories = scanResult.directories,
            directoryTree = scanResult.directoryTree,
            timestamp = System.currentTimeMillis()
        )
        cache[cacheKey] = scanCache
        
        val scanTime = System.currentTimeMillis() - startTime
        
        logger.info("Scan completed: ${scanResult.files.size} files, ${scanResult.directories.size} directories in ${scanTime}ms")
        
        return StepResult.success(
            output = StepOutput.of(
                "files" to scanResult.files,
                "directory_tree" to scanResult.directoryTree,
                "total_files" to scanResult.files.size,
                "total_directories" to scanResult.directories.size,
                "from_cache" to false,
                "scan_time_ms" to scanTime
            ),
            metadata = mapOf(
                "project_path" to projectPath,
                "patterns" to patterns,
                "exclude_patterns" to excludePatterns,
                "cached" to false
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
                        
                        // Инвалидируем кэш для затронутых проектов
                        affectedProjects.forEach { projectPath ->
                            logger.info("File system changed in $projectPath, invalidating cache")
                            cache[projectPath]?.invalidate()
                        }
                    }
                }
            )
            fileListenerInitialized = true
            logger.info("File listener initialized")
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
                        // Проверяем include паттерны
                        val included = includeMatchers.isEmpty() || includeMatchers.any { matcher ->
                            try {
                                matcher.matches(relativePath)
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
 * Результат сканирования проекта
 */
private data class ScanResult(
    val files: List<String>,
    val directories: List<String>,
    val directoryTree: Map<String, Any>
)

/**
 * Кэш данных о проекте
 */
private data class ProjectScanCache(
    val projectPath: String,
    val files: List<String>,
    val directories: List<String>,
    val directoryTree: Map<String, Any>,
    val timestamp: Long,
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
