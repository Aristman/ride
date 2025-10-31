package ru.marslab.ide.ride.service.mcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPServerStatus
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления локальными MCP серверами
 *
 * Отвечает за запуск и остановку MCP серверов, запущенных локально
 */
@Service(Service.Level.PROJECT)
class LocalMCPServerManager(private val project: Project) {

    private val logger = Logger.getInstance(LocalMCPServerManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Процессы запущенных серверов
    private val serverProcesses = ConcurrentHashMap<String, Process>()

    // Статусы серверов
    private val serverStatuses = ConcurrentHashMap<String, MCPServerStatus>()

    companion object {
        fun getInstance(project: Project): LocalMCPServerManager =
            project.getService(LocalMCPServerManager::class.java)
    }

    /**
     * Запускает локальный MCP сервер
     *
     * @param config Конфигурация сервера
     * @return true если сервер успешно запущен
     */
    suspend fun startServer(config: MCPServerConfig): Boolean = withContext(Dispatchers.IO) {
        val serverName = config.name
        logger.info("Запуск локального MCP сервера: $serverName")

        try {
            // Проверяем, что сервер еще не запущен
            if (serverProcesses.containsKey(serverName)) {
                logger.warn("Сервер $serverName уже запущен")
                return@withContext true
            }

            // Запускаем процесс
            val process = startServerProcess(config)
            if (process != null) {
                serverProcesses[serverName] = process
                serverStatuses[serverName] = MCPServerStatus(
                    name = serverName,
                    connected = true
                )

                // Ждем некоторое время для проверки запуска
                delay(2000)

                // Проверяем, что процесс еще жив
                if (process.isAlive) {
                    logger.info("MCP сервер $serverName успешно запущен, PID: ${process.pid()}")

                    // Запускаем мониторинг процесса
                    monitorProcess(serverName, process)

                    return@withContext true
                } else {
                    logger.error("MCP сервер $serverName завершился с ошибкой")
                    serverProcesses.remove(serverName)
                    serverStatuses[serverName] = MCPServerStatus(
                        name = serverName,
                        connected = false,
                        error = "MCP сервер завершился с ошибкой"
                    )
                    return@withContext false
                }
            } else {
                logger.error("Не удалось запустить процесс для сервера $serverName")
                serverStatuses[serverName] = MCPServerStatus(
                        name = serverName,
                        connected = false,
                        error = "MCP сервер завершился с ошибкой"
                    )
                return@withContext false
            }
        } catch (e: Exception) {
            logger.error("Ошибка при запуске MCP сервера $serverName", e)
            serverStatuses[serverName] = MCPServerStatus(
                        name = serverName,
                        connected = false,
                        error = "MCP сервер завершился с ошибкой"
                    )
            return@withContext false
        }
    }

    /**
     * Останавливает локальный MCP сервер
     *
     * @param serverName Имя сервера
     * @return true если сервер успешно остановлен
     */
    suspend fun stopServer(serverName: String): Boolean = withContext(Dispatchers.IO) {
        logger.info("Остановка MCP сервера: $serverName")

        val process = serverProcesses[serverName]
        if (process != null && process.isAlive) {
            try {
                // Сначала пытаемся остановить gracefully
                process.destroy()

                // Ждем некоторое время
                delay(3000)

                // Если процесс все еще жив, принудительно завершаем
                if (process.isAlive) {
                    process.destroyForcibly()
                    delay(1000)
                }

                serverProcesses.remove(serverName)
                serverStatuses[serverName] = MCPServerStatus(
                    name = serverName,
                    connected = false
                )

                logger.info("MCP сервер $serverName успешно остановлен")
                return@withContext true
            } catch (e: Exception) {
                logger.error("Ошибка при остановке MCP сервера $serverName", e)
                return@withContext false
            }
        } else {
            logger.warn("Сервер $serverName не найден или уже остановлен")
            serverProcesses.remove(serverName)
            serverStatuses[serverName] = MCPServerStatus(
                    name = serverName,
                    connected = false
                )
            return@withContext true
        }
    }

    /**
     * Останавливает все локальные MCP серверы
     */
    suspend fun stopAllServers() = withContext(Dispatchers.IO) {
        logger.info("Остановка всех MCP серверов")

        val serverNames = serverProcesses.keys.toList()
        serverNames.forEach { serverName ->
            stopServer(serverName)
        }
    }

    /**
     * Проверяет, запущен ли сервер
     *
     * @param serverName Имя сервера
     * @return true если сервер запущен
     */
    fun isServerRunning(serverName: String): Boolean {
        val process = serverProcesses[serverName]
        return process != null && process.isAlive
    }

    /**
     * Получает статус сервера
     *
     * @param serverName Имя сервера
     * @return Статус сервера
     */
    fun getServerStatus(serverName: String): MCPServerStatus {
        return serverStatuses[serverName] ?: MCPServerStatus(
            name = serverName,
            connected = false
        )
    }

    /**
     * Запускает процесс MCP сервера
     */
    private fun startServerProcess(config: MCPServerConfig): Process? {
        return try {
            val projectDir = project.basePath
            val scriptPath = File(projectDir, "scripts/start-mcp-server.sh")

            if (!scriptPath.exists()) {
                logger.error("Скрипт запуска MCP сервера не найден: ${scriptPath.absolutePath}")
                return null
            }

            val processBuilder = ProcessBuilder(
                "bash", scriptPath.absolutePath
            ).apply {
                // Устанавливаем рабочую директорию
                directory(File(projectDir))

                // Перенаправляем вывод в логи
                redirectErrorStream(true)

                // Устанавливаем переменные окружения из конфигурации
                config.env["RIDE_FS_HOST"]?.let { host ->
                    environment()["RIDE_FS_HOST"] = host
                }
                config.env["RIDE_FS_PORT"]?.let { port ->
                    environment()["RIDE_FS_PORT"] = port
                }
                config.env["RIDE_FS_BASE_DIR"]?.let { baseDir ->
                    environment()["RIDE_FS_BASE_DIR"] = baseDir
                }
                config.env["RIDE_FS_MAX_FILE_SIZE"]?.let { size ->
                    environment()["RIDE_FS_MAX_FILE_SIZE"] = size
                }
                config.env["RIDE_FS_ALLOWED_EXTENSIONS"]?.let { ext ->
                    environment()["RIDE_FS_ALLOWED_EXTENSIONS"] = ext
                }
                config.env["RIDE_FS_LOG_LEVEL"]?.let { level ->
                    environment()["RIDE_FS_LOG_LEVEL"] = level
                }
            }

            val process = processBuilder.start()

            // Запускаем поток для чтения вывода процесса
            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        logger.info("MCP Server[${config.name}]: $line")
                    }
                } catch (e: Exception) {
                    logger.error("Ошибка при чтении вывода MCP сервера ${config.name}", e)
                }
            }

            process
        } catch (e: Exception) {
            logger.error("Ошибка при запуске процесса MCP сервера ${config.name}", e)
            null
        }
    }

    /**
     * Мониторит процесс сервера
     */
    private fun monitorProcess(serverName: String, process: Process) {
        scope.launch {
            try {
                // Ждем завершения процесса
                process.waitFor()

                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    logger.error("MCP сервер $serverName завершился с кодом $exitCode")
                    serverStatuses[serverName] = MCPServerStatus(
                        name = serverName,
                        connected = false,
                        error = "MCP сервер завершился с ошибкой"
                    )
                } else {
                    logger.info("MCP сервер $serverName завершился нормально")
                    serverStatuses[serverName] = MCPServerStatus(
                    name = serverName,
                    connected = false
                )
                }

                // Удаляем процесс из карты запущенных
                serverProcesses.remove(serverName)
            } catch (e: Exception) {
                logger.error("Ошибка при мониторинге MCP сервера $serverName", e)
                serverStatuses[serverName] = MCPServerStatus(
                        name = serverName,
                        connected = false,
                        error = "MCP сервер завершился с ошибкой"
                    )
                serverProcesses.remove(serverName)
            }
        }
    }
}