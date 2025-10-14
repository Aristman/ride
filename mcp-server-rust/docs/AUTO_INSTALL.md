# Автоматическая установка и запуск MCP Server

Руководство по автоматической установке и запуску MCP Server при установке плагина Ride.

## Стратегии установки

### Вариант 1: Встроенный бинарник (Рекомендуется)

Включить предсобранный бинарник в дистрибутив плагина.

#### Преимущества
- ✅ Не требует Rust на машине пользователя
- ✅ Быстрая установка
- ✅ Контролируемая версия

#### Недостатки
- ❌ Увеличивает размер плагина (~5-10 MB)
- ❌ Нужны бинарники для разных платформ

### Вариант 2: Скачивание при первом запуске

Скачать бинарник с GitHub Releases при первом запуске плагина.

#### Преимущества
- ✅ Маленький размер плагина
- ✅ Всегда актуальная версия

#### Недостатки
- ❌ Требует интернет
- ❌ Медленный первый запуск

### Вариант 3: Сборка из исходников

Собрать из исходников при установке (если есть Rust).

#### Преимущества
- ✅ Оптимизация под конкретную систему
- ✅ Маленький размер плагина

#### Недостатки
- ❌ Требует Rust toolchain
- ❌ Долгая установка (3-5 минут)

## Рекомендуемое решение: Гибридный подход

Комбинация вариантов 1 и 2 с fallback на вариант 3.

```
1. Попытка использовать встроенный бинарник
   ↓ (если нет)
2. Попытка скачать с GitHub Releases
   ↓ (если не удалось)
3. Попытка собрать из исходников
   ↓ (если не удалось)
4. Предложить пользователю установить вручную
```

## Реализация в Ride Plugin

### 1. Структура ресурсов

```
ride/
├── src/main/resources/
│   └── mcp-server/
│       ├── mcp-server-windows-x64.exe
│       ├── mcp-server-linux-x64
│       ├── mcp-server-macos-x64
│       └── mcp-server-macos-arm64
└── build.gradle.kts
```

### 2. Gradle конфигурация

```kotlin
// build.gradle.kts

tasks {
    // Задача для сборки MCP сервера
    register("buildMCPServer") {
        group = "build"
        description = "Build MCP Server binaries"
        
        doLast {
            val mcpDir = file("../mcp-server-rust")
            val resourcesDir = file("src/main/resources/mcp-server")
            resourcesDir.mkdirs()
            
            // Сборка для текущей платформы
            exec {
                workingDir = mcpDir
                commandLine("cargo", "build", "--release")
            }
            
            // Копирование бинарника
            val targetDir = mcpDir.resolve("target/release")
            val binaryName = if (System.getProperty("os.name").contains("Windows")) {
                "mcp-server-rust.exe"
            } else {
                "mcp-server-rust"
            }
            
            val platformSuffix = when {
                System.getProperty("os.name").contains("Windows") -> "windows-x64.exe"
                System.getProperty("os.name").contains("Mac") -> {
                    if (System.getProperty("os.arch").contains("aarch64")) {
                        "macos-arm64"
                    } else {
                        "macos-x64"
                    }
                }
                else -> "linux-x64"
            }
            
            copy {
                from(targetDir.resolve(binaryName))
                into(resourcesDir)
                rename { "mcp-server-$platformSuffix" }
            }
        }
    }
    
    // Автоматически собирать перед buildPlugin
    named("buildPlugin") {
        dependsOn("buildMCPServer")
    }
}
```

### 3. MCPServerManager

