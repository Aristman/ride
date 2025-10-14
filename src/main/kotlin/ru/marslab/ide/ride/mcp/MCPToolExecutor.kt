package ru.marslab.ide.ride.mcp

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import ru.marslab.ide.ride.model.FunctionCall
import ru.marslab.ide.ride.model.FunctionResult
import ru.marslab.ide.ride.model.ToolResult

/**
 * Исполнитель MCP tool calls
 * Преобразует вызовы функций от LLM в запросы к MCP Server
 */
class MCPToolExecutor(private val mcpClient: MCPClient) {
    
    private val logger = Logger.getInstance(MCPToolExecutor::class.java)
    
    /**
     * Выполнить tool call и вернуть результат
     */
    suspend fun executeTool(functionCall: FunctionCall): ToolResult = withContext(Dispatchers.IO) {
        logger.info("Executing tool: ${functionCall.name}")
        
        try {
            val result = when (functionCall.name) {
                "create_file" -> executeCreateFile(functionCall.arguments)
                "read_file" -> executeReadFile(functionCall.arguments)
                "update_file" -> executeUpdateFile(functionCall.arguments)
                "delete_file" -> executeDeleteFile(functionCall.arguments)
                "list_files" -> executeListFiles(functionCall.arguments)
                "create_directory" -> executeCreateDirectory(functionCall.arguments)
                "delete_directory" -> executeDeleteDirectory(functionCall.arguments)
                "list_directory" -> executeListDirectory(functionCall.arguments)
                else -> {
                    logger.warn("Unknown tool: ${functionCall.name}")
                    "Error: Unknown tool '${functionCall.name}'"
                }
            }
            
            ToolResult(
                functionResult = FunctionResult(
                    name = functionCall.name,
                    content = result
                )
            )
        } catch (e: Exception) {
            logger.error("Error executing tool ${functionCall.name}", e)
            ToolResult(
                functionResult = FunctionResult(
                    name = functionCall.name,
                    content = "Error: ${e.message}"
                )
            )
        }
    }
    
    private suspend fun executeCreateFile(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "Error: 'path' parameter is required"
        val content = args["content"]?.jsonPrimitive?.content
            ?: return "Error: 'content' parameter is required"
        val overwrite = args["overwrite"]?.jsonPrimitive?.booleanOrNull ?: false
        
        val response = mcpClient.createFile(path, content, overwrite)
        return "File '$path' created successfully. Size: ${response.size} bytes, Checksum: ${response.checksum}"
    }
    
    private suspend fun executeReadFile(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "Error: 'path' parameter is required"
        
        val response = mcpClient.readFile(path)
        return """
            File: ${response.path}
            Size: ${response.size} bytes
            Type: ${response.mime_type}
            
            Content:
            ${response.content}
        """.trimIndent()
    }
    
    private suspend fun executeUpdateFile(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "Error: 'path' parameter is required"
        val content = args["content"]?.jsonPrimitive?.content
            ?: return "Error: 'content' parameter is required"
        
        val response = mcpClient.updateFile(path, content)
        return "File '$path' updated successfully. New size: ${response.size} bytes"
    }
    
    private suspend fun executeDeleteFile(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "Error: 'path' parameter is required"
        
        val response = mcpClient.deleteFile(path)
        return response.message
    }
    
    private suspend fun executeListFiles(args: JsonObject): String {
        val dir = args["dir"]?.jsonPrimitive?.contentOrNull
        
        val response = mcpClient.listFiles(dir)
        
        val filesText = if (response.files.isEmpty()) {
            "No files"
        } else {
            response.files.joinToString("\n") { file ->
                "  - ${file.name} (${file.size} bytes)"
            }
        }
        
        val dirsText = if (response.directories.isEmpty()) {
            "No directories"
        } else {
            response.directories.joinToString("\n") { dir ->
                "  - ${dir.name}/"
            }
        }
        
        return """
            Directory: ${response.path}
            
            Files:
            $filesText
            
            Directories:
            $dirsText
        """.trimIndent()
    }
    
    private suspend fun executeCreateDirectory(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "Error: 'path' parameter is required"
        val recursive = args["recursive"]?.jsonPrimitive?.booleanOrNull ?: false
        
        val response = mcpClient.createDirectory(path, recursive)
        return "Directory '${response.path}' created successfully"
    }
    
    private suspend fun executeDeleteDirectory(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "Error: 'path' parameter is required"
        
        val response = mcpClient.deleteDirectory(path)
        return response.message
    }
    
    private suspend fun executeListDirectory(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.contentOrNull
        
        return executeListFiles(buildJsonObject {
            if (path != null) {
                put("dir", path)
            }
        })
    }
}
