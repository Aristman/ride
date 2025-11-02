package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A2A-enhanced агент для генерации отчетов с сбором данных от всех агентов
 *
 * Отличия от обычного ReportGeneratorToolAgent:
 * - Собирает данные от всех анализирующих агентов через A2A
 * - Создает комплексные отчеты с агрегированными результатами
 * - Поддерживает real-time обновление отчетов при поступлении новых данных
 * - Публикует готовые отчеты через A2A события
 *
 * Capabilities:
 * - a2a_data_collection - сбор данных через A2A
 * - comprehensive_reporting - комплексные отчеты
 * - real_time_updates - обновления в реальном времени
 * - multi_format_export - экспорт в различных форматах
 */
class A2AReportGeneratorToolAgent(
    private val llmProvider: LLMProvider,
    private val messageBus: MessageBus,
    private val agentRegistry: A2AAgentRegistry
) : BaseToolAgent(
    agentType = AgentType.REPORT_GENERATOR,
    toolCapabilities = setOf(
        "markdown_generation",
        "html_generation", 
        "json_export",
        "llm_report",
        "a2a_data_collection",
        "comprehensive_reporting",
        "real_time_updates",
        "multi_format_export"
    )
), A2AAgent {

    override val agentId: String = "report-generator-a2a-${hashCode()}"
    override val a2aAgentId: String = agentId

    // A2A message types this agent supports
    override val supportedMessageTypes: Set<String> = setOf(
        "REPORT_GENERATION_REQUEST",
        "DATA_COLLECTION_REQUEST",
        "REPORT_UPDATE_REQUEST"
    )

    override val publishedEventTypes: Set<String> = setOf(
        "REPORT_GENERATION_STARTED",
        "DATA_COLLECTION_COMPLETED",
        "REPORT_READY",
        "REPORT_UPDATED"
    )

    override val messageProcessingPriority: Int = 3
    override val maxConcurrentMessages: Int = 2

    // Кэш собранных данных от агентов
    private val collectedData = ConcurrentHashMap<String, CollectedAgentData>()
    private val reportCache = ConcurrentHashMap<String, GeneratedReport>()

    init {
        logger.info("Initializing A2A Report Generator Agent: $agentId")
    }    overr
ide fun getDescription(): String {
        return "A2A-enhanced агент для генерации комплексных отчетов с данными от всех анализирующих агентов"
    }

    override fun validateInput(input: StepInput): ValidationResult {
        val format = input.getString("format")
        val collectFromAgents = input.getBoolean("collect_from_agents") ?: false

        if (format.isNullOrEmpty()) {
            return ValidationResult.failure("format is required")
        }

        if (format !in listOf("markdown", "html", "json", "comprehensive")) {
            return ValidationResult.failure("format must be one of: markdown, html, json, comprehensive")
        }

        return ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        logger.info("Starting A2A Report Generation")

        // Публикуем событие о начале генерации отчета
        publishA2AEvent(
            eventType = "REPORT_GENERATION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = agentId,
                requestId = step.id,
                timestamp = System.currentTimeMillis()
            )
        )

        return try {
            val format = step.input.getString("format") ?: "comprehensive"
            val collectFromAgents = step.input.getBoolean("collect_from_agents") ?: true
            val title = step.input.getString("title") ?: "Комплексный отчет анализа кода"
            val useLLM = step.input.getBoolean("use_llm") ?: true

            // Собираем данные от всех агентов через A2A
            val collectedResults = if (collectFromAgents) {
                collectDataFromAllAgents(step.id)
            } else {
                // Используем переданные данные напрямую
                mapOf(
                    "findings" to (step.input.getList<Finding>("findings") ?: emptyList()),
                    "metrics" to (step.input.get<Map<String, Any>>("metrics") ?: emptyMap())
                )
            }

            // Генерируем отчет
            val report = generateComprehensiveReport(
                collectedResults = collectedResults,
                format = format,
                title = title,
                useLLM = useLLM,
                requestId = step.id
            )

            // Кэшируем отчет
            reportCache[step.id] = report

            // Публикуем готовый отчет
            publishReportReady(report, step.id)

            // Публикуем событие о завершении
            publishA2AEvent(
                eventType = "REPORT_READY",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = agentId,
                    requestId = step.id,
                    timestamp = System.currentTimeMillis(),
                    result = "Report generated successfully (${report.content.length} chars)"
                )
            )

            StepResult.success(
                output = mapOf(
                    "report_content" to report.content,
                    "report_format" to report.format,
                    "data_sources" to report.dataSources,
                    "generation_time_ms" to report.generationTimeMs,
                    "total_findings" to report.totalFindings,
                    "participating_agents" to report.participatingAgents,
                    "a2a_enabled" to true
                )
            )

        } catch (e: Exception) {
            logger.error("Error in A2A Report Generation", e)
            
            publishA2AEvent(
                eventType = "REPORT_GENERATION_STARTED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "FAILED",
                    agentId = agentId,
                    requestId = step.id,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            )

            StepResult.error("A2A Report Generation failed: ${e.message}")
        }
    }    /
