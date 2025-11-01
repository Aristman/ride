package ru.marslab.ide.ride.agent.planner

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.DynamicPlanModifier
import ru.marslab.ide.ride.agent.analyzer.UncertaintyResult
import ru.marslab.ide.ride.agent.ToolAgentRegistry
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.agent.ConditionalStep
import ru.marslab.ide.ride.agent.ConditionalStepExecutor

/**
 * Адаптивный планировщик запросов с поддержкой динамической модификации
 *
 * Расширяет RequestPlanner возможностью:
 * - Создавать условные шаги на основе анализа результатов
 * - Динамически добавлять шаги в процессе выполнения
 * - Адаптировать планы на основе промежуточных результатов
 */
class AdaptiveRequestPlanner {

    private val logger = Logger.getInstance(AdaptiveRequestPlanner::class.java)
    private val basePlanner = RequestPlanner()
    private val dynamicModifier = DynamicPlanModifier()
    private val conditionalExecutor = ConditionalStepExecutor(
        // Здесь будет реальный ToolAgentRegistry в будущем
        mockToolAgentRegistry()
    )

    /**
     * Создает адаптивный план с поддержкой условных шагов
     */
    fun createAdaptivePlan(
        request: String,
        uncertainty: UncertaintyResult,
        context: ChatContext,
        userRequestId: String = java.util.UUID.randomUUID().toString()
    ): ExecutionPlan {
        logger.info("Creating adaptive plan for request with complexity: ${uncertainty.complexity}")

        // Создаем базовый план
        val basePlan = basePlanner.createPlan(request, uncertainty, context, userRequestId)

        // Добавляем условные шаги если это сложный запрос
        val enrichedPlan = if (uncertainty.complexity == ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.COMPLEX) {
            addConditionalSteps(basePlan, uncertainty, context)
        } else {
            basePlan
        }

        // Добавляем метаданные об адаптивности
        return enrichedPlan.copy(
            metadata = enrichedPlan.metadata + mapOf(
                "adaptive_planner" to true,
                "supports_conditional_steps" to true,
                "supports_dynamic_modification" to true
            )
        )
    }

    /**
     * Динамически модифицирует план на основе результатов выполнения
     */
    fun modifyPlanBasedOnResults(
        plan: ExecutionPlan,
        stepResults: Map<String, ru.marslab.ide.ride.model.tool.StepOutput>,
        context: ExecutionContext
    ): ExecutionPlan {
        logger.info("Modifying plan ${plan.id} based on ${stepResults.size} step results")

        var modifiedPlan = plan

        // Проверяем условия для добавления шагов
        stepResults.forEach { (stepId, result) ->
            when {
                shouldAddDetailedAnalysis(stepId, result) -> {
                    logger.info("Adding detailed analysis step based on result of $stepId")
                    val analysisStep = createDetailedAnalysisStep(stepId, context)
                    modifiedPlan = dynamicModifier.addStepAfter(modifiedPlan, analysisStep, stepId)
                }

                shouldAddPerformanceOptimization(stepId, result) -> {
                    logger.info("Adding performance optimization step based on result of $stepId")
                    val perfStep = createPerformanceOptimizationStep(stepId, context)
                    modifiedPlan = dynamicModifier.addStepAfter(modifiedPlan, perfStep, stepId)
                }

                shouldAddSecurityAnalysis(stepId, result) -> {
                    logger.info("Adding security analysis step based on result of $stepId")
                    val securityStep = createSecurityAnalysisStep(stepId, context)
                    modifiedPlan = dynamicModifier.addStepAfter(modifiedPlan, securityStep, stepId)
                }

                shouldAddAdditionalTesting(stepId, result) -> {
                    logger.info("Adding additional testing step based on result of $stepId")
                    val testStep = createAdditionalTestingStep(stepId, context)
                    modifiedPlan = dynamicModifier.addStepAfter(modifiedPlan, testStep, stepId)
                }
            }
        }

        // Обновляем метаданные
        return if (modifiedPlan.version > plan.version) {
            modifiedPlan.copy(
                metadata = modifiedPlan.metadata + mapOf(
                    "dynamically_modified" to true,
                    "modification_reason" to "step_results_analysis",
                    "original_version" to plan.version
                )
            )
        } else {
            modifiedPlan
        }
    }

