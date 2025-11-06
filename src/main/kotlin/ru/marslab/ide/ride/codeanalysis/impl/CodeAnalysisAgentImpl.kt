package ru.marslab.ide.ride.codeanalysis.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.Flow
import ru.marslab.ide.ride.codeanalysis.CodeAnalysisAgent
import ru.marslab.ide.ride.codeanalysis.analyzer.ArchitectureAnalyzer
import ru.marslab.ide.ride.codeanalysis.analyzer.BugDetectionAnalyzer
import ru.marslab.ide.ride.codeanalysis.analyzer.CodeQualityAnalyzer
import ru.marslab.ide.ride.codeanalysis.chunker.CodeChunker
import ru.marslab.ide.ride.codeanalysis.formatter.AnalysisResultFormatter
import ru.marslab.ide.ride.codeanalysis.scanner.ProjectScanner
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.impl.TiktokenCounter
import ru.marslab.ide.ride.model.agent.*
import ru.marslab.ide.ride.model.codeanalysis.*
import java.io.File
import java.time.LocalDateTime

/**
 * Реализация агента для анализа кода
 */
class CodeAnalysisAgentImpl(
    project: Project,
    llmProvider: LLMProvider
) : CodeAnalysisAgent {

    private val projectScanner = ProjectScanner(project)
    private val tokenCounter = TiktokenCounter()
    private val codeChunker = CodeChunker(tokenCounter, maxTokensPerChunk = 4000)
    private val bugDetectionAnalyzer = BugDetectionAnalyzer(llmProvider)
    private val architectureAnalyzer = ArchitectureAnalyzer(llmProvider)
    private val codeQualityAnalyzer = CodeQualityAnalyzer(llmProvider)
    private val resultFormatter = AnalysisResultFormatter()

    override val capabilities: AgentCapabilities = AgentCapabilities(
        stateful = false,
        streaming = false,
        reasoning = false,
        tools = setOf("code_analysis", "bug_detection", "architecture_analysis"),
        systemPrompt = "Агент для анализа кода: поиск багов, анализ архитектуры, оценка качества"
    )

    override suspend fun ask(req: AgentRequest): AgentResponse {
        return AgentResponse.error("Используйте специализированные методы analyzeProject или analyzeFile")
    }

    override fun start(req: AgentRequest): Flow<AgentEvent>? = null

    override fun updateSettings(settings: AgentSettings) {
        // Обновление настроек при необходимости
    }

    override fun dispose() {
        // Освобождение ресурсов
    }

    override suspend fun analyzeProject(request: CodeAnalysisRequest): CodeAnalysisResult {
        println("=== CodeAnalysisAgent: Starting project analysis ===")
        println("Project path: ${request.projectPath}")
        println("Analysis types: ${request.analysisTypes}")
        println("File patterns: ${request.filePatterns}")
        println("Exclude patterns: ${request.excludePatterns}")

        // 1. Сканируем проект
        println("\n[1/8] Scanning project files...")
        val files = projectScanner.scanProject(
            request.filePatterns,
            request.excludePatterns
        )
        println("Found ${files.size} files to analyze")

        if (files.isEmpty()) {
            println("WARNING: No files found! Check file patterns and project path.")
        }

        // 2. Приоритизируем файлы
        println("\n[2/8] Prioritizing files...")
        val prioritizedFiles = prioritizeFiles(files)
        println("Files prioritized: ${prioritizedFiles.size}")

        // 3. Разбиваем на батчи
        println("\n[3/8] Creating batches...")
        val batches = prioritizedFiles.chunked(request.maxFilesPerBatch)
        println("Created ${batches.size} batches (max ${request.maxFilesPerBatch} files per batch)")

        val allFindings = mutableListOf<Finding>()

        // 4. Обрабатываем батчами
        println("\n[4/8] Analyzing batches...")
        for ((index, batch) in batches.withIndex()) {
            println("  Processing batch ${index + 1}/${batches.size} (${batch.size} files)...")

            val batchFindings = analyzeBatch(batch, request.analysisTypes)
            println("  Batch ${index + 1} completed: found ${batchFindings.size} issues")
            allFindings.addAll(batchFindings)
        }
        println("Total findings from all batches: ${allFindings.size}")

        // 5. Строим структуру проекта если запрошено
        println("\n[5/8] Building project structure...")
        val structure = if (AnalysisType.ARCHITECTURE in request.analysisTypes ||
            AnalysisType.ALL in request.analysisTypes
        ) {
            println("  Architecture analysis requested")
            val struct = architectureAnalyzer.analyze(files, request.projectPath)
            println("  Found ${struct.modules.size} modules, ${struct.layers.size} layers")
            struct
        } else {
            println("  Architecture analysis skipped")
            null
        }

        // 6. Вычисляем метрики
        println("\n[6/8] Calculating metrics...")
        val metrics = calculateMetrics(files, allFindings)
        println("  Metrics: ${metrics.totalFiles} files, ${metrics.totalLines} lines, ${metrics.totalClasses} classes")

        // 7. Генерируем summary
        println("\n[7/8] Generating summary...")
        val summary = generateSummary(allFindings, metrics)
        println("  Summary generated (${summary.length} chars)")

        // 8. Генерируем рекомендации
        println("\n[8/8] Generating recommendations...")
        val recommendations = generateRecommendations(allFindings, structure)
        println("  Generated ${recommendations.size} recommendations")

        val result = CodeAnalysisResult(
            projectName = File(request.projectPath).name,
            analysisDate = LocalDateTime.now(),
            findings = allFindings,
            projectStructure = structure,
            metrics = metrics,
            summary = summary,
            recommendations = recommendations
        )

        println("\n=== CodeAnalysisAgent: Analysis completed ===")
        println("Total findings: ${result.findings.size}")
        println("Project: ${result.projectName}")

        return result
    }

    override suspend fun analyzeFile(filePath: String, analysisTypes: Set<AnalysisType>): List<Finding> {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return emptyList()
        }

        val content = file.readText()
        val findings = mutableListOf<Finding>()

        if (AnalysisType.BUG_DETECTION in analysisTypes || AnalysisType.ALL in analysisTypes) {
            findings.addAll(bugDetectionAnalyzer.analyze(content, filePath))
        }

        if (AnalysisType.CODE_QUALITY in analysisTypes || AnalysisType.ALL in analysisTypes) {
            findings.addAll(codeQualityAnalyzer.analyze(content, filePath))
        }

        return findings
    }

    override suspend fun buildProjectStructure(projectPath: String): ProjectStructure {
        val files = projectScanner.scanProject(
            listOf("**/*.kt", "**/*.java"),
            listOf("**/build/**", "**/test/**")
        )
        return architectureAnalyzer.analyze(files, projectPath)
    }

    override fun generateReport(result: CodeAnalysisResult, format: ReportFormat): String {
        return resultFormatter.format(result, format)
    }

    /**
     * Приоритизирует файлы для анализа
     */
    private fun prioritizeFiles(files: List<VirtualFile>): List<VirtualFile> {
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

    /**
     * Анализирует батч файлов
     */
    private suspend fun analyzeBatch(batch: List<VirtualFile>, analysisTypes: Set<AnalysisType>): List<Finding> {
        println("    analyzeBatch: Starting analysis of ${batch.size} files")
        println("    Analysis types: $analysisTypes")
        val findings = mutableListOf<Finding>()

        for ((index, file) in batch.withIndex()) {
            try {
                println("      [${index + 1}/${batch.size}] Analyzing: ${file.name}")
                val content = String(file.contentsToByteArray())
                val filePath = file.path
                println("        File size: ${content.length} chars")

                if (AnalysisType.BUG_DETECTION in analysisTypes || AnalysisType.ALL in analysisTypes) {
                    println("        Running BugDetectionAnalyzer...")
                    val bugFindings = bugDetectionAnalyzer.analyze(content, filePath)
                    println("        BugDetectionAnalyzer found ${bugFindings.size} issues")
                    findings.addAll(bugFindings)
                }

                if (AnalysisType.CODE_QUALITY in analysisTypes || AnalysisType.ALL in analysisTypes) {
                    println("        Running CodeQualityAnalyzer...")
                    val qualityFindings = codeQualityAnalyzer.analyze(content, filePath)
                    println("        CodeQualityAnalyzer found ${qualityFindings.size} issues")
                    findings.addAll(qualityFindings)
                }
            } catch (e: Exception) {
                println("        ERROR analyzing file ${file.name}: ${e.message}")
                e.printStackTrace()
                // Пропускаем файлы с ошибками
                continue
            }
        }

        println("    analyzeBatch: Completed with ${findings.size} total findings")
        return findings
    }

    /**
     * Вычисляет метрики кода
     */
    private fun calculateMetrics(files: List<VirtualFile>, findings: List<Finding>): CodeMetrics {
        var totalLines = 0
        var totalClasses = 0
        var totalFunctions = 0

        for (file in files) {
            try {
                val content = String(file.contentsToByteArray())
                totalLines += content.lines().size

                // Простой подсчет классов и функций
                totalClasses += content.split(Regex("\\bclass\\b|\\binterface\\b|\\bobject\\b")).size - 1
                totalFunctions += content.split(Regex("\\bfun\\b|\\bfunction\\b|\\bdef\\b")).size - 1
            } catch (e: Exception) {
                continue
            }
        }

        val averageComplexity = if (totalFunctions > 0) {
            findings.size.toDouble() / totalFunctions
        } else 0.0

        return CodeMetrics(
            totalFiles = files.size,
            totalLines = totalLines,
            totalClasses = totalClasses,
            totalFunctions = totalFunctions,
            averageComplexity = averageComplexity,
            testCoverage = null
        )
    }

    /**
     * Генерирует краткое резюме
     */
    private fun generateSummary(findings: List<Finding>, metrics: CodeMetrics): String {
        val criticalCount = findings.count { it.severity == Severity.CRITICAL }
        val highCount = findings.count { it.severity == Severity.HIGH }
        val mediumCount = findings.count { it.severity == Severity.MEDIUM }

        return buildString {
            appendLine("Проанализировано ${metrics.totalFiles} файлов (${metrics.totalLines} строк кода).")
            appendLine("Найдено ${findings.size} проблем:")
            appendLine("- Критических: $criticalCount")
            appendLine("- Высокий приоритет: $highCount")
            appendLine("- Средний приоритет: $mediumCount")

            if (criticalCount > 0) {
                appendLine("\n⚠️ Обнаружены критические проблемы, требующие немедленного внимания!")
            }
        }
    }

    /**
     * Генерирует рекомендации
     */
    private fun generateRecommendations(findings: List<Finding>, structure: ProjectStructure?): List<String> {
        val recommendations = mutableListOf<String>()

        val criticalFindings = findings.filter { it.severity == Severity.CRITICAL }
        if (criticalFindings.isNotEmpty()) {
            recommendations.add("Исправьте ${criticalFindings.size} критических проблем в первую очередь")
        }

        val bugFindings = findings.filter { it.type == FindingType.BUG }
        if (bugFindings.size > 10) {
            recommendations.add("Обнаружено много потенциальных багов (${bugFindings.size}). Рекомендуется code review")
        }

        val codeSmells = findings.filter { it.type == FindingType.CODE_SMELL }
        if (codeSmells.size > 20) {
            recommendations.add("Высокий уровень code smells. Рассмотрите рефакторинг")
        }

        structure?.let {
            if (it.modules.size > 10) {
                recommendations.add("Проект содержит много модулей. Убедитесь в четкости границ ответственности")
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Код в хорошем состоянии. Продолжайте следовать best practices")
        }

        return recommendations
    }
}
