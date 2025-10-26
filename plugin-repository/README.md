# Deploy Plugin — CLI инструмент для автоматизации публикации плагинов

Высокопроизводительное CLI приложение на Rust для автоматизации пайплайна публикации IntelliJ плагинов в кастомный репозиторий с интеграцией LLM-агентов на базе YandexGPT.

## 🚀 Возможности

- **Умная генерация контента** через YandexGPT API
- **Автоматический анализ Git** для определения версий и изменений
- **Сборка плагинов** с автодетекцией типа проекта
- **CLI интерфейс** с продвинутыми командами и флагами
- **Безопасное управление секретами** через переменные окружения

## 📋 Установка

### Требования
- Rust 1.70+
- Git
- YandexGPT API ключ

### Сборка
```bash
# Клонирование репозитория
git clone https://github.com/Aristman/ride.git
cd ride/plugin-repository

# Настройка окружения
cp .env.example .env
cp config.toml.example config.toml

# Сборка
cargo build --release
```

### Быстрый старт
```bash
# Настройка API ключей в .env
echo "DEPLOY_PLUGIN_YANDEX_API_KEY=your_key_here" >> .env
echo "DEPLOY_PLUGIN_YANDEX_FOLDER_ID=your_folder_id" >> .env

# Запуск
./target/release/deploy-pugin --help
```

## 🛠️ Использование

### AI команды для управления релизами

#### Генерация changelog
```bash
# Базовая генерация
./deploy-pugin ai changelog

# С Git анализом и подробным выводом
./deploy-pugin ai changelog --use-git-analysis --verbose

# Для конкретного диапазона версий
./deploy-pugin ai changelog --since v1.1.0 --to v1.2.0

# Сохранение в файл
./deploy-pugin ai changelog --output CHANGELOG.md
```

#### Предложение версии
```bash
# Анализ коммитов для предложения версии
./deploy-pugin ai suggest-version

# С семантическим анализом
./deploy-pugin ai suggest-version --use-semantic-analysis

# С указанием текущей версии
./deploy-pugin ai suggest-version --current-version 1.1.0

# Применение предложенной версии
./deploy-pugin ai suggest-version --apply
```

#### Генерация release notes
```bash
# Базовая генерация
./deploy-pugin ai release-notes

# С сохранением в файл
./deploy-pugin ai release-notes --output RELEASE_NOTES.md

# С шаблоном
./deploy-pugin ai release-notes --template corporate
```

### Примеры использования

```bash
# Полный пайплайн релиза
./deploy-pugin ai changelog --use-git-analysis --verbose
./deploy-pugin ai suggest-version --use-semantic-analysis
./deploy-pugin ai release-notes --output release-notes.md

# Анализ последних изменений
./deploy-pugin ai changelog --since v1.2.0 --use-git-analysis

# Создание release notes для следующей версии
./deploy-pugin ai suggest-version --current-version 1.2.0
./deploy-pugin ai release-notes --template corporate
```

### Указание версии при сборке

По умолчанию версия берётся из имени ZIP-артефакта. Вы можете явно задать версию при сборке — она будет применена к имени файла (артефакт будет переименован) и использована далее при деплое:

```bash
cargo run -- build --version 1.2.3 --profile release
```

После сборки артефакт будет иметь имя вида `your-plugin-1.2.3.zip`, и деплой возьмёт версию из этого имени.

## ⚙️ Конфигурация

### config.toml
```toml
[project]
name = "ride"
id = "ru.marslab.ide.ride"
type = "intellij"

[build]
gradle_task = "buildPlugin"
output_dir = "build/distributions"

[repository]
url = "${REPOSITORY_URL}"
ssh_host = "${SSH_HOST}"
ssh_user = "${SSH_USER}"
deploy_path = "${DEPLOY_PATH}"
xml_path = "${XML_PATH}"

[llm]
provider = "yandexgpt"
temperature = 0.3
max_tokens = 2000

[yandexgpt]
api_key = "${DEPLOY_PLUGIN_YANDEX_API_KEY}"
folder_id = "${DEPLOY_PLUGIN_YANDEX_FOLDER_ID}"
model = "yandexgpt"

[llm_agents]
changelog_agent = { model = "yandexgpt", temperature = 0.3 }
version_agent = { model = "yandexgpt-lite", temperature = 0.1 }
release_agent = { model = "yandexgpt", temperature = 0.4 }

[git]
main_branch = "main"
tag_prefix = "v"
```

### .env файл
```bash
# YandexGPT API
DEPLOY_PLUGIN_YANDEX_API_KEY=your_yandex_api_key_here
DEPLOY_PLUGIN_YANDEX_FOLDER_ID=your_yandex_folder_id_here

# Репозиторий (опционально)
REPOSITORY_URL=http://your-repository.com/updatePlugins.xml
SSH_HOST=your-server.com
SSH_USER=deploy-user
DEPLOY_PATH=/var/www/plugins/archives/
XML_PATH=/var/www/plugins/updatePlugins.xml
```

## 📚 Команды

### AI команды
- `ai changelog` — генерация changelog с анализом Git
- `ai suggest-version` — предложение версии на основе изменений
- `ai release-notes` — генерация release notes

### Глобальные опции
- `--config <path>` — путь к конфигурационному файлу
- `--verbose` — подробный вывод
- `--help` — справка по команде

## 🏗️ Архитектура

```
src/
├── main.rs              # Точка входа
├── cli/                 # CLI интерфейс
├── commands/            # Реализация команд
│   └── ai.rs           # AI команды
├── core/                # Основная логика
│   └── llm/            # LLM интеграция
├── config/              # Конфигурация
├── git/                 # Git операции
├── models/              # Модели данных
└── utils/               # Утилиты
```

## 🔒 Безопасность

- Все секреты хранятся в переменных окружения
- `.env` файл исключен из Git
- Валидация наличия секретов при запуске
- Проверка API ключей перед использованием

## 📝 Статус разработки

### ✅ Завершенные фазы:
- **Фаза 1**: Базовая архитектура и CLI интерфейс
- **Фаза 2**: Система сборки плагинов
- **Фаза 3**: Интеграция с YandexGPT
- **Фаза 4**: Git операции и анализ изменений
- **Фаза 5**: LLM команды для управления релизами

### 🚧 В разработке:
- **Фаза 6**: Система релизов
- **Фаза 7**: Система деплоя
- **Фаза 8**: Вспомогательные команды
- **Фаза 9**: Полный пайплайн
- **Фаза 10**: Тестирование и документация

## 🤝 Вклад

1. Fork репозитория
2. Создайте feature ветку (`git checkout -b feature/amazing-feature`)
3. Commit изменения (`git commit -m 'Add amazing feature'`)
4. Push в ветку (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

## 📄 Лицензия

Проект распространяется под лицензией MIT — см. файл [LICENSE](../LICENSE) для деталей.

## 🔗 Ссылки

- [Основной репозиторий Ride](../README.md)
- [Документация YandexGPT](https://cloud.yandex.ru/docs/yandexgpt/)
- [Документация IntelliJ Platform](https://www.jetbrains.org/intellij/sdk/docs/)

## 📞 Поддержка

При возникновении проблем:
1. Проверьте [.env.example](.env.example) для правильной конфигурации
2. Убедитесь что YandexGPT API ключи валидны
3. Проверьте [Roadmap](../docs/roadmaps/17-deploy-pugin-cli-rust.md) для статуса разработки

---

**Разработано для [Ride IDE](../README.md)** — AI помощника для IntelliJ IDEA.