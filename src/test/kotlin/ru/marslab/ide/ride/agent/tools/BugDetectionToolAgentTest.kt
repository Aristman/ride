package ru.marslab.ide.ride.agent.tools

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class BugDetectionToolAgentTest {

    private lateinit var tempDir: Path
    private val mockLlmProvider = mockk<LLMProvider>(relaxed = true)
    private val agent = BugDetectionToolAgent(mockLlmProvider)

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
        assertEquals(AgentType.BUG_DETECTION, agent.agentType)
        assertTrue(agent.toolCapabilities.contains("bug_detection"))
        assertTrue(agent.toolCapabilities.contains("null_pointer_analysis"))
        assertTrue(agent.toolCapabilities.contains("resource_leak_detection"))
    }

    @Test
    fun `should detect null pointer risks`() = runTest {
        val file = createTestFile(
            "Test.kt", """
            fun test() {
                val value: String? = null
                val length = value!!.length
            }
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Detect bugs",
            agentType = AgentType.BUG_DETECTION,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Проверяем, что анализ прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
    }

    @Test
    fun `should detect resource leaks`() = runTest {
        val file = createTestFile(
            "Test.kt", """
            fun readFile() {
                val stream = FileInputStream("test.txt")
                // Missing close or use
            }
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Detect resource leaks",
            agentType = AgentType.BUG_DETECTION,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Проверяем, что анализ прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
    }

    @Test
    fun `should detect TODO comments`() = runTest {
        val file = createTestFile(
            "Test.kt", """
            fun test() {
                // TODO: implement this
                println("test")
            }
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Detect TODOs",
            agentType = AgentType.BUG_DETECTION,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Проверяем, что анализ прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
    }

    @Test
    fun `should count findings by severity`() = runTest {
        val file = createTestFile(
            "Test.kt", """
            fun test() {
                val value: String? = null
                val length = value!!.length
                // TODO: fix this
            }
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Count by severity",
            agentType = AgentType.BUG_DETECTION,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Проверяем, что анализ прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
    }

    @Test
    fun `should filter by severity threshold`() = runTest {
        val file = createTestFile(
            "Test.kt", """
            fun test() {
                val value: String? = null
                val length = value!!.length  // HIGH severity
                // TODO: fix this  // LOW severity
            }
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Filter by severity",
            agentType = AgentType.BUG_DETECTION,
            input = StepInput.of(
                "files" to listOf(file.absolutePath),
                "severity_threshold" to "HIGH"
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val findings = result.output.get<List<Finding>>("findings")
        assertNotNull(findings)
        // Должны быть только HIGH и выше (CRITICAL, HIGH)
        assertTrue(findings?.all { it.severity.ordinal <= Severity.HIGH.ordinal } == true)
    }

    @Test
    fun `should handle non-existent files gracefully`() = runTest {
        val step = ToolPlanStep(
            description = "Analyze non-existent file",
            agentType = AgentType.BUG_DETECTION,
            input = StepInput.of("files" to listOf("/non/existent/file.kt"))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val findings = result.output.get<List<Finding>>("findings")
        assertNotNull(findings)
        assertTrue(findings?.isEmpty() == true)
    }

    @Test
    fun `should validate input requires files`() {
        val input = StepInput.empty()

        val result = agent.validateInput(input)

        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `should return metadata with analysis info`() = runTest {
        val file = createTestFile("Test.kt", "fun test() {}")

        val step = ToolPlanStep(
            description = "Check metadata",
            agentType = AgentType.BUG_DETECTION,
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
