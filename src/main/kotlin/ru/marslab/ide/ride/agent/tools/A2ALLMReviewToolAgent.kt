package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.BaseA2AAgent
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.orchestrator.AgentType
import com.intellij.openapi.project.ProjectManager

/**
 * A2A агент для LLM code review
 *
 * Выполняет анализ кода с использованием языковой модели,
 * предоставляет рекомендации по улучшению качества кода.
 */
class A2ALLMReviewToolAgent(
    private val llmProvider: LLMProvider
) : BaseA2AAgent(
    agentType = AgentType.LLM_REVIEW,
    a2aAgentId = "a2a-llm-review-agent",
    supportedMessageTypes = setOf("LLM_REVIEW_REQUEST"),
    publishedEventTypes = setOf("TOOL_EXECUTION_STARTED", "TOOL_EXECUTION_COMPLETED", "TOOL_EXECUTION_FAILED")
) {

    constructor() : this(createDefaultProvider())

    init {
        logger.info("A2ALLMReviewToolAgent initialized with LLM provider: ${llmProvider::class.simpleName}")
    }

    private companion object {
        private fun createDefaultProvider(): LLMProvider {
            // Здесь должна быть реализация создания LLM провайдера по умолчанию
            // Временно возвращаем заглушку, так как YandexGPTProvider недоступен
            throw UnsupportedOperationException("Default LLM provider not implemented. Please provide explicit LLMProvider.")
        }
    }

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        logger.info("A2ALLMReviewToolAgent received request: ${request.messageType}, ID: ${request.id}")

        if (request.messageType != "LLM_REVIEW_REQUEST") {
            logger.warn("Unsupported message type: ${request.messageType}")
            return createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
        }

        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val code = data["code"] as? String ?: ""
        val language = (data["language"] as? String).orEmpty()
        val focusAreas = data["focus_areas"] as? List<String> ?: listOf("general")
        val includeSuggestions = data["include_suggestions"] as? Boolean ?: true
        // Получаем путь проекта из контекста или из текущего открытого проекта
        val projectPath = currentExecutionContext.projectPath
            ?: runCatching {
                com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()?.basePath
                    ?: System.getProperty("user.dir")
            }.getOrNull()
            ?: ""

        logger.info("Processing LLM review request - Language: $language, Code length: ${code.length}, Project path: $projectPath")

        return try {
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

            logger.info("Requesting project data from scanner...")
            // Запрашиваем данные у сканера проекта через A2A шину
            val projectData = requestProjectData(messageBus, projectPath)
            logger.info("Received project data: ${projectData.keys}, size: ${projectData.size}")

            // Если код не предоставлен в запросе, получаем его из файлов проекта
            val finalCode = if (code.isBlank()) {
                logger.info("No code provided in request, fetching from project files...")
                val codeFiles = requestCodeFiles(messageBus, projectPath)
                if (codeFiles.isNotEmpty()) {
                    val fileContents = readCodeFiles(codeFiles)
                    val combinedCode = fileContents.entries.joinToString("\n\n") { (filePath, content) ->
                        val fileName = filePath.substringAfterLast('/')
                        "// File: $fileName\n$content"
                    }
                    logger.info("Combined code from ${fileContents.size} files, total length: ${combinedCode.length}")
                    combinedCode
                } else {
                    logger.warn("No code files found in project")
                    ""
                }
            } else {
                logger.info("Using provided code for review")
                code
            }

            if (finalCode.isBlank()) {
                logger.warn("No code available for review")
                return createErrorResponse(request.id, "No code available for review")
            }

            logger.info("Starting code review with LLM...")
            val review = withContext(Dispatchers.Default) {
                performCodeReview(finalCode, language, focusAreas, includeSuggestions, projectData)
            }
            logger.info("LLM review completed successfully")

            publishEvent(
                messageBus = messageBus,
                eventType = "TOOL_EXECUTION_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = a2aAgentId,
                    requestId = request.id,
                    timestamp = System.currentTimeMillis(),
                    result = "Code review completed with project context"
                )
            )

            val metadata = mutableMapOf(
                "agent" to "LLM_REVIEW",
                "language" to language,
                "focus_areas" to focusAreas,
                "review_timestamp" to System.currentTimeMillis(),
                "used_project_context" to projectData.isNotEmpty(),
                "code_source" to if (code.isBlank()) "auto_fetched_files" else "provided_in_request"
            )

            AgentMessage.Response(
                senderId = a2aAgentId,
                requestId = request.id,
                success = true,
                payload = MessagePayload.CustomPayload(
                    type = "LLM_REVIEW_RESULT",
                    data = mapOf(
                        "review" to review,
                        "project_context" to projectData,
                        "code_analysis_info" to mapOf(
                            "code_length" to finalCode.length,
                            "files_analyzed" to if (code.isBlank()) {
                                (projectData["files"] as? List<*>)?.size ?: 0
                            } else 1,
                            "auto_fetched" to code.isBlank()
                        ),
                        "metadata" to metadata
                    )
                )
            )
        } catch (e: Exception) {
            logger.error("Error in LLM review", e)

            publishEvent(
                messageBus = messageBus,
                eventType = "TOOL_EXECUTION_FAILED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "FAILED",
                    agentId = a2aAgentId,
                    requestId = request.id,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            )

            createErrorResponse(request.id, "LLM review failed: ${e.message}")
        }
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

    /**
     * Запрашивает данные о проекте у A2A сканера проекта
     */
    private suspend fun requestProjectData(messageBus: MessageBus, projectPath: String): Map<String, Any> {
        return try {
            logger.info("Requesting project data from scanner for path: $projectPath")

            val scannerRequest = AgentMessage.Request(
                senderId = a2aAgentId,
                targetId = null, // Широковещательный запрос - любой сканер может ответить
                messageType = "PROJECT_STRUCTURE_REQUEST",
                payload = MessagePayload.CustomPayload(
                    type = "PROJECT_STRUCTURE_REQUEST",
                    data = mapOf(
                        "project_path" to projectPath,
                        "include_metrics" to true,
                        "include_file_list" to true
                    )
                ),
                timeoutMs = 10000 // 10 секунд таймаут
            )

            val response = messageBus.requestResponse(scannerRequest, 10000)

            return if (response.success) {
                when (val payload = response.payload) {
                    is MessagePayload.ProjectStructurePayload -> {
                        mapOf(
                            "project_type" to payload.projectType,
                            "total_files" to payload.totalFiles,
                            "files" to payload.files.take(50), // Ограничиваем для контекста
                            "directories" to payload.directories.take(20),
                            "scanned_at" to payload.scannedAt,
                            "files_by_extension" to payload.files
                                .groupingBy { it.substringAfterLast('.') }
                                .eachCount()
                                .mapValues { (_, count) -> count }
                        )
                    }
                    is MessagePayload.CustomPayload -> {
                        payload.data + ("source" to "A2A_SCANNER")
                    }
                    else -> {
                        logger.warn("Unexpected payload type from scanner: ${payload::class.simpleName}")
                        emptyMap()
                    }
                }
            } else {
                logger.warn("Scanner returned error: ${response.error}")
                emptyMap()
            }
        } catch (e: Exception) {
            logger.error("Failed to request project data from A2A scanner", e)
            emptyMap()
        }
    }

    /**
     * Запрашивает файлы с кодом у A2A сканера проекта
     */
    private suspend fun requestCodeFiles(messageBus: MessageBus, projectPath: String): List<String> {
        return try {
            logger.info("Requesting code files from scanner for path: '$projectPath'")

            val requestData = mapOf(
                "project_path" to projectPath,
                "file_extensions" to listOf("kt", "java", "js", "ts", "py", "go", "rs", "cpp", "c", "h"),
                "max_files" to 20
            )
            logger.debug("File request data: $requestData")

            val fileRequest = AgentMessage.Request(
                senderId = a2aAgentId,
                targetId = null,
                messageType = "FILE_DATA_REQUEST",
                payload = MessagePayload.CustomPayload(
                    type = "FILE_DATA_REQUEST",
                    data = requestData
                ),
                timeoutMs = 15000 // 15 секунд таймаут
            )

            val response = messageBus.requestResponse(fileRequest, 15000)

            if (response.success) {
                logger.debug("File scanner response successful, payload type: ${response.payload::class.simpleName}")
                when (val payload = response.payload) {
                    is MessagePayload.CustomPayload -> {
                        val files = payload.data["files"] as? List<String> ?: emptyList()
                        val scanPath = payload.data["scan_path"] as? String
                        val fileTypes = payload.data["file_types"] as? Map<String, Int>
                        logger.info("Received ${files.size} code files from scanner, scan_path: '$scanPath', file_types: $fileTypes")
                        if (files.isNotEmpty()) {
                            logger.debug("First few files: ${files.take(3)}")
                        }
                        files
                    }
                    is MessagePayload.FilesScannedPayload -> {
                        logger.info("Received ${payload.files.size} code files from scanner, scan_path: '${payload.scanPath}'")
                        if (payload.files.isNotEmpty()) {
                            logger.debug("First few files: ${payload.files.take(3)}")
                        }
                        payload.files
                    }
                    else -> {
                        logger.warn("Unexpected payload type from file scanner: ${payload::class.simpleName}")
                        emptyList()
                    }
                }
            } else {
                logger.warn("File scanner returned error: ${response.error}")
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("Failed to request code files from A2A scanner", e)
            emptyList()
        }
    }

    /**
     * Читает содержимое файлов для анализа
     */
    private suspend fun readCodeFiles(filePathStrings: List<String>): Map<String, String> {
        return try {
            logger.info("Reading content of ${filePathStrings.size} files")

            val fileContents = mutableMapOf<String, String>()
            val maxSize = 10000 // Ограничение размера на файл
            var totalSize = 0
            val maxTotal = 50000 // Общее ограничение

            for (filePath in filePathStrings) {
                if (totalSize >= maxTotal) {
                    logger.warn("Reached total size limit, stopping file reading")
                    break
                }

                try {
                    val file = java.io.File(filePath)
                    if (file.exists() && file.canRead()) {
                        val content = file.readText().take(maxSize)
                        fileContents[filePath] = content
                        totalSize += content.length
                        logger.debug("Read file: $filePath (${content.length} chars)")
                    } else {
                        logger.warn("Cannot read file: $filePath")
                    }
                } catch (e: Exception) {
                    logger.warn("Error reading file $filePath: ${e.message}")
                }
            }

            logger.info("Successfully read ${fileContents.size} files, total size: $totalSize chars")
            fileContents
        } catch (e: Exception) {
            logger.error("Failed to read code files", e)
            emptyMap()
        }
    }

    private suspend fun performCodeReview(
        code: String,
        language: String,
        focusAreas: List<String>,
        includeSuggestions: Boolean,
        projectData: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        logger.info("performCodeReview called - Code length: ${code.length}, Language: $language")

        if (code.isBlank()) {
            logger.warn("Empty code provided for review")
            return mapOf(
                "error" to "No code provided for review",
                "summary" to mapOf(
                    "total_issues" to 0,
                    "severity_breakdown" to emptyMap<String, Int>()
                )
            )
        }

        try {
            val systemPrompt = buildSystemPrompt(language, focusAreas, includeSuggestions, projectData)
            val userPrompt = buildUserPrompt(code, language, focusAreas, projectData)
            logger.info("Built prompts - System prompt length: ${systemPrompt.length}, User prompt length: ${userPrompt.length}")

            val agentRequest = AgentRequest(
                request = userPrompt,
                context = createEmptyChatContext(),
                parameters = LLMParameters.PRECISE.copy(
                    temperature = 0.1,
                    maxTokens = 2000
                )
            )

            val systemPromptWithRules = applyRulesToPrompt(systemPrompt)
            logger.info("Applied rules to prompt, preparing to call LLM provider...")

            // Логируем промпт до отправки в LLM
            logUserPrompt(
                action = "A2A_LLM_REVIEW",
                systemPrompt = systemPromptWithRules,
                userPrompt = userPrompt,
                extraMeta = mapOf(
                    "language" to language,
                    "focus_areas" to focusAreas.joinToString(","),
                    "include_suggestions" to includeSuggestions
                )
            )

            logger.info("Calling LLM provider...")
            val response = llmProvider.sendRequest(
                systemPrompt = systemPromptWithRules,
                userMessage = userPrompt,
                conversationHistory = emptyList(),
                parameters = agentRequest.parameters
            )
            logger.info("LLM provider responded successfully - Response length: ${response.content.length}")

            val result = parseReviewResponse(response.content, code, language, focusAreas, projectData)
            logger.info("Parsed LLM response successfully")
            return result
        } catch (e: Exception) {
            logger.error("Error calling LLM for code review", e)
            return mapOf(
                "error" to "Failed to perform LLM review: ${e.message}",
                "summary" to mapOf(
                    "total_issues" to 0,
                    "severity_breakdown" to emptyMap<String, Int>()
                )
            )
        }
    }

    private fun buildSystemPrompt(language: String, focusAreas: List<String>, includeSuggestions: Boolean, projectData: Map<String, Any> = emptyMap()): String {
        val focusText = when {
            focusAreas.contains("security") -> " Особое внимание уделяй уязвимостям безопасности и лучшим практикам."
            focusAreas.contains("performance") -> " Сфокусируйся на оптимизации производительности и возможных узких местах."
            focusAreas.contains("readability") -> " Подчеркни читаемость кода, сопровождение и соглашения об именовании."
            focusAreas.contains("testing") -> " Обрати внимание на тестопригодность и предложи стратегии тестирования."
            else -> " Проведи всестороннее ревью, охватывающее все аспекты качества кода."
        }

        val suggestionText = if (includeSuggestions) {
            " Для каждой найденной проблемы предоставляй конкретные рекомендации по улучшению."
        } else {
            " Определи проблемы, но не давай детальных рекомендаций."
        }

        // Добавляем контекст проекта в промпт
        val projectContextText = if (projectData.isNotEmpty()) {
            val projectType = projectData["project_type"] as? String ?: "unknown"
            val totalFiles = projectData["total_files"] as? Int ?: 0
            val filesByExt = projectData["files_by_extension"] as? Map<String, Int> ?: emptyMap()

            """
            |
            |КОНТЕКСТ ПРОЕКТА:
            |- Тип проекта: $projectType
            |- Всего файлов: $totalFiles
            |- Распределение файлов: ${filesByExt.entries.joinToString(", ") { "${it.key} (${it.value})" }}
            |
            |Учитывай контекст проекта при анализе кода. Адаптируй рекомендации под стек технологий проекта.
            """.trimMargin()
        } else {
            ""
        }

        return """Ты — эксперт по ревью кода на языке $language.$projectContextText

Твоя задача — проанализировать предоставленный код и выявить:
1. Уязвимости безопасности и потенциальные риски
2. Проблемы производительности и возможности оптимизации
3. Вопросы качества кода и поддерживаемости
4. Нарушения лучших практик
5. Потенциальные баги и крайние случаи

$focusText$suggestionText

Верни ответ в структурированном JSON со следующими полями:
{
  "overall_score": 0-100,
  "summary": {
    "total_issues": number,
    "critical_issues": number,
    "major_issues": number,
    "minor_issues": number,
    "suggestions_count": number
  },
  "issues": [
    {
      "type": "security|performance|quality|bug|best_practice",
      "severity": "critical|major|minor",
      "line_number": number,
      "description": "Четкое описание проблемы",
      "suggestion": "Конкретная рекомендация по исправлению (если применимо)"
    }
  ],
  "positive_points": ["Список сильных сторон реализации"],
  "recommendations": ["Высокоуровневые рекомендации по улучшению"]
}

Дай конструктивную и обучающую обратную связь."""
    }

    private fun buildUserPrompt(code: String, language: String, focusAreas: List<String>, projectData: Map<String, Any> = emptyMap()): String {
        return """Проведи ревью следующего кода на $language:

```$language
$code
```

Области фокуса: ${focusAreas.joinToString(", ")}

Предоставь подробный анализ, следуя формату JSON, указанному в системном промпте."""
    }

    private fun parseReviewResponse(
        response: String,
        originalCode: String,
        language: String,
        focusAreas: List<String>,
        projectData: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        return try {
            // Попытка извлечь JSON из ответа
            val jsonMatch = Regex("""\{[\s\S]*\}""").find(response)
            val jsonResponse = jsonMatch?.groupValues?.get(0) ?: response

            // Если не получается распарсить как JSON, создаем базовую структуру
            if (jsonResponse.contains("overall_score") || jsonResponse.contains("issues")) {
                // Здесь была бы логика парсинга JSON, но для простоты возвращаем текстовый ответ
                mapOf(
                    "overall_score" to extractScore(response),
                    "summary" to extractSummary(response),
                    "issues" to extractIssues(response),
                    "positive_points" to extractPositivePoints(response),
                    "recommendations" to extractRecommendations(response),
                    "raw_response" to response,
                    "code_analysis" to analyzeCodeStructure(originalCode, language),
                    "focus_areas" to focusAreas,
                    "project_context_used" to projectData.isNotEmpty()
                )
            } else {
                // Текстовый ответ - создаем структуру на основе анализа текста
                createReviewFromText(response, originalCode, language, focusAreas, projectData)
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse LLM response, returning as text", e)
            mapOf(
                "overall_score" to 75,
                "summary" to mapOf(
                    "total_issues" to 0,
                    "critical_issues" to 0,
                    "major_issues" to 0,
                    "minor_issues" to 0,
                    "suggestions_count" to 1
                ),
                "issues" to emptyList<Map<String, Any>>(),
                "positive_points" to listOf("Code structure reviewed"),
                "recommendations" to listOf("Consider addressing the feedback provided in the review"),
                "raw_response" to response,
                "code_analysis" to analyzeCodeStructure(originalCode, language),
                "focus_areas" to focusAreas,
                "project_context_used" to projectData.isNotEmpty()
            )
        }
    }

    private fun extractScore(response: String): Int {
        val scoreMatch = Regex("""overall[_\s]*score[:\s]*(\d+)""", RegexOption.IGNORE_CASE).find(response)
        return scoreMatch?.groupValues?.get(1)?.toIntOrNull() ?: 75
    }

    private fun extractSummary(response: String): Map<String, Int> {
        val totalMatch = Regex("""total[_\s]*issues[:\s]*(\d+)""", RegexOption.IGNORE_CASE).find(response)
        val criticalMatch = Regex("""critical[_\s]*issues[:\s]*(\d+)""", RegexOption.IGNORE_CASE).find(response)
        val majorMatch = Regex("""major[_\s]*issues[:\s]*(\d+)""", RegexOption.IGNORE_CASE).find(response)
        val minorMatch = Regex("""minor[_\s]*issues[:\s]*(\d+)""", RegexOption.IGNORE_CASE).find(response)

        return mapOf(
            "total_issues" to (totalMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0),
            "critical_issues" to (criticalMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0),
            "major_issues" to (majorMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0),
            "minor_issues" to (minorMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0),
            "suggestions_count" to Regex("""suggestion""", RegexOption.IGNORE_CASE).findAll(response).count()
        )
    }

    private fun extractIssues(response: String): List<Map<String, Any>> {
        val issues = mutableListOf<Map<String, Any>>()

        // Простая эвристика для извлечения проблем из текста
        val lines = response.lines()
        var currentIssue: MutableMap<String, Any> = mutableMapOf()

        for (line in lines) {
            when {
                line.contains("security", ignoreCase = true) || line.contains("vulnerability", ignoreCase = true) -> {
                    if (currentIssue.isNotEmpty()) issues.add(currentIssue.toMap())
                    currentIssue = mutableMapOf(
                        "type" to "security",
                        "severity" to if (line.contains("critical", ignoreCase = true)) "critical" else "major",
                        "description" to line.trim(),
                        "suggestion" to ""
                    )
                }
                line.contains("performance", ignoreCase = true) -> {
                    if (currentIssue.isNotEmpty()) issues.add(currentIssue.toMap())
                    currentIssue = mutableMapOf(
                        "type" to "performance",
                        "severity" to "major",
                        "description" to line.trim(),
                        "suggestion" to ""
                    )
                }
                line.contains("recommend", ignoreCase = true) && currentIssue.isNotEmpty() -> {
                    currentIssue["suggestion"] = line.trim()
                    issues.add(currentIssue.toMap())
                    currentIssue = mutableMapOf()
                }
            }
        }

        if (currentIssue.isNotEmpty()) issues.add(currentIssue.toMap())
        return issues
    }

    private fun extractPositivePoints(response: String): List<String> {
        val positiveKeywords = listOf("good", "well", "excellent", "properly", "correctly", "effective")
        return response.lines()
            .filter { line ->
                positiveKeywords.any { keyword ->
                    line.contains(keyword, ignoreCase = true) &&
                    !line.contains("not", ignoreCase = true) &&
                    !line.contains("however", ignoreCase = true)
                }
            }
            .map { it.trim() }
            .take(5)
    }

    private fun extractRecommendations(response: String): List<String> {
        return response.lines()
            .filter { line ->
                line.contains("recommend", ignoreCase = true) ||
                line.contains("suggest", ignoreCase = true) ||
                line.contains("consider", ignoreCase = true)
            }
            .map { it.trim() }
            .take(10)
    }

    private fun createReviewFromText(
        response: String,
        originalCode: String,
        language: String,
        focusAreas: List<String>,
        projectData: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        val lines = originalCode.lines()
        val codeAnalysis = analyzeCodeStructure(originalCode, language)

        return mapOf(
            "overall_score" to 75,
            "summary" to mapOf(
                "total_issues" to countIssuesInText(response),
                "critical_issues" to countCriticalIssuesInText(response),
                "major_issues" to countMajorIssuesInText(response),
                "minor_issues" to countMinorIssuesInText(response),
                "suggestions_count" to countSuggestionsInText(response)
            ),
            "issues" to extractIssues(response),
            "positive_points" to extractPositivePoints(response),
            "recommendations" to extractRecommendations(response),
            "raw_response" to response,
            "code_analysis" to codeAnalysis,
            "focus_areas" to focusAreas,
            "project_context_used" to projectData.isNotEmpty()
        )
    }

    private fun analyzeCodeStructure(code: String, language: String): Map<String, Any> {
        val lines = code.lines()
        val totalLines = lines.size
        val nonEmptyLines = lines.count { it.trim().isNotEmpty() }
        val commentLines = lines.count { it.trim().startsWith("//") || it.trim().startsWith("/*") || it.trim().startsWith("*") }

        val functions = when (language) {
            "kotlin", "java" -> Regex("""fun\s+\w+|def\s+\w+""").findAll(code).count()
            "python" -> Regex("""def\s+\w+""").findAll(code).count()
            "javascript", "typescript" -> Regex("""function\s+\w+|\w+\s*:\s*function|\w+\s*=\s*\([^)]*\)\s*=>""").findAll(code).count()
            else -> 0
        }

        val classes = Regex("""class\s+\w+""").findAll(code).count()
        val imports = Regex("""import\s+""").findAll(code).count()

        return mapOf(
            "total_lines" to totalLines,
            "non_empty_lines" to nonEmptyLines,
            "comment_lines" to commentLines,
            "comment_ratio" to if (nonEmptyLines > 0) commentLines.toDouble() / nonEmptyLines else 0.0,
            "functions" to functions,
            "classes" to classes,
            "imports" to imports,
            "average_function_length" to if (functions > 0) nonEmptyLines / functions else 0,
            "language" to language
        )
    }

    private fun countIssuesInText(response: String): Int {
        val issueKeywords = listOf("issue", "problem", "concern", "vulnerability", "bug")
        return issueKeywords.sumOf { keyword ->
            Regex(keyword, RegexOption.IGNORE_CASE).findAll(response).count()
        }
    }

    private fun countCriticalIssuesInText(response: String): Int {
        return Regex("""critical.*?(issue|problem|vulnerability|bug)""", RegexOption.IGNORE_CASE)
            .findAll(response).count()
    }

    private fun countMajorIssuesInText(response: String): Int {
        return Regex("""major.*?(issue|problem|concern)""", RegexOption.IGNORE_CASE)
            .findAll(response).count()
    }

    private fun countMinorIssuesInText(response: String): Int {
        return Regex("""minor.*?(issue|problem|concern)""", RegexOption.IGNORE_CASE)
            .findAll(response).count()
    }

    private fun countSuggestionsInText(response: String): Int {
        return Regex("""suggest|recommend|consider""", RegexOption.IGNORE_CASE)
            .findAll(response).count()
    }

    private fun createEmptyChatContext(): ChatContext {
        return ChatContext(
            project = ProjectManager.getInstance().defaultProject,
            history = emptyList(),
            currentFile = null,
            selectedText = null,
            additionalContext = emptyMap()
        )
    }
}
