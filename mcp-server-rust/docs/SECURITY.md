# Security Guide

## Overview

MCP Server Rust включает несколько уровней защиты для безопасной работы с файловой системой.

## Механизмы защиты

### 1. Path Traversal Protection

Защита от атак с использованием `../` для доступа к файлам вне разрешенной директории.

**Реализация:**
```rust
pub fn sanitize_path(path: &str) -> Result<PathBuf, String> {
    let path = PathBuf::from(path);
    
    for component in path.components() {
        match component {
            std::path::Component::ParentDir => {
                return Err("Path traversal detected".to_string());
            }
            std::path::Component::RootDir => {
                return Err("Absolute paths not allowed".to_string());
            }
            _ => {}
        }
    }
    
    Ok(path)
}
```

**Примеры заблокированных путей:**
- `../etc/passwd`
- `../../secret.txt`
- `/etc/shadow`
- `C:\Windows\System32\config\SAM`

### 2. Base Directory Restriction

Все операции ограничены базовой директорией, указанной в конфигурации.

**Конфигурация:**
```toml
base_dir = "./data"
```

**Проверка:**
```rust
pub fn is_path_allowed(&self, path: &Path) -> bool {
    if let Ok(canonical) = path.canonicalize() {
        if let Ok(base_canonical) = self.base_dir.canonicalize() {
            return canonical.starts_with(base_canonical);
        }
    }
    false
}
```

### 3. Blocked Paths

Черный список системных директорий, доступ к которым запрещен.

**По умолчанию заблокированы:**
- `/etc` - системные конфигурации (Linux)
- `/sys` - системная информация (Linux)
- `/proc` - процессы (Linux)
- `C:\Windows` - системные файлы (Windows)
- `C:\System32` - системные библиотеки (Windows)

**Настройка:**
```toml
blocked_paths = [
    "/etc",
    "/sys",
    "/proc",
    "C:\\Windows",
    "C:\\System32"
]
```

### 4. File Extension Whitelist

Ограничение разрешенных типов файлов.

**Конфигурация:**
```toml
# Пустой список = все расширения разрешены
allowed_extensions = []

# Или ограничить конкретными расширениями
allowed_extensions = ["txt", "md", "json", "rs"]
```

**Проверка:**
```rust
pub fn is_extension_allowed(&self, path: &Path) -> bool {
    if self.allowed_extensions.is_empty() {
        return true;
    }
    
    if let Some(ext) = path.extension() {
        let ext_str = ext.to_string_lossy().to_lowercase();
        self.allowed_extensions.iter()
            .any(|allowed| allowed.to_lowercase() == ext_str)
    } else {
        false
    }
}
```

### 5. File Size Limit

Защита от DoS атак через загрузку больших файлов.

**Конфигурация:**
```toml
max_file_size = 10485760  # 10MB
```

**Проверка:**
```rust
if content_bytes.len() > config.max_file_size {
    return Err(AppError::FileTooLarge(
        content_bytes.len(),
        config.max_file_size,
    ));
}
```

### 6. Filename Validation

Проверка имен файлов на недопустимые символы и зарезервированные имена.

**Недопустимые символы:**
- `<`, `>`, `:`, `"`, `|`, `?`, `*`, `\0`

**Зарезервированные имена (Windows):**
- `CON`, `PRN`, `AUX`, `NUL`
- `COM1-9`, `LPT1-9`

**Реализация:**
```rust
pub fn validate_filename(filename: &str) -> Result<(), String> {
    if filename.is_empty() {
        return Err("Filename cannot be empty".to_string());
    }
    
    let invalid_chars = ['<', '>', ':', '"', '|', '?', '*', '\0'];
    if filename.chars().any(|c| invalid_chars.contains(&c)) {
        return Err("Invalid characters".to_string());
    }
    
    // Check reserved names...
    Ok(())
}
```

### 7. Checksum Verification

SHA256 хеш для проверки целостности файлов.

**Использование:**
```rust
pub fn calculate_checksum(content: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(content);
    hex::encode(hasher.finalize())
}
```

**Ответ API включает checksum:**
```json
{
  "path": "file.txt",
  "checksum": "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e"
}
```

## Рекомендации по безопасности

### 1. Конфигурация в продакшене

