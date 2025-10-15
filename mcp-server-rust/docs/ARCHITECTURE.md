# Architecture

Архитектура MCP Server Rust.

## Обзор

MCP Server Rust построен на модульной архитектуре с четким разделением ответственности и использованием современных паттернов проектирования.

## Слои архитектуры

```
┌─────────────────────────────────────────────────┐
│              HTTP Layer (Axum)                  │
│  - Routing                                      │
│  - Middleware (CORS, Tracing)                   │
│  - Request/Response handling                    │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│              Handler Layer                      │
│  - files.rs (file operations)                   │
│  - directories.rs (directory operations)        │
│  - health.rs (health checks)                    │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│              Service Layer                      │
│  - FileService (business logic)                 │
│  - Validation                                   │
│  - Security checks                              │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│              Security Layer                     │
│  - Path sanitization                            │
│  - Filename validation                          │
│  - Checksum calculation                         │
└─────────────────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────┐
│              File System (Tokio FS)             │
│  - Async file operations                        │
│  - Directory operations                         │
└─────────────────────────────────────────────────┘
```

## Компоненты

### 1. Main (main.rs)

**Ответственность:**
- Инициализация приложения
- Настройка логирования
- Создание роутера
- Запуск HTTP сервера

**Ключевые функции:**
```rust
#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize tracing
    tracing_subscriber::registry()...
    
    // Load configuration
    let config = config::Config::load()?;
    
    // Build router
    let app = Router::new()
        .route("/health", get(handlers::health::health_check))
        .route("/files", post(handlers::files::create_file))
        // ...
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http())
        .with_state(config);
    
    // Start server
    axum::serve(listener, app).await?;
}
```

### 2. Configuration (config.rs)

**Ответственность:**
- Загрузка конфигурации
- Валидация настроек
- Проверка безопасности путей

**Структура:**
```rust
pub struct Config {
    pub base_dir: PathBuf,
    pub max_file_size: usize,
    pub allowed_extensions: Vec<String>,
    pub blocked_paths: Vec<String>,
    pub verbose: bool,
}
```

**Методы:**
- `load()` - загрузка конфигурации
- `is_path_allowed()` - проверка пути
- `is_extension_allowed()` - проверка расширения

### 3. Error Handling (error.rs)

**Ответственность:**
- Определение типов ошибок
- Преобразование ошибок
- Формирование HTTP ответов

**Типы ошибок:**
```rust
pub enum AppError {
    NotFound(String),
    InvalidInput(String),
    PermissionDenied(String),
    FileTooLarge(usize, usize),
    IoError(std::io::Error),
    ValidationError(String),
    InternalError(String),
}
```

**Преобразование в HTTP:**
```rust
impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error_type, message, details) = match self {
            AppError::NotFound(msg) => (StatusCode::NOT_FOUND, ...),
            // ...
        };
        
        (status, Json(ErrorResponse { ... })).into_response()
    }
}
```

### 4. Models (models.rs)

**Ответственность:**
- Определение структур данных
- Сериализация/десериализация
- Валидация входных данных

**Основные модели:**
- `CreateFileRequest` - запрос на создание файла
- `UpdateFileRequest` - запрос на обновление файла
- `FileResponse` - ответ с информацией о файле
- `FileContentResponse` - ответ с содержимым файла
- `DirectoryListResponse` - список файлов и директорий

**Валидация:**
```rust
#[derive(Debug, Serialize, Deserialize, Validate)]
pub struct CreateFileRequest {
    #[validate(length(min = 1, max = 255))]
    pub path: String,
    pub content: String,
    #[serde(default)]
    pub overwrite: bool,
}
```

### 5. Security (security.rs)

**Ответственность:**
- Санитизация путей
- Валидация имен файлов
- Вычисление контрольных сумм

**Ключевые функции:**

```rust
// Защита от path traversal
pub fn sanitize_path(path: &str) -> Result<PathBuf, String> {
    // Проверка на ../ и абсолютные пути
}

// Валидация имени файла
pub fn validate_filename(filename: &str) -> Result<(), String> {
    // Проверка недопустимых символов
    // Проверка зарезервированных имен
}

// Вычисление SHA256
pub fn calculate_checksum(content: &[u8]) -> String {
    // SHA256 hash
}
```

### 6. Handlers

**Ответственность:**
- Обработка HTTP запросов
- Извлечение параметров
- Вызов сервисов
- Формирование ответов

**Структура:**
```rust
pub async fn create_file(
    State(config): State<Config>,
    Json(request): Json<CreateFileRequest>,
) -> Result<(StatusCode, Json<FileResponse>)> {
    request.validate().map_err(AppError::from)?;
    let response = FileService::create_file(&config, request).await?;
    Ok((StatusCode::CREATED, Json(response)))
}
```

### 7. Services (file_service.rs)

**Ответственность:**
- Бизнес-логика
- Работа с файловой системой
- Проверки безопасности
- Обработка ошибок

**Основные методы:**
```rust
impl FileService {
    pub async fn create_file(...) -> Result<FileResponse>
    pub async fn read_file(...) -> Result<FileContentResponse>
    pub async fn update_file(...) -> Result<FileResponse>
    pub async fn delete_file(...) -> Result<DeleteResponse>
    pub async fn list_files(...) -> Result<DirectoryListResponse>
    pub async fn create_directory(...) -> Result<DirectoryResponse>
    pub async fn delete_directory(...) -> Result<DeleteResponse>
}
```

## Поток данных

### Создание файла

