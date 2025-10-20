package ru.marslab.ide.ride.orchestrator

import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.llm.TokenUsage
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.orchestrator.impl.LLMRequestAnalyzer
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer

class LLMRequestAnalyzerTest {

    private lateinit var llmProvider: LLMProvider
    private lateinit var uncertaintyAnalyzer: UncertaintyAnalyzer
    private lateinit var requestAnalyzer: LLMRequestAnalyzer

    @BeforeTest
    fun setUp() {
        llmProvider = mockk()
        uncertaintyAnalyzer = mockk()
        requestAnalyzer = LLMRequestAnalyzer(llmProvider, uncertaintyAnalyzer)

        // Настройка моков по умолчанию
        every { uncertaintyAnalyzer.analyzeUncertainty(any(), any()) } returns 0.1
    }

    @Test
    fun `should analyze code analysis request correctly`() = runTest {
        // Given
        val request = UserRequest(
            originalRequest = "Проанализируй код на наличие багов",
            context = ExecutionContext(projectPath = "/test/project")
        )

        val mockResponse = LLMResponse(
            success = true,
            content = createMockAnalysisJson(
                taskType = "CODE_ANALYSIS",
                requiredTools = listOf("PROJECT_SCANNER", "BUG_DETECTION"),
                complexity = "MEDIUM",
                estimatedSteps = 3,
                requiresUserInput = false,
                confidence = 0.9
            ),
            metadata = mapOf("tokenUsage" to TokenUsage(100, 50, 150))
        )

        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns mockResponse
        every { uncertaintyAnalyzer.analyzeUncertainty(any(), any()) } returns 0.1

        // When
        val result = requestAnalyzer.analyze(request)

        // Then
        assertNotNull(result)
        assertEquals(TaskType.CODE_ANALYSIS, result.taskType)
        assertEquals(setOf(AgentType.PROJECT_SCANNER, AgentType.BUG_DETECTION), result.requiredTools)
        assertEquals(ComplexityLevel.MEDIUM, result.estimatedComplexity)
        assertEquals(3, result.estimatedSteps)
        assertFalse(result.requiresUserInput)
        assertEquals(0.81, result.confidence, 0.01) // 0.9 * (1.0 - 0.1)
    }

    @Test
    fun `should analyze refactoring request correctly`() = runTest {
        // Given
        val request = UserRequest(
            originalRequest = "Выполни рефакторинг этого класса",
            context = ExecutionContext(
                projectPath = "/test/project",
                selectedFiles = listOf("/test/project/MyClass.kt")
            )
        )

        val mockResponse = LLMResponse(
            success = true,
            content = createMockAnalysisJson(
                taskType = "REFACTORING",
                requiredTools = listOf("CODE_QUALITY", "ARCHITECTURE_ANALYSIS"),
                complexity = "HIGH",
                estimatedSteps = 5,
                requiresUserInput = true,
                confidence = 0.8
            )
        )

        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns mockResponse

        // When
        val result = requestAnalyzer.analyze(request)

        // Then
        assertEquals(TaskType.REFACTORING, result.taskType)
        assertEquals(setOf(AgentType.CODE_QUALITY, AgentType.ARCHITECTURE_ANALYSIS), result.requiredTools)
        assertEquals(ComplexityLevel.HIGH, result.estimatedComplexity)
        assertEquals(5, result.estimatedSteps)
        assertTrue(result.requiresUserInput)
    }

    @Test
    fun `should handle LLM error and return fallback analysis`() = runTest {
        // Given
        val request = UserRequest(
            originalRequest = "Проанализируй код на баги",
            context = ExecutionContext()
        )

        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse(
            success = false,
            content = "",
            error = "LLM API Error"
        )

        // When
        val result = requestAnalyzer.analyze(request)

        // Then
        assertNotNull(result)
        assertEquals(TaskType.CODE_ANALYSIS, result.taskType) // Fallback определит по ключевому слову "баг"
        assertEquals(setOf(AgentType.PROJECT_SCANNER, AgentType.BUG_DETECTION), result.requiredTools)
        assertEquals(ComplexityLevel.MEDIUM, result.estimatedComplexity)
        assertTrue(result.requiresUserInput) // При fallback всегда требуем уточнение
        assertEquals(0.3, result.confidence, 0.01) // Низкая уверенность при fallback
    }

