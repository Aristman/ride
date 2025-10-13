package ru.marslab.ide.ride.integration.mcp

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import ru.marslab.ide.ride.model.mcp.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Клиент для подключения к MCP серверу через stdio
 * 
 * Запускает процесс и общается с ним через stdin/stdout используя JSON-RPC протокол
 */
class StdioMCPClient(
    private val config: MCPServerConfig,
    private val timeout: Long = 30000 // 30 секунд по умолчанию
) : MCPClient {
    
    private val logger = Logger.getInstance(StdioMCPClient::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val connected = AtomicBoolean(false)
    private val requestId = AtomicInteger(0)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        require(config.type == MCPServerType.STDIO) {
            "Config must be of type STDIO"
        }
        require(!config.command.isNullOrBlank()) {
            "Command is required for STDIO client"
        }
    }
    
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (connected.get()) {
            logger.warn("Already connected to ${config.name}")
            return@withContext true
        }
        
        try {
            logger.info("Connecting to MCP server: ${config.name}")
            
            // Создаем ProcessBuilder
            val command = mutableListOf(config.command!!)
            command.addAll(config.args)
            
            val processBuilder = ProcessBuilder(command)
            
            // Устанавливаем переменные окружения
            if (config.env.isNotEmpty()) {
                processBuilder.environment().putAll(config.env)
            }
            
            // Запускаем процесс
            process = processBuilder.start()
            
            // Создаем потоки для чтения/записи
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            
            // Запускаем чтение stderr в отдельной корутине для логирования
            scope.launch {
                val errorReader = BufferedReader(InputStreamReader(process!!.errorStream))
                try {
                    errorReader.useLines { lines ->
                        lines.forEach { line ->
                            logger.warn("[${config.name}] stderr: $line")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error reading stderr", e)
                }
            }
            
            connected.set(true)
            logger.info("Successfully connected to ${config.name}")
            true
        } catch (e: Exception) {
            logger.error("Failed to connect to ${config.name}", e)
            cleanup()
            throw MCPConnectionException("Failed to connect to ${config.name}: ${e.message}", e)
        }
    }
    
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        if (!connected.get()) {
            return@withContext
        }
        
        logger.info("Disconnecting from ${config.name}")
        cleanup()
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
     * Отправляет JSON-RPC запрос и ожидает ответ
     */
    private suspend fun sendRequest(request: JsonRpcRequest): JsonRpcResponse = 
        withTimeout(timeout) {
            val requestJson = json.encodeToString(request)
            logger.debug("Sending request: $requestJson")
            
            // Отправляем запрос
            writer?.write(requestJson)
            writer?.newLine()
            writer?.flush()
            
            // Читаем ответ
            val responseLine = reader?.readLine() 
                ?: throw MCPException("Failed to read response: stream closed")
            
            logger.debug("Received response: $responseLine")
            
            try {
                json.decodeFromString<JsonRpcResponse>(responseLine)
            } catch (e: Exception) {
                throw MCPException("Failed to parse response: ${e.message}", e)
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
    
    /**
     * Очищает ресурсы
     */
    private fun cleanup() {
        try {
            writer?.close()
        } catch (e: Exception) {
            logger.error("Error closing writer", e)
        }
        
        try {
            reader?.close()
        } catch (e: Exception) {
            logger.error("Error closing reader", e)
        }
        
        try {
            process?.destroy()
            process?.waitFor(5, TimeUnit.SECONDS)
            if (process?.isAlive == true) {
                process?.destroyForcibly()
            }
        } catch (e: Exception) {
            logger.error("Error destroying process", e)
        }
        
        writer = null
        reader = null
        process = null
    }
}
