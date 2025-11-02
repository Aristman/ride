package ru.marslab.ide.ride.orchestrator.impl

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.orchestrator.RequestAnalyzer

/**
 * Реализация RequestAnalyzer на основе LLM
 *
 * Использует языковую модель для анализа пользовательских запросов,
 * определения типа задачи и необходимых инструментов.
 */
class LLMRequestAnalyzer(
    private val llmProvider: LLMProvider,
    private val uncertaintyAnalyzer: UncertaintyAnalyzer = UncertaintyAnalyzer
) : RequestAnalyzer {

    private val logger = Logger.getInstance(LLMRequestAnalyzer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override val name = "LLMRequestAnalyzer"
    override val version = "1.0.0"

    override suspend fun analyze(request: UserRequest): RequestAnalysis {
        logger.info("Analyzing request with LLM: ${request.originalRequest.take(100)}...")

        try {
            val analysisPrompt = buildAnalysisPrompt(request)

            val llmResponse = llmProvider.sendRequest(
                systemPrompt = SYSTEM_PROMPT,
                userMessage = analysisPrompt,
                conversationHistory = emptyList(),
                parameters = LLMParameters(
                    temperature = 0.1,
                    maxTokens = 1000
                )
            )

            if (!llmResponse.success) {
                logger.error("LLM analysis failed: ${llmResponse.error}")
                return createFallbackAnalysis(request)
            }

            // Извлекаем JSON из ответа
            val jsonContent = extractJsonFromResponse(llmResponse.content)
            if (jsonContent == null) {
                logger.warn("Failed to extract JSON from LLM response")
                return createFallbackAnalysis(request)
            }

            val analysisData = json.decodeFromString(JsonObject.serializer(), jsonContent)

            // Парсим результаты
            val taskType = parseTaskType(analysisData)
            val parsedRequiredTools = parseRequiredTools(analysisData)
            val estimatedComplexity = parseComplexityLevel(analysisData)
            val estimatedSteps = parseEstimatedSteps(analysisData)
            val requiresUserInput = parseRequiresUserInput(analysisData)
            val confidence = parseConfidence(analysisData)
            val reasoning = parseReasoning(analysisData)
            val parameters = parseParameters(analysisData, request)

            // Анализируем неопределенность ответа (передаем пустой контекст, т.к. анализируем только ответ)
            val emptyChatContext = ChatContext(
                project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
                    ?: throw IllegalStateException("No open project"),
                history = emptyList()
            )
            val uncertaintyScore = uncertaintyAnalyzer.analyzeUncertainty(llmResponse.content, emptyChatContext)
            val finalConfidence = if (uncertaintyScore > UNCERTAINTY_THRESHOLD) {
                confidence * (1.0 - uncertaintyScore)
            } else {
                confidence
            }

            // Обогащаем список инструментов на основе типа задачи, чтобы обеспечить полноту плана
            val finalRequiredTools = enrichRequiredTools(taskType, parsedRequiredTools)

            val result = RequestAnalysis(
                taskType = taskType,
                requiredTools = finalRequiredTools,
                context = request.context,
                parameters = parameters,
                requiresUserInput = requiresUserInput || uncertaintyScore > UNCERTAINTY_THRESHOLD,
                estimatedComplexity = estimatedComplexity,
                estimatedSteps = estimatedSteps,
                confidence = finalConfidence,
                reasoning = reasoning
            )

            logger.info("Analysis completed: ${result.taskType}, confidence: ${result.confidence}")
            return result

        } catch (e: Exception) {
            logger.error("Error during request analysis", e)
            return createFallbackAnalysis(request)
        }
    }

    override fun canHandle(request: UserRequest): Boolean {
        // LLM анализатор может обрабатывать любые текстовые запросы
        return request.originalRequest.isNotBlank()
    }

    /**
     * Создает промпт для анализа запроса
     */
    private fun buildAnalysisPrompt(request: UserRequest): String {
        return buildString {
            appendLine("Проанализируй следующий пользовательский запрос:")
            appendLine("Запрос: \"${request.originalRequest}\"")
            appendLine()

            if (request.context.projectPath != null) {
                appendLine("Контекст проекта: ${request.context.projectPath}")
            }

            if (request.context.selectedFiles.isNotEmpty()) {
                appendLine("Выбранные файлы: ${request.context.selectedFiles.joinToString(", ")}")
            }

            if (request.conversationHistory.isNotEmpty()) {
                appendLine("История диалога:")
                request.conversationHistory.takeLast(3).forEach { msg ->
                    appendLine("- $msg")
                }
            }

            appendLine()
            appendLine("Определи тип задачи, необходимые инструменты и параметры выполнения.")
            appendLine("Верни результат в виде JSON согласно схеме.")
        }
    }

    /**
     * Извлекает JSON из текстового ответа LLM
     */
    private fun extractJsonFromResponse(content: String): String? {
        // Ищем JSON блок в ответе
        val jsonRegex = Regex("""\{[\s\S]*\}""")
        val match = jsonRegex.find(content)
        return match?.value
    }

    /**
     * Создает базовый анализ в случае ошибки LLM
     */
    private fun createFallbackAnalysis(request: UserRequest): RequestAnalysis {
        val requestLower = request.originalRequest.lowercase()

        val (taskType, requiredTools) = when {
            requestLower.contains("анализ") || requestLower.contains("проверь") ->
                TaskType.CODE_ANALYSIS to setOf(AgentType.PROJECT_SCANNER, AgentType.BUG_DETECTION)

            requestLower.contains("рефактор") || requestLower.contains("улучш") ->
                TaskType.REFACTORING to setOf(AgentType.CODE_QUALITY, AgentType.ARCHITECTURE_ANALYSIS)

            requestLower.contains("баг") || requestLower.contains("ошибк") ->
                TaskType.BUG_FIX to setOf(AgentType.BUG_DETECTION, AgentType.CODE_FIXER)

            requestLower.contains("отчет") || requestLower.contains("report") ->
                TaskType.REPORT_GENERATION to setOf(AgentType.REPORT_GENERATOR)

            else ->
                TaskType.SIMPLE_QUERY to emptySet()
        }

        return RequestAnalysis(
            taskType = taskType,
            requiredTools = requiredTools,
            context = request.context,
            parameters = mapOf("original_request" to request.originalRequest),
            requiresUserInput = true, // Требуем уточнение при fallback
            estimatedComplexity = ComplexityLevel.MEDIUM,
            estimatedSteps = 3,
            confidence = 0.3, // Низкая уверенность при fallback
            reasoning = "Fallback analysis due to LLM error"
        )
    }

    // Методы для парсинга полей из JSON
    private fun parseTaskType(data: JsonObject): TaskType {
        return data["task_type"]?.jsonPrimitive?.content?.let { type ->
            try {
                TaskType.valueOf(type.uppercase().replace(" ", "_"))
            } catch (e: IllegalArgumentException) {
                logger.warn("Unknown task type: $type, using CODE_ANALYSIS")
                TaskType.CODE_ANALYSIS
            }
        } ?: TaskType.CODE_ANALYSIS
    }

    private fun parseRequiredTools(data: JsonObject): Set<AgentType> {
        val toolsArray = data["required_tools"]?.jsonObject?.get("tools")?.jsonObject
            ?.get("list")?.jsonObject?.get("value")?.toString()

        if (toolsArray != null) {
            try {
                val toolsList = json.decodeFromString<List<String>>(toolsArray)
                return toolsList.mapNotNull { tool ->
                    try {
                        AgentType.valueOf(tool.uppercase().replace(" ", "_"))
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Unknown agent type: $tool")
                        null
                    }
                }.toSet()
            } catch (e: Exception) {
                logger.warn("Failed to parse required tools", e)
            }
        }

        return emptySet()
    }

    private fun parseComplexityLevel(data: JsonObject): ComplexityLevel {
        return data["complexity"]?.jsonPrimitive?.content?.let { complexity ->
            try {
                ComplexityLevel.valueOf(complexity.uppercase())
            } catch (e: IllegalArgumentException) {
                ComplexityLevel.MEDIUM
            }
        } ?: ComplexityLevel.MEDIUM
    }

    private fun parseEstimatedSteps(data: JsonObject): Int {
        return data["estimated_steps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 3
    }

    private fun parseRequiresUserInput(data: JsonObject): Boolean {
        return data["requires_user_input"]?.jsonPrimitive?.content == "true"
    }

    private fun parseConfidence(data: JsonObject): Double {
        return data["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.8
    }

    private fun parseReasoning(data: JsonObject): String {
        return data["reasoning"]?.jsonPrimitive?.content ?: ""
    }

    private fun parseParameters(data: JsonObject, request: UserRequest): Map<String, Any> {
        val parameters = mutableMapOf<String, Any>()
        parameters["original_request"] = request.originalRequest

        // Добавляем дополнительные параметры из анализа
        data["parameters"]?.jsonObject?.forEach { (key, value) ->
            parameters[key] = value.jsonPrimitive.content
        }

        return parameters
    }

    /**
     * Обогащает список инструментов на основе типа задачи, но позволяет агентам
     * динамически определять следующие шаги выполнения.
     *
     * Важно: не добавляем REPORT_GENERATOR автоматически - пусть сами агенты
     * решают, когда нужно генерировать отчет.
     */
    private fun enrichRequiredTools(
        taskType: TaskType,
        parsed: Set<AgentType>
    ): Set<AgentType> {
        val tools = parsed.toMutableSet()

        when (taskType) {
            TaskType.BUG_FIX -> {
                // Для исправления багов нужно сначала найти проблемы
                if (!tools.contains(AgentType.PROJECT_SCANNER)) {
                    tools.add(AgentType.PROJECT_SCANNER)
                }
                if (!tools.contains(AgentType.BUG_DETECTION)) {
                    tools.add(AgentType.BUG_DETECTION)
                }
            }

            TaskType.CODE_ANALYSIS -> {
                // Для анализа кода нужен сканер проекта
                if (!tools.contains(AgentType.PROJECT_SCANNER)) {
                    tools.add(AgentType.PROJECT_SCANNER)
                }
            }

            TaskType.ARCHITECTURE_ANALYSIS -> {
                // Для архитектурного анализа нужен сканер
                if (!tools.contains(AgentType.PROJECT_SCANNER)) {
                    tools.add(AgentType.PROJECT_SCANNER)
                }
            }

            TaskType.PERFORMANCE_OPTIMIZATION -> {
                // Для оптимизации производительности нужен сканер
                if (!tools.contains(AgentType.PROJECT_SCANNER)) {
                    tools.add(AgentType.PROJECT_SCANNER)
                }
            }

            TaskType.SIMPLE_QUERY -> {
                // Для простых запросов не добавляем ничего лишнего
                // Позволяем LLM-анализу определить нужные инструменты
                // Например, для "открой файл" нужен только FILE_OPERATIONS
            }

            // Для остальных типов сохраняем как есть, позволяя динамическое планирование
            else -> {
                // Ничего не добавляем автоматически
            }
        }

        return tools
    }

    companion object {
        private const val SYSTEM_PROMPT = """
            Ты - эксперт по анализу запросов в системе разработки ПО.

            Проанализируй пользовательский запрос и определи:
            1. Тип задачи (CODE_ANALYSIS, REFACTORING, BUG_FIX, REPORT_GENERATION, COMPLEX_MULTI_STEP, SIMPLE_QUERY, ARCHITECTURE_ANALYSIS, TESTING, DOCUMENTATION, MIGRATION, PERFORMANCE_OPTIMIZATION)
            2. Необходимые инструменты (PROJECT_SCANNER, CODE_CHUNKER, BUG_DETECTION, CODE_QUALITY, ARCHITECTURE_ANALYSIS, CODE_FIXER, REPORT_GENERATOR, USER_INTERACTION, FILE_OPERATIONS, GIT_OPERATIONS, TEST_GENERATOR, DOCUMENTATION_GENERATOR, PERFORMANCE_ANALYZER)
            3. Сложность (LOW, MEDIUM, HIGH, VERY_HIGH)
            4. Оценочное количество шагов
            5. Требуется ли ввод от пользователя
            6. Уверенность в анализе (0.0 - 1.0)
            7. Обоснование принятых решений

            ВАЖНЫЕ ПРАВИЛА ВЫБОРА ИНСТРУМЕНТОВ:

            • Для запросов на открытие/просмотр/редактирование файлов используй только FILE_OPERATIONS
            • Для запросов на анализ кода, структуры проекта, архитектуры используй PROJECT_SCANNER
            • Для поиска багов и проблем используй BUG_DETECTION
            • Для генерации отчетов используй REPORT_GENERATOR только если явно запрошен отчет
            • НЕ добавляй лишние инструменты, если они не нужны для выполнения запроса

            Примеры:
            - "Открой файл README.md" → SIMPLE_QUERY + [FILE_OPERATIONS]
            - "Проанализируй качество кода" → CODE_ANALYSIS + [PROJECT_SCANNER, CODE_QUALITY]
            - "Найди баги в проекте" → BUG_FIX + [PROJECT_SCANNER, BUG_DETECTION]
            - "Создай отчет о проекте" → REPORT_GENERATION + [PROJECT_SCANNER, REPORT_GENERATOR]

            Верни результат в виде JSON:
            {
              "task_type": "TASK_TYPE",
              "required_tools": {
                "tools": {
                  "list": {
                    "value": ["TOOL_1", "TOOL_2"]
                  }
                }
              },
              "complexity": "COMPLEXITY_LEVEL",
              "estimated_steps": 3,
              "requires_user_input": false,
              "confidence": 0.9,
              "reasoning": "Обоснование выбора типа задачи и инструментов",
              "parameters": {
                "focus_area": "описание области фокуса",
                "priority": "high/medium/low"
              }
            }
        """

        private const val UNCERTAINTY_THRESHOLD = 0.3
    }
}