    /**
     * Создает условный шаг для проверки результатов
     */
    fun createConditionalStep(
        id: String,
        description: String,
        condition: (ExecutionContext, Map<String, ru.marslab.ide.ride.model.tool.StepOutput>) -> Boolean,
        thenStep: PlanStep,
        elseStep: PlanStep? = null,
        dependencies: Set<String> = emptySet()
    ): ConditionalStep {
        return ConditionalStep(
            id = id,
            description = description,
            condition = condition,
            thenStep = thenStep,
            elseStep = elseStep,
            dependencies = dependencies
        )
    }

    /**
     * Оценивает условный шаг и возвращает выбранный шаг для выполнения
     */
    suspend fun evaluateConditionalStep(
        conditional: ConditionalStep,
        context: ExecutionContext,
        stepResults: Map<String, ru.marslab.ide.ride.model.tool.StepOutput>
    ): Pair<PlanStep, ru.marslab.ide.ride.model.tool.StepResult> {
        return conditionalExecutor.executeConditionalStep(conditional, context, stepResults)
    }

    /**
     * Добавляет условные шаги в план
     */
    private fun addConditionalSteps(
        plan: ExecutionPlan,
        uncertainty: UncertaintyResult,
        context: ChatContext
    ): ExecutionPlan {
        var enrichedPlan = plan

        // Условие: если найдены критические проблемы → добавить детальный анализ
        if (plan.analysis.taskType == TaskType.BUG_FIX || plan.analysis.taskType == TaskType.CODE_ANALYSIS) {
            val criticalIssuesStep = createCriticalIssuesConditionalStep(plan, context)
            enrichedPlan = dynamicModifier.addStepBefore(enrichedPlan, criticalIssuesStep as PlanStep, "analysis")
        }

        // Условие: если большой проект → добавить сегментацию
        val projectPath = context.project?.basePath
        if (projectPath != null && isLargeProject(projectPath)) {
            val segmentationStep = createProjectSegmentationStep(plan, context)
            enrichedPlan = dynamicModifier.addStepAfter(enrichedPlan, segmentationStep, "project_scan")
        }

        // Условие: если много тестов → добавить анализ покрытия
        if (shouldAnalyzeTestCoverage(plan, context)) {
            val testCoverageStep = createTestCoverageConditionalStep(plan, context)
            enrichedPlan = dynamicModifier.addStepAfter(enrichedPlan, testCoverageStep as PlanStep, "project_scan")
        }

        return enrichedPlan
    }

    // --- Условия для динамического добавления шагов ---

    private fun shouldAddDetailedAnalysis(
        stepId: String,
        result: ru.marslab.ide.ride.model.tool.StepOutput
    ): Boolean {
        return when {
            result.data.containsKey("findings") -> {
                val findings = result.data["findings"] as? List<*> ?: emptyList<Any>()
                findings.size > 5 // Много находок → нужен детальный анализ
            }

            result.data.containsKey("issues") -> {
                val issues = result.data["issues"] as? List<*> ?: emptyList<Any>()
                issues.any { issue ->
                    issue.toString().lowercase().contains("critical") ||
                    issue.toString().lowercase().contains("high")
                }
            }

            result.data.containsKey("complexity_score") -> {
                val score = result.get<Double>("complexity_score") ?: 0.0
                score > 0.7 // Высокая сложность → нужен детальный анализ
            }

            else -> false
        }
    }