**
     * Собирает данные от всех доступных анализирующих агентов через A2A
     */
    private suspend fun collectDataFromAllAgents(requestId: String): Map<String, Any> {
        logger.info("Collecting data from all A2A agents for report generation")

        val collectedResults = mutableMapOf<String, Any>()
        val timeout = 45000L // 45 seconds timeout

        try {
            // Публикуем событие о начале сбора данных
            publishA2AEvent(
                eventType = "DATA_COLLECTION_STARTED",
                payload = MessagePayload.CustomPayload(
                    type = "DATA_COLLECTION_STARTED",
                    data = mapOf("request_id" to requestId)
                )
            )

            // Собираем данные параллельно от разных типов агентов
            val dataCollectionJobs = listOf(
                async { collectProjectScannerData(timeout) },
                async { collectBugDetectionData(timeout) },
                async { collectCodeQualityData(timeout) },
                // Можем добавить другие агенты
                // async { collectArchitectureAnalysisData(timeout) },
                // async { collectPerformanceAnalysisData(timeout) }
            )

            // Ждем завершения всех задач сбора данных
            val results = dataCollectionJobs.awaitAll()

            // Объединяем результаты
            results.forEach { result ->
                collectedResults.putAll(result)
            }

            // Публикуем событие о завершении сбора данных
            publishA2AEvent(
                eventType = "DATA_COLLECTION_COMPLETED",
                payload = MessagePayload.CustomPayload(
                    type = "DATA_COLLECTION_COMPLETED",
                    data = mapOf(
                        "request_id" to requestId,
                        "collected_sources" to collectedResults.keys.toList(),
                        "total_data_points" to collectedResults.size
                    )
                )
            )

            logger.info("Data collection completed: ${collectedResults.size} data sources")

        } catch (e: Exception) {
            logger.error("Error collecting data from agents", e)
            collectedResults["collection_error"] = "Failed to collect data: ${e.message}"
        }

        return collectedResults
    }

    /**
     * Собирает данные от ProjectScannerToolAgent
     */
    private suspend fun collectProjectScannerData(timeoutMs: Long): Map<String, Any> {
        return try {
            val scannerAgents = agentRegistry.getAgentsByType(AgentType.PROJECT_SCANNER)
            if (scannerAgents.isEmpty()) {
                logger.warn("No ProjectScannerToolAgent found")
                return mapOf("project_scanner" to "No agent available")
            }

            val request = AgentMessage.Request(
                senderId = agentId,
                requestId = "scanner-data-${System.currentTimeMillis()}",
                payload = MessagePayload.CustomPayload(
                    type = "PROJECT_STRUCTURE_REQUEST",
                    data = mapOf(
                        "include_metrics" to true,
                        "include_file_list" to true
                    )
                ),
                timeoutMs = timeoutMs
            )

            val response = withTimeout(timeoutMs + 5000) {
                messageBus.requestResponse(request)
            }

            if (response.success) {
                when (val payload = response.payload) {
                    is MessagePayload.ProjectStructurePayload -> {
                        mapOf(
                            "project_scanner" to mapOf(
                                "files" to payload.files,
                                "directories" to payload.directories,
                                "project_type" to payload.projectType,
                                "total_files" to payload.totalFiles,
                                "scanned_at" to payload.scannedAt
                            )
                        )
                    }
                    else -> {
                        logger.warn("Unexpected payload from ProjectScanner: ${payload::class.simpleName}")
                        mapOf("project_scanner" to "Unexpected response format")
                    }
                }
            } else {
                logger.error("ProjectScanner request failed: ${response.error}")
                mapOf("project_scanner" to "Request failed: ${response.error}")
            }

        } catch (e: Exception) {
            logger.error("Error collecting ProjectScanner data", e)
            mapOf("project_scanner" to "Collection error: ${e.message}")
        }
    }

    /**
     * Собирает данные от BugDetectionToolAgent
     */
    private suspend fun collectBugDetectionData(timeoutMs: Long): Map<String, Any> {
        return try {
            val bugAgents = agentRegistry.getAgentsByType(AgentType.BUG_DETECTION)
            if (bugAgents.isEmpty()) {
                logger.warn("No BugDetectionToolAgent found")
                return mapOf("bug_detection" to "No agent available")
            }

            val request = AgentMessage.Request(
                senderId = agentId,
                requestId = "bug-data-${System.currentTimeMillis()}",
                payload = MessagePayload.CustomPayload(
                    type = "BUG_ANALYSIS_REQUEST",
                    data = mapOf(
                        "request_source" to "REPORT_GENERATION",
                        "include_summary" to true
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
                        mapOf(
                            "bug_detection" to mapOf(
                                "findings" to payload.findings.map { finding ->
                                    mapOf(
                                        "file" to finding.file,
                                        "line" to (finding.line ?: 0),
                                        "severity" to finding.severity,
                                        "rule" to finding.rule,
                                        "message" to finding.message,
                                        "suggestion" to finding.suggestion
                                    )
                                },
                                "summary" to mapOf(
                                    "total_findings" to payload.summary.totalFindings,
                                    "critical_count" to payload.summary.criticalCount,
                                    "high_count" to payload.summary.highCount,
                                    "medium_count" to payload.summary.mediumCount,
                                    "low_count" to payload.summary.lowCount
                                ),
                                "processed_files" to payload.processedFiles
                            )
                        )
                    }
                    else -> {
                        logger.warn("Unexpected payload from BugDetection: ${payload::class.simpleName}")
                        mapOf("bug_detection" to "Unexpected response format")
                    }
                }
            } else {
                logger.error("BugDetection request failed: ${response.error}")
                mapOf("bug_detection" to "Request failed: ${response.error}")
            }

        } catch (e: Exception) {
            logger.error("Error collecting BugDetection data", e)
            mapOf("bug_detection" to "Collection error: ${e.message}")
        }
    }    /**

     * Собирает данные от CodeQualityToolAgent
     */
    private suspend fun collectCodeQualityData(timeoutMs: Long): Map<String, Any> {
        return try {
            val qualityAgents = agentRegistry.getAgentsByType(AgentType.CODE_QUALITY)
            if (qualityAgents.isEmpty()) {
                logger.warn("No CodeQualityToolAgent found")
                return mapOf("code_quality" to "No agent available")
            }

            val request = AgentMessage.Request(
                senderId = agentId,
                requestId = "quality-data-${System.currentTimeMillis()}",
                payload = MessagePayload.CustomPayload(
                    type = "METRICS_REQUEST",
                    data = mapOf(
                        "include_aggregated_results" to true,
                        "include_quality_score" to true
                    )
                ),
                timeoutMs = timeoutMs
            )

            val response = withTimeout(timeoutMs + 5000) {
                messageBus.requestResponse(request)
            }

            if (response.success) {
                when (val payload = response.payload) {
                    is MessagePayload.CustomPayload -> {
                        if (payload.type == "QUALITY_METRICS" || payload.type == "COMPREHENSIVE_QUALITY_METRICS") {
                            mapOf("code_quality" to payload.data)
                        } else {
                            mapOf("code_quality" to "Unexpected response type: ${payload.type}")
                        }
                    }
                    else -> {
                        logger.warn("Unexpected payload from CodeQuality: ${payload::class.simpleName}")
                        mapOf("code_quality" to "Unexpected response format")
                    }
                }
            } else {
                logger.error("CodeQuality request failed: ${response.error}")
                mapOf("code_quality" to "Request failed: ${response.error}")
            }

        } catch (e: Exception) {
            logger.error("Error collecting CodeQuality data", e)
            mapOf("code_quality" to "Collection error: ${e.message}")
        }
    }

    /**
     * Генерирует комплексный отчет на основе собранных данных
     */
    private suspend fun generateComprehensiveReport(
        collectedResults: Map<String, Any>,
        format: String,
        title: String,
        useLLM: Boolean,
        requestId: String
    ): GeneratedReport {
        val startTime = System.currentTimeMillis()
        
        logger.info("Generating comprehensive report in format: $format")

        val reportContent = when (format) {
            "markdown" -> generateMarkdownReport(collectedResults, title, useLLM)
            "html" -> generateHtmlReport(collectedResults, title, useLLM)
            "json" -> generateJsonReport(collectedResults, title)
            "comprehensive" -> generateComprehensiveMarkdownReport(collectedResults, title, useLLM)
            else -> generateMarkdownReport(collectedResults, title, useLLM)
        }

        val generationTime = System.currentTimeMillis() - startTime
        
        // Подсчитываем общую статистику
        val totalFindings = countTotalFindings(collectedResults)
        val participatingAgents = collectedResults.keys.toList()
        val dataSources = collectedResults.keys.size

        return GeneratedReport(
            content = reportContent,
            format = format,
            title = title,
            generationTimeMs = generationTime,
            totalFindings = totalFindings,
            participatingAgents = participatingAgents,
            dataSources = dataSources,
            requestId = requestId,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Генерирует комплексный Markdown отчет
     */
    private suspend fun generateComprehensiveMarkdownReport(
        collectedResults: Map<String, Any>,
        title: String,
        useLLM: Boolean
    ): String {
        val sb = StringBuilder()
        
        // Заголовок отчета
        sb.appendLine("# $title")
        sb.appendLine()
        sb.appendLine("*Сгенерировано: ${Clock.System.now()}*")
        sb.appendLine("*Источник: A2A Report Generator Agent*")
        sb.appendLine()

        // Исполнительное резюме
        sb.appendLine("## 📊 Исполнительное резюме")
        sb.appendLine()
        
        val totalFindings = countTotalFindings(collectedResults)
        val participatingAgents = collectedResults.keys.size
        
        sb.appendLine("- **Всего найдено проблем**: $totalFindings")
        sb.appendLine("- **Участвующих агентов**: $participatingAgents")
        sb.appendLine("- **Источники данных**: ${collectedResults.keys.joinToString(", ")}")
        sb.appendLine()

        // Данные от ProjectScanner
        collectedResults["project_scanner"]?.let { data ->
            sb.appendLine("## 📁 Структура проекта")
            sb.appendLine()
            if (data is Map<*, *>) {
                sb.appendLine("- **Тип проекта**: ${data["project_type"]}")
                sb.appendLine("- **Всего файлов**: ${data["total_files"]}")
                sb.appendLine("- **Директорий**: ${(data["directories"] as? List<*>)?.size ?: 0}")
                sb.appendLine("- **Время сканирования**: ${data["scanned_at"]}")
            }
            sb.appendLine()
        }

        // Данные от BugDetection
        collectedResults["bug_detection"]?.let { data ->
            sb.appendLine("## 🐛 Анализ багов")
            sb.appendLine()
            if (data is Map<*, *>) {
                val summary = data["summary"] as? Map<*, *>
                summary?.let {
                    sb.appendLine("### Сводка по серьезности")
                    sb.appendLine("- **Критические**: ${it["critical_count"]}")
                    sb.appendLine("- **Высокие**: ${it["high_count"]}")
                    sb.appendLine("- **Средние**: ${it["medium_count"]}")
                    sb.appendLine("- **Низкие**: ${it["low_count"]}")
                    sb.appendLine()
                }

                val findings = data["findings"] as? List<*>
                if (!findings.isNullOrEmpty()) {
                    sb.appendLine("### Топ-10 критических проблем")
                    findings.take(10).forEach { finding ->
                        if (finding is Map<*, *>) {
                            sb.appendLine("- **${finding["file"]}:${finding["line"]}** - ${finding["message"]}")
                        }
                    }
                    sb.appendLine()
                }
            }
        }

        // Данные от CodeQuality
        collectedResults["code_quality"]?.let { data ->
            sb.appendLine("## 📈 Качество кода")
            sb.appendLine()
            if (data is Map<*, *>) {
                data["quality_score"]?.let { score ->
                    sb.appendLine("- **Общий балл качества**: $score/100")
                }
                data["total_issues"]?.let { issues ->
                    sb.appendLine("- **Проблем качества**: $issues")
                }
                data["aggregated_issues"]?.let { aggregated ->
                    sb.appendLine("- **Агрегированных проблем**: $aggregated")
                }
                data["correlated_issues"]?.let { correlated ->
                    sb.appendLine("- **Коррелированных проблем**: $correlated")
                }
            }
            sb.appendLine()
        }

        // LLM-генерированные рекомендации
        if (useLLM) {
            try {
                val recommendations = generateLLMRecommendations(collectedResults)
                sb.appendLine("## 💡 Рекомендации")
                sb.appendLine()
                sb.appendLine(recommendations)
                sb.appendLine()
            } catch (e: Exception) {
                logger.error("Error generating LLM recommendations", e)
                sb.appendLine("## 💡 Рекомендации")
                sb.appendLine()
                sb.appendLine("*Не удалось сгенерировать рекомендации через LLM*")
                sb.appendLine()
            }
        }

        // Заключение
        sb.appendLine("## 🎯 Заключение")
        sb.appendLine()
        sb.appendLine("Анализ завершен успешно. Рекомендуется приоритизировать исправление критических и высоких проблем.")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("*Отчет сгенерирован A2A Report Generator Agent*")

        return sb.toString()
    }

    private suspend fun generateMarkdownReport(collectedResults: Map<String, Any>, title: String, useLLM: Boolean): String {
        // Упрощенная версия для обычного markdown
        return generateComprehensiveMarkdownReport(collectedResults, title, useLLM)
    }

    private suspend fun generateHtmlReport(collectedResults: Map<String, Any>, title: String, useLLM: Boolean): String {
        // Конвертируем markdown в HTML (упрощенно)
        val markdownContent = generateComprehensiveMarkdownReport(collectedResults, title, useLLM)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>$title</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; }
                    h1, h2, h3 { color: #333; }
                    .summary { background: #f5f5f5; padding: 20px; border-radius: 5px; }
                    .finding { margin: 10px 0; padding: 10px; border-left: 3px solid #007acc; }
                </style>
            </head>
            <body>
                <pre>$markdownContent</pre>
            </body>
            </html>
        """.trimIndent()
    }

    private fun generateJsonReport(collectedResults: Map<String, Any>, title: String): String {
        val reportData = mapOf(
            "title" to title,
            "generated_at" to Clock.System.now().toString(),
            "generator" to "A2A Report Generator Agent",
            "total_findings" to countTotalFindings(collectedResults),
            "participating_agents" to collectedResults.keys.toList(),
            "data" to collectedResults
        )
        
        // Простая JSON сериализация (в реальном проекте лучше использовать kotlinx.serialization)
        return reportData.toString()
    }    /*
*
     * Генерирует рекомендации через LLM
     */
    private suspend fun generateLLMRecommendations(collectedResults: Map<String, Any>): String {
        return try {
            val prompt = buildString {
                appendLine("Проанализируй результаты анализа кода и дай рекомендации по улучшению:")
                appendLine()
                
                collectedResults.forEach { (source, data) ->
                    appendLine("Источник: $source")
                    appendLine("Данные: ${data.toString().take(500)}...")
                    appendLine()
                }
                
                appendLine("Дай конкретные рекомендации по приоритетам исправления проблем.")
            }

            // Используем LLM для генерации рекомендаций
            val response = llmProvider.generateText(prompt)
            response.ifEmpty { "Рекомендации не сгенерированы" }

        } catch (e: Exception) {
            logger.error("Error generating LLM recommendations", e)
            "Ошибка генерации рекомендаций: ${e.message}"
        }
    }

    /**
     * Подсчитывает общее количество найденных проблем
     */
    private fun countTotalFindings(collectedResults: Map<String, Any>): Int {
        var total = 0
        
        collectedResults.values.forEach { data ->
            when (data) {
                is Map<*, *> -> {
                    // Ищем findings или summary
                    data["findings"]?.let { findings ->
                        if (findings is List<*>) {
                            total += findings.size
                        }
                    }
                    data["summary"]?.let { summary ->
                        if (summary is Map<*, *>) {
                            summary["total_findings"]?.let { count ->
                                if (count is Number) {
                                    total += count.toInt()
                                }
                            }
                        }
                    }
                    data["total_issues"]?.let { issues ->
                        if (issues is Number) {
                            total += issues.toInt()
                        }
                    }
                }
                is List<*> -> {
                    total += data.size
                }
            }
        }
        
        return total
    }

    /**
     * Публикует готовый отчет через A2A
     */
    private suspend fun publishReportReady(report: GeneratedReport, requestId: String) {
        logger.info("Publishing report ready notification via A2A")

        val reportPayload = MessagePayload.CustomPayload(
            type = "COMPREHENSIVE_REPORT",
            data = mapOf(
                "report_id" to requestId,
                "format" to report.format,
                "title" to report.title,
                "content_length" to report.content.length,
                "total_findings" to report.totalFindings,
                "participating_agents" to report.participatingAgents,
                "data_sources" to report.dataSources,
                "generation_time_ms" to report.generationTimeMs,
                "timestamp" to report.timestamp,
                "content_preview" to report.content.take(500) // Превью для уведомлений
            )
        )

        publishA2AEvent(
            eventType = "REPORT_READY",
            payload = reportPayload
        )
    }

    /**
     * Публикует A2A событие
     */
    private suspend fun publishA2AEvent(eventType: String, payload: MessagePayload) {
        try {
            val event = AgentMessage.Event(
                senderId = agentId,
                eventType = eventType,
                payload = payload,
                broadcast = true
            )

            messageBus.broadcast(event)
            logger.debug("Published A2A event: $eventType")

        } catch (e: Exception) {
            logger.error("Error publishing A2A event: $eventType", e)
        }
    }

    // A2AAgent interface implementation
    override suspend fun handleA2AMessage(message: AgentMessage): AgentMessage? {
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
                        "REPORT_GENERATION_REQUEST" -> handleReportGenerationRequest(request)
                        "DATA_COLLECTION_REQUEST" -> handleDataCollectionRequest(request)
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

    private suspend fun handleReportGenerationRequest(request: AgentMessage.Request): AgentMessage.Response {
        val payload = request.payload as MessagePayload.CustomPayload
        val format = payload.data["format"] as? String ?: "comprehensive"
        val title = payload.data["title"] as? String ?: "A2A Generated Report"

        // Создаем временный ToolPlanStep для генерации отчета
        val tempStep = ToolPlanStep(
            id = "a2a-${request.id}",
            agentType = AgentType.REPORT_GENERATOR,
            input = StepInput(mapOf(
                "format" to format,
                "title" to title,
                "collect_from_agents" to true
            ))
        )

        val result = doExecuteStep(tempStep, ExecutionContext.Empty)

        return if (result.isSuccess) {
            AgentMessage.Response(
                senderId = agentId,
                requestId = request.id,
                success = true,
                payload = MessagePayload.CustomPayload(
                    type = "REPORT_GENERATION_RESULT",
                    data = result.output.data
                )
            )
        } else {
            createErrorResponse(request.id, "Report generation failed: ${result.error}")
        }
    }

    private suspend fun handleDataCollectionRequest(request: AgentMessage.Request): AgentMessage.Response {
        val collectedData = collectDataFromAllAgents(request.id)

        return AgentMessage.Response(
            senderId = agentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "COLLECTED_DATA",
                data = collectedData
            )
        )
    }

    private fun handleA2AEvent(event: AgentMessage.Event) {
        when (event.eventType) {
            "BUG_FINDINGS_AVAILABLE", "QUALITY_METRICS_AVAILABLE", "PROJECT_STRUCTURE_UPDATED" -> {
                logger.info("Received analysis update from ${event.senderId} - may trigger report update")
                // Можем обновить кэшированные отчеты или уведомить подписчиков
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

    override fun getSupportedMessageTypes(): Set<String> = supportedMessageTypes

    override fun getMessageHandler(): suspend (AgentMessage) -> Unit = { message ->
        handleA2AMessage(message)
    }

    override suspend fun startA2AListening() {
        logger.info("Starting A2A message listening for Report Generator Agent")
    }

    override suspend fun stopA2AListening() {
        logger.info("Stopping A2A message listening for Report Generator Agent")
    }

    // Data classes
    data class CollectedAgentData(
        val agentType: AgentType,
        val data: Any,
        val timestamp: Long,
        val source: String
    )

    data class GeneratedReport(
        val content: String,
        val format: String,
        val title: String,
        val generationTimeMs: Long,
        val totalFindings: Int,
        val participatingAgents: List<String>,
        val dataSources: Int,
        val requestId: String,
        val timestamp: Long
    )

    companion object {
        /**
         * Создает A2A-enhanced ReportGeneratorToolAgent
         */
        fun create(
            llmProvider: LLMProvider,
            messageBus: MessageBus,
            agentRegistry: A2AAgentRegistry
        ): A2AReportGeneratorToolAgent {
            return A2AReportGeneratorToolAgent(llmProvider, messageBus, agentRegistry)
        }
    }
}