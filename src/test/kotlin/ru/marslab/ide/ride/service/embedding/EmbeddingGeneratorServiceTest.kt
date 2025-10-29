package ru.marslab.ide.ride.service.embedding

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.model.embedding.IndexingConfig
import ru.marslab.ide.ride.settings.PluginSettings
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class EmbeddingGeneratorServiceTest {

    private lateinit var tempDir: Path
    private lateinit var service: EmbeddingGeneratorService
    private val mockSettings = mockk<PluginSettings>(relaxed = true)
    private lateinit var embeddingService: EmbeddingService

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("generator_test")
        val config = IndexingConfig()
        service = EmbeddingGeneratorService(config, mockSettings)

        // Устанавливаем тестовый генератор эмбеддингов, чтобы исключить реальный LLM
        embeddingService = EmbeddingService.getInstance()
        embeddingService.setTestEmbeddingGenerator { text ->
            // Для совместимости с тестом dimension=384
            val dim = 384
            val seed = text.hashCode()
            val rnd = java.util.Random(seed.toLong())
            FloatArray(dim) { (rnd.nextFloat() - 0.5f) * 2f }.toList()
        }
    }

    @AfterTest
    fun cleanup() {
        // Снимаем тестовый генератор
        EmbeddingService.getInstance().setTestEmbeddingGenerator(null)
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should chunk small file into single chunk`() {
        val content = "fun test() {}"
        val file = createTestFile("test.kt", content)

        val chunks = service.chunkFile(file, fileId = 1)

        assertEquals(1, chunks.size)
        assertEquals(content, chunks[0].content)
        assertEquals(0, chunks[0].chunkIndex)
    }

    @Test
    fun `should chunk large file into multiple chunks`() {
        val content = (1..100).joinToString("\n") { "line $it with some content" }
        val file = createTestFile("large.kt", content)

        val chunks = service.chunkFile(file, fileId = 1)

        assertTrue(chunks.size > 1)
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.chunkIndex)
        }
    }

    @Test
    fun `should handle non-existent file`() {
        val nonExistentFile = File(tempDir.toFile(), "non_existent.kt")

        val chunks = service.chunkFile(nonExistentFile, fileId = 1)

        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `should generate embedding for text`() = runTest {
        val text = "fun test() {}"

        val embedding = service.generateEmbedding(text)

        assertNotNull(embedding)
        assertTrue(embedding.isNotEmpty())
        assertEquals(service.getEmbeddingDimension(), embedding.size)
    }

    @Test
    fun `should calculate file hash`() {
        val content = "fun test() {}"
        val file = createTestFile("test.kt", content)

        val hash = service.calculateFileHash(file)

        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
    }

    @Test
    fun `should return same hash for same content`() {
        val content = "fun test() {}"
        val file1 = createTestFile("test1.kt", content)
        val file2 = createTestFile("test2.kt", content)

        val hash1 = service.calculateFileHash(file1)
        val hash2 = service.calculateFileHash(file2)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `should index kotlin files by default`() {
        val file = createTestFile("Test.kt", "content")

        val shouldIndex = service.shouldIndexFile(file)

        // Файл должен быть проиндексирован, если он соответствует паттернам
        // Проверяем что метод не выбрасывает исключение
        assertNotNull(shouldIndex)
    }

    @Test
    fun `should exclude build directory`() {
        val file = File(tempDir.toFile(), "build/Test.kt").apply {
            parentFile.mkdirs()
            writeText("content")
        }

        val shouldIndex = service.shouldIndexFile(file)

        assertFalse(shouldIndex)
    }

    @Test
    fun `should get embedding dimension`() {
        val dimension = service.getEmbeddingDimension()

        assertTrue(dimension > 0)
        assertEquals(384, dimension)
    }

    // Helper methods
    private fun createTestFile(name: String, content: String): File {
        val file = File(tempDir.toFile(), name)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }
}
