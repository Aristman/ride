package ru.marslab.ide.ride.agent.a2a

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import ru.marslab.ide.ride.agent.tools.A2AProjectScannerToolAgent
import java.nio.file.Files
import java.nio.file.Path

class A2AAgentsSmokeTest {

    @Test
    fun `scanner responds with file data via A2A bus`() = runBlocking {
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
        // Create scanner without registry and handle request directly
        val scanner = A2AProjectScannerToolAgent(bus)

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
        val filesResp = scanner.handleA2AMessage(reqFiles, bus) as AgentMessage.Response
        assertTrue(filesResp.success)
        val files = when (val p = filesResp.payload) {
            is MessagePayload.CustomPayload -> p.data["files"] as? List<*> ?: emptyList<Any>()
            is MessagePayload.ProjectStructurePayload -> p.files
            else -> emptyList<Any>()
        }
        assertTrue(files.any { it.toString().endsWith("Main.kt") })

        // No bug detection here to keep test minimal
    }
}
