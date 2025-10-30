package ru.marslab.ide.ride.service.rag

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import ru.marslab.ide.ride.domain.rag.ChunkCandidate
import ru.marslab.ide.ride.domain.rag.LlmReranker
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.chat.ConversationMessage
import ru.marslab.ide.ride.model.chat.ConversationRole
import ru.marslab.ide.ride.model.llm.LLMParameters

/**
 * Реализация LlmReranker на базе существующего LLMProvider.
 * Возвращает отсортированный список chunkId длиной до topN.
 */
class DefaultLlmReranker(
    private val llmProvider: LLMProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LlmReranker {

    private val logger = Logger.getInstance(DefaultLlmReranker::class.java)

    override suspend fun rerank(query: String, candidates: List<ChunkCandidate>, topN: Int): List<String> {
        if (!llmProvider.isAvailable()) {
            logger.warn("LLM provider is not available, fallback to similarity order")
            return candidates.take(topN).map { it.chunkId }
        }
        if (candidates.isEmpty() || topN <= 0) return emptyList()

        val clippedTopN = topN.coerceAtLeast(1)
        val prompt = buildPrompt(query, candidates)

        val response = try {
            llmProvider.sendRequest(
                systemPrompt = SYSTEM_PROMPT,
                userMessage = prompt,
                conversationHistory = emptyList(),
                parameters = LLMParameters.PRECISE
            )
        } catch (t: Throwable) {
            logger.warn("Rerank request failed: ${t.message}", t)
            return candidates.take(clippedTopN).map { it.chunkId }
        }

        if (!response.success) {
            logger.warn("Rerank response unsuccessful: ${response.error}")
            return candidates.take(clippedTopN).map { it.chunkId }
        }

        val parsed = parseChunkIdList(response.content)
        if (parsed.isEmpty()) {
            logger.warn("Rerank response parsing produced empty list, fallback to similarity order")
            return candidates.take(clippedTopN).map { it.chunkId }
        }

        // Сохраняем только те id, что есть среди кандидатов, и ограничиваем topN
        val candidateIds = candidates.map { it.chunkId }.toSet()
        val filtered = parsed.filter { it in candidateIds }.distinct().take(clippedTopN)
        // Если модель вернула меньше topN — дополняем из исходного порядка
        if (filtered.size < clippedTopN) {
            val remaining = candidates.asSequence()
                .map { it.chunkId }
                .filter { it !in filtered }
                .take(clippedTopN - filtered.size)
                .toList()
            return filtered + remaining
        }
        return filtered
    }

    private fun buildPrompt(query: String, candidates: List<ChunkCandidate>): String {
        val sb = StringBuilder()
        sb.appendLine("User query:")
        sb.appendLine(query.trim())
        sb.appendLine()
        sb.appendLine("Candidates: provide only IDs sorted by relevance in JSON array.")
        candidates.forEachIndexed { idx, c ->
            // Передаем минимум данных, чтобы не расходовать токены
            sb.appendLine("${idx + 1}. id=${c.chunkId}; similarity=${"%.3f".format(c.similarity)}; file=${c.filePath ?: ""}")
        }
        sb.appendLine()
        sb.appendLine("Return format: JSON array of strings, e.g. [\"id1\", \"id2\"].")
        return sb.toString()
    }

    private fun parseChunkIdList(content: String): List<String> {
        // Пытаемся найти первый JSON массив в ответе
        val trimmed = content.trim()
        return try {
            val start = trimmed.indexOf('[')
            val end = trimmed.lastIndexOf(']')
            if (start >= 0 && end > start) {
                val jsonPart = trimmed.substring(start, end + 1)
                val arr: JsonArray = json.parseToJsonElement(jsonPart).jsonArray
                arr.map { el -> el.jsonPrimitive.content }
                    .map { it.trim('"', ' ', '\n', '\r', '\t') }
                    .filter { it.isNotEmpty() }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val SYSTEM_PROMPT = """
You are a precise reranker. Your task:
- Given a user query and a list of candidate chunks (with IDs and similarity),
- Return ONLY a JSON array of candidate IDs sorted by DESC relevance to the user query.
- Do NOT include any extra text.
- If some IDs are equally relevant, keep arbitrary order among them.
"""
    }
}
