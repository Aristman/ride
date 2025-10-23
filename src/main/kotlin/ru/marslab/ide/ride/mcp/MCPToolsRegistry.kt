package ru.marslab.ide.ride.mcp

import kotlinx.serialization.json.*
import ru.marslab.ide.ride.model.llm.FunctionTool
import ru.marslab.ide.ride.model.llm.Tool

/**
 * Реестр MCP Tools для интеграции с Yandex GPT
 */
object MCPToolsRegistry {

    /**
     * Получить все доступные tools в формате Yandex GPT
     */
    fun getAllTools(): List<Tool> {
        return listOf(
            createFileTool(),
            readFileTool(),
            updateFileTool(),
            deleteFileTool(),
            listFilesTool(),
            createDirectoryTool(),
            deleteDirectoryTool(),
            listDirectoryTool()
        )
    }

    /**
     * Получить tool по имени
     */
    fun getToolByName(name: String): Tool? {
        return getAllTools().find { it.function.name == name }
    }

    // Tool definitions

    private fun createFileTool() = Tool(
        function = FunctionTool(
            name = "create_file",
            description = "Create a new file with specified content. Use this when user asks to create, write, or save a file.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path to the file (e.g., 'src/Main.kt' or 'README.md')")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Content to write to the file")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("path"))
                    add(JsonPrimitive("content"))
                }
            },
            strict = true
        )
    )

    private fun readFileTool() = Tool(
        function = FunctionTool(
            name = "read_file",
            description = "Read the content of a file. Use this when user asks to read, show, or display file content.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path to the file to read")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("path"))
                }
            },
            strict = true
        )
    )

    private fun updateFileTool() = Tool(
        function = FunctionTool(
            name = "update_file",
            description = "MUST use for ANY file modifications! When user says 'измени', 'обнови', 'модифицируй', 'добавь', 'замени', 'исправь' - ALWAYS call this tool. Use this to modify, edit, update, add code to, or change any existing file content.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path to the file to update (e.g., 'src/Main.kt')")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "COMPLETE new content for the file - not just changes")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("path"))
                    add(JsonPrimitive("content"))
                }
            },
            strict = true
        )
    )

    private fun deleteFileTool() = Tool(
        function = FunctionTool(
            name = "delete_file",
            description = "Delete a file. Use this when user asks to remove or delete a file.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path to the file to delete")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("path"))
                }
            },
            strict = true
        )
    )

    private fun listFilesTool() = Tool(
        function = FunctionTool(
            name = "list_files",
            description = "List all files in a directory. Use this when user asks to see files, list directory contents, or show what files exist.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("dir") {
                        put("type", "string")
                        put("description", "Directory path to list (optional, defaults to current directory)")
                    }
                }
            },
            strict = false
        )
    )

    private fun createDirectoryTool() = Tool(
        function = FunctionTool(
            name = "create_directory",
            description = "Create a new directory. Use this when user asks to create a folder or directory.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put(
                            "description",
                            "Path for the new directory (parent directories will be created automatically)"
                        )
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("path"))
                }
            },
            strict = true
        )
    )

    private fun deleteDirectoryTool() = Tool(
        function = FunctionTool(
            name = "delete_directory",
            description = "Delete a directory and all its contents. Use this when user asks to remove or delete a folder.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Path to the directory to delete")
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("path"))
                }
            },
            strict = true
        )
    )

    private fun listDirectoryTool() = Tool(
        function = FunctionTool(
            name = "list_directory",
            description = "List contents of a directory including files and subdirectories. Use this when user asks to see directory structure.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Directory path to list (optional)")
                    }
                }
            },
            strict = false
        )
    )
}
