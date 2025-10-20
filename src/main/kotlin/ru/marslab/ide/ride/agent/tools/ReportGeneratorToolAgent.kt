package ru.marslab.ide.ride.agent.tools

import kotlinx.datetime.Clock
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*

/**
 * Агент для генерации отчетов в различных форматах
 * 
 * Capabilities:
 * - markdown_generation - генерация Markdown
 * - html_generation - генерация HTML
 * - json_export - экспорт в JSON
 */
class ReportGeneratorToolAgent : BaseToolAgent(
    agentType = AgentType.REPORT_GENERATOR,
    toolCapabilities = setOf(
        "markdown_generation",
        "html_generation",
        "json_export"
    )
) {
    
    override fun getDescription(): String {
        return "Генерирует отчеты о результатах анализа в различных форматах"
    }
    
    override fun validateInput(input: StepInput): ValidationResult {
        val format = input.getString("format")
        
        if (format.isNullOrEmpty()) {
            return ValidationResult.failure("format is required")
        }
        
        if (format !in listOf("markdown", "html", "json")) {
            return ValidationResult.failure("format must be one of: markdown, html, json")
        }
        
        return ValidationResult.success()
    }
    
    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val format = step.input.getString("format") ?: "markdown"
        val title = step.input.getString("title") ?: "Отчет анализа кода"
        val findings = step.input.getList<Finding>("findings") ?: emptyList()
        val metrics = step.input.get<Map<String, Any>>("metrics") ?: emptyMap()
        
        logger.info("Generating report in $format format with ${findings.size} findings")
        
        val report = when (format.lowercase()) {
            "markdown" -> generateMarkdownReport(title, findings, metrics)
            "html" -> generateHtmlReport(title, findings, metrics)
            "json" -> generateJsonReport(title, findings, metrics)
            else -> return StepResult.error("Unsupported format: $format")
        }
        
        logger.info("Report generated successfully (${report.length} characters)")
        
        return StepResult.success(
            output = StepOutput.of(
                "report" to report,
                "format" to format,
                "size" to report.length
            ),
            metadata = mapOf(
                "findings_count" to findings.size,
                "generated_at" to Clock.System.now().toString()
            )
        )
    }
    
    private fun generateMarkdownReport(title: String, findings: List<Finding>, metrics: Map<String, Any>): String {
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("**Дата генерации:** ${Clock.System.now()}")
            appendLine()
            
            // Метрики
            if (metrics.isNotEmpty()) {
                appendLine("## 📊 Метрики")
                appendLine()
                metrics.forEach { (key, value) ->
                    appendLine("- **${formatKey(key)}:** $value")
                }
                appendLine()
            }
            
            // Сводка по severity
            if (findings.isNotEmpty()) {
                appendLine("## 📋 Сводка")
                appendLine()
                val bySeverity = findings.groupBy { it.severity }
                Severity.values().forEach { severity ->
                    val count = bySeverity[severity]?.size ?: 0
                    if (count > 0) {
                        val emoji = when (severity) {
                            Severity.CRITICAL -> "🔴"
                            Severity.HIGH -> "🟠"
                            Severity.MEDIUM -> "🟡"
                            Severity.LOW -> "🔵"
                            Severity.INFO -> "⚪"
                        }
                        appendLine("- $emoji **${severity.name}:** $count")
                    }
                }
                appendLine()
                
                // Детали находок
                appendLine("## 🔍 Детали")
                appendLine()
                
                bySeverity.entries.sortedBy { it.key }.forEach { (severity, severityFindings) ->
                    if (severityFindings.isNotEmpty()) {
                        appendLine("### ${severity.name} (${severityFindings.size})")
                        appendLine()
                        
                        severityFindings.forEach { finding ->
                            appendLine("#### ${finding.message}")
                            appendLine()
                            appendLine("- **Файл:** `${finding.file}`")
                            appendLine("- **Строка:** ${finding.line}")
                            appendLine("- **Категория:** ${finding.category}")
                            if (finding.description.isNotEmpty()) {
                                appendLine("- **Описание:** ${finding.description}")
                            }
                            if (finding.suggestion != null) {
                                appendLine("- **Рекомендация:** ${finding.suggestion}")
                            }
                            appendLine()
                        }
                    }
                }
            } else {
                appendLine("## ✅ Результат")
                appendLine()
                appendLine("Проблем не обнаружено!")
                appendLine()
            }
        }
    }
    
    private fun generateHtmlReport(title: String, findings: List<Finding>, metrics: Map<String, Any>): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("  <meta charset=\"UTF-8\">")
            appendLine("  <title>$title</title>")
            appendLine("  <style>")
            appendLine("    body { font-family: Arial, sans-serif; margin: 20px; }")
            appendLine("    h1 { color: #333; }")
            appendLine("    .metric { margin: 10px 0; }")
            appendLine("    .finding { border: 1px solid #ddd; padding: 10px; margin: 10px 0; border-radius: 5px; }")
            appendLine("    .critical { border-left: 5px solid #d32f2f; }")
            appendLine("    .high { border-left: 5px solid #f57c00; }")
            appendLine("    .medium { border-left: 5px solid #fbc02d; }")
            appendLine("    .low { border-left: 5px solid #1976d2; }")
            appendLine("    .info { border-left: 5px solid #757575; }")
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <h1>$title</h1>")
            appendLine("  <p><strong>Дата генерации:</strong> ${Clock.System.now()}</p>")
            
            if (metrics.isNotEmpty()) {
                appendLine("  <h2>Метрики</h2>")
                metrics.forEach { (key, value) ->
                    appendLine("  <div class=\"metric\"><strong>${formatKey(key)}:</strong> $value</div>")
                }
            }
            
            if (findings.isNotEmpty()) {
                appendLine("  <h2>Находки (${findings.size})</h2>")
                findings.forEach { finding ->
                    val cssClass = finding.severity.name.lowercase()
                    appendLine("  <div class=\"finding $cssClass\">")
                    appendLine("    <h3>${finding.message}</h3>")
                    appendLine("    <p><strong>Файл:</strong> ${finding.file}</p>")
                    appendLine("    <p><strong>Строка:</strong> ${finding.line}</p>")
                    appendLine("    <p><strong>Severity:</strong> ${finding.severity}</p>")
                    if (finding.description.isNotEmpty()) {
                        appendLine("    <p>${finding.description}</p>")
                    }
                    if (finding.suggestion != null) {
                        appendLine("    <p><strong>Рекомендация:</strong> ${finding.suggestion}</p>")
                    }
                    appendLine("  </div>")
                }
            } else {
                appendLine("  <p>✅ Проблем не обнаружено!</p>")
            }
            
            appendLine("</body>")
            appendLine("</html>")
        }
    }
    
    private fun generateJsonReport(title: String, findings: List<Finding>, metrics: Map<String, Any>): String {
        // Простая JSON сериализация
        return buildString {
            appendLine("{")
            appendLine("  \"title\": \"$title\",")
            appendLine("  \"generated_at\": \"${Clock.System.now()}\",")
            appendLine("  \"metrics\": {")
            metrics.entries.forEachIndexed { index, (key, value) ->
                val comma = if (index < metrics.size - 1) "," else ""
                val valueStr = if (value is String) "\"$value\"" else value.toString()
                appendLine("    \"$key\": $valueStr$comma")
            }
            appendLine("  },")
            appendLine("  \"findings\": [")
            findings.forEachIndexed { index, finding ->
                val comma = if (index < findings.size - 1) "," else ""
                appendLine("    {")
                appendLine("      \"file\": \"${finding.file}\",")
                appendLine("      \"line\": ${finding.line},")
                appendLine("      \"severity\": \"${finding.severity}\",")
                appendLine("      \"category\": \"${finding.category}\",")
                appendLine("      \"message\": \"${finding.message}\",")
                appendLine("      \"description\": \"${finding.description}\",")
                appendLine("      \"suggestion\": ${if (finding.suggestion != null) "\"${finding.suggestion}\"" else "null"}")
                appendLine("    }$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }
    
    private fun formatKey(key: String): String {
        return key.split("_").joinToString(" ") { it.capitalize() }
    }
}
