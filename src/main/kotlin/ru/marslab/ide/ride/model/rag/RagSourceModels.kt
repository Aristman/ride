package ru.marslab.ide.ride.model.rag

import ru.marslab.ide.ride.service.rag.RagChunk
import ru.marslab.ide.ride.service.rag.RagResult

/**
 * Модель для источника чанка в RAG ответе
 */
data class RagChunkSource(
    val path: String,
    val startLine: Int,
    val endLine: Int
)

/**
 * Действие открытия файла в IDE
 */
data class RagChunkOpenAction(
    val command: String,
    val path: String,
    val startLine: Int,
    val endLine: Int
) {
    companion object {
        /**
         * Создает действие открытия в формате: open?path={path}&startLine={n}&endLine={m}
         */
        fun create(source: RagChunkSource): RagChunkOpenAction {
            val command = "open?path=${source.path}&startLine=${source.startLine}&endLine=${source.endLine}"
            return RagChunkOpenAction(
                command = command,
                path = source.path,
                startLine = source.startLine,
                endLine = source.endLine
            )
        }
    }
}

/**
 * Расширенная модель чанка с источником и действием открытия
 */
data class RagChunkWithSource(
    val content: String,
    val similarity: Float,
    val source: RagChunkSource,
    val openAction: RagChunkOpenAction
) {
    companion object {
        /**
         * Создает из базового RagChunk
         */
        fun fromRagChunk(chunk: RagChunk): RagChunkWithSource {
            val source = RagChunkSource(
                path = chunk.filePath,
                startLine = chunk.startLine,
                endLine = chunk.endLine
            )
            val openAction = RagChunkOpenAction.create(source)

            return RagChunkWithSource(
                content = chunk.content,
                similarity = chunk.similarity,
                source = source,
                openAction = openAction
            )
        }
    }
}

/**
 * Расширенный результат RAG с поддержкой source links
 */
data class RagResultWithSources(
    val chunks: List<RagChunkWithSource>,
    val totalTokens: Int,
    val query: String,
    val sourceLinksEnabled: Boolean
) {
    companion object {
        /**
         * Создает из базового RagResult
         */
        fun fromRagResult(
            ragResult: RagResult,
            sourceLinksEnabled: Boolean
        ): RagResultWithSources {
            val chunksWithSources = if (sourceLinksEnabled) {
                ragResult.chunks.map { RagChunkWithSource.fromRagChunk(it) }
            } else {
                emptyList()
            }

            return RagResultWithSources(
                chunks = chunksWithSources,
                totalTokens = ragResult.totalTokens,
                query = ragResult.query,
                sourceLinksEnabled = sourceLinksEnabled
            )
        }
    }
}

