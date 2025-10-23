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

class ArchitectureToolAgentTest {

    private lateinit var tempDir: Path
    private val mockLlmProvider = mockk<LLMProvider>(relaxed = true)
    private val agent = ArchitectureToolAgent(mockLlmProvider)

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
        assertEquals(AgentType.ARCHITECTURE_ANALYSIS, agent.agentType)
        assertTrue(agent.toolCapabilities.contains("architecture_analysis"))
        assertTrue(agent.toolCapabilities.contains("dependency_analysis"))
        assertTrue(agent.toolCapabilities.contains("layer_detection"))
    }

    @Test
    fun `should validate input requires files`() {
        val input = StepInput.empty()

        val result = agent.validateInput(input)

        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `should detect packages`() = runTest {
        val file1 = createTestFile(
            "Service.kt", """
            package com.example.service
            
            class UserService
        """.trimIndent()
        )

        val file2 = createTestFile(
            "Model.kt", """
            package com.example.model
            
            data class User(val id: Int)
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Analyze architecture",
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
            input = StepInput.of("files" to listOf(file1.absolutePath, file2.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val packages = result.output.get<List<String>>("packages")
        assertNotNull(packages)
        assertTrue(packages!!.contains("com.example.service"))
        assertTrue(packages.contains("com.example.model"))
    }

    @Test
    fun `should detect layers`() = runTest {
        val file1 = createTestFile(
            "UI.kt", """
            package com.example.ui
            
            class MainActivity
        """.trimIndent()
        )

        val file2 = createTestFile(
            "Service.kt", """
            package com.example.service
            
            class UserService
        """.trimIndent()
        )

        val file3 = createTestFile(
            "Model.kt", """
            package com.example.model
            
            data class User(val id: Int)
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Detect layers",
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
            input = StepInput.of(
                "files" to listOf(
                    file1.absolutePath,
                    file2.absolutePath,
                    file3.absolutePath
                )
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val layers = result.output.get<List<String>>("layers")
        assertNotNull(layers)
        assertTrue(layers!!.contains("presentation"))
        assertTrue(layers.contains("service"))
        assertTrue(layers.contains("model"))
    }

    @Test
    fun `should detect circular dependencies`() = runTest {
        val file1 = createTestFile(
            "ClassA.kt", """
            package com.example.a
            
            import com.example.b.ClassB
            
            class ClassA
        """.trimIndent()
        )

        val file2 = createTestFile(
            "ClassB.kt", """
            package com.example.b
            
            import com.example.a.ClassA
            
            class ClassB
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Detect cycles",
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
            input = StepInput.of("files" to listOf(file1.absolutePath, file2.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val cycles = result.output.get<List<List<String>>>("cycles")
        assertNotNull(cycles)
        assertTrue(cycles!!.isNotEmpty(), "Should detect circular dependency")
    }

    @Test
    fun `should detect layer violations`() = runTest {
        val file1 = createTestFile(
            "Model.kt", """
            package com.example.model
            
            import com.example.ui.MainActivity
            
            data class User(val id: Int)
        """.trimIndent()
        )

        val file2 = createTestFile(
            "UI.kt", """
            package com.example.ui
            
            class MainActivity
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Detect layer violations",
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
            input = StepInput.of("files" to listOf(file1.absolutePath, file2.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val findings = result.output.get<List<Finding>>("findings")
        assertNotNull(findings)
        // Model layer should not depend on UI layer
        assertTrue(findings?.any { it.category == "layer_violation" } == true)
    }

    @Test
    fun `should return package count`() = runTest {
        val file1 = createTestFile(
            "Service.kt", """
            package com.example.service
            class UserService
        """.trimIndent()
        )

        val file2 = createTestFile(
            "Model.kt", """
            package com.example.model
            data class User(val id: Int)
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Count packages",
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
            input = StepInput.of("files" to listOf(file1.absolutePath, file2.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val packageCount = result.output.get<Int>("package_count")
        assertNotNull(packageCount)
        assertEquals(2, packageCount)
    }

    @Test
    fun `should handle files without package declaration`() = runTest {
        val file = createTestFile(
            "Test.kt", """
            class Test
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Analyze file without package",
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
    }

    @Test
    fun `should return metadata with analysis info`() = runTest {
        val file = createTestFile(
            "Test.kt", """
            package com.example
            class Test
        """.trimIndent()
        )

        val step = ToolPlanStep(
            description = "Check metadata",
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
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