```kotlin
package ru.marslab.ide.ride.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

@Service
class MCPServerManager {
    private val logger = Logger.getInstance(MCPServerManager::class.java)
    private var serverProcess: Process? = null
    private val serverPort = 3000
    private val serverUrl = "http://localhost:$serverPort"
    
    companion object {
        fun getInstance(): MCPServerManager {
            return ApplicationManager.getApplication().getService(MCPServerManager::class.java)
        }
        
        private const val GITHUB_RELEASE_URL = 
            "https://github.com/yourusername/ride/releases/latest/download"
    }
    
    /**
     * Автоматическая установка и запуск сервера
     */
    fun ensureServerRunning(): Boolean {
        // Проверяем, не запущен ли уже
        if (isServerRunning()) {
            logger.info("MCP Server already running")
            return true
        }
        
        // Получаем или устанавливаем бинарник
        val serverBinary = getOrInstallServerBinary() ?: return false
        
        // Запускаем сервер
        return startServer(serverBinary)
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
            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            
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
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                null, 
                "Downloading MCP Server...", 
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Downloading MCP Server binary..."
                    indicator.isIndeterminate = false
                    
                    val platformSuffix = getPlatformSuffix()
                    val downloadUrl = "$GITHUB_RELEASE_URL/mcp-server-$platformSuffix"
                    
                    val client = HttpClient.newHttpClient()
                    val request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .GET()
                        .build()
                    
                    val response = client.send(
                        request, 
                        HttpResponse.BodyHandlers.ofInputStream()
                    )
                    
                    if (response.statusCode() == 200) {
                        targetFile.parentFile.mkdirs()
                        Files.copy(
                            response.body(), 
                            targetFile.toPath(), 
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        
                        // Установить права на выполнение
                        if (!isWindows()) {
                            val permissions = setOf(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE
                            )
                            Files.setPosixFilePermissions(targetFile.toPath(), permissions)
                        }
                        
                        indicator.fraction = 1.0
                    } else {
                        logger.warn("Failed to download: HTTP ${response.statusCode()}")
                    }
                }
            })
            
            if (targetFile.exists()) targetFile else null
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
            
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                null,
                "Building MCP Server from source...",
                false
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.text = "Building MCP Server (this may take a few minutes)..."
                    indicator.isIndeterminate = true
                    
                    // Найти директорию с исходниками
                    val sourceDir = findSourceDirectory()
                    if (sourceDir == null) {
                        logger.warn("Source directory not found")
                        return
                    }
                    
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
                        }
                    }
                }
            })
            
            if (targetFile.exists()) targetFile else null
        } catch (e: Exception) {
            logger.error("Failed to build from source", e)
            null
        }
    }
    
    /**
     * Запустить сервер
     */
    private fun startServer(serverBinary: File): Boolean {
        return try {
            logger.info("Starting MCP Server: ${serverBinary.absolutePath}")
            
            // Создать конфигурацию если не существует
            val configFile = createDefaultConfig()
            
            // Запустить процесс
            serverProcess = ProcessBuilder()
                .command(serverBinary.absolutePath)
                .directory(serverBinary.parentFile)
                .redirectErrorStream(true)
                .start()
            
            // Ждем запуска (максимум 10 секунд)
            var attempts = 0
            while (attempts < 20) {
                Thread.sleep(500)
                if (isServerRunning()) {
                    logger.info("MCP Server started successfully")
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
                it.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
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
     * Проверить, запущен ли сервер
     */
    fun isServerRunning(): Boolean {
        return try {
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$serverUrl/health"))
                .timeout(java.time.Duration.ofSeconds(2))
                .GET()
                .build()
            
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Создать конфигурацию по умолчанию
     */
    private fun createDefaultConfig(): File {
        val configFile = File(getServerDirectory(), "config.toml")
        
        if (!configFile.exists()) {
            val defaultConfig = """
                base_dir = "./data"
                max_file_size = 10485760
                allowed_extensions = []
                blocked_paths = ["/etc", "/sys", "/proc", "C:\\Windows"]
                verbose = false
            """.trimIndent()
            
            configFile.writeText(defaultConfig)
        }
        
        return configFile
    }
    
    // Вспомогательные методы
    
    private fun getServerDirectory(): File {
        val pluginDir = File(System.getProperty("user.home"), ".ride")
        val serverDir = File(pluginDir, "mcp-server")
        serverDir.mkdirs()
        return serverDir
    }
    
    private fun getServerBinaryName(): String {
        return if (isWindows()) "mcp-server.exe" else "mcp-server"
    }
    
    private fun getPlatformSuffix(): String {
        return when {
            System.getProperty("os.name").contains("Windows", ignoreCase = true) -> 
                "windows-x64.exe"
            System.getProperty("os.name").contains("Mac", ignoreCase = true) -> {
                if (System.getProperty("os.arch").contains("aarch64")) {
                    "macos-arm64"
                } else {
                    "macos-x64"
                }
            }
            else -> "linux-x64"
        }
    }
    
    private fun isWindows(): Boolean {
        return System.getProperty("os.name").contains("Windows", ignoreCase = true)
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
```

### 4. Инициализация при запуске плагина

```kotlin
package ru.marslab.ide.ride

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import ru.marslab.ide.ride.mcp.MCPServerManager

class RideStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        // Запустить MCP сервер в фоне
        ApplicationManager.getApplication().executeOnPooledThread {
            val serverManager = MCPServerManager.getInstance()
            val started = serverManager.ensureServerRunning()
            
            if (!started) {
                // Показать уведомление пользователю
                Notifications.Bus.notify(
                    Notification(
                        "Ride",
                        "MCP Server",
                        "Failed to start MCP Server. Some features may not work.",
                        NotificationType.WARNING
                    )
                )
            }
        }
    }
}
```

### 5. Регистрация в plugin.xml

```xml
<idea-plugin>
    <!-- Startup Activity -->
    <extensions defaultExtensionNs="com.intellij">
        <postStartupActivity implementation="ru.marslab.ide.ride.RideStartupActivity"/>
        
        <applicationService 
            serviceImplementation="ru.marslab.ide.ride.mcp.MCPServerManager"/>
    </extensions>
    
    <!-- Остановка сервера при закрытии -->
    <applicationListeners>
        <listener 
            class="ru.marslab.ide.ride.RideApplicationListener"
            topic="com.intellij.ide.AppLifecycleListener"/>
    </applicationListeners>
</idea-plugin>
```

