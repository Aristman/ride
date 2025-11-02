package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.BaseA2AAgent
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.ToolPlanStep

class A2AUserInteractionAgent(
    private val legacy: UserInteractionAgent,
    private val bus: MessageBus
) : BaseA2AAgent(
    agentType = AgentType.USER_INTERACTION,
    a2aAgentId = "a2a-user-interaction-${System.identityHashCode(legacy)}",
    supportedMessageTypes = setOf("USER_INPUT_REQUEST"),
    publishedEventTypes = setOf("TOOL_EXECUTION_STARTED", "TOOL_EXECUTION_COMPLETED", "TOOL_EXECUTION_FAILED")
) {

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        if (request.messageType != "USER_INPUT_REQUEST") {
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
            id = (request.metadata["stepId"] as? String) ?: "user-input-${System.currentTimeMillis()}",
            description = (data["description"] as? String) ?: "User interaction",
            agentType = AgentType.USER_INTERACTION,
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
                        "metadata" to mapOf("agent" to "USER_INTERACTION")
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
                        "metadata" to mapOf("agent" to "USER_INTERACTION")
                    )
                ),
                error = result.error
            )
        }
    }
}
