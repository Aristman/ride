use anyhow::{Context, Result};
use tracing::info;
use colored::*;
use crate::config::parser::Config;
use crate::core::llm::agents::{LLMAgentManager, VersionInfo, PluginInfo};
use crate::cli::ai::{AiCommand, AiSubcommand, ChangelogCommand, SuggestVersionCommand, ReleaseNotesCommand};

/// Обработчик AI команд
pub async fn handle_ai_command(
    command: AiCommand,
    config_file: &str,
) -> Result<()> {
    info!("🤖 Запуск AI команды");

    // Загружаем конфигурацию
    let config = Config::load_from_file(config_file)
        .with_context(|| format!("Не удалось загрузить конфигурацию из файла: {}", config_file))?;

    // Валидируем конфигурацию
    config.validate()
        .with_context(|| "Валидация конфигурации не пройдена")?;

    // Создаем менеджер LLM агентов
    let agent_manager = LLMAgentManager::from_config(&config)
        .context("Не удалось создать менеджер LLM агентов")?;

    // Проверяем доступность YandexGPT API
    if !agent_manager.health_check().await? {
        eprintln!("{} YandexGPT API недоступен. Проверьте API ключ и подключение к интернету.", "❌".red());
        return Err(anyhow::anyhow!("YandexGPT API недоступен"));
    }

    // Обрабатываем подкоманды
    match command.subcommand {
        AiSubcommand::Changelog(cmd) => {
            handle_changelog_command(cmd, agent_manager).await
        }
        AiSubcommand::SuggestVersion(cmd) => {
            handle_suggest_version_command(cmd, agent_manager).await
        }
        AiSubcommand::ReleaseNotes(cmd) => {
            handle_release_notes_command(cmd, agent_manager).await
        }
    }
}

/// Обработчик команды changelog
async fn handle_changelog_command(
    command: ChangelogCommand,
    agent_manager: LLMAgentManager,
) -> Result<()> {
    println!("🤖 Генерация changelog");

    // Получаем git историю
    let git_log = get_git_history(&command.since).await?;

    // Создаем информацию о версии
    let version_info = VersionInfo {
        current_version: "1.0.0".to_string(), // TODO: Определять из git tags
        new_version: Some("1.1.0".to_string()), // TODO: Запрашивать у пользователя или определять
        branch: "main".to_string(), // TODO: Определять текущую ветку
        git_log: Some(git_log),
        changes_count: 0, // TODO: Считать коммиты
    };

    // Генерируем changelog
    let changelog = agent_manager.generate_changelog(&version_info).await?;

    // Выводим результат
    print_changelog_result(&changelog);

    Ok(())
}

/// Обработчик команды suggest-version
async fn handle_suggest_version_command(
    command: SuggestVersionCommand,
    agent_manager: LLMAgentManager,
) -> Result<()> {
    println!("🔍 Анализ изменений для предложения версии");

    let git_log = if command.analyze_commits {
        Some(get_git_history(&None).await?)
    } else {
        None
    };

    let version_info = VersionInfo {
        current_version: "1.0.0".to_string(), // TODO: Определять из git tags
        new_version: None,
        branch: "main".to_string(),
        git_log,
        changes_count: 0,
    };

    // Анализируем версию
    let analysis = agent_manager.suggest_version(&version_info).await?;

    // Выводим результат
    print_version_analysis_result(&analysis);

    Ok(())
}

