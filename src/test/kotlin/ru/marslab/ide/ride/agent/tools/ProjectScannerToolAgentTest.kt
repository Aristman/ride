package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ProjectScannerToolAgentTest {

    private lateinit var tempDir: Path
    private val agent = ProjectScannerToolAgent()

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
        assertEquals(AgentType.PROJECT_SCANNER, agent.agentType)
        assertTrue(agent.toolCapabilities.contains("file_discovery"))
        assertTrue(agent.toolCapabilities.contains("directory_tree"))
        assertTrue(agent.toolCapabilities.contains("cache_management"))
        assertTrue(agent.toolCapabilities.contains("file_monitoring"))
        assertTrue(agent.toolCapabilities.contains("project_type_detection"))
        assertTrue(agent.toolCapabilities.contains("adaptive_filtering"))
        assertTrue(agent.toolCapabilities.contains("file_analysis"))
        assertTrue(agent.toolCapabilities.contains("multi_agent_integration"))
    }

    @Test
    fun `should always validate input successfully`() {
        val input = StepInput.empty()

        val result = agent.validateInput(input)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `should scan directory and find kotlin files`() = runTest {
        // Создаем тестовые файлы
        createTestFile("Test1.kt", "class Test1")
        createTestFile("Test2.kt", "class Test2")
        createTestFile("Test3.java", "class Test3")

        val step = ToolPlanStep(
            description = "Scan for Kotlin files",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", tempDir.toString())
                .set("include_patterns", listOf("*.kt"))
        )

        val context = ExecutionContext(projectPath = tempDir.toString())

        val result = agent.executeStep(step, context)

        assertTrue(result.success, "Scan should succeed: ${result.error}")
        val files = result.output.get<List<String>>("files")
        assertNotNull(files, "Files list should not be null")
        assertTrue(files!!.isNotEmpty(), "Should find at least one file")
        assertTrue(files.all { it.endsWith(".kt") }, "All files should end with .kt")
    }

    @Test
    fun `should exclude files by pattern`() = runTest {
        // Создаем тестовые файлы
        createTestFile("Test1.kt", "class Test1")
        createTestFile("build/Test3.kt", "class Test3")

        val step = ToolPlanStep(
            description = "Scan excluding build directory",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", tempDir.toString())
                .set("include_patterns", listOf("*.kt"))
                .set("exclude_patterns", listOf("build/**"))
        )

        val context = ExecutionContext(projectPath = tempDir.toString())

        val result = agent.executeStep(step, context)

        assertTrue(result.success, "Scan should succeed: ${result.error}")
        val files = result.output.get<List<String>>("files")
        assertNotNull(files, "Files list should not be null")
        assertTrue(files!!.none { it.contains("build") }, "No files should be from build directory")
    }

    @Test
    fun `should return scan time and count`() = runTest {
        createTestFile("Test.kt", "class Test")

        val step = ToolPlanStep(
            description = "Scan project",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", tempDir.toString())
                .set("include_patterns", listOf("**/*.kt"))
        )

        val context = ExecutionContext(projectPath = tempDir.toString())

        val result = agent.executeStep(step, context)

        assertTrue(result.success, "Scan should succeed: ${result.error}")
        val stats = result.output.get<Map<String, Any>>("stats")
        assertNotNull(stats, "Stats should not be null")
        assertTrue(stats!!.containsKey("total_files"))
        assertTrue(stats.containsKey("scan_time_ms"))
    }

    @Test
    fun `should handle non-existent project path`() = runTest {
        val step = ToolPlanStep(
            description = "Scan non-existent path",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", "/non/existent/path")
                .set("include_patterns", listOf("**/*.kt"))
        )

        val context = ExecutionContext(projectPath = "/non/existent/path")

        val result = agent.executeStep(step, context)

        assertFalse(result.success, "Scan should fail for non-existent path")
        assertNotNull(result.error, "Error should not be null")
    }

    @Test
    fun `should handle missing project path in context`() = runTest {
        val step = ToolPlanStep(
            description = "Scan without project path",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("include_patterns", listOf("**/*.kt"))
        )

        val context = ExecutionContext(projectPath = null)

        val result = agent.executeStep(step, context)

        assertFalse(result.success, "Scan should fail without project path")
        assertNotNull(result.error, "Error should not be null")
    }

    @Test
    fun `should respect max depth parameter`() = runTest {
        // Создаем вложенную структуру
        createTestFile("level1/Test1.kt", "class Test1")
        createTestFile("level1/level2/Test2.kt", "class Test2")
        createTestFile("level1/level2/level3/Test3.kt", "class Test3")

        val step = ToolPlanStep(
            description = "Scan with max depth",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", tempDir.toString())
                .set("include_patterns", listOf("**/*.kt"))
                .set("max_directory_depth", 2)
        )

        val context = ExecutionContext(projectPath = tempDir.toString())

        val result = agent.executeStep(step, context)

        assertTrue(result.success, "Scan should succeed: ${result.error}")
        val files = result.output.get<List<String>>("files")
        assertNotNull(files, "Files should not be null")
        // С max_depth=2 должны найти только файлы на уровнях 1 и 2
        assertTrue(files!!.size <= 2, "Should find at most 2 files with depth limit")
    }

    // Helper methods

    private fun createTestFile(relativePath: String, content: String) {
        val file = File(tempDir.toFile(), relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}
