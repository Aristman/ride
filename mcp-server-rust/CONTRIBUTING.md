# Contributing to MCP Server Rust

Спасибо за интерес к проекту! Мы приветствуем любой вклад.

## Как внести вклад

### 1. Форк и клонирование

```bash
git clone https://github.com/yourusername/mcp-server-rust.git
cd mcp-server-rust
```

### 2. Создание ветки

```bash
git checkout -b feature/your-feature-name
```

### 3. Разработка

- Следуйте стилю кода проекта
- Добавьте тесты для новой функциональности
- Обновите документацию при необходимости

### 4. Тестирование

```bash
# Запустите все тесты
cargo test

# Проверьте форматирование
cargo fmt --check

# Запустите clippy
cargo clippy -- -D warnings
```

### 5. Коммит

Используйте [Conventional Commits](https://www.conventionalcommits.org/):

```bash
git commit -m "feat: add new feature"
git commit -m "fix: resolve bug in file handling"
git commit -m "docs: update API documentation"
```

Типы коммитов:
- `feat`: новая функциональность
- `fix`: исправление бага
- `docs`: изменения в документации
- `test`: добавление или изменение тестов
- `refactor`: рефакторинг кода
- `perf`: улучшение производительности
- `chore`: изменения в сборке или инструментах

### 6. Push и Pull Request

```bash
git push origin feature/your-feature-name
```

Создайте Pull Request на GitHub с описанием изменений.

## Стандарты кода

### Форматирование

Используйте `rustfmt`:

```bash
cargo fmt
```

### Линтинг

Используйте `clippy`:

```bash
cargo clippy -- -D warnings
```

### Документация

Документируйте публичные API:

```rust
/// Creates a new file with the given content.
///
/// # Arguments
///
/// * `config` - Server configuration
/// * `request` - File creation request
///
/// # Returns
///
/// Returns `FileResponse` on success or `AppError` on failure.
///
/// # Examples
///
/// ```
/// let response = FileService::create_file(&config, request).await?;
/// ```
pub async fn create_file(
    config: &Config,
    request: CreateFileRequest,
) -> Result<FileResponse> {
    // ...
}
```

### Тесты

Каждая новая функция должна иметь тесты:

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_your_feature() {
        // Arrange
        let input = "test";
        
        // Act
        let result = your_function(input);
        
        // Assert
        assert_eq!(result, expected);
    }
}
```

## Структура проекта

```
mcp-server-rust/
├── src/
│   ├── main.rs              # Точка входа
│   ├── config.rs            # Конфигурация
│   ├── error.rs             # Обработка ошибок
│   ├── models.rs            # Модели данных
│   ├── security.rs          # Безопасность
│   ├── handlers/            # HTTP обработчики
│   │   ├── mod.rs
│   │   ├── files.rs
│   │   ├── directories.rs
│   │   └── health.rs
│   └── services/            # Бизнес-логика
│       ├── mod.rs
│       └── file_service.rs
├── tests/                   # Интеграционные тесты
├── docs/                    # Документация
├── Cargo.toml
└── README.md
```

## Что можно улучшить

### Приоритетные задачи

- [ ] Добавить аутентификацию (JWT, API keys)
- [ ] Реализовать rate limiting
- [ ] Добавить поддержку WebSocket
- [ ] Реализовать streaming для больших файлов
- [ ] Добавить метрики (Prometheus)
- [ ] Реализовать кеширование

### Дополнительные возможности

- [ ] Поддержка S3-совместимых хранилищ
- [ ] Версионирование файлов
- [ ] Поиск по содержимому
- [ ] Сжатие файлов
- [ ] Шифрование файлов

## Процесс ревью

1. Автоматические проверки должны пройти успешно
2. Код будет проверен мантейнерами
3. Могут быть запрошены изменения
4. После одобрения PR будет смержен

## Сообщения об ошибках

При создании issue укажите:

- **Описание проблемы**: что произошло
- **Ожидаемое поведение**: что должно было произойти
- **Шаги для воспроизведения**: как воспроизвести проблему
- **Окружение**: ОС, версия Rust, версия сервера
- **Логи**: соответствующие логи ошибок

Пример:

```markdown
## Описание
Сервер падает при попытке создать файл с именем содержащим emoji.

## Ожидаемое поведение
Файл должен быть создан или возвращена понятная ошибка.

## Шаги для воспроизведения
1. Отправить POST /files с path: "test-😀.txt"
2. Сервер возвращает 500 ошибку

## Окружение
- OS: Ubuntu 22.04
- Rust: 1.75.0
- Server: 0.1.0

## Логи
```
thread 'main' panicked at 'invalid filename'
```
```

## Вопросы?

Если у вас есть вопросы:
- Создайте [Discussion](https://github.com/yourusername/mcp-server-rust/discussions)
- Напишите на email: dev@example.com

Спасибо за вклад! 🚀
