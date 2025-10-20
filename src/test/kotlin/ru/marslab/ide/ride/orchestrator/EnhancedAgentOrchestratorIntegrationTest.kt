package ru.marslab.ide.ride.orchestrator

import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import ru.marslab.ide.ride.agent.OrchestratorStep
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.llm.TokenUsage
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.orchestrator.impl.InMemoryPlanStorage
import ru.marslab.ide.ride.orchestrator.impl.LLMRequestAnalyzer

class EnhancedAgentOrchestratorIntegrationTest {

    private lateinit var llmProvider: LLMProvider
    private lateinit var uncertaintyAnalyzer: UncertaintyAnalyzer
    private lateinit var planStorage: InMemoryPlanStorage
    private lateinit var requestAnalyzer: LLMRequestAnalyzer
    private lateinit var orchestrator: EnhancedAgentOrchestrator

    @BeforeTest
    fun setUp() {
        llmProvider = mockk()
        uncertaintyAnalyzer = mockk()
        planStorage = InMemoryPlanStorage()
        requestAnalyzer = LLMRequestAnalyzer(llmProvider, uncertaintyAnalyzer)
        orchestrator = EnhancedAgentOrchestrator(
            llmProvider = llmProvider,
            uncertaintyAnalyzer = uncertaintyAnalyzer,
            planStorage = planStorage,
            requestAnalyzer = requestAnalyzer
        )

        // Настройка моков по умолчанию
        every { uncertaintyAnalyzer.analyzeUncertainty(any(), any()) } returns 0.1
        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse(
            success = true,
            content = createMockAnalysisJson(),
            metadata = mapOf("tokenUsage" to TokenUsage(100, 50, 150))
        )
    }

    @Test
    fun `should process complete workflow successfully`() = runTest {
        // Given
        val request = createTestAgentRequest("Проанализируй код на наличие багов")
        val stepNotifications = mutableListOf<OrchestratorStep>()

        // When
        val result = orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        // Then
        assertTrue(result.success)
        assertNotNull(result.content)

        // Verify step notifications
        assertTrue(stepNotifications.isNotEmpty())
        assertTrue(stepNotifications.any { it is OrchestratorStep.PlanningComplete })

        // Verify plan was created and stored
        val activePlans = orchestrator.getActivePlans()
        assertTrue(activePlans.isNotEmpty())

        // Verify progress tracking
        val planId = activePlans.first().id
        val progress = orchestrator.getPlanProgress(planId)
        assertNotNull(progress)
    }

    @Test
    fun `should handle code analysis request end-to-end`() = runTest {
        // Given
        val request = createTestAgentRequest("Найди все баги в проекте")
        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse(
            success = true,
            content = createMockAnalysisJson(
                taskType = "CODE_ANALYSIS",
                requiredTools = listOf("PROJECT_SCANNER", "BUG_DETECTION", "REPORT_GENERATOR"),
                complexity = "MEDIUM",
                estimatedSteps = 3,
                requiresUserInput = false
            )
        )

        val stepNotifications = mutableListOf<OrchestratorStep>()

        // When
        val result = orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        // Then
        assertTrue(result.success)
        assertTrue(result.content!!.contains("успешно выполнен"))

        // Verify plan contains correct tools
        val activePlans = orchestrator.getActivePlans()
        val plan = activePlans.first()
        assertTrue(plan.analysis.requiredTools.contains(AgentType.PROJECT_SCANNER))
        assertTrue(plan.analysis.requiredTools.contains(AgentType.BUG_DETECTION))
        assertTrue(plan.analysis.requiredTools.contains(AgentType.REPORT_GENERATOR))
    }

