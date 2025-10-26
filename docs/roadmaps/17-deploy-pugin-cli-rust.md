# Роадмап: CLI приложение deploy-pugin для автоматизации публикации плагинов

## Цель
Создать высокопроизводительное CLI приложение `deploy-pugin` на Rust для автоматизации пайплайна публикации плагинов в кастомный репозиторий с интеграцией LLM-агентов на базе YandexGPT для генерации информации о релизах.

> **🔒 Безопасность через .env файлы**: Все критические данные (API ключи, учетные данные, URL) вынесены в .env файлы, которые исключены из git. Публичная документация содержит только шаблоны переменных без реальных значений.

## Архитектурные решения

### Ключевые компоненты:
1. **Модульная архитектура на Rust** с четким разделением ответственности
2. **Асинхронная обработка** через Tokio runtime для высокой производительности
3. **LLM-интеграция с YandexGPT** для интеллектуальной генерации контента
4. **CLI интерфейс на Clap** с продвинутыми командами и флагами
5. **Конфигурация TOML** с гибкими настройками проекта и репозитория

## Технические требования

### Стек технологий:
- **Язык**: Rust 1.70+
- **Async Runtime**: Tokio
- **CLI Framework**: Clap 4.x
- **HTTP Client**: Reqwest
- **Serialization**: Serde
- **Error Handling**: anyhow + thiserror
- **Config**: TOML
- **Progress Bars**: indicatif
- **Tables**: tabled

### Структура проекта:
```
plugin-repository/
├── Cargo.toml
├── src/
│   ├── main.rs              # Точка входа, CLI инициализация
│   ├── cli/                 # Модуль определения команд Clap
│   │   ├── mod.rs
│   │   ├── build.rs         # Команда build
│   │   ├── release.rs       # Команда release
│   │   ├── deploy.rs        # Команда deploy
│   │   ├── ai.rs            # LLM команды
│   │   ├── validate.rs      # Команда validate
│   │   └── status.rs        # Команда status
│   ├── commands/            # Реализация команд
│   │   ├── mod.rs
│   │   ├── build.rs         # Сборка плагина
│   │   ├── release.rs       # Полный пайплайн релиза
│   │   ├── deploy.rs        # Выкладка в репозиторий
│   │   └── ai.rs            # LLM операции
│   ├── core/                # Основная бизнес-логика
│   │   ├── mod.rs
│   │   ├── builder.rs       # Система сборки
│   │   ├── releaser.rs      # Управление релизами
│   │   ├── deployer.rs      # Деплой в репозиторий
│   │   └── llm/
│   │       ├── mod.rs
│   │       ├── yandexgpt.rs # YandexGPT клиент
│   │       ├── agents.rs    # LLM агенты
│   │       └── prompts.rs   # Промпты для агентов
│   ├── config/              # Управление конфигурацией
│   │   ├── mod.rs
│   │   ├── parser.rs        # Парсер TOML конфигурации
│   │   ├── env_loader.rs    # Загрузка .env файлов
│   │   └── validator.rs     # Валидация конфигурации и секретов
│   ├── git/                 # Git операции
│   │   ├── mod.rs
│   │   ├── history.rs       # Анализ git истории
│   │   └── tags.rs          # Работа с тегами
│   ├── models/              # Модели данных
│   │   ├── mod.rs
│   │   ├── config.rs        # Модели конфигурации
│   │   ├── plugin.rs        # Модели плагинов
│   │   ├── release.rs       # Модели релизов
│   │   └── repository.rs    # Модели репозитория
│   └── utils/               # Вспомогательные функции
│       ├── mod.rs
│       ├── fs.rs            # Файловые операции
│       ├── network.rs       # Сетевые утилиты
│       └── progress.rs      # Прогресс-бары и UI
├── tests/                   # Интеграционные тесты
├── examples/                # Примеры конфигурации
└── README.md
```

## Этапы реализации

### Фаза 1: Создание проекта и базовой архитектуры ✅
**Срок**: 1-2 дня
**Приоритет**: Высокий
**Статус**: Завершено