```toml
# Используйте выделенную директорию
base_dir = "/var/mcp-server/data"

# Ограничьте размер файлов
max_file_size = 5242880  # 5MB

# Разрешите только безопасные расширения
allowed_extensions = ["txt", "md", "json", "csv"]

# Заблокируйте системные директории
blocked_paths = [
    "/etc", "/sys", "/proc", "/root",
    "/var/log", "/var/run",
    "C:\\Windows", "C:\\System32"
]
```

### 2. Сетевая безопасность

**Используйте HTTPS:**
```bash
# Через reverse proxy (nginx, traefik)
# Не выставляйте сервер напрямую в интернет
```

**Настройте firewall:**
```bash
# Разрешите доступ только с определенных IP
sudo ufw allow from 192.168.1.0/24 to any port 3000
```

### 3. Аутентификация

Добавьте middleware для аутентификации:

```rust
use axum::middleware;

async fn auth_middleware(
    headers: HeaderMap,
    request: Request<Body>,
    next: Next<Body>,
) -> Result<Response, StatusCode> {
    let api_key = headers.get("X-API-Key")
        .and_then(|v| v.to_str().ok());
    
    if api_key != Some("your-secret-key") {
        return Err(StatusCode::UNAUTHORIZED);
    }
    
    Ok(next.run(request).await)
}

// Добавьте в роутер
.layer(middleware::from_fn(auth_middleware))
```

### 4. Rate Limiting

Ограничьте количество запросов:

```rust
use tower_governor::{governor::GovernorConfigBuilder, GovernorLayer};

let governor_conf = Box::new(
    GovernorConfigBuilder::default()
        .per_second(10)
        .burst_size(20)
        .finish()
        .unwrap(),
);

.layer(GovernorLayer { config: Box::leak(governor_conf) })
```

### 5. Логирование

Включите детальное логирование:

```bash
RUST_LOG=debug cargo run
```

**Логируйте важные события:**
- Попытки доступа к заблокированным путям
- Превышение лимита размера файла
- Ошибки валидации
- Неудачные операции

### 6. Мониторинг

Настройте мониторинг:
- Количество запросов
- Размер обрабатываемых файлов
- Ошибки и исключения
- Использование ресурсов

### 7. Регулярные обновления

```bash
# Обновляйте зависимости
cargo update

# Проверяйте уязвимости
cargo audit
```

## Тестирование безопасности

### 1. Path Traversal Tests

```rust
#[tokio::test]
async fn test_path_traversal_blocked() {
    let result = sanitize_path("../etc/passwd");
    assert!(result.is_err());
}
```

### 2. File Size Tests

```rust
#[tokio::test]
async fn test_file_too_large() {
    let config = Config {
        max_file_size: 100,
        ..Default::default()
    };
    
    let large_content = "x".repeat(200);
    let request = CreateFileRequest {
        path: "large.txt".to_string(),
        content: large_content,
        overwrite: false,
    };
    
    let result = FileService::create_file(&config, request).await;
    assert!(matches!(result, Err(AppError::FileTooLarge(_, _))));
}
```

### 3. Extension Tests

```rust
#[tokio::test]
async fn test_blocked_extension() {
    let mut config = Config::default();
    config.allowed_extensions = vec!["txt".to_string()];
    
    assert!(config.is_extension_allowed(&PathBuf::from("test.txt")));
    assert!(!config.is_extension_allowed(&PathBuf::from("test.exe")));
}
```

## Отчеты об уязвимостях

Если вы обнаружили уязвимость:

1. **НЕ создавайте публичный issue**
2. Отправьте email на: security@example.com
3. Опишите проблему и шаги для воспроизведения
4. Дождитесь ответа (обычно 48 часов)

## Чеклист безопасности

- [ ] Настроена base_dir
- [ ] Установлен max_file_size
- [ ] Настроен allowed_extensions (если нужно)
- [ ] Добавлены blocked_paths
- [ ] Включено логирование
- [ ] Настроен HTTPS
- [ ] Добавлена аутентификация
- [ ] Настроен rate limiting
- [ ] Регулярные обновления зависимостей
- [ ] Мониторинг и алерты

## Ресурсы

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Rust Security Guidelines](https://anssi-fr.github.io/rust-guide/)
- [CWE-22: Path Traversal](https://cwe.mitre.org/data/definitions/22.html)
