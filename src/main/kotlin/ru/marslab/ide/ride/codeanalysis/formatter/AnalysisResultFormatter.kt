package ru.marslab.ide.ride.codeanalysis.formatter

import ru.marslab.ide.ride.model.codeanalysis.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.format.DateTimeFormatter

/**
 * Форматирует результаты анализа в различные форматы
 */
class AnalysisResultFormatter {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Форматирует результат анализа в указанном формате
     */
    fun format(result: CodeAnalysisResult, format: ReportFormat): String {
        return when (format) {
            ReportFormat.MARKDOWN -> formatMarkdown(result)
            ReportFormat.HTML -> formatHtml(result)
            ReportFormat.JSON -> formatJson(result)
            ReportFormat.TEXT -> formatText(result)
        }
    }
    
    /**
     * Форматирует в Markdown
     */
    private fun formatMarkdown(result: CodeAnalysisResult): String {
        val sb = StringBuilder()
        
        sb.appendLine("# 🔍 Отчет об анализе кода: ${result.projectName}")
        sb.appendLine()
        sb.appendLine("**Дата анализа:** ${result.analysisDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        sb.appendLine()
        
        // Метрики
        sb.appendLine("## 📊 Метрики проекта")
        sb.appendLine()
        sb.appendLine("- **Файлов:** ${result.metrics.totalFiles}")
        sb.appendLine("- **Строк кода:** ${result.metrics.totalLines}")
        sb.appendLine("- **Классов:** ${result.metrics.totalClasses}")
        sb.appendLine("- **Функций:** ${result.metrics.totalFunctions}")
        sb.appendLine("- **Средняя сложность:** ${String.format("%.2f", result.metrics.averageComplexity)}")
        result.metrics.testCoverage?.let {
            sb.appendLine("- **Покрытие тестами:** ${String.format("%.1f", it)}%")
        }
        sb.appendLine()
        
        // Резюме
        sb.appendLine("## 📝 Резюме")
        sb.appendLine()
        sb.appendLine(result.summary)
        sb.appendLine()
        
        // Найденные проблемы
        sb.appendLine("## 🐛 Найденные проблемы")
        sb.appendLine()
        
        val groupedFindings = result.findings.groupBy { it.severity }
        
        for (severity in Severity.values()) {
            val findings = groupedFindings[severity] ?: continue
            if (findings.isEmpty()) continue
            
            val icon = when (severity) {
                Severity.CRITICAL -> "🔴"
                Severity.HIGH -> "🟠"
                Severity.MEDIUM -> "🟡"
                Severity.LOW -> "🟢"
                Severity.INFO -> "ℹ️"
            }
            
            sb.appendLine("### $icon ${severity.name} (${findings.size})")
            sb.appendLine()
            
            for (finding in findings) {
                sb.appendLine("#### ${finding.title}")
                sb.appendLine()
                sb.appendLine("- **Файл:** `${finding.file}`")
                finding.line?.let { sb.appendLine("- **Строка:** $it") }
                sb.appendLine("- **Тип:** ${finding.type.name}")
                sb.appendLine()
                sb.appendLine(finding.description)
                sb.appendLine()
                
                finding.suggestion?.let {
                    sb.appendLine("**💡 Рекомендация:**")
                    sb.appendLine(it)
                    sb.appendLine()
                }
                
                finding.codeSnippet?.let {
                    sb.appendLine("```")
                    sb.appendLine(it)
                    sb.appendLine("```")
                    sb.appendLine()
                }
            }
        }
        
        // Структура проекта
        result.projectStructure?.let { structure ->
            sb.appendLine("## 🏗️ Структура проекта")
            sb.appendLine()
            sb.appendLine("**Корневой пакет:** `${structure.rootPackage}`")
            sb.appendLine()
            
            sb.appendLine("### Модули")
            sb.appendLine()
            for (module in structure.modules) {
                sb.appendLine("- **${module.name}** (${module.type.name})")
                sb.appendLine("  - Путь: `${module.path}`")
                sb.appendLine("  - Файлов: ${module.files}")
                sb.appendLine("  - Строк кода: ${module.linesOfCode}")
            }
            sb.appendLine()
            
            sb.appendLine("### Архитектурные слои")
            sb.appendLine()
            for (layer in structure.layers) {
                sb.appendLine("- **${layer.name}**")
                sb.appendLine("  - Модули: ${layer.modules.joinToString(", ")}")
            }
            sb.appendLine()
        }
        
        // Рекомендации
        if (result.recommendations.isNotEmpty()) {
            sb.appendLine("## 💡 Рекомендации")
            sb.appendLine()
            for ((index, recommendation) in result.recommendations.withIndex()) {
                sb.appendLine("${index + 1}. $recommendation")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    /**
     * Форматирует в простой текст
     */
    private fun formatText(result: CodeAnalysisResult): String {
        val sb = StringBuilder()
        
        sb.appendLine("=".repeat(60))
        sb.appendLine("ОТЧЕТ ОБ АНАЛИЗЕ КОДА: ${result.projectName}")
        sb.appendLine("=".repeat(60))
        sb.appendLine()
        sb.appendLine("Дата: ${result.analysisDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        sb.appendLine()
        
        sb.appendLine("МЕТРИКИ:")
        sb.appendLine("  Файлов: ${result.metrics.totalFiles}")
        sb.appendLine("  Строк кода: ${result.metrics.totalLines}")
        sb.appendLine("  Классов: ${result.metrics.totalClasses}")
        sb.appendLine("  Функций: ${result.metrics.totalFunctions}")
        sb.appendLine()
        
        sb.appendLine("РЕЗЮМЕ:")
        sb.appendLine(result.summary)
        sb.appendLine()
        
        sb.appendLine("НАЙДЕННЫЕ ПРОБЛЕМЫ: ${result.findings.size}")
        for (finding in result.findings) {
            sb.appendLine("-".repeat(60))
            sb.appendLine("[${finding.severity.name}] ${finding.title}")
            sb.appendLine("Файл: ${finding.file}${finding.line?.let { ":$it" } ?: ""}")
            sb.appendLine(finding.description)
            finding.suggestion?.let {
                sb.appendLine("Рекомендация: $it")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
    
    /**
     * Форматирует в JSON
     */
    private fun formatJson(result: CodeAnalysisResult): String {
        // Простой JSON без kotlinx.serialization для избежания проблем с типами
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{\n")
        jsonBuilder.append("  \"projectName\": \"${result.projectName}\",\n")
        jsonBuilder.append("  \"analysisDate\": \"${result.analysisDate}\",\n")
        jsonBuilder.append("  \"metrics\": {\n")
        jsonBuilder.append("    \"totalFiles\": ${result.metrics.totalFiles},\n")
        jsonBuilder.append("    \"totalLines\": ${result.metrics.totalLines},\n")
        jsonBuilder.append("    \"totalClasses\": ${result.metrics.totalClasses},\n")
        jsonBuilder.append("    \"totalFunctions\": ${result.metrics.totalFunctions},\n")
        jsonBuilder.append("    \"averageComplexity\": ${result.metrics.averageComplexity},\n")
        jsonBuilder.append("    \"testCoverage\": ${result.metrics.testCoverage}\n")
        jsonBuilder.append("  },\n")
        jsonBuilder.append("  \"summary\": \"${result.summary.replace("\"", "\\\"")}\",\n")
        jsonBuilder.append("  \"findingsCount\": ${result.findings.size},\n")
        jsonBuilder.append("  \"findings\": [\n")
        
        result.findings.forEachIndexed { index, finding ->
            jsonBuilder.append("    {\n")
            jsonBuilder.append("      \"id\": \"${finding.id}\",\n")
            jsonBuilder.append("      \"type\": \"${finding.type.name}\",\n")
            jsonBuilder.append("      \"severity\": \"${finding.severity.name}\",\n")
            jsonBuilder.append("      \"file\": \"${finding.file}\",\n")
            jsonBuilder.append("      \"line\": ${finding.line},\n")
            jsonBuilder.append("      \"title\": \"${finding.title.replace("\"", "\\\"")}\",\n")
            jsonBuilder.append("      \"description\": \"${finding.description.replace("\"", "\\\"")}\",\n")
            jsonBuilder.append("      \"suggestion\": ${finding.suggestion?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"}\n")
            jsonBuilder.append("    }${if (index < result.findings.size - 1) "," else ""}\n")
        }
        
        jsonBuilder.append("  ],\n")
        jsonBuilder.append("  \"recommendations\": [\n")
        
        result.recommendations.forEachIndexed { index, rec ->
            jsonBuilder.append("    \"${rec.replace("\"", "\\\"")}\"${if (index < result.recommendations.size - 1) "," else ""}\n")
        }
        
        jsonBuilder.append("  ]\n")
        jsonBuilder.append("}")
        
        return jsonBuilder.toString()
    }
    
    /**
     * Форматирует в HTML
     */
    private fun formatHtml(result: CodeAnalysisResult): String {
        val sb = StringBuilder()
        
        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html>")
        sb.appendLine("<head>")
        sb.appendLine("  <meta charset='UTF-8'>")
        sb.appendLine("  <title>Анализ кода: ${result.projectName}</title>")
        sb.appendLine("  <style>")
        sb.appendLine("    body { font-family: Arial, sans-serif; margin: 20px; }")
        sb.appendLine("    h1 { color: #333; }")
        sb.appendLine("    .critical { color: #d32f2f; }")
        sb.appendLine("    .high { color: #f57c00; }")
        sb.appendLine("    .medium { color: #fbc02d; }")
        sb.appendLine("    .low { color: #388e3c; }")
        sb.appendLine("    .info { color: #1976d2; }")
        sb.appendLine("    .finding { border: 1px solid #ddd; padding: 10px; margin: 10px 0; }")
        sb.appendLine("    pre { background: #f5f5f5; padding: 10px; overflow-x: auto; }")
        sb.appendLine("  </style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")
        sb.appendLine("  <h1>🔍 Отчет об анализе кода: ${result.projectName}</h1>")
        sb.appendLine("  <p><strong>Дата:</strong> ${result.analysisDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}</p>")
        
        sb.appendLine("  <h2>📊 Метрики</h2>")
        sb.appendLine("  <ul>")
        sb.appendLine("    <li>Файлов: ${result.metrics.totalFiles}</li>")
        sb.appendLine("    <li>Строк кода: ${result.metrics.totalLines}</li>")
        sb.appendLine("    <li>Классов: ${result.metrics.totalClasses}</li>")
        sb.appendLine("    <li>Функций: ${result.metrics.totalFunctions}</li>")
        sb.appendLine("  </ul>")
        
        sb.appendLine("  <h2>📝 Резюме</h2>")
        sb.appendLine("  <p>${result.summary}</p>")
        
        sb.appendLine("  <h2>🐛 Найденные проблемы (${result.findings.size})</h2>")
        for (finding in result.findings) {
            val cssClass = finding.severity.name.lowercase()
            sb.appendLine("  <div class='finding'>")
            sb.appendLine("    <h3 class='$cssClass'>[${finding.severity.name}] ${finding.title}</h3>")
            sb.appendLine("    <p><strong>Файл:</strong> ${finding.file}${finding.line?.let { ":$it" } ?: ""}</p>")
            sb.appendLine("    <p>${finding.description}</p>")
            finding.suggestion?.let {
                sb.appendLine("    <p><strong>💡 Рекомендация:</strong> $it</p>")
            }
            finding.codeSnippet?.let {
                sb.appendLine("    <pre><code>$it</code></pre>")
            }
            sb.appendLine("  </div>")
        }
        
        if (result.recommendations.isNotEmpty()) {
            sb.appendLine("  <h2>💡 Рекомендации</h2>")
            sb.appendLine("  <ol>")
            for (recommendation in result.recommendations) {
                sb.appendLine("    <li>$recommendation</li>")
            }
            sb.appendLine("  </ol>")
        }
        
        sb.appendLine("</body>")
        sb.appendLine("</html>")
        
        return sb.toString()
    }
}
