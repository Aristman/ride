package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class CodeChunkerToolAgentTest {

    private lateinit var tempDir: Path
    private val agent = CodeChunkerToolAgent()

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
        assertEquals(AgentType.CODE_CHUNKER, agent.agentType)
        assertTrue(agent.toolCapabilities.contains("file_chunking"))
        assertTrue(agent.toolCapabilities.contains("token_counting"))
    }

    @Test
    fun `should validate input requires files`() {
        val input = StepInput.empty()

        val result = agent.validateInput(input)

        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `should validate chunk size must be positive`() {
        val input = StepInput.of(
            "files" to listOf("test.kt"),
            "chunk_size" to -10
        )

        val result = agent.validateInput(input)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("chunk_size") })
    }

    @Test
    fun `should create single chunk for small file`() = runTest {
        val content = (1..50).joinToString("\n") { "line $it" }
        val file = createTestFile("Small.kt", content)

        val step = ToolPlanStep(
            description = "Chunk small file",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of(
                "files" to listOf(file.absolutePath),
                "chunk_size" to 100
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val chunks = result.output.get<List<FileChunk>>("chunks")
        assertNotNull(chunks)
        assertEquals(1, chunks?.size, "Small file should create single chunk")
        assertEquals(0, chunks?.first()?.chunkIndex)
    }

    @Test
    fun `should create multiple chunks for large file`() = runTest {
        val content = (1..250).joinToString("\n") { "line $it" }
        val file = createTestFile("Large.kt", content)

        val step = ToolPlanStep(
            description = "Chunk large file",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of(
                "files" to listOf(file.absolutePath),
                "chunk_size" to 100,
                "overlap" to 10
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val chunks = result.output.get<List<FileChunk>>("chunks")
        assertNotNull(chunks)
        assertTrue(chunks!!.size > 1, "Large file should create multiple chunks")

        // Проверяем, что индексы последовательны
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.chunkIndex)
        }
    }

    @Test
    fun `should apply overlap between chunks`() = runTest {
        val content = (1..200).joinToString("\n") { "line $it" }
        val file = createTestFile("Test.kt", content)

        val step = ToolPlanStep(
            description = "Chunk with overlap",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of(
                "files" to listOf(file.absolutePath),
                "chunk_size" to 100,
                "overlap" to 20
            )
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val chunks = result.output.get<List<FileChunk>>("chunks")
        assertNotNull(chunks)

        if (chunks!!.size > 1) {
            // Проверяем, что есть перекрытие
            val firstChunkEnd = chunks[0].endLine
            val secondChunkStart = chunks[1].startLine
            assertTrue(secondChunkStart < firstChunkEnd, "Chunks should overlap")
        }
    }

    @Test
    fun `should estimate tokens`() = runTest {
        val content = "fun test() { println(\"Hello, World!\") }"
        val file = createTestFile("Test.kt", content)

        val step = ToolPlanStep(
            description = "Estimate tokens",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val chunks = result.output.get<List<FileChunk>>("chunks")
        assertNotNull(chunks)
        assertTrue(chunks!!.isNotEmpty())
        assertTrue(chunks.first().estimatedTokens > 0, "Should estimate tokens")
    }

    @Test
    fun `should return total token count`() = runTest {
        val file1 = createTestFile("Test1.kt", "fun test1() {}")
        val file2 = createTestFile("Test2.kt", "fun test2() {}")

        val step = ToolPlanStep(
            description = "Count total tokens",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of("files" to listOf(file1.absolutePath, file2.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val totalTokens = result.output.get<Int>("total_tokens")
        assertNotNull(totalTokens)
        assertTrue(totalTokens!! > 0)
    }

    @Test
    fun `should return chunk count`() = runTest {
        val file = createTestFile("Test.kt", (1..50).joinToString("\n") { "line $it" })

        val step = ToolPlanStep(
            description = "Count chunks",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val chunkCount = result.output.get<Int>("chunk_count")
        assertNotNull(chunkCount)
        assertTrue(chunkCount!! > 0)
    }

    @Test
    fun `should handle non-existent files gracefully`() = runTest {
        val step = ToolPlanStep(
            description = "Chunk non-existent file",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of("files" to listOf("/non/existent/file.kt"))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val chunks = result.output.get<List<FileChunk>>("chunks")
        assertNotNull(chunks)
    }

    @Test
    fun `should use default chunk size when not specified`() = runTest {
        val content = (1..50).joinToString("\n") { "line $it" }
        val file = createTestFile("Test.kt", content)

        val step = ToolPlanStep(
            description = "Chunk with defaults",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val chunks = result.output.get<List<FileChunk>>("chunks")
        assertNotNull(chunks)
        assertTrue(chunks!!.isNotEmpty())
    }

    @Test
    fun `should return metadata with processing info`() = runTest {
        val file = createTestFile("Test.kt", "fun test() {}")

        val step = ToolPlanStep(
            description = "Check metadata",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        assertTrue(result.metadata.containsKey("files_processed"))
        assertEquals(1, result.metadata["files_processed"])
    }

    @Test
    fun `chunks should contain file path`() = runTest {
        val file = createTestFile("Test.kt", "fun test() {}")

        val step = ToolPlanStep(
            description = "Check chunk file path",
            agentType = AgentType.CODE_CHUNKER,
            input = StepInput.of("files" to listOf(file.absolutePath))
        )

        val result = agent.executeStep(step, ExecutionContext())

        assertTrue(result.success)
        val chunks = result.output.get<List<FileChunk>>("chunks")
        assertNotNull(chunks)
        assertTrue(chunks!!.isNotEmpty())
        assertEquals(file.absolutePath, chunks.first().file)
    }

    // Helper methods

    private fun createTestFile(name: String, content: String): File {
        val file = File(tempDir.toFile(), name)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }
}
