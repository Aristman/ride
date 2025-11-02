package ru.marslab.ide.ride.agent.planner

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.*
import ru.marslab.ide.ride.agent.analyzer.*
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.tool.StepOutput
import ru.marslab.ide.ride.agent.ConditionalStep
import com.intellij.openapi.project.Project
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.runBlocking

/**
 * Тесты для AdaptiveRequestPlanner
 */
class AdaptiveRequestPlannerTest {

    private lateinit var adaptivePlanner: AdaptiveRequestPlanner
    private lateinit var mockProject: Project
    private lateinit var context: ChatContext

    @BeforeEach
    fun setUp() {
        adaptivePlanner = AdaptiveRequestPlanner()
        mockProject = mock()
        context = ChatContext(project = mockProject, history = emptyList())

        whenever(mockProject.basePath).thenReturn("/test/project")
    }

    @Test
    fun `should create adaptive plan with metadata`() {
        val request = "Проанализируй код"
        val uncertainty = UncertaintyResult(
            score = 0.8,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план"),
            reasoning = "Сложный запрос"
        )

        val plan = adaptivePlanner.createAdaptivePlan(request, uncertainty, context)

        assertNotNull(plan.id)
        assertEquals(request, plan.originalRequest)
        assertTrue(plan.metadata.containsKey("adaptive_planner"))
        assertTrue(plan.metadata.containsKey("supports_conditional_steps"))
        assertTrue(plan.metadata.containsKey("supports_dynamic_modification"))
        assertEquals(true, plan.metadata["adaptive_planner"])
    }

    @Test
    fun `should not add conditional steps for simple queries`() {
        val request = "Что такое Kotlin?"
        val uncertainty = UncertaintyResult(
            score = 0.1,
            complexity = ComplexityLevel.SIMPLE,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Простой запрос"
        )

        val plan = adaptivePlanner.createAdaptivePlan(request, uncertainty, context)

        // Простые запросы не должны иметь дополнительных шагов
        assertEquals(1, plan.steps.size) // Только базовый шаг
        assertEquals("simple_query_1", plan.steps.first().id)
    }

    @Test
    fun `should add conditional steps for complex queries`() {
        val request = "Проанализируй и исправь баги в коде"
        val uncertainty = UncertaintyResult(
            score = 0.8,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план"),
            reasoning = "Сложный запрос на исправление багов"
        )

        val plan = adaptivePlanner.createAdaptivePlan(request, uncertainty, context)

        // Сложные запросы должны иметь больше шагов
        assertTrue(plan.steps.size > 1)
        assertEquals(TaskType.BUG_FIX, plan.analysis.taskType)
    }

    @Test
    fun `should create conditional step correctly`() {
        val condition: (ExecutionContext, Map<String, StepOutput>) -> Boolean = { _, results ->
            results.containsKey("test_result")
        }

        val thenStep = PlanStep(
            id = "then_step",
            title = "Then Step",
            description = "Execute if condition is true",
            agentType = AgentType.LLM_REVIEW,
            input = emptyMap(),
            estimatedDurationMs = 5000
        )

        val conditionalStep = adaptivePlanner.createConditionalStep(
            id = "test_conditional",
            description = "Test conditional step",
            condition = condition,
            thenStep = thenStep
        )

        assertEquals("test_conditional", conditionalStep.id)
        assertEquals("Test conditional step", conditionalStep.description)
        assertEquals(thenStep, conditionalStep.thenStep)
        assertNull(conditionalStep.elseStep)
    }

    @Test
    fun `should evaluate conditional step with true condition`() = runBlocking {
        val condition: (ExecutionContext, Map<String, StepOutput>) -> Boolean = { _, results ->
            results.containsKey("test_result")
        }

        val thenStep = PlanStep(
            id = "then_step",
            title = "Then Step",
            description = "Execute if condition is true",
            agentType = AgentType.LLM_REVIEW,
            input = emptyMap(),
            estimatedDurationMs = 5000
        )

        val conditionalStep = adaptivePlanner.createConditionalStep(
            id = "test_conditional",
            description = "Test conditional step",
            condition = condition,
            thenStep = thenStep
        )

        val executionContext = ExecutionContext(projectPath = "/test/project")
        val stepResults = mapOf(
            "test_result" to StepOutput.of("value" to "test")
        )

        val (selectedStep, result) = adaptivePlanner.evaluateConditionalStep(
            conditionalStep,
            executionContext,
            stepResults
        )

        assertEquals(thenStep, selectedStep)
        assertTrue(result.success)
        assertTrue(result.output.contains("conditional_selected"))
        assertEquals(true, result.output["conditional_selected"])
    }

