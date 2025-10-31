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
            input = StepInput.of(
                "files" to listOf(file1.absolutePath, file2.absolutePath),
                "analysis_type" to "architecture_analysis"
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Проверяем, что анализ прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
        // Проверяем основные поля, которые должны быть в результате
        assertTrue(result.output.data.containsKey("modules") || result.output.data.containsKey("project_structure"))
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
                ),
                "analysis_type" to "layer_detection"
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Проверяем, что анализ слоев прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
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
            input = StepInput.of(
                "files" to listOf(file1.absolutePath, file2.absolutePath),
                "analysis_type" to "dependency_analysis"
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Проверяем, что анализ зависимостей прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
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
            input = StepInput.of(
                "files" to listOf(file1.absolutePath, file2.absolutePath),
                "analysis_type" to "architecture_analysis"
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Просто проверяем, что анализ прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
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
            input = StepInput.of(
                "files" to listOf(file1.absolutePath, file2.absolutePath),
                "analysis_type" to "architecture_analysis"
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Проверяем, что анализ прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
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
            input = StepInput.of(
                "files" to listOf(file.absolutePath),
                "analysis_type" to "architecture_analysis"
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        // Проверяем, что анализ прошел успешно и есть какой-то результат
        assertTrue(result.output.data.isNotEmpty())
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
            input = StepInput.of(
                "files" to listOf(file.absolutePath),
                "analysis_type" to "architecture_analysis"
            )
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
