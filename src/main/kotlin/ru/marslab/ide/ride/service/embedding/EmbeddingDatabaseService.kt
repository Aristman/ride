package ru.marslab.ide.ride.service.embedding

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.model.embedding.EmbeddingData
import ru.marslab.ide.ride.model.embedding.FileChunkData
import ru.marslab.ide.ride.model.embedding.IndexedFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Сервис для работы с SQLite базой данных эмбеддингов
 */
class EmbeddingDatabaseService(private val dbPath: String) {

    private val logger = Logger.getInstance(EmbeddingDatabaseService::class.java)
    private var connection: Connection? = null

    init {
        initDatabase()
    }

    /**
     * Инициализация базы данных и создание таблиц
     */
    private fun initDatabase() {
        try {
            val dbFile = File(dbPath)
            dbFile.parentFile?.mkdirs()

            // Явно загружаем SQLite драйвер
            try {
                Class.forName("org.sqlite.JDBC")
            } catch (e: ClassNotFoundException) {
                logger.error("SQLite JDBC driver not found", e)
                throw IllegalStateException("SQLite JDBC driver not found. Make sure sqlite-jdbc is in classpath.", e)
            }

            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            createTables()
            logger.info("Embedding database initialized at: $dbPath")
        } catch (e: Exception) {
            logger.error("Failed to initialize embedding database", e)
            throw e
        }
    }