- [x] Инициализация Rust проекта с Cargo
- [x] Настройка зависимостей (tokio, clap, serde, anyhow, reqwest)
- [x] Создание модульной структуры директорий
- [x] Реализация базового CLI интерфейса с Clap
- [x] Настройка конфигурации через TOML + .env файлы
- [x] Поддержка dotenv для загрузки переменных окружения
- [x] Валидация наличия всех необходимых секретов при запуске
- [x] Создание моделей данных для проекта, плагина, репозитория
- [x] Базовая обработка ошибок с anyhow/thiserror

### Фаза 2: Ядро системы сборки ✅
**Срок**: 2-3 дня
**Приоритет**: Высокий
**Статус**: Завершено

- [x] Реализация `builder.rs` - системы сборки плагинов
- [x] Автодетекция проектов (Gradle/Maven)
- [x] Параллельная сборка с индикацией прогресса
- [x] Валидация артефактов (checksum, структура)
- [x] Кэширование зависимостей
- [x] Команда `deploy-pugin build` с опциями версии и профиля
- [x] Тесты системы сборки

### Фаза 3: Интеграция с YandexGPT ✅
**Срок**: 2-3 дня
**Приоритет**: Высокий
**Статус**: Завершено

- [x] Реализация `yandexgpt.rs` - HTTP клиента для YandexGPT API
- [x] Создание `agents.rs` - LLM агентов
- [x] Разработка промптов в `prompts.rs`:
  - [x] Changelog Agent промпт
  - [x] Version Suggestion Agent промпт
  - [x] Release Notes Agent промпт
- [x] Асинхронные запросы к API с retry логикой
- [x] Кэширование запросов для экономии токенов
- [x] Обработка ошибок API с контекстуализацией
- [x] Тесты LLM интеграции

### Фаза 4: Git операции и анализ изменений ✅
**Срок**: 1-2 дня
**Приоритет**: Средний
**Статус**: Завершено

- [x] Реализация `git/history.rs` - анализ git истории
- [x] Реализация `git/tags.rs` - работа с тегами
- [x] Анализ коммитов между версиями
- [x] Детекция типа изменений (features, fixes, breaking)
- [x] Интеграция с LLM агентами для анализа изменений
- [x] Тесты git операций

### Фаза 5: Команды LLM агентов ✅
**Срок**: 2 дня
**Приоритет**: Средний
**Статус**: Завершено

- [x] Команда `deploy-pugin ai changelog` с флагами (--since, --verbose)
- [x] Команда `deploy-pugin ai suggest-version` (--analyze-commits)
- [x] Команда `deploy-pugin ai release-notes` (--template)
- [x] Мульти-модельная поддержка (OpenAI, Anthropic, локальные модели)
- [x] Настройка разных агентов в конфигурации
- [x] Валидация и фильтрация LLM ответов
- [x] Тесты LLM команд

### Фаза 6: Система релизов
**Срок**: 2-3 дня
**Приоритет**: Высокий

- [ ] Реализация `releaser.rs` - менеджера релизов
- [ ] Автоматическое определение версий через semver
- [ ] Интеграция с git tags и аннотированными коммитами
- [ ] Генерация release notes через YandexGPT
- [ ] Валидация перед релизом
- [ ] Команда `deploy-pugin release` с опциями (--dry-run, --auto-version)
- [ ] Тесты системы релизов

### Фаза 7: Система деплоя
**Срок**: 2-3 дня
**Приоритет**: Высокий

- [ ] Реализация `deployer.rs` - движка деплоя
- [ ] Поддержка SSH/SCP для загрузки на сервер
- [ ] Atomic updates XML репозитория
- [ ] Проверка целостности после деплоя
- [ ] Механизм отката при failures
- [ ] Команда `deploy-pugin deploy` с опциями (--force, --rollback-on-failure)
- [ ] Интеграция с репозиторием по указанному URL
- [ ] Тесты системы деплоя

### Фаза 8: Вспомогательные команды и утилиты
**Срок**: 1-2 дня
**Приоритет**: Средний

- [ ] Команда `deploy-pugin validate` (--metadata, --compatibility)
- [ ] Команда `deploy-pugin status` - статус репозитория и последних релизов
- [ ] Улучшенный CLI интерфейс с цветным выводом
- [ ] Прогресс-бары и детализация операций
- [ ] Вывод в различных форматах (table, json, markdown)
- [ ] Подробная справка (--help) для всех команд
- [ ] Тесты утилит

