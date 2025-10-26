# Быстрый старт: deploy-pugin

- **Требования**: Rust 1.70+, Git, доступ в интернет.
- **Цель**: за 5 минут собрать, сгенерировать релизные артефакты и проверить конфигурацию.

## Установка
```bash
cd plugin-repository
cargo build
```

## Конфигурация
- Скопируйте примеры:
```bash
cp .env.example .env
cp config.toml.example config.toml
```
- Заполните `.env` значениями: `DEPLOY_PLUGIN_YANDEX_API_KEY`, `DEPLOY_PLUGIN_YANDEX_FOLDER_ID`.

## Проверка
```bash
cargo run -- --help
cargo run -- status --repository --releases --format table
cargo run -- validate --full
```

## Пайплайн (dry-run)
```bash
cargo run -- release --dry-run --auto-version
```

## Деплой (опционально)
```bash
# для реального SSH деплоя потребуется сборка с фичей ssh и настроенные SSH параметры
cargo run --features ssh -- deploy --rollback-on-failure
```

## Явная версия при сборке

Вы можете задать версию явно при сборке. Она будет применена к имени артефакта и использована при деплое:

```bash
cargo run -- build --version 1.2.3 --profile release
```