    /**
     * Создание таблиц в БД
     */
    private fun createTables() {
        connection?.createStatement()?.use { stmt ->
            // Таблица файлов
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS indexed_files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_path TEXT UNIQUE NOT NULL,
                    file_hash TEXT NOT NULL,
                    last_modified INTEGER NOT NULL,
                    indexed_at INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Таблица чанков
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS file_chunks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_id INTEGER NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    start_line INTEGER,
                    end_line INTEGER,
                    FOREIGN KEY (file_id) REFERENCES indexed_files(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Таблица эмбеддингов
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS embeddings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    chunk_id INTEGER NOT NULL,
                    embedding BLOB NOT NULL,
                    dimension INTEGER NOT NULL,
                    FOREIGN KEY (chunk_id) REFERENCES file_chunks(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Индексы для ускорения поиска
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_path ON indexed_files(file_path)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunk_file_id ON file_chunks(file_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_embedding_chunk_id ON embeddings(chunk_id)")
        }
    }

    /**
     * Сохранение индексированного файла
     */
    fun saveIndexedFile(file: IndexedFile): Long {
        val sql = """
            INSERT OR REPLACE INTO indexed_files (file_path, file_hash, last_modified, indexed_at)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, file.filePath)
            stmt.setString(2, file.fileHash)
            stmt.setLong(3, file.lastModified)
            stmt.setLong(4, file.indexedAt)
            stmt.executeUpdate()

            // Получаем ID вставленной записи
            val generatedKeys = stmt.generatedKeys
            if (generatedKeys.next()) {
                return generatedKeys.getLong(1)
            }
        }
        return 0
    }

    /**
     * Получение индексированного файла по пути
     */
    fun getIndexedFile(filePath: String): IndexedFile? {
        val sql = "SELECT * FROM indexed_files WHERE file_path = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, filePath)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.toIndexedFile()
            }
        }
        return null
    }

    /**
     * Сохранение чанка файла
     */
    fun saveFileChunk(chunk: FileChunkData): Long {
        val sql = """
            INSERT INTO file_chunks (file_id, chunk_index, content, start_line, end_line)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setLong(1, chunk.fileId)
            stmt.setInt(2, chunk.chunkIndex)
            stmt.setString(3, chunk.content)
            stmt.setInt(4, chunk.startLine)
            stmt.setInt(5, chunk.endLine)
            stmt.executeUpdate()

            val generatedKeys = stmt.generatedKeys
            if (generatedKeys.next()) {
                return generatedKeys.getLong(1)
            }
        }
        return 0
    }

    /**
     * Сохранение эмбеддинга
     */
    fun saveEmbedding(embedding: EmbeddingData): Long {
        val sql = """
            INSERT INTO embeddings (chunk_id, embedding, dimension)
            VALUES (?, ?, ?)
        """.trimIndent()

        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setLong(1, embedding.chunkId)
            stmt.setBytes(2, floatListToByteArray(embedding.embedding))
            stmt.setInt(3, embedding.dimension)
            stmt.executeUpdate()

            val generatedKeys = stmt.generatedKeys
            if (generatedKeys.next()) {
                return generatedKeys.getLong(1)
            }
        }
        return 0
    }

    /**
     * Поиск похожих эмбеддингов (косинусное сходство)
     *
     * Оптимизировано по памяти: поддерживается только topK кандидатов через min-heap.
     * Можно задать минимальный порог схожести для раннего отсечения.
     */
    fun findSimilarEmbeddings(
        queryEmbedding: List<Float>,
        topK: Int = 10,
        minSimilarity: Float? = null
    ): List<Pair<Long, Float>> {
        if (topK <= 0) return emptyList()

        // Конвертируем запрос в примитивный массив для исключения бокса
        val queryArray = FloatArray(queryEmbedding.size) { i -> queryEmbedding[i] }

        // Min-heap по схожести: на вершине минимальный элемент
        val heap = java.util.PriorityQueue<Pair<Long, Float>>(topK) { a, b ->
            a.second.compareTo(b.second)
        }

        val countSql = "SELECT COUNT(*) AS cnt FROM embeddings"
        val pageSql = "SELECT chunk_id, embedding FROM embeddings LIMIT ? OFFSET ?"
        // Небольшой размер страницы уменьшает пиковые аллокации (байтовые массивы/буферы)
        val pageSize = 500

        val conn = connection ?: return emptyList()

        // Получаем количество строк и обходим таблицу батчами
        val total = conn.createStatement().use { st ->
            val rs = st.executeQuery(countSql)
            if (rs.next()) rs.getInt("cnt") else 0
        }

        var offset = 0
        while (offset < total) {
            conn.prepareStatement(pageSql).use { stmt ->
                stmt.setInt(1, pageSize)
                stmt.setInt(2, offset)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val chunkId = rs.getLong("chunk_id")
                    val binStream = rs.getBinaryStream("embedding")

                    val similarity = cosineSimilarityFromStream(queryArray, binStream)

                    // Отсечение по порогу (если задан)
                    if (minSimilarity != null && similarity < minSimilarity) continue

                    if (heap.size < topK) {
                        heap.add(chunkId to similarity)
                    } else if (heap.peek().second < similarity) {
                        heap.poll()
                        heap.add(chunkId to similarity)
                    }
                }
            }
            offset += pageSize
        }

        // Преобразуем heap в отсортированный по убыванию список
        val result = ArrayList<Pair<Long, Float>>(heap.size)
        while (heap.isNotEmpty()) result.add(heap.poll())
        return result.sortedByDescending { it.second }
    }

