package ru.marslab.ide.ride.agent.planner

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ru.marslab.ide.ride.agent.analyzer.*
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.*
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock
import java.util.*

/**
 * Тесты для RequestPlanner
 */
class RequestPlannerTest {

    private val planner = RequestPlanner()
    private val mockProject = mock(Project::class.java)
    private val context = ChatContext(project = mockProject, history = emptyList())

    @Test
    fun `simple query should create single step plan`() {
        val request = "Какой сегодня день?"
        val uncertainty = UncertaintyResult(
            score = 0.0,
            complexity = ComplexityLevel.SIMPLE,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Простой вопрос о времени"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        // Проверяем базовые свойства плана
        assertNotNull(plan.id)
        assertEquals(request, plan.originalRequest)
        assertEquals(PlanState.CREATED, plan.currentState)
        assertEquals(1, plan.steps.size)

        // Проверяем единственный шаг
        val step = plan.steps.first()
        assertEquals("simple_query_1", step.id)
        assertEquals("Обработка простого запроса", step.title)
        assertEquals(AgentType.USER_INTERACTION, step.agentType)
        assertEquals(StepStatus.PENDING, step.status)
    }

    @Test
    fun `code analysis request should create multi-step plan`() {
        val request = "Проанализируй качество этого кода"
        val uncertainty = UncertaintyResult(
            score = 0.5,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("контекстный_ответ", "поиск_контекста"),
            reasoning = "Запрос средней сложности требует анализа кода"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        assertEquals(TaskType.CODE_ANALYSIS, plan.analysis.taskType)
        assertEquals(ComplexityLevel.MEDIUM, plan.analysis.estimatedComplexity)
        assertEquals(2, plan.steps.size) // analysis + quality_check

        // Проверяем шаги
        val analysisStep = plan.steps.find { it.id.startsWith("analysis") }
        val qualityStep = plan.steps.find { it.id.startsWith("quality_check") }

        assertNotNull(analysisStep)
        assertNotNull(qualityStep)

        // Проверяем зависимости
        assertTrue(qualityStep.dependencies.contains(analysisStep.id))
        assertEquals(StepStatus.PENDING, analysisStep.status)
        assertEquals(StepStatus.PENDING, qualityStep.status)
    }

    @Test
    fun `bug fix request should create comprehensive plan`() {
        val request = "Найди и исправь баги в этом коде"
        val uncertainty = UncertaintyResult(
            score = 0.6,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "использовать_оркестратор"),
            reasoning = "Сложный запрос на исправление багов"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        assertEquals(TaskType.BUG_FIX, plan.analysis.taskType)
        assertEquals(ComplexityLevel.HIGH, plan.analysis.estimatedComplexity)
        assertEquals(3, plan.steps.size) // bug_detection + quality_check + bug_fix

        // Проверяем типы агентов
        val bugDetectionStep = plan.steps.find { it.agentType == AgentType.BUG_DETECTION }
        val qualityStep = plan.steps.find { it.agentType == AgentType.CODE_QUALITY }
        val bugFixStep = plan.steps.find { it.agentType == AgentType.CODE_FIXER }

        assertNotNull(bugDetectionStep)
        assertNotNull(qualityStep)
        assertNotNull(bugFixStep)

        // Проверяем цепочку зависимостей
        assertTrue(qualityStep.dependencies.contains(bugDetectionStep.id))
        assertTrue(bugFixStep.dependencies.containsAll(listOf(bugDetectionStep.id, qualityStep.id)))
    }

    @Test
    fun `architecture analysis should create comprehensive plan`() {
        val request = "Проанализируй архитектуру этого проекта"
        val uncertainty = UncertaintyResult(
            score = 0.8,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "использовать_оркестратор", "поиск_контекста"),
            reasoning = "Сложный архитектурный анализ"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        assertEquals(TaskType.ARCHITECTURE_ANALYSIS, plan.analysis.taskType)
        assertEquals(4, plan.steps.size) // project_scan + architecture + quality_check + documentation

        // Проверяем шаги
        assertTrue(plan.steps.any { it.agentType == AgentType.PROJECT_SCANNER })
        assertTrue(plan.steps.any { it.agentType == AgentType.ARCHITECTURE_ANALYSIS })
        assertTrue(plan.steps.any { it.agentType == AgentType.CODE_QUALITY })
        assertTrue(plan.steps.any { it.agentType == AgentType.DOCUMENTATION_GENERATOR })

        // Проверяем что требует ввода пользователя
        assertTrue(plan.analysis.requiresUserInput)
    }

    @Test
    fun `refactoring request should create appropriate plan`() {
        val request = "Сделай рефакторинг этого класса"
        val uncertainty = UncertaintyResult(
            score = 0.7,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "использовать_оркестратор"),
            reasoning = "Сложный запрос на рефакторинг"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        assertEquals(TaskType.REFACTORING, plan.analysis.taskType)
        assertEquals(3, plan.steps.size) // quality_check + refactor + validation

        val refactorStep = plan.steps.find { it.id.startsWith("refactor") }
        assertNotNull(refactorStep)
        assertEquals(AgentType.LLM_REVIEW, refactorStep.agentType)

        // Проверяем что содержит фокусные области
        val focusAreas = refactorStep.input["focus_areas"] as? List<*>
        assertNotNull(focusAreas)
        assertTrue(focusAreas!!.contains("readability"))
        assertTrue(focusAreas.contains("performance"))
        assertTrue(focusAreas.contains("maintainability"))
    }

    @Test
    fun `complex multi-step request should include RAG enrichment when suggested`() {
        val request = "Проанализируй этот сложный проект и предложи улучшения"
        val uncertainty = UncertaintyResult(
            score = 0.7,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "использовать_оркестратор", "поиск_контекста"),
            reasoning = "Сложный многошаговый запрос",
            detectedFeatures = listOf("связан_с_кодом", "подробный")
        )

        val plan = planner.createPlan(request, uncertainty, context)

        assertEquals(TaskType.COMPLEX_MULTI_STEP, plan.analysis.taskType)
        assertTrue(plan.steps.size >= 4)

        // Проверяем наличие RAG обогащения
        val ragStep = plan.steps.find { it.agentType == AgentType.EMBEDDING_INDEXER }
        assertNotNull(ragStep, "Should include RAG enrichment step when context search is suggested")
        assertEquals("Поиск релевантного контекста", ragStep.title)
    }

    @Test
    fun `plan should include correct metadata`() {
        val request = "Тестовый запрос"
        val uncertainty = UncertaintyResult(
            score = 0.4,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("контекстный_ответ"),
            reasoning = "Тестовый запрос средней сложности",
            detectedFeatures = listOf("тестовый_признак")
        )

        val plan = planner.createPlan(request, uncertainty, context, "test-request-id")

        // Проверяем метаданные плана
        assertEquals("test-request-id", plan.userRequestId)
        assertEquals("1.0", plan.metadata["planner_version"])
        assertEquals(0.4, plan.metadata["uncertainty_score"])
        assertEquals("MEDIUM", plan.metadata["complexity_level"])
        assertEquals("контекстный_ответ", (plan.metadata["suggested_actions"] as List<*>).first())
        assertEquals("тестовый_признак", (plan.metadata["detected_features"] as List<*>).first())
        assertEquals("Тестовый запрос средней сложности", plan.metadata["reasoning"])

        // Проверяем метаданные анализа
        assertTrue(plan.analysis.confidence > 0.5) // 1.0 - uncertainty.score
        assertEquals("Тестовый запрос средней сложности", plan.analysis.reasoning)
    }

    @Test
    fun `plan steps should have valid structure`() {
        val request = "Проверь качество кода"
        val uncertainty = UncertaintyResult(
            score = 0.5,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("контекстный_ответ"),
            reasoning = "Запрос на проверку качества"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        // Проверяем структуру каждого шага
        plan.steps.forEach { step ->
            assertNotNull(step.id)
            assertNotNull(step.title)
            assertNotNull(step.description)
            assertNotNull(step.agentType)
            assertTrue(step.estimatedDurationMs > 0)
            assertEquals(StepStatus.PENDING, step.status)
        }

        // Проверяем уникальность ID шагов
        val stepIds = plan.steps.map { it.id }
        assertEquals(stepIds.size, stepIds.distinct().size, "Step IDs should be unique")
    }

    @Test
    fun `plan should handle different complexity levels correctly`() {
        val simpleRequest = "Что такое Kotlin?"
        val simpleUncertainty = UncertaintyResult(
            score = 0.1,
            complexity = ComplexityLevel.SIMPLE,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Простой вопрос"
        )

        val complexRequest = "Проанализируй архитектуру микросервисного приложения и предложи улучшения"
        val complexUncertainty = UncertaintyResult(
            score = 0.9,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "использовать_оркестратор"),
            reasoning = "Очень сложный архитектурный запрос"
        )

        val simplePlan = planner.createPlan(simpleRequest, simpleUncertainty, context)
        val complexPlan = planner.createPlan(complexRequest, complexUncertainty, context)

        // Простой план должен иметь меньше шагов
        assertTrue(simplePlan.steps.size < complexPlan.steps.size)
        assertEquals(ComplexityLevel.LOW, simplePlan.analysis.estimatedComplexity)
        assertEquals(ComplexityLevel.HIGH, complexPlan.analysis.estimatedComplexity)

        // Сложный план должен иметь больше инструментов
        assertTrue(complexPlan.analysis.requiredTools.size > simplePlan.analysis.requiredTools.size)
    }

    @Test
    fun `plan steps should have logical dependencies`() {
        val request = "Найди баги и исправь их"
        val uncertainty = UncertaintyResult(
            score = 0.6,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план"),
            reasoning = "Запрос на исправление багов"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        // Проверяем что есть шаги с зависимостями
        val stepsWithDependencies = plan.steps.filter { it.dependencies.isNotEmpty() }
        assertTrue(stepsWithDependencies.isNotEmpty(), "Should have steps with dependencies")

        // Проверяем что зависимости корректны
        stepsWithDependencies.forEach { step ->
            step.dependencies.forEach { dep ->
                assertTrue(plan.steps.any { it.id == dep }, "Dependency $dep should exist in plan")
            }
        }

        // Проверяем что можно получить готовые шаги
        val readySteps = plan.getReadySteps()
        assertTrue(readySteps.isNotEmpty(), "Should have at least one ready step")
        assertTrue(readySteps.all { it.status == StepStatus.PENDING })
        assertTrue(readySteps.all { it.dependencies.isEmpty() })
    }
}