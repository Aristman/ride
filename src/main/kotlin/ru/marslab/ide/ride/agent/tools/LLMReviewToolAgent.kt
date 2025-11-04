package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*

/**
 * Агент для ревью кода с использованием LLM
 *
 * Capabilities:
 * - code_review - ревью кода
 * - best_practices - проверка лучших практик
 * - security_review - проверка безопасности
 * - performance_review - проверка производительности
 */
class LLMReviewToolAgent(
    private val llmProvider: LLMProvider
) : BaseToolAgent(
    agentType = AgentType.LLM_REVIEW,
    toolCapabilities = setOf(
        "code_review",
        "best_practices",
        "security_review",
        "performance_review"
    )
) {

    override fun getDescription(): String {
        return "Проводит интеллектуальное ревью кода с использованием LLM"
    }

    override fun validateInput(input: StepInput): ValidationResult {
        val code = input.getString("code")
        val files = input.getList<String>("files")
        val reviewType = input.getString("review_type") ?: "general"

        if (code.isNullOrBlank() && files.isNullOrEmpty()) {
            return ValidationResult.failure("Either 'code' or 'files' must be provided for review")
        }

        if (reviewType !in setOf("general", "security", "performance", "best_practices", "architecture")) {
            return ValidationResult.failure("Invalid review_type: $reviewType")
        }

        logger.info("LLM_REVIEW validation passed: review_type=$reviewType, has_code=${!code.isNullOrBlank()}, files_count=${files?.size ?: 0}")
        return ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val code = step.input.getString("code")
        val files = step.input.getList<String>("files") ?: emptyList()
        val reviewType = step.input.getString("review_type") ?: "general"
        val language = step.input.getString("language").orEmpty()

        logger.info("LLM_REVIEW executing: review_type=$reviewType, language=$language, files=${files.size}, has_code=${!code.isNullOrBlank()}")

        try {
            // Собираем код для ревью
            val codeToReview = if (!code.isNullOrBlank()) {
                code
            } else if (files.isNotEmpty()) {
                collectCodeFromFiles(files)
            } else {
                return StepResult.error("No code provided for review")
            }

            if (codeToReview.isBlank()) {
                return StepResult.error("No code content found for review")
            }

            // Формируем промпт для ревью
            val systemPrompt = buildReviewSystemPrompt(reviewType, language)
            val userPrompt = buildReviewUserPrompt(codeToReview, reviewType, language, files)

            logger.info("LLM_REVIEW sending request to LLM")

            // Отправляем запрос в LLM
            val response = llmProvider.sendRequest(
                systemPrompt = systemPrompt,
                userMessage = userPrompt,
                conversationHistory = emptyList(),
                parameters = ru.marslab.ide.ride.model.llm.LLMParameters()
            )

            logger.info("LLM_REVIEW received LLM response: ${response.content.take(100)}...")

            // Извлекаем результаты ревью
            val reviewResult = parseReviewResponse(response.content, reviewType)

            logger.info("LLM_REVIEW parsed review: issues=${reviewResult.issues.size}, suggestions=${reviewResult.suggestions.size}, rating=${reviewResult.rating}")

            return StepResult.success(
                output = StepOutput.of(
                    "review_summary" to reviewResult.summary,
                    "issues" to reviewResult.issues,
                    "suggestions" to reviewResult.suggestions,
                    "rating" to reviewResult.rating,
                    "review_type" to reviewType,
                    "language" to language,
                    "files_reviewed" to files,
                    "code_reviewed" to if (code != null) true else false,
                    "best_practices" to reviewResult.bestPractices,
                    "security_issues" to reviewResult.securityIssues,
                    "performance_issues" to reviewResult.performanceIssues
                ),
                metadata = mapOf(
                    "code_length" to codeToReview.length,
                    "files_count" to files.size,
                    "review_type" to reviewType,
                    "response_tokens" to 0
                )
            )

        } catch (e: Exception) {
            logger.error("LLM_REVIEW error during review", e)
            return StepResult.error(
                "Failed to review code: ${e.message}",
                output = StepOutput.of(
                    "error" to (e.message ?: "Unknown error"),
                    "review_type" to reviewType
                )
            )
        }
    }

    private fun collectCodeFromFiles(files: List<String>): String {
        return try {
            files.take(10).mapNotNull { filePath ->
                try {
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        val content = file.readText()
                        "// File: $filePath\n$content"
                    } else {
                        logger.warn("File not found: $filePath")
                        null
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to read file: $filePath", e)
                    null
                }
            }.joinToString("\n\n")
        } catch (e: Exception) {
            logger.error("Error collecting code from files", e)
            ""
        }
    }

    private fun buildReviewSystemPrompt(reviewType: String, language: String): String {
        val basePrompt = """
            Ты - старший разработчик-эксперт по $language. Проводи качественное ревью кода.

            Твоя задача:
            - Найти потенциальные проблемы и баги
            - Предложить улучшения
            - Оценить качество кода
            - Дать конструктивную обратную связь

            Формат ответа:
            ## Краткое резюме
            [Общая оценка кода]

            ## Найденные проблемы
            - **[Критичность]**: [Описание проблемы]
            - **Рекомендация**: [Как исправить]

            ## Предложения по улучшению
            - [Предложение 1]
            - [Предложение 2]

            ## Общая оценка: [X/10]

            Критичность: [КРИТИЧЕСКИЙ, ВЫСОКИЙ, СРЕДНИЙ, НИЗКИЙ]
        """.trimIndent()

        val typeSpecific = when (reviewType) {
            "security" -> """
                |
                |Фокус на безопасности:
                |- Проверка входных данных
                |- SQL-инъекции и XSS
                |- Аутентификация и авторизация
                |- Криптография и секреты
                |- Валидация данных
            """.trimMargin()
            "performance" -> """
                |
                |Фокус на производительности:
                |- Алгоритмическая сложность
                |- Использование памяти
                |- Эффективные структуры данных
                |- Кэширование
                |- Блокировки и конкурентность
            """.trimMargin()
            "best_practices" -> """
                |
                |Фокус на лучших практиках:
                |- Чистый код и命名
                |- Принципы SOLID
                |- Архитектурные паттерны
                |- Тестирование
                |- Документация
            """.trimMargin()
            "architecture" -> """
                |
                |Фокус на архитектуре:
                |- Модульность и связность
                |- Абстракции и инкапсуляция
                |- Масштабируемость
                |- Поддерживаемость
                |- Зависимости
            """.trimMargin()
            else -> ""
        }

        return basePrompt + typeSpecific
    }

    private fun buildReviewUserPrompt(code: String, reviewType: String, language: String, files: List<String>): String {
        val fileContext = if (files.isNotEmpty()) {
            "Файлы для ревью: ${files.joinToString(", ")}\n\n"
        } else {
            ""
        }

        return """
            ${fileContext}Проведи ревью следующего кода ($reviewType):

            ```$language
            $code
            ```
        """.trimIndent()
    }

    private fun parseReviewResponse(response: String, reviewType: String): ReviewResult {
        val issues = mutableListOf<ReviewIssue>()
        val suggestions = mutableListOf<String>()
        val bestPractices = mutableListOf<String>()
        val securityIssues = mutableListOf<ReviewIssue>()
        val performanceIssues = mutableListOf<ReviewIssue>()

        // Извлекаем оценку
        val ratingRegex = Regex("""Общая оценка:\s*(\d+)/?10""")
        val rating = ratingRegex.find(response)?.groupValues?.get(1)?.toIntOrNull() ?: 5

        // Извлекаем резюме
        val summaryRegex = Regex("""## Краткое резюме\s*\n(.+?)(?=##|\z)""", RegexOption.DOT_MATCHES_ALL)
        val summary = summaryRegex.find(response)?.groupValues?.get(1)?.trim() ?: "Ревью завершено"

        // Извлекаем проблемы
        val issuesRegex = Regex("""## Найденные проблемы\s*\n(.+?)(?=##|\z)""", RegexOption.DOT_MATCHES_ALL)
        val issuesSection = issuesRegex.find(response)?.groupValues?.get(1) ?: ""

        val issuePattern = Regex("""-\s*\*\*([^\*]+)\*\*:\s*([^\n]+)\s*-?\s*\*\*Рекомендация\*\*:\s*([^\n]+)""")
        issuePattern.findAll(issuesSection).forEach { match ->
            val severity = match.groupValues[1].trim()
            val description = match.groupValues[2].trim()
            val recommendation = match.groupValues[3].trim()

            val issue = ReviewIssue(
                severity = when {
                    severity.contains("КРИТИЧЕСКИ", ignoreCase = true) -> Severity.CRITICAL
                    severity.contains("ВЫСОКИЙ", ignoreCase = true) -> Severity.HIGH
                    severity.contains("СРЕДНИЙ", ignoreCase = true) -> Severity.MEDIUM
                    else -> Severity.LOW
                },
                description = description,
                recommendation = recommendation
            )

            issues.add(issue)

            // Классифицируем проблемы
            when {
                description.contains("безопас", ignoreCase = true) ||
                severity.contains("безопас", ignoreCase = true) -> securityIssues.add(issue)
                description.contains("производ", ignoreCase = true) ||
                severity.contains("производ", ignoreCase = true) -> performanceIssues.add(issue)
            }
        }

        // Извлекаем предложения
        val suggestionsRegex = Regex("""## Предложения по улучшению\s*\n(.+?)(?=##|\z)""", RegexOption.DOT_MATCHES_ALL)
        val suggestionsSection = suggestionsRegex.find(response)?.groupValues?.get(1) ?: ""
        val suggestionPattern = Regex("""-\s*(.+)""")
        suggestionPattern.findAll(suggestionsSection).forEach { match ->
            suggestions.add(match.groupValues[1].trim())
        }

        return ReviewResult(
            summary = summary,
            issues = issues,
            suggestions = suggestions,
            rating = rating,
            bestPractices = bestPractices,
            securityIssues = securityIssues,
            performanceIssues = performanceIssues
        )
    }

    private data class ReviewResult(
        val summary: String,
        val issues: List<ReviewIssue>,
        val suggestions: List<String>,
        val rating: Int,
        val bestPractices: List<String>,
        val securityIssues: List<ReviewIssue>,
        val performanceIssues: List<ReviewIssue>
    )

    private data class ReviewIssue(
        val severity: Severity,
        val description: String,
        val recommendation: String
    )
}