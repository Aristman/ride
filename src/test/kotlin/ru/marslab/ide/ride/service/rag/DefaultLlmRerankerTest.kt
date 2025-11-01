package ru.marslab.ide.ride.service.rag

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.marslab.ide.ride.domain.rag.ChunkCandidate
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.chat.ConversationMessage
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.llm.LLMResponse

class DefaultLlmRerankerTest {

    private class StubLLMProvider(
        private val available: Boolean,
        private val responseContent: String? = null,
        private val error: String? = null
    ) : LLMProvider {
        override suspend fun sendRequest(
            systemPrompt: String,
            userMessage: String,
            conversationHistory: List<ConversationMessage>,
            parameters: LLMParameters
        ): LLMResponse {
            return if (error != null) LLMResponse.error(error) else LLMResponse.success(responseContent.orEmpty())
        }
        override fun isAvailable(): Boolean = available
        override fun getProviderName(): String = "Stub"
    }

    @Test
    fun `falls back to initial order when provider unavailable`() = runBlocking {
        val candidates = listOf(
            ChunkCandidate("c1", 0.9),
            ChunkCandidate("c2", 0.8),
            ChunkCandidate("c3", 0.7)
        )
        val reranker = DefaultLlmReranker(StubLLMProvider(available = false))
        val res = reranker.rerank("q", candidates, topN = 2)
        assertEquals(listOf("c1", "c2"), res)
    }

    @Test
    fun `uses JSON id order from model response`() = runBlocking {
        val candidates = listOf(
            ChunkCandidate("c1", 0.9),
            ChunkCandidate("c2", 0.8),
            ChunkCandidate("c3", 0.7)
        )
        val json = "[\"c3\", \"c1\", \"c2\"]"
        val reranker = DefaultLlmReranker(StubLLMProvider(available = true, responseContent = json))
        val res = reranker.rerank("q", candidates, topN = 2)
        assertEquals(listOf("c3", "c1"), res)
    }

    @Test
    fun `invalid JSON response triggers fallback with padding`() = runBlocking {
        val candidates = listOf(
            ChunkCandidate("c1", 0.9),
            ChunkCandidate("c2", 0.8),
            ChunkCandidate("c3", 0.7)
        )
        val reranker = DefaultLlmReranker(StubLLMProvider(available = true, responseContent = "not json"))
        val res = reranker.rerank("q", candidates, topN = 2)
        assertEquals(listOf("c1", "c2"), res)
    }
}
