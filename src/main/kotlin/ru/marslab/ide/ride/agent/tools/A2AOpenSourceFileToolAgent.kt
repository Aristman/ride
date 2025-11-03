package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.BaseA2AAgent
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.model.orchestrator.AgentType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A2A агент для операций с файлами
 *
 * Предоставляет функциональность для чтения, записи и анализа файлов,
 * а также для навигации по файловой системе проекта.
 */
class A2AOpenSourceFileToolAgent : BaseA2AAgent(
    agentType = AgentType.FILE_OPERATIONS,
    a2aAgentId = "a2a-file-operations-agent",
    supportedMessageTypes = setOf(
        "OPEN_FILE_REQUEST",
        "READ_FILE_REQUEST",
        "WRITE_FILE_REQUEST",
        "LIST_FILES_REQUEST",
        "FILE_INFO_REQUEST",
        "SEARCH_FILES_REQUEST"
    ),
    publishedEventTypes = setOf("TOOL_EXECUTION_STARTED", "TOOL_EXECUTION_COMPLETED", "TOOL_EXECUTION_FAILED")
) {

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        return try {
            when (request.messageType) {
                "OPEN_FILE_REQUEST" -> handleOpenFileRequest(request, messageBus)
                "READ_FILE_REQUEST" -> handleReadFileRequest(request, messageBus)
                "WRITE_FILE_REQUEST" -> handleWriteFileRequest(request, messageBus)
                "LIST_FILES_REQUEST" -> handleListFilesRequest(request, messageBus)
                "FILE_INFO_REQUEST" -> handleFileInfoRequest(request, messageBus)
                "SEARCH_FILES_REQUEST" -> handleSearchFilesRequest(request, messageBus)
                else -> createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
            }
        } catch (e: Exception) {
            logger.error("Error in file operations agent", e)
            createErrorResponse(request.id, "File operation failed: ${e.message}")
        }
    }

    private suspend fun handleOpenFileRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val filePath = data["file_path"] as? String ?: ""
        val lineRange = data["line_range"] as? Map<String, Int>
        val encoding = data["encoding"] as? String ?: "UTF-8"

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.IO) {
            openFile(filePath, lineRange, encoding)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "File opened successfully"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "OPEN_FILE_RESULT",
                data = mapOf(
                    "file_data" to result,
                    "metadata" to mapOf(
                        "agent" to "FILE_OPERATIONS",
                        "file_path" to filePath,
                        "encoding" to encoding,
                        "open_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleReadFileRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val filePath = data["file_path"] as? String ?: ""
        val offset = data["offset"] as? Long ?: 0
        val length = data["length"] as? Int

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.IO) {
            readFile(filePath, offset, length)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "File read successfully"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "READ_FILE_RESULT",
                data = mapOf(
                    "content" to result,
                    "metadata" to mapOf(
                        "agent" to "FILE_OPERATIONS",
                        "file_path" to filePath,
                        "read_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleWriteFileRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val filePath = data["file_path"] as? String ?: ""
        val content = data["content"] as? String ?: ""
        val createDirectories = data["create_directories"] as? Boolean ?: true
        val backup = data["backup"] as? Boolean ?: false

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.IO) {
            writeFile(filePath, content, createDirectories, backup)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "File written successfully"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "WRITE_FILE_RESULT",
                data = mapOf(
                    "write_result" to result,
                    "metadata" to mapOf(
                        "agent" to "FILE_OPERATIONS",
                        "file_path" to filePath,
                        "content_length" to content.length,
                        "write_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleListFilesRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val directoryPath = data["directory_path"] as? String ?: "."
        val recursive = data["recursive"] as? Boolean ?: false
        val includePattern = data["include_pattern"] as? String
        val excludePattern = data["exclude_pattern"] as? String

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.IO) {
            listFiles(directoryPath, recursive, includePattern, excludePattern)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "Files listed successfully"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "LIST_FILES_RESULT",
                data = mapOf(
                    "files" to result,
                    "metadata" to mapOf(
                        "agent" to "FILE_OPERATIONS",
                        "directory_path" to directoryPath,
                        "recursive" to recursive,
                        "list_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleFileInfoRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val filePath = data["file_path"] as? String ?: ""

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.IO) {
            getFileInfo(filePath)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "File info retrieved successfully"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "FILE_INFO_RESULT",
                data = mapOf(
                    "file_info" to result,
                    "metadata" to mapOf(
                        "agent" to "FILE_OPERATIONS",
                        "file_path" to filePath,
                        "info_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private suspend fun handleSearchFilesRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val directoryPath = data["directory_path"] as? String ?: "."
        val searchPattern = data["search_pattern"] as? String ?: ""
        val searchInContent = data["search_in_content"] as? Boolean ?: false
        val fileExtensions = data["file_extensions"] as? List<String>
        val maxResults = data["max_results"] as? Int ?: 100

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_STARTED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "STARTED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis()
            )
        )

        val result = withContext(Dispatchers.IO) {
            searchFiles(directoryPath, searchPattern, searchInContent, fileExtensions, maxResults)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "TOOL_EXECUTION_COMPLETED",
            payload = MessagePayload.ExecutionStatusPayload(
                status = "COMPLETED",
                agentId = a2aAgentId,
                requestId = request.id,
                timestamp = System.currentTimeMillis(),
                result = "File search completed successfully"
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "SEARCH_FILES_RESULT",
                data = mapOf(
                    "search_results" to result,
                    "metadata" to mapOf(
                        "agent" to "FILE_OPERATIONS",
                        "directory_path" to directoryPath,
                        "search_pattern" to searchPattern,
                        "search_timestamp" to System.currentTimeMillis()
                    )
                )
            )
        )
    }

    private fun createErrorResponse(requestId: String, error: String): AgentMessage.Response {
        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = requestId,
            success = false,
            payload = MessagePayload.ErrorPayload(error = error),
            error = error
        )
    }

    private suspend fun openFile(
        filePath: String,
        lineRange: Map<String, Int>?,
        encoding: String
    ): Map<String, Any> {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: $filePath")
        }

        val content = file.readText(Charsets.UTF_8) // Для простоты всегда UTF-8
        val lines = content.lines()

        val actualContent = if (lineRange != null) {
            val start = (lineRange["start"] ?: 1) - 1 // 0-based indexing
            val end = (lineRange["end"] ?: lines.size)

            if (start >= 0 && start < lines.size && end > start && end <= lines.size) {
                lines.subList(start, end).joinToString("\n")
            } else {
                throw IllegalArgumentException("Invalid line range: $lineRange")
            }
        } else {
            content
        }

        return mapOf<String, Any>(
            "path" to file.absolutePath,
            "name" to file.name,
            "extension" to file.extension,
            "size" to file.length(),
            "content" to actualContent,
            "total_lines" to lines.size,
            "line_range" to (lineRange ?: emptyMap<String, Int>()),
            "last_modified" to file.lastModified(),
            "is_readable" to file.canRead(),
            "is_writable" to file.canWrite(),
            "language" to detectLanguage(file.extension)
        )
    }

    private suspend fun readFile(filePath: String, offset: Long, length: Int?): String {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: $filePath")
        }

        return if (length != null) {
            file.readText().substring(offset.toInt(), minOf(offset.toInt() + length, file.readText().length))
        } else {
            file.readText().substring(offset.toInt())
        }
    }

    private suspend fun writeFile(
        filePath: String,
        content: String,
        createDirectories: Boolean,
        backup: Boolean
    ): Map<String, Any> {
        val file = File(filePath)

        // Создаем директории если нужно
        if (createDirectories && file.parentFile != null && !file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        // Создаем бэкап если нужно
        if (backup && file.exists()) {
            val backupFile = File("${file.absolutePath}.backup.${System.currentTimeMillis()}")
            file.copyTo(backupFile, overwrite = true)
        }

        // Записываем файл
        file.writeText(content)

        return mapOf<String, Any>(
            "path" to file.absolutePath,
            "bytes_written" to content.toByteArray().size,
            "success" to true,
            "backup_created" to (backup && file.exists()),
            "timestamp" to System.currentTimeMillis()
        )
    }

    private suspend fun listFiles(
        directoryPath: String,
        recursive: Boolean,
        includePattern: String?,
        excludePattern: String?
    ): List<Map<String, Any>> {
        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            throw IllegalArgumentException("Directory does not exist or is not a directory: $directoryPath")
        }

        val files = mutableListOf<File>()
        val includeRegex = includePattern?.let { Regex(it, RegexOption.IGNORE_CASE) }
        val excludeRegex = excludePattern?.let { Regex(it, RegexOption.IGNORE_CASE) }

        fun scan(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory && recursive) {
                    scan(file)
                } else if (file.isFile) {
                    val matches = when {
                        includeRegex != null && excludeRegex != null -> {
                            includeRegex.matches(file.name) && !excludeRegex.matches(file.name)
                        }
                        includeRegex != null -> includeRegex.matches(file.name)
                        excludeRegex != null -> !excludeRegex.matches(file.name)
                        else -> true
                    }

                    if (matches) {
                        files.add(file)
                    }
                }
            }
        }

        scan(directory)

        return files.map { file ->
            mapOf(
                "path" to file.absolutePath,
                "name" to file.name,
                "extension" to file.extension,
                "size" to file.length(),
                "last_modified" to file.lastModified(),
                "is_directory" to file.isDirectory,
                "is_readable" to file.canRead(),
                "is_writable" to file.canWrite(),
                "relative_path" to file.relativeTo(directory).path
            )
        }
    }

    private suspend fun getFileInfo(filePath: String): Map<String, Any> {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: $filePath")
        }

        val path = Paths.get(filePath)
        val contentType = try {
            Files.probeContentType(path)
        } catch (e: Exception) {
            "unknown"
        }

        return mapOf<String, Any>(
            "path" to file.absolutePath,
            "name" to file.name,
            "extension" to file.extension,
            "size" to file.length(),
            "last_modified" to file.lastModified(),
            "created" to (file.parentFile?.let { parent ->
                File(parent, file.name).let { f -> if (f.exists()) f.lastModified() else 0L }
            } ?: 0L),
            "is_directory" to file.isDirectory,
            "is_file" to file.isFile,
            "is_readable" to file.canRead(),
            "is_writable" to file.canWrite(),
            "is_executable" to file.canExecute(),
            "content_type" to contentType,
            "language" to detectLanguage(file.extension),
            "parent_directory" to file.parent,
            "absolute_path" to file.absolutePath
        )
    }

    private suspend fun searchFiles(
        directoryPath: String,
        searchPattern: String,
        searchInContent: Boolean,
        fileExtensions: List<String>?,
        maxResults: Int
    ): List<Map<String, Any>> {
        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            throw IllegalArgumentException("Directory does not exist or is not a directory: $directoryPath")
        }

        val results = mutableListOf<Map<String, Any>>()
        val searchRegex = Regex(searchPattern, RegexOption.IGNORE_CASE)

        fun searchInFile(file: File): List<SearchMatch> {
            if (!searchInContent) return emptyList()

            val matches = mutableListOf<SearchMatch>()
            val lines = file.readText().lines()

            lines.forEachIndexed { lineIndex, line ->
                if (searchRegex.containsMatchIn(line)) {
                    val allMatches = searchRegex.findAll(line)
                    allMatches.forEach { match ->
                        matches.add(SearchMatch(
                            lineNumber = lineIndex + 1,
                            lineContent = line,
                            matchStart = match.range.first,
                            matchEnd = match.range.last + 1,
                            matchedText = match.value
                        ))
                    }
                }
            }

            return matches
        }

        fun scan(dir: File) {
            if (results.size >= maxResults) return

            dir.listFiles()?.forEach { file ->
                if (results.size >= maxResults) return@forEach

                if (file.isDirectory) {
                    scan(file)
                } else if (file.isFile) {
                    // Фильтрация по расширениям
                    if (fileExtensions != null && file.extension !in fileExtensions) {
                        return@forEach
                    }

                    val nameMatches = searchRegex.containsMatchIn(file.name)
                    val contentMatches = searchInFile(file)

                    if (nameMatches || contentMatches.isNotEmpty()) {
                        results.add(mapOf(
                            "path" to file.absolutePath,
                            "name" to file.name,
                            "extension" to file.extension,
                            "size" to file.length(),
                            "name_match" to nameMatches,
                            "content_matches" to contentMatches,
                            "total_content_matches" to contentMatches.size,
                            "last_modified" to file.lastModified()
                        ))
                    }
                }
            }
        }

        scan(directory)

        return results
    }

    private fun detectLanguage(extension: String): String {
        return when (extension.lowercase()) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "scala" -> "scala"
            "groovy" -> "groovy"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "jsx" -> "react"
            "tsx" -> "react-typescript"
            "html" -> "html"
            "css" -> "css"
            "scss", "sass" -> "sass"
            "json" -> "json"
            "xml" -> "xml"
            "yaml", "yml" -> "yaml"
            "md" -> "markdown"
            "txt" -> "text"
            "sql" -> "sql"
            "sh" -> "shell"
            "gradle" -> "gradle"
            "properties" -> "properties"
            else -> "unknown"
        }
    }

    // Data classes
    private data class SearchMatch(
        val lineNumber: Int,
        val lineContent: String,
        val matchStart: Int,
        val matchEnd: Int,
        val matchedText: String
    )
}
