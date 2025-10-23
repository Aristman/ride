package ru.marslab.ide.ride.agent.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.formatter.TerminalOutputFormatter
import ru.marslab.ide.ride.model.agent.*
import ru.marslab.ide.ride.model.terminal.TerminalCommand
import ru.marslab.ide.ride.model.terminal.TerminalCommandResult
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Агент для выполнения команд в локальном терминале
 *
 * Поддерживает выполнение shell-команд с возвратом результата,
 * включая stdout, stderr, код завершения и время выполнения.
 */
class TerminalAgent : Agent {

    private val terminalOutputFormatter = TerminalOutputFormatter()

    override val capabilities: AgentCapabilities = AgentCapabilities(
        stateful = false,
        streaming = true,
        reasoning = false,
        tools = setOf("terminal", "shell", "command-execution"),
        systemPrompt = "Агент для выполнения команд в локальном терминале",
        responseRules = listOf(
            "Выполнять только безопасные команды",
            "Возвращать полный вывод команды включая stdout и stderr",
        )
    )

    private var currentSettings: AgentSettings = AgentSettings()

    override suspend fun ask(req: AgentRequest): AgentResponse {
        return withContext(Dispatchers.IO) {
            try {
                // Получаем рабочую директорию из проекта, если доступна
                val projectBasePath = req.context.project?.basePath
                val command = parseCommandFromRequest(req.request, projectBasePath)
                val result = executeCommand(command)

                // Используем форматтер для создания форматированного вывода
                val formattedOutput = terminalOutputFormatter.formatAsHtml(
                    command = command.command,
                    exitCode = result.exitCode,
                    executionTime = result.executionTime,
                    stdout = result.stdout,
                    stderr = result.stderr,
                    success = result.success
                )

                // Создаем базовый текстовый контент для fallback
                val content =
                    "Command: ${command.command}\nExit Code: ${result.exitCode}\nExecution Time: ${result.executionTime}ms\n\n${result.stdout}${if (result.stderr.isNotEmpty()) "\n\nErrors:\n${result.stderr}" else ""}"

                val metadata = mapOf(
                    "command" to command.command,
                    "exitCode" to result.exitCode,
                    "executionTime" to result.executionTime,
                    "workingDir" to (command.workingDir ?: System.getProperty("user.dir"))
                )

                if (result.success) {
                    AgentResponse.success(content, formattedOutput, metadata)
                } else {
                    AgentResponse(
                        content = content,
                        success = false,
                        error = "Command failed with exit code ${result.exitCode}",
                        formattedOutput = formattedOutput,
                        metadata = metadata
                    )
                }
            } catch (e: Exception) {
                AgentResponse.error("Failed to execute command: ${e.message}")
            }
        }
    }

    override fun start(req: AgentRequest): Flow<AgentEvent>? {
        return flow {
            try {
                emit(AgentEvent.Started)

                val command = parseCommandFromRequest(req.request)
                emit(AgentEvent.ContentChunk("Executing command: ${command.command}"))

                // Выполняем команду синхронно для простоты
                val result = withContext(Dispatchers.IO) {
                    executeCommand(command)
                }

                emit(AgentEvent.ContentChunk("Command execution completed"))

                // Используем форматтер для создания форматированного вывода
                val formattedOutput = terminalOutputFormatter.formatAsHtml(
                    command = command.command,
                    exitCode = result.exitCode,
                    executionTime = result.executionTime,
                    stdout = result.stdout,
                    stderr = result.stderr,
                    success = result.success
                )

                // Создаем базовый текстовый контент для fallback
                val content =
                    "Command: ${command.command}\nExit Code: ${result.exitCode}\nExecution Time: ${result.executionTime}ms\n\n${result.stdout}${if (result.stderr.isNotEmpty()) "\n\nErrors:\n${result.stderr}" else ""}"

                val metadata = mapOf(
                    "command" to command.command,
                    "exitCode" to result.exitCode,
                    "executionTime" to result.executionTime,
                    "workingDir" to (command.workingDir ?: System.getProperty("user.dir"))
                )

                val response = if (result.success) {
                    AgentResponse.success(content, formattedOutput, metadata)
                } else {
                    AgentResponse(
                        content = content,
                        success = false,
                        error = "Command failed with exit code ${result.exitCode}",
                        formattedOutput = formattedOutput,
                        metadata = metadata
                    )
                }

                emit(AgentEvent.Completed(response))
            } catch (e: Exception) {
                emit(AgentEvent.Error("Failed to execute command: ${e.message}"))
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun updateSettings(settings: AgentSettings) {
        currentSettings = settings
    }

    override fun dispose() {
        // Нет ресурсов для освобождения
    }

    /**
     * Парсит команду из текстового запроса
     */
    private fun parseCommandFromRequest(request: String, projectBasePath: String? = null): TerminalCommand {
        // Простая логика парсинга - можно расширить
        val trimmed = request.trim()

        // Проверяем на указание рабочей директории
        val workingDir = if (trimmed.startsWith("cd ")) {
            val parts = trimmed.split(" ", limit = 2)
            if (parts.size > 1) {
                val dir = parts[1].trim()
                // Проверяем, что директория существует
                if (File(dir).exists()) {
                    dir
                } else null
            } else null
        } else {
            // Используем базовую директорию проекта, если доступна
            projectBasePath
        }

        // Извлекаем команду (после cd если было)
        val command = if (trimmed.startsWith("cd ") && trimmed.contains("&&")) {
            val parts = trimmed.split(" ", limit = 3)
            if (parts.size > 2) parts[2].trim() else ""
        } else trimmed

        return TerminalCommand(
            command = command,
            workingDir = workingDir
        )
    }

    /**
     * Выполняет команду и возвращает результат
     */
    private suspend fun executeCommand(command: TerminalCommand): TerminalCommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                val processBuilder = ProcessBuilder()

                // Настройка команды для разных ОС
                val osName = System.getProperty("os.name").lowercase()
                val actualCommand = if (osName.contains("win")) {
                    // На Windows запускаем через cmd без смены codepage, чтение делаем в CP866
                    listOf("cmd", "/c", command.command)
                } else {
                    listOf("/bin/sh", "-c", command.command)
                }

                processBuilder.command(actualCommand)

                // Установка рабочей директории
                command.workingDir?.let { dir ->
                    val dirFile = File(dir)
                    if (dirFile.exists()) {
                        processBuilder.directory(dirFile)
                    }
                }

                // Установка переменных окружения
                command.environmentVariables.forEach { (key, value) ->
                    processBuilder.environment()[key] = value
                }

                val process = processBuilder.start()

                // Выбираем корректную кодировку вывода для ОС
                val charset: Charset = if (osName.contains("win")) Charset.forName("CP866") else Charsets.UTF_8
                val stdout = InputStreamReader(process.inputStream, charset).buffered().use { it.readText() }
                val stderr = InputStreamReader(process.errorStream, charset).buffered().use { it.readText() }

                // Ожидание завершения с таймаутом
                val finished = process.waitFor(command.timeout, TimeUnit.MILLISECONDS)
                val executionTime = System.currentTimeMillis() - startTime

                if (!finished) {
                    process.destroyForcibly()
                    TerminalCommandResult.error(
                        command = command.command,
                        exitCode = -1,
                        stdout = stdout,
                        stderr = "Command timed out after ${command.timeout}ms",
                        executionTime = executionTime
                    )
                } else {
                    val exitCode = process.exitValue()
                    if (exitCode == 0) {
                        TerminalCommandResult.success(
                            command = command.command,
                            stdout = stdout,
                            stderr = stderr,
                            executionTime = executionTime
                        )
                    } else {
                        TerminalCommandResult.error(
                            command = command.command,
                            exitCode = exitCode,
                            stdout = stdout,
                            stderr = stderr,
                            executionTime = executionTime
                        )
                    }
                }
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                TerminalCommandResult.error(
                    command = command.command,
                    exitCode = -1,
                    stdout = "",
                    stderr = e.message ?: "Unknown error",
                    executionTime = executionTime
                )
            }
        }
    }

