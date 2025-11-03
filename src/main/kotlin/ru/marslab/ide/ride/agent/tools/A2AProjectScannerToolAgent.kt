package ru.marslab.ide.ride.agent.tools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.agent.a2a.A2AAgent
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.StepOutput
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import java.io.File

/**
 * A2A-версия ProjectScannerToolAgent.
 * Обрабатывает FILE_DATA_REQUEST и PROJECT_STRUCTURE_REQUEST через шину MessageBus.
 */
class A2AProjectScannerToolAgent() : BaseToolAgent(
    agentType = AgentType.PROJECT_SCANNER,
    toolCapabilities = setOf(
        "file_listing",
        "project_structure",
        "a2a_scanner"
    )
), A2AAgent {

    private val agentId: String = "project-scanner-a2a-${hashCode()}"
    override val a2aAgentId: String = agentId

    private val a2aJob: Job = SupervisorJob()
    private val a2aScope = CoroutineScope(Dispatchers.Default + a2aJob)

    override val supportedMessageTypes: Set<String> = setOf(
        "FILE_DATA_REQUEST",
        "PROJECT_STRUCTURE_REQUEST"
    )

    override val publishedEventTypes: Set<String> = setOf(
        "FILES_SCANNED",
        "PROJECT_STRUCTURE_AVAILABLE"
    )

    override fun getDescription(): String =
        "A2A агент-сканер проекта: выдает список файлов и структуру проекта по запросу"

    override fun validateInput(input: StepInput): ValidationResult {
        // Для совместимости с оркестратором — проектный путь опционален
        return ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val projectPath = step.input.getString("project_path") ?: ""
        val includeMetrics = step.input.getBoolean("include_metrics") ?: true
        val includeFileList = step.input.getBoolean("include_file_list") ?: true

        return try {
            val (files, directories) = scanProject(projectPath)

            val totalFiles = files.size
            val projectType = detectProjectType(projectPath)

            StepResult.success(
                output = StepOutput.of(
                    "files" to if (includeFileList) files else emptyList<String>(),
                    "directories" to directories,
                    "project_type" to projectType,
                    "total_files" to totalFiles,
                    "scanned_at" to System.currentTimeMillis(),
                    "metrics" to if (includeMetrics) mapOf(
                        "kt_files" to files.count { it.endsWith(".kt") },
                        "java_files" to files.count { it.endsWith(".java") }
                    ) else emptyMap<String, Int>()
                )
            )
        } catch (e: Exception) {
            logger.error("Error executing A2AProjectScannerToolAgent step", e)
            StepResult.error("Scanner error: ${e.message}")
        }
    }

    override suspend fun handleA2AMessage(
        message: AgentMessage,
        messageBus: MessageBus
    ): AgentMessage? {
        return when (message) {
            is AgentMessage.Request -> handleRequest(message)
            is AgentMessage.Event -> null
            is AgentMessage.Response -> null
            is AgentMessage.Ack -> null
        }
    }

    override suspend fun initializeA2A(
        messageBus: MessageBus,
        context: ExecutionContext
    ) {
        // Публикуем событие инициализации
        try {
            val init = AgentMessage.Event(
                senderId = a2aAgentId,
                eventType = "AGENT_INITIALIZED",
                payload = MessagePayload.AgentInfoPayload(
                    agentId = a2aAgentId,
                    agentType = agentType.name,
                    legacyAgentClass = this@A2AProjectScannerToolAgent::class.java.name,
                    supportedMessageTypes = supportedMessageTypes,
                    timestamp = System.currentTimeMillis()
                )
            )
            messageBus.publish(init)
        } catch (e: Exception) {
            logger.warn("Failed to publish init event for $a2aAgentId", e)
        }

        // Подписка на запросы по поддерживаемым типам и (опционально) адресу agentId
        a2aScope.launch {
            messageBus
                .subscribe(AgentMessage.Request::class) { req ->
                    supportedMessageTypes.contains(req.messageType) &&
                        (req.targetId == null || req.targetId == a2aAgentId)
                }
                .collect { req ->
                    try {
                        val resp = handleRequest(req)
                        messageBus.publish(resp)
                    } catch (e: Exception) {
                        logger.error("A2AProjectScannerToolAgent failed to handle request ${req.messageType}", e)
                        val error = AgentMessage.Response(
                            senderId = a2aAgentId,
                            requestId = req.id,
                            success = false,
                            payload = MessagePayload.ErrorPayload(
                                error = e.message ?: "Scanner error",
                                cause = "A2AProjectScannerToolAgent exception"
                            ),
                            error = e.message
                        )
                        messageBus.publish(error)
                    }
                }
        }
    }

    override suspend fun shutdownA2A(messageBus: MessageBus) {
        a2aJob.cancel()
    }

    private suspend fun handleRequest(request: AgentMessage.Request): AgentMessage.Response {
        return try {
            when (val payload = request.payload) {
                is MessagePayload.CustomPayload -> when (request.messageType) {
                    "FILE_DATA_REQUEST" -> handleFileDataRequest(request, payload)
                    "PROJECT_STRUCTURE_REQUEST" -> handleProjectStructureRequest(request, payload)
                    else -> errorResponse(request.id, "Unsupported message type: ${request.messageType}")
                }
                else -> errorResponse(request.id, "Unsupported payload type: ${payload::class.simpleName}")
            }
        } catch (e: Exception) {
            logger.error("Error handling A2A request", e)
            errorResponse(request.id, "Internal error: ${e.message}")
        }
    }

    private suspend fun handleFileDataRequest(
        request: AgentMessage.Request,
        payload: MessagePayload.CustomPayload
    ): AgentMessage.Response {
        val projectPath = payload.data["project_path"] as? String ?: ""
        val extensions = (payload.data["file_extensions"] as? List<*>)?.filterIsInstance<String>()
        val maxFiles = (payload.data["max_files"] as? Int) ?: 100

        val (files, _) = scanProject(projectPath)
        val filtered = files
            .asSequence()
            .filter { extFilter(it, extensions) }
            .take(maxFiles)
            .toList()

        return AgentMessage.Response(
            senderId = agentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "FILE_DATA",
                data = mapOf(
                    "files" to filtered,
                    "scan_path" to projectPath,
                    "file_types" to filtered.groupingBy { File(it).extension.lowercase() }.eachCount(),
                    "scan_duration_ms" to 0L
                )
            )
        )
    }

    private suspend fun handleProjectStructureRequest(
        request: AgentMessage.Request,
        payload: MessagePayload.CustomPayload
    ): AgentMessage.Response {
        val projectPath = (payload.data["project_path"] as? String) ?: ""
        val (files, directories) = scanProject(projectPath)
        val projectType = detectProjectType(projectPath)

        return AgentMessage.Response(
            senderId = agentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.ProjectStructurePayload(
                files = files,
                directories = directories,
                projectType = projectType,
                totalFiles = files.size,
                scannedAt = System.currentTimeMillis()
            )
        )
    }

    private fun errorResponse(requestId: String, error: String): AgentMessage.Response {
        return AgentMessage.Response(
            senderId = agentId,
            requestId = requestId,
            success = false,
            payload = MessagePayload.ErrorPayload(error = error)
        )
    }

    private suspend fun scanProject(projectPath: String): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        val root = if (projectPath.isBlank()) File("") else File(projectPath)
        if (!root.exists()) return@withContext emptyList<String>() to emptyList<String>()

        val files = mutableListOf<String>()
        val dirs = mutableListOf<String>()

        fun walk(dir: File) {
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) {
                    dirs += f.absolutePath
                    // ограничим глубину и объем, чтобы не перегружать
                    if (dirs.size < 500) walk(f)
                } else {
                    files += f.absolutePath
                }
            }
        }

        if (root.isDirectory) walk(root) else files += root.absolutePath

        files to dirs
    }

    private fun detectProjectType(projectPath: String): String {
        val root = if (projectPath.isBlank()) File("") else File(projectPath)
        val isGradle = File(root, "build.gradle.kts").exists() || File(root, "build.gradle").exists()
        val isMaven = File(root, "pom.xml").exists()
        val isNode = File(root, "package.json").exists()
        return when {
            isGradle -> "gradle"
            isMaven -> "maven"
            isNode -> "node"
            else -> "generic"
        }
    }

    private fun extFilter(path: String, extensions: List<String>?): Boolean {
        if (extensions.isNullOrEmpty()) return true
        return extensions.any { path.endsWith(it) }
    }
}

