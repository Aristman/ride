package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.a2a.A2AAgentAdapter
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.integration.llm.LLMProvider

/**
 * A2A адаптер для CodeGeneratorToolAgent
 */
class A2ACodeGeneratorToolAgent(
    legacy: CodeGeneratorToolAgent,
    messageBus: MessageBus
) : A2AAgentAdapter(
    agentType = "CODE_GENERATOR",
    legacyAgent = legacy,
    messageBus = messageBus
) {

    init {
        // Поддерживаемые типы сообщений для генерации кода
        supportedMessageTypes = setOf(
            "CODE_GENERATION_REQUEST",
            "CLASS_GENERATION_REQUEST",
            "FUNCTION_GENERATION_REQUEST",
            "ALGORITHM_GENERATION_REQUEST"
        )

        // Маппинг типов сообщений на типы кода
        messageTypeToCodeType = mapOf(
            "CODE_GENERATION_REQUEST" to "general",
            "CLASS_GENERATION_REQUEST" to "class",
            "FUNCTION_GENERATION_REQUEST" to "function",
            "ALGORITHM_GENERATION_REQUEST" to "algorithm"
        )
    }

    override suspend fun processMessage(request: AgentMessage.Request): AgentMessage.Response {
        val messageType = request.messageType
        val codeType = messageTypeToCodeType[messageType] ?: "general"

        logger.info("A2A_CODE_GENERATOR processing $messageType")

        return try {
            // Извлекаем данные из payload
            val payloadData = when (val payload = request.payload) {
                is ru.marslab.ide.ride.agent.a2a.MessagePayload.CustomPayload -> payload.data
                else -> emptyMap()
            }

            val inputData = payloadData["input"] as? Map<String, Any> ?: emptyMap()

            // Формируем входные данные для legacy агента
            val stepInput = ru.marslab.ide.ride.model.tool.StepInput.of(
                "request" to (inputData["request"] ?: throw IllegalArgumentException("request parameter required")),
                "language" to (inputData["language"] ?: "kotlin"),
                "code_type" to codeType,
                "context_files" to (inputData["context_files"] ?: emptyList<String>())
            )

            // Выполняем шаг с помощью legacy агента
            val step = ru.marslab.ide.ride.model.tool.ToolPlanStep(
                id = request.metadata["stepId"] as? String ?: "unknown",
                description = "Generate $codeType code",
                agentType = ru.marslab.ide.ride.model.orchestrator.AgentType.CODE_GENERATOR,
                input = stepInput
            )

            val context = ru.marslab.ide.ride.model.orchestrator.ExecutionContext.of(
                "requestId" to (request.metadata["requestId"] ?: "unknown"),
                "messageType" to messageType
            )

            val result = legacyAgent.executeStep(step, context)

            if (result.success) {
                val output = result.output
                AgentMessage.Response(
                    messageId = java.util.UUID.randomUUID().toString(),
                    senderId = agentId,
                    requestId = request.messageId,
                    success = true,
                    payload = ru.marslab.ide.ride.agent.a2a.MessagePayload.CustomPayload(
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
                        "agentType" to agentType
                    )
                )
            } else {
                logger.error("Code generation failed: ${result.error}")
                AgentMessage.Response(
                    messageId = java.util.UUID.randomUUID().toString(),
                    senderId = agentId,
                    requestId = request.messageId,
                    success = false,
                    error = result.error ?: "Unknown error during code generation",
                    payload = ru.marslab.ide.ride.agent.a2a.MessagePayload.CustomPayload(
                        type = "${messageType}_ERROR",
                        data = mapOf(
                            "request" to (inputData["request"] ?: ""),
                            "error_type" to "generation_failed"
                        )
                    ),
                    metadata = mapOf(
                        "stepId" to step.id,
                        "executionTime" to System.currentTimeMillis(),
                        "agentType" to agentType
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("A2A_CODE_GENERATOR error processing request", e)
            AgentMessage.Response(
                messageId = java.util.UUID.randomUUID().toString(),
                senderId = agentId,
                requestId = request.messageId,
                success = false,
                error = "Code generation failed: ${e.message}",
                payload = ru.marslab.ide.ride.agent.a2a.MessagePayload.CustomPayload(
                    type = "${messageType}_ERROR",
                    data = mapOf(
                        "error_type" to "processing_error",
                        "error_details" to (e.message ?: "Unknown error")
                    )
                ),
                metadata = mapOf(
                    "executionTime" to System.currentTimeMillis(),
                    "agentType" to agentType
                )
            )
        }
    }

    companion object {
        fun create(llmProvider: LLMProvider, messageBus: MessageBus): A2ACodeGeneratorToolAgent {
            val legacy = CodeGeneratorToolAgent(llmProvider)
            return A2ACodeGeneratorToolAgent(legacy, messageBus)
        }
    }
}