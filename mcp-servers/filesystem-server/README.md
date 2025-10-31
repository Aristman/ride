# Ride Filesystem MCP Server

Безопасный MCP сервер на Python для доступа к локальной файловой системе из IntelliJ IDEA плагина Ride.

## 🚀 Возможности

- ✅ **Полная CRUD поддержка** файлов и директорий
- 🔒 **Безопасные операции** с валидацией путей и защитой от traversal атак
- 📁 **Работа с файлами** любого размера (в рамках лимитов)
- 🔄 **Пакетные операции** для множественных файловых операций
- 👀 **Отслеживание изменений** файлов в реальном времени (опционально)
- 🛡️ **Контроль доступа** через белые/черные списки путей и расширений
- 📊 **RESTful API** с автоматической документацией
- 🧪 **Полное покрытие тестами**
- ⚡ **Асинхронная обработка** на базе FastAPI и asyncio

## 📋 Требования

- **Python** 3.8 или выше
- **pip** или **poetry** для управления зависимостями

## 🔧 Установка

### Способ 1: pip (рекомендуется)

```bash
# Клонируем репозиторий
git clone <repository-url>
cd ride/mcp-servers/filesystem-server

# Устанавливаем зависимости
pip install -e .

# Или для разработки с dev зависимостями
pip install -e ".[dev]"
```

### Способ 2: Poetry

```bash
# Клонируем репозиторий
git clone <repository-url>
cd ride/mcp-servers/filesystem-server

# Устанавливаем зависимости
poetry install

# Активируем виртуальное окружение
poetry shell
```

## ⚙️ Конфигурация

### Автоматическая инициализация

```bash
# Создать конфигурацию по умолчанию
ride-filesystem-server init

# Показать текущий статус и конфигурацию
ride-filesystem-server status

# Проверить валидность конфигурации
ride-filesystem-server validate
```

### Ручная конфигурация

Создайте файл `config.toml` или `~/.ride/filesystem-server-config.toml`:

```toml
[server]
host = "127.0.0.1"
port = 3001
log_level = "info"
base_dir = "./data"
max_file_size = 10485760  # 10MB
allowed_extensions = ["txt", "md", "json", "py", "kt", "js"]
blocked_paths = [
    "/etc", "/sys", "/proc", "/boot",
    "C:\\Windows", "C:\\Program Files"
]
enable_file_watch = false
cors_origins = ["http://localhost:63342"]
```

### Переменные окружения

```bash
export RIDE_FS_HOST=127.0.0.1
export RIDE_FS_PORT=3001
export RIDE_FS_BASE_DIR=./data
export RIDE_FS_MAX_FILE_SIZE=10485760
export RIDE_FS_ALLOWED_EXTENSIONS=txt,md,json,py,kt,js
export RIDE_FS_ENABLE_WATCH=true
export RIDE_FS_LOG_LEVEL=info
```

## 🚀 Запуск

### Базовый запуск

```bash
# Запуск с конфигурацией по умолчанию
ride-filesystem-server serve

# С указанием параметров
ride-filesystem-server serve --host 127.0.0.1 --port 3001 --base-dir ./my-data

# С отслеживанием файлов
ride-filesystem-server serve --enable-watch

# С файлом конфигурации
ride-filesystem-server serve --config /path/to/config.toml
```

### Разработка

```bash
# Запуск с auto-reload (требуется uvicorn[standard])
ride-filesystem-server serve --log-level debug

# Или через Python напрямую
python -m filesystem_server.main serve --debug
```

## 📖 API Документация

После запуска сервера доступна документация:
- **Swagger UI**: http://127.0.0.1:3001/docs
- **ReDoc**: http://127.0.0.1:3001/redoc

### Основные эндпоинты

#### Health Check
```http
GET /health
```

#### Работа с файлами
```http
# Создать файл
POST /files
Content-Type: application/json
{
  "path": "example.txt",
  "content": "Hello, World!",
  "overwrite": false
}

# Прочитать файл
GET /files/example.txt

# Обновить файл
PUT /files/example.txt
Content-Type: application/json
{
  "content": "Updated content",
  "create_if_missing": false
}

# Удалить файл
DELETE /files/example.txt

# Получить информацию о файле
GET /files/example.txt/info

# Список файлов в директории
GET /files?dir=subdirectory
```

#### Работа с директориями
```http
# Создать директорию
POST /directories
Content-Type: application/json
{
  "path": "new_folder",
  "recursive": true
}

# Удалить директорию
DELETE /directories/new_folder
```

#### Пакетные операции
```http
POST /batch
Content-Type: application/json
{
  "operations": [
    {
      "type": "create_file",
      "path": "file1.txt",
      "data": {
        "content": "Content 1",
        "overwrite": true
      }
    },
    {
      "type": "create_directory",
      "path": "folder1",
      "data": {
        "recursive": true
      }
    }
  ],
  "continue_on_error": true
}
```

