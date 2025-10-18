package ru.marslab.ide.ride.formatter

import ru.marslab.ide.ride.model.agent.AgentOutputType
import ru.marslab.ide.ride.model.agent.FormattedOutput
import ru.marslab.ide.ride.model.agent.FormattedOutputBlock

/**
 * Форматтер для результатов вызова инструментов (MCP Tools)
 */
class ToolResultFormatter {

    /**
     * Форматирует вызов инструмента
     */
    fun formatToolCall(toolName: String, params: Map<String, Any>): FormattedOutputBlock {
        val content = buildString {
            appendLine("Вызов инструмента: **$toolName**")
            appendLine()
            appendLine("Параметры:")
            params.forEach { (key, value) ->
                appendLine("- `$key`: `$value`")
            }
        }

        val metadata = mutableMapOf<String, Any>()
        metadata["toolName"] = toolName
        metadata["params"] = params
        metadata["callTime"] = System.currentTimeMillis()

        return FormattedOutputBlock(
            type = AgentOutputType.TOOL_RESULT,
            content = content,
            cssClasses = listOf("tool-call", "tool-call-block"),
            metadata = metadata,
            order = 0
        )
    }

    /**
     * Форматирует результат выполнения инструмента
     */
    fun formatToolResult(result: Any, success: Boolean): FormattedOutputBlock {
        val content = buildString {
            if (success) {
                appendLine("✅ **Выполнено успешно**")
            } else {
                appendLine("❌ **Ошибка выполнения**")
            }
            appendLine()
            appendLine("Результат:")
            appendLine("```")
            appendLine(result.toString())
            appendLine("```")
        }

        val metadata = mutableMapOf<String, Any>()
        metadata["success"] = success
        metadata["result"] = result.toString()
        metadata["completionTime"] = System.currentTimeMillis()

        return FormattedOutputBlock(
            type = AgentOutputType.TOOL_RESULT,
            content = content,
            cssClasses = listOf("tool-result", if (success) "tool-success" else "tool-error"),
            metadata = metadata,
            order = 0
        )
    }

    /**
     * Форматирует файловую операцию
     */
    fun formatFileOperation(
        operation: String,
        path: String,
        result: String,
        success: Boolean = true,
        fileSize: Long? = null
    ): FormattedOutput {
        val blocks = mutableListOf<FormattedOutputBlock>()
        var order = 0

        // Основной блок с информацией об операции
        val operationIcon = when (operation.lowercase()) {
            "create", "создание" -> "📝"
            "read", "чтение" -> "📖"
            "update", "write", "изменение", "запись" -> "✏️"
            "delete", "удаление" -> "🗑️"
            "copy", "копирование" -> "📋"
            "move", "перемещение" -> "📁"
            else -> "🔧"
        }

        val operationContent = buildString {
            appendLine("$operationIcon **${operation.capitalize()} файла**")
            appendLine()
            appendLine("**Путь:** `$path`")
            if (fileSize != null) {
                appendLine("**Размер:** ${formatFileSize(fileSize)}")
            }
            appendLine("**Статус:** ${if (success) "✅ Успешно" else "❌ Ошибка"}")
            appendLine()
            appendLine("**Результат:**")
            appendLine(result)
        }

        val operationMetadata = mutableMapOf<String, Any>()
        operationMetadata["operationType"] = operation
        operationMetadata["filePath"] = path
        operationMetadata["success"] = success
        operationMetadata["result"] = result
        if (fileSize != null) {
            operationMetadata["fileSize"] = fileSize
        }

        blocks.add(FormattedOutputBlock(
            type = AgentOutputType.TOOL_RESULT,
            content = operationContent,
            cssClasses = listOf("file-operation", if (success) "file-success" else "file-error"),
            metadata = operationMetadata,
            order = order++
        ))

        // Если операция чтения и результат содержит код, создаем блок кода
        if (operation.lowercase() in listOf("read", "чтение") && success && result.trim().isNotEmpty()) {
            val language = detectLanguage(path)
            blocks.add(FormattedOutputBlock.codeBlock(
                content = result,
                language = language,
                order = order++
            ))
        }

        return FormattedOutput.multiple(blocks)
    }

    /**
     * Форматирует результат создания файла
     */
    fun formatFileCreation(path: String, content: String, success: Boolean = true): FormattedOutput {
        val fileSize = content.toByteArray().size.toLong()
        return formatFileOperation("создание", path, "Файл успешно создан", success, fileSize)
    }

    /**
     * Форматирует результат чтения файла
     */
    fun formatFileReading(path: String, content: String, success: Boolean = true): FormattedOutput {
        val fileSize = content.toByteArray().size.toLong()
        val result = if (success) "Файл успешно прочитан" else "Ошибка чтения файла"
        return formatFileOperation("чтение", path, result, success, fileSize)
    }

