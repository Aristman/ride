package ru.marslab.ide.ride.agent.tools

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.StepOutput
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import java.io.File

/**
 * Универсальный LLM агент для обзора кода в разных языках.
 * Вход: files (List<String>), опционально maxFindingsPerFile, maxCharsPerFile
 * Выход: StepOutput.of("findings" to List<Map<String,Any>>)
 */
class LLMCodeReviewToolAgent(
    private val llmProvider: LLMProvider,
) : BaseToolAgent(
    agentType = AgentType.LLM_REVIEW,
    toolCapabilities = setOf("llm_review", "multi_language", "code_smells", "bug_suspicion")
) {
    private val log = Logger.getInstance(LLMCodeReviewToolAgent::class.java)

    override fun getDescription(): String = "LLM-based multi-language code review agent"

    override fun validateInput(input: StepInput): ru.marslab.ide.ride.agent.ValidationResult {
        val files = input.getList<String>("files")
        if (files.isNullOrEmpty()) {
            // Fallback: если нет files, пытаемся работать с другими параметрами
            val request = input.getString("request")
            if (!request.isNullOrBlank()) {
                log.info("LLM_REVIEW fallback: using request parameter instead of files")
                return ru.marslab.ide.ride.agent.ValidationResult.success()
            }
            return ru.marslab.ide.ride.agent.ValidationResult.failure("files is required and must not be empty")
        }
        return ru.marslab.ide.ride.agent.ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val files = step.input.getList<String>("files")?.distinct() ?: emptyList()
        val maxFindingsPerFile = step.input.getInt("maxFindingsPerFile") ?: 20
        val maxCharsPerFile = step.input.getInt("maxCharsPerFile") ?: 8000

        val allFindings = mutableListOf<Map<String, Any>>()

        // Входные логи
        log.info("LLM_REVIEW input: files=${files.size}, maxFindingsPerFile=$maxFindingsPerFile, maxCharsPerFile=$maxCharsPerFile")
        files.take(10).forEach { log.info("LLM_REVIEW file: $it") }

        // Fallback: если нет файлов, используем запрос для генерации ответа
        if (files.isEmpty()) {
            val request = step.input.getString("request") ?: "Анализ кода"
            log.info("LLM_REVIEW fallback: processing request without files: $request")

            val prompt = buildString {
                appendLine("Проанализируй следующий запрос и предоставь рекомендации по улучшению кода:")
                appendLine()
                appendLine("Запрос: $request")
                appendLine()
                appendLine("Верни JSON с ключом 'findings' и массивом объектов с полями:")
                appendLine("file (string), line (number|null), severity (critical/high/medium/low), rule (string), message (string), suggestion (string)")
            }

            val response = withContext(Dispatchers.IO) {
                llmProvider.sendRequest(
                    systemPrompt = "You are a senior code reviewer. Provide ONLY JSON as requested.",
                    userMessage = prompt,
                    conversationHistory = emptyList(),
                    parameters = LLMParameters.PRECISE
                )
            }

            if (response.success) {
                val findings = parseFindings(response.content, "general_analysis")
                return StepResult.success(
                    output = StepOutput.of(
                        "findings" to findings,
                        "total" to findings.size,
                        "mode" to "fallback_analysis"
                    )
                )
            } else {
                return StepResult.error("Failed to process fallback request: ${response.error}")
            }
        }

        for (path in files) {
            val file = File(path)
            if (!file.exists() || !file.isFile) continue

            val language = detectLanguageByExt(file.extension)
            val content = safeRead(file, maxCharsPerFile)
            if (content.isBlank()) continue

            val prompt = buildPrompt(language, path, content, maxFindingsPerFile)

            // Логи промпта (усеченно)
            val preview = prompt.take(400).replace("\n", "\\n")
            log.info("LLM_REVIEW prompt preview (file=$path, lang=$language, codeChars=${content.length}): $preview ...")

            val response = withContext(Dispatchers.IO) {
                // Логи длины запросов
                log.info("LLM_REVIEW sendRequest: systemPromptLen=${SYSTEM_PROMPT.length}, userMessageLen=${prompt.length}")
                llmProvider.sendRequest(
                    systemPrompt = SYSTEM_PROMPT,
                    userMessage = prompt,
                    conversationHistory = emptyList(),
                    parameters = LLMParameters.PRECISE
                )
            }

            if (!response.success) {
                log.warn("LLM review failed for $path: ${response.error}")
                continue
            }

            val findings = parseFindings(response.content, path)
            if (findings.isNotEmpty()) {
                allFindings.addAll(findings.take(maxFindingsPerFile))
            }
        }

        return StepResult.success(
            output = StepOutput.of(
                "findings" to allFindings,
                "total" to allFindings.size
            )
        )
    }

    private fun detectLanguageByExt(ext: String?): String = when (ext?.lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js" -> "javascript"
        "ts" -> "typescript"
        "py" -> "python"
        "go" -> "go"
        "rs" -> "rust"
        "cpp", "cc", "cxx", "hpp", "h" -> "cpp"
        "cs" -> "csharp"
        else -> "plain"
    }

    private fun safeRead(file: File, maxChars: Int): String = try {
        file.inputStream().bufferedReader().use { it.readText() }.take(maxChars)
    } catch (_: Exception) {
        ""
    }

    private fun buildPrompt(language: String, path: String, code: String, maxFindings: Int): String = buildString {
        appendLine("Analyze the following ${'$'}language code and return potential bugs and code smells.")
        appendLine("Return STRICT JSON with key 'findings' which is an array of objects with fields:")
        appendLine("file (string), line (number|null), severity (one of: critical, high, medium, low), rule (string), message (string), suggestion (string). Do not include any text outside JSON.")
        appendLine()
        appendLine("Constraints:")
        appendLine("- Max findings: ${'$'}maxFindings")
        appendLine("- If line is unknown, use null")
        appendLine()
        appendLine("File: ${'$'}path")
        appendLine("Language: ${'$'}language")
        appendLine("Code snippet:\n```${'$'}language\n${'$'}code\n```")
    }

    private fun parseFindings(content: String, path: String): List<Map<String, Any>> {
        val jsonBlock = extractJson(content) ?: return emptyList()
        return try {
            val findingsRaw = Regex("""\{\s*\"findings\"\s*:\s*\[(.*)]\s*}\\""", RegexOption.DOT_MATCHES_ALL)
                .find(jsonBlock)?.groupValues?.getOrNull(1) ?: run {
                // fallback без завершающей кавычки (если провайдер вернул чистый JSON)
                Regex("""\{\s*\"findings\"\s*:\s*\[(.*)]\s*}""", RegexOption.DOT_MATCHES_ALL)
                    .find(jsonBlock)?.groupValues?.getOrNull(1) ?: return emptyList()
            }

            val objs = splitObjects(findingsRaw)
            objs.mapNotNull { obj ->
                val map = mutableMapOf<String, Any>("file" to path, "source" to "llm")
                map["message"] =
                    Regex(""""message"\s*:\s*"(.*?)"""").find(obj)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
                map["severity"] = Regex(""""severity"\s*:\s*"(.*?)"""").find(obj)?.groupValues?.getOrNull(1) ?: "medium"
                map["rule"] = Regex(""""rule"\s*:\s*"(.*?)"""").find(obj)?.groupValues?.getOrNull(1) ?: "llm_suggestion"
                val line = Regex(""""line"\s*:\s*(\d+|null)""").find(obj)?.groupValues?.getOrNull(1)
                map["line"] = line?.toIntOrNull() ?: 0
                map["suggestion"] = Regex(""""suggestion"\s*:\s*"(.*?)"""").find(obj)?.groupValues?.getOrNull(1) ?: ""
                map
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractJson(text: String): String? {
        val m = Regex("""\{[\n\r\t\s\S]*}.?""").find(text)
        return m?.value ?: Regex("""\{[\n\r\t\s\S]*}""").find(text)?.value
    }

    private fun splitObjects(raw: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        for ((i, ch) in raw.withIndex()) {
            if (ch == '{') depth++
            if (ch == '}') depth--
            if (depth == 0 && i >= start) {
                val segment = raw.substring(start, i + 1).trim()
                if (segment.isNotEmpty()) parts.add(segment)
                start = i + 1
                while (start < raw.length && (raw[start] == ',' || raw[start].isWhitespace())) start++
            }
        }
        return parts
    }

    companion object {
        private const val SYSTEM_PROMPT = "You are a senior code reviewer. Provide ONLY JSON as requested."
    }
}
