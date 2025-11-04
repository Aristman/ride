package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.llm.LLMParameters

/**
 * A2A агент для генерации кода
 *
 * Независимая реализация для генерации различных типов кода:
 * классов, функций, алгоритмов, тестов и т.д.
 */
class A2ACodeGeneratorToolAgent(
    private val llmProvider: LLMProvider
) : BaseA2AAgent(
    agentType = AgentType.CODE_GENERATOR,
    a2aAgentId = "a2a-code-generator-agent",
    supportedMessageTypes = setOf(
        "CODE_GENERATION_REQUEST",
        "CLASS_GENERATION_REQUEST",
        "FUNCTION_GENERATION_REQUEST",
        "ALGORITHM_GENERATION_REQUEST",
        "TEST_GENERATION_REQUEST",
        "CODE_REFACTORING_REQUEST"
    ),
    publishedEventTypes = setOf(
        "TOOL_EXECUTION_STARTED",
        "TOOL_EXECUTION_COMPLETED",
        "TOOL_EXECUTION_FAILED"
    )
) {

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        return try {
            when (request.messageType) {
                "CODE_GENERATION_REQUEST" -> handleCodeGenerationRequest(request, messageBus)
                "CLASS_GENERATION_REQUEST" -> handleClassGenerationRequest(request, messageBus)
                "FUNCTION_GENERATION_REQUEST" -> handleFunctionGenerationRequest(request, messageBus)
                "ALGORITHM_GENERATION_REQUEST" -> handleAlgorithmGenerationRequest(request, messageBus)
                "TEST_GENERATION_REQUEST" -> handleTestGenerationRequest(request, messageBus)
                "CODE_REFACTORING_REQUEST" -> handleCodeRefactoringRequest(request, messageBus)
                else -> createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
            }
        } catch (e: Exception) {
            logger.error("Error in code generator", e)
            createErrorResponse(request.id, "Code generation failed: ${e.message}")
        }
    }

    private suspend fun handleCodeGenerationRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val description = data["description"] as? String ?: ""
        val language = data["language"] as? String ?: "kotlin"
        val context = data["context"] as? String ?: ""
        val files = data["files"] as? List<String> ?: emptyList()
        val requirements = data["requirements"] as? List<String> ?: emptyList()

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

        val result = withContext(Dispatchers.Default) {
            generateCode(description, language, context, files, requirements)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Code generation completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "CODE_GENERATION_RESULT",
                data = mapOf<String, Any>(
                    "generated_code" to result.code,
                    "explanation" to result.explanation,
                    "language" to language,
                    "dependencies" to result.dependencies,
                    "file_suggestions" to result.fileSuggestions,
                    "test_cases" to result.testCases,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "CODE_GENERATOR",
                        "generation_type" to "general",
                        "tokens_used" to result.tokensUsed,
                        "confidence" to result.confidence,
                        "generation_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleClassGenerationRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val className = data["class_name"] as? String ?: ""
        val responsibilities = data["responsibilities"] as? List<String> ?: emptyList()
        val properties = data["properties"] as? List<String> ?: emptyList()
        val methods = data["methods"] as? List<String> ?: emptyList()
        val language = data["language"] as? String ?: "kotlin"
        val designPattern = data["design_pattern"] as? String
        val parentClass = data["parent_class"] as? String
        val interfaces = data["interfaces"] as? List<String> ?: emptyList()

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

        val result = withContext(Dispatchers.Default) {
            generateClass(
                className, responsibilities, properties, methods,
                language, designPattern, parentClass, interfaces
            )
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Class generation completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "CLASS_GENERATION_RESULT",
                data = mapOf<String, Any>(
                    "generated_class" to result.code,
                    "explanation" to result.explanation,
                    "class_name" to className,
                    "file_path" to result.filePath,
                    "dependencies" to result.dependencies,
                    "usage_example" to result.usageExample,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "CODE_GENERATOR",
                        "generation_type" to "class",
                        "design_pattern" to (designPattern ?: ""),
                        "is_abstract" to result.isAbstract,
                        "is_data_class" to result.isDataClass,
                        "tokens_used" to result.tokensUsed
                    )
                )
            )
        )
    }

    private suspend fun handleFunctionGenerationRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val functionName = data["function_name"] as? String ?: ""
        val description = data["description"] as? String ?: ""
        val parameters = data["parameters"] as? List<Map<String, Any>> ?: emptyList()
        val returnType = data["return_type"] as? String
        val language = data["language"] as? String ?: "kotlin"
        val algorithm = data["algorithm"] as? String
        val complexity = data["complexity"] as? String ?: "O(n)"

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

        val result = withContext(Dispatchers.Default) {
            generateFunction(functionName, description, parameters, returnType, language, algorithm, complexity)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Function generation completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "FUNCTION_GENERATION_RESULT",
                data = mapOf<String, Any>(
                    "generated_function" to result.code,
                    "explanation" to result.explanation,
                    "function_name" to functionName,
                    "parameters" to parameters,
                    "return_type" to (returnType ?: ""),
                    "complexity" to complexity,
                    "algorithm" to (algorithm ?: ""),
                    "usage_example" to result.usageExample,
                    "test_cases" to result.testCases,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "CODE_GENERATOR",
                        "generation_type" to "function",
                        "is_recursive" to result.isRecursive,
                        "is_pure" to result.isPure,
                        "tokens_used" to result.tokensUsed
                    )
                )
            )
        )
    }

    private suspend fun handleAlgorithmGenerationRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val algorithmType = data["algorithm_type"] as? String ?: ""
        val problemDescription = data["problem_description"] as? String ?: ""
        val inputFormat = data["input_format"] as? String ?: ""
        val outputFormat = data["output_format"] as? String ?: ""
        val constraints = data["constraints"] as? List<String> ?: emptyList()
        val language = data["language"] as? String ?: "kotlin"
        val timeComplexity = data["target_time_complexity"] as? String
        val spaceComplexity = data["target_space_complexity"] as? String

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

        val result = withContext(Dispatchers.Default) {
            generateAlgorithm(
                algorithmType, problemDescription, inputFormat, outputFormat,
                constraints, language, timeComplexity, spaceComplexity
            )
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Algorithm generation completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "ALGORITHM_GENERATION_RESULT",
                data = mapOf<String, Any>(
                    "generated_algorithm" to result.code,
                    "explanation" to result.explanation,
                    "algorithm_type" to algorithmType,
                    "actual_time_complexity" to result.timeComplexity,
                    "actual_space_complexity" to result.spaceComplexity,
                    "optimization_notes" to result.optimizationNotes,
                    "test_cases" to result.testCases,
                    "benchmarks" to result.benchmarks,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "CODE_GENERATOR",
                        "generation_type" to "algorithm",
                        "is_optimized" to result.isOptimized,
                        "uses_standard_library" to result.usesStandardLibrary,
                        "tokens_used" to result.tokensUsed
                    )
                )
            )
        )
    }

    private suspend fun handleTestGenerationRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val sourceCode = data["source_code"] as? String ?: ""
        val codeType = data["code_type"] as? String ?: "function"
        val testFramework = data["test_framework"] as? String ?: "JUnit"
        val language = data["language"] as? String ?: "kotlin"
        val coverageTarget = data["coverage_target"] as? Double ?: 80.0

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

        val result = withContext(Dispatchers.Default) {
            generateTests(sourceCode, codeType, testFramework, language, coverageTarget)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Test generation completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "TEST_GENERATION_RESULT",
                data = mapOf<String, Any>(
                    "generated_tests" to result.code,
                    "explanation" to result.explanation,
                    "test_framework" to testFramework,
                    "test_cases_count" to result.testCasesCount,
                    "estimated_coverage" to result.estimatedCoverage,
                    "test_classes" to result.testClasses,
                    "mock_objects" to result.mockObjects,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "CODE_GENERATOR",
                        "generation_type" to "test",
                        "includes_edge_cases" to result.includesEdgeCases,
                        "includes_performance_tests" to result.includesPerformanceTests,
                        "tokens_used" to result.tokensUsed
                    )
                )
            )
        )
    }

    private suspend fun handleCodeRefactoringRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val sourceCode = data["source_code"] as? String ?: ""
        val refactoringType = data["refactoring_type"] as? String ?: ""
        val goals = data["goals"] as? List<String> ?: emptyList()
        val language = data["language"] as? String ?: "kotlin"
        val preserveBehavior = data["preserve_behavior"] as? Boolean ?: true

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

        val result = withContext(Dispatchers.Default) {
            refactorCode(sourceCode, refactoringType, goals, language, preserveBehavior)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Code refactoring completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "CODE_REFACTORING_RESULT",
                data = mapOf<String, Any>(
                    "refactored_code" to result.code,
                    "explanation" to result.explanation,
                    "refactoring_type" to refactoringType,
                    "changes_summary" to result.changesSummary,
                    "improvements" to result.improvements,
                    "risks" to result.risks,
                    "backup_suggestion" to result.backupSuggestion,
                    "metadata" to mapOf<String, Any>(
                        "agent" to "CODE_GENERATOR",
                        "generation_type" to "refactoring",
                        "is_breaking_change" to result.isBreakingChange,
                        "complexity_reduction" to result.complexityReduction,
                        "tokens_used" to result.tokensUsed
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

    // Реальные методы генерации кода

    private suspend fun generateCode(
        description: String,
        language: String,
        context: String,
        files: List<String>,
        requirements: List<String>
    ): CodeGenerationResult = withContext(Dispatchers.Default) {
        val prompt = buildString {
            appendLine("Generate $language code based on the following description:")
            appendLine(description)
            if (context.isNotBlank()) {
                appendLine("\nContext:")
                appendLine(context)
            }
            if (files.isNotEmpty()) {
                appendLine("\nRelated files:")
                files.forEach { appendLine("- $it") }
            }
            if (requirements.isNotEmpty()) {
                appendLine("\nRequirements:")
                requirements.forEach { appendLine("- $it") }
            }
            appendLine("\nProvide clean, well-documented code with explanations.")
            appendLine("Also suggest file structure and any dependencies needed.")
        }

        try {
            val baseSystemPrompt = "You are a senior software developer. Generate high-quality, production-ready code with proper documentation."
            val systemPromptWithRules = applyRulesToPrompt(baseSystemPrompt)

            val response = llmProvider.sendRequest(
                systemPrompt = systemPromptWithRules,
                userMessage = prompt,
                conversationHistory = emptyList(),
                LLMParameters()
            )

            // Улучшенный парсинг ответа LLM
            val parts = response.content.split("```")
            val code = if (parts.size >= 2) {
                // Извлекаем код между тройными кавычками
                parts[1].trim()
            } else {
                // Если нет кодовых блоков, проверяем есть ли вообще код в ответе
                val content = response.content.trim()
                if (content.contains("fun ") || content.contains("class ") || content.contains("interface ") ||
                    content.contains("object ") || content.contains("{") && content.contains("}")) {
                    content
                } else {
                    // Если кода нет, возможно это описание структуры файлов
                    ""
                }
            }

            val explanation = if (parts.size >= 3) {
                parts[2].trim()
            } else {
                // Если нет отдельного объяснения, ищем его после кода или в начале
                if (code.isNotEmpty() && response.content.contains(code)) {
                    val afterCode = response.content.substringAfter(code).trim()
                    if (afterCode.isNotEmpty()) afterCode else "Generated code based on requirements."
                } else {
                    response.content.take(200) + (if (response.content.length > 200) "..." else "")
                }
            }

            CodeGenerationResult(
                code = code.trim(),
                explanation = explanation,
                dependencies = extractDependencies(code),
                fileSuggestions = generateFileSuggestions(code, language),
                testCases = extractTestCases(response.content),
                tokensUsed = response.tokenUsage.totalTokens,
                confidence = calculateConfidence(response.content)
            )
        } catch (e: Exception) {
            logger.error("Code generation failed", e)
            CodeGenerationResult(
                code = "",
                explanation = "Code generation failed: ${e.message}",
                dependencies = emptyList(),
                fileSuggestions = emptyList(),
                testCases = emptyList(),
                tokensUsed = 0,
                confidence = 0.0
            )
        }
    }

    private suspend fun generateClass(
        className: String,
        responsibilities: List<String>,
        properties: List<String>,
        methods: List<String>,
        language: String,
        designPattern: String?,
        parentClass: String?,
        interfaces: List<String>
    ): ClassGenerationResult = withContext(Dispatchers.Default) {
        val prompt = buildString {
            appendLine("Generate a $language class named '$className':")
            if (responsibilities.isNotEmpty()) {
                appendLine("Responsibilities:")
                responsibilities.forEach { appendLine("- $it") }
            }
            if (properties.isNotEmpty()) {
                appendLine("Properties:")
                properties.forEach { appendLine("- $it") }
            }
            if (methods.isNotEmpty()) {
                appendLine("Methods:")
                methods.forEach { appendLine("- $it") }
            }
            if (designPattern != null) {
                appendLine("Design Pattern: $designPattern")
            }
            if (parentClass != null) {
                appendLine("Extends: $parentClass")
            }
            if (interfaces.isNotEmpty()) {
                appendLine("Implements: ${interfaces.joinToString(", ")}")
            }
            appendLine("\nProvide complete implementation with proper documentation and usage example.")
        }

        try {
            val response = llmProvider.sendRequest(
                systemPrompt = "You are an OOP expert. Generate well-designed classes following SOLID principles.",
                userMessage = prompt,
                conversationHistory = emptyList(),
                LLMParameters()
            )

            val parts = response.content.split("```")
            val code = if (parts.size >= 2) parts[1] else response.content
            val explanation = if (parts.size >= 3) parts[2].trim() else "Generated class implementation."

            ClassGenerationResult(
                code = code.trim(),
                explanation = explanation,
                filePath = suggestFilePath(className, language),
                dependencies = extractDependencies(code),
                usageExample = extractUsageExample(response.content),
                isAbstract = code.contains("abstract class") || code.contains("abstract fun"),
                isDataClass = code.contains("data class"),
                tokensUsed = response.tokenUsage.totalTokens
            )
        } catch (e: Exception) {
            logger.error("Class generation failed", e)
            ClassGenerationResult(
                code = "",
                explanation = "Class generation failed: ${e.message}",
                filePath = "",
                dependencies = emptyList(),
                usageExample = "",
                isAbstract = false,
                isDataClass = false,
                tokensUsed = 0
            )
        }
    }

    private suspend fun generateFunction(
        functionName: String,
        description: String,
        parameters: List<Map<String, Any>>,
        returnType: String?,
        language: String,
        algorithm: String?,
        complexity: String
    ): FunctionGenerationResult = withContext(Dispatchers.Default) {
        val prompt = buildString {
            appendLine("Generate a $language function named '$functionName':")
            appendLine("Description: $description")
            if (parameters.isNotEmpty()) {
                appendLine("Parameters:")
                parameters.forEach { param ->
                    val name = param["name"] as? String ?: ""
                    val type = param["type"] as? String ?: "Any"
                    val desc = param["description"] as? String ?: ""
                    appendLine("- $name: $type ($desc)")
                }
            }
            if (returnType != null) {
                appendLine("Return Type: $returnType")
            }
            if (algorithm != null) {
                appendLine("Algorithm: $algorithm")
            }
            appendLine("Target Complexity: $complexity")
            appendLine("\nProvide implementation with documentation, usage example, and test cases.")
        }

        try {
            val response = llmProvider.sendRequest(
                systemPrompt = "You are an algorithm expert. Generate efficient, well-documented functions.",
                userMessage = prompt,
                conversationHistory = emptyList(),
                LLMParameters()
            )

            val parts = response.content.split("```")
            val code = if (parts.size >= 2) parts[1] else response.content
            val explanation = if (parts.size >= 3) parts[2].trim() else "Generated function implementation."

            FunctionGenerationResult(
                code = code.trim(),
                explanation = explanation,
                usageExample = extractUsageExample(response.content),
                testCases = extractTestCases(response.content),
                isRecursive = code.contains("fun $functionName(") && code.contains("$functionName("),
                isPure = !code.contains("var ") && !code.contains("mut ") && !code.contains("global"),
                tokensUsed = response.tokenUsage.totalTokens
            )
        } catch (e: Exception) {
            logger.error("Function generation failed", e)
            FunctionGenerationResult(
                code = "",
                explanation = "Function generation failed: ${e.message}",
                usageExample = "",
                testCases = emptyList(),
                isRecursive = false,
                isPure = false,
                tokensUsed = 0
            )
        }
    }

    private suspend fun generateAlgorithm(
        algorithmType: String,
        problemDescription: String,
        inputFormat: String,
        outputFormat: String,
        constraints: List<String>,
        language: String,
        timeComplexity: String?,
        spaceComplexity: String?
    ): AlgorithmGenerationResult = withContext(Dispatchers.Default) {
        val prompt = buildString {
            appendLine("Generate a $language algorithm for $algorithmType:")
            appendLine("Problem: $problemDescription")
            appendLine("Input Format: $inputFormat")
            appendLine("Output Format: $outputFormat")
            if (constraints.isNotEmpty()) {
                appendLine("Constraints:")
                constraints.forEach { appendLine("- $it") }
            }
            if (timeComplexity != null) {
                appendLine("Target Time Complexity: $timeComplexity")
            }
            if (spaceComplexity != null) {
                appendLine("Target Space Complexity: $spaceComplexity")
            }
            appendLine("\nProvide optimized implementation with complexity analysis and test cases.")
        }

        try {
            val response = llmProvider.sendRequest(
                systemPrompt = "You are an algorithm expert. Generate optimized algorithms with proper complexity analysis.",
                userMessage = prompt,
                conversationHistory = emptyList(),
                LLMParameters()
            )

            val parts = response.content.split("```")
            val code = if (parts.size >= 2) parts[1] else response.content
            val explanation = if (parts.size >= 3) parts[2].trim() else "Generated algorithm implementation."

            AlgorithmGenerationResult(
                code = code.trim(),
                explanation = explanation,
                timeComplexity = extractComplexity(response.content, "Time"),
                spaceComplexity = extractComplexity(response.content, "Space"),
                optimizationNotes = extractOptimizationNotes(response.content),
                testCases = extractTestCases(response.content),
                benchmarks = extractBenchmarks(response.content),
                isOptimized = code.contains("optim") || explanation.contains("optim"),
                usesStandardLibrary = usesStandardLibrary(code, language),
                tokensUsed = response.tokenUsage.totalTokens
            )
        } catch (e: Exception) {
            logger.error("Algorithm generation failed", e)
            AlgorithmGenerationResult(
                code = "",
                explanation = "Algorithm generation failed: ${e.message}",
                timeComplexity = "Unknown",
                spaceComplexity = "Unknown",
                optimizationNotes = "",
                testCases = emptyList(),
                benchmarks = emptyList(),
                isOptimized = false,
                usesStandardLibrary = false,
                tokensUsed = 0
            )
        }
    }

    private suspend fun generateTests(
        sourceCode: String,
        codeType: String,
        testFramework: String,
        language: String,
        coverageTarget: Double
    ): TestGenerationResult = withContext(Dispatchers.Default) {
        val prompt = buildString {
            appendLine("Generate comprehensive $testFramework tests for the following $language $codeType:")
            appendLine("```$language")
            appendLine(sourceCode)
            appendLine("```")
            appendLine("Target Coverage: ${coverageTarget}%")
            appendLine("\nInclude:")
            appendLine("- Unit tests for all methods/functions")
            appendLine("- Edge cases and boundary conditions")
            appendLine("- Error handling tests")
            appendLine("- Performance tests if applicable")
            appendLine("- Integration tests if needed")
        }

        try {
            val response = llmProvider.sendRequest(
                systemPrompt = "You are a testing expert. Generate comprehensive tests with high coverage.",
                userMessage = prompt,
                conversationHistory = emptyList(),
                LLMParameters()
            )

            val parts = response.content.split("```")
            val code = if (parts.size >= 2) parts[1] else response.content
            val explanation = if (parts.size >= 3) parts[2].trim() else "Generated test cases."

            TestGenerationResult(
                code = code.trim(),
                explanation = explanation,
                testCasesCount = countTestCases(code),
                estimatedCoverage = estimateCoverage(code, sourceCode),
                testClasses = extractTestClasses(code),
                mockObjects = extractMockObjects(code),
                includesEdgeCases = explanation.contains("edge") || explanation.contains("boundary"),
                includesPerformanceTests = code.contains("@Benchmark") || code.contains("performance"),
                tokensUsed = response.tokenUsage.totalTokens
            )
        } catch (e: Exception) {
            logger.error("Test generation failed", e)
            TestGenerationResult(
                code = "",
                explanation = "Test generation failed: ${e.message}",
                testCasesCount = 0,
                estimatedCoverage = 0.0,
                testClasses = emptyList(),
                mockObjects = emptyList(),
                includesEdgeCases = false,
                includesPerformanceTests = false,
                tokensUsed = 0
            )
        }
    }

    private suspend fun refactorCode(
        sourceCode: String,
        refactoringType: String,
        goals: List<String>,
        language: String,
        preserveBehavior: Boolean
    ): RefactoringResult = withContext(Dispatchers.Default) {
        val prompt = buildString {
            appendLine("Refactor the following $language code using $refactoringType:")
            appendLine("```$language")
            appendLine(sourceCode)
            appendLine("```")
            if (goals.isNotEmpty()) {
                appendLine("Refactoring Goals:")
                goals.forEach { appendLine("- $it") }
            }
            if (preserveBehavior) {
                appendLine("IMPORTANT: Preserve existing behavior - no breaking changes.")
            }
            appendLine("\nProvide refactored code with explanation of changes and improvements.")
        }

        try {
            val response = llmProvider.sendRequest(
                systemPrompt = "You are a refactoring expert. Improve code quality while preserving functionality.",
                userMessage = prompt,
                conversationHistory = emptyList(),
                LLMParameters()
            )

            val parts = response.content.split("```")
            val code = if (parts.size >= 2) parts[1] else response.content
            val explanation = if (parts.size >= 3) parts[2].trim() else "Refactored code implementation."

            RefactoringResult(
                code = code.trim(),
                explanation = explanation,
                changesSummary = extractChangesSummary(response.content),
                improvements = extractImprovements(response.content),
                risks = extractRisks(response.content),
                backupSuggestion = "Create backup of original code before applying refactoring",
                isBreakingChange = explanation.contains("breaking") || explanation.contains("API change"),
                complexityReduction = estimateComplexityReduction(sourceCode, code),
                tokensUsed = response.tokenUsage.totalTokens
            )
        } catch (e: Exception) {
            logger.error("Code refactoring failed", e)
            RefactoringResult(
                code = sourceCode,
                explanation = "Refactoring failed: ${e.message}",
                changesSummary = "",
                improvements = emptyList(),
                risks = listOf("Refactoring process failed"),
                backupSuggestion = "Manual refactoring required",
                isBreakingChange = false,
                complexityReduction = 0.0,
                tokensUsed = 0
            )
        }
    }

    // Вспомогательные методы

    private fun extractDependencies(code: String): List<String> {
        val dependencies = mutableListOf<String>()
        val importRegex = Regex("(?:import|include|require)\\s+([\\w.]+)")
        importRegex.findAll(code).forEach { match ->
            dependencies.add(match.groupValues[1])
        }
        return dependencies.distinct()
    }

    private fun generateFileSuggestions(code: String, language: String): List<String> {
        val suggestions = mutableListOf<String>()
        when (language.lowercase()) {
            "kotlin" -> {
                if (code.contains("class ")) {
                    suggestions.add("${extractClassName(code)}.kt")
                }
                if (code.contains("fun main")) {
                    suggestions.add("Main.kt")
                }
            }
            "java" -> {
                if (code.contains("public class ")) {
                    suggestions.add("${extractClassName(code)}.java")
                }
            }
            "python" -> {
                if (code.contains("def main")) {
                    suggestions.add("main.py")
                } else if (code.contains("class ")) {
                    suggestions.add("${extractClassName(code).lowercase()}.py")
                }
            }
        }
        return suggestions
    }

    private fun extractTestCases(content: String): List<String> {
        val testCases = mutableListOf<String>()
        val testRegex = Regex("(?:test_|@Test|def test_|fun test)([\\w_]+)")
        testRegex.findAll(content).forEach { match ->
            testCases.add(match.groupValues[1])
        }
        return testCases
    }

    private fun calculateConfidence(content: String): Double {
        val indicators = listOf("implementation", "complete", "functional", "working")
        val count = indicators.count { content.contains(it, ignoreCase = true) }
        return minOf(1.0, count * 0.2 + 0.4)
    }

    private fun suggestFilePath(className: String, language: String): String {
        return when (language.lowercase()) {
            "kotlin", "java" -> "src/main/kotlin/com/example/${className}.kt"
            "python" -> "src/${className.lowercase()}.py"
            else -> "$className.${getLanguageExtension(language)}"
        }
    }

    private fun extractUsageExample(content: String): String {
        val exampleRegex = Regex("(?:Example|Usage):?\\s*```[\\w]*\\n(.*?)\\n```")
        val match = exampleRegex.find(content)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractComplexity(content: String, type: String): String {
        val complexityRegex = Regex("${type} Complexity:\\s*([Oo]\\([^)]+\\))")
        val match = complexityRegex.find(content)
        return match?.groupValues?.get(1) ?: "Unknown"
    }

    private fun extractOptimizationNotes(content: String): String {
        val lines = content.lines()
        return lines.filter {
            it.contains("optim", ignoreCase = true) ||
            it.contains("improve", ignoreCase = true)
        }.joinToString("\n")
    }

    private fun extractBenchmarks(content: String): List<String> {
        val benchmarks = mutableListOf<String>()
        val benchmarkRegex = Regex("(?:Benchmark|Performance):\\s*(.+)")
        benchmarkRegex.findAll(content).forEach { match ->
            benchmarks.add(match.groupValues[1].trim())
        }
        return benchmarks
    }

    private fun usesStandardLibrary(code: String, language: String): Boolean {
        val stdLibPatterns = when (language.lowercase()) {
            "kotlin" -> listOf("kotlin.", "java.util.")
            "java" -> listOf("java.util.", "java.io.")
            "python" -> listOf("import ", "from ")
            else -> emptyList()
        }
        return stdLibPatterns.any { pattern ->
            code.contains(pattern, ignoreCase = true)
        }
    }

    private fun countTestCases(code: String): Int {
        val testPatterns = listOf("@Test", "test_", "fun test", "def test")
        return testPatterns.sumOf { pattern ->
            code.split("\n").count { it.contains(pattern, ignoreCase = true) }
        }
    }

    private fun estimateCoverage(testCode: String, sourceCode: String): Double {
        val sourceLines = sourceCode.lines().filter { it.isNotBlank() && !it.trim().startsWith("//") }
        val testLines = testCode.lines().filter { it.isNotBlank() && !it.trim().startsWith("//") }

        val ratio = testLines.size.toDouble() / sourceLines.size.toDouble()
        return minOf(95.0, ratio * 100.0)
    }

    private fun extractTestClasses(code: String): List<String> {
        val testClasses = mutableListOf<String>()
        val testClassRegex = Regex("class\\s+(\\w*Test\\w*)")
        testClassRegex.findAll(code).forEach { match ->
            testClasses.add(match.groupValues[1])
        }
        return testClasses
    }

    private fun extractMockObjects(code: String): List<String> {
        val mocks = mutableListOf<String>()
        val mockRegex = Regex("(?:@Mock|MockK|mock\\()([<>\\s\\w,]+)")
        mockRegex.findAll(code).forEach { match ->
            mocks.add(match.groupValues[1].trim())
        }
        return mocks.distinct()
    }

    private fun extractChangesSummary(content: String): String {
        val lines = content.lines()
        return lines.filter {
            it.contains("change", ignoreCase = true) ||
            it.contains("refactor", ignoreCase = true)
        }.take(5).joinToString("\n")
    }

    private fun extractImprovements(content: String): List<String> {
        val improvements = mutableListOf<String>()
        val improvementRegex = Regex("(?:Improvement|Better):\\s*(.+)")
        improvementRegex.findAll(content).forEach { match ->
            improvements.add(match.groupValues[1].trim())
        }
        return improvements
    }

    private fun extractRisks(content: String): List<String> {
        val risks = mutableListOf<String>()
        val riskRegex = Regex("(?:Risk|Caution):\\s*(.+)")
        riskRegex.findAll(content).forEach { match ->
            risks.add(match.groupValues[1].trim())
        }
        return risks
    }

    private fun estimateComplexityReduction(original: String, refactored: String): Double {
        val originalComplexity = calculateCyclomaticComplexity(original)
        val refactoredComplexity = calculateCyclomaticComplexity(refactored)
        return if (originalComplexity > 0) {
            ((originalComplexity - refactoredComplexity) / originalComplexity) * 100.0
        } else 0.0
    }

    private fun calculateCyclomaticComplexity(code: String): Int {
        val complexityKeywords = listOf("if", "for", "while", "case", "catch", "&&", "||")
        return 1 + complexityKeywords.sumOf { keyword ->
            code.split("\n").count { it.contains(keyword, ignoreCase = true) }
        }
    }

    private fun extractClassName(code: String): String {
        val classRegex = Regex("(?:class|interface)\\s+(\\w+)")
        val match = classRegex.find(code)
        return match?.groupValues?.get(1) ?: "GeneratedClass"
    }

    private fun getLanguageExtension(language: String): String {
        return when (language.lowercase()) {
            "kotlin" -> "kt"
            "java" -> "java"
            "python" -> "py"
            "javascript" -> "js"
            "typescript" -> "ts"
            "cpp", "c++" -> "cpp"
            "c" -> "c"
            "go" -> "go"
            "rust" -> "rs"
            else -> "txt"
        }
    }

    // Data классы для результатов
    private data class CodeGenerationResult(
        val code: String,
        val explanation: String,
        val dependencies: List<String>,
        val fileSuggestions: List<String>,
        val testCases: List<String>,
        val tokensUsed: Int,
        val confidence: Double
    )

    private data class ClassGenerationResult(
        val code: String,
        val explanation: String,
        val filePath: String,
        val dependencies: List<String>,
        val usageExample: String,
        val isAbstract: Boolean,
        val isDataClass: Boolean,
        val tokensUsed: Int
    )

    private data class FunctionGenerationResult(
        val code: String,
        val explanation: String,
        val usageExample: String,
        val testCases: List<String>,
        val isRecursive: Boolean,
        val isPure: Boolean,
        val tokensUsed: Int
    )

    private data class AlgorithmGenerationResult(
        val code: String,
        val explanation: String,
        val timeComplexity: String,
        val spaceComplexity: String,
        val optimizationNotes: String,
        val testCases: List<String>,
        val benchmarks: List<String>,
        val isOptimized: Boolean,
        val usesStandardLibrary: Boolean,
        val tokensUsed: Int
    )

    private data class TestGenerationResult(
        val code: String,
        val explanation: String,
        val testCasesCount: Int,
        val estimatedCoverage: Double,
        val testClasses: List<String>,
        val mockObjects: List<String>,
        val includesEdgeCases: Boolean,
        val includesPerformanceTests: Boolean,
        val tokensUsed: Int
    )

    private data class RefactoringResult(
        val code: String,
        val explanation: String,
        val changesSummary: String,
        val improvements: List<String>,
        val risks: List<String>,
        val backupSuggestion: String,
        val isBreakingChange: Boolean,
        val complexityReduction: Double,
        val tokensUsed: Int
    )
}