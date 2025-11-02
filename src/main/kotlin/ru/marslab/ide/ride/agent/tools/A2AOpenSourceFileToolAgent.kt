package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.a2a.A2AAgent
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.ToolPlanStep

class A2AOpenSourceFileToolAgent(
    private val legacy: OpenSourceFileToolAgent,
    private val messageBus: MessageBus
) : A2AAgent {
    override val a2aAgentId: String = "a2a-open-file-${hashCode()}"
    override val supportedMessageTypes: Set<String> = setOf("OPEN_FILE_REQUEST")

    override suspend fun initializeA2A(messageBus: MessageBus, context: ExecutionContext) {}

    override suspend fun shutdownA2A(messageBus: MessageBus) {}

    override suspend fun handleRequest(message: AgentMessage.Request): AgentMessage.Response {
        if (message.messageType != "OPEN_FILE_REQUEST") {
            return AgentMessage.Response(
                success = false,
                payload = MessagePayload.ErrorPayload("Unsupported message type: ${message.messageType}"),
                error = "unsupported_type"
            )
        }
        val data = (message.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val step = ToolPlanStep(
            id = (message.metadata["stepId"] as? String) ?: "open-file-${System.currentTimeMillis()}",
            title = (data["description"] as? String) ?: "Open source file",
            description = (data["description"] as? String) ?: "",
            agentType = AgentType.FILE_OPERATIONS,
            input = StepInput(data = (data["input"] as? Map<String, Any>) ?: emptyMap())
        )
        val result = legacy.executeStep(step)
        return if (result.success) {
            AgentMessage.Response(
                success = true,
                payload = MessagePayload.CustomPayload(
                    type = "TOOL_EXECUTION_RESULT",
                    data = mapOf(
                        "output" to result.output.data,
                        "metadata" to mapOf("agent" to "FILE_OPERATIONS")
                    )
                )
            )
        } else {
            AgentMessage.Response(
                success = false,
                payload = MessagePayload.CustomPayload(
                    type = "TOOL_EXECUTION_RESULT",
                    data = mapOf(
                        "error" to (result.error ?: "error"),
                        "metadata" to mapOf("agent" to "FILE_OPERATIONS")
                    )
                ),
                error = result.error
            )
        }
    }
}
