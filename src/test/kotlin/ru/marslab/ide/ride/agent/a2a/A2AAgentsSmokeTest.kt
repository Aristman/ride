package ru.marslab.ide.ride.agent.a2a

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.marslab.ide.ride.agent.tools.A2ABugDetectionToolAgent
import ru.marslab.ide.ride.agent.tools.A2AProjectScannerToolAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.llm.TokenUsage
import java.nio.file.Files
import java.nio.file.Path

class A2AAgentsSmokeTest {

    @Test
    fun `scanner and bug detection interact via A2A bus`() = runBlocking {
        // Arrange: temp project with a small Kotlin file
        val tempDir: Path = Files.createTempDirectory("a2a-smoke-")
        val file = tempDir.resolve("Main.kt")
        Files.writeString(
            file,
            """
            package demo
            fun add(a: Int, b: Int) = a + b
            """.trimIndent()
        )

        val bus = MessageBusProvider.get()
        val registry = A2AAgentRegistry.getInstance()

        // Minimal LLM mock for BugDetection
        val llm: LLMProvider = mockk(relaxed = true)
        every { llm.isAvailable() } returns true
        every { llm.getProviderName() } returns "TestLLM"
        coEvery { llm.sendRequest(any(), any(), any(), any()) } returns LLMResponse(
            content = "OK",
            success = true,
            tokenUsage = TokenUsage(inputTokens = 10, outputTokens = 10, totalTokens = 20)
        )

        // Agents
        val scanner = A2AProjectScannerToolAgent(bus)
        val bugDetection = A2ABugDetectionToolAgent(llm, bus, registry)

        // Register
        registry.registerAgent(scanner)
        registry.registerAgent(bugDetection)

        // Act 1: request file data from scanner
        val reqFiles = AgentMessage.Request(
            senderId = "test-suite",
            messageType = "FILE_DATA_REQUEST",
            payload = MessagePayload.CustomPayload(
                type = "FILE_DATA_REQUEST",
                data = mapOf(
                    "project_path" to tempDir.toAbsolutePath().toString(),
                    "file_extensions" to listOf(".kt"),
                    "max_files" to 10,
                    "include_content" to false
                )
            )
        )
        val filesResp = bus.requestResponse(reqFiles)
        assertTrue(filesResp.success)
        val files = when (val p = filesResp.payload) {
            is MessagePayload.CustomPayload -> p.data["files"] as? List<*> ?: emptyList<Any>()
            is MessagePayload.ProjectStructurePayload -> p.files
            else -> emptyList<Any>()
        }
        assertTrue(files.any { it.toString().endsWith("Main.kt") })

        // Act 2: request bug analysis for received file
        val reqAnalysis = AgentMessage.Request(
            senderId = "test-suite",
            messageType = "BUG_ANALYSIS_REQUEST",
            payload = MessagePayload.CustomPayload(
                type = "BUG_ANALYSIS_REQUEST",
                data = mapOf(
                    "files" to listOf(file.toAbsolutePath().toString()),
                    "max_files" to 5
                )
            )
        )
        val analysisResp = bus.requestResponse(reqAnalysis)
        assertTrue(analysisResp.success)
        val processed = when (val pp = analysisResp.payload) {
            is MessagePayload.CodeAnalysisPayload -> pp.processedFiles
            is MessagePayload.CustomPayload -> (pp.data["processed_files"] as? Int) ?: 0
            else -> 0
        }
        assertEquals(1, processed)
    }
}
