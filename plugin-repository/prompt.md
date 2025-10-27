По приведенному ТЗ составь план работы в виде роадмапы, запиши роадмап в существующию docs/roadmaps как роадмап для фичи
План дай на утверждение.

# ТЗ:
Создай высокопроизводительное CLI приложение deploy-pugin на Rust для автоматизации пайплайна публикации плагинов в
кастомный репозиторий, с интеграцией LLM-агентов для генерации информации о релизах.
Директория создания - plugin-repository

URL репозитория:
http://158.160.107.227/updatePlugins.xml

Путь к директории с плагинами:
/var/www/plugins/archives/

# 1. Архитектура на Rust

Технические требования:

- Использование Clap для продвинутого CLI интерфейса
- Асинхронная архитектура с Tokio runtime
- Модульная структура с изолированными компонентами
- Обработка ошибок через anyhow/thiserror с контекстуализацией
- Сериализация/десериализация через serde

Структура проекта:
```
src/
├── main.rs # Точка входа, инициализация CLI
├── cli/ # Модуль определения команд Clap
├── commands/ # Реализация команд
│ ├── build.rs # Сборка плагина
│ ├── release.rs # Полный пайплайн
│ ├── deploy.rs # Выкладка в репозиторий
│ └── ai.rs # LLM команды
├── core/ # Основная логика
│ ├── builder.rs # Система сборки
│ ├── releaser.rs # Управление релизами
│ ├── deployer.rs # Деплой в репозиторий
│ └── llm/ # Модуль LLM-агентов
├── config/ # Управление конфигурацией
└── utils/ # Вспомогательные функции
```

# 2. Команды CLI

```bash
// Основные команды
deploy-pugin build [--version <VERSION>] [--profile <PROFILE>]
deploy-pugin release [--dry-run] [--auto-version]
deploy-pugin deploy [--force] [--rollback-on-failure]

// LLM-команды
deploy-pugin ai changelog [--since <TAG>] [--verbose]
deploy-pugin ai suggest-version [--analyze-commits]
deploy-pugin ai release-notes [--template <TEMPLATE>]

// Утилиты
deploy-pugin validate [--metadata] [--compatibility]
deploy-pugin status // Статус репозитория и последних релизов
```

ИНТЕГРАЦИЯ LLM-АГЕНТОВ:
- Мульти-модельная поддержка (OpenAI, Anthropic, локальные модели)
- Агент для анализа git history и генерации changelog
- Агент для предложения следующей версии (semantic versioning)
- Агент для написания release notes на основе кодовых изменений
- Кэширование запросов для экономии токенов

# 3. Интеграция с YandexGPT

Конфигурация YandexGPT:
```toml
[project]
name = "ride"
id = "ru.marslab.ide.ride"
type = "intellij"  # или "android-studio"

[build]
gradle_task = "buildPlugin"
output_dir = "build/distributions"
build_args = ["-x test"]  # опциональные аргументы

[repository]
url = "http://your-repository.com/updatePlugins.xml"
ssh_host = "your-server.com"
ssh_user = "deploy-user"
deploy_path = "/var/www/plugins/archives/"
xml_path = "/var/www/plugins/updatePlugins.xml"

[llm]
provider = "yandexgpt"
temperature = 0.3
max_tokens = 2000

[yandexgpt]
api_key = "${YANDEX_API_KEY}"
folder_id = "${YANDEX_FOLDER_ID}"
model = "yandexgpt"

[llm.agents]
changelog_agent = { model = "yandexgpt", temperature = 0.3 }
version_agent = { model = "yandexgpt-lite", temperature = 0.1 }
release_agent = { model = "yandexgpt", temperature = 0.4 }

[git]
main_branch = "main"
tag_prefix = "v"
```

# 4. LLM-агенты на YandexGPT

### 4.1. Changelog Agent
```
// Промпт для YandexGPT
static CHANGELOG_PROMPT: &str = r#"
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
- Для исправлений укажи номер issue если есть в сообщении коммита

Сгенерируй changelog в формате Markdown:
"#;
```

