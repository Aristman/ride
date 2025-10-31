package ru.marslab.ide.ride.mcp

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import ru.marslab.ide.ride.model.llm.FunctionCall
import ru.marslab.ide.ride.model.llm.FunctionResult
import ru.marslab.ide.ride.model.llm.ToolResult

/**
 * Исполнитель MCP tool calls
 * Преобразует вызовы функций от LLM в запросы к MCP Server
 */
class MCPToolExecutor(
    private val mcpClient: MCPClient,
    private val pathNormalizer: PathNormalizer,
    private val projectPath: String? = null
) {

    private val logger = Logger.getInstance(MCPToolExecutor::class.java)

    /**
     * Разрешает путь относительно проектной директории
     * Если путь относительный - добавляет проектный путь
     * Если путь абсолютный - использует как есть
     */
    private fun resolveProjectPath(path: String): String {
        // Если путь уже абсолютный, используем как есть
        if (path.startsWith("/") || path.contains(":\\")) {
            return path
        }
        
        // Если проектный путь не задан, используем путь как есть
        if (projectPath == null) {
            return path
        }
        
        // Для относительных путей добавляем проектный путь
        val normalizedProjectPath = projectPath.trimEnd('/')
        return "$normalizedProjectPath/$path"
    }

    /**
     * Выполнить tool call и вернуть результат
     */
    suspend fun executeTool(functionCall: FunctionCall): ToolResult = withContext(Dispatchers.IO) {
        logger.info("Executing tool: ${functionCall.name}")
        println("🔧 MCPToolExecutor: Executing ${functionCall.name}")
        println("  Arguments: ${functionCall.arguments}")

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

        // Нормализуем путь через LLM
        val normalizedPath = pathNormalizer.normalizePath(path, "create_file")
        
        // Разрешаем путь относительно проекта
        val fullPath = resolveProjectPath(normalizedPath)

        println("🔧 MCPToolExecutor: create_file")
        println("  Original path: '$path'")
        println("  Normalized path: '$normalizedPath'")
        println("  Full path: '$fullPath'")
        println("  Content length: ${content.length}")

        val response = mcpClient.createFile(fullPath, content, overwrite)
        return "File '$fullPath' created successfully. Size: ${response.size} bytes, Checksum: ${response.checksum}"
    }

    private suspend fun executeReadFile(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "Error: 'path' parameter is required"

        // Нормализуем путь через LLM
        val normalizedPath = pathNormalizer.normalizePath(path, "read_file")
        
        // Разрешаем путь относительно проекта
        val fullPath = resolveProjectPath(normalizedPath)

        println("🔧 MCPToolExecutor: read_file")
        println("  Original path: '$path'")
        println("  Normalized path: '$normalizedPath'")
        println("  Full path: '$fullPath'")

        val response = mcpClient.readFile(fullPath)
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

        // Нормализуем путь через LLM
        val normalizedPath = pathNormalizer.normalizePath(path, "update_file")
        
        // Разрешаем путь относительно проекта
        val fullPath = resolveProjectPath(normalizedPath)

        println("🔧 MCPToolExecutor: update_file")
        println("  Original path: '$path'")
        println("  Normalized path: '$normalizedPath'")
        println("  Full path: '$fullPath'")
        println("  Content length: ${content.length}")

        val response = mcpClient.updateFile(fullPath, content)
        return "File '$fullPath' updated successfully. New size: ${response.size} bytes"
    }

    private suspend fun executeDeleteFile(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "Error: 'path' parameter is required"
        
        // Нормализуем путь через LLM
        val normalizedPath = pathNormalizer.normalizePath(path, "delete_file")
        
        // Разрешаем путь относительно проекта
        val fullPath = resolveProjectPath(normalizedPath)

        println("🔧 MCPToolExecutor: delete_file")
        println("  Original path: '$path'")
        println("  Normalized path: '$normalizedPath'")
        println("  Full path: '$fullPath'")

        val response = mcpClient.deleteFile(fullPath)
        return response.message
    }

    private suspend fun executeListFiles(args: JsonObject): String {
        val dir = args["dir"]?.jsonPrimitive?.contentOrNull
        
        val fullDir = if (dir != null) {
            // Нормализуем путь через LLM
            val normalizedDir = pathNormalizer.normalizePath(dir, "list_files")
            // Разрешаем путь относительно проекта
            resolveProjectPath(normalizedDir)
        } else {
            // Если директория не указана, используем проектную директорию
            projectPath ?: "."
        }

        println("🔧 MCPToolExecutor: list_files")
        println("  Original dir: '$dir'")
        println("  Full dir: '$fullDir'")

        val response = mcpClient.listFiles(fullDir)

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
        val normalizedPath = path.replace("\\", "/").replace("\u0001", "/")

        val response = mcpClient.createDirectory(normalizedPath, recursive)
        return "Directory '${response.path}' created successfully"
    }

    private suspend fun executeDeleteDirectory(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "Error: 'path' parameter is required"
        val normalizedPath = path.replace("\\", "/").replace("\u0001", "/")

        val response = mcpClient.deleteDirectory(normalizedPath)
        return response.message
    }

    private suspend fun executeListDirectory(args: JsonObject): String {
        val path = args["path"]?.jsonPrimitive?.contentOrNull
        val normalizedPath = path?.let { it.replace("\\", "/").replace("\u0001", "/") }

        return executeListFiles(buildJsonObject {
            if (normalizedPath != null) {
                put("dir", normalizedPath)
            }
        })
    }
}
