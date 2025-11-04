package ru.marslab.ide.ride.agent.tools

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.integration.llm.LLMProvider

class A2AAgentsSmokeTest {

    private val dummyBus = mockk<MessageBus>(relaxed = true)

    @Test
    fun `architecture agent should handle ARCHITECTURE_ANALYSIS_REQUEST`() = runBlocking {
        val llm = mockk<LLMProvider>(relaxed = true)
        val legacy = ArchitectureToolAgent(llm)
        val agent = A2AArchitectureToolAgent()
        val request = AgentMessage.Request(
            senderId = "test",
            messageType = "ARCHITECTURE_ANALYSIS_REQUEST",
            payload = MessagePayload.CustomPayload(
                type = "ARCHITECTURE_ANALYSIS_REQUEST",
                data = mapOf("input" to mapOf("files" to emptyList<String>()))
            ),
            metadata = mapOf("stepId" to "s1")
        )
        val msg = agent.handleA2AMessage(request, dummyBus)
        assertNotNull(msg)
        val resp = msg as AgentMessage.Response
        assertTrue(resp.success)
    }

    @Test
    fun `code chunker agent should handle CODE_CHUNK_REQUEST`() = runBlocking {
        val legacy = CodeChunkerToolAgent()
        val agent = A2ACodeChunkerToolAgent()
        val request = AgentMessage.Request(
            senderId = "test",
            messageType = "CODE_CHUNK_REQUEST",
            payload = MessagePayload.CustomPayload(
                type = "CODE_CHUNK_REQUEST",
                data = mapOf("input" to mapOf("files" to emptyList<String>()))
            ),
            metadata = mapOf("stepId" to "s2")
        )
        val msg = agent.handleA2AMessage(request, dummyBus)
        assertNotNull(msg)
        val resp = msg as AgentMessage.Response
        assertTrue(resp.success)
    }
}