### Фаза 9: Интеграция и полный пайплайн
**Срок**: 2 дня
**Приоритет**: Высокий

- [ ] Интеграция всех компонентов в единый пайплайн
- [ ] Комплексная обработка ошибок с контекстом
- [ ] Automatic retry для сетевых запросов
- [ ] Валидация всех входных данных
- [ ] Оптимизация производительности (сборка < 30 сек, LLM < 10 сек)
- [ ] Интеграционные тесты полного цикла
- [ ] Тесты производительности и нагрузочные тесты

### Фаза 10: Тестирование и документация
**Срок**: 2-3 дня
**Приоритет**: Средний

- [ ] Комплексное тестирование всех модулей
- [ ] Интеграционные тесты с реальным репозиторием
- [ ] Тесты CLI интерфейса
- [ ] Создание README.md с установкой и быстрым стартом
- [ ] Примеры конфигурационных файлов (.env.example, config.toml.example)
- [ ] API документация для всех публичных модулей
- [ ] Инструкция по интеграции с CI/CD
- [ ] Траблшутинг гайд для常见 проблем
- [ ] Security guide по управлению секретами и .env файлами
- [ ] Пример setup.sh скрипта для быстрой инициализации
- [ ] Документация API и архитектуры
- [ ] Подробная инструкция по развертыванию и использованию

### Фаза 11: Финализация и CI/CD
**Срок**: 1-2 дня
**Приоритет**: Низкий

- [ ] Настройка CI/CD для автоматического тестирования
- [ ] Создание release binaries для разных платформ
- [ ] Финальное тестирование на реальных проектах
- [ ] Оптимизация бинарного размера и зависимостей
- [ ] Код review и рефакторинг
- [ ] Подготовка к первому релизу

## Детальная реализация LLM агентов

### Changelog Agent
```rust
impl LLMAgent for ChangelogAgent {
    async fn generate_changelog(&self, git_log: &str, version_info: &VersionInfo) -> Result<String> {
        let prompt = format!(
            r#"
            Ты - технический писатель в компании разработки IDE плагинов.
            Проанализируй историю git коммитов и создай качественный changelog на русском языке.

            Версия: {new_version}
            Предыдущая версия: {old_version}
            Ветка: {branch}

            Список коммитов:
            {git_log}

            Требования к формату:
            - Используй маркированный список
            - Группируй изменения по категориям: 🚀 Новые возможности, 🐛 Исправления, 🔧 Улучшения, 💥 Критические изменения
            - Будь конкретен, но лаконичен
            - Упоминай конкретные файлы или компоненты если это уместно
            "#,
            new_version = version_info.new_version,
            old_version = version_info.old_version,
            branch = version_info.branch,
            git_log = git_log
        );

        self.client.chat_completion(&prompt).await
    }
}
```

### Version Suggestion Agent
```rust
impl LLMAgent for VersionAgent {
    async fn suggest_version(&self, changes: &[GitChange]) -> Result<String> {
        let change_types = self.categorize_changes(changes);
        let breaking_changes = changes.iter().filter(|c| c.is_breaking).count();

        let prompt = format!(
            r#"
            Проанализируй изменения в кодовой базе и предложи следующую версию по semantic versioning.

            Текущая версия: {current_version}
            Типы изменений: {change_types}
            Критические изменения: {breaking_changes}

            Правила semver:
            - MAJOR версия при несовместимых изменениях API
            - MINOR версия при добавлении новой функциональности
            - PATCH версия при обратно-совместимых исправлениях

            Верни ТОЛЬКО версию в формате X.Y.Z и краткое обоснование через двоеточие.
            "#,
            current_version = self.current_version,
            change_types = change_types,
            breaking_changes = breaking_changes
        );

        self.client.chat_completion(&prompt).await
    }
}
```

