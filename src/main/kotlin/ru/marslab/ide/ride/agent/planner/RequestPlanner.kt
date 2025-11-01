package ru.marslab.ide.ride.agent.planner

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.analyzer.UncertaintyResult
import ru.marslab.ide.ride.agent.analyzer.ComplexityLevel
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.*
import kotlinx.datetime.Clock
import java.util.*

/**
 * Планировщик запросов для создания адаптивных планов выполнения
 *
 * Создает планы на основе анализа неопределенности и сложности запроса:
 * - Простые планы для средней сложности (1-3 шага)
 * - Базовые планы для сложных запросов (3-5 шагов)
 * - Поддержка зависимостей между шагами
 * - Адаптация под тип задачи
 */
class RequestPlanner {

    private val logger = Logger.getInstance(RequestPlanner::class.java)

    /**
     * Создает план выполнения на основе запроса и анализа неопределенности
     */
    fun createPlan(
        request: String,
        uncertainty: UncertaintyResult,
        context: ChatContext,
        userRequestId: String = UUID.randomUUID().toString()
    ): ExecutionPlan {
        logger.info("Creating execution plan for request with complexity: ${uncertainty.complexity}")

        // Анализируем запрос для определения типа задачи
        val requestAnalysis = analyzeRequest(request, uncertainty, context)

        // Создаем шаги плана
        val steps = createPlanSteps(requestAnalysis, uncertainty, context)

        // Формируем план
        return ExecutionPlan(
            id = UUID.randomUUID().toString(),
            userRequestId = userRequestId,
            originalRequest = request,
            analysis = requestAnalysis,
            steps = steps,
            currentState = PlanState.CREATED,
            createdAt = Clock.System.now(),
            version = 1,
            metadata = mapOf(
                "planner_version" to "1.0",
                "uncertainty_score" to uncertainty.score,
                "complexity_level" to uncertainty.complexity.name,
                "suggested_actions" to uncertainty.suggestedActions,
                "detected_features" to uncertainty.detectedFeatures,
                "reasoning" to uncertainty.reasoning
            )
        )
    }

    /**
     * Анализирует запрос и создает RequestAnalysis
     */
    private fun analyzeRequest(request: String, uncertainty: UncertaintyResult, context: ChatContext): RequestAnalysis {
        val taskType = determineTaskType(request, uncertainty)
        val requiredTools = determineRequiredTools(taskType, uncertainty)
        val estimatedComplexity = mapComplexityLevel(uncertainty.complexity)
        val estimatedSteps = estimateSteps(taskType, uncertainty.complexity)
        val requiresUserInput = determineUserInputRequirement(taskType, uncertainty)

        val executionContext = ExecutionContext(
            projectPath = context.project?.basePath,
            selectedFiles = extractSelectedFiles(context),
            selectedDirectories = emptyList(),
            gitBranch = null, // Можно добавить в будущем
            additionalContext = mapOf(
                "conversation_history_size" to context.history.size,
                "has_code_context" to (request.contains("код") || request.contains("файл")),
                "uncertainty_score" to uncertainty.score
            )
        )

        val parameters = mapOf(
            "original_request" to request,
            "uncertainty_analysis" to uncertainty,
            "complexity_factors" to uncertainty.detectedFeatures
        )

        return RequestAnalysis(
            taskType = taskType,
            requiredTools = requiredTools,
            context = executionContext,
            parameters = parameters,
            requiresUserInput = requiresUserInput,
            estimatedComplexity = estimatedComplexity,
            estimatedSteps = estimatedSteps,
            confidence = 1.0 - uncertainty.score,
            reasoning = uncertainty.reasoning
        )
    }