```
1. HTTP POST /files
   ↓
2. Handler: files::create_file
   - Извлечение State и Json
   - Валидация request
   ↓
3. Service: FileService::create_file
   - Санитизация пути (security::sanitize_path)
   - Проверка разрешений (config.is_path_allowed)
   - Проверка расширения (config.is_extension_allowed)
   - Проверка размера
   - Создание родительских директорий
   - Запись файла (tokio::fs)
   - Вычисление checksum
   ↓
4. Response: FileResponse
   - Сериализация в JSON
   - Возврат 201 Created
```

### Обработка ошибок

```
1. Ошибка в любом слое
   ↓
2. Преобразование в AppError
   - From<std::io::Error>
   - From<ValidationErrors>
   ↓
3. IntoResponse для AppError
   - Определение HTTP статуса
   - Формирование ErrorResponse
   ↓
4. JSON ответ с ошибкой
```

## Паттерны проектирования

### 1. Repository Pattern

`FileService` действует как репозиторий для файловых операций:
- Инкапсуляция логики доступа к данным
- Абстракция от деталей реализации
- Легкое тестирование через моки

### 2. Error Handling Pattern

Централизованная обработка ошибок:
- Единый тип `AppError`
- Автоматическое преобразование
- Консистентные HTTP ответы

### 3. Validation Pattern

Декларативная валидация:
- Атрибуты `#[validate(...)]`
- Автоматическая проверка
- Понятные сообщения об ошибках

### 4. State Pattern

Передача конфигурации через State:
- Immutable shared state
- Безопасный доступ из handlers
- Легкое тестирование

## Асинхронность

### Tokio Runtime

```rust
#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Tokio runtime автоматически создается
}
```

### Async/Await

Все I/O операции асинхронные:
```rust
pub async fn create_file(...) -> Result<FileResponse> {
    // Async file operations
    let mut file = fs::File::create(&full_path).await?;
    file.write_all(content_bytes).await?;
    file.sync_all().await?;
}
```

### Преимущества

- **Высокая производительность** - обработка множества запросов параллельно
- **Эффективное использование ресурсов** - минимальное количество потоков
- **Масштабируемость** - легко обрабатывать тысячи соединений

## Безопасность

### Многоуровневая защита

1. **Валидация входных данных** (models.rs)
   - Длина строк
   - Формат данных

2. **Санитизация путей** (security.rs)
   - Защита от path traversal
   - Блокировка абсолютных путей

3. **Проверка разрешений** (config.rs)
   - Whitelist расширений
   - Blacklist путей
   - Ограничение base_dir

4. **Ограничение размера** (file_service.rs)
   - Проверка перед записью
   - Защита от DoS

5. **Checksum** (security.rs)
   - Контроль целостности
   - SHA256 хеш

## Тестирование

### Unit Tests

Каждый модуль содержит тесты:
```rust
#[cfg(test)]
mod tests {
    use super::*;
    
    #[tokio::test]
    async fn test_create_file() {
        // Arrange
        let config = create_test_config();
        
        // Act
        let result = FileService::create_file(&config, request).await;
        
        // Assert
        assert!(result.is_ok());
    }
}
```

### Integration Tests

Тесты полного цикла в `tests/`:
```rust
#[tokio::test]
async fn test_full_file_lifecycle() {
    // Create → Read → Update → Delete
}
```

## Производительность

### Оптимизации

1. **Zero-copy где возможно**
   - Использование `&[u8]` вместо `Vec<u8>`
   - Минимальное клонирование

2. **Async I/O**
   - Неблокирующие операции
   - Параллельная обработка

3. **Release оптимизации**
   ```toml
   [profile.release]
   opt-level = 3
   lto = true
   codegen-units = 1
   strip = true
   ```

4. **Эффективная сериализация**
   - Serde с оптимизациями
   - Минимальное копирование

## Расширяемость

### Добавление новых endpoints

1. Создать handler в `handlers/`
2. Добавить route в `main.rs`
3. Реализовать логику в `services/`
4. Добавить тесты

### Добавление middleware

```rust
let app = Router::new()
    .route(...)
    .layer(middleware::from_fn(auth_middleware))
    .layer(middleware::from_fn(rate_limit_middleware));
```

### Добавление новых типов хранилищ

Создать trait и реализации:
```rust
#[async_trait]
pub trait StorageBackend {
    async fn create_file(...) -> Result<...>;
    async fn read_file(...) -> Result<...>;
    // ...
}

pub struct FileSystemBackend;
pub struct S3Backend;
```

## Мониторинг

### Логирование

Использование `tracing`:
```rust
tracing::info!("File created: {}", path);
tracing::error!("Failed to create file: {}", error);
```

### Метрики

Можно добавить Prometheus:
```rust
use prometheus::{Counter, Histogram};

lazy_static! {
    static ref FILE_OPERATIONS: Counter = ...;
    static ref OPERATION_DURATION: Histogram = ...;
}
```

## Deployment

### Production Checklist

- [ ] Настроить `base_dir` на выделенную директорию
- [ ] Ограничить `max_file_size`
- [ ] Настроить `allowed_extensions`
- [ ] Добавить `blocked_paths`
- [ ] Настроить логирование
- [ ] Добавить аутентификацию
- [ ] Настроить HTTPS
- [ ] Добавить rate limiting
- [ ] Настроить мониторинг
- [ ] Настроить бэкапы

## Ресурсы

- [Axum Documentation](https://docs.rs/axum/)
- [Tokio Documentation](https://docs.rs/tokio/)
- [Serde Documentation](https://serde.rs/)
- [Rust Async Book](https://rust-lang.github.io/async-book/)