    @Test
    fun `should handle refactoring request with user input`() = runTest {
        // Given
        val request = createTestAgentRequest("Выполни рефакторинг архитектуры")
        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse(
            success = true,
            content = createMockAnalysisJson(
                taskType = "REFACTORING",
                requiredTools = listOf("ARCHITECTURE_ANALYSIS", "CODE_QUALITY"),
                complexity = "HIGH",
                estimatedSteps = 5,
                requiresUserInput = true
            )
        )

        val stepNotifications = mutableListOf<OrchestratorStep>()

        // When
        val result = orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        // Then
        assertTrue(result.success)

        // Verify plan requires user input
        val activePlans = orchestrator.getActivePlans()
        val plan = activePlans.first()
        assertTrue(plan.analysis.requiresUserInput)
        assertEquals(ComplexityLevel.HIGH, plan.analysis.estimatedComplexity)
    }

    @Test
    fun `should pause and resume plan execution`() = runTest {
        // Given
        val request = createTestAgentRequest("Долгая задача")
        val stepNotifications = mutableListOf<OrchestratorStep>()

        // Start execution
        orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        val activePlans = orchestrator.getActivePlans()
        val planId = activePlans.first().id

        // When
        val pauseResult = orchestrator.pausePlan(planId)
        val resumeResult = orchestrator.resumePlan(planId)

        // Then
        assertTrue(pauseResult)
        assertTrue(resumeResult)

        // Verify plan state changes
        val updatedPlan = planStorage.load(planId)
        assertNotNull(updatedPlan)
    }

    @Test
    fun `should handle user input for plans requiring input`() = runTest {
        // Given
        val request = createTestAgentRequest("Задача с уточнением")
        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse(
            success = true,
            content = createMockAnalysisJson(
                taskType = "SIMPLE_QUERY",
                requiredTools = emptyList(),
                complexity = "LOW",
                estimatedSteps = 1,
                requiresUserInput = true
            )
        )

        val stepNotifications = mutableListOf<OrchestratorStep>()

        orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        val activePlans = orchestrator.getActivePlans()
        val planId = activePlans.first().id

        // When
        val userInputResult = orchestrator.handleUserInput(planId, "Пользовательский ответ")

        // Then
        assertTrue(userInputResult)
    }

    @Test
    fun `should cancel plan execution`() = runTest {
        // Given
        val request = createTestAgentRequest("Отменяемая задача")
        val stepNotifications = mutableListOf<OrchestratorStep>()

        orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        val activePlans = orchestrator.getActivePlans()
        val planId = activePlans.first().id

        // When
        val cancelResult = orchestrator.cancelPlan(planId)

        // Then
        assertTrue(cancelResult)

        // Verify plan is cancelled
        val cancelledPlan = planStorage.load(planId)
        assertNotNull(cancelledPlan)
    }

    @Test
    fun `should handle LLM provider errors gracefully`() = runTest {
        // Given
        val request = createTestAgentRequest("Тестовый запрос")
        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse(
            success = false,
            content = "",
            error = "LLM API Error"
        )

        val stepNotifications = mutableListOf<OrchestratorStep>()

        // When
        val result = orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        // Then
        // Должен вернуться успешный результат с fallback анализом
        assertTrue(result.success)
        assertTrue(stepNotifications.isNotEmpty())
    }

    @Test
    fun `should track progress throughout execution`() = runTest {
        // Given
        val request = createTestAgentRequest("Задача для отслеживания прогресса")
        val stepNotifications = mutableListOf<OrchestratorStep>()

        // When
        orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        val activePlans = orchestrator.getActivePlans()
        val planId = activePlans.first().id

        // Then
        val progress = orchestrator.getPlanProgress(planId)
        assertNotNull(progress)
        assertEquals(3, progress.totalSteps) // Из-за инструментов в моке
        assertTrue(progress.isCompleted)
        assertTrue(progress.success)
    }

