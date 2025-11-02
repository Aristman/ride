package ru.marslab.ide.ride.agent.planner

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.*
import ru.marslab.ide.ride.agent.analyzer.*
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator
import com.intellij.openapi.project.Project
import java.util.*

/**
 * Интеграционные тесты для RequestPlanner с EnhancedAgentOrchestrator
 */
class RequestPlannerOrchestratorIntegrationTest {

    private lateinit var planner: RequestPlanner
    private lateinit var mockProject: Project
    private lateinit var mockLLMProvider: LLMProvider
    private lateinit var mockOrchestrator: EnhancedAgentOrchestrator
    private lateinit var context: ChatContext

    @BeforeEach
    fun setUp() {
        planner = RequestPlanner()
        mockProject = mock(Project::class.java)
        mockLLMProvider = mock(LLMProvider::class.java)
        mockOrchestrator = mock(EnhancedAgentOrchestrator::class.java)
        context = ChatContext(project = mockProject, history = emptyList())

        // Настройка моков
        `when`(mockProject.basePath).thenReturn("/test/project")
        `when`(mockLLMProvider.isAvailable()).thenReturn(true)
        `when`(mockLLMProvider.getProviderName()).thenReturn("TestProvider")
    }

    @Test
    fun `simple plan should be executable by orchestrator`() {
        val request = "Что такое Kotlin?"
        val uncertainty = UncertaintyResult(
            score = 0.1,
            complexity = ComplexityLevel.SIMPLE,
            suggestedActions = listOf("прямой_ответ"),
            reasoning = "Простой вопрос"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        // Проверяем что план готов к выполнению
        assertEquals(PlanState.CREATED, plan.currentState)
        assertTrue(plan.steps.isNotEmpty())

        // Проверяем что есть готовые к выполнению шаги
        val readySteps = plan.getReadySteps()
        assertTrue(readySteps.isNotEmpty())

        // Простой план должен иметь шаг без зависимостей
        val firstStep = readySteps.first()
        assertEquals(StepStatus.PENDING, firstStep.status)
        assertTrue(firstStep.dependencies.isEmpty())
    }

    @Test
    fun `complex plan should have proper step dependencies`() {
        val request = "Найди и исправь баги в этом коде"
        val uncertainty = UncertaintyResult(
            score = 0.7,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план"),
            reasoning = "Сложный запрос на исправление багов"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        assertEquals(3, plan.steps.size)

        // Проверяем что только первый шаг готов к выполнению
        val readySteps = plan.getReadySteps()
        assertEquals(1, readySteps.size)

        val firstStep = readySteps.first()
        assertEquals(AgentType.BUG_DETECTION, firstStep.agentType)
        assertTrue(firstStep.dependencies.isEmpty())

        // Проверяем что остальные шаги имеют зависимости
        val otherSteps = plan.steps.filter { it.id != firstStep.id }
        assertTrue(otherSteps.all { it.dependencies.isNotEmpty() })

        // Проверяем что зависимости указывают на существующие шаги
        otherSteps.forEach { step ->
            step.dependencies.forEach { dep ->
                assertTrue(plan.steps.any { it.id == dep }, "Dependency $dep should exist")
            }
        }
    }

    @Test
    fun `plan should include all required tools in analysis`() {
        val request = "Проанализируй архитектуру этого проекта"
        val uncertainty = UncertaintyResult(
            score = 0.8,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "поиск_контекста"),
            reasoning = "Сложный архитектурный анализ",
            detectedFeatures = listOf("архитектурный", "связан_с_кодом")
        )

        val plan = planner.createPlan(request, uncertainty, context)

        // Проверяем что анализ содержит правильные инструменты
        val requiredTools = plan.analysis.requiredTools
        assertTrue(requiredTools.contains(AgentType.PROJECT_SCANNER))
        assertTrue(requiredTools.contains(AgentType.ARCHITECTURE_ANALYSIS))
        assertTrue(requiredTools.contains(AgentType.CODE_QUALITY))

        // Проверяем что шаги используют правильные агенты
        assertTrue(plan.steps.any { it.agentType == AgentType.PROJECT_SCANNER })
        assertTrue(plan.steps.any { it.agentType == AgentType.ARCHITECTURE_ANALYSIS })
        assertTrue(plan.steps.any { it.agentType == AgentType.CODE_QUALITY })
    }

    @Test
    fun `plan should handle RAG enrichment step correctly`() {
        val request = "Проанализируй этот сложный проект"
        val uncertainty = UncertaintyResult(
            score = 0.7,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "поиск_контекста"),
            reasoning = "Сложный запрос с поиском контекста"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        // Должен включать шаг RAG обогащения
        val ragStep = plan.steps.find { it.agentType == AgentType.EMBEDDING_INDEXER }
        assertNotNull(ragStep, "Should include RAG enrichment step")

        if (ragStep != null) {
            assertEquals("Поиск релевантного контекста", ragStep.title)
            assertTrue(ragStep.input.containsKey("query"))
            assertTrue(ragStep.input.containsKey("max_chunks"))
        }
    }

    @Test
    fun `plan should include correct execution context`() {
        val request = "Проанализируй код в этом проекте"
        val uncertainty = UncertaintyResult(
            score = 0.5,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("контекстный_ответ"),
            reasoning = "Запрос средней сложности"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        val execContext = plan.analysis.context
        assertEquals("/test/project", execContext.projectPath)
        assertTrue(execContext.additionalContext.containsKey("conversation_history_size"))
        assertTrue(execContext.additionalContext.containsKey("has_code_context"))
        assertTrue(execContext.additionalContext.containsKey("uncertainty_score"))
    }

    @Test
    fun `plan should provide detailed step information`() {
        val request = "Оптимизируй производительность этого кода"
        val uncertainty = UncertaintyResult(
            score = 0.6,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план"),
            reasoning = "Запрос на оптимизацию"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        // Проверяем что каждый шаг имеет полную информацию
        plan.steps.forEach { step ->
            assertNotNull(step.id)
            assertNotNull(step.title)
            assertNotNull(step.description)
            assertNotNull(step.agentType)
            assertTrue(step.input.isNotEmpty())
            assertTrue(step.estimatedDurationMs > 0)
            assertEquals(StepStatus.PENDING, step.status)

            // Проверяем что вводные данные корректны
            step.input.forEach { (key, value) ->
                assertNotNull(key)
                assertNotNull(value)
            }
        }
    }

    @Test
    fun `plan should calculate correct complexity and steps`() {
        val testCases = listOf(
            Triple("Какой сегодня день?", ComplexityLevel.SIMPLE, 1),
            Triple("Объясни как работает этот метод", ComplexityLevel.MEDIUM, 2),
            Triple("Проанализируй архитектуру", ComplexityLevel.COMPLEX, 4),
            Triple("Найди и исправь баги", ComplexityLevel.COMPLEX, 3)
        )

        testCases.forEach { (request, expectedComplexity, expectedMinSteps) ->
            val uncertainty = UncertaintyResult(
                score = when (expectedComplexity) {
                    ComplexityLevel.SIMPLE -> 0.1
                    ComplexityLevel.MEDIUM -> 0.5
                    ComplexityLevel.COMPLEX -> 0.8
                },
                complexity = expectedComplexity,
                suggestedActions = listOf("прямой_ответ"),
                reasoning = "Тестовый запрос"
            )

            val plan = planner.createPlan(request, uncertainty, context)

            assertTrue(plan.steps.size >= expectedMinSteps,
                "Request '$request' should have at least $expectedMinSteps steps, got ${plan.steps.size}")

            val expectedAnalysisComplexity = when (expectedComplexity) {
                ComplexityLevel.SIMPLE -> ComplexityLevel.LOW
                ComplexityLevel.MEDIUM -> ComplexityLevel.MEDIUM
                ComplexityLevel.COMPLEX -> ComplexityLevel.HIGH
            }

            assertEquals(expectedAnalysisComplexity, plan.analysis.estimatedComplexity)
        }
    }

    @Test
    fun `plan should be valid for orchestrator execution`() {
        val request = "Сделай комплексный анализ проекта"
        val uncertainty = UncertaintyResult(
            score = 0.9,
            complexity = ComplexityLevel.COMPLEX,
            suggestedActions = listOf("создать_план", "использовать_оркестратор", "поиск_контекста"),
            reasoning = "Максимально сложный запрос"
        )

        val plan = planner.createPlan(request, uncertainty, context)

        // Проверяем что план корректен для выполнения оркестратором
        assertNotNull(plan.id)
        assertNotNull(plan.userRequestId)
        assertEquals(request, plan.originalRequest)
        assertEquals(PlanState.CREATED, plan.currentState)
        assertTrue(plan.steps.isNotEmpty())

        // Проверяем что анализ корректен
        assertNotNull(plan.analysis.taskType)
        assertTrue(plan.analysis.requiredTools.isNotEmpty())
        assertNotNull(plan.analysis.context)
        assertTrue(plan.analysis.estimatedSteps > 0)
        assertTrue(plan.analysis.confidence >= 0.0 && plan.analysis.confidence <= 1.0)

        // Проверяем что шаги готовы к выполнению
        val readySteps = plan.getReadySteps()
        assertTrue(readySteps.isNotEmpty(), "Should have at least one ready step")

        // Проверяем прогресс
        assertEquals(0.0, plan.getProgress(), 0.01, "Initial progress should be 0%")
        assertFalse(plan.isAllStepsCompleted(), "Plan should not be completed initially")
        assertFalse(plan.hasErrors(), "Plan should not have errors initially")
    }

    @Test
    fun `plan should handle user input requirements correctly`() {
        val testCases = listOf(
            Triple("Простой вопрос", false, "Simple queries shouldn't require input"),
            Triple("Проанализируй этот код", false, "Code analysis shouldn't require input"),
            Triple("Архитектура проекта", true, "Architecture analysis should require input"),
            Triple("Сделай миграцию базы данных", true, "Migration should require input")
        )

        testCases.forEach { (request, expectedRequiresInput, description) ->
            val uncertainty = UncertaintyResult(
                score = if (expectedRequiresInput) 0.7 else 0.4,
                complexity = if (expectedRequiresInput) ComplexityLevel.COMPLEX else ComplexityLevel.MEDIUM,
                suggestedActions = listOf("прямой_ответ"),
                reasoning = "Тестовый запрос"
            )

            val plan = planner.createPlan(request, uncertainty, context)

            assertEquals(expectedRequiresInput, plan.analysis.requiresUserInput,
                description)
        }
    }

    @Test
    fun `plan metadata should be comprehensive`() {
        val request = "Комплексный тестовый запрос"
        val uncertainty = UncertaintyResult(
            score = 0.6,
            complexity = ComplexityLevel.MEDIUM,
            suggestedActions = listOf("контекстный_ответ"),
            reasoning = "Тестовый запрос для проверки метаданных",
            detectedFeatures = listOf("тест", "метаданные")
        )

        val plan = planner.createPlan(request, uncertainty, context, "test-plan-id")

        // Проверяем метаданные плана
        assertTrue(plan.metadata.isNotEmpty())
        assertEquals("1.0", plan.metadata["planner_version"])
        assertEquals(0.6, plan.metadata["uncertainty_score"])
        assertEquals("MEDIUM", plan.metadata["complexity_level"])
        assertEquals("test-plan-id", plan.userRequestId)

        // Проверяем метаданные анализа
        assertTrue(plan.analysis.parameters.isNotEmpty())
        assertEquals(request, plan.analysis.parameters["original_request"])
        assertTrue(plan.analysis.parameters.containsKey("uncertainty_analysis"))
        assertTrue(plan.analysis.parameters.containsKey("complexity_factors"))
    }
}