    /**
     * Определяет тип задачи на основе запроса и неопределенности
     */
    private fun determineTaskType(request: String, uncertainty: UncertaintyResult): TaskType {
        val requestLower = request.lowercase()

        return when {
            requestLower.contains("архитектур") -> TaskType.ARCHITECTURE_ANALYSIS
            requestLower.contains("баг") || requestLower.contains("ошибк") -> TaskType.BUG_FIX
            requestLower.contains("рефактор") -> TaskType.REFACTORING
            requestLower.contains("оптимиз") || requestLower.contains("производительность") -> TaskType.PERFORMANCE_OPTIMIZATION
            requestLower.contains("тест") -> TaskType.TESTING
            requestLower.contains("документ") -> TaskType.DOCUMENTATION
            requestLower.contains("миграц") -> TaskType.MIGRATION
            requestLower.contains("качеств") || requestLower.contains("code review") -> TaskType.CODE_ANALYSIS
            requestLower.contains("отчет") || requestLower.contains("report") -> TaskType.REPORT_GENERATION
            uncertainty.complexity == ComplexityLevel.SIMPLE -> TaskType.SIMPLE_QUERY
            else -> TaskType.COMPLEX_MULTI_STEP
        }
    }

    /**
     * Определяет необходимые инструменты для задачи
     */
    private fun determineRequiredTools(taskType: TaskType, uncertainty: UncertaintyResult): Set<AgentType> {
        val tools = mutableSetOf<AgentType>()

        when (taskType) {
            TaskType.ARCHITECTURE_ANALYSIS -> {
                tools.add(AgentType.PROJECT_SCANNER)
                tools.add(AgentType.ARCHITECTURE_ANALYSIS)
                if (uncertainty.suggestedActions.contains("поиск_контекста")) {
                    tools.add(AgentType.EMBEDDING_INDEXER)
                }
            }

            TaskType.BUG_FIX -> {
                tools.add(AgentType.BUG_DETECTION)
                tools.add(AgentType.CODE_QUALITY)
                tools.add(AgentType.LLM_REVIEW)
            }

            TaskType.REFACTORING -> {
                tools.add(AgentType.CODE_QUALITY)
                tools.add(AgentType.LLM_REVIEW)
                tools.add(AgentType.CODE_FIXER)
            }

            TaskType.PERFORMANCE_OPTIMIZATION -> {
                tools.add(AgentType.PERFORMANCE_ANALYZER)
                tools.add(AgentType.CODE_QUALITY)
            }

            TaskType.TESTING -> {
                tools.add(AgentType.TEST_GENERATOR)
                tools.add(AgentType.CODE_QUALITY)
            }

            TaskType.DOCUMENTATION -> {
                tools.add(AgentType.DOCUMENTATION_GENERATOR)
                tools.add(AgentType.PROJECT_SCANNER)
            }

            TaskType.CODE_ANALYSIS -> {
                tools.add(AgentType.CODE_QUALITY)
                tools.add(AgentType.LLM_REVIEW)
            }

            TaskType.REPORT_GENERATION -> {
                tools.add(AgentType.PROJECT_SCANNER)
                tools.add(AgentType.CODE_QUALITY)
                tools.add(AgentType.REPORT_GENERATOR)
            }

            TaskType.SIMPLE_QUERY -> {
                // Для простых запросов дополнительные инструменты не нужны
            }

            TaskType.COMPLEX_MULTI_STEP -> {
                tools.add(AgentType.PROJECT_SCANNER)
                tools.add(AgentType.CODE_QUALITY)
                if (uncertainty.suggestedActions.contains("поиск_контекста")) {
                    tools.add(AgentType.EMBEDDING_INDEXER)
                }
            }

            else -> {
                tools.add(AgentType.PROJECT_SCANNER)
            }
        }

        return tools
    }

    /**
     * Преобразует ComplexityLevel в ComplexityLevel из RequestAnalysis
     */
    private fun mapComplexityLevel(level: ru.marslab.ide.ride.agent.analyzer.ComplexityLevel): ru.marslab.ide.ride.model.orchestrator.ComplexityLevel {
        return when (level) {
            ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.SIMPLE -> ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.LOW
            ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.MEDIUM -> ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.MEDIUM
            ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.COMPLEX -> ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.HIGH
        }
    }

