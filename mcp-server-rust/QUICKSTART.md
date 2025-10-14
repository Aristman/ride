# Quick Start Guide

Быстрый старт для MCP Server Rust.

## 🚀 За 5 минут

### 1. Установка Rust

Если Rust еще не установлен:

**Linux/macOS:**
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

**Windows:**
Скачайте и запустите [rustup-init.exe](https://rustup.rs/)

### 2. Клонирование и сборка

```bash
# Клонируйте репозиторий
git clone <repository-url>
cd mcp-server-rust

# Запустите скрипт установки
# Linux/macOS:
chmod +x scripts/setup.sh
./scripts/setup.sh

# Windows:
.\scripts\setup.ps1

# Или вручную:
cargo build --release
```

### 3. Конфигурация

Скопируйте пример конфигурации:

```bash
cp config.example.toml config.toml
```

Отредактируйте `config.toml`:

```toml
base_dir = "./data"
max_file_size = 10485760  # 10MB
allowed_extensions = []
blocked_paths = ["/etc", "/sys", "C:\\Windows"]
verbose = false
```

### 4. Запуск

```bash
cargo run --release
```

Сервер запустится на `http://localhost:3000`

### 5. Проверка

```bash
# Health check
curl http://localhost:3000/health

# Создать тестовый файл
curl -X POST http://localhost:3000/files \
  -H "Content-Type: application/json" \
  -d '{"path":"test.txt","content":"Hello, World!","overwrite":false}'

# Прочитать файл
curl http://localhost:3000/files/test.txt
```

## 🐳 Docker Quick Start

### Запуск с Docker Compose

```bash
# Создайте config.toml
cp config.example.toml config.toml

# Запустите контейнер
docker-compose up -d

# Проверьте логи
docker-compose logs -f

# Остановите контейнер
docker-compose down
```

### Запуск с Docker

```bash
# Соберите образ
docker build -t mcp-server-rust .

# Запустите контейнер
docker run -d \
  -p 3000:3000 \
  -v $(pwd)/data:/app/data \
  -v $(pwd)/config.toml:/app/config.toml:ro \
  --name mcp-server \
  mcp-server-rust

# Проверьте статус
docker ps

# Проверьте логи
docker logs -f mcp-server

# Остановите контейнер
docker stop mcp-server
docker rm mcp-server
```

## 📝 Первые шаги

### Создание файла

```bash
curl -X POST http://localhost:3000/files \
  -H "Content-Type: application/json" \
  -d '{
    "path": "notes.txt",
    "content": "My first note",
    "overwrite": false
  }'
```

**Ответ:**
```json
{
  "path": "notes.txt",
  "size": 13,
  "created_at": "...",
  "modified_at": "...",
  "is_readonly": false,
  "checksum": "..."
}
```

### Чтение файла

```bash
curl http://localhost:3000/files/notes.txt
```

**Ответ:**
```json
{
  "path": "notes.txt",
  "content": "My first note",
  "size": 13,
  "mime_type": "text/plain",
  "checksum": "..."
}
```

### Обновление файла

```bash
curl -X PUT http://localhost:3000/files/notes.txt \
  -H "Content-Type: application/json" \
  -d '{"content": "Updated note"}'
```

### Список файлов

```bash
curl http://localhost:3000/files
```

**Ответ:**
```json
{
  "path": ".",
  "files": [
    {
      "name": "notes.txt",
      "path": "/full/path/to/notes.txt",
      "size": 12,
      "modified_at": "...",
      "is_readonly": false
    }
  ],
  "directories": []
}
```

### Удаление файла

```bash
curl -X DELETE http://localhost:3000/files/notes.txt
```

## 🔧 Разработка

### Запуск в режиме разработки

```bash
# С автоматической перезагрузкой
cargo install cargo-watch
cargo watch -x run

# С подробными логами
RUST_LOG=debug cargo run
```

### Запуск тестов

```bash
# Все тесты
cargo test

# С выводом
cargo test -- --nocapture

# Конкретный тест
cargo test test_create_and_read_file

# Проверка кода
cargo clippy -- -D warnings

# Форматирование
cargo fmt
```

### Структура проекта

```
mcp-server-rust/
├── src/
│   ├── main.rs              # Точка входа
│   ├── config.rs            # Конфигурация
│   ├── error.rs             # Обработка ошибок
│   ├── models.rs            # Модели данных
│   ├── security.rs          # Безопасность
│   ├── handlers/            # HTTP обработчики
│   │   ├── files.rs         # Операции с файлами
│   │   ├── directories.rs   # Операции с директориями
│   │   └── health.rs        # Health check
│   └── services/            # Бизнес-логика
│       └── file_service.rs  # Сервис работы с файлами
├── tests/                   # Интеграционные тесты
├── docs/                    # Документация
├── scripts/                 # Скрипты
├── Cargo.toml              # Зависимости
└── config.toml             # Конфигурация
```

## 🎯 Следующие шаги

1. **Изучите API**: [docs/API.md](docs/API.md)
2. **Настройте безопасность**: [docs/SECURITY.md](docs/SECURITY.md)
3. **Посмотрите примеры**: [docs/EXAMPLES.md](docs/EXAMPLES.md)
4. **Внесите вклад**: [CONTRIBUTING.md](CONTRIBUTING.md)

## 🐛 Решение проблем

### Порт уже занят

```bash
# Linux/macOS: найти процесс
lsof -i :3000

# Windows: найти процесс
netstat -ano | findstr :3000

# Изменить порт в main.rs:
let addr = SocketAddr::from(([127, 0, 0, 1], 3001));
```

### Ошибка прав доступа

```bash
# Убедитесь, что директория data существует и доступна
mkdir -p data
chmod 755 data

# Проверьте конфигурацию base_dir в config.toml
```

### Ошибки компиляции

```bash
# Обновите Rust
rustup update

# Очистите кеш
cargo clean

# Пересоберите
cargo build --release
```

### Проблемы с зависимостями

```bash
# Обновите зависимости
cargo update

# Проверьте Cargo.lock
git checkout Cargo.lock

# Пересоберите
cargo build
```

## 📚 Полезные команды

```bash
# Сборка
make build          # Собрать проект
make test           # Запустить тесты
make run            # Запустить сервер
make clean          # Очистить артефакты

# Docker
make docker-build   # Собрать Docker образ
make docker-run     # Запустить контейнер
make docker-stop    # Остановить контейнер

# Разработка
make fmt            # Форматировать код
make clippy         # Проверить код
make watch          # Запустить с автоперезагрузкой
make audit          # Проверить уязвимости
```

## 💡 Советы

1. **Используйте RUST_LOG** для управления логированием:
   ```bash
   RUST_LOG=debug cargo run
   RUST_LOG=info cargo run
   ```

2. **Настройте base_dir** на выделенную директорию:
   ```toml
   base_dir = "/var/mcp-server/data"
   ```

3. **Ограничьте размер файлов** для безопасности:
   ```toml
   max_file_size = 5242880  # 5MB
   ```

4. **Используйте whitelist расширений** в продакшене:
   ```toml
   allowed_extensions = ["txt", "md", "json"]
   ```

5. **Включите verbose логирование** для отладки:
   ```toml
   verbose = true
   ```

## 🆘 Помощь

- **Документация**: [README.md](README.md)
- **API Reference**: [docs/API.md](docs/API.md)
- **Issues**: [GitHub Issues](https://github.com/yourusername/mcp-server-rust/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/mcp-server-rust/discussions)

---

**Готово!** 🎉 Теперь у вас работает MCP Server Rust.
