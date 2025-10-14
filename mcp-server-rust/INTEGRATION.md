# Интеграция с Ride IDE Plugin

Руководство по интеграции MCP Server Rust с плагином Ride для IntelliJ IDEA.

## 📚 Документация

- **Автоматическая установка**: [docs/AUTO_INSTALL.md](docs/AUTO_INSTALL.md) - Как автоматически устанавливать и запускать сервер при установке плагина
- **API документация**: [docs/API.md](docs/API.md)
- **Безопасность**: [docs/SECURITY.md](docs/SECURITY.md)

## Обзор

MCP Server Rust предоставляет файловую систему как сервис для плагина Ride через MCP (Model Context Protocol).

## Архитектура интеграции

```
┌─────────────────────────────────────┐
│     IntelliJ IDEA Plugin (Ride)     │
│  - Kotlin/JVM                       │
│  - MCP Client                       │
└─────────────────────────────────────┘
              ↓ HTTP/STDIO
┌─────────────────────────────────────┐
│     MCP Server Rust                 │
│  - Rust/Tokio                       │
│  - File System Operations           │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│     File System                     │
└─────────────────────────────────────┘
```

## Конфигурация в Ride

### 1. Создание конфигурации MCP

Создайте файл `.ride/mcp.json` в корне проекта:

```json
{
  "servers": [
    {
      "name": "filesystem-rust",
      "type": "HTTP",
      "url": "http://localhost:3000",
      "enabled": true,
      "description": "High-performance file system operations"
    }
  ]
}
```

### 2. Альтернативно: STDIO режим

Для STDIO режима (если будет реализован):

```json
{
  "servers": [
    {
      "name": "filesystem-rust",
      "type": "STDIO",
      "command": "cargo",
      "args": ["run", "--release", "--manifest-path", "mcp-server-rust/Cargo.toml"],
      "enabled": true
    }
  ]
}
```

## API Endpoints для Ride

### Операции с файлами

#### 1. Создание файла

**Kotlin код в Ride:**
```kotlin
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.serialization.json.*

class MCPFileSystemClient(private val baseUrl: String = "http://localhost:3000") {
    private val client = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun createFile(path: String, content: String, overwrite: Boolean = false): FileResponse {
        val requestBody = buildJsonObject {
            put("path", path)
            put("content", content)
            put("overwrite", overwrite)
        }.toString()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/files"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 201) {
            throw MCPException("Failed to create file: ${response.body()}")
        }

        return json.decodeFromString(response.body())
    }

    fun readFile(path: String): FileContentResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/files/$path"))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw MCPException("Failed to read file: ${response.body()}")
        }

        return json.decodeFromString(response.body())
    }

    fun updateFile(path: String, content: String): FileResponse {
        val requestBody = buildJsonObject {
            put("content", content)
        }.toString()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/files/$path"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw MCPException("Failed to update file: ${response.body()}")
        }

        return json.decodeFromString(response.body())
    }

    fun deleteFile(path: String) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/files/$path"))
            .DELETE()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw MCPException("Failed to delete file: ${response.body()}")
        }
    }

    fun listFiles(dir: String? = null): DirectoryListResponse {
        val url = if (dir != null) {
            "$baseUrl/files?dir=${URLEncoder.encode(dir, StandardCharsets.UTF_8)}"
        } else {
            "$baseUrl/files"
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw MCPException("Failed to list files: ${response.body()}")
        }

        return json.decodeFromString(response.body())
    }
}

@Serializable
data class FileResponse(
    val path: String,
    val size: Long,
    val created_at: String,
    val modified_at: String,
    val is_readonly: Boolean,
    val checksum: String
)

@Serializable
data class FileContentResponse(
    val path: String,
    val content: String,
    val size: Long,
    val mime_type: String,
    val checksum: String
)

@Serializable
data class DirectoryListResponse(
    val path: String,
    val files: List<FileInfo>,
    val directories: List<DirectoryInfo>
)

@Serializable
data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val modified_at: String,
    val is_readonly: Boolean
)

@Serializable
data class DirectoryInfo(
    val name: String,
    val path: String,
    val modified_at: String
)

class MCPException(message: String) : Exception(message)
```

