package ru.marslab.ide.ride.agent.tools

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
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.streams.toList

/**
 * Агент для сканирования файловой системы проекта
 * 
 * Capabilities:
 * - file_discovery - поиск файлов
 * - pattern_matching - фильтрация по glob паттернам
 * - exclusion_filtering - исключение файлов
 */
class ProjectScannerToolAgent : BaseToolAgent(
    agentType = AgentType.PROJECT_SCANNER,
    toolCapabilities = setOf(
        "file_discovery",
        "pattern_matching",
        "exclusion_filtering"
    )
) {
    
    override fun getDescription(): String {
        return "Сканирует файловую систему проекта с поддержкой фильтрации по паттернам"
    }
    
    override fun validateInput(input: StepInput): ValidationResult {
        val patterns = input.getList<String>("patterns")
        
        if (patterns.isNullOrEmpty()) {
            return ValidationResult.failure("patterns is required and must not be empty")
        }
        
        return ValidationResult.success()
    }
    
    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val startTime = System.currentTimeMillis()
        
        val projectPath = context.projectPath 
            ?: return StepResult.error("Project path is not specified in context")
        
        val patterns = step.input.getList<String>("patterns") ?: emptyList()
        val excludePatterns = step.input.getList<String>("exclude_patterns") ?: emptyList()
        val maxDepth = step.input.getInt("max_depth") ?: Int.MAX_VALUE
        
        logger.info("Scanning project at $projectPath with patterns: $patterns")
        
        val projectDir = File(projectPath)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            return StepResult.error("Project path does not exist or is not a directory: $projectPath")
        }
        
        // Создаем PathMatchers для паттернов
        val includeMatchers = patterns.map { createGlobMatcher(it) }
        val excludeMatchers = excludePatterns.map { createGlobMatcher(it) }
        
        // Сканируем файлы
        val files = scanDirectory(
            projectDir.toPath(),
            includeMatchers,
            excludeMatchers,
            maxDepth
        )
        
        val scanTime = System.currentTimeMillis() - startTime
        
        logger.info("Scan completed: found ${files.size} files in ${scanTime}ms")
        
        return StepResult.success(
            output = StepOutput.of(
                "files" to files,
                "total_count" to files.size,
                "scan_time" to scanTime
            ),
            metadata = mapOf(
                "patterns" to patterns,
                "exclude_patterns" to excludePatterns,
                "project_path" to projectPath
            )
        )
    }
    
    private fun createGlobMatcher(pattern: String): PathMatcher {
        return FileSystems.getDefault().getPathMatcher("glob:$pattern")
    }
    
    private fun scanDirectory(
        root: Path,
        includeMatchers: List<PathMatcher>,
        excludeMatchers: List<PathMatcher>,
        maxDepth: Int
    ): List<String> {
        return try {
            Files.walk(root, maxDepth)
                .filter { it.isRegularFile() }
                .filter { path ->
                    val relativePath = root.relativize(path)
                    
                    // Проверяем exclude паттерны
                    val excluded = excludeMatchers.any { matcher ->
                        try {
                            matcher.matches(relativePath)
                        } catch (e: Exception) {
                            false
                        }
                    }
                    if (excluded) return@filter false
                    
                    // Проверяем include паттерны
                    if (includeMatchers.isEmpty()) return@filter true
                    
                    includeMatchers.any { matcher ->
                        try {
                            matcher.matches(relativePath)
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
                .map { it.pathString }
                .toList()
        } catch (e: Exception) {
            logger.error("Error scanning directory", e)
            emptyList()
        }
    }
}
