# MCP Server Rust

Высокопроизводительный MCP сервер для работы с файловой системой на Rust.

## 🚀 Возможности

- ✅ **CRUD операции** с файлами и директориями
- ⚡ **Асинхронная обработка** на базе Tokio
- 🔒 **Встроенная безопасность** с валидацией путей
- 🛡️ **Защита от path traversal** атак
- 📊 **Валидация данных** с помощью validator
- 🔍 **Checksum проверка** файлов (SHA256)
- 📝 **Подробное логирование** с tracing
- 🧪 **Полное покрытие тестами**

## 📋 Требования

- **Rust** 1.75 или выше
- **Cargo** (устанавливается с Rust)

## 🔧 Установка

1. Клонируйте репозиторий:
```bash
git clone <repository-url>
cd mcp-server-rust
```

2. Соберите проект:
```bash
cargo build --release
```

3. Запустите сервер:
```bash
cargo run --release
```

## ⚙️ Конфигурация

Создайте файл `config.toml` в корне проекта:

```toml
base_dir = "./data"
max_file_size = 10485760  # 10MB
allowed_extensions = ["txt", "md", "json"]
blocked_paths = ["/etc", "/sys", "C:\\Windows"]
verbose = true
```

Или используйте переменную окружения:
```bash
export MCP_CONFIG_PATH=/path/to/config.toml
```

## 📖 API Документация

### Health Check

```http
GET /health
```

**Ответ:**
```json
{
  "status": "healthy",
  "version": "0.1.0",
  "uptime_seconds": 3600
}
```

### Создание файла

```http
POST /files
Content-Type: application/json

{
  "path": "test.txt",
  "content": "Hello, World!",
  "overwrite": false
}
```

**Ответ:**
```json
{
  "path": "test.txt",
  "size": 13,
  "created_at": "...",
  "modified_at": "...",
  "is_readonly": false,
  "checksum": "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f"
}
```

### Чтение файла

```http
GET /files/:path
```

**Ответ:**
```json
{
  "path": "test.txt",
  "content": "Hello, World!",
  "size": 13,
  "mime_type": "text/plain",
  "checksum": "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f"
}
```

### Обновление файла

```http
PUT /files/:path
Content-Type: application/json

{
  "content": "Updated content"
}
```

### Удаление файла

```http
DELETE /files/:path
```

**Ответ:**
```json
{
  "success": true,
  "message": "File 'test.txt' deleted successfully"
}
```

### Список файлов

```http
GET /files?dir=subdirectory
```

**Ответ:**
```json
{
  "path": "subdirectory",
  "files": [
    {
      "name": "test.txt",
      "path": "/path/to/test.txt",
      "size": 13,
      "modified_at": "...",
      "is_readonly": false
    }
  ],
  "directories": [
    {
      "name": "subdir",
      "path": "/path/to/subdir",
      "modified_at": "..."
    }
  ]
}
```

### Создание директории

```http
POST /directories
Content-Type: application/json

{
  "path": "new_directory",
  "recursive": true
}
```

### Удаление директории

```http
DELETE /directories/:path
```

## 🔒 Безопасность

Сервер включает несколько уровней защиты:

1. **Валидация путей** - защита от path traversal атак
2. **Белый список расширений** - ограничение типов файлов
3. **Черный список путей** - блокировка системных директорий
4. **Ограничение размера** - защита от DoS атак
5. **Checksum проверка** - контроль целостности файлов

## 🧪 Тестирование

Запуск всех тестов:
```bash
cargo test
```

Запуск с выводом логов:
```bash
cargo test -- --nocapture
```

Запуск конкретного теста:
```bash
cargo test test_create_and_read_file
```

## 📊 Производительность

- **Асинхронная обработка** - обработка множества запросов параллельно
- **Zero-copy операции** - минимальное копирование данных
- **Оптимизированная сборка** - LTO и strip в release режиме

## 🏗️ Архитектура

```
mcp-server-rust/
├── src/
│   ├── main.rs              # Точка входа
│   ├── config.rs            # Конфигурация
│   ├── error.rs             # Обработка ошибок
│   ├── models.rs            # Модели данных
│   ├── security.rs          # Функции безопасности
│   ├── handlers/            # HTTP обработчики
│   │   ├── files.rs
│   │   ├── directories.rs
│   │   └── health.rs
│   └── services/            # Бизнес-логика
│       └── file_service.rs
├── Cargo.toml
└── README.md
```

## 🛠️ Технологический стек

| Категория | Технология | Версия |
|-----------|-----------|--------|
| **Runtime** | Tokio | 1.35 |
| **Web Framework** | Axum | 0.7 |
| **Serialization** | Serde | 1.0 |
| **Validation** | Validator | 0.18 |
| **Logging** | Tracing | 0.1 |
| **Security** | SHA2 | 0.10 |

## 📝 Примеры использования

### Curl

```bash
# Health check
curl http://localhost:3000/health

# Создать файл
curl -X POST http://localhost:3000/files \
  -H "Content-Type: application/json" \
  -d '{"path":"test.txt","content":"Hello","overwrite":false}'

# Прочитать файл
curl http://localhost:3000/files/test.txt

# Обновить файл
curl -X PUT http://localhost:3000/files/test.txt \
  -H "Content-Type: application/json" \
  -d '{"content":"Updated"}'

# Удалить файл
curl -X DELETE http://localhost:3000/files/test.txt

# Список файлов
curl http://localhost:3000/files
```

### Rust Client

```rust
use reqwest;
use serde_json::json;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let client = reqwest::Client::new();
    
    // Создать файл
    let response = client
        .post("http://localhost:3000/files")
        .json(&json!({
            "path": "test.txt",
            "content": "Hello, World!",
            "overwrite": false
        }))
        .send()
        .await?;
    
    println!("Status: {}", response.status());
    println!("Body: {}", response.text().await?);
    
    Ok(())
}
```

## 🐛 Известные проблемы

Нет известных критических проблем.

## 📄 Лицензия

MIT License

## 🤝 Вклад в проект

Мы приветствуем вклад в проект! Пожалуйста:

1. Форкните репозиторий
2. Создайте ветку для вашей фичи
3. Напишите тесты
4. Отправьте Pull Request

## 📧 Контакты

- GitHub Issues: [создать issue](https://github.com/yourusername/mcp-server-rust/issues)
