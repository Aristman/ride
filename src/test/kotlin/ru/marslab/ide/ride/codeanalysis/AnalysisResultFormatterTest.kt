package ru.marslab.ide.ride.codeanalysis

import org.junit.Test
import ru.marslab.ide.ride.codeanalysis.formatter.AnalysisResultFormatter
import ru.marslab.ide.ride.model.codeanalysis.*
import java.time.LocalDateTime
import kotlin.test.assertTrue

/**
 * Тесты для AnalysisResultFormatter
 */
class AnalysisResultFormatterTest {

    private val formatter = AnalysisResultFormatter()

    private fun createTestResult(): CodeAnalysisResult {
        return CodeAnalysisResult(
            projectName = "TestProject",
            analysisDate = LocalDateTime.of(2025, 10, 20, 12, 0),
            findings = listOf(
                Finding(
                    id = "1",
                    type = FindingType.BUG,
                    severity = Severity.CRITICAL,
                    file = "test.kt",
                    line = 10,
                    title = "Test Bug",
                    description = "Test description",
                    suggestion = "Test suggestion",
                    codeSnippet = null
                )
            ),
            projectStructure = null,
            metrics = CodeMetrics(
                totalFiles = 10,
                totalLines = 1000,
                totalClasses = 5,
                totalFunctions = 20,
                averageComplexity = 2.5,
                testCoverage = 80.0
            ),
            summary = "Test summary",
            recommendations = listOf("Recommendation 1", "Recommendation 2")
        )
    }

    @Test
    fun `should format as markdown`() {
        val result = createTestResult()
        val markdown = formatter.format(result, ReportFormat.MARKDOWN)

        assertTrue(markdown.contains("# 🔍 Отчет об анализе кода: TestProject"))
        assertTrue(markdown.contains("**Файлов:** 10"))
        assertTrue(markdown.contains("Test Bug"))
        assertTrue(markdown.contains("CRITICAL"))
    }

    @Test
    fun `should format as text`() {
        val result = createTestResult()
        val text = formatter.format(result, ReportFormat.TEXT)

        assertTrue(text.contains("ОТЧЕТ ОБ АНАЛИЗЕ КОДА: TestProject"))
        assertTrue(text.contains("Файлов: 10"))
        assertTrue(text.contains("Test Bug"))
    }

    @Test
    fun `should format as json`() {
        val result = createTestResult()
        val json = formatter.format(result, ReportFormat.JSON)

        assertTrue(json.contains("\"projectName\""))
        assertTrue(json.contains("\"TestProject\""))
        assertTrue(json.contains("\"findingsCount\""))
    }

    @Test
    fun `should format as html`() {
        val result = createTestResult()
        val html = formatter.format(result, ReportFormat.HTML)

        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<title>Анализ кода: TestProject</title>"))
        assertTrue(html.contains("Test Bug"))
        assertTrue(html.contains("class='critical'"))
    }
}
