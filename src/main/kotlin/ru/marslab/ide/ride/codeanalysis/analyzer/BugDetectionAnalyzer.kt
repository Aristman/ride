package ru.marslab.ide.ride.codeanalysis.analyzer

import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.codeanalysis.Finding
import ru.marslab.ide.ride.model.codeanalysis.FindingType
import ru.marslab.ide.ride.model.codeanalysis.Severity
import ru.marslab.ide.ride.model.llm.LLMParameters
import java.util.*

/**
 * Анализатор для поиска багов и потенциальных проблем в коде
 */
class BugDetectionAnalyzer(
    private val llmProvider: LLMProvider
) {
    /**
     * Анализирует код на наличие багов
     *
     * @param code Код для анализа
     * @param filePath Путь к файлу
     * @return Список найденных проблем
     */
    suspend fun analyze(code: String, filePath: String): List<Finding> {
        println("          BugDetectionAnalyzer.analyze() called for: $filePath")
        val language = detectLanguage(filePath)
        println("          Detected language: $language")

        val prompt = buildBugDetectionPrompt(code, filePath, language)
        println("          Prompt length: ${prompt.length} chars")

        println("          Sending request to LLM...")
        val response = llmProvider.sendRequest(
            systemPrompt = BUG_DETECTION_SYSTEM_PROMPT,
            userMessage = prompt,
            conversationHistory = emptyList(),
            parameters = LLMParameters.PRECISE
        )

        println("          LLM response received. Success: ${response.success}")
        if (!response.success) {
            println("          LLM request failed: ${response.error}")
            return emptyList()
        }

        println("          Response content length: ${response.content.length} chars")
        println("          Parsing findings...")
        val findings = parseFindingsFromResponse(response.content, filePath, FindingType.BUG)
        println("          Parsed ${findings.size} findings")

        return findings
    }

    /**
     * Определяет язык программирования по расширению файла
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
            "cs" -> "csharp"
            "rb" -> "ruby"
            "php" -> "php"
            "swift" -> "swift"
            "scala" -> "scala"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            else -> "text"
        }
    }

    /**
     * Строит промпт для поиска багов
     */
    private fun buildBugDetectionPrompt(code: String, filePath: String, language: String): String {
        return """
        Проанализируй следующий код на наличие очевидных багов и потенциальных проблем.
        
        Файл: $filePath
        Язык: $language
        
        ```$language
        $code
        ```
        
        Найди типичные проблемы для $language:
        - Null pointer exceptions / null safety issues
        - Resource leaks (незакрытые файлы, соединения, потоки)
        - Неправильная обработка ошибок
        - Race conditions и проблемы многопоточности
        - Логические ошибки
        - Неиспользуемый код
        - Memory leaks
        - Проблемы безопасности
        
        Для каждой проблемы укажи в формате:
        LINE: <номер строки>
        SEVERITY: <CRITICAL/HIGH/MEDIUM/LOW>
        TITLE: <краткое название проблемы>
        DESCRIPTION: <описание проблемы>
        SUGGESTION: <рекомендация по исправлению>
        ---
        
        Если проблем не найдено, напиши: NO_ISSUES_FOUND
        """.trimIndent()
    }

    /**
     * Парсит ответ LLM и извлекает найденные проблемы
     */
    private fun parseFindingsFromResponse(
        response: String,
        filePath: String,
        findingType: FindingType
    ): List<Finding> {
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
                            line = textLine.substringAfter(":").trim().toIntOrNull()
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
                            type = findingType,
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
                // Пропускаем блоки с ошибками парсинга
                continue
            }
        }

        return findings
    }

    /**
     * Парсит уровень серьезности из строки
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
        private const val BUG_DETECTION_SYSTEM_PROMPT = """
        Ты - эксперт по анализу кода и поиску багов во всех популярных языках программирования.
        Твоя задача - найти потенциальные проблемы в коде и предложить решения.
        
        Учитывай специфику каждого языка:
        - Для Kotlin/Java: null safety, resource management, concurrency
        - Для Python: type hints, exception handling, memory management
        - Для JavaScript/TypeScript: async/await, promise handling, type safety
        - Для C/C++: memory leaks, buffer overflows, pointer issues
        - Для Go: goroutine leaks, error handling, race conditions
        - Для Rust: ownership, borrowing, lifetime issues
        
        Будь точным и конкретным. Указывай номера строк и фрагменты кода.
        Оценивай серьезность проблем объективно с учетом контекста языка.
        
        Отвечай строго в указанном формате.
        """
    }
}