    private fun shouldAddPerformanceOptimization(
        stepId: String,
        result: ru.marslab.ide.ride.model.tool.StepOutput
    ): Boolean {
        return when {
            result.data.containsKey("performance_issues") -> true
            result.data.containsKey("bottlenecks") -> true
            result.data.containsKey("slow_operations") -> true
            result.data.containsKey("memory_issues") -> true
            else -> false
        }
    }

    private fun shouldAddSecurityAnalysis(
        stepId: String,
        result: ru.marslab.ide.ride.model.tool.StepOutput
    ): Boolean {
        return when {
            result.data.containsKey("security_issues") -> true
            result.data.containsKey("vulnerabilities") -> true
            result.data.containsKey("authentication") -> true
            result.data.containsKey("authorization") -> true
            result.data.containsKey("encryption") -> true
            else -> false
        }
    }

    private fun shouldAddAdditionalTesting(
        stepId: String,
        result: ru.marslab.ide.ride.model.tool.StepOutput
    ): Boolean {
        return when {
            result.data.containsKey("test_coverage") -> {
                val coverage = result.get<Double>("test_coverage") ?: 100.0
                coverage < 80.0 // Низкое покрытие → нужны дополнительные тесты
            }

            result.data.containsKey("missing_tests") -> true
            result.data.containsKey("edge_cases") -> true
            else -> false
        }
    }

    // --- Фабричные методы для создания условных шагов ---

    private fun createCriticalIssuesConditionalStep(
        plan: ExecutionPlan,
        context: ChatContext
    ): ConditionalStep {
        val condition: (ExecutionContext, Map<String, ru.marslab.ide.ride.model.tool.StepOutput>) -> Boolean = { ctx, results ->
            val analysisResult = results["analysis"]
            analysisResult?.let { result ->
                val findings = result.data["findings"] as? List<*> ?: emptyList<Any>()
                findings.any { finding ->
                    finding.toString().lowercase().contains("critical") ||
                    finding.toString().lowercase().contains("severe")
                }
            } ?: false
        }

        val thenStep = PlanStep(
            id = "critical_issues_analysis",
            title = "Детальный анализ критических проблем",
            description = "Глубокий анализ найденных критических проблем",
            agentType = AgentType.BUG_DETECTION,
            input = mapOf(
                "analysis_depth" to "critical",
                "focus_on" to "security_and_performance"
            ),
            dependencies = setOf("analysis"),
            estimatedDurationMs = 15000
        )

        return ConditionalStep(
            id = "check_critical_issues",
            description = "Проверить наличие критических проблем",
            condition = condition,
            thenStep = thenStep,
            dependencies = setOf("analysis")
        )
    }

    private fun createProjectSegmentationStep(
        plan: ExecutionPlan,
        context: ChatContext
    ): PlanStep {
        return PlanStep(
            id = "project_segmentation",
            title = "Сегментация проекта",
            description = "Разделение проекта на управляемые сегменты для анализа",
            agentType = AgentType.PROJECT_SCANNER,
            input = mapOf(
                "segmentation_strategy" to "by_module",
                "max_segments" to 5
            ),
            dependencies = setOf("project_scan"),
            estimatedDurationMs = 8000
        )
    }

    private fun createTestCoverageConditionalStep(
        plan: ExecutionPlan,
        context: ChatContext
    ): ConditionalStep {
        val condition: (ExecutionContext, Map<String, ru.marslab.ide.ride.model.tool.StepOutput>) -> Boolean = { ctx, results ->
            val scanResult = results["project_scan"]
            scanResult?.let { result ->
                val testFiles = result.get<List<String>>("test_files") ?: emptyList()
                val sourceFiles = result.get<List<String>>("source_files") ?: emptyList()
                sourceFiles.isNotEmpty() && (testFiles.size.toDouble() / sourceFiles.size) < 0.5
            } ?: false
        }

        val thenStep = PlanStep(
            id = "test_coverage_analysis",
            title = "Анализ тестового покрытия",
            description = "Анализ покрытия кода тестами",
            agentType = AgentType.TEST_GENERATOR,
            input = mapOf(
                "analysis_mode" to "coverage",
                "generate_missing" to false
            ),
            dependencies = setOf("project_scan"),
            estimatedDurationMs = 10000
        )

        return ConditionalStep(
            id = "check_test_coverage",
            description = "Проверить тестовое покрытие",
            condition = condition,
            thenStep = thenStep,
            dependencies = setOf("project_scan")
        )
    }

