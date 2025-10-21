package ru.marslab.ide.ride.agent.tools

import kotlinx.datetime.Clock
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*

/**
 * Агент для генерации отчетов в различных форматах
 * 
 * Использует LLM для создания структурированных отчетов на основе результатов анализа.
 * 
 * Capabilities:
 * - markdown_generation - генерация Markdown
 * - html_generation - генерация HTML
 * - json_export - экспорт в JSON
 * - llm_report - генерация отчета через LLM
 */
class ReportGeneratorToolAgent(
    private val llmProvider: LLMProvider
) : BaseToolAgent(
    agentType = AgentType.REPORT_GENERATOR,
    toolCapabilities = setOf(
        "markdown_generation",
        "html_generation",
        "json_export",
        "llm_report"
    )
) {
    
    override fun getDescription(): String {
        return "Генерирует отчеты о результатах анализа в различных форматах"
    }

    private fun splitObjects(raw: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        for ((i, ch) in raw.withIndex()) {
            if (ch == '{') depth++
            if (ch == '}') depth--
            if (depth == 0 && i >= start) {
                val seg = raw.substring(start, i + 1).trim()
                if (seg.startsWith("{") && seg.endsWith("}")) parts.add(seg)
                start = i + 1
                while (start < raw.length && (raw[start] == ',' || raw[start].isWhitespace())) start++
            }
        }
        return parts
    }

    private fun mapToFinding(obj: String): Finding? {
        fun grp(rx: String) = Regex(rx, RegexOption.DOT_MATCHES_ALL).find(obj)?.groupValues?.getOrNull(1)
        val file = grp(""""file"\s*:\s*"(.*?)"""")
        val lineStr = grp(""""line"\s*:\s*(\d+)"""")
        val severityStr = grp(""""severity"\s*:\s*"(.*?)"""")
        val message = grp(""""message"\s*:\s*"(.*?)"""") ?: return null
        val description = grp(""""description"\s*:\s*"(.*?)"""") ?: ""
        val category = grp(""""category"\s*:\s*"(.*?)"""") ?: "general"
        val suggestion = grp(""""suggestion"\s*:\s*"(.*?)"""")
        val severity = try { Severity.valueOf((severityStr ?: "MEDIUM").uppercase()) } catch (_: Exception) { Severity.MEDIUM }
        val line = lineStr?.toIntOrNull() ?: 0
        return Finding(
            file = file ?: "",
            line = line,
            severity = severity,
            category = category,
            message = message,
            description = description,
            suggestion = suggestion
        )
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
        val useLLM = step.input.getBoolean("use_llm") ?: true
        val title = step.input.getString("title") ?: "Отчет анализа кода"
        val explicitFindings = step.input.getList<Finding>("findings") ?: emptyList()
        val metrics = step.input.get<Map<String, Any>>("metrics") ?: emptyMap()

        // Собираем результаты из previousResults
        val previousResults = step.input.get<Map<String, Any>>("previousResults") ?: emptyMap()
        
        logger.info("Generating report in $format format (use_llm=$useLLM, previous_results=${previousResults.size})")
        
        val report = if (useLLM && format.lowercase() == "markdown" && previousResults.isNotEmpty()) {
            // Генерируем отчет через LLM на основе результатов всех шагов
            generateLLMReport(previousResults)
        } else {
            // Используем старую логику для обратной совместимости
            val aggregatedFromDeps = mutableListOf<Finding>()
            if (previousResults.isNotEmpty()) {
                previousResults.values.forEach { v ->
                    val text = v.toString()
                    val m = Regex("findings=\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(text)
                    val arr = m?.groupValues?.getOrNull(1)
                    if (!arr.isNullOrBlank()) {
                        val objs = splitObjects(arr)
                        objs.mapNotNull { obj -> mapToFinding(obj) }.forEach { aggregatedFromDeps.add(it) }
                    }
                }
            }

            val findings = (explicitFindings + aggregatedFromDeps)
            
            when (format.lowercase()) {
                "markdown" -> generateMarkdownReport(title, findings, metrics)
                "html" -> generateHtmlReport(title, findings, metrics)
                "json" -> generateJsonReport(title, findings, metrics)
                else -> return StepResult.error("Unsupported format: $format")
            }
        }
        
        logger.info("Report generated successfully (${report.length} characters)")
        
        return StepResult.success(
            output = StepOutput.of(
                "report" to report,
                "format" to format,
                "size" to report.length
            ),
            metadata = mapOf(
                "generated_at" to Clock.System.now().toString(),
                "used_llm" to useLLM
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
    
    /**
     * Генерирует отчет через LLM на основе результатов всех шагов
     */
    private suspend fun generateLLMReport(previousResults: Map<String, Any>): String {
        logger.info("Generating LLM report from ${previousResults.size} step results")
        
        val reportPrompt = buildLLMReportPrompt(previousResults)
        
        return try {
            val llmResponse = llmProvider.sendRequest(
                systemPrompt = LLM_REPORT_SYSTEM_PROMPT,
                userMessage = reportPrompt,
                conversationHistory = emptyList(),
                parameters = ru.marslab.ide.ride.model.llm.LLMParameters.BALANCED
            )
            
            if (llmResponse.success) {
                llmResponse.content
            } else {
                logger.error("Failed to generate LLM report: ${llmResponse.error}")
                buildFallbackReport(previousResults)
            }
        } catch (e: Exception) {
            logger.error("Error generating LLM report", e)
            buildFallbackReport(previousResults)
        }
    }
    
    /**
     * Строит промпт для LLM на основе результатов шагов
     */
    private fun buildLLMReportPrompt(previousResults: Map<String, Any>): String {
        return buildString {
            appendLine("# Результаты анализа кода")
            appendLine()
            
            previousResults.forEach { (stepId, result) ->
                appendLine("## Шаг: $stepId")
                appendLine()
                
                when (result) {
                    is Map<*, *> -> {
                        val output = result["output"] as? Map<*, *>
                        if (output != null) {
                            // Findings
                            val findings = output["findings"] as? List<*>
                            if (findings != null && findings.isNotEmpty()) {
                                appendLine("**Найдено проблем:** ${findings.size}")
                                
                                val criticalCount = output["critical_count"] as? Int ?: 0
                                val highCount = output["high_count"] as? Int ?: 0
                                val mediumCount = output["medium_count"] as? Int ?: 0
                                val lowCount = output["low_count"] as? Int ?: 0
                                
                                if (criticalCount + highCount + mediumCount + lowCount > 0) {
                                    appendLine("- Критичных: $criticalCount")
                                    appendLine("- Высокий приоритет: $highCount")
                                    appendLine("- Средний приоритет: $mediumCount")
                                    appendLine("- Низкий приоритет: $lowCount")
                                }
                                appendLine()
                                
                                appendLine("**Детали:**")
                                findings.take(10).forEach { finding ->
                                    if (finding is Map<*, *>) {
                                        val message = finding["message"] ?: finding["description"]
                                        val severity = finding["severity"]
                                        val file = finding["file"]
                                        val line = finding["line"]
                                        val suggestion = finding["suggestion"]
                                        
                                        appendLine("- **$message** (Уровень: $severity)")
                                        if (file != null) {
                                            appendLine("  - Файл: `$file${if (line != null) ":$line" else ""}`")
                                        }
                                        if (suggestion != null && suggestion != "") {
                                            appendLine("  - Рекомендация: $suggestion")
                                        }
                                    }
                                }
                                if (findings.size > 10) {
                                    appendLine("- ... и еще ${findings.size - 10} проблем(ы)")
                                }
                            }
                            
                            // Files
                            val totalFiles = output["total_files"] as? Int
                            val totalDirs = output["total_directories"] as? Int
                            if (totalFiles != null) {
                                appendLine("**Проанализировано файлов:** $totalFiles")
                                if (totalDirs != null) {
                                    appendLine("**Директорий:** $totalDirs")
                                }
                            }
                            
                            // Architecture
                            val modules = output["modules"] as? List<*>
                            if (modules != null) {
                                appendLine("**Обнаружено модулей:** ${modules.size}")
                            }
                            
                            val layers = output["layers"] as? List<*>
                            if (layers != null) {
                                appendLine("**Обнаружено слоев:** ${layers.size}")
                            }
                        }
                    }
                    is String -> {
                        appendLine(result)
                    }
                }
                
                appendLine()
            }
            
            appendLine()
            appendLine("# Инструкция")
            appendLine("Создай подробный, структурированный отчет на русском языке в формате Markdown.")
            appendLine()
            appendLine("Структура отчета:")
            appendLine("1. **Заголовок** - Отчет о выполнении задачи")
            appendLine("2. **Краткое резюме** - 2-3 предложения о выполненной работе")
            appendLine("3. **Детальные результаты** - для каждого этапа анализа")
            appendLine("4. **Ключевые находки** - самые важные проблемы")
            appendLine("5. **Рекомендации** - конкретные шаги по улучшению")
            appendLine("6. **Заключение** - итоговая оценка")
            appendLine()
            appendLine("Требования:")
            appendLine("- Используй заголовки (##, ###)")
            appendLine("- Используй списки и **жирный** текст")
            appendLine("- Используй `код` для файлов")
            appendLine("- Добавляй пустые строки для читаемости")
            appendLine("- НЕ включай информацию о планировании")
        }
    }
    
    /**
     * Создает упрощенный отчет при ошибке LLM
     */
    private fun buildFallbackReport(previousResults: Map<String, Any>): String {
        return buildString {
            appendLine("# Отчет о выполнении задачи")
            appendLine()
            appendLine("**Дата генерации:** ${Clock.System.now()}")
            appendLine()
            
            appendLine("## Краткое резюме")
            appendLine()
            appendLine("Выполнен анализ кода. Завершено ${previousResults.size} этапов.")
            appendLine()
            
            appendLine("## Детальные результаты")
            appendLine()
            
            previousResults.forEach { (stepId, result) ->
                appendLine("### $stepId")
                appendLine()
                
                when (result) {
                    is Map<*, *> -> {
                        val output = result["output"] as? Map<*, *>
                        if (output != null) {
                            val findings = output["findings"] as? List<*>
                            if (findings != null && findings.isNotEmpty()) {
                                appendLine("**Найдено проблем:** ${findings.size}")
                                appendLine()
                                
                                findings.take(5).forEach { finding ->
                                    if (finding is Map<*, *>) {
                                        val message = finding["message"] ?: finding["description"]
                                        val severity = finding["severity"]
                                        appendLine("- **$message** (Уровень: $severity)")
                                    }
                                }
                                if (findings.size > 5) {
                                    appendLine("- ... и еще ${findings.size - 5} проблем(ы)")
                                }
                            } else {
                                appendLine("Проблем не обнаружено.")
                            }
                        }
                    }
                    is String -> {
                        appendLine(result)
                    }
                }
                
                appendLine()
            }
            
            appendLine("## Заключение")
            appendLine()
            appendLine("Анализ кода завершен. Детальные результаты представлены выше.")
        }
    }
    
    companion object {
        private const val LLM_REPORT_SYSTEM_PROMPT = """
Ты - эксперт по анализу кода и генерации технических отчетов.
Твоя задача - создавать подробные, структурированные отчеты на основе результатов работы различных агентов анализа кода.

Требования к отчету:
- Используй профессиональный технический язык
- Структурируй информацию логично и последовательно
- Выделяй ключевые находки и проблемы
- Предоставляй конкретные рекомендации
- Используй markdown для форматирования
- Пиши на русском языке

Стиль:
- Четкий и лаконичный
- Фокус на важной информации
- Избегай избыточных деталей
- Используй списки и таблицы для структурирования данных
"""
    }
}
