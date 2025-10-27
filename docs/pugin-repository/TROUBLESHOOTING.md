# Troubleshooting Guide

- **invalid model_uri (YandexGPT)**
  - Проверьте `DEPLOY_PLUGIN_YANDEX_API_KEY` и `DEPLOY_PLUGIN_YANDEX_FOLDER_ID`
  - В `config.toml` для `[yandexgpt].model` используйте `yandexgpt` или `yandexgpt-lite`
  - Включите `--log-level debug` и смотрите логи формирования `model_uri`

- **.env не подхватился**
  - Убедитесь, что `.env` в корне `plugin-repository/`
  - При запуске из корня монорепо используйте автоподхват `plugin-repository/.env` (реализовано в `main.rs`)

- **git: пустая история или не репозиторий**
  - Проверьте, что запускаете в git-репозитории
  - Создайте коммит перед `release`

- **SSH деплой не работает**
  - Сборка должна быть с фичей `ssh`: `cargo run --features ssh -- deploy`
  - Проверьте `SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY_PATH`
  - Проверьте права на ключ (600) и доступ к `deploy_path`

- **Падение тестов**
  - Запустите `cargo test -p deploy-pugin` в каталоге `plugin-repository`
  - Убедитесь, что версии зависимостей совместимы
