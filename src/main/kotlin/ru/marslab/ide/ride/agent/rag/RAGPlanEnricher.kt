package ru.marslab.ide.ride.agent.rag

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.runBlocking
import ru.marslab.ide.ride.agent.analyzer.UncertaintyResult
import ru.marslab.ide.ride.agent.analyzer.UncertaintyThresholds
import ru.marslab.ide.ride.agent.impl.MCPFileSystemAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTConfig
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.ExecutionPlan
import ru.marslab.ide.ride.service.rag.RagEnrichmentService
import ru.marslab.ide.ride.service.rag.RagResult
import ru.marslab.ide.ride.settings.PluginSettings

/**
 * Обогатитель планов с использованием RAG (Retrieval-Augmented Generation)
 *
 * Используется только на этапе планирования для:
 * - Поиска релевантных файлов по ключевым словам
 * - Анализа найденных файлов для составления дополнительных шагов
 * - Добавления шагов на основе контекста проекта
 */
class RAGPlanEnricher(
    private val llmProvider: LLMProvider? = null
) {

    private val logger = Logger.getInstance(RAGPlanEnricher::class.java)
    private val ragService = service<RagEnrichmentService>()
    private val settings = service<PluginSettings>()

    /**
     * Обогащает план выполнения с помощью RAG
     *
     * @param plan Оригинальный план выполнения
     * @param request Оригинальный запрос пользователя
     * @param context Контекст выполнения
     * @return Обогащенный план или оригинальный если RAG недоступен
     */
    suspend fun enrichPlan(
        plan: ExecutionPlan,
        request: String,
        context: ChatContext
    ): ExecutionPlan {
        // Проверяем, нужно ли RAG обогащение
        if (!shouldUseRagEnrichment(plan, request)) {
            logger.debug("RAG enrichment not needed for this plan")
            return plan
        }

        logger.info("Starting RAG enrichment for plan ${plan.id}")

        return try {
            // Выполняем RAG поиск
            val ragResult = performRagSearch(request, context)
                ?: return plan // RAG недоступен, возвращаем оригинальный план

            // Анализируем найденные файлы
            val fileAnalysis = analyzeFoundFiles(ragResult, request)

            // Обогащаем план на основе анализа
            val enrichedPlan = enrichPlanWithFileAnalysis(plan, ragResult, fileAnalysis, context)

            logger.info("RAG enrichment completed for plan ${plan.id}")
            enrichedPlan

        } catch (e: Exception) {
            logger.warn("RAG enrichment failed for plan ${plan.id}", e)
            plan // Возвращаем оригинальный план в случае ошибки
        }
    }

    /**
     * Проверяет, нужно ли использовать RAG обогащение
     *
     * RAG обогащение используется для:
     * 1. Конкретных поисковых запросов (найти класс/метод)
     * 2. Сложных запросов, требующих контекста из проекта
     * Но с фильтрацией релевантности данных
     */
    private fun shouldUseRagEnrichment(plan: ExecutionPlan, request: String): Boolean {
        // Проверяем настройки
        if (!settings.enableRagEnrichment) {
            return false
        }

        val requestLower = request.lowercase()

        // 1. Конкретные поисковые запросы (требуют RAG всегда)
        val searchKeywords = listOf(
            "найди", "покажи", "поищи", "где находится", "в каком файле", "как реализован",
            "использование", "примеры использования", "применение", "реализация",
            "поиск", "search", "find", "locate", "where is", "how is implemented"
        )

        val specificSearchKeywords = listOf(
            "функция", "метод", "класс", "интерфейс", "переменная", "константа",
            "function", "method", "class", "interface", "variable", "constant"
        )

        val isSearchQuery = searchKeywords.any { keyword -> requestLower.contains(keyword) }
        val hasSpecificObjects = specificSearchKeywords.any { keyword -> requestLower.contains(keyword) }
        val needsRagForSearch = isSearchQuery && hasSpecificObjects

        // 2. Сложные запросы, требующие контекста (с проверкой релевантности)
        val complexity = plan.analysis.estimatedComplexity
        val isComplexQuery = complexity.name in setOf("HIGH", "VERY_HIGH", "EXTREME")

        // Сложные запросы, где нужен контекст проекта
        val complexContextKeywords = listOf(
            "архитектура", "структура", "взаимодействие", "зависимости", "модули",
            "архитектура", "структура", "взаимодействие", "зависимости", "модули",
            "рефакторинг", "улучшение", "оптимизация", "производительность",
            "безопасность", "security", "безопасность", "оптимизация"
        )

        val needsContext = complexContextKeywords.any { keyword -> requestLower.contains(keyword) }
        val needsRagForComplex = isComplexQuery && needsContext

        // 3. Общие запросы, НЕ требующие RAG
        val isGeneralAnalysis = setOf(
            "проанализируй проект", "анализ проекта", "анализируй весь проект",
            "оцени проект", "обзор проекта", "структура проекта"
        ).any { pattern -> requestLower.contains(pattern) }

        val shouldUseRag = (needsRagForSearch || needsRagForComplex) && !isGeneralAnalysis

        logger.info("RAG enrichment decision: query='$request', complexity=${complexity.name}, " +
                   "needsRagForSearch=$needsRagForSearch, needsRagForComplex=$needsRagForComplex, " +
                   "isGeneral=$isGeneralAnalysis, shouldUse=$shouldUseRag")

        return shouldUseRag
    }

    /**
     * Выполняет RAG поиск релевантных файлов
     */
    private suspend fun performRagSearch(request: String, context: ChatContext): RagResult? {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        if (project == null) {
            logger.warn("No open project found for RAG enrichment")
            return null
        }

        val projectPath = project.basePath ?: return null
        val maxRagTokens = (settings.maxContextTokens * 0.2).toInt() // 20% контекста на RAG

        return try {
            // Используем advanced RAG с LLM reranking
            val ragResultAdvanced = ragService.enrichQueryAdvanced(request, maxRagTokens)
            if (ragResultAdvanced != null) {
                ragService.toLegacyResult(ragResultAdvanced)
            } else {
                // Fallback к простому RAG
                ragService.enrichQueryLegacy(request, maxRagTokens)
            }
        } catch (e: Exception) {
            logger.error("Error performing RAG search", e)
            null
        }
    }

    /**
     * Анализирует найденные файлы для определения дополнительной работы
     */
    private suspend fun analyzeFoundFiles(ragResult: RagResult, request: String): FileAnalysisResult {
        if (ragResult.chunks.isEmpty()) {
            return FileAnalysisResult(
                relevantFiles = emptyList(),
                suggestedSteps = emptyList(),
                complexityAdjustment = 0.0,
                reasoning = "No relevant files found"
            )
        }

        // Фильтруем найденные данные по релевантности
        val relevantChunks = filterChunksByRelevance(ragResult.chunks, request)
        if (relevantChunks.isEmpty()) {
            logger.warn("Found chunks but none passed relevance filter")
            return FileAnalysisResult(
                relevantFiles = emptyList(),
                suggestedSteps = emptyList(),
                complexityAdjustment = 0.0,
                reasoning = "Found chunks but none passed relevance filter"
            )
        }

        val relevantFiles = relevantChunks.groupBy { it.filePath }
        val suggestedSteps = mutableListOf<String>()
        var complexityAdjustment = 0.0

        // Анализируем типы файлов и количество
        relevantFiles.forEach { (filePath, chunks) ->
            val fileName = filePath.substringAfterLast("/")
            val fileExtension = fileName.substringAfterLast(".", "")

            when {
                fileExtension in listOf("kt", "java", "scala") -> {
                    // Kotlin/Java файлы - возможно нужен детальный анализ
                    if (chunks.size > 2) {
                        suggestedSteps.add("Детальный анализ кода в $filePath")
                        complexityAdjustment += 0.1
                    }
                }

                fileExtension in listOf("gradle", "pom.xml", "build.gradle") -> {
                    // Файлы сборки - возможно нужно проанализировать зависимости
                    suggestedSteps.add("Анализ зависимостей и конфигурации сборки")
                    complexityAdjustment += 0.05
                }

                fileExtension in listOf("md", "txt", "rst") -> {
                    // Документация - возможно нужно проанализировать существующую документацию
                    suggestedSteps.add("Анализ существующей документации")
                }

                fileName.contains("test", ignoreCase = true) -> {
                    // Тестовые файлы - возможно нужен анализ тестового покрытия
                    suggestedSteps.add("Анализ тестового покрытия")
                    complexityAdjustment += 0.05
                }

                chunks.any { it.content.lowercase().contains("todo") ||
                              it.content.lowercase().contains("fixme") } -> {
                    // Найдены TODO/FIXME - возможно нужно включить в план
                    suggestedSteps.add("Анализ технического долга (TODO/FIXME)")
                    complexityAdjustment += 0.1
                }
            }
        }

        // Анализируем общий контент для определения дополнительных шагов
        val allContent = ragResult.chunks.joinToString("\n") { it.content }
        val contentLower = allContent.lowercase()

        when {
            contentLower.contains("exception") || contentLower.contains("error") -> {
                suggestedSteps.add("Анализ обработки ошибок")
                complexityAdjustment += 0.1
            }

            contentLower.contains("database") || contentLower.contains("sql") -> {
                suggestedSteps.add("Анализ работы с базой данных")
                complexityAdjustment += 0.1
            }

            contentLower.contains("performance") || contentLower.contains("optimization") -> {
                suggestedSteps.add("Анализ производительности")
                complexityAdjustment += 0.15
            }

            contentLower.contains("security") || contentLower.contains("auth") -> {
                suggestedSteps.add("Анализ безопасности")
                complexityAdjustment += 0.2
            }

            ragResult.chunks.size > 10 -> {
                // Много найденных файлов - возможно нужен дополнительный анализ
                suggestedSteps.add("Комплексный анализ структуры проекта")
                complexityAdjustment += 0.1
            }
        }

        // Ограничиваем корректировку сложности
        complexityAdjustment = complexityAdjustment.coerceIn(0.0, 0.5)

        return FileAnalysisResult(
            relevantFiles = relevantFiles.keys.toList(),
            suggestedSteps = suggestedSteps.distinct(),
            complexityAdjustment = complexityAdjustment,
            reasoning = "Based on analysis of ${ragResult.chunks.size} chunks from ${relevantFiles.size} files"
        )
    }

    /**
     * Обогащает план на основе анализа файлов
     */
    private suspend fun enrichPlanWithFileAnalysis(
        plan: ExecutionPlan,
        ragResult: RagResult,
        fileAnalysis: FileAnalysisResult,
        context: ChatContext
    ): ExecutionPlan {
        val enrichedSteps = plan.steps.toMutableList()
        val stepIdCounter = mutableMapOf<String, Int>()

        // Обновляем существующие шаги с информацией о найденных файлах
        enrichedSteps.forEach { step ->
            when (step.agentType.name) {
                "PROJECT_SCANNER" -> {
                    // Обогащаем шаг сканирования информацией о найденных файлах
                    val updatedInput = step.input.toMutableMap()
                    updatedInput["rag_enriched_files"] = fileAnalysis.relevantFiles
                    updatedInput["rag_chunks_count"] = ragResult.chunks.size
                    enrichedSteps[enrichedSteps.indexOf(step)] = step.copy(input = updatedInput)
                }

                "CODE_ANALYSIS" -> {
                    // Обогащаем шаг анализа информацией о релевантных файлах
                    val updatedInput = step.input.toMutableMap()
                    updatedInput["focus_files"] = fileAnalysis.relevantFiles.take(5) // Фокус на 5 самых релевантных
                    enrichedSteps[enrichedSteps.indexOf(step)] = step.copy(input = updatedInput)
                }
            }
        }

        // Добавляем новые шаги на основе анализа файлов
        fileAnalysis.suggestedSteps.forEach { suggestedStep ->
            val newStep = when {
                suggestedStep.contains("Детальный анализ кода") -> createDetailedCodeAnalysisStep(
                    plan, fileAnalysis, stepIdCounter
                )

                suggestedStep.contains("Анализ зависимостей") -> createDependencyAnalysisStep(
                    plan, stepIdCounter
                )

                suggestedStep.contains("Анализ тестового покрытия") -> createTestCoverageAnalysisStep(
                    plan, stepIdCounter
                )

                suggestedStep.contains("Анализ технического долга") -> createTechnicalDebtAnalysisStep(
                    plan, stepIdCounter
                )

                suggestedStep.contains("Анализ обработки ошибок") -> createErrorHandlingAnalysisStep(
                    plan, stepIdCounter
                )

                suggestedStep.contains("Анализ работы с базой данных") -> createDatabaseAnalysisStep(
                    plan, stepIdCounter
                )

                suggestedStep.contains("Анализ производительности") -> createPerformanceAnalysisStep(
                    plan, stepIdCounter
                )

                suggestedStep.contains("Анализ безопасности") -> createSecurityAnalysisStep(
                    plan, stepIdCounter
                )

                suggestedStep.contains("Анализ существующей документации") -> createDocumentationAnalysisStep(
                    plan, stepIdCounter
                )

                suggestedStep.contains("Комплексный анализ структуры проекта") -> createStructureAnalysisStep(
                    plan, stepIdCounter
                )

                else -> createGenericAnalysisStep(plan, suggestedStep, stepIdCounter)
            }

            enrichedSteps.add(newStep)
        }

        // Обновляем анализ плана
        val updatedAnalysis = plan.analysis.copy(
            estimatedComplexity = adjustComplexity(plan.analysis.estimatedComplexity, fileAnalysis.complexityAdjustment),
            estimatedSteps = enrichedSteps.size,
            confidence = (plan.analysis.confidence * 0.9).coerceAtLeast(0.1), // Немного снижаем уверенность
            reasoning = "${plan.analysis.reasoning}. ${fileAnalysis.reasoning}"
        )

        // Обновляем метаданные
        val updatedMetadata = plan.metadata.toMutableMap()
        updatedMetadata["rag_enriched"] = true
        updatedMetadata["rag_files_found"] = fileAnalysis.relevantFiles.size
        updatedMetadata["rag_chunks_found"] = ragResult.chunks.size
        updatedMetadata["rag_suggested_steps"] = fileAnalysis.suggestedSteps.size
        updatedMetadata["rag_complexity_adjustment"] = fileAnalysis.complexityAdjustment

        return plan.copy(
            steps = enrichedSteps,
            analysis = updatedAnalysis,
            metadata = updatedMetadata,
            version = plan.version + 1
        )
    }

    /**
     * Корректирует уровень сложности на основе RAG анализа
     */
    private fun adjustComplexity(
        currentComplexity: ru.marslab.ide.ride.model.orchestrator.ComplexityLevel,
        adjustment: Double
    ): ru.marslab.ide.ride.model.orchestrator.ComplexityLevel {
        return when (currentComplexity) {
            ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.LOW -> {
                if (adjustment > 0.2) ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.MEDIUM
                else ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.LOW
            }

            ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.MEDIUM -> {
                if (adjustment > 0.3) ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.HIGH
                else if (adjustment < -0.2) ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.LOW
                else ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.MEDIUM
            }

            ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.HIGH -> {
                if (adjustment < -0.3) ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.MEDIUM
                else ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.HIGH
            }

            else -> currentComplexity
        }
    }

    // --- Фабричные методы для создания дополнительных шагов ---

    private fun createDetailedCodeAnalysisStep(
        plan: ExecutionPlan,
        fileAnalysis: FileAnalysisResult,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("detailed_code_analysis", counter),
        title = "Детальный анализ кода",
        description = "Глубокий анализ найденных релевантных файлов",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.LLM_REVIEW,
        input = mapOf(
            "focus_files" to fileAnalysis.relevantFiles.take(5),
            "analysis_depth" to "deep",
            "include_suggestions" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 12000
    )

    private fun createDependencyAnalysisStep(
        plan: ExecutionPlan,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("dependency_analysis", counter),
        title = "Анализ зависимостей",
        description = "Анализ зависимостей проекта и конфигурации сборки",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.CODE_QUALITY,
        input = mapOf(
            "analysis_type" to "dependencies",
            "include_transitive" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 8000
    )

    private fun createTestCoverageAnalysisStep(
        plan: ExecutionPlan,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("test_coverage_analysis", counter),
        title = "Анализ тестового покрытия",
        description = "Анализ покрытия кода тестами",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.TEST_GENERATOR,
        input = mapOf(
            "analysis_mode" to "coverage_only",
            "include_suggestions" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 10000
    )

    private fun createTechnicalDebtAnalysisStep(
        plan: ExecutionPlan,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("technical_debt_analysis", counter),
        title = "Анализ технического долга",
        description = "Поиск и анализ TODO, FIXME и других меток технического долга",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.CODE_QUALITY,
        input = mapOf(
            "focus_patterns" to listOf("TODO", "FIXME", "HACK", "XXX"),
            "include_suggestions" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 6000
    )

    private fun createErrorHandlingAnalysisStep(
        plan: ExecutionPlan,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("error_handling_analysis", counter),
        title = "Анализ обработки ошибок",
        description = "Анализ паттернов обработки ошибок и исключений",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.CODE_QUALITY,
        input = mapOf(
            "analysis_type" to "error_handling",
            "include_best_practices" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 8000
    )

    private fun createDatabaseAnalysisStep(
        plan: ExecutionPlan,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("database_analysis", counter),
        title = "Анализ работы с базой данных",
        description = "Анализ взаимодействия с базой данных",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.PERFORMANCE_ANALYZER,
        input = mapOf(
            "analysis_type" to "database_interactions",
            "include_optimization_suggestions" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 10000
    )

    private fun createPerformanceAnalysisStep(
        plan: ExecutionPlan,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("performance_analysis", counter),
        title = "Анализ производительности",
        description = "Анализ производительности кода и поиск узких мест",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.PERFORMANCE_ANALYZER,
        input = mapOf(
            "analysis_depth" to "comprehensive",
            "include_optimization_suggestions" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 12000
    )

    private fun createSecurityAnalysisStep(
        plan: ExecutionPlan,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("security_analysis", counter),
        title = "Анализ безопасности",
        description = "Анализ безопасности кода и поиск уязвимостей",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.CODE_QUALITY,
        input = mapOf(
            "security_scan_level" to "medium",
            "include_best_practices" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 15000
    )

    private fun createDocumentationAnalysisStep(
        plan: ExecutionPlan,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("documentation_analysis", counter),
        title = "Анализ документации",
        description = "Анализ существующей документации проекта",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.DOCUMENTATION_GENERATOR,
        input = mapOf(
            "analysis_mode" to "review_existing",
            "include_gap_analysis" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 6000
    )

    private fun createStructureAnalysisStep(
        plan: ExecutionPlan,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("structure_analysis", counter),
        title = "Комплексный анализ структуры проекта",
        description = "Анализ общей структуры и организации проекта",
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.ARCHITECTURE_ANALYSIS,
        input = mapOf(
            "analysis_scope" to "comprehensive",
            "include_recommendations" to true
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 10000
    )

    private fun createGenericAnalysisStep(
        plan: ExecutionPlan,
        description: String,
        counter: MutableMap<String, Int>
    ) = ru.marslab.ide.ride.model.orchestrator.PlanStep(
        id = generateStepId("generic_analysis", counter),
        title = "Дополнительный анализ",
        description = description,
        agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.LLM_REVIEW,
        input = mapOf(
            "analysis_type" to "custom",
            "custom_description" to description
        ),
        dependencies = setOf("project_scan"),
        estimatedDurationMs = 8000
    )

    /**
     * Генерирует уникальный ID для шага
     */
    private fun generateStepId(stepType: String, counter: MutableMap<String, Int>): String {
        val count = counter.getOrPut(stepType) { 0 } + 1
        counter[stepType] = count
        return "${stepType}_${count}"
    }
}

    /**
     * Фильтрует найденные чанки по релевантности к запросу
     *
     * @param chunks Найденные чанки из RAG
     * @param request Оригинальный запрос
     * @return Отфильтрованные релевантные чанки
     */
    private fun filterChunksByRelevance(chunks: List<ru.marslab.ide.ride.service.rag.RagChunk>, request: String): List<ru.marslab.ide.ride.service.rag.RagChunk> {
        val requestLower = request.lowercase()
        val requestKeywords = extractKeywords(requestLower)

        return chunks.filter { chunk ->
            val contentLower = chunk.content.lowercase()
            val fileNameLower = chunk.filePath.substringAfterLast("/").lowercase()

            // 1. Проверяем прямое совпадение ключевых слов
            val keywordMatch = requestKeywords.any { keyword ->
                contentLower.contains(keyword) || fileNameLower.contains(keyword)
            }

            // 2. Проверяем семантическую релевантность
            val semanticRelevance = calculateSemanticRelevance(contentLower, requestKeywords)

            // 3. Проверяем консистентность (не устаревший код, не TODO/FIXME без запроса)
            val isConsistent = !isOutdatedContent(contentLower, requestLower)

            // 4. Проверяем качество кода (не мусорный код)
            val isQualityContent = isQualityCode(contentLower)

            val finalScore = when {
                keywordMatch -> 0.8 // Прямое совпадение - высокий приоритет
                semanticRelevance > 0.5 -> 0.6 // Семантическая релевантность
                semanticRelevance > 0.3 -> 0.4 // Слабая релевантность
                else -> 0.0
            }

            // Применяем штрафы за консистентность и качество
            val adjustedScore = finalScore * when {
                !isConsistent -> 0.3 // Штраф за неконсистентность
                !isQualityContent -> 0.5 // Штраф за низкое качество
                else -> 1.0
            }

            // Возвращаем только достаточно релевантные чанки
            adjustedScore >= 0.4
        }.sortedByDescending { chunk ->
            // Дополнительная сортировка по релевантности
            val contentLower = chunk.content.lowercase()
            calculateSemanticRelevance(contentLower, requestKeywords)
        }
    }

    /**
     * Извлекает ключевые слова из запроса
     */
    private fun extractKeywords(request: String): Set<String> {
        val stopWords = setOf("и", "в", "на", "с", "для", "по", "из", "к", "от", "об", "без", "через", "под", "над", "при", "о", "а", "но", "да", "или", "что", "как", "где", "когда", "зачем", "почему")

        return request.split(Regex("[^a-zA-Zа-яА-Я0-9_]+"))
            .filter { it.length > 2 }
            .filter { it !in stopWords }
            .toSet()
    }

    /**
     * Вычисляет семантическую релевантность контента к ключевым словам
     */
    private fun calculateSemanticRelevance(content: String, keywords: Set<String>): Double {
        if (keywords.isEmpty()) return 0.0

        val contentWords = content.split(Regex("[^a-zA-Zа-яА-Я0-9_]+")).toSet()
        val intersection = keywords.intersect(contentWords)

        // Базовая релевантность по ключевым словам
        val keywordScore = intersection.size.toDouble() / keywords.size

        // Бонус за связанные термины
        val relatedTermsBonus = when {
            keywords.any { it.contains("архитект") } && content.contains("структура") -> 0.2
            keywords.any { it.contains("производ") } && content.contains("оптимиз") -> 0.2
            keywords.any { it.contains("безопас") } && content.contains("security") -> 0.2
            keywords.any { it.contains("зависим") } && content.contains("import") -> 0.2
            else -> 0.0
        }

        return (keywordScore + relatedTermsBonus).coerceAtMost(1.0)
    }

    /**
     * Проверяет, не является ли контент устаревшим
     */
    private fun isOutdatedContent(content: String, request: String): Boolean {
        // Проверяем на явные маркеры устаревшего кода
        val outdatedMarkers = listOf(
            "deprecated", "@deprecated", "todo", "fixme", "hack", "workaround",
            "legacy", "old code", "remove", "delete", "obsolete"
        )

        // Если в запросе не запрашиваются TODO/FIXME, то считаем их устаревшими
        val requestWantsTodos = request.contains("todo") || request.contains("fixme")

        return outdatedMarkers.any { marker ->
            content.contains(marker, ignoreCase = true) && !requestWantsTodos
        }
    }

    /**
     * Проверяет качество контента
     */
    private fun isQualityCode(content: String): Boolean {
        // Проверяем на мусорный контент
        val junkPatterns = listOf(
            Regex("[a-zA-Z]\\s*[a-zA-Z]\\s*[a-zA-Z]\\s*[a-zA-Z]"), // Слишком короткие слова
            Regex("[{}\\[\\]]\\s*[{}\\[\\]]\\s*[{}\\[\\]]"), // Пустые скобки
            Regex("(\\s*\\n){3,}"), // Много пустых строк
            Regex("^[\\s\\W\\d_]+$") // Только символы без букв
        )

        // Проверяем минимальное содержательное содержимое
        val hasMeaningfulContent = content.length > 50 &&
                                   content.contains(Regex("[a-zA-Zа-яА-Я]{4,}")) // Есть слова длиной > 3

        return !junkPatterns.any { it.containsMatchIn(content) } && hasMeaningfulContent
    }

/**
 * Результат анализа файлов найденных через RAG
 */
data class FileAnalysisResult(
    /** Список релевантных файлов */
    val relevantFiles: List<String>,

    /** Рекомендуемые дополнительные шаги */
    val suggestedSteps: List<String>,

    /** Корректировка сложности (-0.5 до +0.5) */
    val complexityAdjustment: Double,

    /** Обоснование анализа */
    val reasoning: String
)