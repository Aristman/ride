package ru.marslab.ide.ride.service.rag

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

                val (baseForFile, relPath) = resolveBaseAndRel(path)
                // Обеспечиваем MCP сервер для данного проекта (best-effort)
                if (baseForFile.isNotBlank()) {
                    runCatching { MCPServerManager.getInstance().ensureProjectServer(baseForFile) }
                }
                val effectiveBase = baseForFile
                val relToUse = relPath

                val cacheKey = "$effectiveBase::$relToUse"
                val fileContent = fileCache.getOrPut(cacheKey) {
                    readWithRetry(effectiveBase, relToUse)
                }

                if (fileContent.isBlank()) {
                    // Нет файла — возвращаем базовый чанк без обогащения
                    result.add(
                        EnrichedChunk(
                            chunkId = chunkId,
                            content = baseContent,
                            filePath = relToUse,
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
                        filePath = relToUse,
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

    private suspend fun readWithRetry(baseDir: String, relPath: String): String {
        return try {
            runBlockingRead(relPath)
        } catch (t: Throwable) {
            logger.warn("MCP Enricher: first read failed for '$relPath' (base=$baseDir): ${t.message}")
            // Возможная гонка после смены проекта — дождемся перезапуска и повторим
            delay(300)
            if (baseDir.isNotBlank()) {
                runCatching { MCPServerManager.getInstance().ensureProjectServer(baseDir) }
                waitForServerReady()
            }
            try {
                runBlockingRead(relPath)
            } catch (t2: Throwable) {
                logger.warn("MCP Enricher: second read failed for '$relPath' (base=$baseDir): ${t2.message}")
                ""
            }
        }
    }

    private suspend fun waitForServerReady(maxAttempts: Int = 10, delayMs: Long = 150) {
        val client = mcpClientProvider()
        repeat(maxAttempts) { attempt ->
            runCatching { client.health() }
                .onSuccess { return }
            delay(delayMs)
        }
    }

    private fun resolveBaseAndRel(originalPath: String): Pair<String, String> {
        val normalized = originalPath.replace('\\', '/')
        val isAbsolute = normalized.startsWith("/") || normalized.matches(Regex("^[A-Za-z]:/.*"))
        if (!isAbsolute) {
            // Для относительных путей не трогаем base_dir и не обращаемся к IDE API
            return "" to normalized.trimStart('/')
        }
        val openBases = runCatching {
            ProjectManager.getInstance().openProjects.mapNotNull { it.basePath?.replace('\\', '/') }
        }.getOrElse { emptyList() }
        val bestBase = openBases
            .filter { normalized.startsWith(it.trimEnd('/')) }
            .maxByOrNull { it.length }
        if (bestBase != null) {
            val baseNorm = bestBase.trimEnd('/')
            val rel = normalized.removePrefix(baseNorm).trimStart('/')
            return bestBase to rel
        }
        // Фоллбэк: нет совпадений среди открытых проектов — используем директорию файла как base_dir
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash > 0) {
            val base = normalized.substring(0, lastSlash)
            val rel = normalized.substring(lastSlash + 1)
            base to rel
        } else {
            "" to normalized
        }
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
