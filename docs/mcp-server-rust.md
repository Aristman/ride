# MCP Server Rust

Высокопроизводительный MCP сервер для работы с файловой системой, написанный на Rust.

## Расположение

Проект находится в директории `mcp-server-rust/` в корне репозитория.

## Быстрый старт

```bash
cd mcp-server-rust
cargo run --release
```

Сервер запустится на `http://localhost:3000`

## Документация

- **README**: [mcp-server-rust/README.md](../mcp-server-rust/README.md)
- **Quick Start**: [mcp-server-rust/QUICKSTART.md](../mcp-server-rust/QUICKSTART.md)
- **API Documentation**: [mcp-server-rust/docs/API.md](../mcp-server-rust/docs/API.md)
- **Security Guide**: [mcp-server-rust/docs/SECURITY.md](../mcp-server-rust/docs/SECURITY.md)
- **Architecture**: [mcp-server-rust/docs/ARCHITECTURE.md](../mcp-server-rust/docs/ARCHITECTURE.md)
- **Examples**: [mcp-server-rust/docs/EXAMPLES.md](../mcp-server-rust/docs/EXAMPLES.md)
- **Integration**: [mcp-server-rust/INTEGRATION.md](../mcp-server-rust/INTEGRATION.md)

## Возможности

- ✅ CRUD операции с файлами и директориями
- ⚡ Асинхронная обработка на базе Tokio
- 🔒 Встроенная безопасность с валидацией путей
- 🛡️ Защита от path traversal атак
- 📊 Валидация данных
- 🔍 Checksum проверка файлов (SHA256)
- 📝 Подробное логирование
- 🧪 Полное покрытие тестами

## Интеграция с Ride

Для интеграции с плагином Ride см. [INTEGRATION.md](../mcp-server-rust/INTEGRATION.md)

## Технологии

- **Rust** 1.75+
- **Tokio** - async runtime
- **Axum** - web framework
- **Serde** - serialization
- **Validator** - validation
- **Tracing** - logging

## Разработка

```bash
# Тесты
cd mcp-server-rust
cargo test

# Форматирование
cargo fmt

# Линтинг
cargo clippy -- -D warnings

# Запуск с логами
RUST_LOG=debug cargo run
```

## Docker

```bash
cd mcp-server-rust
docker-compose up -d
```

## Лицензия

MIT License
