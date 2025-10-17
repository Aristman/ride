package ru.marslab.ide.ride.service

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.model.chat.MessageRole
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты для интеграции TerminalAgent с ChatService
 */
class ChatServiceTerminalTest {

    private lateinit var chatService: ChatService
    private lateinit var mockProject: Project

    @BeforeEach
    fun setUp() {
        chatService = ChatService()
        mockProject = mockk<Project> {
            every { basePath } returns System.getProperty("user.dir")
        }
    }

    @Test
    fun `executeTerminalCommand executes simple echo command`() = runTest {
        var receivedMessage: Message? = null
        var errorOccurred = false

        chatService.executeTerminalCommand(
            command = "echo Hello Terminal",
            project = mockProject,
            onResponse = { message ->
                receivedMessage = message
            },
            onError = { error ->
                errorOccurred = true
                println("Error: $error")
            }
        )

        // Даем время на выполнение асинхронной операции
        kotlinx.coroutines.delay(2000)

        // Проверяем результат
        assertNotNull(receivedMessage, "Message should not be null")
        assertEquals(MessageRole.ASSISTANT, receivedMessage?.role)
        assertTrue(receivedMessage?.content?.contains("Hello Terminal") == true, 
            "Content should contain command output")
        assertTrue(receivedMessage?.content?.contains("✅ Success") == true,
            "Content should indicate success")
        
        // Проверяем метаданные
        assertEquals("terminal", receivedMessage?.metadata?.get("agentType"))
        assertEquals(0, receivedMessage?.metadata?.get("exitCode"))
        assertNotNull(receivedMessage?.metadata?.get("executionTime"))
        
        // Проверяем, что ошибок не было
        assertTrue(!errorOccurred, "No errors should occur")
    }

    @Test
    fun `executeTerminalCommand handles failed command`() = runTest {
        var receivedMessage: Message? = null
        var errorMessage: String? = null

        chatService.executeTerminalCommand(
            command = "exit 1",
            project = mockProject,
            onResponse = { message ->
                receivedMessage = message
            },
            onError = { error ->
                errorMessage = error
            }
        )

        // Даем время на выполнение
        kotlinx.coroutines.delay(2000)

        // Должна быть ошибка
        assertTrue(errorMessage != null || receivedMessage?.content?.contains("❌ Failed") == true,
            "Should indicate failure")
    }

    @Test
    fun `executeTerminalCommand adds message to history`() = runTest {
        val historyBefore = chatService.getHistory().size

        chatService.executeTerminalCommand(
            command = "echo test",
            project = mockProject,
            onResponse = { },
            onError = { }
        )

        // Даем время на выполнение
        kotlinx.coroutines.delay(2000)

        val historyAfter = chatService.getHistory().size
        
        // История должна увеличиться (добавится ответ ассистента)
        assertTrue(historyAfter > historyBefore, "History should grow after command execution")
    }

    @Test
    fun `executeTerminalCommand rejects empty command`() = runTest {
        var errorMessage: String? = null

        chatService.executeTerminalCommand(
            command = "",
            project = mockProject,
            onResponse = { },
            onError = { error ->
                errorMessage = error
            }
        )

        // Ошибка должна прийти сразу
        kotlinx.coroutines.delay(100)

        assertNotNull(errorMessage, "Should reject empty command")
        assertTrue(errorMessage?.contains("пустой") == true || errorMessage?.contains("empty") == true,
            "Error message should mention empty command")
    }

    @Test
    fun `executeTerminalCommand includes metadata in response`() = runTest {
        var receivedMessage: Message? = null

        chatService.executeTerminalCommand(
            command = "echo metadata test",
            project = mockProject,
            onResponse = { message ->
                receivedMessage = message
            },
            onError = { }
        )

        kotlinx.coroutines.delay(2000)

        assertNotNull(receivedMessage)
        
        val metadata = receivedMessage?.metadata
        assertNotNull(metadata)
        
        // Проверяем наличие всех ожидаемых метаданных
        assertTrue(metadata.containsKey("agentType"))
        assertTrue(metadata.containsKey("command"))
        assertTrue(metadata.containsKey("exitCode"))
        assertTrue(metadata.containsKey("executionTime"))
        assertTrue(metadata.containsKey("workingDir"))
        assertTrue(metadata.containsKey("responseTimeMs"))
        assertTrue(metadata.containsKey("isFinal"))
        assertTrue(metadata.containsKey("uncertainty"))
        
        // Проверяем значения
        assertEquals("terminal", metadata["agentType"])
        assertEquals("echo metadata test", metadata["command"])
        assertEquals(true, metadata["isFinal"])
        assertEquals(0.0, metadata["uncertainty"])
    }
}