## Использование в Ride Agent

### Пример интеграции с ChatAgent

```kotlin
class FileSystemAgent(
    private val mcpClient: MCPFileSystemClient,
    private val llmProvider: LLMProvider
) : Agent {
    
    override suspend fun processRequest(
        userMessage: String,
        context: ChatContext
    ): AgentResponse {
        // Анализ запроса пользователя
        val intent = analyzeIntent(userMessage)
        
        return when (intent) {
            is FileIntent.Create -> {
                val result = mcpClient.createFile(
                    path = intent.path,
                    content = intent.content
                )
                AgentResponse(
                    content = "Файл ${intent.path} создан успешно. Размер: ${result.size} байт",
                    metadata = mapOf("checksum" to result.checksum)
                )
            }
            
            is FileIntent.Read -> {
                val result = mcpClient.readFile(intent.path)
                // Передать содержимое в LLM для анализа
                val analysis = llmProvider.sendRequest(
                    "Проанализируй этот файл:\n${result.content}",
                    LLMParameters()
                )
                AgentResponse(content = analysis.content)
            }
            
            is FileIntent.List -> {
                val result = mcpClient.listFiles(intent.directory)
                val fileList = result.files.joinToString("\n") { 
                    "- ${it.name} (${it.size} bytes)" 
                }
                AgentResponse(content = "Файлы:\n$fileList")
            }
            
            else -> {
                // Обычная обработка через LLM
                val response = llmProvider.sendRequest(userMessage, LLMParameters())
                AgentResponse(content = response.content)
            }
        }
    }
    
    private fun analyzeIntent(message: String): FileIntent {
        // Простой анализ намерений
        return when {
            message.contains("создай файл", ignoreCase = true) -> 
                FileIntent.Create(extractPath(message), extractContent(message))
            message.contains("прочитай файл", ignoreCase = true) -> 
                FileIntent.Read(extractPath(message))
            message.contains("список файлов", ignoreCase = true) -> 
                FileIntent.List(extractDirectory(message))
            else -> FileIntent.Unknown
        }
    }
}

sealed class FileIntent {
    data class Create(val path: String, val content: String) : FileIntent()
    data class Read(val path: String) : FileIntent()
    data class List(val directory: String?) : FileIntent()
    object Unknown : FileIntent()
}
```

## Настройка в plugin.xml

```xml
<idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
        <!-- MCP Server Configuration -->
        <applicationConfigurable 
            parentId="tools" 
            instance="ru.marslab.ide.ride.settings.MCPConfigurable"
            id="ru.marslab.ide.ride.settings.MCPConfigurable"
            displayName="MCP Servers"/>
        
        <!-- MCP Service -->
        <applicationService 
            serviceImplementation="ru.marslab.ide.ride.mcp.MCPServerManager"/>
    </extensions>
</idea-plugin>
```

## Запуск MCP Server

### Автоматический запуск

Добавьте в `MCPServerManager`:

```kotlin
class MCPServerManager : ApplicationService {
    private var serverProcess: Process? = null
    
    fun startServer() {
        val serverPath = findServerExecutable()
        
        serverProcess = ProcessBuilder()
            .command(serverPath, "--config", "config.toml")
            .directory(File("mcp-server-rust"))
            .redirectErrorStream(true)
            .start()
        
        // Ждем запуска сервера
        Thread.sleep(2000)
        
        // Проверяем health
        if (!checkHealth()) {
            throw Exception("Failed to start MCP server")
        }
    }
    
    fun stopServer() {
        serverProcess?.destroy()
        serverProcess = null
    }
    
    private fun checkHealth(): Boolean {
        return try {
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:3000/health"))
                .GET()
                .build()
            
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
}
```

### Ручной запуск

Пользователь может запустить сервер вручную:

```bash
cd mcp-server-rust
cargo run --release
```

## Обработка ошибок