### Release Notes Agent
```rust
impl LLMAgent for ReleaseAgent {
    async fn generate_release_notes(&self, changelog: &str, version_info: &VersionInfo) -> Result<String> {
        let prompt = format!(
            r#"
            Напиши привлекательные release notes для новой версии плагина JetBrains.

            Информация о плагине:
            Название: {plugin_name}
            Версия: {version}
            ID: {plugin_id}

            Ключевые изменения:
            {changelog}

            Требования:
            - Начни с краткого описания основных улучшений
            - Выдели 2-3 самых важных изменения
            - Используй эмодзи для визуального выделения
            - Будь профессиональным, но дружелюбным
            - Целевая аудитория - разработчики
            - Длина: 150-300 слов
            "#,
            plugin_name = self.plugin_name,
            version = version_info.new_version,
            plugin_id = self.plugin_id,
            changelog = changelog
        );

        self.client.chat_completion(&prompt).await
    }
}
```

## Пример конфигурации TOML

## Пример конфигурации

### 📁 .env.example - Файл с секретами (не добавлять в git!)
```bash
# 🔒 КОНФИДЕНЦИАЛЬНО - Скопируйте в .env и не добавляйте в git!
# Все критические данные вынесены в переменные окружения

# Репозиторий плагинов
REPOSITORY_URL=http://your-repository.com/updatePlugins.xml
SSH_HOST=your-server.com
SSH_USER=deploy-user
SSH_PRIVATE_KEY_PATH=~/.ssh/deploy_key

# Пути на сервере
DEPLOY_PATH=/var/www/plugins/archives/
XML_PATH=/var/www/plugins/updatePlugins.xml

# YandexGPT API
DEPLOY_PLUGIN_YANDEX_API_KEY=your_yandex_api_key_here
DEPLOY_PLUGIN_YANDEX_FOLDER_ID=your_yandex_folder_id_here

# Опционально: дополнительные LLM провайдеры
OPENAI_API_KEY=your_openai_key_here
ANTHROPIC_API_KEY=your_anthropic_key_here
```

### 📁 config.toml - Основная конфигурация (без секретов)
```toml
[project]
name = "ride"
id = "ru.marslab.ide.ride"
type = "intellij"

[build]
gradle_task = "buildPlugin"
output_dir = "build/distributions"
build_args = ["-x test"]

[repository]
# Все секреты загружаются из .env файла
url = "${REPOSITORY_URL}"
ssh_host = "${SSH_HOST}"
ssh_user = "${SSH_USER}"
ssh_private_key_path = "${SSH_PRIVATE_KEY_PATH}"
deploy_path = "${DEPLOY_PATH}"
xml_path = "${XML_PATH}"

[llm]
provider = "yandexgpt"
temperature = 0.3
max_tokens = 2000

[yandexgpt]
# Загружается из .env
api_key = "${DEPLOY_PLUGIN_YANDEX_API_KEY}"
folder_id = "${DEPLOY_PLUGIN_YANDEX_FOLDER_ID}"
model = "yandexgpt"

# Опциональные провайдеры
[openai]
api_key = "${OPENAI_API_KEY}"
model = "gpt-4"

[anthropic]
api_key = "${ANTHROPIC_API_KEY}"
model = "claude-3-sonnet"

[llm.agents]
changelog_agent = { model = "yandexgpt", temperature = 0.3 }
version_agent = { model = "yandexgpt-lite", temperature = 0.1 }
release_agent = { model = "yandexgpt", temperature = 0.4 }

[git]
main_branch = "main"
tag_prefix = "v"
```

### 📁 .gitignore - Исключение секретов
```gitignore
# Переменные окружения с секретами
.env
.env.local
.env.production

# Временные файлы
*.tmp
*.log

# Артефакты сборки
target/
dist/

# IDE файлы
.vscode/
.idea/
```

### 📁 setup.sh - Скрипт инициализации окружения
```bash
#!/bin/bash
# 🚀 Скрипт быстрой настройки окружения

echo "🔧 Настройка окружения deploy-pugin..."

# Копируем пример .env
if [ ! -f .env ]; then
    cp .env.example .env
    echo "✅ Создан файл .env - отредактируйте его с вашими данными"
else
    echo "⚠️  Файл .env уже существует"
fi

# Копируем конфигурацию
if [ ! -f config.toml ]; then
    cp config.toml.example config.toml
    echo "✅ Создан файл config.toml"
else
    echo "⚠️  Файл config.toml уже существует"
fi

echo ""
echo "🔒 ВАЖНО: Отредактируйте .env файл с вашими секретными данными!"
echo "📝 Не добавляйте .env в git репозиторий!"
echo ""
echo "🚀 Команда для запуска:"
echo "   cargo run -- release --dry-run"
```

