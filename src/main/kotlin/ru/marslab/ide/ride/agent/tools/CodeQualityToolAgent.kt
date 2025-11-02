package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File

/**
 * Агент для анализа качества кода и code smells
 *
 * Capabilities:
 * - code_quality_analysis - анализ качества
 * - code_smell_detection - поиск code smells
 * - complexity_analysis - анализ сложности
 */
class CodeQualityToolAgent : BaseToolAgent(
    agentType = AgentType.CODE_QUALITY,
    toolCapabilities = setOf(
        "code_quality_analysis",
        "code_smell_detection",
        "complexity_analysis"
    )
) {

    override fun getDescription(): String {
        return "Анализирует качество кода и выявляет code smells"
    }

    override fun validateInput(input: StepInput): ValidationResult {
        val files = input.getList<String>("files")
        val projectPath = input.getString("project_path")

        // Если есть файлы - отлично
        if (!files.isNullOrEmpty()) {
            return ValidationResult.success()
        }

        // Если нет файлов, но есть project_path - можем найти файлы сами
        if (projectPath != null) {
            logger.info("CODE_QUALITY: No files provided, but project_path available for scanning: $projectPath")
            return ValidationResult.success()
        }

        // Если нет ни файлов, ни project_path - это ошибка
        return ValidationResult.failure("Either 'files' or 'project_path' must be provided for quality analysis")
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val files = step.input.getList<String>("files") ?: emptyList()
        val checkComplexity = step.input.getBoolean("check_complexity") ?: true
        val maxComplexity = step.input.getInt("max_complexity") ?: 10

        // Входные логи
        logger.info("CODE_QUALITY input: files=${files.size}, check_complexity=${checkComplexity}, max_complexity=${maxComplexity}")
        files.take(10).forEach { logger.info("CODE_QUALITY file: $it") }

        // Если файлы не предоставлены, но есть project_path, попробуем найти файлы
        val filesToAnalyze = if (files.isEmpty()) {
            val projectPath = step.input.getString("project_path")
            if (projectPath != null) {
                logger.info("CODE_QUALITY: No files provided, scanning project directory: $projectPath")
                findProjectFiles(projectPath)
            } else {
                logger.warn("CODE_QUALITY: No files provided and no project_path available")
                emptyList()
            }
        } else {
            files
        }

        if (filesToAnalyze.isEmpty()) {
            logger.warn("CODE_QUALITY: No files to analyze, returning empty result")
            return StepResult.success(
                output = StepOutput(mapOf(
                    "findings" to emptyList<Finding>(),
                    "summary" to mapOf(
                        "total_files" to 0,
                        "issues_found" to 0,
                        "complexity_issues" to 0
                    ),
                    "message" to "No files available for quality analysis"
                ))
            )
        }

        val findings = mutableListOf<Finding>()
        val metrics = mutableMapOf<String, Any>()

        var totalLines = 0
        var totalMethods = 0
        var totalClasses = 0

        for (filePath in filesToAnalyze) {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                logger.warn("File does not exist: $filePath")
                continue
            }
            // Превью файла (усеченно)
            runCatching {
                val preview = file.readText().lineSequence().filter { it.isNotBlank() }.joinToString("\n").take(200)
                logger.info("CODE_QUALITY preview($filePath): ${'$'}preview ...")
            }

            val fileAnalysis = analyzeFile(file, checkComplexity, maxComplexity)
            findings.addAll(fileAnalysis.findings)
            logger.info("CODE_QUALITY end file: $filePath, findings=${fileAnalysis.findings.size}, lines=${fileAnalysis.lines}, methods=${fileAnalysis.methods}, classes=${fileAnalysis.classes}")
        }
        metrics["total_methods"] = totalMethods
        metrics["total_classes"] = totalClasses
        metrics["avg_lines_per_method"] = if (totalMethods > 0) totalLines / totalMethods else 0

        logger.info("Code quality analysis completed: ${findings.size} issues found")

        return StepResult.success(
            output = StepOutput.of(
                "findings" to findings,
                "metrics" to metrics,
                "total_issues" to findings.size
            ),
            metadata = mapOf(
                "files_analyzed" to files.size,
                "check_complexity" to checkComplexity
            )
        )
    }

    private fun analyzeFile(file: File, checkComplexity: Boolean, maxComplexity: Int): FileAnalysis {
        val findings = mutableListOf<Finding>()

        try {
            val lines = file.readLines()
            val nonEmptyLines = lines.count { it.trim().isNotEmpty() }
            var methodCount = 0
            var classCount = 0

            lines.forEachIndexed { index, line ->
                val lineNumber = index + 1
                val trimmed = line.trim()

                // Подсчет классов и методов
                if (trimmed.startsWith("class ") || trimmed.startsWith("data class ") ||
                    trimmed.startsWith("object ") || trimmed.startsWith("interface ")
                ) {
                    classCount++
                }

                if (trimmed.startsWith("fun ") || trimmed.contains(" fun ")) {
                    methodCount++
                }

                // Long lines
                if (line.length > 120) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.LOW,
                            category = "long_line",
                            message = "Слишком длинная строка (${line.length} символов)",
                            suggestion = "Разбейте строку на несколько для улучшения читаемости"
                        )
                    )
                }

                // Magic numbers
                val magicNumberRegex = Regex("""[^a-zA-Z_]\d{2,}[^a-zA-Z_]""")
                if (magicNumberRegex.containsMatchIn(line) && !trimmed.startsWith("//")) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.LOW,
                            category = "magic_number",
                            message = "Использование магического числа",
                            suggestion = "Вынесите число в именованную константу"
                        )
                    )
                }

                // Deep nesting
                val indentLevel = line.takeWhile { it == ' ' || it == '\t' }.length / 4
                if (indentLevel > 4) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.MEDIUM,
                            category = "deep_nesting",
                            message = "Глубокая вложенность (уровень $indentLevel)",
                            suggestion = "Рефакторинг для уменьшения вложенности"
                        )
                    )
                }

                // Commented code
                if (trimmed.startsWith("//") && trimmed.length > 10 &&
                    (trimmed.contains("fun ") || trimmed.contains("val ") || trimmed.contains("var "))
                ) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.LOW,
                            category = "commented_code",
                            message = "Закомментированный код",
                            suggestion = "Удалите неиспользуемый код"
                        )
                    )
                }
            }

            // Check for god classes
            if (nonEmptyLines > 500) {
                findings.add(
                    Finding(
                        file = file.absolutePath,
                        line = 1,
                        severity = Severity.HIGH,
                        category = "god_class",
                        message = "Слишком большой класс ($nonEmptyLines строк)",
                        suggestion = "Разбейте класс на несколько меньших"
                    )
                )
            }

            return FileAnalysis(
                findings = findings,
                lines = nonEmptyLines,
                methods = methodCount,
                classes = classCount
            )

        } catch (e: Exception) {
            logger.error("Error analyzing file ${file.absolutePath}", e)
            return FileAnalysis(emptyList(), 0, 0, 0)
        }
    }

    /**
     * Находит файлы для анализа в директории проекта
     */
    private fun findProjectFiles(projectPath: String): List<String> {
        try {
            val projectDir = File(projectPath)
            if (!projectDir.exists()) {
                logger.warn("CODE_QUALITY: Project directory does not exist: $projectPath")
                return emptyList()
            }

            val sourceExtensions = setOf("kt", "java", "scala", "groovy", "js", "ts", "py", "cpp", "c", "h")
            val maxFiles = 50 // Ограничим количество файлов для анализа

            val files = projectDir.walkTopDown()
                .filter { file ->
                    file.isFile &&
                    sourceExtensions.contains(file.extension.lowercase()) &&
                    !file.path.contains("/build/") &&
                    !file.path.contains("/out/") &&
                    !file.path.contains("/target/") &&
                    !file.path.contains("/node_modules/") &&
                    !file.path.contains("/.git/")
                }
                .take(maxFiles)
                .map { it.absolutePath }
                .toList()

            logger.info("CODE_QUALITY: Found ${files.size} files for analysis")
            return files

        } catch (e: Exception) {
            logger.error("CODE_QUALITY: Error scanning project directory: $projectPath", e)
            return emptyList()
        }
    }

    private data class FileAnalysis(
        val findings: List<Finding>,
        val lines: Int,
        val methods: Int,
        val classes: Int
    )
}
