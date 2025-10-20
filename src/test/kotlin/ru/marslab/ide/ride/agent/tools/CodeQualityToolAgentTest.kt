package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.io.path.createTempDirectory
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.nio.file.Path

class CodeQualityToolAgentTest {
    
    private lateinit var tempDir: Path
    private val agent = CodeQualityToolAgent()
    
    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("test")
    }
    
    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }
    
    @Test
    fun `should have correct agent type and capabilities`() {
        assertEquals(AgentType.CODE_QUALITY, agent.agentType)
        assertTrue(agent.toolCapabilities.contains("code_quality_analysis"))
        assertTrue(agent.toolCapabilities.contains("code_smell_detection"))
        assertTrue(agent.toolCapabilities.contains("complexity_analysis"))
    }
    
    @Test
    fun `should validate input requires files`() {
        val input = StepInput.empty()
        
        val result = agent.validateInput(input)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `should detect long lines`() = runTest {
        val file = createTestFile("Test.kt", """
            fun test() {
                val veryLongLine = "This is a very long line that exceeds the recommended 120 character limit and should be detected by the code quality analyzer"
            }
        """.trimIndent())
        
        val step = ToolPlanStep(
            description = "Analyze code quality",
            agentType = AgentType.CODE_QUALITY,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val findings = result.output.get<List<Finding>>("findings")
        assertNotNull(findings)
        assertTrue(findings?.any { it.category == "long_line" } == true)
    }
    
    @Test
    fun `should detect magic numbers`() = runTest {
        val file = createTestFile("Test.kt", """
            fun calculate() {
                val result = value * 42 + 100
            }
        """.trimIndent())
        
        val step = ToolPlanStep(
            description = "Detect magic numbers",
            agentType = AgentType.CODE_QUALITY,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val findings = result.output.get<List<Finding>>("findings")
        assertNotNull(findings)
        assertTrue(findings?.any { it.category == "magic_number" } == true)
    }
    
    @Test
    fun `should detect deep nesting`() = runTest {
        val file = createTestFile("Test.kt", """
            fun test() {
                if (condition1) {
                    if (condition2) {
                        if (condition3) {
                            if (condition4) {
                                if (condition5) {
                                    println("too deep")
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent())
        
        val step = ToolPlanStep(
            description = "Detect deep nesting",
            agentType = AgentType.CODE_QUALITY,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val findings = result.output.get<List<Finding>>("findings")
        assertNotNull(findings)
        assertTrue(findings?.any { it.category == "deep_nesting" } == true)
    }
    
    @Test
    fun `should detect god class`() = runTest {
        val longCode = (1..600).joinToString("\n") { "    val field$it = $it" }
        val file = createTestFile("Test.kt", """
            class GodClass {
            $longCode
            }
        """.trimIndent())
        
        val step = ToolPlanStep(
            description = "Detect god class",
            agentType = AgentType.CODE_QUALITY,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val findings = result.output.get<List<Finding>>("findings")
        assertNotNull(findings)
        assertTrue(findings?.any { it.category == "god_class" } == true)
    }
    
    @Test
    fun `should return metrics`() = runTest {
        val file = createTestFile("Test.kt", """
            class Test {
                fun method1() {}
                fun method2() {}
            }
        """.trimIndent())
        
        val step = ToolPlanStep(
            description = "Get metrics",
            agentType = AgentType.CODE_QUALITY,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val metrics = result.output.get<Map<String, Any>>("metrics")
        assertNotNull(metrics)
        assertTrue(metrics!!.containsKey("total_lines"))
        assertTrue(metrics.containsKey("total_methods"))
        assertTrue(metrics.containsKey("total_classes"))
    }
    
    @Test
    fun `should handle non-existent files gracefully`() = runTest {
        val step = ToolPlanStep(
            description = "Analyze non-existent file",
            agentType = AgentType.CODE_QUALITY,
            input = StepInput.of("files" to listOf("/non/existent/file.kt"))
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        val findings = result.output.get<List<Finding>>("findings")
        assertNotNull(findings)
    }
    
    @Test
    fun `should return metadata with analysis info`() = runTest {
        val file = createTestFile("Test.kt", "class Test {}")
        
        val step = ToolPlanStep(
            description = "Check metadata",
            agentType = AgentType.CODE_QUALITY,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )
        
        val result = agent.executeStep(step, ExecutionContext())
        
        assertTrue(result.success)
        assertTrue(result.metadata.containsKey("files_analyzed"))
        assertEquals(1, result.metadata["files_analyzed"])
    }
    
    // Helper methods
    
    private fun createTestFile(name: String, content: String): File {
        val file = File(tempDir.toFile(), name)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }
}
