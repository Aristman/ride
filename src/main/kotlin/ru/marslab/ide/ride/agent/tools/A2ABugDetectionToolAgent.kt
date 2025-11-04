package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.BaseA2AAgent
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.llm.LLMParameters
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A2A агент для поиска багов в коде
 *
 * Независимая реализация, которая анализирует исходный код на наличие потенциальных багов,
 * уязвимостей и проблем с качеством кода с использованием LLM.
 */
class A2ABugDetectionToolAgent(
    private val llmProvider: LLMProvider
) : BaseA2AAgent(
    agentType = AgentType.BUG_DETECTION,
    a2aAgentId = "a2a-bug-detection-agent",
    supportedMessageTypes = setOf(
        "BUG_ANALYSIS_REQUEST",
        "CODE_REVIEW_REQUEST",
        "VULNERABILITY_SCAN_REQUEST"
    ),
    publishedEventTypes = setOf("TOOL_EXECUTION_STARTED", "TOOL_EXECUTION_COMPLETED", "TOOL_EXECUTION_FAILED")
) {

    // Кэш для результатов анализа
    private val analysisCache = ConcurrentHashMap<String, BugAnalysisResult>()

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        return try {
            when (request.messageType) {
                "BUG_ANALYSIS_REQUEST" -> handleBugAnalysisRequest(request, messageBus)
                "CODE_REVIEW_REQUEST" -> handleCodeReviewRequest(request, messageBus)
                "VULNERABILITY_SCAN_REQUEST" -> handleVulnerabilityScanRequest(request, messageBus)
                else -> createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
            }
        } catch (e: Exception) {
            logger.error("Error in bug detection agent", e)
            createErrorResponse(request.id, "Bug detection failed: ${e.message}")
        }
    }

    private suspend fun handleBugAnalysisRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val files = data["files"] as? List<String> ?: emptyList()
        val projectPath = data["project_path"] as? String
        val includeTests = data["include_tests"] as? Boolean ?: false
        val maxFiles = data["max_files"] as? Int ?: 50

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

        val analysis = withContext(Dispatchers.Default) {
            val filesToAnalyze = if (files.isNotEmpty()) {
                files
            } else {
                findCodeFiles(projectPath ?: ".", includeTests, maxFiles)
            }

            performBugAnalysis(filesToAnalyze, data)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Bug analysis completed: ${analysis.totalIssues} issues found"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "BUG_ANALYSIS_RESULT",
                data = mapOf(
                    "analysis" to analysis,
                    "metadata" to mapOf(
                        "agent" to "BUG_DETECTION",
                        "files_analyzed" to files.size,
                        "analysis_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleCodeReviewRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val code = data["code"] as? String ?: ""
        val language = data["language"] as? String ?: "kotlin"
        val filePath = data["file_path"] as? String

        if (code.isBlank()) {
            return createErrorResponse(request.id, "No code provided for review")
        }

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
            performCodeReview(code, language, filePath)
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

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "CODE_REVIEW_RESULT",
                data = mapOf(
                    "review" to review,
                    "metadata" to mapOf(
                        "agent" to "BUG_DETECTION",
                        "language" to language,
                        "review_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleVulnerabilityScanRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val files = data["files"] as? List<String> ?: emptyList()
        val scanTypes = data["scan_types"] as? List<String> ?: listOf("sql_injection", "xss", "authentication")

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

        val scan = withContext(Dispatchers.Default) {
            performVulnerabilityScan(files, scanTypes)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Vulnerability scan completed: ${scan.totalVulnerabilities} found"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "VULNERABILITY_SCAN_RESULT",
                data = mapOf(
                    "scan" to scan,
                    "metadata" to mapOf(
                        "agent" to "BUG_DETECTION",
                        "scan_types" to scanTypes,
                        "scan_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
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

    private suspend fun performBugAnalysis(files: List<String>, config: Map<String, Any>): BugAnalysisResult {
        val maxFiles = (config["max_files"] as? Int) ?: 50
        val includeTests = (config["include_tests"] as? Boolean) ?: false
        val filesToAnalyze = files.take(maxFiles)

        val allIssues = mutableListOf<BugIssue>()
        val languageStats = mutableMapOf<String, Int>()
        var totalLines = 0

        for (filePath in filesToAnalyze) {
            try {
                val file = File(filePath)
                if (!file.exists() || !file.isFile) continue

                val content = file.readText()
                val lines = content.lines()
                totalLines += lines.size

                val language = detectLanguage(file.extension)
                languageStats[language] = languageStats.getOrDefault(language, 0) + 1

                // Статический анализ без LLM
                val staticIssues = performStaticAnalysis(content, filePath, language)
                allIssues.addAll(staticIssues)

                // LLM анализ для важных файлов
                if (shouldUseLLMAnalysis(filePath, content)) {
                    val llmIssues = performLLMBugAnalysis(content, filePath, language)
                    allIssues.addAll(llmIssues)
                }

            } catch (e: Exception) {
                logger.warn("Error analyzing file: $filePath", e)
            }
        }

        // Кэшируем результат
        val cacheKey = filesToAnalyze.joinToString(",")
        val result = BugAnalysisResult(
            totalIssues = allIssues.size,
            criticalIssues = allIssues.count { it.severity == BugSeverity.CRITICAL },
            highIssues = allIssues.count { it.severity == BugSeverity.HIGH },
            mediumIssues = allIssues.count { it.severity == BugSeverity.MEDIUM },
            lowIssues = allIssues.count { it.severity == BugSeverity.LOW },
            issues = allIssues.groupBy { it.category },
            languageDistribution = languageStats,
            filesAnalyzed = filesToAnalyze.size,
            totalLines = totalLines,
            recommendations = generateBugRecommendations(allIssues),
            analysisTimestamp = System.currentTimeMillis()
        )
        analysisCache[cacheKey] = result

        return result
    }

    private fun performStaticAnalysis(code: String, filePath: String, language: String): List<BugIssue> {
        val issues = mutableListOf<BugIssue>()
        val lines = code.lines()

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val trimmedLine = line.trim()

            // Анализ пустых catch блоков
            if (trimmedLine.matches(Regex("""^\s*\}\s*catch\s*\(\s*[^)]*\)\s*\{\s*\}\s*$"""))) {
                issues.add(BugIssue(
                    type = BugType.RESOURCE_LEAK,
                    severity = BugSeverity.HIGH,
                    category = "Error Handling",
                    file = filePath,
                    line = lineNumber,
                    message = "Empty catch block detected",
                    description = "Catch block without proper error handling can hide exceptions",
                    suggestion = "Add proper logging or error handling in catch block"
                ))
            }

            // Анализ потенциальных null pointer exceptions
            when (language) {
                "kotlin", "java" -> {
                    if (trimmedLine.contains("!!") && !trimmedLine.contains("?:")) {
                        issues.add(BugIssue(
                            type = BugType.NULL_POINTER,
                            severity = BugSeverity.MEDIUM,
                            category = "Null Safety",
                            file = filePath,
                            line = lineNumber,
                            message = "Potential null pointer exception with !! operator",
                            description = "Using !! operator can cause NPE if the value is null",
                            suggestion = "Consider using safe calls (?.) or Elvis operator (?:)"
                        ))
                    }
                }
                "javascript", "typescript" -> {
                    if (trimmedLine.matches(Regex(""".*\.\w+\s*\[\s*\w+\s*\]\s*"""))) {
                        issues.add(BugIssue(
                            type = BugType.NULL_POINTER,
                            severity = BugSeverity.MEDIUM,
                            category = "Null Safety",
                            file = filePath,
                            line = lineNumber,
                            message = "Potential undefined property access",
                            description = "Property access without null check may cause runtime errors",
                            suggestion = "Add null checks or use optional chaining (?.)"
                        ))
                    }
                }
            }

            // Анализ hardcoded паролей/ключей
            if (trimmedLine.contains("password") || trimmedLine.contains("secret") ||
                trimmedLine.contains("key") && trimmedLine.contains("=")) {
                if (trimmedLine.matches(Regex(""".*(?:password|secret|key)\s*=\s*["'][^"']+["'].*""", RegexOption.IGNORE_CASE))) {
                    issues.add(BugIssue(
                        type = BugType.SECURITY,
                        severity = BugSeverity.CRITICAL,
                        category = "Security",
                        file = filePath,
                        line = lineNumber,
                        message = "Hardcoded credentials detected",
                        description = "Hardcoded passwords, secrets, or keys are security risks",
                        suggestion = "Use environment variables or secure configuration files"
                    ))
                }
            }

            // Анализ SQL инъекций
            if (trimmedLine.contains("SELECT") || trimmedLine.contains("INSERT") ||
                trimmedLine.contains("UPDATE") || trimmedLine.contains("DELETE")) {
                if (trimmedLine.contains("+") && trimmedLine.contains("\"")) {
                    issues.add(BugIssue(
                        type = BugType.SQL_INJECTION,
                        severity = BugSeverity.HIGH,
                        category = "Security",
                        file = filePath,
                        line = lineNumber,
                        message = "Potential SQL injection vulnerability",
                        description = "String concatenation in SQL queries can lead to SQL injection",
                        suggestion = "Use parameterized queries or prepared statements"
                    ))
                }
            }
        }

        return issues
    }

    private suspend fun performLLMBugAnalysis(code: String, filePath: String, language: String): List<BugIssue> {
        val cacheKey = "${filePath}_${code.hashCode()}"
        analysisCache[cacheKey]?.let { return emptyList() } // Простая кэшизация

        val baseSystemPrompt = buildBugAnalysisSystemPrompt(language)
        val systemPromptWithRules = applyRulesToPrompt(baseSystemPrompt)
        val userPrompt = buildBugAnalysisUserPrompt(code, filePath, language)

        try {
            val response = llmProvider.sendRequest(
                systemPrompt = systemPromptWithRules,
                userMessage = userPrompt,
                conversationHistory = emptyList(),
                parameters = LLMParameters.PRECISE.copy(
                    temperature = 0.2,
                    maxTokens = 1500
                )
            )

            return parseLLMBugAnalysisResponse(response.content, filePath)

        } catch (e: Exception) {
            logger.warn("LLM bug analysis failed for $filePath", e)
            return emptyList()
        }
    }

    private fun buildBugAnalysisSystemPrompt(language: String): String {
        return """You are an expert code reviewer specializing in bug detection for $language code.

Your task is to analyze the provided code and identify potential bugs, security vulnerabilities, and code quality issues.

Focus on detecting:
1. Null pointer exceptions and null safety issues
2. Resource leaks (unclosed files, database connections, etc.)
3. Security vulnerabilities (SQL injection, XSS, hardcoded secrets)
4. Logic errors and potential runtime failures
5. Performance issues and inefficiencies
6. Concurrency problems (race conditions, deadlocks)
7. Error handling problems

For each issue found, provide:
- Issue type and severity (CRITICAL/HIGH/MEDIUM/LOW)
- Category (Security, Performance, Logic, Resource Management, etc.)
- Clear description of the problem
- Specific suggestion for fixing it

Format your response as JSON with the following structure:
{
  "issues": [
    {
      "type": "NULL_POINTER|RESOURCE_LEAK|SECURITY|LOGIC_ERROR|PERFORMANCE|CONCURRENCY",
      "severity": "CRITICAL|HIGH|MEDIUM|LOW",
      "category": "string",
      "line": 0,
      "message": "Brief description",
      "description": "Detailed explanation",
      "suggestion": "Specific fix recommendation"
    }
  ],
  "summary": {
    "total_issues": 0,
    "risk_level": "LOW|MEDIUM|HIGH|CRITICAL"
  }
}"""
    }

    private fun buildBugAnalysisUserPrompt(code: String, filePath: String, language: String): String {
        val codeSample = if (code.length > 2000) code.take(2000) + "\n... (truncated)" else code

        return """Please analyze the following $language code for potential bugs and issues:

File: $filePath

```$language
$codeSample
```

Focus on identifying real bugs and potential runtime failures rather than style issues. Provide the analysis in the JSON format specified in the system prompt."""
    }

    private fun parseLLMBugAnalysisResponse(response: String, filePath: String): List<BugIssue> {
        try {
            // Пытаемся извлечь JSON из ответа
            val jsonMatch = Regex("""\{[\s\S]*\}""").find(response)
            val jsonResponse = jsonMatch?.groupValues?.get(0) ?: response

            // Здесь будет логика парсинга JSON, но для простоты возвращаем базовый анализ
            if (jsonResponse.contains("issues") || jsonResponse.contains("summary")) {
                // Парсинг JSON был бы здесь, но временно возвращаем пустой список
                return emptyList()
            } else {
                // Текстовый ответ - ищем ключевые слова проблем
                return extractIssuesFromText(response, filePath)
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse LLM bug analysis response", e)
            return emptyList()
        }
    }

    private fun extractIssuesFromText(response: String, filePath: String): List<BugIssue> {
        val issues = mutableListOf<BugIssue>()
        val lines = response.lines()

        lines.forEachIndexed { index, line ->
            val text = line.lowercase()
            when {
                text.contains("null pointer") || text.contains("null safety") -> {
                    issues.add(BugIssue(
                        type = BugType.NULL_POINTER,
                        severity = BugSeverity.MEDIUM,
                        category = "Null Safety",
                        file = filePath,
                        line = 0, // Не можем определить точную строку
                        message = "Potential null pointer issue detected",
                        description = "LLM identified a potential null pointer problem",
                        suggestion = "Add null checks or use safe operators"
                    ))
                }
                text.contains("security") || text.contains("vulnerability") -> {
                    issues.add(BugIssue(
                        type = BugType.SECURITY,
                        severity = BugSeverity.HIGH,
                        category = "Security",
                        file = filePath,
                        line = 0,
                        message = "Security vulnerability detected",
                        description = "LLM identified a potential security issue",
                        suggestion = "Review and fix the security vulnerability"
                    ))
                }
                text.contains("performance") || text.contains("inefficient") -> {
                    issues.add(BugIssue(
                        type = BugType.PERFORMANCE,
                        severity = BugSeverity.MEDIUM,
                        category = "Performance",
                        file = filePath,
                        line = 0,
                        message = "Performance issue detected",
                        description = "LLM identified a performance problem",
                        suggestion = "Optimize the identified performance issue"
                    ))
                }
            }
        }

        return issues
    }

    private fun performCodeReview(code: String, language: String, filePath: String?): CodeReviewResult {
        val lines = code.lines()
        val issues = performStaticAnalysis(code, filePath ?: "", language)

        val score = calculateCodeReviewScore(issues, lines.size)
        val categories = analyzeCodeCategories(code, language)

        return CodeReviewResult(
            filePath = filePath ?: "",
            language = language,
            score = score,
            totalIssues = issues.size,
            issues = issues.groupBy { it.severity },
            categories = categories,
            recommendations = generateCodeReviewRecommendations(issues),
            metrics = CodeMetrics(
                totalLines = lines.size,
                codeLines = lines.count { it.trim().isNotEmpty() },
                commentLines = lines.count { it.trim().startsWith("//") || it.trim().startsWith("/*") },
                complexity = calculateComplexity(code, language),
                maintainabilityIndex = calculateMaintainabilityIndex(issues, lines.size)
            ),
            reviewTimestamp = System.currentTimeMillis()
        )
    }

    private fun performVulnerabilityScan(files: List<String>, scanTypes: List<String>): VulnerabilityScanResult {
        val vulnerabilities = mutableListOf<Vulnerability>()
        val scanStats = mutableMapOf<String, Int>()

        files.forEach { filePath ->
            try {
                val file = File(filePath)
                if (!file.exists()) return@forEach

                val content = file.readText()
                val fileVulnerabilities = scanForVulnerabilities(content, filePath, scanTypes)
                vulnerabilities.addAll(fileVulnerabilities)

                fileVulnerabilities.forEach { vuln ->
                    scanStats[vuln.type.name] = scanStats.getOrDefault(vuln.type.name, 0) + 1
                }

            } catch (e: Exception) {
                logger.warn("Error scanning file for vulnerabilities: $filePath", e)
            }
        }

        return VulnerabilityScanResult(
            totalVulnerabilities = vulnerabilities.size,
            criticalVulnerabilities = vulnerabilities.count { it.severity == VulnerabilitySeverity.CRITICAL },
            highVulnerabilities = vulnerabilities.count { it.severity == VulnerabilitySeverity.HIGH },
            mediumVulnerabilities = vulnerabilities.count { it.severity == VulnerabilitySeverity.MEDIUM },
            lowVulnerabilities = vulnerabilities.count { it.severity == VulnerabilitySeverity.LOW },
            vulnerabilities = vulnerabilities.groupBy { it.type },
            scanTypes = scanTypes,
            filesScanned = files.size,
            scanTimestamp = System.currentTimeMillis()
        )
    }

    private fun scanForVulnerabilities(code: String, filePath: String, scanTypes: List<String>): List<Vulnerability> {
        val vulnerabilities = mutableListOf<Vulnerability>()
        val lines = code.lines()

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val trimmedLine = line.trim()

            if (scanTypes.contains("sql_injection")) {
                if (trimmedLine.matches(Regex(""".*(?:select|insert|update|delete)\s+.*\+.*["'].*""", RegexOption.IGNORE_CASE))) {
                    vulnerabilities.add(Vulnerability(
                        type = VulnerabilityType.SQL_INJECTION,
                        severity = VulnerabilitySeverity.HIGH,
                        file = filePath,
                        line = lineNumber,
                        description = "Potential SQL injection vulnerability",
                        recommendation = "Use parameterized queries or prepared statements",
                        cwe = "CWE-89"
                    ))
                }
            }

            if (scanTypes.contains("xss")) {
                if (trimmedLine.contains("innerHTML") || trimmedLine.contains("document.write")) {
                    vulnerabilities.add(Vulnerability(
                        type = VulnerabilityType.XSS,
                        severity = VulnerabilitySeverity.HIGH,
                        file = filePath,
                        line = lineNumber,
                        description = "Potential XSS vulnerability",
                        recommendation = "Sanitize user input before rendering HTML",
                        cwe = "CWE-79"
                    ))
                }
            }

            if (scanTypes.contains("authentication")) {
                if (trimmedLine.matches(Regex(""".*password\s*=\s*["'][^"']+["'].*""", RegexOption.IGNORE_CASE))) {
                    vulnerabilities.add(Vulnerability(
                        type = VulnerabilityType.HARD_CODED_CREDENTIALS,
                        severity = VulnerabilitySeverity.CRITICAL,
                        file = filePath,
                        line = lineNumber,
                        description = "Hardcoded password detected",
                        recommendation = "Use secure authentication mechanisms and environment variables",
                        cwe = "CWE-798"
                    ))
                }
            }
        }

        return vulnerabilities
    }

    // Вспомогательные функции
    private fun findCodeFiles(projectPath: String, includeTests: Boolean, maxFiles: Int): List<String> {
        val root = File(projectPath)
        val codeExtensions = setOf(".kt", ".java", ".js", ".ts", ".py", ".cpp", ".c", ".cs", ".php", ".rb")
        val files = mutableListOf<String>()

        fun scan(dir: File) {
            if (files.size >= maxFiles) return

            dir.listFiles()?.forEach { file ->
                if (files.size >= maxFiles) return@forEach

                if (file.isDirectory && !file.name.startsWith(".") &&
                    file.name != "build" && file.name != "target" && file.name != "node_modules" &&
                    (includeTests || !file.name.contains("test"))) {
                    scan(file)
                } else if (file.isFile && codeExtensions.any { file.name.endsWith(it) }) {
                    files.add(file.absolutePath)
                }
            }
        }

        scan(root)
        return files
    }

    private fun detectLanguage(extension: String): String {
        return when (extension.lowercase()) {
            ".kt", ".kts" -> "kotlin"
            ".java" -> "java"
            ".js" -> "javascript"
            ".ts" -> "typescript"
            ".py" -> "python"
            ".cpp", ".cc", ".cxx" -> "cpp"
            ".c" -> "c"
            ".cs" -> "csharp"
            ".php" -> "php"
            ".rb" -> "ruby"
            else -> "unknown"
        }
    }

    private fun shouldUseLLMAnalysis(filePath: String, code: String): Boolean {
        // Используем LLM анализ только для важных файлов и если код не слишком большой
        val extension = File(filePath).extension
        val importantExtensions = setOf("kt", "java", "py", "js", "ts")

        return importantExtensions.contains(extension) &&
               code.length < 5000 &&
               !filePath.contains("test") &&
               !filePath.contains("generated")
    }

    private fun calculateCodeReviewScore(issues: List<BugIssue>, totalLines: Int): Double {
        val baseScore = 100.0
        val severityPenalties = mapOf(
            BugSeverity.CRITICAL to 20.0,
            BugSeverity.HIGH to 10.0,
            BugSeverity.MEDIUM to 5.0,
            BugSeverity.LOW to 1.0
        )

        val totalPenalty = issues.sumOf { issue ->
            severityPenalties[issue.severity] ?: 0.0
        }

        val score = maxOf(0.0, baseScore - totalPenalty)
        return minOf(100.0, score)
    }

    private fun analyzeCodeCategories(code: String, language: String): Map<String, Int> {
        val categories = mutableMapOf<String, Int>()

        // Простая эвристика для определения категорий кода
        if (code.contains("class ") || code.contains("interface ")) categories["OOP"] = 1
        if (code.contains("async ") || code.contains("await ")) categories["Async"] = 1
        if (code.contains("SELECT ") || code.contains("INSERT ")) categories["Database"] = 1
        if (code.contains("http.") || code.contains("fetch(")) categories["Network"] = 1
        if (code.contains("test") || code.contains("Test")) categories["Testing"] = 1

        return categories
    }

    private fun generateCodeReviewRecommendations(issues: List<BugIssue>): List<String> {
        val recommendations = mutableListOf<String>()

        val severityCounts = issues.groupBy { it.severity }
        if (severityCounts[BugSeverity.CRITICAL]?.isNotEmpty() == true) {
            recommendations.add("Address critical issues immediately as they can cause system failures")
        }

        if (severityCounts[BugSeverity.HIGH]?.isNotEmpty() == true) {
            recommendations.add("Prioritize high severity issues to prevent major problems")
        }

        val categoryCounts = issues.groupBy { it.category }
        if (categoryCounts["Security"]?.isNotEmpty() == true) {
            recommendations.add("Review and fix security vulnerabilities to protect against attacks")
        }

        if (categoryCounts["Performance"]?.isNotEmpty() == true) {
            recommendations.add("Consider performance optimizations to improve application speed")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Good code quality! Continue following best practices.")
        }

        return recommendations
    }

    private fun calculateComplexity(code: String, language: String): Int {
        // Простая метрика сложности на основе ключевых слов
        val complexityKeywords = when (language) {
            "kotlin", "java" -> listOf("if", "else", "for", "while", "switch", "case", "try", "catch", "&&", "||")
            "python" -> listOf("if", "else", "for", "while", "try", "except", "and", "or", "not")
            "javascript", "typescript" -> listOf("if", "else", "for", "while", "switch", "case", "try", "catch", "&&", "||")
            else -> emptyList()
        }

        return complexityKeywords.sumOf { keyword ->
            Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE).findAll(code).count()
        }
    }

    private fun calculateMaintainabilityIndex(issues: List<BugIssue>, totalLines: Int): Double {
        val baseScore = 100.0
        val densityPenalty = (issues.size.toDouble() / totalLines) * 100
        return maxOf(0.0, baseScore - densityPenalty)
    }

    private fun generateBugRecommendations(issues: List<BugIssue>): List<String> {
        val recommendations = mutableListOf<String>()

        val criticalCount = issues.count { it.severity == BugSeverity.CRITICAL }
        if (criticalCount > 0) {
            recommendations.add("Fix $criticalCount critical issues immediately to prevent system failures")
        }

        val securityIssues = issues.filter { it.category == "Security" }
        if (securityIssues.isNotEmpty()) {
            recommendations.add("Address ${securityIssues.size} security vulnerabilities to protect against attacks")
        }

        val resourceLeaks = issues.filter { it.type == BugType.RESOURCE_LEAK }
        if (resourceLeaks.isNotEmpty()) {
            recommendations.add("Fix ${resourceLeaks.size} potential resource leaks to prevent memory issues")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Good job! No major bugs detected. Continue following best practices.")
        }

        return recommendations
    }

    // Data classes
    data class BugAnalysisResult(
        val totalIssues: Int,
        val criticalIssues: Int,
        val highIssues: Int,
        val mediumIssues: Int,
        val lowIssues: Int,
        val issues: Map<String, List<BugIssue>>,
        val languageDistribution: Map<String, Int>,
        val filesAnalyzed: Int,
        val totalLines: Int,
        val recommendations: List<String>,
        val analysisTimestamp: Long
    )

    data class CodeReviewResult(
        val filePath: String,
        val language: String,
        val score: Double,
        val totalIssues: Int,
        val issues: Map<BugSeverity, List<BugIssue>>,
        val categories: Map<String, Int>,
        val recommendations: List<String>,
        val metrics: CodeMetrics,
        val reviewTimestamp: Long
    )

    data class VulnerabilityScanResult(
        val totalVulnerabilities: Int,
        val criticalVulnerabilities: Int,
        val highVulnerabilities: Int,
        val mediumVulnerabilities: Int,
        val lowVulnerabilities: Int,
        val vulnerabilities: Map<VulnerabilityType, List<Vulnerability>>,
        val scanTypes: List<String>,
        val filesScanned: Int,
        val scanTimestamp: Long
    )

    data class CodeMetrics(
        val totalLines: Int,
        val codeLines: Int,
        val commentLines: Int,
        val complexity: Int,
        val maintainabilityIndex: Double
    )

    data class BugIssue(
        val type: BugType,
        val severity: BugSeverity,
        val category: String,
        val file: String,
        val line: Int,
        val message: String,
        val description: String,
        val suggestion: String
    )

    data class Vulnerability(
        val type: VulnerabilityType,
        val severity: VulnerabilitySeverity,
        val file: String,
        val line: Int,
        val description: String,
        val recommendation: String,
        val cwe: String
    )

    enum class BugType {
        NULL_POINTER, RESOURCE_LEAK, SECURITY, LOGIC_ERROR, PERFORMANCE, CONCURRENCY, SQL_INJECTION
    }

    enum class BugSeverity {
        CRITICAL, HIGH, MEDIUM, LOW
    }

    enum class VulnerabilityType {
        SQL_INJECTION, XSS, HARD_CODED_CREDENTIALS, INSECURE_CRYPTO, PATH_TRAVERSAL
    }

    enum class VulnerabilitySeverity {
        CRITICAL, HIGH, MEDIUM, LOW
    }
}