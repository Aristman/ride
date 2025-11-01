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
    private val serverPort = 3000
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
     * Автоматическая установка и запуск универсального MCP сервера
     */
    fun ensureServerRunning(): Boolean {
        if (isStarting) {
            logger.info("Server is already starting")
            return false
        }

        // Проверяем, запущен ли уже сервер
        if (isServerRunning()) {
            logger.info("MCP Server already running")
            return true
        }

        isStarting = true
        try {
            // Пытаемся получить встроенный бинарник
            val serverBinary = getOrInstallServerBinary()
            if (serverBinary == null) {
                logger.warn("No embedded MCP Server binary available. Please start external MCP server manually.")
                return false
            }

            // Запускаем универсальный сервер
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

        // 1. Проверяем существующий бинарник
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

        logger.info("No embedded MCP Server binary available. External server can be used instead.")
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
     * Собрать из исходников (если есть Rust)
     */
    private fun buildFromSource(targetFile: File): File? {
        return try {
            // Проверяем наличие cargo
            val cargoCheck = ProcessBuilder("cargo", "--version")
                .redirectErrorStream(true)
                .start()

            if (cargoCheck.waitFor() != 0) {
                logger.warn("Cargo not found, cannot build from source")
                return null
            }

            // Найти директорию с исходниками
            val sourceDir = findSourceDirectory()
            if (sourceDir == null) {
                logger.warn("Source directory not found")
                return null
            }

            logger.info("Building MCP Server from source (this may take a few minutes)...")

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
                    return targetFile
                }
            }

            null
        } catch (e: Exception) {
            logger.error("Failed to build from source", e)
            null
        }
    }

    /**
     * Запустить универсальный MCP сервер
     */
    private fun startServer(serverBinary: File): Boolean {
        return try {
            logger.info("Starting universal MCP Server")

            // Создать универсальную конфигурацию
            val configFile = createUniversalConfig()

            // Запустить процесс с указанием конфигурации
            val processBuilder = ProcessBuilder()
                .command(serverBinary.absolutePath)
                .directory(serverBinary.parentFile)
                .redirectErrorStream(true)
            
            // Передаем путь к конфигурации через переменную окружения
            processBuilder.environment()["MCP_CONFIG_PATH"] = configFile.absolutePath
            
            serverProcess = processBuilder.start()

            // Ждем запуска (максимум 10 секунд)
            var attempts = 0
            while (attempts < 20) {
                Thread.sleep(500)
                if (isServerRunning()) {
                    logger.info("Universal MCP Server started successfully")
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
     * Создать универсальную конфигурацию MCP сервера
     */
    private fun createUniversalConfig(): File {
        val serverDir = getServerDirectory()
        val configFile = File(serverDir, "mcp-config.toml")

        val config = """
            # MCP Server Configuration - Universal File System Access
            # Plugin controls project paths, server provides file system access
            base_dir = "/"
            max_file_size = 10485760
            
            # Allowed file extensions for development
            allowed_extensions = [
                "txt", "md", "json", "yaml", "yml", "toml", "xml",
                "kt", "java", "js", "ts", "py", "rs", "go", "cpp", "c", "h",
                "html", "css", "scss", "sass", "vue", "jsx", "tsx",
                "sql", "sh", "bat", "ps1", "dockerfile", "gitignore",
                "properties", "gradle", "maven", "sbt", "lock"
            ]
            
            # Blocked paths for security (system directories)
            blocked_paths = [
                "/etc", "/sys", "/proc", "/root", "/boot", "/dev",
                "/var/log", "/usr/bin", "/usr/sbin", "/sbin",
                "C:\\Windows", "C:\\System32", "C:\\Program Files"
            ]
            verbose = true
        """.trimIndent()

        configFile.writeText(config)
        logger.info("Created universal MCP config: ${configFile.absolutePath}")
        
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
`
    private fun getServerDirectory(): File {
        val pluginDir = File(System.getProperty("user.home"), ".ride")
        val serverDir = File(pluginDir, "mcp-server")
        serverDir.mkdirs()
        return serverDir
    }

    /**
     * Обеспечить запуск универсального MCP сервера
     * Проектный путь теперь передается через MCPClient, а не через конфигурацию сервера
     */
    fun ensureProjectServer(projectPath: String?): Boolean {
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
        // Попробовать найти исходники относительно плагина
        val possiblePaths = listOf(
            File("../mcp-server-rust"),
            File("../../mcp-server-rust"),
            File(System.getProperty("user.home"), ".ride/mcp-server-rust")
        )

        return possiblePaths.firstOrNull { it.exists() && File(it, "Cargo.toml").exists() }
    }
}
