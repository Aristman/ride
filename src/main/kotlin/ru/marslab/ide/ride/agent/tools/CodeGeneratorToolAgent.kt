package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*

/**
 * Агент для генерации кода на основе запросов пользователя
 *
 * Capabilities:
 * - code_generation - генерация кода
 * - class_generation - создание классов
 * - function_generation - создание функций
 * - algorithm_generation - реализация алгоритмов
 */
class CodeGeneratorToolAgent(
    private val llmProvider: LLMProvider
) : BaseToolAgent(
    agentType = AgentType.CODE_GENERATOR,
    toolCapabilities = setOf(
        "code_generation",
        "class_generation",
        "function_generation",
        "algorithm_generation"
    )
) {

    override fun getDescription(): String {
        return "Генерирует код на основе текстовых запросов"
    }

    override fun validateInput(input: StepInput): ValidationResult {
        val request = input.getString("request")
        val language = input.getString("language") .orEmpty()
        val codeType = input.getString("code_type")

        if (request.isNullOrBlank()) {
            return ValidationResult.failure("Parameter 'request' is required for code generation")
        }

        if (codeType != null && codeType !in setOf("class", "function", "algorithm", "general")) {
            return ValidationResult.failure("Invalid code_type: $codeType. Must be one of: class, function, algorithm, general")
        }

        logger.info("CODE_GENERATOR validation passed: request=${request.take(50)}..., language=$language, code_type=$codeType")
        return ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val request = step.input.getString("request")!!
        val language = step.input.getString("language") .orEmpty()
        val codeType = step.input.getString("code_type") ?: "general"
        val contextFiles = step.input.getList<String>("context_files") ?: emptyList()

        logger.info("CODE_GENERATOR executing: request=${request.take(50)}..., language=$language, code_type=$codeType, context_files=${contextFiles.size}")

        try {
            // Формируем промпт для генерации кода
            val systemPrompt = buildSystemPrompt(language, codeType, contextFiles)
            val userPrompt = buildUserPrompt(request, codeType)

            // Добавляем контекст из файлов, если они есть
            val contextInfo = if (contextFiles.isNotEmpty()) {
                buildFileContext(contextFiles)
            } else {
                ""
            }

            val fullUserPrompt = if (contextInfo.isNotBlank()) {
                "$contextInfo\n\n$request"
            } else {
                request
            }

            logger.info("CODE_GENERATOR sending request to LLM")
            // Логируем промпт пользователя
            logUserPrompt(
                action = "CODE_GENERATION",
                systemPrompt = systemPrompt,
                userPrompt = fullUserPrompt,
                extraMeta = mapOf(
                    "code_type" to codeType,
                    "language" to language,
                    "has_context" to (contextFiles.isNotEmpty())
                )
            )

            // Отправляем запрос в LLM
            val response = llmProvider.sendRequest(
                systemPrompt = systemPrompt,
                userMessage = fullUserPrompt,
                conversationHistory = emptyList(),
                parameters = ru.marslab.ide.ride.model.llm.LLMParameters()
            )

            logger.info("CODE_GENERATOR received LLM response: ${response.content.take(100)}...")

            // Извлекаем сгенерированный код
            val generatedCode = extractCodeFromResponse(response.content, language)
            val explanation = extractExplanationFromResponse(response.content)

            logger.info("CODE_GENERATOR extracted code: ${generatedCode.take(100)}...")

            return StepResult.success(
                output = StepOutput.of(
                    "generated_code" to generatedCode,
                    "explanation" to explanation,
                    "language" to language,
                    "code_type" to codeType,
                    "request" to request,
                    "files_created" to listOf<String>(), // Может быть заполнено позже
                    "dependencies" to extractDependencies(generatedCode, language)
                ),
                metadata = mapOf(
                    "request_length" to request.length,
                    "code_length" to generatedCode.length,
                    "has_context" to (contextFiles.isNotEmpty()),
                    "response_tokens" to 0
                )
            )

        } catch (e: Exception) {
            logger.error("CODE_GENERATOR error during code generation", e)
            return StepResult.error(
                "Failed to generate code: ${e.message}",
                output = StepOutput.of(
                    "error" to (e.message ?: "Unknown error"),
                    "request" to request
                )
            )
        }
    }

    private fun buildSystemPrompt(language: String, codeType: String, contextFiles: List<String>): String {
        val basePrompt = """
            Ты - эксперт по разработке на $language. Создавай качественный, хорошо структурированный код.

            Требования:
            - Используй современные идиомы $language
            - Добавляй комментарии для сложных участков
            - Следуй принципам чистого кода
            - Создавай тестируемый и поддерживаемый код
            - Включай необходимые import'ы
        """.trimIndent()

        val typeSpecific = when (codeType) {
            "class" -> """
                |
                |Для создания классов:
                |- Определяй поля и методы согласно требованиям
                |- Добавляй конструкторы при необходимости
                |- Следуй принципам инкапсуляции
                |- Добавляй toString(), equals(), hashCode() если нужно
            """.trimMargin()
            "function" -> """
                |
                |Для создания функций:
                |- Определяй четкую сигнатуру с типами
                |- Добавляй валидацию параметров
                |- Возвращай осмысленные результаты
                |- Обрабатывай ошибки корректно
            """.trimMargin()
            "algorithm" -> """
                |
                |Для реализации алгоритмов:
                |- Оптимизируй по производительности
                |- Добавляй комментарии по сложности
                |- Обрабатывай крайние случаи
                |- Используй подходящие структуры данных
            """.trimMargin()
            else -> ""
        }

        val contextHint = if (contextFiles.isNotEmpty()) {
            """
                |
                |Учти контекст существующих файлов при генерации кода.
                |Создавай код, который хорошо интегрируется с существующей архитектурой.
            """.trimMargin()
        } else {
            ""
        }

        return basePrompt + typeSpecific + contextHint
    }

    private fun buildUserPrompt(request: String, codeType: String): String {
        return when (codeType) {
            "class" -> "Создай класс для: $request"
            "function" -> "Создай функцию для: $request"
            "algorithm" -> "Реализуй алгоритм: $request"
            else -> "Создай код для: $request"
        }
    }

    private fun buildFileContext(files: List<String>): String {
        if (files.isEmpty()) return ""

        return try {
            val fileContents = files.take(5).mapNotNull { filePath ->
                try {
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        val content = file.readText()
                        "Файл: $filePath\n```\n$content\n```\n"
                    } else null
                } catch (e: Exception) {
                    logger.warn("Failed to read context file: $filePath", e)
                    null
                }
            }.joinToString("\n")

            if (fileContents.isNotBlank()) {
                "Контекст существующих файлов:\n$fileContents"
            } else ""
        } catch (e: Exception) {
            logger.warn("Error building file context", e)
            ""
        }
    }

    private fun extractCodeFromResponse(response: String, language: String): String {
        // Ищем код в блоках ```language ... ```
        val codeBlockRegex = Regex("""```(?:$language|kotlin|java|python|javascript|typescript|cpp|c)\s*\n(.*?)\n```""", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockRegex.find(response)

        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Ищем любые блоки кода
        val genericCodeBlockRegex = Regex("""```\s*\n(.*?)\n```""", RegexOption.DOT_MATCHES_ALL)
        val genericMatch = genericCodeBlockRegex.find(response)

        if (genericMatch != null) {
            return genericMatch.groupValues[1].trim()
        }

        // Если блоков кода нет, возвращаем весь ответ (возможно код без разметки)
        return response.trim()
    }

    private fun extractExplanationFromResponse(response: String): String {
        // Извлекаем текст вне блоков кода
        val codeBlocks = Regex("""```.*?```""", RegexOption.DOT_MATCHES_ALL).findAll(response)
        val codeBlockRanges = codeBlocks.map { it.range }.toSet()

        val explanationParts = mutableListOf<String>()
        var currentStart = 0

        codeBlockRanges.forEach { range ->
            if (currentStart < range.start) {
                val text = response.substring(currentStart, range.start).trim()
                if (text.isNotBlank()) {
                    explanationParts.add(text)
                }
            }
            currentStart = range.endInclusive + 1
        }

        // Добавляем текст после последнего блока кода
        if (currentStart < response.length) {
            val text = response.substring(currentStart).trim()
            if (text.isNotBlank()) {
                explanationParts.add(text)
            }
        }

        return explanationParts.joinToString("\n\n")
    }

    private fun extractDependencies(code: String, language: String): List<String> {
        return when (language.lowercase()) {
            "kotlin", "java" -> {
                val importRegex = Regex("""import\s+([\w.]+)""")
                importRegex.findAll(code).map { it.groupValues[1] }.toList()
            }
            "python" -> {
                val importRegex = Regex("""import\s+(\w+)""")
                val fromImportRegex = Regex("""from\s+(\w+)\s+import""")
                (importRegex.findAll(code).map { it.groupValues[1] } +
                 fromImportRegex.findAll(code).map { it.groupValues[1] }).toList()
            }
            else -> emptyList()
        }
    }
}