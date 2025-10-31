# Интеграция MCP сервера файловой системы в плагин Ride

## Обзор

В плагин Ride интегрирован новый MCP (Model Context Protocol) сервер на Python для безопасного доступа к локальной файловой системе. Сервер обеспечивает полный CRUD-доступ к файлам и директориям с многоуровневой защитой.

## Архитектура

### Компоненты

1. **Python MCP Server** (`mcp-servers/filesystem-server/`)
   - FastAPI сервер с асинхронной обработкой
   - Многоуровневая безопасность (path traversal защита, контроль расширений)
   - RESTful API с автоматической документацией
   - Поддержка отслеживания изменений файлов (опционально)

2. **MCPServerManager** (`src/main/kotlin/ru/marslab/ide/ride/mcp/MCPServerManager.kt`)
   - Автоматическая установка, запуск и управление жизненным циклом сервера
   - Поддержка как Python, так и Rust MCP серверов
   - Интеграция с системой конфигурации плагина

3. **MCPFileSystemAgent** (`src/main/kotlin/ru/marslab/ide/ride/agent/impl/MCPFileSystemAgent.kt`)
   - Специализированный агент для файловых операций
   - Интеграция с Yandex GPT Tools API
   - Автоматическое форматирование результатов

### Поток данных

```
Пользователь → ChatService → MCPFileSystemAgent → MCPServerManager → Python MCP Server → Файловая система
```

## Возможности

### Файловые операции

- ✅ **Создание файлов**: `POST /files`
- ✅ **Чтение файлов**: `GET /files/{path}`
- ✅ **Обновление файлов**: `PUT /files/{path}`
- ✅ **Удаление файлов**: `DELETE /files/{path}`
- ✅ **Информация о файле**: `GET /files/{path}/info`

### Операции с директориями

- ✅ **Создание директорий**: `POST /directories`
- ✅ **Удаление директорий**: `DELETE /directories/{path}`
- ✅ **Список файлов**: `GET /files?dir=path`
- ✅ **Рекурсивные операции**

### Пакетные операции

- ✅ **Массовые операции**: `POST /batch`
- ✅ **Обработка ошибок**: `continue_on_error`
- ✅ **Детальные результаты**: статистика успехов/неудач

### Безопасность

- 🔒 **Path Traversal защита**: блокировка `../` и абсолютных путей
- 🔒 **Базовая директория**: все операции ограничены `base_dir`
- 🔒 **Контроль расширений**: белый список разрешенных типов файлов
- 🔒 **Черный список путей**: блокировка системных директорий
- 🔒 **Лимиты размеров**: защита от DoS атак
- 🔒 **SHA256 хеши**: контроль целостности файлов

### Дополнительные возможности

- 📊 **Отслеживание изменений**: Server-Sent Events поток
- 📚 **Автодокументация**: Swagger UI и ReDoc
- 🔄 **CORS поддержка**: настраиваемые origins
- 📝 **Подробное логирование**: отладочная информация
- 🧪 **Полное тестирование**: unit и integration тесты

## Конфигурация

### Автоматическая конфигурация

Плагин автоматически создает конфигурацию при запуске:

```toml
[server]
host = "127.0.0.1"
port = 3001
log_level = "info"
base_dir = "/path/to/project"  # Автоматически определяется
max_file_size = 10485760  # 10MB
allowed_extensions = ["txt", "md", "json", "kt", "java", "py", "js", "xml", "gradle"]
blocked_paths = ["/etc", "/sys", "/proc", "/boot", "/usr/bin", "/bin", "/sbin", "C:\\Windows", "C:\\Program Files"]
enable_file_watch = false
cors_origins = ["http://localhost:63342"]
```

### Ручная настройка

1. Через переменные окружения:
   ```bash
   export RIDE_FS_HOST=127.0.0.1
   export RIDE_FS_PORT=3001
   export RIDE_FS_BASE_DIR=/custom/path
   export RIDE_FS_ALLOWED_EXTENSIONS=txt,md,json
   ```

2. Через файл конфигурации `~/.ride/filesystem-server-config.toml`

## Сборка и развертывание

### Gradle интеграция

```kotlin
// build.gradle.kts
python {
    pip("fastapi>=0.104.0")
    pip("uvicorn[standard]>=0.24.0")
    pip("pydantic>=2.4.0")
    // ... другие зависимости
}

tasks {
    val buildMcpServer by registering {
        doLast {
            project.exec {
                workingDir = File(projectDir, "mcp-servers/filesystem-server")
                commandLine = listOf("pip", "install", "-e", ".")
            }
        }
    }

    named("buildPlugin") {
        dependsOn(buildMcpServer)
    }
}
```

### Команды сборки

```bash
# Собрать MCP сервер
./gradlew buildMcpServer

# Собрать плагин с MCP сервером
./gradlew buildPlugin

# Запустить с отладкой
./gradlew runIde
```

