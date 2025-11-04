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
        if (request.messageType != "LLM_REVIEW_REQUEST") {
            return createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
        }

        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val code = data["code"] as? String ?: ""
        val language = (data["language"] as? String).orEmpty()
        val focusAreas = data["focus_areas"] as? List<String> ?: listOf("general")
        val includeSuggestions = data["include_suggestions"] as? Boolean ?: true

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

            val review = withContext(Dispatchers.Default) {
                performCodeReview(code, language, focusAreas, includeSuggestions)
            }

            publishEvent(
                messageBus = messageBus,
                eventType = "TOOL_EXECUTION_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = a2aAgentId,
                    requestId = request.id,
                    timestamp = System.currentTimeMillis(),
                    result = "Code review completed"
                )
            )

            AgentMessage.Response(
                senderId = a2aAgentId,
                requestId = request.id,
                success = true,
                payload = MessagePayload.CustomPayload(
                    type = "LLM_REVIEW_RESULT",
                    data = mapOf(
                        "review" to review,
                        "metadata" to mapOf(
                            "agent" to "LLM_REVIEW",
                            "language" to language,
                            "focus_areas" to focusAreas,
                            "review_timestamp" to System.currentTimeMillis()
                        )
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

    private suspend fun performCodeReview(
        code: String,
        language: String,
        focusAreas: List<String>,
        includeSuggestions: Boolean
    ): Map<String, Any> {
        if (code.isBlank()) {
            return mapOf(
                "error" to "No code provided for review",
                "summary" to mapOf(
                    "total_issues" to 0,
                    "severity_breakdown" to emptyMap<String, Int>()
                )
            )
        }

        val systemPrompt = buildSystemPrompt(language, focusAreas, includeSuggestions)
        val userPrompt = buildUserPrompt(code, language, focusAreas)

        val agentRequest = AgentRequest(
            request = userPrompt,
            context = createEmptyChatContext(),
            parameters = LLMParameters.PRECISE.copy(
                temperature = 0.1,
                maxTokens = 2000
            )
        )

        try {
            val systemPromptWithRules = applyRulesToPrompt(systemPrompt)

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

            val response = llmProvider.sendRequest(
                systemPrompt = systemPromptWithRules,
                userMessage = userPrompt,
                conversationHistory = emptyList(),
                parameters = agentRequest.parameters
            )

            return parseReviewResponse(response.content, code, language, focusAreas)
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

    private fun buildSystemPrompt(language: String, focusAreas: List<String>, includeSuggestions: Boolean): String {
        val focusText = when {
            focusAreas.contains("security") -> " Pay special attention to security vulnerabilities and best practices."
            focusAreas.contains("performance") -> " Focus on performance optimizations and potential bottlenecks."
            focusAreas.contains("readability") -> " Emphasize code readability, maintainability, and naming conventions."
            focusAreas.contains("testing") -> " Look for testability issues and suggest testing strategies."
            else -> " Provide a comprehensive review covering all aspects of code quality."
        }

        val suggestionText = if (includeSuggestions) {
            " For each issue found, provide specific suggestions for improvement."
        } else {
            " Identify issues but do not provide detailed suggestions."
        }

        return """Ты — эксперт по ревью кода на языке $language.

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

    private fun buildUserPrompt(code: String, language: String, focusAreas: List<String>): String {
        return """Please review the following $language code:

```$language
$code
```

Focus areas: ${focusAreas.joinToString(", ")}

Provide a thorough analysis following the JSON format specified in the system prompt."""
    }

    private fun parseReviewResponse(
        response: String,
        originalCode: String,
        language: String,
        focusAreas: List<String>
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
                    "focus_areas" to focusAreas
                )
            } else {
                // Текстовый ответ - создаем структуру на основе анализа текста
                createReviewFromText(response, originalCode, language, focusAreas)
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
                "focus_areas" to focusAreas
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
        focusAreas: List<String>
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
            "focus_areas" to focusAreas
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
