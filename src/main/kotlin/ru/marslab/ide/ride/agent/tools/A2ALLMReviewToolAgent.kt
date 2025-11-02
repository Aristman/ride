package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.BaseA2AAgent
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.ToolPlanStep

class A2ALLMReviewToolAgent(
    private val legacy: LLMReviewToolAgent,
    private val bus: MessageBus
) : BaseA2AAgent(
    agentType = AgentType.LLM_REVIEW,
    a2aAgentId = "a2a-llm-review-${System.identityHashCode(legacy)}",
    supportedMessageTypes = setOf("LLM_REVIEW_REQUEST"),
    publishedEventTypes = setOf("TOOL_EXECUTION_STARTED", "TOOL_EXECUTION_COMPLETED", "TOOL_EXECUTION_FAILED")
) {

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        if (request.messageType != "LLM_REVIEW_REQUEST") {
            return AgentMessage.Response(
                senderId = a2aAgentId,
                requestId = request.id,
                success = false,
                payload = MessagePayload.ErrorPayload("Unsupported message type: ${request.messageType}"),
                error = "unsupported_type"
            )
        }
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val step = ToolPlanStep(
            id = (request.metadata["stepId"] as? String) ?: "llm-review-${System.currentTimeMillis()}",
            description = (data["description"] as? String) ?: "LLM review",
            agentType = AgentType.LLM_REVIEW,
            input = StepInput(data = (data["input"] as? Map<String, Any>) ?: emptyMap())
        )
        val result = legacy.executeStep(step, ExecutionContext.Empty)
        return if (result.success) {
            AgentMessage.Response(
                senderId = a2aAgentId,
                requestId = request.id,
                success = true,
                payload = MessagePayload.CustomPayload(
                    type = "TOOL_EXECUTION_RESULT",
                    data = mapOf(
                        "output" to result.output.data,
                        "metadata" to mapOf("agent" to "LLM_REVIEW")
                    )
                )
            )
        } else {
            AgentMessage.Response(
                senderId = a2aAgentId,
                requestId = request.id,
                success = false,
                payload = MessagePayload.CustomPayload(
                    type = "TOOL_EXECUTION_RESULT",
                    data = mapOf(
                        "error" to (result.error ?: "error"),
                        "metadata" to mapOf("agent" to "LLM_REVIEW")
                    )
                ),
                error = result.error
            )
        }
    }
}
