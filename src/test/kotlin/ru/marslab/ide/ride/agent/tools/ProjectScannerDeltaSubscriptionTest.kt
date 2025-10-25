package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ProjectScannerDeltaSubscriptionTest {

    private lateinit var tempDir: Path
    private val agent = ProjectScannerToolAgent()
    private var lastDeltaUpdate: DeltaUpdate? = null
    private val deltaCallback: (DeltaUpdate) -> Unit = { update ->
        lastDeltaUpdate = update
    }

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("test")
    }

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should create delta subscription successfully`() {
        val agentId = "test-agent"
        val projectPath = tempDir.toString()

        val subscriptionId = agent.createDeltaSubscription(agentId, projectPath, deltaCallback)

        assertNotNull(subscriptionId, "Subscription ID should not be null")
        assertTrue(subscriptionId.isNotEmpty(), "Subscription ID should not be empty")
    }

    @Test
    fun `should cancel delta subscription successfully`() {
        val agentId = "test-agent"
        val projectPath = tempDir.toString()

        val subscriptionId = agent.createDeltaSubscription(agentId, projectPath, deltaCallback)
        val cancelled = agent.cancelDeltaSubscription(subscriptionId)

        assertTrue(cancelled, "Subscription should be cancelled successfully")
    }

    @Test
    fun `should return false when cancelling non-existent subscription`() {
        val nonExistentId = "non-existent-id"
        val cancelled = agent.cancelDeltaSubscription(nonExistentId)

        assertFalse(cancelled, "Should return false for non-existent subscription")
    }

    @Test
    fun `should create multiple subscriptions for different agents`() {
        val projectPath = tempDir.toString()

        val subscription1 = agent.createDeltaSubscription("agent1", projectPath) { }
        val subscription2 = agent.createDeltaSubscription("agent2", projectPath) { }

        assertNotNull(subscription1)
        assertNotNull(subscription2)
        assertNotEquals(subscription1, subscription2, "Subscription IDs should be different")
    }

    @Test
    fun `should handle scan with delta timestamp`() = runTest {
        // Создаем тестовый файл
        createTestFile("Test.kt", "class Test")

        val step = ToolPlanStep(
            description = "Scan project with delta",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", tempDir.toString())
                .set("since_ts", 0L) // Запрашиваем все изменения
                .set("batch_size", 100)
        )

        val context = ExecutionContext(projectPath = tempDir.toString())

        val result = agent.executeStep(step, context)

        assertTrue(result.success, "Scan should succeed: ${result.error}")

        // Проверяем наличие дельты в результате
        val jsonResult = result.output.get<Map<String, Any>>("json")
        assertNotNull(jsonResult, "JSON result should not be null")

        val delta = jsonResult?.get("delta") as? Map<String, Any>
        assertNotNull(delta, "Delta should be present in result")
        assertEquals(0L, delta?.get("since_ts"))
    }

    @Test
    fun `should return empty delta for recent timestamp`() = runTest {
        // Создаем тестовый файл
        createTestFile("Test.kt", "class Test")

        val currentTimestamp = System.currentTimeMillis()

        val step = ToolPlanStep(
            description = "Scan with recent timestamp",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", tempDir.toString())
                .set("since_ts", currentTimestamp)
                .set("batch_size", 100)
        )

        val context = ExecutionContext(projectPath = tempDir.toString())

        val result = agent.executeStep(step, context)

        assertTrue(result.success, "Scan should succeed: ${result.error}")

        val jsonResult = result.output.get<Map<String, Any>>("json")
        val delta = jsonResult?.get("delta") as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val changedFiles = delta?.get("changed_files") as? List<String>

        assertTrue(changedFiles?.isEmpty() ?: true, "Should have no changed files for recent timestamp")
    }

    @Test
    fun `should support batching with delta`() = runTest {
        // Создаем несколько тестовых файлов
        createTestFile("Test1.kt", "class Test1")
        createTestFile("Test2.kt", "class Test2")
        createTestFile("Test3.kt", "class Test3")

        val step = ToolPlanStep(
            description = "Scan with batching and delta",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", tempDir.toString())
                .set("since_ts", 0L)
                .set("page", 1)
                .set("batch_size", 2)
        )

        val context = ExecutionContext(projectPath = tempDir.toString())

        val result = agent.executeStep(step, context)

        assertTrue(result.success, "Scan should succeed: ${result.error}")

        val jsonResult = result.output.get<Map<String, Any>>("json")
        val batch = jsonResult?.get("batch") as? Map<String, Any>

        assertNotNull(batch, "Batch info should be present")
        assertEquals(1, batch?.get("page"))
        assertEquals(2, batch?.get("batch_size"))
        assertEquals(3, batch?.get("total"))
        assertEquals(true, batch?.get("has_more"))
    }

    @Test
    fun `should handle delta subscription with file modifications simulation`() {
        val agentId = "test-agent"
        val projectPath = tempDir.toString()

        // Создаем подписку
        val subscriptionId = agent.createDeltaSubscription(agentId, projectPath, deltaCallback)
        assertNotNull(subscriptionId)

        // Создаем файл для инициализации
        createTestFile("Initial.kt", "class Initial")

        // Имитируем изменение файлов (через создание нового файла)
        createTestFile("NewFile.kt", "class NewFile")

        // Отменяем подписку
        val cancelled = agent.cancelDeltaSubscription(subscriptionId)
        assertTrue(cancelled)
    }

    // Helper methods

    private fun createTestFile(relativePath: String, content: String) {
        val file = File(tempDir.toFile(), relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }
}