    @Test
    fun `should evaluate conditional step with false condition`() = runBlocking {
        val condition: (ExecutionContext, Map<String, StepOutput>) -> Boolean = { _, results ->
            results.containsKey("non_existent_result")
        }

        val thenStep = PlanStep(
            id = "then_step",
            title = "Then Step",
            description = "Execute if condition is true",
            agentType = AgentType.LLM_REVIEW,
            input = emptyMap(),
            estimatedDurationMs = 5000
        )

        val elseStep = PlanStep(
            id = "else_step",
            title = "Else Step",
            description = "Execute if condition is false",
            agentType = AgentType.CODE_QUALITY,
            input = emptyMap(),
            estimatedDurationMs = 3000
        )

        val conditionalStep = adaptivePlanner.createConditionalStep(
            id = "test_conditional",
            description = "Test conditional step",
            condition = condition,
            thenStep = thenStep,
            elseStep = elseStep
        )

        val executionContext = ExecutionContext(projectPath = "/test/project")
        val stepResults = mapOf(
            "other_result" to StepOutput.of("value" to "test")
        )

        val (selectedStep, result) = adaptivePlanner.evaluateConditionalStep(
            conditionalStep,
            executionContext,
            stepResults
        )

        assertEquals(elseStep, selectedStep)
        assertTrue(result.success)
        assertEquals(false, result.output["conditional_selected"])
    }

    @Test
    fun `should modify plan based on step results`() {
        val plan = createTestPlan()
        val executionContext = ExecutionContext(projectPath = "/test/project")

        // Результаты, которые должны вызвать добавление шагов
        val stepResults = mapOf(
            "analysis" to StepOutput.of(
                "findings" to listOf("issue1", "issue2", "issue3", "issue4", "issue5", "issue6"),
                "complexity_score" to 0.8
            ),
            "quality_check" to StepOutput.of(
                "performance_issues" to listOf("slow_method"),
                "security_issues" to listOf("vulnerability")
            )
        )

        val modifiedPlan = adaptivePlanner.modifyPlanBasedOnResults(plan, stepResults, executionContext)

        // План должен быть изменен (версия увеличена)
        assertTrue(modifiedPlan.version > plan.version)
        assertTrue(modifiedPlan.metadata.containsKey("dynamically_modified"))
        assertEquals(true, modifiedPlan.metadata["dynamically_modified"])

        // Должны быть добавлены новые шаги
        assertTrue(modifiedPlan.steps.size > plan.steps.size)

        // Проверяем наличие специфических шагов
        assertTrue(modifiedPlan.steps.any { it.id.contains("detailed_analysis") })
        assertTrue(modifiedPlan.steps.any { it.id.contains("performance_optimization") })
        assertTrue(modifiedPlan.steps.any { it.id.contains("security_analysis") })
    }

    @Test
    fun `should not modify plan if no conditions met`() {
        val plan = createTestPlan()
        val executionContext = ExecutionContext(projectPath = "/test/project")

        // Результаты, которые НЕ должны вызывать добавление шагов
        val stepResults = mapOf(
            "analysis" to StepOutput.of(
                "findings" to listOf("issue1", "issue2"), // Мало находок
                "complexity_score" to 0.5 // Низкая сложность
            )
        )

        val modifiedPlan = adaptivePlanner.modifyPlanBasedOnResults(plan, stepResults, executionContext)

        // План не должен быть изменен
        assertEquals(plan.version, modifiedPlan.version)
        assertEquals(plan.steps.size, modifiedPlan.steps.size)
        assertFalse(modifiedPlan.metadata.containsKey("dynamically_modified"))
    }

    @Test
    fun `should add detailed analysis step for many findings`() {
        val plan = createTestPlan()
        val executionContext = ExecutionContext(projectPath = "/test/project")

        val stepResults = mapOf(
            "analysis" to StepOutput.of(
                "findings" to (1..10).map { "finding_$it" } // 10 находок
            )
        )

        val modifiedPlan = adaptivePlanner.modifyPlanBasedOnResults(plan, stepResults, executionContext)

        // Должен быть добавлен шаг детального анализа
        assertTrue(modifiedPlan.steps.any { step ->
            step.id.startsWith("detailed_analysis_analysis") &&
            step.title == "Детальный анализ результатов"
        })
    }

