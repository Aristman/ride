package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.codeanalysis.analyzer.BugDetectionAnalyzer
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File

/**
 * Агент для поиска багов и потенциальных проблем в коде
 * 
 * Использует LLM для глубокого анализа кода на различных языках программирования.
 * 
 * Capabilities:
 * - bug_detection - поиск багов
 * - null_pointer_analysis - анализ NPE
 * - resource_leak_detection - поиск утечек ресурсов
 */
class BugDetectionToolAgent(
    private val llmProvider: LLMProvider
) : BaseToolAgent(
    agentType = AgentType.BUG_DETECTION,
    toolCapabilities = setOf(
        "bug_detection",
        "null_pointer_analysis",
        "resource_leak_detection"
    )
) {
    
    private val analyzer by lazy { BugDetectionAnalyzer(llmProvider) }
    
    override fun getDescription(): String {
        return "Анализирует код на наличие багов и потенциальных проблем"
    }
    
    override fun validateInput(input: StepInput): ValidationResult {
        val files = input.getList<String>("files")
        
        if (files.isNullOrEmpty()) {
            return ValidationResult.failure("files is required and must not be empty")
        }
        
        return ValidationResult.success()
    }
    
    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val files = step.input.getList<String>("files") ?: emptyList()
        val maxFilesToAnalyze = step.input.getInt("max_files") ?: 20

        logger.info("BUG_DETECTION input: files=${files.size}, max_files=$maxFilesToAnalyze")
        files.take(10).forEach { logger.info("BUG_DETECTION file: $it") }
        
        val allFindings = mutableListOf<Map<String, Any>>()
        val filesToAnalyze = files.take(maxFilesToAnalyze)
        
        for (filePath in filesToAnalyze) {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                logger.warn("File does not exist or is not a file: $filePath")
                continue
            }
            
            // Проверяем размер файла (не анализируем слишком большие файлы)
            if (file.length() > 100_000) {
                logger.warn("File too large, skipping: $filePath (${file.length()} bytes)")
                continue
            }
            
            try {
                logger.info("BUG_DETECTION analyzing file: $filePath")
                val code = file.readText()
                
                // Используем LLM-анализатор
                val findings = analyzer.analyze(code, filePath)
                
                // Конвертируем в Map для вывода
                val findingMaps = findings.map { finding ->
                    mapOf(
                        "file" to finding.file,
                        "line" to finding.line,
                        "severity" to finding.severity.name,
                        "message" to finding.title,
                        "description" to finding.description,
                        "suggestion" to finding.suggestion,
                        "type" to finding.type.name
                    )
                }
                
                allFindings.addAll(findingMaps)
                logger.info("BUG_DETECTION found ${findings.size} issues in $filePath")
            } catch (e: Exception) {
                logger.error("Error analyzing file $filePath", e)
            }
        }
        
        // Группируем по severity
        val criticalCount = allFindings.count { it["severity"] == "CRITICAL" }
        val highCount = allFindings.count { it["severity"] == "HIGH" }
        val mediumCount = allFindings.count { it["severity"] == "MEDIUM" }
        val lowCount = allFindings.count { it["severity"] == "LOW" }
        
        logger.info("Bug detection completed: found ${allFindings.size} issues " +
                "(critical: $criticalCount, high: $highCount, medium: $mediumCount, low: $lowCount)")
        
        return StepResult.success(
            output = StepOutput.of(
                "findings" to allFindings,
                "total_count" to allFindings.size,
                "critical_count" to criticalCount,
                "high_count" to highCount,
                "medium_count" to mediumCount,
                "low_count" to lowCount
            ),
            metadata = mapOf(
                "files_analyzed" to filesToAnalyze.size,
                "files_skipped" to (files.size - filesToAnalyze.size)
            )
        )
    }
}