## Примеры использования CLI

```bash
# Базовый пайплайн релиза
deploy-pugin build --version 1.2.0
deploy-pugin ai changelog --since v1.1.0
deploy-pugin release --dry-run
deploy-pugin release
deploy-pugin deploy --rollback-on-failure

# LLM операции
deploy-pugin ai suggest-version --analyze-commits
deploy-pugin ai release-notes --template corporate

# Валидация и статус
deploy-pugin validate --metadata --compatibility
deploy-pugin status

# Продвинутые опции
deploy-pugin build --version 1.2.0 --profile release
deploy-pugin release --auto-version --verbose
deploy-pugin deploy --force --no-rollback
```

## Критерии готовности

### Функциональные требования:
- [ ] Все CLI команды реализованы и работают корректно
- [ ] Сборка плагинов работает с Gradle/Maven проектами
- [ ] LLM агенты генерируют качественный контент (changelog, release notes)
- [ ] Деплой в репозиторий работает с откатами при ошибках
- [ ] Автоматическое определение версий работает по semver
- [ ] Git интеграция корректно анализирует историю изменений

### Нефункциональные требования:
- [ ] Время сборки < 30 секунд для средних проектов
- [ ] LLM-запросы выполняются < 10 секунд
- [ ] Приложение работает на Linux/macOS/Windows
- [ ] Потребление памяти < 100MB в нормальном режиме
- [ ] Обработка ошибок с контекстом и retry логикой
- [ ] Цветной вывод с прогресс-барами и таблицами

### Качество кода:
- [ ] Все модули покрыты unit тестами (>80%)
- [ ] Интеграционные тесты покрывают основные сценарии
- [ ] Код следует Rust best practices и idioms
- [ ] Полная документация API и примеры использования
- [ ] Конфигурация через TOML с валидацией
- [ ] Логирование операций с разными уровнями детализации

### Документация:
- [ ] README.md с установкой и быстрым стартом
- [ ] Примеры конфигурационных файлов (без секретов)
- [ ] API документация для всех публичных модулей
- [ ] Инструкция по интеграции с CI/CD
- [ ] Траблшутинг гайд для常见 проблем
- [ ] Security guide по управлению секретами и переменными окружения

## Риски и митигации

### Технические риски:
- **Риск**: Проблемы с асинхронностью в Rust
  - **Митигация**: Использование стандартных практик Tokio, простая архитектура
- **Риск**: Нестабильность YandexGPT API
  - **Митигация**: Retry логика, exponential backoff, кэширование
- **Риск**: Сложность с SSH/SCP деплоем
  - **Митигация**: Использование проверенных библиотек (ssh2), альтернативные методы

### Проектные риски:
- **Риск**: Сложность интеграции всех компонентов
  - **Митигация**: Поэтапная разработка с тестированием каждого компонента
- **Риск**: Проблемы с производительностью
  - **Митигация**: Профилирование, оптимизация критических путей, кэширование

### Безопасность:
- **Риск**: Случайная публикация секретов в репозитории
  - **Митигация**: Все чувствительные данные вынесены в переменные окружения, примеры конфигураций не содержат реальных данных
- **Риск**: Недостаточная защита API ключей
  - **Митигация**: Использование префиксных имен переменных окружения, валидация наличия secrets при запуске

## Следующие шаги после завершения

1. **Расширение функциональности**:
   - Поддержка других типов репозиториев (Artifactory, Nexus)
   - Веб-интерфейс для управления релизами
   - Интеграция с GitHub/GitLab CI/CD

2. **Улучшения LLM**:
   - Поддержка других LLM провайдеров
   - Более умные промпты с контекстом проекта
   - Генерация технической документации

3. **Мониторинг и аналитика**:
   - Сбор метрик использования
   - Аналитика успешности релизов
   - Алерты при проблемах с деплоем

---

**Дата создания**: 2025-10-26
**Статус**: На утверждении
**Приоритет**: Высокий
**Ожидаемый срок завершения**: 14-18 дней