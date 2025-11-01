package ru.marslab.ide.ride.integration.embedding

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import ru.marslab.ide.ride.service.embedding.EmbeddingService

/**
 * Адаптер над EmbeddingService/DB для интерфейса EmbeddingIndexClient
 */
class EmbeddingIndexClientImpl : EmbeddingIndexClient {
    private val logger = Logger.getInstance(EmbeddingIndexClientImpl::class.java)
    private val embeddingService = service<EmbeddingService>()

    private fun currentProjectPath(): String? =
        ProjectManager.getInstance().openProjects.firstOrNull()?.basePath

    override fun searchSimilar(query: String, limit: Int): List<EmbeddingIndexClient.EmbeddingMatch> {
        val projectPath = currentProjectPath() ?: return emptyList()
        return try {
            // Генерируем эмбеддинг и ищем ближайшие
            val embedding = kotlinx.coroutines.runBlocking { embeddingService.generateEmbedding(query) }
            if (embedding.isEmpty()) return emptyList()
            val db = embeddingService.getDatabaseService(projectPath) ?: return emptyList()
            val pairs = db.findSimilarEmbeddings(embedding, limit, null)
            db.close()
            pairs.map { (id, sim) -> EmbeddingIndexClient.EmbeddingMatch(chunkId = id.toString(), similarity = sim.toDouble()) }
        } catch (t: Throwable) {
            logger.warn("EmbeddingIndexClientImpl.searchSimilar failed: ${t.message}", t)
            emptyList()
        }
    }

    override fun getChunkById(chunkId: String): EmbeddingIndexClient.ChunkData? {
        val projectPath = currentProjectPath() ?: return null
        return try {
            val db = embeddingService.getDatabaseService(projectPath) ?: return null
            val id = chunkId.toLongOrNull() ?: return null.also { db.close() }
            val data = db.getChunkById(id)?.let { ch ->
                EmbeddingIndexClient.ChunkData(
                    content = ch.content,
                    filePath = db.getFilePathByChunkId(id)
                )
            }
            db.close()
            data
        } catch (t: Throwable) {
            logger.warn("EmbeddingIndexClientImpl.getChunkById failed: ${t.message}", t)
            null
        }
    }

    override fun getFilePathByChunkId(chunkId: String): String? {
        val projectPath = currentProjectPath() ?: return null
        return try {
            val db = embeddingService.getDatabaseService(projectPath) ?: return null
            val id = chunkId.toLongOrNull() ?: return null.also { db.close() }
            val path = db.getFilePathByChunkId(id)
            db.close()
            path
        } catch (t: Throwable) {
            logger.warn("EmbeddingIndexClientImpl.getFilePathByChunkId failed: ${t.message}", t)
            null
        }
    }
}
