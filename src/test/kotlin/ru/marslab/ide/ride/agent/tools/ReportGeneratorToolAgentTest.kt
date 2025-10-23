package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import ru.marslab.ide.ride.integration.llm.LLMProvider
import io.mockk.mockk

class ReportGeneratorToolAgentTest {

    private val mockLlmProvider = mockk<LLMProvider>(relaxed = true)
    private val agent = ReportGeneratorToolAgent(mockLlmProvider)
    
    @Test
    fun `should have correct agent type and capabilities`() {
        assertEquals(AgentType.REPORT_GENERATOR, agent.agentType)
        assertTrue(agent.toolCapabilities.contains("markdown_generation"))
        assertTrue(agent.toolCapabilities.contains("html_generation"))
        assertTrue(agent.toolCapabilities.contains("json_export"))
    }
    
    @Test
    fun `should validate format is required`() {
        val input = StepInput.empty()
        
        val result = agent.validateInput(input)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("format") })
    }
    
    @Test
    fun `should validate format is supported`() {
        val input = StepInput.of("format" to "xml")
        
        val result = agent.validateInput(input)
        
        assertFalse(result.isValid)
    }
    
    @Test
    fun `should generate markdown report`() = runTest {
        val findings = listOf(
            Finding(
                file = "Test.kt",
                line = 10,
                severity = Severity.HIGH,
                category = "bug",
                message = "Potential NPE",
                description = "Null pointer exception risk"
            )
        )
        
        val step = ToolPlanStep(
            description = "Generate markdown report",
            agentType = AgentType.REPORT_GENERATOR,
            input = StepInput.of(
                "format" to "markdown",
                "title" to "Test Report",
                "findings" to findings
            )
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val report = result.output.get<String>("report")
        assertNotNull(report)
        assertTrue(report!!.contains("# Test Report"))
        assertTrue(report.contains("Potential NPE"))
        assertTrue(report.contains("HIGH"))
    }
    
    @Test
    fun `should generate html report`() = runTest {
        val findings = listOf(
            Finding(
                file = "Test.kt",
                line = 10,
                severity = Severity.CRITICAL,
                category = "bug",
                message = "Critical bug"
            )
        )
        
        val step = ToolPlanStep(
            description = "Generate HTML report",
            agentType = AgentType.REPORT_GENERATOR,
            input = StepInput.of(
                "format" to "html",
                "findings" to findings
            )
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val report = result.output.get<String>("report")
        assertNotNull(report)
        assertTrue(report!!.contains("<!DOCTYPE html>"))
        assertTrue(report.contains("Critical bug"))
        assertTrue(report.contains("critical"))
    }
    
    @Test
    fun `should generate json report`() = runTest {
        val findings = listOf(
            Finding(
                file = "Test.kt",
                line = 10,
                severity = Severity.MEDIUM,
                category = "quality",
                message = "Code smell"
            )
        )
        
        val step = ToolPlanStep(
            description = "Generate JSON report",
            agentType = AgentType.REPORT_GENERATOR,
            input = StepInput.of(
                "format" to "json",
                "findings" to findings
            )
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val report = result.output.get<String>("report")
        assertNotNull(report)
        assertTrue(report!!.contains("\"findings\""))
        assertTrue(report.contains("Code smell"))
        assertTrue(report.contains("MEDIUM"))
    }
    
    @Test
    fun `should include metrics in report`() = runTest {
        val metrics = mapOf(
            "total_files" to 10,
            "total_lines" to 1000
        )
        
        val step = ToolPlanStep(
            description = "Generate report with metrics",
            agentType = AgentType.REPORT_GENERATOR,
            input = StepInput.of(
                "format" to "markdown",
                "metrics" to metrics,
                "findings" to emptyList<Finding>()
            )
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val report = result.output.get<String>("report")
        assertNotNull(report)
        assertTrue(report!!.contains("Метрики"))
        assertTrue(report.contains("10"))
        assertTrue(report.contains("1000"))
    }
    
    @Test
    fun `should handle empty findings`() = runTest {
        val step = ToolPlanStep(
            description = "Generate report with no findings",
            agentType = AgentType.REPORT_GENERATOR,
            input = StepInput.of(
                "format" to "markdown",
                "findings" to emptyList<Finding>()
            )
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val report = result.output.get<String>("report")
        assertNotNull(report)
        assertTrue(report!!.contains("Проблем не обнаружено"))
    }
    
    @Test
    fun `should group findings by severity in markdown`() = runTest {
        val findings = listOf(
            Finding(file = "Test1.kt", line = 1, severity = Severity.CRITICAL, category = "bug", message = "Critical 1"),
            Finding(file = "Test2.kt", line = 2, severity = Severity.CRITICAL, category = "bug", message = "Critical 2"),
            Finding(file = "Test3.kt", line = 3, severity = Severity.HIGH, category = "bug", message = "High 1"),
            Finding(file = "Test4.kt", line = 4, severity = Severity.LOW, category = "quality", message = "Low 1")
        )
        
        val step = ToolPlanStep(
            description = "Generate grouped report",
            agentType = AgentType.REPORT_GENERATOR,
            input = StepInput.of(
                "format" to "markdown",
                "findings" to findings
            )
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val report = result.output.get<String>("report")
        assertNotNull(report)
        assertTrue(report!!.contains("CRITICAL"))
        assertTrue(report.contains("HIGH"))
        assertTrue(report.contains("LOW"))
    }
    
    @Test
    fun `should return metadata with generation info`() = runTest {
        val step = ToolPlanStep(
            description = "Check metadata",
            agentType = AgentType.REPORT_GENERATOR,
            input = StepInput.of(
                "format" to "markdown",
                "findings" to emptyList<Finding>()
            )
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        assertTrue(result.metadata.containsKey("findings_count"))
        assertTrue(result.metadata.containsKey("generated_at"))
    }
}
