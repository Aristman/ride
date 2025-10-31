package ru.marslab.ide.ride.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Менеджер жизненного цикла MCP Server
 * Отвечает за автоматическую установку, запуск и остановку сервера
 */
@Service(Service.Level.APP)
class MCPServerManager {
    private val logger = Logger.getInstance(MCPServerManager::class.java)
    private var serverProcess: Process? = null
    private val serverPort = 3001
    private val serverUrl = "http://localhost:$serverPort"

    @Volatile
    private var isStarting = false

    companion object {
        fun getInstance(): MCPServerManager {
            return ApplicationManager.getApplication().getService(MCPServerManager::class.java)
        }

        private const val GITHUB_RELEASE_URL =
            "https://github.com/Aristman/ride/releases/latest/download"
    }

    /**
     * Проверить, запущен ли сервер
     */
    fun isServerRunning(): Boolean {
        return try {
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$serverUrl/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Автоматическая установка и запуск сервера
     */
    fun ensureServerRunning(): Boolean {
        if (isStarting) {
            logger.info("Server is already starting")
            return false
        }

        if (isServerRunning()) {
            logger.info("MCP Server already running")
            return true
        }

        isStarting = true
        try {
            // Получаем или устанавливаем бинарник
            val serverBinary = getOrInstallServerBinary()
            if (serverBinary == null) {
                logger.error("Failed to obtain MCP Server binary")
                return false
            }

            // Запускаем сервер
            return startServer(serverBinary)
        } finally {
            isStarting = false
        }
    }

    /**
     * Получить или установить бинарник сервера
     */
    private fun getOrInstallServerBinary(): File? {
        val serverDir = getServerDirectory()
        val binaryName = getServerBinaryName()
        val serverBinary = File(serverDir, binaryName)

        // 1. Проверяем существующий бинарник или Python сервер
        val pythonMarkerFile = File(serverDir, "python_server_path.txt")
        if (pythonMarkerFile.exists()) {
            val pythonPath = pythonMarkerFile.readText().trim()
            val pythonDir = File(pythonPath)
            if (pythonDir.exists() && File(pythonDir, "pyproject.toml").exists()) {
                logger.info("Using existing Python MCP Server: ${pythonDir.absolutePath}")
                return File(pythonDir, "src/filesystem_server/main.py")
            }
        }

        if (serverBinary.exists() && serverBinary.canExecute()) {
            logger.info("Using existing MCP Server binary: ${serverBinary.absolutePath}")
            return serverBinary
        }

        // 2. Извлекаем встроенный бинарник
        val embedded = extractEmbeddedBinary(serverBinary)
        if (embedded != null) {
            logger.info("Extracted embedded MCP Server binary")
            return embedded
        }

        // 3. Скачиваем с GitHub
        val downloaded = downloadServerBinary(serverBinary)
        if (downloaded != null) {
            logger.info("Downloaded MCP Server binary from GitHub")
            return downloaded
        }

        // 4. Собираем из исходников
        val built = buildFromSource(serverBinary)
        if (built != null) {
            logger.info("Built MCP Server from source")
            return built
        }

        logger.error("Failed to obtain MCP Server binary")
        return null
    }

    /**
     * Извлечь встроенный бинарник из ресурсов
     */
    private fun extractEmbeddedBinary(targetFile: File): File? {
        return try {
            val platformSuffix = getPlatformSuffix()
            val resourcePath = "/mcp-server/mcp-server-$platformSuffix"

            val inputStream = javaClass.getResourceAsStream(resourcePath)
            if (inputStream == null) {
                logger.warn("Embedded binary not found: $resourcePath")
                return null
            }

            targetFile.parentFile.mkdirs()
            inputStream.use { input ->
                Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            // Установить права на выполнение (Unix)
            if (!isWindows()) {
                val permissions = setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
                )
                Files.setPosixFilePermissions(targetFile.toPath(), permissions)
            }

            targetFile
        } catch (e: Exception) {
            logger.error("Failed to extract embedded binary", e)
            null
        }
    }

    /**
     * Скачать бинарник с GitHub Releases
     */
    private fun downloadServerBinary(targetFile: File): File? {
        return try {
            val platformSuffix = getPlatformSuffix()
            val downloadUrl = "$GITHUB_RELEASE_URL/mcp-server-$platformSuffix"

            logger.info("Downloading MCP Server from: $downloadUrl")

            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            if (response.statusCode() == 200) {
                targetFile.parentFile.mkdirs()
                response.body().use { input ->
                    Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }

                // Установить права на выполнение
                if (!isWindows()) {
                    val permissions = setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                    )
                    Files.setPosixFilePermissions(targetFile.toPath(), permissions)
                }

                targetFile
            } else {
                logger.warn("Failed to download: HTTP ${response.statusCode()}")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to download server binary", e)
            null
        }
    }

    /**
     * Собрать из исходников (Python или Rust)
     */
    private fun buildFromSource(targetFile: File): File? {
        val sourceDir = findSourceDirectory()
        if (sourceDir == null) {
            logger.warn("Source directory not found")
            return null
        }

        return try {
            // Проверяем тип MCP сервера
            if (File(sourceDir, "pyproject.toml").exists()) {
                // Python MCP сервер
                return buildPythonServer(sourceDir, targetFile)
            } else if (File(sourceDir, "Cargo.toml").exists()) {
                // Rust MCP сервер
                return buildRustServer(sourceDir, targetFile)
            } else {
                logger.warn("Unknown MCP server type in directory: ${sourceDir.absolutePath}")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to build from source", e)
            null
        }
    }

    /**
     * Собрать Python MCP сервер
     */
    private fun buildPythonServer(sourceDir: File, targetFile: File): File? {
        return try {
            logger.info("Installing Python MCP Server dependencies...")

            // Проверяем наличие Python
            val pythonCheck = ProcessBuilder("python", "--version")
                .redirectErrorStream(true)
                .start()

            if (pythonCheck.waitFor() != 0) {
                logger.warn("Python not found, trying python3...")
                val python3Check = ProcessBuilder("python3", "--version")
                    .redirectErrorStream(true)
                    .start()

                if (python3Check.waitFor() != 0) {
                    logger.warn("Python3 not found, cannot build Python MCP server")
                    return null
                }
            }

            // Устанавливаем зависимости в режиме разработки
            val installProcess = ProcessBuilder()
                .command("pip", "install", "-e", ".")
                .directory(sourceDir)
                .redirectErrorStream(true)
                .start()

            val installExitCode = installProcess.waitFor()
            if (installExitCode != 0) {
                logger.error("Failed to install Python MCP server dependencies")
                return null
            }

            // Создаем символическую ссылку на main.py или используем путь к исходникам
            targetFile.parentFile.mkdirs()

            // Для Python сервера сохраняем путь к директории исходников
            val markerFile = File(targetFile.parent, "python_server_path.txt")
            markerFile.writeText(sourceDir.absolutePath)

            logger.info("Python MCP Server installed successfully")
            targetFile
        } catch (e: Exception) {
            logger.error("Failed to build Python MCP server", e)
            null
        }
    }

    /**
     * Собрать Rust MCP сервер
     */
    private fun buildRustServer(sourceDir: File, targetFile: File): File? {
        return try {
            // Проверяем наличие cargo
            val cargoCheck = ProcessBuilder("cargo", "--version")
                .redirectErrorStream(true)
                .start()

            if (cargoCheck.waitFor() != 0) {
                logger.warn("Cargo not found, cannot build Rust MCP server")
                return null
            }

            logger.info("Building Rust MCP Server from source (this may take a few minutes)...")

            // Собрать проект
            val buildProcess = ProcessBuilder()
                .command("cargo", "build", "--release")
                .directory(sourceDir)
                .redirectErrorStream(true)
                .start()

            val exitCode = buildProcess.waitFor()
            if (exitCode == 0) {
                // Копировать бинарник
                val builtBinary = File(
                    sourceDir,
                    "target/release/${getServerBinaryName()}"
                )

                if (builtBinary.exists()) {
                    targetFile.parentFile.mkdirs()
                    Files.copy(
                        builtBinary.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    logger.info("Rust MCP Server built successfully")
                    return targetFile
                }
            }

            null
        } catch (e: Exception) {
            logger.error("Failed to build Rust MCP server", e)
            null
        }
    }

    /**
     * Запустить сервер
     */
    private fun startServer(serverBinary: File): Boolean {
        return try {
            logger.info("Starting MCP Server: ${serverBinary.absolutePath}")

            // Создать конфигурацию если не существует (base_dir = корень текущего проекта)
            val baseDir = resolveBaseDir()
            createDefaultConfig(baseDir)

            // Определяем команду запуска в зависимости от типа сервера
            val command = if (serverBinary.name.endsWith(".py") || serverBinary.name == "main") {
                // Python MCP сервер
                listOf("python", "-m", "filesystem_server.main", "serve", "--port", serverPort.toString(), "--base-dir", baseDir ?: "./data")
            } else {
                // Бинарный сервер (Rust)
                listOf(serverBinary.absolutePath)
            }

            // Запустить процесс
            val processBuilder = ProcessBuilder(command)
                .directory(serverBinary.parentFile)
                .redirectErrorStream(true)

            // Добавляем переменные окружения для Python
            if (command.first() == "python") {
                val env = processBuilder.environment()
                env["PYTHONPATH"] = serverBinary.parentFile.toString()
                env["PYTHONUNBUFFERED"] = "1"
            }

            serverProcess = processBuilder.start()

            // Ждем запуска (максимум 15 секунд для Python сервера)
            val maxAttempts = if (command.first() == "python") 30 else 20
            var attempts = 0
            while (attempts < maxAttempts) {
                Thread.sleep(500)
                if (isServerRunning()) {
                    logger.info("MCP Server started successfully on port $serverPort")
                    return true
                }
                attempts++
            }

            logger.error("MCP Server failed to start in time")
            stopServer()
            false
        } catch (e: Exception) {
            logger.error("Failed to start MCP Server", e)
            false
        }
    }

    /**
     * Остановить сервер
     */
    fun stopServer() {
        serverProcess?.let {
            try {
                it.destroy()
                it.waitFor(5, TimeUnit.SECONDS)
                if (it.isAlive) {
                    it.destroyForcibly()
                }
                logger.info("MCP Server stopped")
            } catch (e: Exception) {
                logger.error("Error stopping MCP Server", e)
            }
        }
        serverProcess = null
    }

    /**
     * Создать конфигурацию по умолчанию
     */
    private fun createDefaultConfig(baseDirOverride: String? = null): File {
        val configFile = File(getServerDirectory(), "config.toml")

        val effectiveBaseDir = baseDirOverride ?: "./data"

        if (!configFile.exists()) {
            val config = """
                [server]
                host = "127.0.0.1"
                port = ${serverPort}
                log_level = "info"
                base_dir = "${effectiveBaseDir}"
                max_file_size = 10485760
                allowed_extensions = ["txt", "md", "json", "kt", "java", "py", "js", "xml", "gradle"]
                blocked_paths = ["/etc", "/sys", "/proc", "/boot", "/usr/bin", "/bin", "/sbin", "C:\\Windows", "C:\\Program Files"]
                enable_file_watch = false
                cors_origins = ["http://localhost:63342"]
            """.trimIndent()

            configFile.writeText(config)
        } else if (baseDirOverride != null) {
            // Перезаписываем base_dir при наличии проекта
            var content = configFile.readText()
            content = content.replace(
                Regex("""base_dir\s*=\s*"[^"]*""""),
                "base_dir = \"${effectiveBaseDir}\""
            )
            configFile.writeText(content)
        }

        return configFile
    }

    private fun resolveBaseDir(): String? {
        return try {
            ProjectManager.getInstance().openProjects.firstOrNull()?.basePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Получить URL сервера
     */
    fun getServerUrl(): String = serverUrl

    // Вспомогательные методы

    private fun getServerDirectory(): File {
        val pluginDir = File(System.getProperty("user.home"), ".ride")
        val serverDir = File(pluginDir, "mcp-server")
        serverDir.mkdirs()
        return serverDir
    }

    /**
     * Прочитать текущий base_dir из config.toml
     */
    fun getConfiguredBaseDir(): String? {
        return try {
            val cfg = File(getServerDirectory(), "config.toml")
            if (!cfg.exists()) return null
            cfg.useLines { lines ->
                lines.firstOrNull { it.trim().startsWith("base_dir") }
            }?.substringAfter("=")?.trim()?.trim('"')
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Обеспечить установленный base_dir. Если сервер запущен и base_dir отличается — перезапускаем.
     */
    fun ensureBaseDir(baseDir: String?): Boolean {
        if (baseDir.isNullOrBlank()) return false
        val current = getConfiguredBaseDir()
        if (current == baseDir) return true
        // Перезаписываем конфиг
        createDefaultConfig(baseDir)
        // Если сервер запущен — перезапускаем
        if (isServerRunning()) {
            stopServer()
        }
        return ensureServerRunning()
    }

    private fun getServerBinaryName(): String {
        return if (isWindows()) "mcp-server.exe" else "mcp-server"
    }

    private fun getPlatformSuffix(): String {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        return when {
            osName.contains("windows") -> "windows-x64.exe"
            osName.contains("mac") -> {
                if (osArch.contains("aarch64") || osArch.contains("arm")) {
                    "macos-arm64"
                } else {
                    "macos-x64"
                }
            }

            else -> "linux-x64"
        }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }

    private fun findSourceDirectory(): File? {
        // Попробовать найти исходники Python MCP сервера относительно плагина
        val pythonPaths = listOf(
            File("../mcp-servers/filesystem-server"),
            File("../../mcp-servers/filesystem-server"),
            File("mcp-servers/filesystem-server"),
            File(System.getProperty("user.home"), ".ride/mcp-servers/filesystem-server")
        )

        // Проверяем Python сервер (приоритет)
        for (path in pythonPaths) {
            if (path.exists() && File(path, "pyproject.toml").exists()) {
                logger.info("Found Python MCP server at: ${path.absolutePath}")
                return path
            }
        }

        // Fallback к Rust серверу
        val rustPaths = listOf(
            File("../mcp-server-rust"),
            File("../../mcp-server-rust"),
            File(System.getProperty("user.home"), ".ride/mcp-server-rust")
        )

        for (path in rustPaths) {
            if (path.exists() && File(path, "Cargo.toml").exists()) {
                logger.info("Found Rust MCP server at: ${path.absolutePath}")
                return path
            }
        }

        return null
    }
}