    /**
     * Оценивает количество шагов для задачи
     */
    private fun estimateSteps(taskType: TaskType, complexity: ComplexityLevel): Int {
        val baseSteps = when (taskType) {
            TaskType.SIMPLE_QUERY -> 1
            TaskType.CODE_ANALYSIS -> 2
            TaskType.DOCUMENTATION -> 2
            TaskType.TESTING -> 3
            TaskType.BUG_FIX -> 3
            TaskType.REFACTORING -> 3
            TaskType.PERFORMANCE_OPTIMIZATION -> 3
            TaskType.REPORT_GENERATION -> 3
            TaskType.ARCHITECTURE_ANALYSIS -> 4
            TaskType.MIGRATION -> 4
            TaskType.COMPLEX_MULTI_STEP -> 5
            else -> 2
        }

        // Корректируем на основе сложности
        return when (complexity) {
            ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.SIMPLE -> baseSteps
            ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.MEDIUM -> baseSteps + 1
            ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.COMPLEX -> baseSteps + 2
        }
    }

    /**
     * Определяет, требуется ли ввод от пользователя
     */
    private fun determineUserInputRequirement(taskType: TaskType, uncertainty: UncertaintyResult): Boolean {
        return when (taskType) {
            TaskType.SIMPLE_QUERY -> false
            TaskType.BUG_FIX -> uncertainty.score > 0.3 // Требует уточнения для сложных багов
            TaskType.REFACTORING -> uncertainty.score > 0.4 // Требует понимания требований
            TaskType.ARCHITECTURE_ANALYSIS -> true // Всегда требует уточнения
            TaskType.MIGRATION -> true // Сложные миграции требуют уточнения
            else -> uncertainty.score > 0.5
        }
    }

    /**
     * Извлекает выбранные файлы из контекста
     */
    private fun extractSelectedFiles(context: ChatContext): List<String> {
        // В будущем можно добавить извлечение файлов из контекста
        // Например, если пользователь выбрал файлы в IDE
        return emptyList()
    }

    /**
     * Создает шаги плана выполнения
     */
    private fun createPlanSteps(
        requestAnalysis: RequestAnalysis,
        uncertainty: UncertaintyResult,
        context: ChatContext
    ): List<PlanStep> {
        val steps = mutableListOf<PlanStep>()
        val stepIdCounter = mutableMapOf<String, Int>()

        when (requestAnalysis.taskType) {
            TaskType.SIMPLE_QUERY -> {
                // Один шаг для простого запроса
                steps.add(createSimpleQueryStep(requestAnalysis, stepIdCounter))
            }

            TaskType.CODE_ANALYSIS -> {
                steps.add(createAnalysisStep(requestAnalysis, stepIdCounter))
                steps.add(createQualityCheckStep(requestAnalysis, stepIdCounter, setOf("analysis")))
            }

            TaskType.BUG_FIX -> {
                steps.add(createBugDetectionStep(requestAnalysis, stepIdCounter))
                steps.add(createQualityCheckStep(requestAnalysis, stepIdCounter, setOf("bug_detection")))
                steps.add(createBugFixStep(requestAnalysis, stepIdCounter, setOf("bug_detection", "quality_check")))
            }

            TaskType.REFACTORING -> {
                steps.add(createQualityCheckStep(requestAnalysis, stepIdCounter))
                steps.add(createRefactorStep(requestAnalysis, stepIdCounter, setOf("quality_check")))
                steps.add(createValidationStep(requestAnalysis, stepIdCounter, setOf("quality_check", "refactor")))
            }

            TaskType.ARCHITECTURE_ANALYSIS -> {
                steps.add(createProjectScanStep(requestAnalysis, stepIdCounter))
                steps.add(createArchitectureStep(requestAnalysis, stepIdCounter, setOf("project_scan")))
                steps.add(createQualityCheckStep(requestAnalysis, stepIdCounter, setOf("project_scan", "architecture")))
                steps.add(createDocumentationStep(requestAnalysis, stepIdCounter, setOf("architecture", "quality_check")))
            }

            TaskType.REPORT_GENERATION -> {
                steps.add(createProjectScanStep(requestAnalysis, stepIdCounter))
                steps.add(createAnalysisStep(requestAnalysis, stepIdCounter, setOf("project_scan")))
                steps.add(createReportStep(requestAnalysis, stepIdCounter, setOf("project_scan", "analysis")))
            }

            TaskType.COMPLEX_MULTI_STEP -> {
                steps.add(createProjectScanStep(requestAnalysis, stepIdCounter))
                if (uncertainty.suggestedActions.contains("поиск_контекста")) {
                    steps.add(createRagEnrichmentStep(requestAnalysis, stepIdCounter, setOf("project_scan")))
                }
                steps.add(createAnalysisStep(requestAnalysis, stepIdCounter, setOf("project_scan")))
                steps.add(createQualityCheckStep(requestAnalysis, stepIdCounter, setOf("analysis")))
                steps.add(createDocumentationStep(requestAnalysis, stepIdCounter, setOf("analysis", "quality_check")))
            }

            else -> {
                steps.add(createGenericStep(requestAnalysis, stepIdCounter))
            }
        }

        return steps
    }

