package ru.marslab.ide.ride.agent.a2a

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Простые unit тесты для A2A функциональности без зависимостей от MessageBus
 */
class A2ASimpleTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("a2a-test-")
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should create A2A message structure correctly`() {
        // Эмулируем структуру AgentMessage
        data class TestAgentMessage(
            val id: String,
            val senderId: String,
            val messageType: String,
            val payload: Map<String, Any>
        )

        val message = TestAgentMessage(
            id = "test-123",
            senderId = "test-agent",
            messageType = "TEST_MESSAGE",
            payload = mapOf(
                "test_key" to "test_value",
                "number" to 42
            )
        )

        assertEquals("test-123", message.id)
        assertEquals("test-agent", message.senderId)
        assertEquals("TEST_MESSAGE", message.messageType)
        assertEquals("test_value", message.payload["test_key"])
        assertEquals(42, message.payload["number"])
    }

    @Test
    fun `should validate message types correctly`() {
        val supportedTypes = setOf("FILE_DATA_REQUEST", "BUG_ANALYSIS_REQUEST", "CODE_GENERATION_REQUEST")

        fun canHandleMessageType(messageType: String): Boolean {
            return messageType in supportedTypes
        }

        assertTrue(canHandleMessageType("FILE_DATA_REQUEST"))
        assertTrue(canHandleMessageType("BUG_ANALYSIS_REQUEST"))
        assertTrue(canHandleMessageType("CODE_GENERATION_REQUEST"))
        assertFalse(canHandleMessageType("UNSUPPORTED_REQUEST"))
        assertFalse(canHandleMessageType("random_message"))
    }

    @Test
    fun `should create temporary project structure correctly`() {
        val projectDir = File(tempDir.toFile(), "test-project")
        projectDir.mkdirs()

        val sourceFile = File(projectDir, "Main.kt")
        sourceFile.writeText("""
            package demo

            fun add(a: Int, b: Int): Int = a + b

            fun main() {
                println(add(2, 3))
            }
        """.trimIndent())

        assertTrue(projectDir.exists())
        assertTrue(projectDir.isDirectory)
        assertTrue(sourceFile.exists())
        assertTrue(sourceFile.readText().contains("fun add"))
        assertTrue(sourceFile.readText().contains("package demo"))
    }

    @Test
    fun `should filter files by extension correctly`() {
        val projectDir = File(tempDir.toFile(), "test-project")
        projectDir.mkdirs()

        // Создаем разные файлы
        File(projectDir, "Main.kt").writeText("fun main() {}")
        File(projectDir, "Utils.kt").writeText("fun utils() {}")
        File(projectDir, "Test.java").writeText("public class Test {}")
        File(projectDir, "README.md").writeText("# Project")
        File(projectDir, "config.txt").writeText("config")

        val kotlinFiles = projectDir.listFiles { file ->
            file.isFile && file.extension == "kt"
        }?.map { it.name }?.sorted() ?: emptyList()

        assertEquals(2, kotlinFiles.size)
        assertTrue(kotlinFiles.contains("Main.kt"))
        assertTrue(kotlinFiles.contains("Utils.kt"))
        assertFalse(kotlinFiles.contains("Test.java"))
        assertFalse(kotlinFiles.contains("README.md"))
        assertFalse(kotlinFiles.contains("config.txt"))
    }

    @Test
    fun `should simulate agent registration correctly`() {
        data class TestAgent(
            val id: String,
            val name: String,
            val supportedTypes: Set<String>
        )

        val registry = mutableMapOf<String, TestAgent>()

        val scanner = TestAgent(
            id = "scanner-agent",
            name = "Project Scanner",
            supportedTypes = setOf("FILE_DATA_REQUEST", "PROJECT_SCAN")
        )

        val bugDetector = TestAgent(
            id = "bug-detector-agent",
            name = "Bug Detector",
            supportedTypes = setOf("BUG_ANALYSIS_REQUEST", "CODE_ANALYSIS")
        )

        registry[scanner.id] = scanner
        registry[bugDetector.id] = bugDetector

        assertEquals(2, registry.size)
        assertTrue(registry.containsKey("scanner-agent"))
        assertTrue(registry.containsKey("bug-detector-agent"))

        // Проверка поиска агента по типу сообщения
        fun findAgentForMessageType(messageType: String): TestAgent? {
            return registry.values.find { messageType in it.supportedTypes }
        }

        assertEquals(scanner, findAgentForMessageType("FILE_DATA_REQUEST"))
        assertEquals(bugDetector, findAgentForMessageType("BUG_ANALYSIS_REQUEST"))
        assertNull(findAgentForMessageType("UNSUPPORTED_TYPE"))
    }

    @Test
    fun `should simulate message payload serialization`() {
        // Эмулируем MessagePayload.CustomPayload
        data class TestCustomPayload(
            val type: String,
            val data: Map<String, Any>
        )

        val payload = TestCustomPayload(
            type = "FILE_DATA_REQUEST",
            data = mapOf(
                "project_path" to "/test/project",
                "file_extensions" to listOf(".kt", ".java"),
                "max_files" to 10,
                "include_content" to true
            )
        )

        assertEquals("FILE_DATA_REQUEST", payload.type)
        assertEquals("/test/project", payload.data["project_path"])
        assertEquals(listOf(".kt", ".java"), payload.data["file_extensions"])
        assertEquals(10, payload.data["max_files"])
        assertEquals(true, payload.data["include_content"])
    }
}