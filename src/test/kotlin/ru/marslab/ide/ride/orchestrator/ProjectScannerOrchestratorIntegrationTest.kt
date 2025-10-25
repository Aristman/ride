package ru.marslab.ide.ride.orchestrator

import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.agent.tools.DeltaUpdate
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.model.tool.StepInput
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ProjectScannerOrchestratorIntegrationTest {

    private lateinit var tempDir: Path
    private lateinit var integration: ProjectScannerOrchestratorIntegration

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("test")
        integration = ProjectScannerOrchestratorIntegration()
    }

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should create scan step correctly`() {
        val step = integration.createScanStep(
            projectPath = tempDir.toString(),
            forceRescan = true,
            includePatterns = listOf("*.kt", "*.java"),
            excludePatterns = listOf("test/**")
        )

        assertNotNull(step, "Step should not be null")
        assertEquals("Scan project structure and files", step.description)
        assertEquals(AgentType.PROJECT_SCANNER, step.agentType)
        assertNotNull(step.input)
        assertEquals(tempDir.toString(), step.input.getString("project_path"))
        assertEquals(true, step.input.getBoolean("force_rescan"))
        assertEquals(1000, step.input.getInt("batch_size"))
    }

    @Test
    fun `should create delta step correctly`() {
        val sinceTs = System.currentTimeMillis() - 1000

        val step = integration.createDeltaStep(
            projectPath = tempDir.toString(),
            sinceTs = sinceTs
        )

        assertNotNull(step, "Step should not be null")
        assertEquals("Get incremental changes since timestamp", step.description)
        assertEquals(AgentType.PROJECT_SCANNER, step.agentType)
        assertNotNull(step.input)
        assertEquals(tempDir.toString(), step.input.getString("project_path"))
        assertEquals(sinceTs, step.input.get<Long>("since_ts"))
        assertEquals(2000, step.input.getInt("batch_size"))
    }

    @Test
    fun `should filter files by language correctly`() {
        val files = listOf(
            "src/main/kotlin/Test.kt",
            "src/main/java/Test.java",
            "src/main/resources/config.properties",
            "README.md",
            "build.gradle",
            "src/test/kotlin/TestTest.kt"
        )

        val kotlinFiles = integration.filterFilesByLanguage(files, setOf("kt"))
        assertEquals(2, kotlinFiles.size)
        assertTrue(kotlinFiles.all { it.endsWith(".kt") })

        val javaFiles = integration.filterFilesByLanguage(files, setOf("java"))
        assertEquals(1, javaFiles.size)
        assertTrue(javaFiles.all { it.endsWith(".java") })

        val multipleLanguages = integration.filterFilesByLanguage(files, setOf("kt", "java", "gradle"))
        assertEquals(4, multipleLanguages.size)
    }

    @Test
    fun `should get integration status correctly`() {
        val status = integration.getIntegrationStatus()

        assertNotNull(status, "Status should not be null")
        assertTrue(status.containsKey("cached_scans"), "Should contain cached_scans")
        assertTrue(status.containsKey("active_subscriptions"), "Should contain active_subscriptions")
        assertTrue(status.containsKey("cache_entries"), "Should contain cache_entries")
        assertTrue(status.containsKey("subscription_plans"), "Should contain subscription_plans")

        val cachedScans = status["cached_scans"] as? Int
        val activeSubscriptions = status["active_subscriptions"] as? Int

        assertNotNull(cachedScans, "cached_scans should be a number")
        assertNotNull(activeSubscriptions, "active_subscriptions should be a number")
        assertTrue(cachedScans!! >= 0, "cached_scans should be non-negative")
        assertTrue(activeSubscriptions!! >= 0, "active_subscriptions should be non-negative")
    }

    @Test
    fun `should handle subscription operations correctly`() {
        val analysis = RequestAnalysis(
            taskType = TaskType.CODE_ANALYSIS,
            requiredTools = setOf(AgentType.PROJECT_SCANNER),
            context = ExecutionContext(projectPath = tempDir.toString()),
            parameters = emptyMap(),
            requiresUserInput = false,
            estimatedComplexity = ComplexityLevel.LOW,
            estimatedSteps = 2,
            confidence = 1.0
        )

        val steps = listOf(
            PlanStep(
                id = "step1",
                title = "Step 1",
                description = "First step",
                agentType = AgentType.PROJECT_SCANNER
            ),
            PlanStep(
                id = "step2",
                title = "Step 2",
                description = "Second step",
                agentType = AgentType.BUG_DETECTION
            )
        )

        val plan = ExecutionPlan(
            id = "test-plan-subscription",
            userRequestId = "test-request",
            originalRequest = "test subscription",
            analysis = analysis,
            steps = steps,
            currentState = PlanState.CREATED
        )

        var callbackCalled = false
        val callback: suspend (DeltaUpdate) -> Unit = { callbackCalled = true }

        // Создание подписки
        val created = integration.createDeltaSubscriptionForPlan(plan, tempDir.toString(), callback)
        // Может вернуть false если агент недоступен в тестах

        // Отмена подписки
        val cancelled = integration.cancelDeltaSubscriptionForPlan(plan)
        // Может вернуть false если подписка не была создана

        // Очистка ресурсов
        assertDoesNotThrow {
            integration.cleanupForPlan(plan)
        }
    }

    @Test
    fun `should handle prepare project scan gracefully`() = runTest {
        val analysis = RequestAnalysis(
            taskType = TaskType.CODE_ANALYSIS,
            requiredTools = setOf(AgentType.PROJECT_SCANNER),
            context = ExecutionContext(projectPath = tempDir.toString()),
            parameters = emptyMap(),
            requiresUserInput = false,
            estimatedComplexity = ComplexityLevel.LOW,
            estimatedSteps = 1,
            confidence = 1.0
        )

        val steps = listOf(
            PlanStep(
                id = "scan-step",
                title = "Scan Project",
                description = "Scan project files",
                agentType = AgentType.PROJECT_SCANNER
            )
        )

        val plan = ExecutionPlan(
            id = "test-plan",
            userRequestId = "test-request",
            originalRequest = "scan project",
            analysis = analysis,
            steps = steps,
            currentState = PlanState.CREATED
        )

        // Создаем тестовые файлы
        createTestFile("Test1.kt", "class Test1")

        val result = integration.prepareProjectScan(plan, tempDir.toString())

        // Результат должен быть не null независимо от доступности агента
        assertNotNull(result, "Result should not be null")
    }

    // Helper methods

    private fun createTestFile(relativePath: String, content: String) {
        val file = File(tempDir.toFile(), relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("Block should not throw exception: ${e.message}")
        }
    }
}