#### Отслеживание файлов
```http
# Server-Sent Events поток изменений
GET /watch/events
Accept: text/event-stream
```

## 🔒 Безопасность

Сервер включает многоуровневую защиту:

### Валидация путей
- **Path Traversal защита**: Запрет `../` и абсолютных путей
- **Базовая директория**: Все операции ограничены `base_dir`
- **Скрытые файлы**: Игнорирование файлов/директорий начинающихся с `.`

### Контроль доступа
- **Белый список расширений**: Только указанные типы файлов
- **Черный список путей**: Блокировка системных директорий
- **Размер файлов**: Лимит на размер загружаемых файлов

### Дополнительные меры
- **CORS**: Настройка разрешенных источников
- **SHA256 хеши**: Контроль целостности файлов
- **Права доступа**: Проверка прав чтения/записи

## 🧪 Тестирование

```bash
# Запустить все тесты
pytest

# С покрытием
pytest --cov=filesystem_server

# Конкретный тест
pytest tests/test_api.py::test_create_file

# С выводом
pytest -v -s
```

## 🔧 Интеграция с IntelliJ IDEA плагином

### Автоматическая интеграция

Плагин Ride автоматически:
1. Находит MCP сервер в `mcp-servers/filesystem-server`
2. Собирает его при сборке плагина
3. Запускает при необходимости
4. Настраивает `base_dir` на корень проекта

### Ручная настройка

```kotlin
// В MCPServerManager.kt
val serverConfig = mapOf(
    "type" to "filesystem",
    "host" to "127.0.0.1",
    "port" to 3001,
    "base_dir" to project.basePath
)
```

## 📝 Примеры использования

### Базовые операции

```python
import httpx

base_url = "http://127.0.0.1:3001"

# Создать файл
response = httpx.post(f"{base_url}/files", json={
    "path": "test.txt",
    "content": "Hello from Python!"
})

# Прочитать файл
response = httpx.get(f"{base_url}/files/test.txt")
file_data = response.json()
print(file_data["content"])

# Обновить файл
response = httpx.put(f"{base_url}/files/test.txt", json={
    "content": "Updated content!"
})
```

### Отслеживание изменений

```python
import httpx
import json

# Подписаться на изменения
with httpx.stream("GET", f"{base_url}/watch/events") as response:
    for line in response.iter_lines():
        if line.startswith(b"data: "):
            event = json.loads(line[6:])  # Убираем "data: "
            print(f"File {event['path']} was {event['event_type']}")
```

### Пакетные операции

```python
# Массовое создание файлов
operations = []
for i in range(5):
    operations.append({
        "type": "create_file",
        "path": f"file_{i}.txt",
        "data": {
            "content": f"Content of file {i}",
            "overwrite": True
        }
    })

response = httpx.post(f"{base_url}/batch", json={
    "operations": operations,
    "continue_on_error": True
})
result = response.json()
print(f"Успешно: {result['successful_operations']}/{result['total_operations']}")
```

## 🛠️ Разработка

### Структура проекта

```
src/filesystem_server/
├── __init__.py          # Метаданные пакета
├── main.py              # CLI интерфейс
├── config.py            # Управление конфигурацией
├── models.py            # Pydantic модели данных
├── security.py          # Безопасность и валидация
├── service.py           # Бизнес-логика файловых операций
└── api.py               # FastAPI эндпоинты
```

### Добавление новых функций

1. **Модели данных**: Добавить в `models.py`
2. **Бизнес-логика**: Реализовать в `service.py`
3. **Безопасность**: Проверить в `security.py`
4. **API**: Добавить эндпоинт в `api.py`
5. **Тесты**: Написать в `tests/`

## 🐛 Устранение проблем

### Сервер не запускается
```bash
# Проверить конфигурацию
ride-filesystem-server validate

# Проверить доступность порта
netstat -an | grep 3001

# Запустить с отладкой
ride-filesystem-server serve --log-level debug
```

### Ошибки доступа к файлам
```bash
# Проверить права доступа
ls -la /path/to/base/dir

# Изменить базовую директорию
ride-filesystem-server serve --base-dir /tmp/ride-data
```

### Проблемы с зависимостями
```bash
# Переустановить зависимости
pip install --upgrade -e .

# Или через poetry
poetry install --no-dev
```

## 📄 Лицензия

MIT License

## 🤝 Вклад в проект

1. Форкните репозиторий
2. Создайте feature ветку
3. Напишите тесты
4. Убедитесь что все тесты проходят
5. Отправьте Pull Request

## 📞 Поддержка

- **Issues**: [GitHub Issues](https://github.com/your-repo/issues)
- **Документация**: [Wiki](https://github.com/your-repo/wiki)
- **Discord**: [Сервер сообщества](https://discord.gg/your-server)