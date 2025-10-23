package ru.marslab.ide.ride.codeanalysis.analyzer

import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.codeanalysis.Finding
import ru.marslab.ide.ride.model.codeanalysis.FindingType
import ru.marslab.ide.ride.model.codeanalysis.Severity
import ru.marslab.ide.ride.model.llm.LLMParameters
import java.util.*

/**
 * Анализатор качества кода
 */
class CodeQualityAnalyzer(
    private val llmProvider: LLMProvider
) {
    /**
     * Анализирует качество кода
     *
     * @param code Код для анализа
     * @param filePath Путь к файлу
     * @return Список найденных проблем качества
     */
    suspend fun analyze(code: String, filePath: String): List<Finding> {
        println("          CodeQualityAnalyzer.analyze() called for: $filePath")
        val language = detectLanguage(filePath)
        println("          Detected language: $language")

        val prompt = buildCodeQualityPrompt(code, filePath, language)
        println("          Prompt length: ${prompt.length} chars")

        println("          Sending request to LLM...")
        val response = llmProvider.sendRequest(
            systemPrompt = CODE_QUALITY_SYSTEM_PROMPT,
            userMessage = prompt,
            conversationHistory = emptyList(),
            parameters = LLMParameters.BALANCED
        )

        println("          LLM response received. Success: ${response.success}")
        if (!response.success) {
            println("          LLM request failed: ${response.error}")
            return emptyList()
        }

        println("          Response content length: ${response.content.length} chars")
        println("          Parsing findings...")
        val findings = parseFindingsFromResponse(response.content, filePath)
        println("          Parsed ${findings.size} findings")

        return findings
    }

    /**
     * Определяет язык программирования
     */
    private fun detectLanguage(filePath: String): String {
        return when (filePath.substringAfterLast('.', "")) {
            "kt" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "go" -> "go"
            "rs" -> "rust"
            "cpp", "cc", "cxx" -> "cpp"
            "c" -> "c"
            else -> "text"
        }
    }

    /**
     * Строит промпт для анализа качества
     */
    private fun buildCodeQualityPrompt(code: String, filePath: String, language: String): String {
        return """
        Проанализируй качество следующего кода и найди code smells.
        
        Файл: $filePath
        Язык: $language
        
        ```$language
        $code
        ```
        
        Проверь на:
        - Code smells (Long Method, God Class, Duplicate Code, etc.)
        - Нарушения принципов SOLID
        - Плохое именование переменных и функций
        - Избыточная сложность
        - Отсутствие комментариев для сложной логики
        - Магические числа и строки
        - Неиспользуемые импорты и переменные
        
        Для каждой проблемы укажи в формате:
        LINE: <номер строки или N/A>
        SEVERITY: <HIGH/MEDIUM/LOW>
        TITLE: <краткое название проблемы>
        DESCRIPTION: <описание проблемы>
        SUGGESTION: <рекомендация по исправлению>
        ---
        
        Если проблем не найдено, напиши: NO_ISSUES_FOUND
        """.trimIndent()
    }

    /**
     * Парсит ответ LLM
     */
    private fun parseFindingsFromResponse(response: String, filePath: String): List<Finding> {
        if (response.contains("NO_ISSUES_FOUND", ignoreCase = true)) {
            return emptyList()
        }

        val findings = mutableListOf<Finding>()
        val blocks = response.split("---").map { it.trim() }.filter { it.isNotEmpty() }

        for (block in blocks) {
            try {
                val lines = block.lines()
                var line: Int? = null
                var severity = Severity.MEDIUM
                var title = ""
                var description = ""
                var suggestion: String? = null

                for (textLine in lines) {
                    when {
                        textLine.startsWith("LINE:", ignoreCase = true) -> {
                            val lineStr = textLine.substringAfter(":").trim()
                            line = if (lineStr.equals("N/A", ignoreCase = true)) null else lineStr.toIntOrNull()
                        }

                        textLine.startsWith("SEVERITY:", ignoreCase = true) -> {
                            val severityStr = textLine.substringAfter(":").trim()
                            severity = parseSeverity(severityStr)
                        }

                        textLine.startsWith("TITLE:", ignoreCase = true) -> {
                            title = textLine.substringAfter(":").trim()
                        }

                        textLine.startsWith("DESCRIPTION:", ignoreCase = true) -> {
                            description = textLine.substringAfter(":").trim()
                        }

                        textLine.startsWith("SUGGESTION:", ignoreCase = true) -> {
                            suggestion = textLine.substringAfter(":").trim()
                        }
                    }
                }

                if (title.isNotEmpty() && description.isNotEmpty()) {
                    findings.add(
                        Finding(
                            id = UUID.randomUUID().toString(),
                            type = FindingType.CODE_SMELL,
                            severity = severity,
                            file = filePath,
                            line = line,
                            title = title,
                            description = description,
                            suggestion = suggestion,
                            codeSnippet = null
                        )
                    )
                }
            } catch (e: Exception) {
                continue
            }
        }

        return findings
    }

    /**
     * Парсит уровень серьезности
     */
    private fun parseSeverity(severityStr: String): Severity {
        return when (severityStr.uppercase()) {
            "CRITICAL" -> Severity.CRITICAL
            "HIGH" -> Severity.HIGH
            "MEDIUM" -> Severity.MEDIUM
            "LOW" -> Severity.LOW
            "INFO" -> Severity.INFO
            else -> Severity.MEDIUM
        }
    }

    companion object {
        private const val CODE_QUALITY_SYSTEM_PROMPT = """
        Ты - эксперт по качеству кода и code review.
        Твоя задача - находить code smells и нарушения best practices.
        
        Фокусируйся на:
        - Читаемости и поддерживаемости кода
        - Соблюдении принципов SOLID и DRY
        - Правильном именовании
        - Оптимальной сложности
        
        Будь конструктивным и предлагай конкретные улучшения.
        Отвечай строго в указанном формате.
        """
    }
}