    /**
     * Выполняет команду со стримингом вывода
     */
    private suspend fun executeCommandStreaming(
        command: TerminalCommand,
        onProgress: (String) -> Unit
    ): TerminalCommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            try {
                val processBuilder = ProcessBuilder()

                // Настройка команды для разных ОС
                val osName = System.getProperty("os.name").lowercase()
                val actualCommand = if (osName.contains("win")) {
                    // На Windows запускаем через cmd без смены codepage, чтение делаем в CP866
                    listOf("cmd", "/c", command.command)
                } else {
                    listOf("/bin/sh", "-c", command.command)
                }

                processBuilder.command(actualCommand)

                // Установка рабочей директории
                command.workingDir?.let { dir ->
                    val dirFile = File(dir)
                    if (dirFile.exists()) {
                        processBuilder.directory(dirFile)
                    }
                }

                val process = processBuilder.start()

                // Асинхронное чтение вывода
                val stdoutJob = async(Dispatchers.IO) {
                    val charset: Charset = if (osName.contains("win")) Charset.forName("CP866") else Charsets.UTF_8
                    BufferedReader(InputStreamReader(process.inputStream, charset)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val text = line!! + "\n"
                            stdoutBuilder.append(text)
                            onProgress("stdout: $text")
                        }
                    }
                }

                val stderrJob = async(Dispatchers.IO) {
                    val charset: Charset = if (osName.contains("win")) Charset.forName("CP866") else Charsets.UTF_8
                    BufferedReader(InputStreamReader(process.errorStream, charset)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val text = line!! + "\n"
                            stderrBuilder.append(text)
                            onProgress("stderr: $text")
                        }
                    }
                }

                // Ожидание завершения
                val finished = process.waitFor(command.timeout, TimeUnit.MILLISECONDS)
                val executionTime = System.currentTimeMillis() - startTime

                stdoutJob.await()
                stderrJob.await()

                if (!finished) {
                    process.destroyForcibly()
                    TerminalCommandResult.error(
                        command = command.command,
                        exitCode = -1,
                        stdout = stdoutBuilder.toString(),
                        stderr = "Command timed out after ${command.timeout}ms",
                        executionTime = executionTime
                    )
                } else {
                    val exitCode = process.exitValue()
                    val stdout = stdoutBuilder.toString()
                    val stderr = stderrBuilder.toString()

                    if (exitCode == 0) {
                        TerminalCommandResult.success(
                            command = command.command,
                            stdout = stdout,
                            stderr = stderr,
                            executionTime = executionTime
                        )
                    } else {
                        TerminalCommandResult.error(
                            command = command.command,
                            exitCode = exitCode,
                            stdout = stdout,
                            stderr = stderr,
                            executionTime = executionTime
                        )
                    }
                }
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                TerminalCommandResult.error(
                    command = command.command,
                    exitCode = -1,
                    stdout = stdoutBuilder.toString(),
                    stderr = e.message ?: "Unknown error",
                    executionTime = executionTime
                )
            }
        }
    }
}