/// Обработчик команды release-notes
async fn handle_release_notes_command(
    command: ReleaseNotesCommand,
    agent_manager: LLMAgentManager,
) -> Result<()> {
    println!("📝 Генерация release notes");

    // Создаем информацию о плагине
    let plugin_info = PluginInfo {
        name: "Ride".to_string(), // TODO: Определять из конфигурации
        id: "ru.marslab.ide.ride".to_string(),
        version: "1.1.0".to_string(),
        description: Some("AI помощник для IntelliJ IDEA".to_string()),
    };

    // Временный changelog для теста
    let changelog = "## Изменения в v1.1.0

🚀 Новые возможности:
- Добавлена интеграция с YandexGPT
- Реализован CLI интерфейс для управления плагинами
- Добавлена система автоматической публикации

🐛 Исправления:
- Исправлены проблемы с конфигурацией
- Улучшена обработка ошибок

🔧 Улучшения:
- Оптимизирована производительность сборки
- Улучшено логирование операций";

    // Генерируем release notes
    let version = "1.1.0"; // TODO: Получать из параметров или git tags
    let release_notes = agent_manager.generate_release_notes(version, changelog, &plugin_info).await?;

    // Выводим результат
    print_release_notes_result(&release_notes);

    Ok(())
}

/// Получает историю git коммитов
async fn get_git_history(since_tag: &Option<String>) -> Result<String> {
    use std::process::Command;

    let mut cmd = Command::new("git");
    cmd.args(&["log", "--oneline", "--no-merges"]);

    if let Some(tag) = since_tag {
        cmd.args(&[format!("{}..HEAD", tag)]);
    } else {
        cmd.args(&["-10"]); // Последние 10 коммитов
    }

    let output = cmd.output()
        .context("Ошибка выполнения git log")?;

    if !output.status.success() {
        return Err(anyhow::anyhow!(
            "Git log завершился с ошибкой: {}",
            String::from_utf8_lossy(&output.stderr)
        ));
    }

    let git_log = String::from_utf8_lossy(&output.stdout);
    Ok(git_log.to_string())
}

/// Выводит результат генерации changelog
fn print_changelog_result(changelog: &crate::core::llm::agents::GeneratedChangelog) {
    println!("{}", "=".repeat(60).bright_black());
    println!("📋 CHANGELOG v{}", changelog.version);
    println!("{}", "=".repeat(60).bright_black());

    for section in &changelog.sections {
        println!("\n{} {}", section.emoji, section.title.bright_blue());
        println!("{}", "-".repeat(40).bright_black());

        if section.changes.is_empty() {
            println!("  Нет изменений в этой категории");
        } else {
            for change in &section.changes {
                println!("  • {}", change);
            }
        }
    }

    println!("\n📊 Всего изменений: {}", changelog.total_changes);
    println!("{}", "=".repeat(60).bright_black());
}

/// Выводит результат анализа версий
fn print_version_analysis_result(analysis: &crate::core::llm::agents::VersionAnalysis) {
    println!("{}", "=".repeat(60).bright_black());
    println!("🔍 АНАЛИЗ ВЕРСИИ");
    println!("{}", "=".repeat(60).bright_black());

    println!("📈 Предлагаемая версия: {}", analysis.suggested_version.bright_green());
    println!("🎯 Уверенность: {:.1}%", analysis.confidence * 100.0);
    println!("📝 Обоснование: {}", analysis.reasoning);

    if !analysis.change_types.is_empty() {
        println!("📋 Типы изменений: {}", analysis.change_types.join(", "));
    }

    println!("{}", "=".repeat(60).bright_black());
}

/// Выводит результат генерации release notes
fn print_release_notes_result(notes: &crate::core::llm::agents::GeneratedReleaseNotes) {
    println!("{}", "=".repeat(60).bright_black());
    println!("📝 RELEASE NOTES v{}", notes.version);
    println!("{}", "=".repeat(60).bright_black());

    println!("\n{}\n", notes.title.bright_blue().bold());

    if !notes.highlights.is_empty() {
        println!("🌟 Основные улучшения:");
        for highlight in &notes.highlights {
            println!("  • {}", highlight);
        }
        println!();
    }

    if !notes.body.is_empty() {
        println!("{}", notes.body);
    }

    println!("\n{}", notes.subtitle);
    println!("{}", "=".repeat(60).bright_black());
}