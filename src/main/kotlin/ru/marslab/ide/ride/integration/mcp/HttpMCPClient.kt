package ru.marslab.ide.ride.integration.mcp

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import ru.marslab.ide.ride.model.mcp.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Клиент для подключения к MCP серверу через HTTP
 * 
 * Использует JSON-RPC over HTTP для коммуникации с сервером
 */
class HttpMCPClient(
    private val config: MCPServerConfig,
    private val timeout: Long = 30000 // 30 секунд по умолчанию
) : MCPClient {
    
    private val logger = Logger.getInstance(HttpMCPClient::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeout))
        .build()
    
    private val connected = AtomicBoolean(false)
    private val requestId = AtomicInteger(0)
    
    init {
        require(config.type == MCPServerType.HTTP) {
            "Config must be of type HTTP"
        }
        require(!config.url.isNullOrBlank()) {
            "URL is required for HTTP client"
        }
    }
    
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (connected.get()) {
            logger.warn("Already connected to ${config.name}")
            return@withContext true
        }
        
        try {
            logger.info("Connecting to MCP server: ${config.name} at ${config.url}")
            
            // Проверяем доступность сервера простым запросом
            val pingRequest = JsonRpcRequest(
                id = requestId.incrementAndGet(),
                method = "ping",
                params = null
            )
            
            try {
                sendRequest(pingRequest)
                connected.set(true)
                logger.info("Successfully connected to ${config.name}")
                true
            } catch (e: Exception) {
                // Если ping не поддерживается, пробуем получить список методов
                logger.debug("Ping failed, trying tools/list")
                val listRequest = JsonRpcRequest(
                    id = requestId.incrementAndGet(),
                    method = "tools/list",
                    params = null
                )
                sendRequest(listRequest)
                connected.set(true)
                logger.info("Successfully connected to ${config.name}")
                true
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to ${config.name}", e)
            throw MCPConnectionException("Failed to connect to ${config.name}: ${e.message}", e)
        }
    }
    
    override suspend fun disconnect() {
        if (!connected.get()) {
            return
        }
        
        logger.info("Disconnecting from ${config.name}")
        connected.set(false)
    }
    
    override fun isConnected(): Boolean = connected.get()
    
    override suspend fun listMethods(): List<MCPMethod> = withContext(Dispatchers.IO) {
        ensureConnected()
        
        try {
            val request = JsonRpcRequest(
                id = requestId.incrementAndGet(),
                method = "tools/list",
                params = null
            )
            
            val response = sendRequest(request)
            
            if (response.hasError()) {
                throw MCPException("Failed to list methods: ${response.error?.message}")
            }
            
            val result = response.result ?: throw MCPException("Empty result")
            val toolsListResponse = json.decodeFromJsonElement<ToolsListResponse>(result)
            
            toolsListResponse.tools.map { tool ->
                MCPMethod(
                    name = tool.name,
                    description = tool.description,
                    inputSchema = tool.inputSchema
                )
            }
        } catch (e: MCPException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to list methods", e)
            throw MCPException("Failed to list methods: ${e.message}", e)
        }
    }
    
    override suspend fun callMethod(methodName: String, arguments: JsonElement?): MCPMethodResult = 
        withContext(Dispatchers.IO) {
            ensureConnected()
            
            val startTime = System.currentTimeMillis()
            
            try {
                val params = buildJsonObject {
                    put("name", methodName)
                    put("arguments", arguments ?: buildJsonObject {})
                }
                
                val request = JsonRpcRequest(
                    id = requestId.incrementAndGet(),
                    method = "tools/call",
                    params = params
                )
                
                val response = sendRequest(request)
                val executionTime = System.currentTimeMillis() - startTime
                
                if (response.hasError()) {
                    return@withContext MCPMethodResult.error(
                        response.error?.message ?: "Unknown error",
                        executionTime
                    )
                }
                
                MCPMethodResult.success(
                    response.result ?: JsonNull,
                    executionTime
                )
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                logger.error("Failed to call method $methodName", e)
                MCPMethodResult.error(e.message ?: "Unknown error", executionTime)
            }
        }
    
    override fun getServerName(): String = config.name
    
    /**
     * Отправляет HTTP запрос с JSON-RPC
     */
    private suspend fun sendRequest(request: JsonRpcRequest): JsonRpcResponse = 
        withContext(Dispatchers.IO) {
            val requestJson = json.encodeToString(request)
            logger.debug("Sending HTTP request to ${config.url}: $requestJson")
            
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.url!!))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofMillis(timeout))
                .build()
            
            try {
                val httpResponse = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
                )
                
                logger.debug("Received HTTP response: ${httpResponse.statusCode()}")
                
                if (httpResponse.statusCode() !in 200..299) {
                    throw MCPException(
                        "HTTP error: ${httpResponse.statusCode()} - ${httpResponse.body()}"
                    )
                }
                
                val responseBody = httpResponse.body()
                logger.debug("Response body: $responseBody")
                
                json.decodeFromString<JsonRpcResponse>(responseBody)
            } catch (e: Exception) {
                logger.error("HTTP request failed", e)
                throw MCPException("HTTP request failed: ${e.message}", e)
            }
        }
    
    /**
     * Проверяет, что клиент подключен
     */
    private fun ensureConnected() {
        if (!connected.get()) {
            throw MCPConnectionException("Not connected to ${config.name}")
        }
    }
}
