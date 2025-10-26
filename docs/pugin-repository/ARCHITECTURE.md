# Архитектура deploy-pugin

## Слои
- core/: бизнес-логика (builder, releaser, deployer, llm)
- cli/: описание CLI флагов и субкоманд (clap)
- commands/: обработчики субкоманд
- config/: парсер и валидатор конфигурации
- git/: операции с git (история, теги)
- models/: структуры данных
- utils/: вспомогательные утилиты (progress, network)

## Потоки данных (release)
1) CLI -> commands/release.rs
2) Загрузка config -> валидация
3) GitRepository -> анализ изменений
4) LLMAgentManager -> генерация changelog/notes
5) ReleaseManager -> подготовка/создание/публикация

## Потоки данных (deploy)
1) CLI -> commands/deploy.rs
2) Загрузка config -> валидация (опц.)
3) Поиск артефактов -> генерация updatePlugins.xml
4) SSH/SCP (feature "ssh") -> загрузка артефактов
5) SFTP rename -> атомарная замена XML
6) Откат при сбоях

## Расширяемость
- LLM провайдеры через конфиг и LLMAgentManager
- Artefact discovery/validator в builder
- Механизм deploy через фичи и стратегии (локально/ssh)