    // --- Фабричные методы для динамических шагов ---

    private fun createDetailedAnalysisStep(
        basedOnStepId: String,
        context: ExecutionContext
    ): PlanStep {
        return PlanStep(
            id = "detailed_analysis_${basedOnStepId}",
            title = "Детальный анализ результатов",
            description = "Глубокий анализ результатов предыдущего шага",
            agentType = AgentType.LLM_REVIEW,
            input = mapOf(
                "based_on_step" to basedOnStepId,
                "analysis_depth" to "deep"
            ),
            dependencies = setOf(basedOnStepId),
            estimatedDurationMs = 12000
        )
    }

    private fun createPerformanceOptimizationStep(
        basedOnStepId: String,
        context: ExecutionContext
    ): PlanStep {
        return PlanStep(
            id = "performance_optimization_${basedOnStepId}",
            title = "Оптимизация производительности",
            description = "Анализ и оптимизация производительности",
            agentType = AgentType.PERFORMANCE_ANALYZER,
            input = mapOf(
                "optimization_scope" to "identified_issues",
                "based_on_step" to basedOnStepId
            ),
            dependencies = setOf(basedOnStepId),
            estimatedDurationMs = 15000
        )
    }

    private fun createSecurityAnalysisStep(
        basedOnStepId: String,
        context: ExecutionContext
    ): PlanStep {
        return PlanStep(
            id = "security_analysis_${basedOnStepId}",
            title = "Анализ безопасности",
            description = "Проверка безопасности на основе найденных проблем",
            agentType = AgentType.CODE_QUALITY,
            input = mapOf(
                "security_scan_level" to "focused",
                "based_on_step" to basedOnStepId
            ),
            dependencies = setOf(basedOnStepId),
            estimatedDurationMs = 12000
        )
    }

    private fun createAdditionalTestingStep(
        basedOnStepId: String,
        context: ExecutionContext
    ): PlanStep {
        return PlanStep(
            id = "additional_testing_${basedOnStepId}",
            title = "Дополнительное тестирование",
            description = "Создание дополнительных тестов на основе анализа",
            agentType = AgentType.TEST_GENERATOR,
            input = mapOf(
                "test_type" to "complementary",
                "based_on_step" to basedOnStepId
            ),
            dependencies = setOf(basedOnStepId),
            estimatedDurationMs = 10000
        )
    }

    // --- Вспомогательные методы ---

    private fun isLargeProject(projectPath: String): Boolean {
        // Простая эвристика для определения размера проекта
        // В реальной реализации здесь был бы анализ файловой системы
        return false // Заглушка для тестов
    }

    private fun shouldAnalyzeTestCoverage(plan: ExecutionPlan, context: ChatContext): Boolean {
        // Проверяем, связан ли запрос с кодом и тестированием
        val request = plan.originalRequest.lowercase()
        return request.contains("тест") ||
               request.contains("test") ||
               plan.analysis.taskType == TaskType.TESTING ||
               plan.analysis.taskType == TaskType.CODE_ANALYSIS
    }

    /**
     * Mock ToolAgentRegistry для тестирования
     * В реальной реализации здесь будет настоящая зависимость
     */
    private fun mockToolAgentRegistry(): ToolAgentRegistry {
        // Заглушка - в реальном коде будет инъекция зависимости
        return ToolAgentRegistry()
    }
}