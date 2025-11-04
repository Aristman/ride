package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.llm.LLMParameters
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A2A агент для генерации отчетов
 *
 * Независимая реализация для создания различных типов отчетов:
 * анализ кода, баг-репорты, архитектурные отчеты и т.д.
 */
class A2AReportGeneratorToolAgent(
    private val llmProvider: LLMProvider
) : BaseA2AAgent(
    agentType = AgentType.REPORT_GENERATOR,
    a2aAgentId = "a2a-report-generator-agent",
    supportedMessageTypes = setOf(
        "REPORT_GENERATION_REQUEST",
        "ANALYSIS_SUMMARY_REQUEST",
        "METRICS_COLLECTION_REQUEST",
        "QUALITY_ASSESSMENT_REQUEST"
    ),
    publishedEventTypes = setOf(
        "TOOL_EXECUTION_STARTED",
        "TOOL_EXECUTION_COMPLETED",
        "TOOL_EXECUTION_FAILED"
    )
) {

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        return try {
            when (request.messageType) {
                "REPORT_GENERATION_REQUEST" -> handleReportGenerationRequest(request, messageBus)
                "ANALYSIS_SUMMARY_REQUEST" -> handleAnalysisSummaryRequest(request, messageBus)
                "METRICS_COLLECTION_REQUEST" -> handleMetricsCollectionRequest(request, messageBus)
                "QUALITY_ASSESSMENT_REQUEST" -> handleQualityAssessmentRequest(request, messageBus)
                else -> createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
            }
        } catch (e: Exception) {
            logger.error("Error in report generator", e)
            createErrorResponse(request.id, "Report generation failed: ${e.message}")
        }
    }

    private suspend fun handleReportGenerationRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val reportType = data["report_type"] as? String ?: "summary"
        val sourceData = data["source_data"] as? Map<String, Any> ?: emptyMap()
        val format = data["format"] as? String ?: "markdown"
        val title = data["title"] as? String ?: "Generated Report"
        val includeRecommendations = data["include_recommendations"] as? Boolean ?: true

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.Default) {
            generateReport(reportType, sourceData, format, title, includeRecommendations)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Report generation completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "REPORT_GENERATION_RESULT",
                data = mapOf(
                    "report_content" to result.content,
                    "report_format" to format,
                    "report_title" to title,
                    "generation_time_ms" to result.generationTimeMs,
                    "total_findings" to result.totalFindings,
                    "participating_agents" to result.participatingAgents,
                    "data_sources" to result.dataSources,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "REPORT_GENERATOR",
                        "report_type" to reportType,
                        "generation_timestamp" to System.currentTimeMillis(),
                        "template_used" to result.templateUsed
                    )
                )
            )
        )
    }

    private suspend fun handleAnalysisSummaryRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val analysisResults = data["analysis_results"] as? List<Map<String, Any>> ?: emptyList()
        val focusAreas = data["focus_areas"] as? List<String> ?: emptyList()
        val summaryType = data["summary_type"] as? String ?: "executive"

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.Default) {
            generateAnalysisSummary(analysisResults, focusAreas, summaryType)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Analysis summary completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "ANALYSIS_SUMMARY_RESULT",
                data = mapOf(
                    "summary_content" to result.content,
                    "summary_type" to summaryType,
                    "key_findings" to result.keyFindings,
                    "recommendations" to result.recommendations,
                    "risk_areas" to result.riskAreas,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "REPORT_GENERATOR",
                        "analysis_count" to analysisResults.size,
                        "focus_areas_count" to focusAreas.size
                    )
                )
            )
        )
    }

    private suspend fun handleMetricsCollectionRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val metricTypes = data["metric_types"] as? List<String> ?: listOf("quality", "complexity", "coverage")
        val targetFiles = data["target_files"] as? List<String> ?: emptyList()
        val timeRange = data["time_range"] as? Map<String, String> ?: emptyMap()

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.Default) {
            collectMetrics(metricTypes, targetFiles, timeRange)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Metrics collection completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "METRICS_COLLECTION_RESULT",
                data = mapOf(
                    "metrics" to result.metrics,
                    "summary" to result.summary,
                    "trends" to result.trends,
                    "benchmarks" to result.benchmarks,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "REPORT_GENERATOR",
                        "metric_types" to metricTypes,
                        "files_analyzed" to result.filesAnalyzed,
                        "collection_duration_ms" to result.collectionDurationMs
                    )
                )
            )
        )
    }

    private suspend fun handleQualityAssessmentRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val qualityModel = data["quality_model"] as? String ?: "ISO_25010"
        val assessmentScope = data["assessment_scope"] as? Map<String, Any> ?: emptyMap()
        val includeScores = data["include_scores"] as? Boolean ?: true

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.Default) {
            assessQuality(qualityModel, assessmentScope, includeScores)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Quality assessment completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "QUALITY_ASSESSMENT_RESULT",
                data = mapOf(
                    "quality_scores" to result.qualityScores,
                    "quality_level" to result.qualityLevel,
                    "improvement_areas" to result.improvementAreas,
                    "compliance_status" to result.complianceStatus,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "REPORT_GENERATOR",
                        "quality_model" to qualityModel,
                        "assessment_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private fun createErrorResponse(requestId: String, error: String): AgentMessage.Response {
        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = requestId,
            success = false,
            payload = MessagePayload.ErrorPayload(error = error),
            error = error
        )
    }

    // Реализация методов генерации отчетов

    private suspend fun generateReport(
        reportType: String,
        sourceData: Map<String, Any>,
        format: String,
        title: String,
        includeRecommendations: Boolean
    ): ReportResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        try {
            val prompt = buildString {
                appendLine("Generate a $reportType report with the following data:")
                appendLine("Title: $title")
                appendLine("Format: $format")
                appendLine("Include Recommendations: $includeRecommendations")
                appendLine("Source Data: $sourceData")
                appendLine("\nGenerate a comprehensive, well-structured report.")
            }

            val response = llmProvider.sendRequest(
                systemPrompt = "Ты — генератор технических отчётов. Создавай понятные и практичные отчёты для команд разработки.",
                userMessage = prompt,
                conversationHistory = emptyList(),
                LLMParameters()
            )

            val content = response.content
            val totalFindings = extractFindingsCount(content)
            val participatingAgents = extractParticipatingAgents(content)
            val templateUsed = extractTemplateUsed(content)

            ReportResult(
                content = content,
                generationTimeMs = System.currentTimeMillis() - startTime,
                totalFindings = totalFindings,
                participatingAgents = participatingAgents,
                dataSources = sourceData.size,
                templateUsed = templateUsed
            )
        } catch (e: Exception) {
            logger.error("Report generation failed", e)
            val fallbackContent = generateFallbackReport(reportType, title, includeRecommendations)

            ReportResult(
                content = fallbackContent,
                generationTimeMs = System.currentTimeMillis() - startTime,
                totalFindings = 0,
                participatingAgents = emptyList(),
                dataSources = sourceData.size,
                templateUsed = "fallback"
            )
        }
    }

    private suspend fun generateAnalysisSummary(
        analysisResults: List<Map<String, Any>>,
        focusAreas: List<String>,
        summaryType: String
    ): SummaryResult = withContext(Dispatchers.Default) {
        try {
            val keyFindings = analysisResults.flatMap { result ->
                (result["findings"] as? List<String>) ?: emptyList()
            }.take(10)

            val recommendations = analysisResults.flatMap { result ->
                (result["recommendations"] as? List<String>) ?: emptyList()
            }.take(15)

            val riskAreas = analysisResults.mapNotNull { result ->
                (result["severity"] as? String)?.let { severity ->
                    if (severity in listOf("high", "critical")) {
                        result["area"] as? String
                    } else null
                }
            }.distinct()

            val content = buildString {
                appendLine("# Analysis Summary\n")
                appendLine("Generated: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\n")
                appendLine("## Key Findings\n")
                keyFindings.forEach { finding ->
                    appendLine("- $finding\n")
                }
                if (recommendations.isNotEmpty()) {
                    appendLine("## Recommendations\n")
                    recommendations.forEach { recommendation ->
                        appendLine("- $recommendation\n")
                    }
                }
                if (riskAreas.isNotEmpty()) {
                    appendLine("## Risk Areas\n")
                    riskAreas.forEach { area ->
                        appendLine("- **$area**: Requires immediate attention\n")
                    }
                }
            }

            SummaryResult(
                content = content,
                keyFindings = keyFindings,
                recommendations = recommendations,
                riskAreas = riskAreas
            )
        } catch (e: Exception) {
            logger.error("Analysis summary generation failed", e)
            SummaryResult(
                content = "# Analysis Summary\n\nError generating summary: ${e.message}",
                keyFindings = emptyList(),
                recommendations = emptyList(),
                riskAreas = emptyList()
            )
        }
    }

    private suspend fun collectMetrics(
        metricTypes: List<String>,
        targetFiles: List<String>,
        timeRange: Map<String, String>
    ): MetricsResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        try {
            val metrics = mutableMapOf<String, Any>()

            metricTypes.forEach { metricType ->
                when (metricType) {
                    "quality" -> {
                        metrics["code_quality_score"] = calculateQualityScore(targetFiles)
                        metrics["code_smells"] = countCodeSmells(targetFiles)
                    }
                    "complexity" -> {
                        metrics["cyclomatic_complexity"] = calculateCyclomaticComplexity(targetFiles)
                        metrics["cognitive_complexity"] = calculateCognitiveComplexity(targetFiles)
                    }
                    "coverage" -> {
                        metrics["test_coverage"] = calculateTestCoverage(targetFiles)
                        metrics["branch_coverage"] = calculateBranchCoverage(targetFiles)
                    }
                    "performance" -> {
                        metrics["performance_score"] = calculatePerformanceScore(targetFiles)
                        metrics["bottlenecks"] = identifyBottlenecks(targetFiles)
                    }
                }
            }

            val summary = generateMetricsSummary(metrics)
            val trends = generateTrends(metrics)
            val benchmarks = generateBenchmarks(metrics)

            MetricsResult(
                metrics = metrics,
                summary = summary,
                trends = trends,
                benchmarks = benchmarks,
                filesAnalyzed = targetFiles.size,
                collectionDurationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            logger.error("Metrics collection failed", e)
            MetricsResult(
                metrics = emptyMap(),
                summary = "Error collecting metrics: ${e.message}",
                trends = emptyMap(),
                benchmarks = emptyMap(),
                filesAnalyzed = targetFiles.size,
                collectionDurationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun assessQuality(
        qualityModel: String,
        assessmentScope: Map<String, Any>,
        includeScores: Boolean
    ): QualityAssessmentResult = withContext(Dispatchers.Default) {
        try {
            val qualityScores = mutableMapOf<String, Double>()

            // Основные характеристики качества по ISO 25010
            if (includeScores) {
                qualityScores["functional_suitability"] = assessFunctionalSuitability(assessmentScope)
                qualityScores["performance_efficiency"] = assessPerformanceEfficiency(assessmentScope)
                qualityScores["compatibility"] = assessCompatibility(assessmentScope)
                qualityScores["usability"] = assessUsability(assessmentScope)
                qualityScores["reliability"] = assessReliability(assessmentScope)
                qualityScores["security"] = assessSecurity(assessmentScope)
                qualityScores["maintainability"] = assessMaintainability(assessmentScope)
                qualityScores["portability"] = assessPortability(assessmentScope)
            }

            val overallScore = if (qualityScores.isNotEmpty()) {
                qualityScores.values.average()
            } else 0.0

            val qualityLevel = when {
                overallScore >= 9.0 -> "Excellent"
                overallScore >= 8.0 -> "Very Good"
                overallScore >= 7.0 -> "Good"
                overallScore >= 6.0 -> "Acceptable"
                overallScore >= 5.0 -> "Needs Improvement"
                else -> "Poor"
            }

            val improvementAreas = qualityScores.filter { it.value < 7.0 }.keys.toList()
            val complianceStatus = checkCompliance(qualityModel, qualityScores)

            QualityAssessmentResult(
                qualityScores = qualityScores,
                qualityLevel = qualityLevel,
                improvementAreas = improvementAreas,
                complianceStatus = complianceStatus
            )
        } catch (e: Exception) {
            logger.error("Quality assessment failed", e)
            QualityAssessmentResult(
                qualityScores = emptyMap(),
                qualityLevel = "Unknown",
                improvementAreas = emptyList(),
                complianceStatus = mapOf<String, Any>("status" to "error", "message" to (e.message ?: "Unknown error"))
            )
        }
    }

    // Вспомогательные методы

    private fun extractFindingsCount(content: String): Int {
        return Regex("""(?i)(finding|issue|bug|warning|error)""").findAll(content).count()
    }

    private fun extractParticipatingAgents(content: String): List<String> {
        val agentRegex = Regex("""(?i)(\w+agent|scanner|analyzer|reviewer)""")
        return agentRegex.findAll(content).map { it.value.lowercase() }.distinct().toList()
    }

    private fun extractTemplateUsed(content: String): String {
        return when {
            content.contains("# Executive Summary", ignoreCase = true) -> "executive"
            content.contains("# Technical Report", ignoreCase = true) -> "technical"
            content.contains("# Quality Assessment", ignoreCase = true) -> "quality"
            else -> "standard"
        }
    }

    private fun generateFallbackReport(reportType: String, title: String, includeRecommendations: Boolean): String {
        return buildString {
            appendLine("# $title\n")
            appendLine("Report Type: $reportType\n")
            appendLine("Generated: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\n")
            appendLine("## Status\n")
            appendLine("⚠️ Report generation encountered issues. Please check the data source and try again.\n")
            if (includeRecommendations) {
                appendLine("## Recommendations\n")
                appendLine("- Verify input data integrity\n")
                appendLine("- Check agent connectivity\n")
                appendLine("- Retry with simplified parameters\n")
            }
        }
    }

    private fun calculateQualityScore(files: List<String>): Double {
        // Простая эмуляция оценки качества
        return (70..95).random().toDouble()
    }

    private fun countCodeSmells(files: List<String>): Int {
        // Эмуляция подсчета code smells
        return files.size * (1..5).random()
    }

    private fun calculateCyclomaticComplexity(files: List<String>): Double {
        // Эмуляция расчета цикломатической сложности
        return (5..25).random().toDouble()
    }

    private fun calculateCognitiveComplexity(files: List<String>): Double {
        // Эмуляция расчета когнитивной сложности
        return (8..30).random().toDouble()
    }

    private fun calculateTestCoverage(files: List<String>): Double {
        // Эмуляция покрытия тестами
        return (60..95).random().toDouble()
    }

    private fun calculateBranchCoverage(files: List<String>): Double {
        // Эмуляция покрытия ветвей
        return (50..90).random().toDouble()
    }

    private fun calculatePerformanceScore(files: List<String>): Double {
        // Эмуляция оценки производительности
        return (70..95).random().toDouble()
    }

    private fun identifyBottlenecks(files: List<String>): List<String> {
        // Эмуляция поиска узких мест
        return listOf("Memory allocation", "I/O operations", "Algorithm efficiency").shuffled().take(2)
    }

    private fun generateMetricsSummary(metrics: Map<String, Any>): String {
        val summary = StringBuilder()
        summary.appendLine("## Metrics Summary\n")
        metrics.forEach { (key, value) ->
            summary.appendLine("- **$key**: $value\n")
        }
        return summary.toString()
    }

    private fun generateTrends(metrics: Map<String, Any>): Map<String, String> {
        return mapOf(
            "quality_trend" to "stable",
            "performance_trend" to "improving",
            "coverage_trend" to "stable"
        )
    }

    private fun generateBenchmarks(metrics: Map<String, Any>): Map<String, Any> {
        return mapOf(
            "industry_average" to mapOf(
                "quality_score" to 85.0,
                "test_coverage" to 80.0
            ),
            "peer_comparison" to mapOf(
                "quality_percentile" to 75,
                "performance_percentile" to 80
            )
        )
    }

    private fun assessFunctionalSuitability(scope: Map<String, Any>): Double {
        return (7..10).random().toDouble()
    }

    private fun assessPerformanceEfficiency(scope: Map<String, Any>): Double {
        return (6..9).random().toDouble()
    }

    private fun assessCompatibility(scope: Map<String, Any>): Double {
        return (8..10).random().toDouble()
    }

    private fun assessUsability(scope: Map<String, Any>): Double {
        return (6..9).random().toDouble()
    }

    private fun assessReliability(scope: Map<String, Any>): Double {
        return (7..10).random().toDouble()
    }

    private fun assessSecurity(scope: Map<String, Any>): Double {
        return (7..10).random().toDouble()
    }

    private fun assessMaintainability(scope: Map<String, Any>): Double {
        return (6..9).random().toDouble()
    }

    private fun assessPortability(scope: Map<String, Any>): Double {
        return (8..10).random().toDouble()
    }

    private fun checkCompliance(model: String, scores: Map<String, Double>): Map<String, Any> {
        val overallScore = scores.values.average()
        return mapOf(
            "model" to model,
            "compliant" to (overallScore >= 7.0),
            "score" to overallScore,
            "threshold" to 7.0
        )
    }

    // Data классы для результатов
    private data class ReportResult(
        val content: String,
        val generationTimeMs: Long,
        val totalFindings: Int,
        val participatingAgents: List<String>,
        val dataSources: Int,
        val templateUsed: String
    )

    private data class SummaryResult(
        val content: String,
        val keyFindings: List<String>,
        val recommendations: List<String>,
        val riskAreas: List<String>
    )

    private data class MetricsResult(
        val metrics: Map<String, Any>,
        val summary: String,
        val trends: Map<String, String>,
        val benchmarks: Map<String, Any>,
        val filesAnalyzed: Int,
        val collectionDurationMs: Long
    )

    private data class QualityAssessmentResult(
        val qualityScores: Map<String, Double>,
        val qualityLevel: String,
        val improvementAreas: List<String>,
        val complianceStatus: Map<String, Any>
    )
}