```kotlin
fun handleMCPError(error: MCPException): String {
    return when {
        error.message?.contains("404") == true -> 
            "Файл не найден"
        error.message?.contains("403") == true -> 
            "Доступ запрещен"
        error.message?.contains("413") == true -> 
            "Файл слишком большой"
        else -> 
            "Ошибка операции с файлом: ${error.message}"
    }
}
```

## Примеры использования

### 1. Создание файла из чата

```
Пользователь: Создай файл test.txt с содержимым "Hello, World!"

Agent:
1. Анализирует запрос
2. Вызывает mcpClient.createFile("test.txt", "Hello, World!")
3. Возвращает: "Файл test.txt создан успешно"
```

### 2. Анализ кода

```
Пользователь: Проанализируй файл Main.kt

Agent:
1. Вызывает mcpClient.readFile("Main.kt")
2. Отправляет содержимое в LLM
3. Возвращает анализ кода
```

### 3. Рефакторинг

```
Пользователь: Оптимизируй код в Utils.kt

Agent:
1. Читает файл через MCP
2. Анализирует через LLM
3. Генерирует улучшенный код
4. Обновляет файл через mcpClient.updateFile()
```

## Конфигурация безопасности

В `config.toml` для Ride:

```toml
base_dir = "./project-workspace"
max_file_size = 5242880  # 5MB
allowed_extensions = [
    "kt", "java", "xml", "gradle", 
    "txt", "md", "json", "yaml"
]
blocked_paths = [
    ".git",
    ".idea",
    "build",
    "target"
]
```

## Мониторинг

Добавьте в Ride UI индикатор статуса MCP сервера:

```kotlin
class MCPStatusWidget : StatusBarWidget {
    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return object : StatusBarWidget.TextPresentation {
            override fun getText(): String {
                return if (mcpServerManager.isRunning()) {
                    "MCP: ✓"
                } else {
                    "MCP: ✗"
                }
            }
            
            override fun getTooltipText(): String {
                return "MCP Server Status"
            }
        }
    }
}
```

## Тестирование интеграции

```kotlin
class MCPIntegrationTest {
    private lateinit var mcpClient: MCPFileSystemClient
    
    @Before
    fun setup() {
        // Запустить тестовый сервер
        startTestServer()
        mcpClient = MCPFileSystemClient("http://localhost:3000")
    }
    
    @Test
    fun testFileOperations() {
        // Create
        val created = mcpClient.createFile("test.txt", "content")
        assertEquals("test.txt", created.path)
        
        // Read
        val content = mcpClient.readFile("test.txt")
        assertEquals("content", content.content)
        
        // Update
        mcpClient.updateFile("test.txt", "updated")
        
        // Delete
        mcpClient.deleteFile("test.txt")
    }
    
    @After
    fun teardown() {
        stopTestServer()
    }
}
```

## Deployment

### Development

```bash
# Запустить MCP сервер
cd mcp-server-rust
cargo run

# Запустить Ride plugin
./gradlew runIde
```

### Production

```bash
# Собрать MCP сервер
cd mcp-server-rust
cargo build --release

# Скопировать бинарник в plugin
cp target/release/mcp-server-rust ../ride/resources/bin/

# Собрать plugin
cd ../ride
./gradlew buildPlugin
```

## Troubleshooting

### MCP сервер не запускается

1. Проверьте порт: `netstat -ano | findstr :3000`
2. Проверьте логи: `RUST_LOG=debug cargo run`
3. Проверьте конфигурацию: `config.toml`

### Ошибки подключения

1. Проверьте health endpoint: `curl http://localhost:3000/health`
2. Проверьте firewall
3. Проверьте CORS настройки

### Проблемы с производительностью

1. Увеличьте `max_file_size` если нужно
2. Используйте release build: `cargo build --release`
3. Настройте кеширование в Ride

## Дальнейшее развитие

- [ ] WebSocket поддержка для real-time обновлений
- [ ] Streaming для больших файлов
- [ ] Кеширование на стороне клиента
- [ ] Batch операции
- [ ] Транзакции для атомарных операций
- [ ] Версионирование файлов
- [ ] Поиск по содержимому
