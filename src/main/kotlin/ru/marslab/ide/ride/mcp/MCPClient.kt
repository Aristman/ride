package ru.marslab.ide.ride.mcp

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * HTTP клиент для взаимодействия с MCP Server
 */
class MCPClient(private val baseUrl: String = "http://localhost:3000") {
    
    private val logger = Logger.getInstance(MCPClient::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    /**
     * Создать файл
     */
    suspend fun createFile(path: String, content: String, overwrite: Boolean = false): FileResponse {
        val request = CreateFileRequest(path, content, overwrite)
        val response = post("/files", request)
        return json.decodeFromString(FileResponse.serializer(), response)
    }
    
    /**
     * Прочитать файл
     */
    suspend fun readFile(path: String): FileContentResponse {
        val response = get("/files/$path")
        return json.decodeFromString(FileContentResponse.serializer(), response)
    }
    
    /**
     * Обновить файл
     */
    suspend fun updateFile(path: String, content: String): FileResponse {
        val request = UpdateFileRequest(content)
        val response = put("/files/$path", request)
        return json.decodeFromString(FileResponse.serializer(), response)
    }
    
    /**
     * Удалить файл
     */
    suspend fun deleteFile(path: String): DeleteResponse {
        val response = delete("/files/$path")
        return json.decodeFromString(DeleteResponse.serializer(), response)
    }
    
    /**
     * Список файлов
     */
    suspend fun listFiles(dir: String? = null): DirectoryListResponse {
        val url = if (dir != null) "/files?dir=$dir" else "/files"
        val response = get(url)
        return json.decodeFromString(DirectoryListResponse.serializer(), response)
    }
    
    /**
     * Создать директорию
     */
    suspend fun createDirectory(path: String, recursive: Boolean = false): DirectoryResponse {
        val request = CreateDirectoryRequest(path, recursive)
        val response = post("/directories", request)
        return json.decodeFromString(DirectoryResponse.serializer(), response)
    }
    
    /**
     * Удалить директорию
     */
    suspend fun deleteDirectory(path: String): DeleteResponse {
        val response = delete("/directories/$path")
        return json.decodeFromString(DeleteResponse.serializer(), response)
    }
    
    /**
     * Проверка health
     */
    suspend fun health(): HealthResponse {
        val response = get("/health")
        return json.decodeFromString(HealthResponse.serializer(), response)
    }
    
    // HTTP методы
    
    private inline fun <reified T : Any> post(endpoint: String, body: T): String {
        val jsonBody = json.encodeToString(serializer(), body)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl$endpoint"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()
        
        return executeRequest(request)
    }
    
    private inline fun <reified T : Any> put(endpoint: String, body: T): String {
        val jsonBody = json.encodeToString(serializer(), body)
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl$endpoint"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()
        
        return executeRequest(request)
    }
    
    private fun get(endpoint: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl$endpoint"))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        
        return executeRequest(request)
    }
    
    private fun delete(endpoint: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl$endpoint"))
            .timeout(Duration.ofSeconds(30))
            .DELETE()
            .build()
        
        return executeRequest(request)
    }
    
    private fun executeRequest(request: HttpRequest): String {
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() !in 200..299) {
                val errorBody = response.body()
                logger.error("MCP Server error: ${response.statusCode()} - $errorBody")
                throw MCPException("HTTP ${response.statusCode()}: $errorBody")
            }
            
            return response.body()
        } catch (e: Exception) {
            logger.error("Failed to execute MCP request", e)
            throw MCPException("Request failed: ${e.message}", e)
        }
    }
}

// Модели данных

@Serializable
data class CreateFileRequest(
    val path: String,
    val content: String,
    val overwrite: Boolean = false
)

@Serializable
data class UpdateFileRequest(
    val content: String
)

@Serializable
data class CreateDirectoryRequest(
    val path: String,
    val recursive: Boolean = false
)

@Serializable
data class FileResponse(
    val path: String,
    val size: Long,
    val created_at: String,
    val modified_at: String,
    val is_readonly: Boolean,
    val checksum: String
)

@Serializable
data class FileContentResponse(
    val path: String,
    val content: String,
    val size: Long,
    val mime_type: String,
    val checksum: String
)

@Serializable
data class DirectoryResponse(
    val path: String,
    val created_at: String
)

@Serializable
data class DeleteResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class DirectoryListResponse(
    val path: String,
    val files: List<FileInfo>,
    val directories: List<DirectoryInfo>
)

@Serializable
data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val modified_at: String,
    val is_readonly: Boolean
)

@Serializable
data class DirectoryInfo(
    val name: String,
    val path: String,
    val modified_at: String
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val uptime_seconds: Long
)

/**
 * Исключение MCP операций
 */
class MCPException(message: String, cause: Throwable? = null) : Exception(message, cause)
