package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.BaseA2AAgent
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.model.orchestrator.AgentType
import java.io.File

/**
 * A2A агент для разбиения кода на чанки (chunks)
 *
 * Разбивает большие файлы и фрагменты кода на управляемые части
 * для дальнейшей обработки и анализа.
 */
class A2ACodeChunkerToolAgent : BaseA2AAgent(
    agentType = AgentType.CODE_CHUNKER,
    a2aAgentId = "a2a-code-chunker-agent",
    supportedMessageTypes = setOf("CODE_CHUNK_REQUEST", "CHUNK_ANALYZE_REQUEST", "CHUNK_MERGE_REQUEST"),
    publishedEventTypes = setOf("TOOL_EXECUTION_STARTED", "TOOL_EXECUTION_COMPLETED", "TOOL_EXECUTION_FAILED")
) {

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        return try {
            when (request.messageType) {
                "CODE_CHUNK_REQUEST" -> handleChunkRequest(request, messageBus)
                "CHUNK_ANALYZE_REQUEST" -> handleAnalyzeRequest(request, messageBus)
                "CHUNK_MERGE_REQUEST" -> handleMergeRequest(request, messageBus)
                else -> createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
            }
        } catch (e: Exception) {
            logger.error("Error in code chunker", e)
            createErrorResponse(request.id, "Code chunking failed: ${e.message}")
        }
    }

    private suspend fun handleChunkRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val content = data["content"] as? String ?: ""
        val filePath = data["file_path"] as? String
        val chunkSize = data["chunk_size"] as? Int ?: 1000
        val overlap = data["overlap"] as? Int ?: 100
        val preserveStructure = data["preserve_structure"] as? Boolean ?: true

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val chunks = withContext(Dispatchers.Default) {
            if (filePath != null) {
                chunkFile(filePath, chunkSize, overlap, preserveStructure)
            } else {
                chunkText(content, chunkSize, overlap, preserveStructure)
            }
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Code chunking completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "CODE_CHUNK_RESULT",
                data = mapOf(
                    "chunks" to chunks,
                    "metadata" to mapOf(
                        "agent" to "CODE_CHUNKER",
                        "total_chunks" to chunks.size,
                        "content_length" to content.length,
                        "chunking_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleAnalyzeRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val chunks = data["chunks"] as? List<Map<String, Any>> ?: emptyList()

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val analysis = withContext(Dispatchers.Default) {
            analyzeChunks(chunks)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Chunk analysis completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "CHUNK_ANALYSIS_RESULT",
                data = mapOf(
                    "analysis" to analysis,
                    "metadata" to mapOf(
                        "agent" to "CODE_CHUNKER",
                        "analyzed_chunks" to chunks.size,
                        "analysis_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleMergeRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val chunks = data["chunks"] as? List<Map<String, Any>> ?: emptyList()
        val strategy = data["merge_strategy"] as? String ?: "sequential"

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val mergedContent = withContext(Dispatchers.Default) {
            mergeChunks(chunks, strategy)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Chunk merging completed"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "CHUNK_MERGE_RESULT",
                data = mapOf(
                    "merged_content" to mergedContent,
                    "metadata" to mapOf(
                        "agent" to "CODE_CHUNKER",
                        "merge_strategy" to strategy,
                        "merged_chunks" to chunks.size,
                        "merge_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private fun createErrorResponse(requestId: String, error: String): AgentMessage.Response {
        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = requestId,
            success = false,
            payload = MessagePayload.ErrorPayload(error = error),
            error = error
        )
    }

    private fun chunkFile(
        filePath: String,
        chunkSize: Int,
        overlap: Int,
        preserveStructure: Boolean
    ): List<CodeChunk> {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: $filePath")
        }

        val content = file.readText()
        return chunkText(content, chunkSize, overlap, preserveStructure).map { chunk ->
            chunk.copy(filePath = filePath)
        }
    }

    private fun chunkText(
        content: String,
        chunkSize: Int,
        overlap: Int,
        preserveStructure: Boolean
    ): List<CodeChunk> {
        if (content.isBlank()) return emptyList()

        val chunks = mutableListOf<CodeChunk>()

        if (preserveStructure) {
            // Интеллектуальное разбиение с сохранением структуры
            chunks.addAll(chunkWithStructure(content, chunkSize, overlap))
        } else {
            // Простое разбиение по символам
            chunks.addAll(chunkByCharacters(content, chunkSize, overlap))
        }

        return chunks
    }

    private fun chunkWithStructure(content: String, chunkSize: Int, overlap: Int): List<CodeChunk> {
        val chunks = mutableListOf<CodeChunk>()
        val lines = content.lines()
        var currentChunk = StringBuilder()
        var currentChunkSize = 0
        var chunkIndex = 0
        var lastChunkEnd = 0

        val structureBoundaries = identifyStructureBoundaries(lines)

        var i = 0
        while (i < lines.size) {
            val line = lines[i] + "\n"
            val lineSize = line.length

            // Проверяем, нужно ли начать новый чанк
            if (currentChunkSize + lineSize > chunkSize && currentChunk.isNotEmpty()) {
                // Ищем лучшую границу для разбиения
                val splitPoint = findBestSplitPoint(i, structureBoundaries, lastChunkEnd, overlap)

                if (splitPoint > lastChunkEnd) {
                    // Создаем чанк до точки разделения
                    val chunkContent = lines.subList(lastChunkEnd, splitPoint).joinToString("\n")
                    chunks.add(CodeChunk(
                        id = "chunk_${chunkIndex++}",
                        content = chunkContent,
                        startLine = lastChunkEnd,
                        endLine = splitPoint - 1,
                        size = chunkContent.length,
                        type = detectChunkType(chunkContent)
                    ))

                    lastChunkEnd = splitPoint
                    currentChunk = StringBuilder()
                    currentChunkSize = 0
                    i = splitPoint - 1 // Вернемся к строке, с которой начинаем новый чанк
                    continue
                }
            }

            currentChunk.append(line)
            currentChunkSize += lineSize
            i++
        }

        // Добавляем последний чанк
        if (currentChunk.isNotEmpty()) {
            val chunkContent = currentChunk.toString().trimEnd()
            chunks.add(CodeChunk(
                id = "chunk_${chunkIndex}",
                content = chunkContent,
                startLine = lastChunkEnd,
                endLine = lines.size - 1,
                size = chunkContent.length,
                type = detectChunkType(chunkContent)
            ))
        }

        return chunks
    }

    private fun chunkByCharacters(content: String, chunkSize: Int, overlap: Int): List<CodeChunk> {
        val chunks = mutableListOf<CodeChunk>()
        var startIndex = 0
        var chunkIndex = 0

        while (startIndex < content.length) {
            val endIndex = minOf(startIndex + chunkSize, content.length)
            val chunkContent = content.substring(startIndex, endIndex)

            chunks.add(CodeChunk(
                id = "chunk_${chunkIndex++}",
                content = chunkContent,
                startLine = 0, // Не определяем строки для простого разбиения
                endLine = 0,
                size = chunkContent.length,
                type = "text"
            ))

            startIndex = endIndex - overlap
            if (startIndex < 0) startIndex = 0
        }

        return chunks
    }

    private fun identifyStructureBoundaries(lines: List<String>): Set<Int> {
        val boundaries = mutableSetOf<Int>()

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                // Начало классов, функций, интерфейсов
                trimmed.matches(Regex("""(class|interface|object|fun|def|function)\s+\w+""")) -> {
                    boundaries.add(index)
                }
                // Начало блоков (if, for, while, try)
                trimmed.matches(Regex("""\s*(if|for|while|try|match|when)\s+.*""")) -> {
                    boundaries.add(index)
                }
                // Пустые строки как возможные границы
                trimmed.isEmpty() && index > 0 -> {
                    boundaries.add(index)
                }
            }
        }

        return boundaries
    }

    private fun findBestSplitPoint(
        currentIndex: Int,
        boundaries: Set<Int>,
        lastChunkEnd: Int,
        overlap: Int
    ): Int {
        // Ищем ближайшую границу структуры перед текущей позицией
        val possibleBoundaries = boundaries.filter {
            it > lastChunkEnd + overlap && it <= currentIndex
        }

        return possibleBoundaries.maxOrNull() ?: currentIndex
    }

    private fun detectChunkType(content: String): String {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }

        return when {
            lines.any { it.matches(Regex("""(class|interface|object)\s+\w+""")) } -> "class"
            lines.any { it.matches(Regex("""fun\s+\w+.*\(.*\).*:""")) } -> "function"
            lines.any { it.matches(Regex("""def\s+\w+.*\(.*\):""")) } -> "function"
            lines.any { it.matches(Regex("""function\s+\w+.*\(.*\)\s*{""")) } -> "function"
            lines.any { it.matches(Regex("""import\s+""")) } -> "imports"
            lines.any { it.matches(Regex("""package\s+""")) } -> "package"
            content.contains("{") || content.contains("}") -> "block"
            else -> "text"
        }
    }

    private fun analyzeChunks(chunks: List<Map<String, Any>>): Map<String, Any> {
        val totalChunks = chunks.size
        val totalSize = chunks.sumOf { (it["size"] as? Int) ?: 0 }
        val types = chunks.groupBy { (it["type"] as? String) ?: "unknown" }
        val sizes = chunks.map { (it["size"] as? Int) ?: 0 }

        return mapOf(
            "total_chunks" to totalChunks,
            "total_size" to totalSize,
            "average_chunk_size" to if (totalChunks > 0) totalSize / totalChunks else 0,
            "min_chunk_size" to if (sizes.isNotEmpty()) sizes.minOrNull() ?: 0 else 0,
            "max_chunk_size" to if (sizes.isNotEmpty()) sizes.maxOrNull() ?: 0 else 0,
            "chunk_types" to types.mapValues { it.value.size },
            "size_distribution" to calculateSizeDistribution(sizes),
            "recommendations" to generateChunkingRecommendations(sizes, types.keys.toList())
        )
    }

    private fun mergeChunks(chunks: List<Map<String, Any>>, strategy: String): String {
        return when (strategy) {
            "sequential" -> chunks.joinToString("\n") { (it["content"] as? String) ?: "" }
            "with_separators" -> chunks.joinToString("\n---\n") { (it["content"] as? String) ?: "" }
            "no_overlap" -> mergeWithoutOverlap(chunks)
            "intelligent" -> mergeIntelligently(chunks)
            else -> chunks.joinToString("\n") { (it["content"] as? String) ?: "" }
        }
    }

    private fun mergeWithoutOverlap(chunks: List<Map<String, Any>>): String {
        val result = StringBuilder()
        var lastEndLine = -1

        chunks.sortedBy { (it["start_line"] as? Int) ?: 0 }.forEach { chunk ->
            val startLine = (chunk["start_line"] as? Int) ?: 0
            val content = (chunk["content"] as? String) ?: ""

            if (startLine > lastEndLine) {
                if (result.isNotEmpty()) {
                    result.append("\n")
                }
                result.append(content)
                lastEndLine = (chunk["end_line"] as? Int) ?: startLine
            }
        }

        return result.toString()
    }

    private fun mergeIntelligently(chunks: List<Map<String, Any>>): String {
        // Умное слияние с учетом типов чанков и их структуры
        val sortedChunks = chunks.sortedWith(compareBy<Map<String, Any>> {
            (it["start_line"] as? Int) ?: 0
        }.thenBy {
            (it["type"] as? String) ?: ""
        })

        val result = StringBuilder()
        var lastType: String? = null

        sortedChunks.forEach { chunk ->
            val content = (chunk["content"] as? String) ?: ""
            val type = (chunk["type"] as? String) ?: "unknown"

            // Добавляем разделители между разными типами контента
            if (lastType != null && lastType != type &&
                (lastType == "class" || type == "class" ||
                 lastType == "function" || type == "function")) {
                result.append("\n\n")
            } else if (result.isNotEmpty() && !content.endsWith("\n")) {
                result.append("\n")
            }

            result.append(content)
            lastType = type
        }

        return result.toString()
    }

    private fun calculateSizeDistribution(sizes: List<Int>): Map<String, Int> {
        val total = sizes.size
        if (total == 0) return emptyMap()

        val small = sizes.count { it < 500 }
        val medium = sizes.count { it in 500..2000 }
        val large = sizes.count { it > 2000 }

        return mapOf(
            "small" to small,
            "medium" to medium,
            "large" to large
        )
    }

    private fun generateChunkingRecommendations(sizes: List<Int>, types: List<String>): List<String> {
        val recommendations = mutableListOf<String>()

        val avgSize = if (sizes.isNotEmpty()) sizes.average() else 0.0
        val maxSize = sizes.maxOrNull() ?: 0

        if (avgSize < 200) {
            recommendations.add("Chunks are quite small (${avgSize.toInt()} chars average). Consider increasing chunk size for better context.")
        }

        if (maxSize > 3000) {
            recommendations.add("Some chunks are very large (${maxSize} chars max). Consider reducing chunk size for better processing.")
        }

        val hasFunctions = types.contains("function")
        val hasClasses = types.contains("class")

        if (!hasFunctions && !hasClasses) {
            recommendations.add("No structured code detected. Consider enabling structure preservation for better chunking.")
        }

        val variance = if (sizes.isNotEmpty()) {
            val mean = sizes.average()
            sizes.map { (it - mean) * (it - mean) }.average()
        } else 0.0

        if (variance > 1000000) { // Высокая дисперсия
            recommendations.add("High variance in chunk sizes detected. Consider using more uniform chunking strategy.")
        }

        return recommendations
    }

    // Data classes
    private data class CodeChunk(
        val id: String,
        val content: String,
        val startLine: Int,
        val endLine: Int,
        val size: Int,
        val type: String,
        val filePath: String? = null,
        val metadata: Map<String, Any> = emptyMap()
    )
}