    @Test
    fun `should handle invalid JSON response gracefully`() = runTest {
        // Given
        val request = UserRequest(
            originalRequest = "Проанализируй код",
            context = ExecutionContext()
        )

        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse(
            success = true,
            content = "Invalid JSON response without proper structure"
        )

        // When
        val result = requestAnalyzer.analyze(request)

        // Then
        assertNotNull(result)
        assertEquals(TaskType.CODE_ANALYSIS, result.taskType) // Fallback на базовый тип
        assertEquals(0.3, result.confidence, 0.01)
        assertTrue(result.reasoning.contains("Fallback"))
    }

    @Test
    fun `should handle high uncertainty correctly`() = runTest {
        // Given
        val request = UserRequest(
            originalRequest = "Сделай что-то",
            context = ExecutionContext()
        )

        val mockResponse = LLMResponse(
            success = true,
            content = createMockAnalysisJson(
                taskType = "SIMPLE_QUERY",
                requiredTools = emptyList(),
                complexity = "LOW",
                estimatedSteps = 1,
                requiresUserInput = false,
                confidence = 0.9
            )
        )

        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns mockResponse
        every { uncertaintyAnalyzer.analyzeUncertainty(any(), any()) } returns 0.5 // Высокая неопределенность

        // When
        val result = requestAnalyzer.analyze(request)

        // Then
        assertEquals(TaskType.SIMPLE_QUERY, result.taskType)
        assertTrue(result.requiresUserInput) // Должно потребовать ввод при высокой неопределенности
        assertEquals(0.45, result.confidence, 0.01) // 0.9 * (1.0 - 0.5)
    }

    @Test
    fun `should check if request can be handled`() {
        // Given
        val validRequest = UserRequest(
            originalRequest = "Valid request",
            context = ExecutionContext()
        )

        val emptyRequest = UserRequest(
            originalRequest = "",
            context = ExecutionContext()
        )

        // When & Then
        assertTrue(requestAnalyzer.canHandle(validRequest))
        assertFalse(requestAnalyzer.canHandle(emptyRequest))
    }

    @Test
    fun `should return correct name and version`() {
        // When & Then
        assertEquals("LLMRequestAnalyzer", requestAnalyzer.name)
        assertEquals("1.0.0", requestAnalyzer.version)
    }

    @Test
    fun `should parse complex tool list correctly`() = runTest {
        // Given
        val request = UserRequest(
            originalRequest = "Выполни полный анализ проекта",
            context = ExecutionContext()
        )

        val mockResponse = LLMResponse(
            success = true,
            content = createMockAnalysisJson(
                taskType = "COMPLEX_MULTI_STEP",
                requiredTools = listOf(
                    "PROJECT_SCANNER",
                    "BUG_DETECTION",
                    "CODE_QUALITY",
                    "ARCHITECTURE_ANALYSIS",
                    "REPORT_GENERATOR"
                ),
                complexity = "VERY_HIGH",
                estimatedSteps = 8,
                requiresUserInput = false,
                confidence = 0.95
            )
        )

        coEvery { llmProvider.sendRequest(any(), any(), any(), any()) } returns mockResponse

        // When
        val result = requestAnalyzer.analyze(request)

        // Then
        assertEquals(5, result.requiredTools.size)
        assertTrue(result.requiredTools.contains(AgentType.PROJECT_SCANNER))
        assertTrue(result.requiredTools.contains(AgentType.BUG_DETECTION))
        assertTrue(result.requiredTools.contains(AgentType.CODE_QUALITY))
        assertTrue(result.requiredTools.contains(AgentType.ARCHITECTURE_ANALYSIS))
        assertTrue(result.requiredTools.contains(AgentType.REPORT_GENERATOR))
        assertEquals(ComplexityLevel.VERY_HIGH, result.estimatedComplexity)
        assertEquals(8, result.estimatedSteps)
    }

    private fun createMockAnalysisJson(
        taskType: String,
        requiredTools: List<String>,
        complexity: String,
        estimatedSteps: Int,
        requiresUserInput: Boolean,
        confidence: Double
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
          "confidence": $confidence,
          "reasoning": "Analysis based on request content and context",
          "parameters": {
            "focus_area": "general analysis",
            "priority": "medium"
          }
        }
        """.trimIndent()
    }
}