package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A2A-enhanced агент для анализа качества кода с агрегацией результатов
 *
 * Отличия от обычного CodeQualityToolAgent:
 * - Агрегирует результаты от других анализирующих агентов через A2A
 * - Подписывается на события от BugDetectionToolAgent и других
 * - Создает комплексный отчет о качестве кода
 * - Публикует агрегированные метрики через A2A
 *
 * Capabilities:
 * - code_quality_analysis - собственный анализ качества
 * - a2a_result_aggregation - агрегация результатов от других агентов
 * - a2a_metrics_publishing - публикация метрик через A2A
 * - cross_agent_correlation - корреляция результатов между агентами
 */
class A2ACodeQualityToolAgent(
    private val messageBus: MessageBus,
    private val agentRegistry: A2AAgentRegistry
) : BaseToolAgent(
    agentType = AgentType.CODE_QUALITY,
    toolCapabilities = setOf(
        "code_quality_analysis",
        "code_smell_detection", 
        "complexity_analysis",
        "a2a_result_aggregation",
        "a2a_metrics_publishing",
        "cross_agent_correlation"
    )
), A2AAgent {

    private val agentId: String = "code-quality-a2a-${hashCode()}"
    override val a2aAgentId: String = agentId

    // A2A message types this agent supports
    override val supportedMessageTypes: Set<String> = setOf(
        "CODE_QUALITY_REQUEST",
        "ANALYSIS_RESULT_AGGREGATION",
        "METRICS_REQUEST"
    )

    override val publishedEventTypes: Set<String> = setOf(
        "CODE_QUALITY_STARTED",
        "CODE_QUALITY_COMPLETED", 
        "QUALITY_METRICS_AVAILABLE",
        "AGGREGATED_ANALYSIS_READY"
    )

    override val messageProcessingPriority: Int = 2
    override val maxConcurrentMessages: Int = 5

    // Агрегация результатов от других агентов
    private val aggregatedResults = ConcurrentHashMap<String, AnalysisResult>()
    private val analysisCorrelations = ConcurrentHashMap<String, MutableList<String>>()
    
    // Поток событий для real-time агрегации
    private val _aggregationEvents = MutableSharedFlow<AggregationEvent>()
    val aggregationEvents: SharedFlow<AggregationEvent> = _aggregationEvents.asSharedFlow()

    // Корутина для обработки агрегации
    private val aggregationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        logger.info("Initializing A2A Code Quality Agent: $agentId")
        startAggregationProcessing()
    }

    override fun getDescription(): String {
        return "A2A-enhanced агент для анализа качества кода с агрегацией результатов от других агентов"
    }

    override fun validateInput(input: StepInput): ValidationResult {
        val files = input.getList<String>("files")
        val projectPath = input.get<String>("project_path")
        val aggregateResults = input.getBoolean("aggregate_results") ?: false

        if (files.isNullOrEmpty() && projectPath.isNullOrBlank() && !aggregateResults) {
            return ValidationResult.failure("Either 'files', 'project_path', or 'aggregate_results=true' must be provided")
        }

        return ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        logger.info("Starting A2A Code Quality analysis")

        // Публикуем событие о начале анализа
        publishA2AEvent(
            eventType = "CODE_QUALITY_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = agentId,
                requestId = step.id,
                timestamp = System.currentTimeMillis()
            )
        )

        return try {
            val aggregateResults = step.input.getBoolean("aggregate_results") ?: false
            val files = step.input.getList<String>("files") ?: emptyList()

            val qualityResults = if (files.isNotEmpty()) {
                // Выполняем собственный анализ качества
                performOwnQualityAnalysis(files, step)
            } else {
                emptyList()
            }

            val aggregatedResults = if (aggregateResults) {
                // Агрегируем результаты от других агентов
                aggregateAnalysisResults(step.id)
            } else {
                emptyMap()
            }

            // Создаем комплексный отчет
            val comprehensiveReport = createComprehensiveReport(qualityResults, aggregatedResults)

            // Публикуем агрегированные результаты
            publishAggregatedResults(comprehensiveReport, step.id)

            // Публикуем событие о завершении
            publishA2AEvent(
                eventType = "CODE_QUALITY_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = agentId,
                    requestId = step.id,
                    timestamp = System.currentTimeMillis(),
                    result = "Quality analysis completed with ${comprehensiveReport.totalIssues} total issues"
                )
            )

            StepResult.success(
                output = StepOutput.of(
                    "quality_findings" to qualityResults,
                    "aggregated_results" to aggregatedResults,
                    "comprehensive_report" to comprehensiveReport,
                    "total_issues" to comprehensiveReport.totalIssues,
                    "quality_score" to comprehensiveReport.qualityScore,
                    "a2a_enabled" to true
                )
            )

        } catch (e: Exception) {
            logger.error("Error in A2A Code Quality analysis", e)
            
            publishA2AEvent(
                eventType = "CODE_QUALITY_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "FAILED",
                    agentId = agentId,
                    requestId = step.id,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            )

            StepResult.error("A2A Code Quality analysis failed: ${e.message}")
        }
    }

    /**
     * Выполняет собственный анализ качества кода
     */
    private suspend fun performOwnQualityAnalysis(files: List<String>, step: ToolPlanStep): List<Map<String, Any>> {
        val checkComplexity = step.input.getBoolean("check_complexity") ?: true
        val maxComplexity = step.input.getInt("max_complexity") ?: 10
        
        logger.info("Performing own quality analysis on ${files.size} files")

        val findings = mutableListOf<Map<String, Any>>()
        val metrics = mutableMapOf<String, Any>()

        var totalLines = 0
        var totalMethods = 0
        var totalClasses = 0

        for (filePath in files) {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                logger.warn("File does not exist: $filePath")
                continue
            }

            val fileAnalysis = analyzeFile(file, checkComplexity, maxComplexity)
            
            // Конвертируем findings в Map format
            val fileFindingMaps = fileAnalysis.findings.map { finding ->
                mapOf<String, Any>(
                    "file" to finding.file,
                    "line" to finding.line,
                    "severity" to finding.severity.name,
                    "category" to finding.category,
                    "message" to finding.message,
                    "suggestion" to (finding.suggestion ?: ""),
                    "agent_type" to "CODE_QUALITY",
                    "analysis_type" to "quality_analysis"
                )
            }
            
            findings.addAll(fileFindingMaps)
            totalLines += fileAnalysis.lines
            totalMethods += fileAnalysis.methods
            totalClasses += fileAnalysis.classes
        }

        metrics["total_lines"] = totalLines
        metrics["total_methods"] = totalMethods
        metrics["total_classes"] = totalClasses
        metrics["avg_lines_per_method"] = if (totalMethods > 0) totalLines / totalMethods else 0

        logger.info("Own quality analysis completed: ${findings.size} issues found")
        return findings
    }

    /**
     * Агрегирует результаты анализа от других агентов через A2A
     */
    private suspend fun aggregateAnalysisResults(requestId: String): Map<String, Any> {
        logger.info("Aggregating analysis results from other A2A agents")

        val aggregationResults = mutableMapOf<String, Any>()
        val timeout = 30000L // 30 seconds timeout

        try {
            // Запрашиваем результаты от BugDetectionToolAgent
            val bugResults = requestAnalysisResults(AgentType.BUG_DETECTION, "BUG_ANALYSIS_REQUEST", timeout)
            if (bugResults.isNotEmpty()) {
                aggregationResults["bug_detection"] = bugResults
                logger.info("Aggregated ${bugResults.size} bug detection results")
            }

            // Можем добавить запросы к другим агентам
            // val architectureResults = requestAnalysisResults(AgentType.ARCHITECTURE_ANALYSIS, "ARCHITECTURE_ANALYSIS_REQUEST", timeout)
            // val performanceResults = requestAnalysisResults(AgentType.PERFORMANCE_ANALYZER, "PERFORMANCE_ANALYSIS_REQUEST", timeout)

            // Корреляция результатов
            val correlatedIssues = correlateAnalysisResults(aggregationResults)
            if (correlatedIssues.isNotEmpty()) {
                aggregationResults["correlated_issues"] = correlatedIssues
                logger.info("Found ${correlatedIssues.size} correlated issues across agents")
            }

            // Создаем сводную статистику
            val summary = createAggregationSummary(aggregationResults)
            aggregationResults["summary"] = summary

        } catch (e: Exception) {
            logger.error("Error aggregating analysis results", e)
            aggregationResults["error"] = "Aggregation failed: ${e.message}"
        }

        return aggregationResults
    }

    /**
     * Запрашивает результаты анализа от конкретного типа агента
     */
    private suspend fun requestAnalysisResults(
        agentType: AgentType, 
        requestType: String, 
        timeoutMs: Long
    ): List<Map<String, Any>> {
        return try {
            val agents = agentRegistry.getAgentsByType(agentType)
            if (agents.isEmpty()) {
                logger.warn("No agents of type $agentType found in registry")
                return emptyList()
            }

            val agent = agents.first()

            val request = AgentMessage.Request(
                senderId = agentId,
                messageType = requestType,
                payload = MessagePayload.CustomPayload(
                    type = requestType,
                    data = mapOf(
                        "request_source" to "CODE_QUALITY_AGGREGATION",
                        "include_details" to true
                    )
                ),
                timeoutMs = timeoutMs
            )

            val response = withTimeout(timeoutMs + 5000) {
                messageBus.requestResponse(request)
            }

            if (response.success) {
                when (val payload = response.payload) {
                    is MessagePayload.CodeAnalysisPayload -> {
                        payload.findings.map { finding ->
                            mapOf<String, Any>(
                                "file" to finding.file,
                                "line" to (finding.line ?: 0),
                                "severity" to finding.severity,
                                "rule" to finding.rule,
                                "message" to finding.message,
                                "suggestion" to finding.suggestion,
                                "agent_type" to agentType.name,
                                "analysis_type" to requestType
                            )
                        }
                    }
                    else -> {
                        logger.warn("Unexpected payload type from $agentType: ${payload::class.simpleName}")
                        emptyList()
                    }
                }
            } else {
                logger.error("Request to $agentType failed: ${response.error}")
                emptyList()
            }

        } catch (e: Exception) {
            logger.error("Error requesting results from $agentType", e)
            emptyList()
        }
    }

    /**
     * Корреляция результатов между разными агентами
     */
    private fun correlateAnalysisResults(aggregationResults: Map<String, Any>): List<Map<String, Any>> {
        val correlatedIssues = mutableListOf<Map<String, Any>>()

        try {
            // Группируем результаты по файлам
            val resultsByFile = mutableMapOf<String, MutableList<Map<String, Any>>>()

            aggregationResults.values.forEach { results ->
                if (results is List<*>) {
                    results.filterIsInstance<Map<String, Any>>().forEach { result ->
                        val file = result["file"] as? String ?: return@forEach
                        resultsByFile.getOrPut(file) { mutableListOf() }.add(result)
                    }
                }
            }

            // Ищем корреляции в каждом файле
            resultsByFile.forEach { (file, issues) ->
                if (issues.size > 1) {
                    // Группируем по строкам
                    val issuesByLine = issues.groupBy { it["line"] as? Int ?: 0 }
                    
                    issuesByLine.forEach { (line, lineIssues) ->
                        if (lineIssues.size > 1) {
                            // Найдена корреляция - несколько агентов нашли проблемы в одной строке
                            correlatedIssues.add(mapOf(
                                "file" to file,
                                "line" to line,
                                "correlation_type" to "SAME_LINE_MULTIPLE_AGENTS",
                                "issues" to lineIssues,
                                "severity" to "HIGH", // Повышаем приоритет коррелированных проблем
                                "message" to "Multiple analysis agents found issues in the same location",
                                "agents_involved" to lineIssues.map { it["agent_type"] }.distinct()
                            ))
                        }
                    }
                }
            }

        } catch (e: Exception) {
            logger.error("Error correlating analysis results", e)
        }

        return correlatedIssues
    }

    /**
     * Создает сводную статистику агрегации
     */
    private fun createAggregationSummary(aggregationResults: Map<String, Any>): Map<String, Any> {
        var totalIssues = 0
        val issuesByAgent = mutableMapOf<String, Int>()
        val issuesBySeverity = mutableMapOf<String, Int>()

        aggregationResults.values.forEach { results ->
            if (results is List<*>) {
                results.filterIsInstance<Map<String, Any>>().forEach { result ->
                    totalIssues++
                    
                    val agentType = result["agent_type"] as? String ?: "UNKNOWN"
                    issuesByAgent[agentType] = (issuesByAgent[agentType] ?: 0) + 1
                    
                    val severity = result["severity"] as? String ?: "UNKNOWN"
                    issuesBySeverity[severity] = (issuesBySeverity[severity] ?: 0) + 1
                }
            }
        }

        return mapOf(
            "total_issues" to totalIssues,
            "issues_by_agent" to issuesByAgent,
            "issues_by_severity" to issuesBySeverity,
            "agents_participated" to issuesByAgent.keys.toList(),
            "aggregation_timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Создает комплексный отчет о качестве
     */
    private fun createComprehensiveReport(
        qualityResults: List<Map<String, Any>>,
        aggregatedResults: Map<String, Any>
    ): ComprehensiveQualityReport {
        val aggregatedTotal: Int = ((aggregatedResults["summary"] as? Map<String, Any>)?.get("total_issues") as? Int) ?: 0
        val totalIssues = qualityResults.size + aggregatedTotal

        val qualityScore = calculateQualityScore(qualityResults, aggregatedResults)

        return ComprehensiveQualityReport(
            totalIssues = totalIssues,
            qualityScore = qualityScore,
            ownAnalysisIssues = qualityResults.size,
            aggregatedIssues = (aggregatedResults["summary"] as? Map<String, Any>)?.get("total_issues") as? Int ?: 0,
            correlatedIssues = (aggregatedResults["correlated_issues"] as? List<*>)?.size ?: 0,
            participatingAgents = (aggregatedResults["summary"] as? Map<String, Any>)?.get("agents_participated") as? List<String> ?: emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Вычисляет общий балл качества кода
     */
    private fun calculateQualityScore(
        qualityResults: List<Map<String, Any>>,
        aggregatedResults: Map<String, Any>
    ): Double {
        // Базовый балл 100
        var score = 100.0

        // Снижаем балл за каждую проблему качества
        qualityResults.forEach { issue ->
            when (issue["severity"] as? String) {
                "HIGH" -> score -= 5.0
                "MEDIUM" -> score -= 2.0
                "LOW" -> score -= 1.0
            }
        }

        // Снижаем балл за агрегированные проблемы
        val aggregatedIssues = (aggregatedResults["summary"] as? Map<String, Any>)?.get("issues_by_severity") as? Map<String, Int>
        aggregatedIssues?.forEach { (severity, count) ->
            when (severity) {
                "CRITICAL" -> score -= count * 10.0
                "HIGH" -> score -= count * 5.0
                "MEDIUM" -> score -= count * 2.0
                "LOW" -> score -= count * 1.0
            }
        }

        // Дополнительно снижаем за коррелированные проблемы (они более серьезные)
        val correlatedCount = (aggregatedResults["correlated_issues"] as? List<*>)?.size ?: 0
        score -= correlatedCount * 15.0

        return maxOf(0.0, score) // Не может быть меньше 0
    }

    /**
     * Публикует агрегированные результаты через A2A
     */
    private suspend fun publishAggregatedResults(report: ComprehensiveQualityReport, requestId: String) {
        logger.info("Publishing comprehensive quality report via A2A")

        val metricsPayload = MessagePayload.CustomPayload(
            type = "COMPREHENSIVE_QUALITY_METRICS",
            data = mapOf(
                "total_issues" to report.totalIssues,
                "quality_score" to report.qualityScore,
                "own_analysis_issues" to report.ownAnalysisIssues,
                "aggregated_issues" to report.aggregatedIssues,
                "correlated_issues" to report.correlatedIssues,
                "participating_agents" to report.participatingAgents,
                "timestamp" to report.timestamp,
                "request_id" to requestId
            )
        )

        publishA2AEvent(
            eventType = "QUALITY_METRICS_AVAILABLE",
            payload = metricsPayload
        )

        publishA2AEvent(
            eventType = "AGGREGATED_ANALYSIS_READY",
            payload = MessagePayload.CustomPayload(
                type = "AGGREGATED_ANALYSIS_COMPLETE",
                data = mapOf(
                    "report" to report,
                    "request_id" to requestId
                )
            )
        )
    }

    /**
     * Запускает обработку агрегации в фоне
     */
    private fun startAggregationProcessing() {
        aggregationScope.launch {
            aggregationEvents.collect { event ->
                try {
                    processAggregationEvent(event)
                } catch (e: Exception) {
                    logger.error("Error processing aggregation event", e)
                }
            }
        }
    }

    private suspend fun processAggregationEvent(event: AggregationEvent) {
        when (event.type) {
            "NEW_ANALYSIS_RESULT" -> {
                // Обрабатываем новый результат анализа
                aggregatedResults[event.sourceAgent] = event.result
                logger.debug("Added analysis result from ${event.sourceAgent}")
            }
            "CORRELATION_REQUEST" -> {
                // Запрос на корреляцию результатов
                val correlations = correlateAnalysisResults(aggregatedResults.mapValues { listOf(it.value) })
                logger.info("Generated ${correlations.size} correlations")
            }
        }
    }

    // Копируем методы анализа из оригинального CodeQualityToolAgent
    private fun analyzeFile(file: File, checkComplexity: Boolean, maxComplexity: Int): FileAnalysis {
        val findings = mutableListOf<Finding>()

        try {
            val lines = file.readLines()
            val nonEmptyLines = lines.count { it.trim().isNotEmpty() }
            var methodCount = 0
            var classCount = 0

            lines.forEachIndexed { index, line ->
                val lineNumber = index + 1
                val trimmed = line.trim()

                // Подсчет классов и методов
                if (trimmed.startsWith("class ") || trimmed.startsWith("data class ") ||
                    trimmed.startsWith("object ") || trimmed.startsWith("interface ")
                ) {
                    classCount++
                }

                if (trimmed.startsWith("fun ") || trimmed.contains(" fun ")) {
                    methodCount++
                }

                // Long lines
                if (line.length > 120) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.LOW,
                            category = "long_line",
                            message = "Слишком длинная строка (${line.length} символов)",
                            suggestion = "Разбейте строку на несколько для улучшения читаемости"
                        )
                    )
                }

                // Magic numbers
                val magicNumberRegex = Regex("""[^a-zA-Z_]\d{2,}[^a-zA-Z_]""")
                if (magicNumberRegex.containsMatchIn(line) && !trimmed.startsWith("//")) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.LOW,
                            category = "magic_number",
                            message = "Использование магического числа",
                            suggestion = "Вынесите число в именованную константу"
                        )
                    )
                }

                // Deep nesting
                val indentLevel = line.takeWhile { it == ' ' || it == '\t' }.length / 4
                if (indentLevel > 4) {
                    findings.add(
                        Finding(
                            file = file.absolutePath,
                            line = lineNumber,
                            severity = Severity.MEDIUM,
                            category = "deep_nesting",
                            message = "Слишком глубокая вложенность (уровень $indentLevel)",
                            suggestion = "Рефакторинг для уменьшения вложенности"
                        )
                    )
                }
            }

            return FileAnalysis(
                findings = findings,
                lines = nonEmptyLines,
                methods = methodCount,
                classes = classCount
            )

        } catch (e: Exception) {
            logger.error("Error analyzing file ${file.absolutePath}", e)
            return FileAnalysis(emptyList(), 0, 0, 0)
        }
    }

    /**
     * Публикует A2A событие
     */
    private suspend fun publishA2AEvent(eventType: String, payload: MessagePayload) {
        try {
            val event = AgentMessage.Event(
                senderId = agentId,
                eventType = eventType,
                payload = payload
            )

            messageBus.publish(event)
            logger.debug("Published A2A event: $eventType")

        } catch (e: Exception) {
            logger.error("Error publishing A2A event: $eventType", e)
        }
    }

    // A2AAgent interface implementation
    override suspend fun handleA2AMessage(
        message: AgentMessage,
        messageBus: MessageBus
    ): AgentMessage? {
        return when (message) {
            is AgentMessage.Request -> handleA2ARequest(message)
            is AgentMessage.Event -> {
                handleA2AEvent(message)
                null
            }
            else -> null
        }
    }

    private suspend fun handleA2ARequest(request: AgentMessage.Request): AgentMessage.Response {
        return try {
            when (request.payload) {
                is MessagePayload.CustomPayload -> {
                    when (request.payload.type) {
                        "CODE_QUALITY_REQUEST" -> handleCodeQualityRequest(request)
                        "METRICS_REQUEST" -> handleMetricsRequest(request)
                        else -> createErrorResponse(request.id, "Unsupported request type: ${request.payload.type}")
                    }
                }
                else -> createErrorResponse(request.id, "Unsupported payload type: ${request.payload::class.simpleName}")
            }
        } catch (e: Exception) {
            logger.error("Error handling A2A request", e)
            createErrorResponse(request.id, "Internal error: ${e.message}")
        }
    }

    private suspend fun handleCodeQualityRequest(request: AgentMessage.Request): AgentMessage.Response {
        val payload = request.payload as MessagePayload.CustomPayload
        val files = payload.data["files"] as? List<String> ?: emptyList()

        if (files.isEmpty()) {
            return createErrorResponse(request.id, "No files provided for quality analysis")
        }

        // Создаем временный ToolPlanStep для анализа
        val tempStep = ToolPlanStep(
            id = "a2a-${request.id}",
            description = "Run code quality analysis via A2A request",
            agentType = AgentType.CODE_QUALITY,
            input = StepInput(mapOf("files" to files))
        )

        val results = performOwnQualityAnalysis(files, tempStep)

        return AgentMessage.Response(
            senderId = agentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "CODE_QUALITY_RESULTS",
                data = mapOf(
                    "findings" to results,
                    "total_issues" to results.size,
                    "files_analyzed" to files.size
                )
            )
        )
    }

    private fun handleMetricsRequest(request: AgentMessage.Request): AgentMessage.Response {
        val currentMetrics = mapOf(
            "aggregated_results_count" to aggregatedResults.size,
            "correlation_count" to analysisCorrelations.size,
            "agent_id" to agentId,
            "timestamp" to System.currentTimeMillis()
        )

        return AgentMessage.Response(
            senderId = agentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "QUALITY_METRICS",
                data = currentMetrics
            )
        )
    }

    private fun handleA2AEvent(event: AgentMessage.Event) {
        when (event.eventType) {
            "BUG_FINDINGS_AVAILABLE" -> {
                logger.info("Received bug findings from BugDetectionToolAgent")
                // Добавляем в агрегацию
                aggregationScope.launch {
                    _aggregationEvents.emit(
                        AggregationEvent(
                            type = "NEW_ANALYSIS_RESULT",
                            sourceAgent = event.senderId,
                            result = AnalysisResult(event.payload, System.currentTimeMillis())
                        )
                    )
                }
            }
            "PROJECT_STRUCTURE_UPDATED" -> {
                logger.info("Received project structure update - may need to re-analyze")
            }
            else -> {
                logger.debug("Received A2A event: ${event.eventType}")
            }
        }
    }

    private fun createErrorResponse(requestId: String, error: String): AgentMessage.Response {
        return AgentMessage.Response(
            senderId = agentId,
            requestId = requestId,
            success = false,
            payload = MessagePayload.ErrorPayload(error = error)
        )
    }

    // Удалены устаревшие override-методы, не предусмотренные интерфейсом A2AAgent

    override fun dispose() {
        super.dispose()
        aggregationScope.cancel()
    }

    // Data classes для агрегации
    data class AnalysisResult(
        val payload: MessagePayload,
        val timestamp: Long
    )

    data class AggregationEvent(
        val type: String,
        val sourceAgent: String,
        val result: AnalysisResult
    )

    data class ComprehensiveQualityReport(
        val totalIssues: Int,
        val qualityScore: Double,
        val ownAnalysisIssues: Int,
        val aggregatedIssues: Int,
        val correlatedIssues: Int,
        val participatingAgents: List<String>,
        val timestamp: Long
    )

    data class FileAnalysis(
        val findings: List<Finding>,
        val lines: Int,
        val methods: Int,
        val classes: Int
    )

    companion object {
        /**
         * Создает A2A-enhanced CodeQualityToolAgent
         */
        fun create(
            messageBus: MessageBus,
            agentRegistry: A2AAgentRegistry
        ): A2ACodeQualityToolAgent {
            return A2ACodeQualityToolAgent(messageBus, agentRegistry)
        }
    }
}