    @Test
    fun `should maintain plan persistence`() = runTest {
        // Given
        val request = createTestAgentRequest("Тест персистентности")
        val stepNotifications = mutableListOf<OrchestratorStep>()

        // When
        orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        val activePlansBefore = orchestrator.getActivePlans()
        val planId = activePlansBefore.first().id

        // Create new orchestrator instance (simulating restart)
        val newOrchestrator = EnhancedAgentOrchestrator(
            llmProvider = llmProvider,
            uncertaintyAnalyzer = uncertaintyAnalyzer,
            planStorage = planStorage,
            requestAnalyzer = requestAnalyzer
        )

        // Then
        val activePlansAfter = newOrchestrator.getActivePlans()
        assertTrue(activePlansAfter.any { it.id == planId })

        val savedPlan = planStorage.load(planId)
        assertNotNull(savedPlan)
        assertEquals(planId, savedPlan?.id)
    }

    @Test
    fun `should handle multiple concurrent plans`() = runTest {
        // Given
        val request1 = createTestAgentRequest("Первая задача")
        val request2 = createTestAgentRequest("Вторая задача")
        val request3 = createTestAgentRequest("Третья задача")

        val stepNotifications = mutableListOf<OrchestratorStep>()

        // When
        orchestrator.processEnhanced(request1) { stepNotifications.add(it) }
        orchestrator.processEnhanced(request2) { stepNotifications.add(it) }
        orchestrator.processEnhanced(request3) { stepNotifications.add(it) }

        // Then
        val activePlans = orchestrator.getActivePlans()
        assertEquals(3, activePlans.size)

        // Verify all plans have unique IDs
        val planIds = activePlans.map { it.id }.toSet()
        assertEquals(3, planIds.size)

        // Verify all plans have progress tracking
        activePlans.forEach { plan ->
            val progress = orchestrator.getPlanProgress(plan.id)
            assertNotNull(progress)
        }
    }

    @Test
    fun `should handle high uncertainty requests`() = runTest {
        // Given
        val request = createTestAgentRequest("Неопределенный запрос")
        every { uncertaintyAnalyzer.analyzeUncertainty(any(), any()) } returns 0.5 // Высокая неопределенность

        val stepNotifications = mutableListOf<OrchestratorStep>()

        // When
        val result = orchestrator.processEnhanced(request) { step ->
            stepNotifications.add(step)
        }

        // Then
        assertTrue(result.success)

        // Verify plan requires user input due to high uncertainty
        val activePlans = orchestrator.getActivePlans()
        val plan = activePlans.first()
        assertTrue(plan.analysis.requiresUserInput)
        assertTrue(plan.analysis.confidence < 1.0)
    }

    @Test
    fun `should dispose resources correctly`() {
        // Given
        val orchestrator = EnhancedAgentOrchestrator(
            llmProvider = llmProvider,
            uncertaintyAnalyzer = uncertaintyAnalyzer,
            planStorage = planStorage,
            requestAnalyzer = requestAnalyzer
        )

        // When & Then - should not throw exception
        orchestrator.dispose() // Should not throw exception
    }

    private fun createTestAgentRequest(requestText: String): AgentRequest {
        return AgentRequest(
            request = requestText,
            context = ChatContext(
                project = mockk(relaxed = true)
            )
        )
    }

    private fun createMockAnalysisJson(
        taskType: String = "CODE_ANALYSIS",
        requiredTools: List<String> = listOf("PROJECT_SCANNER", "BUG_DETECTION"),
        complexity: String = "MEDIUM",
        estimatedSteps: Int = 3,
        requiresUserInput: Boolean = false
    ): String {
        return """
        {
          "task_type": "$taskType",
          "required_tools": {
            "tools": {
              "list": {
                "value": ${requiredTools.map { "\"$it\"" }}
              }
            }
          },
          "complexity": "$complexity",
          "estimated_steps": $estimatedSteps,
          "requires_user_input": $requiresUserInput,
          "confidence": 0.9,
          "reasoning": "Analysis based on request content",
          "parameters": {
            "focus_area": "general analysis",
            "priority": "medium"
          }
        }
        """.trimIndent()
    }
}