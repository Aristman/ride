package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.withTimeout
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.codeanalysis.analyzer.BugDetectionAnalyzer
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import java.io.File

/**
 * A2A-enhanced агент для поиска багов с интеграцией через MessageBus
 *
 * Отличия от обычного BugDetectionToolAgent:
 * - Запрашивает файловые данные через A2A от ProjectScannerToolAgent
 * - Публикует результаты анализа через A2A события
 * - Поддерживает асинхронную обработку через MessageBus
 *
 * Capabilities:
 * - bug_detection - поиск багов через A2A
 * - a2a_file_requests - запросы файлов через A2A
 * - a2a_result_broadcasting - публикация результатов через A2A
 */
class A2ABugDetectionToolAgent(
    private val llmProvider: LLMProvider,
    private val messageBus: MessageBus,
    private val agentRegistry: A2AAgentRegistry
) : BaseToolAgent(
    agentType = AgentType.BUG_DETECTION,
    toolCapabilities = setOf(
        "bug_detection",
        "null_pointer_analysis", 
        "resource_leak_detection",
        "a2a_file_requests",
        "a2a_result_broadcasting"
    )
), A2AAgent {

    private val agentId: String = "bug-detection-a2a-${hashCode()}"
    override val a2aAgentId: String = agentId

    private val analyzer by lazy { BugDetectionAnalyzer(llmProvider) }

    // A2A message types this agent supports
    override val supportedMessageTypes: Set<String> = setOf(
        "FILE_DATA_REQUEST",
        "BUG_ANALYSIS_REQUEST", 
        "PROJECT_STRUCTURE_NOTIFICATION"
    )

    override val publishedEventTypes: Set<String> = setOf(
        "BUG_ANALYSIS_STARTED",
        "BUG_ANALYSIS_COMPLETED",
        "BUG_FINDINGS_AVAILABLE"
    )

    override val messageProcessingPriority: Int = 1
    override val maxConcurrentMessages: Int = 3

    init {
        // Регистрируем агента в A2A системе
        logger.info("Initializing A2A Bug Detection Agent: $agentId")
    }

    override fun getDescription(): String {
        return "A2A-enhanced агент для анализа кода на наличие багов с интеграцией через MessageBus"
    }

    override fun validateInput(input: StepInput): ValidationResult {
        // Для A2A версии можем принимать либо файлы напрямую, либо project_path для запроса через A2A
        val files = input.getList<String>("files")
        val projectPath = input.get<String>("project_path")

        if (files.isNullOrEmpty() && projectPath.isNullOrBlank()) {
            return ValidationResult.failure("Either 'files' or 'project_path' must be provided")
        }

        return ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        logger.info("Starting A2A Bug Detection analysis")

        // Публикуем событие о начале анализа
        publishA2AEvent(
            eventType = "BUG_ANALYSIS_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = agentId,
                requestId = step.id,
                timestamp = System.currentTimeMillis()
            )
        )

        return try {
            val files = step.input.getList<String>("files")
            val projectPath = step.input.get<String>("project_path")

            val filesToAnalyze = if (!files.isNullOrEmpty()) {
                // Используем файлы напрямую (legacy mode)
                files
            } else if (!projectPath.isNullOrBlank()) {
                // Запрашиваем файлы через A2A от ProjectScannerToolAgent
                requestFilesViaA2A(projectPath)
            } else {
                emptyList()
            }

            if (filesToAnalyze.isEmpty()) {
                return StepResult.error("No files to analyze")
            }

            // Выполняем анализ
            val analysisResults = performBugAnalysis(filesToAnalyze, step)

            // Публикуем результаты через A2A
            publishAnalysisResults(analysisResults, step.id)

            // Публикуем событие о завершении
            publishA2AEvent(
                eventType = "BUG_ANALYSIS_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = agentId,
                    requestId = step.id,
                    timestamp = System.currentTimeMillis(),
                    result = "Found ${analysisResults.size} potential issues"
                )
            )

            StepResult.success(
                output = ru.marslab.ide.ride.model.tool.StepOutput.of(
                    "findings" to analysisResults,
                    "total_issues" to analysisResults.size,
                    "files_analyzed" to filesToAnalyze.size,
                    "a2a_enabled" to true
                )
            )

        } catch (e: Exception) {
            logger.error("Error in A2A Bug Detection analysis", e)
            
            publishA2AEvent(
                eventType = "BUG_ANALYSIS_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "FAILED",
                    agentId = agentId,
                    requestId = step.id,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            )

            StepResult.error("A2A Bug Detection failed: ${e.message}")
        }
    }

    /**
     * Запрашивает список файлов через A2A от ProjectScannerToolAgent
     */
    private suspend fun requestFilesViaA2A(projectPath: String): List<String> {
        logger.info("Requesting files via A2A for project: $projectPath")

        try {
            // Ищем ProjectScannerToolAgent в реестре
            val scannerAgents = agentRegistry.getAgentsByType(AgentType.PROJECT_SCANNER)
            if (scannerAgents.isEmpty()) {
                logger.warn("No ProjectScannerToolAgent found in A2A registry")
                return emptyList()
            }

            val scannerAgent = scannerAgents.first()
            
            // Создаем A2A запрос на получение файлов
            val request = AgentMessage.Request(
                senderId = agentId,
                messageType = "FILE_DATA_REQUEST",
                payload = MessagePayload.CustomPayload(
                    type = "FILE_DATA_REQUEST",
                    data = mapOf(
                        "project_path" to projectPath,
                        "file_extensions" to listOf(".kt", ".java", ".js", ".ts", ".py", ".cpp", ".c", ".cs"),
                        "max_files" to 50,
                        "include_content" to false // Нам нужны только пути
                    )
                ),
                timeoutMs = 30000
            )

            // Отправляем запрос через MessageBus
            val response = withTimeout(35000) {
                messageBus.requestResponse(request)
            }

            if (response.success) {
                // Извлекаем список файлов из ответа
                when (val payload = response.payload) {
                    is MessagePayload.ProjectStructurePayload -> {
                        logger.info("Received ${payload.files.size} files via A2A")
                        return payload.files
                    }
                    is MessagePayload.CustomPayload -> {
                        val files = payload.data["files"] as? List<String> ?: emptyList()
                        logger.info("Received ${files.size} files via A2A (custom payload)")
                        return files
                    }
                    else -> {
                        logger.warn("Unexpected payload type in A2A response: ${payload::class.simpleName}")
                        return emptyList()
                    }
                }
            } else {
                logger.error("A2A file request failed: ${response.error}")
                return emptyList()
            }

        } catch (e: Exception) {
            logger.error("Error requesting files via A2A", e)
            return emptyList()
        }
    }

    /**
     * Выполняет анализ багов для списка файлов
     */
    private suspend fun performBugAnalysis(files: List<String>, step: ToolPlanStep): List<Map<String, Any>> {
        val maxFilesToAnalyze = step.input.getInt("max_files") ?: 20
        val allFindings = mutableListOf<Map<String, Any>>()
        val filesToAnalyze = files.take(maxFilesToAnalyze)

        logger.info("Analyzing ${filesToAnalyze.size} files for bugs")

        for (filePath in filesToAnalyze) {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                logger.warn("File does not exist or is not a file: $filePath")
                continue
            }

            // Проверяем размер файла
            if (file.length() > 100_000) {
                logger.warn("File too large, skipping: $filePath (${file.length()} bytes)")
                continue
            }

            try {
                logger.debug("Analyzing file: $filePath")
                val code = file.readText()

                // Используем LLM-анализатор
                val findings = analyzer.analyze(code, filePath)

                // Конвертируем в Map для вывода
                val findingMaps: List<Map<String, Any>> = findings.map { finding ->
                    mapOf<String, Any>(
                        "file" to finding.file,
                        "line" to (finding.line ?: 0),
                        "severity" to finding.severity.name,
                        "message" to finding.title,
                        "description" to finding.description,
                        "suggestion" to (finding.suggestion ?: ""),
                        "type" to finding.type.name,
                        "analyzed_via_a2a" to true
                    )
                }

                allFindings.addAll(findingMaps)
                logger.debug("Found ${findings.size} issues in $filePath")

            } catch (e: Exception) {
                logger.error("Error analyzing file $filePath", e)
            }
        }

        return allFindings
    }

    /**
     * Публикует результаты анализа через A2A события
     */
    private suspend fun publishAnalysisResults(results: List<Map<String, Any>>, requestId: String) {
        logger.info("Publishing bug analysis results via A2A: ${results.size} findings")

        val analysisPayload = MessagePayload.CodeAnalysisPayload(
            findings = results.map { finding ->
                MessagePayload.CodeFinding(
                    file = finding["file"] as String,
                    line = finding["line"] as? Int,
                    severity = finding["severity"] as String,
                    rule = finding["type"] as String,
                    message = finding["message"] as String,
                    suggestion = finding["suggestion"] as String
                )
            },
            summary = MessagePayload.AnalysisSummary(
                totalFindings = results.size,
                criticalCount = results.count { (it["severity"] as String) == "CRITICAL" },
                highCount = results.count { (it["severity"] as String) == "HIGH" },
                mediumCount = results.count { (it["severity"] as String) == "MEDIUM" },
                lowCount = results.count { (it["severity"] as String) == "LOW" }
            ),
            processedFiles = results.map { it["file"] as String }.distinct().size
        )

        publishA2AEvent(
            eventType = "BUG_FINDINGS_AVAILABLE",
            payload = analysisPayload
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
                null // Events don't require responses
            }
            else -> null
        }
    }

    private suspend fun handleA2ARequest(request: AgentMessage.Request): AgentMessage.Response {
        return try {
            when (request.payload) {
                is MessagePayload.CustomPayload -> {
                    if (request.payload.type == "BUG_ANALYSIS_REQUEST") {
                        handleBugAnalysisRequest(request)
                    } else {
                        AgentMessage.Response(
                            senderId = agentId,
                            requestId = request.id,
                            success = false,
                            payload = MessagePayload.ErrorPayload(
                                error = "Unsupported request type: ${request.payload.type}"
                            )
                        )
                    }
                }
                else -> {
                    AgentMessage.Response(
                        senderId = agentId,
                        requestId = request.id,
                        success = false,
                        payload = MessagePayload.ErrorPayload(
                            error = "Unsupported payload type: ${request.payload::class.simpleName}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling A2A request", e)
            AgentMessage.Response(
                senderId = agentId,
                requestId = request.id,
                success = false,
                payload = MessagePayload.ErrorPayload(
                    error = "Internal error: ${e.message}",
                    cause = e.javaClass.simpleName
                )
            )
        }
    }

    private suspend fun handleBugAnalysisRequest(request: AgentMessage.Request): AgentMessage.Response {
        // Обрабатываем запрос на анализ багов от других агентов
        val payload = request.payload as MessagePayload.CustomPayload
        val files = payload.data["files"] as? List<String> ?: emptyList()

        if (files.isEmpty()) {
            return AgentMessage.Response(
                senderId = agentId,
                requestId = request.id,
                success = false,
                payload = MessagePayload.ErrorPayload(error = "No files provided for analysis")
            )
        }

        // Создаем временный ToolPlanStep для анализа
        val tempStep = ToolPlanStep(
            id = "a2a-${request.id}",
            description = "Run bug analysis via A2A request",
            agentType = AgentType.BUG_DETECTION,
            input = StepInput(mapOf("files" to files))
        )

        val results = performBugAnalysis(files, tempStep)

        return AgentMessage.Response(
            senderId = agentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CodeAnalysisPayload(
                findings = results.map { finding ->
                    MessagePayload.CodeFinding(
                        file = finding["file"] as String,
                        line = finding["line"] as? Int,
                        severity = finding["severity"] as String,
                        rule = finding["type"] as String,
                        message = finding["message"] as String,
                        suggestion = finding["suggestion"] as String
                    )
                },
                summary = MessagePayload.AnalysisSummary(
                    totalFindings = results.size,
                    criticalCount = results.count { (it["severity"] as String) == "CRITICAL" },
                    highCount = results.count { (it["severity"] as String) == "HIGH" },
                    mediumCount = results.count { (it["severity"] as String) == "MEDIUM" },
                    lowCount = results.count { (it["severity"] as String) == "LOW" }
                ),
                processedFiles = results.map { it["file"] as String }.distinct().size
            )
        )
    }

    private fun handleA2AEvent(event: AgentMessage.Event) {
        when (event.eventType) {
            "PROJECT_STRUCTURE_UPDATED" -> {
                logger.info("Received project structure update notification")
                // Можем обновить кэш или перезапустить анализ
            }
            "FILE_SYSTEM_CHANGED" -> {
                logger.info("Received file system change notification")
                // Можем пометить файлы для повторного анализа
            }
            else -> {
                logger.debug("Received A2A event: ${event.eventType}")
            }
        }
    }

    // Удалены устаревшие override-методы, не предусмотренные интерфейсом A2AAgent

    companion object {
        /**
         * Создает A2A-enhanced BugDetectionToolAgent
         */
        fun create(
            llmProvider: LLMProvider,
            messageBus: MessageBus,
            agentRegistry: A2AAgentRegistry
        ): A2ABugDetectionToolAgent {
            return A2ABugDetectionToolAgent(llmProvider, messageBus, agentRegistry)
        }
    }
}