### 4.2. Version Suggestion Agent
```
static VERSION_PROMPT: &str = r#"
Проанализируй изменения в кодовой базе и предложи следующую версию по semantic versioning (semver).

Текущая версия: {current_version}
Типы изменений: {change_types}
Критические изменения: {breaking_changes}

Правила semver:

- MAJOR версия при несовместимых изменениях API
- MINOR версия при добавлении новой функциональности
- PATCH версия при обратно-совместимых исправлениях

Верни ТОЛЬКО версию в формате X.Y.Z и краткое обоснование через двоеточие.
Пример: "1.2.3: Добавлена новая функциональность без breaking changes"
"#;

4.3. Release Notes Agent
static RELEASE_NOTES_PROMPT: &str = r#"
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

Формат Markdown:
"#;
```

### 5. Компоненты системы

#### 5.1. Build System

- Автодетекция проектов (Gradle/Maven)
- Параллельная сборка с индикацией прогресса
- Валидация артефактов (checksum, структура)
- Кэширование зависимостей

#### 5.2. Release Manager

- Автоматическое определение версий через semver
- Интеграция с git tags и аннотированными коммитами
- Генерация release notes через YandexGPT
- Валидация перед релизом

#### 5.3. Deploy Engine

- Поддержка SSH/SCP для загрузки на сервер
- Atomic updates XML репозитория
- Проверка целостности после деплоя
- Механизм отката при failures

# 7. Особенности реализации на Rust
#### 7.1. Асинхронные LLM-запросы

```rust
impl YandexGPTClient {
pub async fn generate_changelog(
&self,
git_log: &str,
version_info: &VersionInfo
) -> Result<String> {
let prompt = self.build_changelog_prompt(git_log, version_info);
self.chat_completion(&prompt).await
}
}
```

#### 7.2. Обработка ошибок

```rust
#[derive(Debug, thiserror::Error)]
pub enum PipelineError {
#[error("Build failed: {0}")]
BuildFailed(String),
#[error("YandexGPT API error: {0}")]
LLMError(#[from] YandexGPTError),
#[error("Deployment failed: {0}")]
DeployError(String),
#[error("Configuration error: {0}")]
ConfigError(String),
}
```

#### 7.3. Прогресс-бары и UI
- Использование indicatif для прогресс-баров
- Цветной вывод через colored
- Структурированные таблицы через tabled
- JSON логи для CI/CD интеграции

# 8. Workflow пайплайна
#### 8.1. Полный цикл релиза
```bash
# 1. Сборка и валидация

deploy-pugin build --version 1.2.0

# 2. Генерация changelog через YandexGPT

deploy-pugin ai changelog --since v1.1.0

# 3. Предложение версии (опционально)

deploy-pugin ai suggest-version --analyze-commits

# 4. Запуск полного пайплайна

deploy-pugin release --dry-run # предпросмотр
deploy-pugin release # реальное выполнение

# 5. Деплой в репозиторий

deploy-pugin deploy --rollback-on-failure
```

# 9. Требования к качеству

Производительность:

- Время сборки < 30 секунд для средних проектов
- LLM-запросы < 10 секунд каждый
- Минимальное потребление памяти

Надежность:

- 100% обработка ошибок с контекстом
- Automatic retry для сетевых запросов
- Валидация всех входных данных

Пользовательский опыт:

- Интуитивные команды и флаги
- Подробная помощь (--help)
- Цветной вывод с уровнями детализации
- Поддержка dry-run для всех деструктивных операций

# 10. Выходные данные и отчетность

Форматы вывода:

- Таблицы с статусом операций
- JSON для машинной обработки
- Markdown для документации
- Цветные логи с уровнями (INFO, WARN, ERROR)

Пример отчета:

```
🚀 Release Pipeline Report
────────────────────────────
✅ Build completed: my-plugin-1.2.0.zip
✅ Changelog generated via YandexGPT
✅ Version validated: 1.2.0 (semver compliant)
✅ Repository updated successfully
📊 Stats: 15 files changed, 3 new features, 8 fixes
🌐 Repository URL: http://plugins.company.com/updatePlugins.xml
```

Приложение должно обеспечить полную автоматизацию процесса релиза плагинов с интеллектуальной поддержкой YandexGPT для
генерации качественного контента.