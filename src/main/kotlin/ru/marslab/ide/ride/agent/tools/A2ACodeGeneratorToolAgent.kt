package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import ru.marslab.ide.ride.agent.ValidationResult

/**
 * A2A агент для генерации кода с использованием legacy CodeGeneratorToolAgent
 */
class A2ACodeGeneratorToolAgent constructor(
    private val legacyAgent: CodeGeneratorToolAgent,
    override val a2aAgentId: String,
    override val supportedMessageTypes: Set<String>,
    override val publishedEventTypes: Set<String>,
    override val messageProcessingPriority: Int,
    override val maxConcurrentMessages: Int
) : BaseToolAgent(
    agentType = AgentType.CODE_GENERATOR,
    toolCapabilities = setOf(
        "code_generation",
        "class_generation",
        "function_generation",
        "algorithm_generation"
    )
), A2AAgent {

    private val messageTypeToCodeType = mapOf(
        "CODE_GENERATION_REQUEST" to "general",
        "CLASS_GENERATION_REQUEST" to "class",
        "FUNCTION_GENERATION_REQUEST" to "function",
        "ALGORITHM_GENERATION_REQUEST" to "algorithm"
    )

    override fun getDescription(): String {
        return "A2A агент для генерации кода"
    }

    override fun validateInput(input: StepInput): ValidationResult {
        val request = input.getString("request")
        val language = input.getString("language") ?: "kotlin"
        val codeType = input.getString("code_type")

        if (request.isNullOrBlank()) {
            return ValidationResult.failure("Parameter 'request' is required for code generation")
        }

        if (codeType != null && codeType !in setOf("class", "function", "algorithm", "general")) {
            return ValidationResult.failure("Invalid code_type: $codeType. Must be one of: class, function, algorithm, general")
        }

        return ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        // Этот метод не используется в A2A режиме, вся логика в handleA2AMessage
        return legacyAgent.executeStep(step, context)
    }

    override suspend fun handleA2AMessage(
        message: AgentMessage,
        messageBus: MessageBus
    ): AgentMessage? {
        return when (message) {
            is AgentMessage.Request -> handleA2ARequest(message)
            else -> null
        }
    }

    private suspend fun handleA2ARequest(request: AgentMessage.Request): AgentMessage.Response {
        val messageType = request.messageType
        val codeType = messageTypeToCodeType[messageType] ?: "general"

        logger.info("A2A_CODE_GENERATOR processing $messageType")

        return try {
            // Извлекаем данные из payload
            val payloadData = when (val payload = request.payload) {
                is MessagePayload.CustomPayload -> payload.data
                else -> emptyMap()
            }

            val inputData = payloadData["input"] as? Map<String, Any> ?: emptyMap()

            // Формируем входные данные для legacy агента
            val stepInput = StepInput(mapOf(
                "request" to (inputData["request"] ?: throw IllegalArgumentException("request parameter required")),
                "language" to (inputData["language"] ?: "kotlin"),
                "code_type" to codeType,
                "context_files" to (inputData["context_files"] ?: emptyList<String>())
            ))

            // Выполняем шаг с помощью legacy агента
            val step = ToolPlanStep(
                id = request.metadata["stepId"] as? String ?: "unknown",
                description = "Generate $codeType code",
                agentType = AgentType.CODE_GENERATOR,
                input = stepInput
            )

            val context = ExecutionContext(
                additionalContext = mapOf(
                    "requestId" to (request.metadata["requestId"] ?: "unknown"),
                    "messageType" to messageType
                )
            )

            val result = legacyAgent.executeStep(step, context)

            if (result.success) {
                val output = result.output
                AgentMessage.Response(
                    senderId = a2aAgentId,
                    requestId = request.id,
                    success = true,
                    payload = MessagePayload.CustomPayload(
                        type = "${messageType}_RESPONSE",
                        data = mapOf(
                            "generated_code" to (output["generated_code"] ?: ""),
                            "explanation" to (output["explanation"] ?: ""),
                            "language" to (output["language"] ?: "kotlin"),
                            "code_type" to (output["code_type"] ?: codeType),
                            "dependencies" to (output["dependencies"] ?: emptyList<String>()),
                            "metadata" to mapOf(
                                "request_length" to (result.metadata["request_length"] ?: 0),
                                "code_length" to (result.metadata["code_length"] ?: 0),
                                "has_context" to (result.metadata["has_context"] ?: false)
                            )
                        )
                    ),
                    metadata = mapOf(
                        "stepId" to step.id,
                        "executionTime" to System.currentTimeMillis(),
                        "agentType" to "CODE_GENERATOR"
                    )
                )
            } else {
                logger.error("Code generation failed: ${result.error}")
                AgentMessage.Response(
                    senderId = a2aAgentId,
                    requestId = request.id,
                    success = false,
                    error = result.error ?: "Unknown error during code generation",
                    payload = MessagePayload.CustomPayload(
                        type = "${messageType}_ERROR",
                        data = mapOf(
                            "request" to (inputData["request"] ?: ""),
                            "error_type" to "generation_failed"
                        )
                    ),
                    metadata = mapOf(
                        "stepId" to step.id,
                        "executionTime" to System.currentTimeMillis(),
                        "agentType" to "CODE_GENERATOR"
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("A2A_CODE_GENERATOR error processing request", e)
            AgentMessage.Response(
                senderId = a2aAgentId,
                requestId = request.id,
                success = false,
                error = "Code generation failed: ${e.message}",
                payload = MessagePayload.CustomPayload(
                    type = "${messageType}_ERROR",
                    data = mapOf(
                        "error_type" to "processing_error",
                        "error_details" to (e.message ?: "Unknown error")
                    )
                ),
                metadata = mapOf(
                    "executionTime" to System.currentTimeMillis(),
                    "agentType" to "CODE_GENERATOR"
                )
            )
        }
    }

    companion object {
        fun create(llmProvider: LLMProvider, messageBus: MessageBus): A2ACodeGeneratorToolAgent {
            val legacy = CodeGeneratorToolAgent(llmProvider)
            return A2ACodeGeneratorToolAgent(
                legacyAgent = legacy,
                a2aAgentId = "code-generator-a2a-${System.identityHashCode(legacy)}",
                supportedMessageTypes = setOf(
                    "CODE_GENERATION_REQUEST",
                    "CLASS_GENERATION_REQUEST",
                    "FUNCTION_GENERATION_REQUEST",
                    "ALGORITHM_GENERATION_REQUEST"
                ),
                publishedEventTypes = setOf(
                    "CODE_GENERATION_STARTED",
                    "CODE_GENERATION_COMPLETED",
                    "CODE_GENERATION_FAILED"
                ),
                messageProcessingPriority = 1,
                maxConcurrentMessages = 3
            )
        }
    }
}