## Использование

### В коде плагина

```kotlin
// Запуск сервера
val serverManager = MCPServerManager.getInstance()
val started = serverManager.ensureServerRunning()

// Работа с файлами через агент
val agent = MCPFileSystemAgent(config)
val response = agent.processRequest("Создай файл src/main/kotlin/Hello.kt")
```

### API запросы

```bash
# Health check
curl http://localhost:3001/health

# Создать файл
curl -X POST http://localhost:3001/files \
  -H "Content-Type: application/json" \
  -d '{"path":"test.txt","content":"Hello","overwrite":false}'

# Прочитать файл
curl http://localhost:3001/files/test.txt

# Список файлов
curl http://localhost:3001/files?dir=src
```

### В чате плагина

Пользователь может использовать естественный язык:

- "Создай файл main.py с кодом print('Hello')"
- "Прочитай содержимое файла README.md"
- "Обнови файл config.json добавив новую секцию"
- "Удали временные файлы в директории temp"

## Безопасность

### Меры защиты

1. **Изоляция**: Все операции ограничены `base_dir`
2. **Валидация путей**: Блокировка traversal атак
3. **Контроль доступа**: Проверка прав чтения/записи
4. **Фильтрация**: Белые/черные списки путей и расширений
5. **Лимиты**: Ограничения размеров файлов
6. **Логирование**: Аудит всех операций

### Рекомендации

1. Используйте относительные пути
2. Ограничивайте разрешенные расширения
3. Настраивайте `base_dir` на конкретный проект
4. Включайте логирование для отладки
5. Регулярно проверяйте права доступа

## Тестирование

### Запуск тестов

```bash
# В директории MCP сервера
cd mcp-servers/filesystem-server

# Установить dev зависимости
pip install -e ".[dev]"

# Запустить тесты
pytest

# С покрытием
pytest --cov=filesystem_server

# Конкретный тест
pytest tests/test_api.py::test_create_file
```

### Ручное тестирование

```bash
# Запустить сервер
ride-filesystem-server serve --port 3001 --base-dir ./test-data

# В другом терминале
curl http://localhost:3001/docs  # Swagger UI
curl http://localhost:3001/health
```

## Тестирование производительности

### Нагрузочное тестирование

```python
import asyncio
import httpx
import time

async def benchmark():
    async with httpx.AsyncClient() as client:
        start = time.time()

        tasks = []
        for i in range(100):
            tasks.append(client.post(
                "http://localhost:3001/files",
                json={"path": f"test_{i}.txt", "content": f"Content {i}"}
            ))

        results = await asyncio.gather(*tasks)
        end = time.time()

        success = sum(1 for r in results if r.status_code == 200)
        print(f"Создано файлов: {success}/100 за {end-start:.2f} сек")

asyncio.run(benchmark())
```

## Мониторинг и отладка

### Логи

```bash
# Логи MCP сервера
tail -f ~/.ride/logs/filesystem-server.log

# Логи плагина
tail -f ~/Library/Logs/IdeaIC2024.2/idea.log
```

### Статус

```bash
# Проверить статус сервера
ride-filesystem-server status

# Тест подключения
ride-filesystem-server test-connection

# Валидация конфигурации
ride-filesystem-server validate
```

## Устранение проблем

### Частые проблемы

1. **Сервер не запускается**
   ```bash
   # Проверить Python
   python --version

   # Проверить зависимости
   pip list | grep fastapi

   # Пересобрать
   ./gradlew buildMcpServer
   ```

2. **Ошибки доступа**
   ```bash
   # Проверить права
   ls -la /path/to/base/dir

   # Изменить базовую директорию
   ride-filesystem-server serve --base-dir /tmp/ride-data
   ```

3. **Конфликты портов**
   ```bash
   # Проверить порт
   netstat -an | grep 3001

   # Использовать другой порт
   export RIDE_FS_PORT=3002
   ```

## Будущие улучшения

### Планируемые возможности

1. **WebSockets**: реальное время обновлений
2. **Аутентификация**: JWT токены
3. **Кэширование**: локальное кэширование файлов
4. **Сжатие**: автоматическое сжатие больших файлов
5. **Метрики**: Prometheus integration
6. **Кластеризация**: поддержка нескольких экземпляров

### Производительность

1. **Асинхронная запись**: улучшенная производительность
2. **Пул соединений**: оптимизация HTTP запросов
3. **Потоковая передача**: для больших файлов
4. **Параллелизм**: одновременные операции

## Заключение

MCP сервер файловой системы предоставляет безопасный и мощный интерфейс для файловых операций в IntelliJ IDEA плагине Ride. Он сочетает простоту использования с мощными возможностями настройки и обеспечивает высокий уровень безопасности для файловых операций.

Сервер полностью интегрирован в процесс сборки плагина и автоматически управляется жизненным циклом, что обеспечивает прозрачную работу для конечного пользователя.