### 6. Listener для остановки сервера

```kotlin
package ru.marslab.ide.ride

import com.intellij.ide.AppLifecycleListener
import ru.marslab.ide.ride.mcp.MCPServerManager

class RideApplicationListener : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
        // Остановить MCP сервер при закрытии IDE
        MCPServerManager.getInstance().stopServer()
    }
}
```

## Сборка бинарников для всех платформ

### GitHub Actions workflow

```yaml
# .github/workflows/build-mcp-server.yml
name: Build MCP Server

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    strategy:
      matrix:
        include:
          - os: ubuntu-latest
            target: x86_64-unknown-linux-gnu
            artifact: mcp-server-linux-x64
          - os: windows-latest
            target: x86_64-pc-windows-msvc
            artifact: mcp-server-windows-x64.exe
          - os: macos-latest
            target: x86_64-apple-darwin
            artifact: mcp-server-macos-x64
          - os: macos-latest
            target: aarch64-apple-darwin
            artifact: mcp-server-macos-arm64

    runs-on: ${{ matrix.os }}

    steps:
      - uses: actions/checkout@v3
      
      - name: Install Rust
        uses: actions-rs/toolchain@v1
        with:
          toolchain: stable
          target: ${{ matrix.target }}
          override: true
      
      - name: Build
        working-directory: mcp-server-rust
        run: cargo build --release --target ${{ matrix.target }}
      
      - name: Rename binary
        run: |
          if [ "${{ matrix.os }}" = "windows-latest" ]; then
            cp mcp-server-rust/target/${{ matrix.target }}/release/mcp-server-rust.exe ${{ matrix.artifact }}
          else
            cp mcp-server-rust/target/${{ matrix.target }}/release/mcp-server-rust ${{ matrix.artifact }}
          fi
        shell: bash
      
      - name: Upload artifact
        uses: actions/upload-artifact@v3
        with:
          name: ${{ matrix.artifact }}
          path: ${{ matrix.artifact }}
      
      - name: Release
        uses: softprops/action-gh-release@v1
        if: startsWith(github.ref, 'refs/tags/')
        with:
          files: ${{ matrix.artifact }}
```

## UI для управления сервером

### Settings panel

```kotlin
class MCPServerConfigurable : Configurable {
    private lateinit var panel: JPanel
    private lateinit var statusLabel: JLabel
    private lateinit var startButton: JButton
    private lateinit var stopButton: JButton
    private lateinit var autoStartCheckbox: JCheckBox
    
    override fun createComponent(): JComponent {
        panel = JPanel(BorderLayout())
        
        val serverManager = MCPServerManager.getInstance()
        
        // Status panel
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        statusLabel = JLabel()
        updateStatus()
        statusPanel.add(statusLabel)
        
        // Control buttons
        val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        
        startButton = JButton("Start Server").apply {
            addActionListener {
                ApplicationManager.getApplication().executeOnPooledThread {
                    serverManager.ensureServerRunning()
                    SwingUtilities.invokeLater { updateStatus() }
                }
            }
        }
        
        stopButton = JButton("Stop Server").apply {
            addActionListener {
                serverManager.stopServer()
                updateStatus()
            }
        }
        
        controlPanel.add(startButton)
        controlPanel.add(stopButton)
        
        // Auto-start option
        autoStartCheckbox = JCheckBox("Auto-start on IDE startup")
        
        panel.add(statusPanel, BorderLayout.NORTH)
        panel.add(controlPanel, BorderLayout.CENTER)
        panel.add(autoStartCheckbox, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun updateStatus() {
        val isRunning = MCPServerManager.getInstance().isServerRunning()
        statusLabel.text = if (isRunning) {
            "Status: ✓ Running"
        } else {
            "Status: ✗ Stopped"
        }
        startButton.isEnabled = !isRunning
        stopButton.isEnabled = isRunning
    }
    
    override fun isModified(): Boolean = true
    override fun apply() {}
    override fun getDisplayName(): String = "MCP Server"
}
```

## Резюме

Реализация автоматической установки включает:

1. **Встроенные бинарники** в ресурсы плагина
2. **Автоматическое извлечение** при первом запуске
3. **Fallback на скачивание** с GitHub Releases
4. **Fallback на сборку** из исходников (если есть Rust)
5. **Автоматический запуск** при старте IDE
6. **Автоматическая остановка** при закрытии IDE
7. **UI для управления** сервером в настройках
8. **CI/CD pipeline** для сборки бинарников

Пользователь получает полностью автоматическую установку без необходимости ручных действий! 🚀
