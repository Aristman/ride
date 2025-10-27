package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.model.embedding.IndexingConfig
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class EmbeddingIndexerToolAgentTest {

    private lateinit var tempDir: Path
    private val agent = EmbeddingIndexerToolAgent()

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("embedding_test")
    }

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should have correct agent type and capabilities`() {
        assertEquals(AgentType.EMBEDDING_INDEXER, agent.agentType)
        assertTrue(agent.toolCapabilities.contains("embedding_indexing"))
        assertTrue(agent.toolCapabilities.contains("file_chunking"))
        assertTrue(agent.toolCapabilities.contains("semantic_search"))
        assertTrue(agent.toolCapabilities.contains("index_management"))
    }

    @Test
    fun `should validate input requires project_path for indexing`() {
        val input = StepInput.empty()
            .set("action", "index")

        val result = agent.validateInput(input)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("project_path") })
    }

    @Test
    fun `should validate input requires query for search`() {
        val input = StepInput.empty()
            .set("action", "search")

        val result = agent.validateInput(input)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("query") })
    }

    @Test
    fun `should validate clear action`() {
        val input = StepInput.empty()
            .set("action", "clear")

        val result = agent.validateInput(input)

        assertTrue(result.isValid)
    }

    @Test
    fun `should validate stats action`() {
        val input = StepInput.empty()
            .set("action", "stats")

        val result = agent.validateInput(input)

        assertTrue(result.isValid)
    }

    @Test
    fun `should reject unknown action`() {
        val input = StepInput.empty()
            .set("action", "unknown_action")

        val result = agent.validateInput(input)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Unknown action") })
    }

    @Test
    fun `should get statistics for empty index`() = runTest {
        val projectPath = tempDir.toString()
        
        val step = ToolPlanStep(
            description = "Get statistics",
            agentType = AgentType.EMBEDDING_INDEXER,
            input = StepInput.empty()
                .set("action", "stats")
                .set("project_path", projectPath)
        )

        val result = agent.executeStep(step, ExecutionContext(projectPath))

        assertTrue(result.success)
        val stats = result.output.get<Map<String, Int>>("statistics")
        assertNotNull(stats)
        assertEquals(0, stats["files"])
        assertEquals(0, stats["chunks"])
        assertEquals(0, stats["embeddings"])
    }

    @Test
    fun `should clear index successfully`() = runTest {
        val projectPath = tempDir.toString()
        
        val step = ToolPlanStep(
            description = "Clear index",
            agentType = AgentType.EMBEDDING_INDEXER,
            input = StepInput.empty()
                .set("action", "clear")
                .set("project_path", projectPath)
        )

        val result = agent.executeStep(step, ExecutionContext(projectPath))

        assertTrue(result.success)
        val message = result.output.get<String>("message")
        assertNotNull(message)
        assertTrue(message.contains("cleared"))
    }

    @Test
    fun `should update config`() {
        val newConfig = IndexingConfig(
            chunkSize = 500,
            chunkOverlap = 100,
            includePatterns = listOf("**/*.kt"),
            excludePatterns = listOf("**/test/**")
        )

        agent.updateConfig(newConfig)

        // Конфигурация обновлена, проверяем что не выбрасывается исключение
        assertTrue(true)
    }

    @Test
    fun `should set progress callback`() {
        var callbackInvoked = false
        
        agent.setProgressCallback { progress ->
            callbackInvoked = true
        }

        // Callback установлен, проверяем что не выбрасывается исключение
        assertTrue(true)
    }

    @Test
    fun `should dispose agent without errors`() {
        agent.dispose()
        // Проверяем что dispose не выбрасывает исключений
        assertTrue(true)
    }

    // Helper methods

    private fun createTestFile(name: String, content: String): File {
        val file = File(tempDir.toFile(), name)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    private fun createTestProject(): String {
        val projectDir = File(tempDir.toFile(), "test_project")
        projectDir.mkdirs()

        // Создаем несколько тестовых файлов
        createTestFile("test_project/Main.kt", """
            package com.example
            
            fun main() {
                println("Hello, World!")
            }
        """.trimIndent())

        createTestFile("test_project/Utils.kt", """
            package com.example
            
            object Utils {
                fun helper() = "helper"
            }
        """.trimIndent())

        return projectDir.absolutePath
    }
}