    /**
     * Получение чанка по ID
     */
    fun getChunkById(chunkId: Long): FileChunkData? {
        val sql = "SELECT * FROM file_chunks WHERE id = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setLong(1, chunkId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.toFileChunk()
            }
        }
        return null
    }

    /**
     * Получение пути файла по ID чанка
     */
    fun getFilePathByChunkId(chunkId: Long): String? {
        val sql = """
            SELECT f.file_path
            FROM file_chunks c
            JOIN indexed_files f ON c.file_id = f.id
            WHERE c.id = ?
        """.trimIndent()

        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setLong(1, chunkId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return rs.getString("file_path")
            }
        }
        return null
    }

    /**
     * Удаление индекса для файла
     */
    fun deleteFileIndex(filePath: String) {
        val file = getIndexedFile(filePath) ?: return
        val sql = "DELETE FROM indexed_files WHERE id = ?"
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setLong(1, file.id)
            stmt.executeUpdate()
        }
    }

    /**
     * Очистка всей базы данных
     */
    fun clearAll() {
        connection?.createStatement()?.use { stmt ->
            stmt.execute("DELETE FROM embeddings")
            stmt.execute("DELETE FROM file_chunks")
            stmt.execute("DELETE FROM indexed_files")
        }
        logger.info("Embedding database cleared")
    }

    /**
     * Получение статистики БД
     */
    fun getStatistics(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        connection?.createStatement()?.use { stmt ->
            val filesRs = stmt.executeQuery("SELECT COUNT(*) as count FROM indexed_files")
            if (filesRs.next()) stats["files"] = filesRs.getInt("count")

            val chunksRs = stmt.executeQuery("SELECT COUNT(*) as count FROM file_chunks")
            if (chunksRs.next()) stats["chunks"] = chunksRs.getInt("count")

            val embeddingsRs = stmt.executeQuery("SELECT COUNT(*) as count FROM embeddings")
            if (embeddingsRs.next()) stats["embeddings"] = embeddingsRs.getInt("count")
        }
        return stats
    }

    /**
     * Закрытие соединения с БД
     */
    fun close() {
        connection?.close()
        connection = null
    }

    // Вспомогательные методы

    private fun ResultSet.toIndexedFile() = IndexedFile(
        id = getLong("id"),
        filePath = getString("file_path"),
        fileHash = getString("file_hash"),
        lastModified = getLong("last_modified"),
        indexedAt = getLong("indexed_at")
    )

    private fun ResultSet.toFileChunk() = FileChunkData(
        id = getLong("id"),
        fileId = getLong("file_id"),
        chunkIndex = getInt("chunk_index"),
        content = getString("content"),
        startLine = getInt("start_line"),
        endLine = getInt("end_line")
    )

    private fun floatListToByteArray(floats: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun byteArrayToFloatList(bytes: ByteArray): List<Float> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = mutableListOf<Float>()
        while (buffer.hasRemaining()) {
            floats.add(buffer.float)
        }
        return floats
    }

    /**
     * Косинусное сходство, вычисляемое напрямую из byte[] BLOB без создания списка Float
     */
    private fun cosineSimilarityFromByteArray(query: FloatArray, bytes: ByteArray): Float {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var dot = 0f
        var normQ = 0f
        var normV = 0f
        var i = 0
        while (buffer.hasRemaining() && i < query.size) {
            val v = buffer.float
            val q = query[i]
            dot += q * v
            normQ += q * q
            normV += v * v
            i++
        }
        val denom = kotlin.math.sqrt(normQ) * kotlin.math.sqrt(normV)
        return if (denom > 0f) dot / denom else 0f
    }

    /**
     * Косинусное сходство, вычисляемое из InputStream BLOB без создания больших массивов
     */
    private fun cosineSimilarityFromStream(query: FloatArray, stream: java.io.InputStream): Float {
        stream.use { inp ->
            val buf = ByteArray(4)
            var dot = 0f
            var normQ = 0f
            var normV = 0f
            var i = 0
            while (i < query.size) {
                var read = 0
                while (read < 4) {
                    val r = inp.read(buf, read, 4 - read)
                    if (r <= 0) {
                        // достигли конца BLOB раньше времени
                        val denomEarly = kotlin.math.sqrt(normQ) * kotlin.math.sqrt(normV)
                        return if (denomEarly > 0f) dot / denomEarly else 0f
                    }
                    read += r
                }
                val v = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).float
                val q = query[i]
                dot += q * v
                normQ += q * q
                normV += v * v
                i++
            }
            val denom = kotlin.math.sqrt(normQ) * kotlin.math.sqrt(normV)
            return if (denom > 0f) dot / denom else 0f
        }
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
}
