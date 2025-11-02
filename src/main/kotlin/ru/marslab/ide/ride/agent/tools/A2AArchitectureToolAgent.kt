package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.ToolAgent
import ru.marslab.ide.ride.agent.a2a.A2AAgent
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.model.tool.StepInput

class A2AArchitectureToolAgent(
    private val legacy: ArchitectureToolAgent,
    private val messageBus: MessageBus
) : A2AAgent {
    override val a2aAgentId: String = "a2a-architecture-${hashCode()}"
    override val supportedMessageTypes: Set<String> = setOf("ARCHITECTURE_ANALYSIS_REQUEST")

    override suspend fun initializeA2A(messageBus: MessageBus, context: ExecutionContext) {}

    override suspend fun shutdownA2A(messageBus: MessageBus) {}

    override suspend fun handleRequest(message: AgentMessage.Request): AgentMessage.Response {
        if (message.messageType != "ARCHITECTURE_ANALYSIS_REQUEST") {
            return AgentMessage.Response(
                success = false,
                payload = MessagePayload.ErrorPayload("Unsupported message type: ${message.messageType}"),
                error = "unsupported_type"
            )
        }
        val data = (message.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val step = ToolPlanStep(
            id = (message.metadata["stepId"] as? String) ?: "arch-${System.currentTimeMillis()}",
            title = (data["description"] as? String) ?: "Architecture analysis",
            description = (data["description"] as? String) ?: "",
            agentType = AgentType.ARCHITECTURE_ANALYSIS,
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
                        "metadata" to mapOf("agent" to "ARCHITECTURE_ANALYSIS")
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
                        "metadata" to mapOf("agent" to "ARCHITECTURE_ANALYSIS")
                    )
                ),
                error = result.error
            )
        }
    }
}