    // --- Фабричные методы для создания шагов ---

    private fun createSimpleQueryStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>): PlanStep {
        return PlanStep(
            id = generateStepId("simple_query", counter),
            title = "Обработка простого запроса",
            description = "Прямой ответ на простой запрос без дополнительного анализа",
            agentType = AgentType.USER_INTERACTION,
            input = mapOf(
                "request" to (analysis.parameters["original_request"] ?: ""),
                "complexity" to "low"
            ),
            estimatedDurationMs = 1000
        )
    }

    private fun createProjectScanStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>): PlanStep {
        return PlanStep(
            id = generateStepId("project_scan", counter),
            title = "Сканирование проекта",
            description = "Анализ структуры проекта и поиск релевантных файлов",
            agentType = AgentType.PROJECT_SCANNER,
            input = mapOf(
                "project_path" to (analysis.context.projectPath ?: "."),
                "task_type" to analysis.taskType.name
            ),
            estimatedDurationMs = 5000
        )
    }

    private fun createAnalysisStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>, dependencies: Set<String> = emptySet()): PlanStep {
        return PlanStep(
            id = generateStepId("analysis", counter),
            title = "Анализ кода",
            description = "Детальный анализ кода и выявление проблем",
            agentType = AgentType.LLM_REVIEW,
            input = mapOf<String, Any>(
                "request" to (analysis.parameters["original_request"] ?: ""),
                "context" to analysis.context,
                "task_type" to analysis.taskType.name
            ),
            dependencies = dependencies,
            estimatedDurationMs = 8000
        )
    }

    private fun createQualityCheckStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>, dependencies: Set<String> = emptySet()): PlanStep {
        return PlanStep(
            id = generateStepId("quality_check", counter),
            title = "Проверка качества кода",
            description = "Анализ качества кода и поиск потенциальных проблем",
            agentType = AgentType.CODE_QUALITY,
            input = mapOf(
                "project_path" to (analysis.context.projectPath ?: "."),
                "severity" to "medium"
            ),
            dependencies = dependencies,
            estimatedDurationMs = 6000
        )
    }

    private fun createBugDetectionStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>): PlanStep {
        return PlanStep(
            id = generateStepId("bug_detection", counter),
            title = "Поиск багов",
            description = "Поиск потенциальных ошибок в коде",
            agentType = AgentType.BUG_DETECTION,
            input = mapOf(
                "project_path" to (analysis.context.projectPath ?: "."),
                "scan_depth" to "deep"
            ),
            estimatedDurationMs = 10000
        )
    }

    private fun createBugFixStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>, dependencies: Set<String> = emptySet()): PlanStep {
        return PlanStep(
            id = generateStepId("bug_fix", counter),
            title = "Исправление багов",
            description = "Предложения по исправлению найденных проблем",
            agentType = AgentType.CODE_FIXER,
            input = mapOf(
                "fix_strategy" to "suggestions_only" // Только предложения, не автоправки
            ),
            dependencies = dependencies,
            estimatedDurationMs = 8000
        )
    }

    private fun createRefactorStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>, dependencies: Set<String> = emptySet()): PlanStep {
        return PlanStep(
            id = generateStepId("refactor", counter),
            title = "Рефакторинг",
            description = "Анализ и предложения по рефакторингу кода",
            agentType = AgentType.LLM_REVIEW,
            input = mapOf(
                "refactor_type" to "suggestions",
                "focus_areas" to listOf("readability", "performance", "maintainability")
            ),
            dependencies = dependencies,
            estimatedDurationMs = 12000
        )
    }

    private fun createArchitectureStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>, dependencies: Set<String> = emptySet()): PlanStep {
        return PlanStep(
            id = generateStepId("architecture", counter),
            title = "Анализ архитектуры",
            description = "Оценка архитектуры проекта и выявление проблем",
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
            input = mapOf(
                "analysis_depth" to "comprehensive",
                "focus_patterns" to listOf("layering", "dependencies", "design_patterns")
            ),
            dependencies = dependencies,
            estimatedDurationMs = 15000
        )
    }

    private fun createValidationStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>, dependencies: Set<String> = emptySet()): PlanStep {
        return PlanStep(
            id = generateStepId("validation", counter),
            title = "Валидация изменений",
            description = "Проверка корректности предложенных изменений",
            agentType = AgentType.LLM_REVIEW,
            input = mapOf(
                "validation_type" to "comprehensive"
            ),
            dependencies = dependencies,
            estimatedDurationMs = 5000
        )
    }

    private fun createRagEnrichmentStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>, dependencies: Set<String> = emptySet()): PlanStep {
        return PlanStep(
            id = generateStepId("rag_enrichment", counter),
            title = "Поиск релевантного контекста",
            description = "Поиск и анализ релевантных фрагментов кода",
            agentType = AgentType.EMBEDDING_INDEXER,
            input = mapOf<String, Any>(
                "query" to (analysis.parameters["original_request"] ?: ""),
                "max_chunks" to 10
            ),
            dependencies = dependencies,
            estimatedDurationMs = 3000
        )
    }

    private fun createReportStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>, dependencies: Set<String> = emptySet()): PlanStep {
        return PlanStep(
            id = generateStepId("report", counter),
            title = "Создание отчета",
            description = "Генерация отчета на основе анализа",
            agentType = AgentType.REPORT_GENERATOR,
            input = mapOf(
                "report_type" to "comprehensive",
                "format" to "markdown"
            ),
            dependencies = dependencies,
            estimatedDurationMs = 7000
        )
    }

    private fun createDocumentationStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>, dependencies: Set<String> = emptySet()): PlanStep {
        return PlanStep(
            id = generateStepId("documentation", counter),
            title = "Создание документации",
            description = "Генерация документации по результатам анализа",
            agentType = AgentType.DOCUMENTATION_GENERATOR,
            input = mapOf(
                "doc_type" to "analysis_report",
                "include_examples" to true
            ),
            dependencies = dependencies,
            estimatedDurationMs = 6000
        )
    }

    private fun createGenericStep(analysis: RequestAnalysis, counter: MutableMap<String, Int>): PlanStep {
        return PlanStep(
            id = generateStepId("generic", counter),
            title = "Обработка запроса",
            description = "Стандартная обработка запроса",
            agentType = AgentType.USER_INTERACTION,
            input = mapOf<String, Any>(
                "request" to (analysis.parameters["original_request"] ?: ""),
                "task_type" to analysis.taskType.name
            ),
            estimatedDurationMs = 5000
        )
    }

    /**
     * Генерирует уникальный ID для шага с учетом типа шага
     */
    private fun generateStepId(stepType: String, counter: MutableMap<String, Int>): String {
        val count = counter.getOrPut(stepType) { 0 } + 1
        counter[stepType] = count
        return "${stepType}_${count}"
    }
}