    /**
     * Форматирует результат изменения файла
     */
    fun formatFileUpdate(path: String, changes: String, success: Boolean = true): FormattedOutput {
        val result = if (success) "Файл успешно изменен" else "Ошибка изменения файла"
        val blocks = mutableListOf<FormattedOutputBlock>()
        var order = 0

        // Основной блок
        blocks.add(formatFileOperation("изменение", path, result, success).blocks.first().copy(order = order++))

        // Блок с изменениями (diff или просто контент)
        if (success && changes.trim().isNotEmpty()) {
            blocks.add(FormattedOutputBlock.codeBlock(
                content = changes,
                language = detectLanguage(path),
                order = order++
            ))
        }

        return FormattedOutput.multiple(blocks)
    }

    /**
     * Форматирует результат удаления файла
     */
    fun formatFileDeletion(path: String, success: Boolean = true): FormattedOutput {
        val result = if (success) "Файл успешно удален" else "Ошибка удаления файла"
        return formatFileOperation("удаление", path, result, success)
    }

    /**
     * Форматирует множественные операции в одном ответе
     */
    fun formatMultipleOperations(
        operations: List<FileOperation>
    ): FormattedOutput {
        val blocks = mutableListOf<FormattedOutputBlock>()
        var order = 0

        operations.forEach { operation ->
            val block = when (operation.type.lowercase()) {
                "create", "создание" -> formatFileCreation(operation.path, operation.content ?: "", operation.success)
                "read", "чтение" -> formatFileReading(operation.path, operation.content ?: "", operation.success)
                "update", "изменение" -> formatFileUpdate(operation.path, operation.content ?: "", operation.success)
                "delete", "удаление" -> formatFileDeletion(operation.path, operation.success)
                else -> formatFileOperation(operation.type, operation.path, operation.content ?: "", operation.success)
            }

            // Обновляем order для всех блоков
            block.blocks.forEach { it.copy(order = order++) }
            blocks.addAll(block.blocks)
        }

        return FormattedOutput.multiple(blocks)
    }

    /**
     * Форматирует ошибку инструмента
     */
    fun formatToolError(toolName: String, errorMessage: String, params: Map<String, Any>? = null): FormattedOutput {
        val content = buildString {
            appendLine("❌ **Ошибка выполнения инструмента: $toolName**")
            appendLine()
            if (params != null && params.isNotEmpty()) {
                appendLine("Параметры:")
                params.forEach { (key, value) ->
                    appendLine("- `$key`: `$value`")
                }
                appendLine()
            }
            appendLine("Ошибка:")
            appendLine("```")
            appendLine(errorMessage)
            appendLine("```")
        }

        val errorBlock = FormattedOutputBlock(
            type = AgentOutputType.TOOL_RESULT,
            content = content,
            cssClasses = listOf("tool-error", "error-block"),
            metadata = mapOf(
                "toolName" to toolName,
                "error" to errorMessage,
                "success" to false,
                "params" to (params ?: emptyMap())
            ),
            order = 0
        )

        return FormattedOutput.single(errorBlock)
    }

    /**
     * Определяет язык программирования по расширению файла
     */
    private fun detectLanguage(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "py" -> "python"
            "rs" -> "rust"
            "go" -> "go"
            "cpp", "cxx", "cc" -> "cpp"
            "c" -> "c"
            "cs" -> "csharp"
            "php" -> "php"
            "rb" -> "ruby"
            "swift" -> "swift"
            "scala" -> "scala"
            "sh", "bash", "zsh", "fish" -> "bash"
            "ps1" -> "powershell"
            "bat", "cmd" -> "batch"
            "sql" -> "sql"
            "html" -> "html"
            "css" -> "css"
            "scss", "sass" -> "scss"
            "less" -> "less"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "toml" -> "toml"
            "ini" -> "ini"
            "md", "markdown" -> "markdown"
            "dockerfile" -> "dockerfile"
            "gradle" -> "gradle"
            "properties" -> "properties"
            "log" -> "log"
            "txt", "text" -> "text"
            else -> "text"
        }
    }

    /**
     * Форматирует размер файла
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        if (bytes < 1024 * 1024) return "${bytes / 1024}KB"
        if (bytes < 1024 * 1024 * 1024) return "${bytes / (1024 * 1024)}MB"
        return "${bytes / (1024 * 1024 * 1024)}GB"
    }

    /**
     * Класс для представления файловой операции
     */
    data class FileOperation(
        val type: String,
        val path: String,
        val content: String? = null,
        val success: Boolean = true,
        val metadata: Map<String, Any> = emptyMap()
    )
}