package ru.marslab.ide.ride.service.rag

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.domain.rag.ChunkEnricher
import ru.marslab.ide.ride.domain.rag.EnrichedChunk
import ru.marslab.ide.ride.integration.embedding.EmbeddingIndexClient
import ru.marslab.ide.ride.mcp.MCPClient
import ru.marslab.ide.ride.mcp.MCPServerManager

/**
 * Обогащает чанки контекстом из файловой системы через MCP.
 * Использует EmbeddingIndexClient для получения пути к файлу по chunkId,
 * а затем читает файл через MCP, чтобы вычислить якори (диапазоны строк) и выделить близкий контекст.
 */
class McpChunkEnricher(
    private val indexClient: EmbeddingIndexClient,
    private val mcpClientProvider: () -> MCPClient = { MCPClient(MCPServerManager.getInstance().getServerUrl()) },
    private val logger: Logger = Logger.getInstance(McpChunkEnricher::class.java),
    private val readFileFn: suspend (String) -> String = { path -> mcpClientProvider().readFile(path).content }
) : ChunkEnricher {

    override suspend fun enrich(chunkIds: List<String>): List<EnrichedChunk> = withContext(Dispatchers.IO) {
        if (chunkIds.isEmpty()) return@withContext emptyList()

        val fileCache = mutableMapOf<String, String>() // path -> content

        val result = mutableListOf<EnrichedChunk>()
        for (chunkId in chunkIds) {
            try {
                val chunk = indexClient.getChunkById(chunkId)
                val path = chunk?.filePath
                val baseContent = chunk?.content ?: ""
                if (path.isNullOrBlank() || baseContent.isBlank()) {
                    logger.warn("MCP Enricher: missing path/content for chunkId=$chunkId")
                    continue
                }

                val fileContent = fileCache.getOrPut(path) {
                    try {
                        runBlockingRead(path)
                    } catch (t: Throwable) {
                        logger.warn("MCP Enricher: failed to read file '$path': ${t.message}")
                        ""
                    }
                }

                if (fileContent.isBlank()) {
                    // Нет файла — возвращаем базовый чанк без обогащения
                    result.add(
                        EnrichedChunk(
                            chunkId = chunkId,
                            content = baseContent,
                            filePath = path,
                            anchors = emptyList()
                        )
                    )
                    continue
                }

                val (enriched, anchors) = extractContextWithAnchors(baseContent, fileContent, contextLines = 3)
                result.add(
                    EnrichedChunk(
                        chunkId = chunkId,
                        content = enriched,
                        filePath = path,
                        anchors = anchors
                    )
                )
            } catch (t: Throwable) {
                logger.warn("MCP Enricher: error processing chunkId=$chunkId: ${t.message}", t)
            }
        }
        result
    }

    private suspend fun runBlockingRead(path: String): String {
        return readFileFn(path)
    }

    private fun extractContextWithAnchors(snippet: String, fileContent: String, contextLines: Int): Pair<String, List<IntRange>> {
        val fileLines = fileContent.split("\n")
        val snippetTrim = snippet.trim()
        val idx = fileContent.indexOf(snippetTrim)
        if (idx < 0) {
            // Не нашли — возвращаем исходный чанк
            return snippetTrim to emptyList()
        }
        // Определяем номера строк по позиции
        var charCount = 0
        var startLine = 0
        for ((i, line) in fileLines.withIndex()) {
            val next = charCount + line.length + 1 // +\n
            if (idx < next) {
                startLine = i
                break
            }
            charCount = next
        }
        // Примерная длина чанка по строкам — ищем конец вхождения
        val endIdx = idx + snippetTrim.length
        var endLine = startLine
        charCount = 0
        for ((i, line) in fileLines.withIndex()) {
            val next = charCount + line.length + 1
            if (endIdx <= next) {
                endLine = i
                break
            }
            charCount = next
        }
        val from = (startLine - contextLines).coerceAtLeast(0)
        val to = (endLine + contextLines).coerceAtMost(fileLines.lastIndex)
        val contextBlock = buildString {
            appendLine("// ${from + 1}-${to + 1}: ${to - from + 1} lines from ${startLine + 1}-${endLine + 1}")
            for (i in from..to) {
                appendLine(fileLines[i])
            }
        }.trimEnd()
        return contextBlock to listOf(IntRange(startLine + 1, endLine + 1)) // 1-based для UI
    }
}
