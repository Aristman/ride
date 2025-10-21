package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File

/**
 * Агент для поиска багов и потенциальных проблем в коде
 * 
 * Capabilities:
 * - bug_detection - поиск багов
 * - null_pointer_analysis - анализ NPE
 * - resource_leak_detection - поиск утечек ресурсов
 */
class BugDetectionToolAgent : BaseToolAgent(
    agentType = AgentType.BUG_DETECTION,
    toolCapabilities = setOf(
        "bug_detection",
        "null_pointer_analysis",
        "resource_leak_detection"
    )
) {
    
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
        val severityThreshold = step.input.getString("severity_threshold")
            ?.let { Severity.valueOf(it) } ?: Severity.INFO

        // Входные логи
        logger.info("BUG_DETECTION input: files=${files.size}, severity_threshold=${severityThreshold}")
        files.take(10).forEach { logger.info("BUG_DETECTION file: $it") }
        
        val findings = mutableListOf<Finding>()
        
        for (filePath in files) {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                logger.warn("File does not exist or is not a file: $filePath")
                continue
            }
            // Превью файла (усеченно)
            runCatching {
                val preview = file.readText().lineSequence().filter { it.isNotBlank() }.joinToString("\n").take(200)
                logger.info("BUG_DETECTION preview($filePath): ${'$'}preview ...")
            }

            logger.info("BUG_DETECTION start file: $filePath")
            // Анализируем файл
            val fileFindings = analyzeFile(file, severityThreshold)
            findings.addAll(fileFindings)
            logger.info("BUG_DETECTION end file: $filePath, findings=${fileFindings.size}")
        }
        
        // Группируем по severity
        val criticalCount = findings.count { it.severity == Severity.CRITICAL }
        val highCount = findings.count { it.severity == Severity.HIGH }
        val mediumCount = findings.count { it.severity == Severity.MEDIUM }
        val lowCount = findings.count { it.severity == Severity.LOW }
        
        logger.info("Bug detection completed: found ${findings.size} issues " +
                "(critical: $criticalCount, high: $highCount, medium: $mediumCount, low: $lowCount)")
        
        return StepResult.success(
            output = StepOutput.of(
                "findings" to findings,
                "total_count" to findings.size,
                "critical_count" to criticalCount,
                "high_count" to highCount,
                "medium_count" to mediumCount,
                "low_count" to lowCount
            ),
            metadata = mapOf(
                "files_analyzed" to files.size,
                "severity_threshold" to severityThreshold.name
            )
        )
    }
    
    private fun analyzeFile(file: File, severityThreshold: Severity): List<Finding> {
        val findings = mutableListOf<Finding>()
        
        try {
            val lines = file.readLines()
            
            lines.forEachIndexed { index, line ->
                val lineNumber = index + 1
                
                // Простые эвристики для поиска проблем
                
                // Null pointer risks
                if (line.contains("!!") && !line.trim().startsWith("//")) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.HIGH,
                            category = "null_pointer_risk",
                            message = "Использование !! оператора может привести к NPE",
                            description = "Оператор !! принудительно разыменовывает nullable значение",
                            suggestion = "Используйте безопасный вызов ?. или elvis оператор ?:"
                        )
                    )
                }
                
                // Resource leaks
                if ((line.contains("FileInputStream") || line.contains("FileOutputStream")) 
                    && !line.contains("use")) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.MEDIUM,
                            category = "resource_leak",
                            message = "Потенциальная утечка ресурса",
                            description = "Файловый поток может не быть закрыт",
                            suggestion = "Используйте .use { } для автоматического закрытия ресурса"
                        )
                    )
                }
                
                // Empty catch blocks
                if (line.trim() == "catch" && lines.getOrNull(index + 1)?.trim() == "{}") {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.MEDIUM,
                            category = "empty_catch",
                            message = "Пустой catch блок",
                            description = "Исключения игнорируются без логирования",
                            suggestion = "Добавьте логирование или обработку ошибки"
                        )
                    )
                }
                
                // TODO/FIXME comments
                if (line.contains("TODO", ignoreCase = true) || line.contains("FIXME", ignoreCase = true)) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.LOW,
                            category = "todo_comment",
                            message = "Незавершенный код",
                            description = "Найден TODO/FIXME комментарий"
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error analyzing file ${file.absolutePath}", e)
        }
        
        // Фильтруем по severity threshold
        return findings.filter { it.severity.ordinal <= severityThreshold.ordinal }
    }
}
