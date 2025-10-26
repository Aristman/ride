# Руководство по деплою

## Подготовка
- Заполните `.env` (см. `.env.example`).
- Убедитесь, что SSH доступ к серверу настроен и ключ доступен.
- Проверьте права на `deploy_path` и путь `xml_path`.

## Сборка артефактов плагина
```bash
cargo run -- build --profile release
```

## Подготовка релиза (dry-run)
```bash
cargo run -- release --dry-run --auto-version
```

## Создание релиза
```bash
cargo run -- release --auto-version
```

## Деплой
- По умолчанию SSH выключен — нужен флаг сборки `ssh`:
```bash
cargo run --features ssh -- deploy --rollback-on-failure
```

## Проверка
- Просмотрите логи и убедитесь, что XML обновился атомарно (через временный файл и rename).
- Проверьте, что артефакты доступны клиентам.
