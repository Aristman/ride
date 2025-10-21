package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.io.path.createTempDirectory
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.nio.file.Path

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
        assertTrue(agent.toolCapabilities.contains("pattern_matching"))
        assertTrue(agent.toolCapabilities.contains("exclusion_filtering"))
    }
    
    @Test
    fun `should validate input with patterns`() {
        val input = StepInput.of("patterns" to listOf("**/*.kt"))
        
        val result = agent.validateInput(input)
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `should fail validation without patterns`() {
        val input = StepInput.empty()
        
        val result = agent.validateInput(input)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
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
            input = StepInput.of("patterns" to listOf("*.kt"))
        )
        
        val context = ExecutionContext(projectPath = tempDir.toString())
        
        val result = agent.executeStep(step, context)
        
        assertTrue(result.success, "Scan should succeed")
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
            input = StepInput.of(
                "patterns" to listOf("*.kt"),
                "exclude_patterns" to listOf("build/*.kt")
            )
        )
        
        val context = ExecutionContext(projectPath = tempDir.toString())
        
        val result = agent.executeStep(step, context)
        
        assertTrue(result.success, "Scan should succeed")
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
            input = StepInput.of("patterns" to listOf("**/*.kt"))
        )
        
        val context = ExecutionContext(projectPath = tempDir.toString())
        
        val result = agent.executeStep(step, context)
        
        assertTrue(result.success)
        assertNotNull(result.output.get<Int>("total_count"))
        assertNotNull(result.output.get<Long>("scan_time"))
        assertTrue(result.output.get<Long>("scan_time")!! >= 0)
    }
    
    @Test
    fun `should handle non-existent project path`() = runTest {
        val step = ToolPlanStep(
            description = "Scan non-existent path",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.of("patterns" to listOf("**/*.kt"))
        )
        
        val context = ExecutionContext(projectPath = "/non/existent/path")
        
        val result = agent.executeStep(step, context)
        
        assertFalse(result.success)
        assertNotNull(result.error)
    }
    
    @Test
    fun `should handle missing project path in context`() = runTest {
        val step = ToolPlanStep(
            description = "Scan without project path",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.of("patterns" to listOf("**/*.kt"))
        )
        
        val context = ExecutionContext(projectPath = null)
        
        val result = agent.executeStep(step, context)
        
        assertFalse(result.success)
        assertNotNull(result.error)
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
            input = StepInput.of(
                "patterns" to listOf("**/*.kt"),
                "max_depth" to 2
            )
        )
        
        val context = ExecutionContext(projectPath = tempDir.toString())
        
        val result = agent.executeStep(step, context)
        
        assertTrue(result.success)
        val files = result.output.get<List<String>>("files")
        assertNotNull(files)
        // С max_depth=2 должны найти только файлы на уровнях 1 и 2
        assertTrue(files != null && files.size <= 2)
    }
    
    // Helper methods
    
    private fun createTestFile(relativePath: String, content: String) {
        val file = File(tempDir.toFile(), relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}