    @Test
    fun `should add performance optimization step for performance issues`() {
        val plan = createTestPlan()
        val executionContext = ExecutionContext(projectPath = "/test/project")

        val stepResults = mapOf(
            "analysis" to StepOutput.of(
                "performance_issues" to listOf("slow_method", "memory_leak")
            )
        )

        val modifiedPlan = adaptivePlanner.modifyPlanBasedOnResults(plan, stepResults, executionContext)

        // Должен быть добавлен шаг оптимизации производительности
        assertTrue(modifiedPlan.steps.any { step ->
            step.id.startsWith("performance_optimization_analysis") &&
            step.title == "Оптимизация производительности"
        })
    }

    @Test
    fun `should add security analysis step for security issues`() {
        val plan = createTestPlan()
        val executionContext = ExecutionContext(projectPath = "/test/project")

        val stepResults = mapOf(
            "analysis" to StepOutput.of(
                "security_issues" to listOf("sql_injection", "xss_vulnerability")
            )
        )

        val modifiedPlan = adaptivePlanner.modifyPlanBasedOnResults(plan, stepResults, executionContext)

        // Должен быть добавлен шаг анализа безопасности
        assertTrue(modifiedPlan.steps.any { step ->
            step.id.startsWith("security_analysis_analysis") &&
            step.title == "Анализ безопасности"
        })
    }

    @Test
    fun `should add additional testing step for low test coverage`() {
        val plan = createTestPlan()
        val executionContext = ExecutionContext(projectPath = "/test/project")

        val stepResults = mapOf(
            "quality_check" to StepOutput.of(
                "test_coverage" to 60.0 // Низкое покрытие
            )
        )

        val modifiedPlan = adaptivePlanner.modifyPlanBasedOnResults(plan, stepResults, executionContext)

        // Должен быть добавлен шаг дополнительного тестирования
        assertTrue(modifiedPlan.steps.any { step ->
            step.id.startsWith("additional_testing_quality_check") &&
            step.title == "Дополнительное тестирование"
        })
    }

    @Test
    fun `should handle conditional step without else branch`() = runBlocking {
        val condition: (ExecutionContext, Map<String, StepOutput>) -> Boolean = { _, results ->
            false // Условие не выполнено
        }

        val thenStep = PlanStep(
            id = "then_step",
            title = "Then Step",
            description = "Execute if condition is true",
            agentType = AgentType.LLM_REVIEW,
            input = emptyMap(),
            estimatedDurationMs = 5000
        )

        val conditionalStep = adaptivePlanner.createConditionalStep(
            id = "test_conditional",
            description = "Test conditional step",
            condition = condition,
            thenStep = thenStep
            // Нет else шага
        )

        val executionContext = ExecutionContext(projectPath = "/test/project")
        val stepResults = emptyMap<String, StepOutput>()

        val (selectedStep, result) = adaptivePlanner.evaluateConditionalStep(
            conditionalStep,
            executionContext,
            stepResults
        )

        // Должен вернуться then шаг с флагом пропуска
        assertEquals(thenStep, selectedStep)
        assertTrue(result.success)
        assertEquals(false, result.output["conditional_selected"])
        assertEquals(true, result.output["skipped"])
    }

    // --- Вспомогательные методы ---

    private fun createTestPlan(): ExecutionPlan {
        return ExecutionPlan(
            id = "test-plan-id",
            userRequestId = "test-request-id",
            originalRequest = "Проанализируй код",
            analysis = RequestAnalysis(
                taskType = TaskType.CODE_ANALYSIS,
                requiredTools = setOf(AgentType.LLM_REVIEW),
                context = ExecutionContext(projectPath = "/test/project"),
                parameters = emptyMap(),
                requiresUserInput = false,
                estimatedComplexity = ComplexityLevel.MEDIUM,
                estimatedSteps = 2,
                confidence = 0.8,
                reasoning = "Тестовый анализ"
            ),
            steps = listOf(
                PlanStep(
                    id = "analysis",
                    title = "Анализ кода",
                    description = "Базовый анализ кода",
                    agentType = AgentType.LLM_REVIEW,
                    input = mapOf("request" to "Проанализируй код"),
                    estimatedDurationMs = 8000
                ),
                PlanStep(
                    id = "quality_check",
                    title = "Проверка качества",
                    description = "Проверка качества кода",
                    agentType = AgentType.CODE_QUALITY,
                    input = mapOf("project_path" to "/test/project"),
                    dependencies = setOf("analysis"),
                    estimatedDurationMs = 6000
                )
            ),
            currentState = PlanState.CREATED,
            version = 1,
            metadata = emptyMap()
        )
    }
}