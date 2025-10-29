package ru.marslab.ide.ride.service.embedding

import ru.marslab.ide.ride.model.embedding.EmbeddingData
import ru.marslab.ide.ride.model.embedding.FileChunkData
import ru.marslab.ide.ride.model.embedding.IndexedFile
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*
import kotlin.test.Ignore

@Ignore("Disabled RAG tests")
class EmbeddingDatabaseServiceTest {

    private lateinit var tempDir: Path
    private lateinit var dbService: EmbeddingDatabaseService

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("db_test")
        val dbPath = File(tempDir.toFile(), "test.db").absolutePath
        dbService = EmbeddingDatabaseService(dbPath)
    }

    @AfterTest
    fun cleanup() {
        dbService.close()
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should save and retrieve indexed file`() {
        val file = IndexedFile(
            filePath = "/test/file.kt",
            fileHash = "abc123",
            lastModified = System.currentTimeMillis(),
            indexedAt = System.currentTimeMillis()
        )

        val fileId = dbService.saveIndexedFile(file)
        assertTrue(fileId > 0)

        val retrieved = dbService.getIndexedFile("/test/file.kt")
        assertNotNull(retrieved)
        assertEquals(file.filePath, retrieved.filePath)
        assertEquals(file.fileHash, retrieved.fileHash)
    }

    @Test
    fun `should save and retrieve file chunk`() {
        val file = IndexedFile(
            filePath = "/test/file.kt",
            fileHash = "abc123",
            lastModified = System.currentTimeMillis(),
            indexedAt = System.currentTimeMillis()
        )
        val fileId = dbService.saveIndexedFile(file)

        val chunk = FileChunkData(
            fileId = fileId,
            chunkIndex = 0,
            content = "fun test() {}",
            startLine = 0,
            endLine = 0
        )

        val chunkId = dbService.saveFileChunk(chunk)
        assertTrue(chunkId > 0)

        val retrieved = dbService.getChunkById(chunkId)
        assertNotNull(retrieved)
        assertEquals(chunk.content, retrieved.content)
        assertEquals(chunk.chunkIndex, retrieved.chunkIndex)
    }

    @Test
    fun `should save and retrieve embedding`() {
        val file = IndexedFile(
            filePath = "/test/file.kt",
            fileHash = "abc123",
            lastModified = System.currentTimeMillis(),
            indexedAt = System.currentTimeMillis()
        )
        val fileId = dbService.saveIndexedFile(file)

        val chunk = FileChunkData(
            fileId = fileId,
            chunkIndex = 0,
            content = "fun test() {}",
            startLine = 0,
            endLine = 0
        )
        val chunkId = dbService.saveFileChunk(chunk)

        val embedding = EmbeddingData(
            chunkId = chunkId,
            embedding = listOf(0.1f, 0.2f, 0.3f),
            dimension = 3
        )

        val embeddingId = dbService.saveEmbedding(embedding)
        assertTrue(embeddingId > 0)
    }

    @Test
    fun `should delete file index`() {
        val file = IndexedFile(
            filePath = "/test/file.kt",
            fileHash = "abc123",
            lastModified = System.currentTimeMillis(),
            indexedAt = System.currentTimeMillis()
        )

        dbService.saveIndexedFile(file)
        assertNotNull(dbService.getIndexedFile("/test/file.kt"))

        dbService.deleteFileIndex("/test/file.kt")
        assertNull(dbService.getIndexedFile("/test/file.kt"))
    }

    @Test
    fun `should clear all data`() {
        val file = IndexedFile(
            filePath = "/test/file.kt",
            fileHash = "abc123",
            lastModified = System.currentTimeMillis(),
            indexedAt = System.currentTimeMillis()
        )
        dbService.saveIndexedFile(file)

        dbService.clearAll()

        val stats = dbService.getStatistics()
        assertEquals(0, stats["files"])
        assertEquals(0, stats["chunks"])
        assertEquals(0, stats["embeddings"])
    }

    @Test
    fun `should get statistics`() {
        val stats = dbService.getStatistics()
        assertNotNull(stats)
        assertTrue(stats.containsKey("files"))
        assertTrue(stats.containsKey("chunks"))
        assertTrue(stats.containsKey("embeddings"))
    }

    @Test
    fun `should find similar embeddings`() {
        // Создаем несколько эмбеддингов
        val file = IndexedFile(
            filePath = "/test/file.kt",
            fileHash = "abc123",
            lastModified = System.currentTimeMillis(),
            indexedAt = System.currentTimeMillis()
        )
        val fileId = dbService.saveIndexedFile(file)

        val chunk1 = FileChunkData(
            fileId = fileId,
            chunkIndex = 0,
            content = "fun test1() {}",
            startLine = 0,
            endLine = 0
        )
        val chunkId1 = dbService.saveFileChunk(chunk1)

        val embedding1 = EmbeddingData(
            chunkId = chunkId1,
            embedding = listOf(1.0f, 0.0f, 0.0f),
            dimension = 3
        )
        dbService.saveEmbedding(embedding1)

        val chunk2 = FileChunkData(
            fileId = fileId,
            chunkIndex = 1,
            content = "fun test2() {}",
            startLine = 1,
            endLine = 1
        )
        val chunkId2 = dbService.saveFileChunk(chunk2)

        val embedding2 = EmbeddingData(
            chunkId = chunkId2,
            embedding = listOf(0.9f, 0.1f, 0.0f),
            dimension = 3
        )
        dbService.saveEmbedding(embedding2)

        // Ищем похожие
        val query = listOf(1.0f, 0.0f, 0.0f)
        val results = dbService.findSimilarEmbeddings(query, topK = 2)

        assertEquals(2, results.size)
        // Первый результат должен быть наиболее похожим
        assertTrue(results[0].second >= results[1].second)
    }

    @Test
    fun `should replace existing file on save`() {
        val file1 = IndexedFile(
            filePath = "/test/file.kt",
            fileHash = "abc123",
            lastModified = System.currentTimeMillis(),
            indexedAt = System.currentTimeMillis()
        )
        dbService.saveIndexedFile(file1)

        val file2 = IndexedFile(
            filePath = "/test/file.kt",
            fileHash = "def456",
            lastModified = System.currentTimeMillis(),
            indexedAt = System.currentTimeMillis()
        )
        dbService.saveIndexedFile(file2)

        val retrieved = dbService.getIndexedFile("/test/file.kt")
        assertNotNull(retrieved)
        assertEquals("def456", retrieved.fileHash)
    }
}
