use anyhow::{Context, Result};
use tracing::info;
use colored::*;
use crate::config::parser::Config;
use crate::core::llm::agents::{LLMAgentManager, PluginInfo};
use crate::cli::ai::{AiCommand, AiSubcommand, ChangelogCommand, SuggestVersionCommand, ReleaseNotesCommand};
use crate::git::GitRepository;

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

    // Создаем Git репозиторий
    let current_dir = std::env::current_dir()
        .context("Не удалось определить текущую директорию")?;
    let git_repo = GitRepository::new(&current_dir);

    // Проверяем, что мы в git репозитории
    if !git_repo.is_valid_repository() {
        eprintln!("{} Текущая директория не является git репозиторием", "❌".red());
        return Err(anyhow::anyhow!("Не git репозиторий"));
    }

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
            handle_changelog_command(cmd, agent_manager, git_repo).await
        }
        AiSubcommand::SuggestVersion(cmd) => {
            handle_suggest_version_command(cmd, agent_manager, git_repo).await
        }
        AiSubcommand::ReleaseNotes(cmd) => {
            handle_release_notes_command(cmd, agent_manager, git_repo).await
        }
    }
}

/// Обработчик команды changelog
async fn handle_changelog_command(
    command: ChangelogCommand,
    agent_manager: LLMAgentManager,
    git_repo: GitRepository,
) -> Result<()> {
    println!("🤖 Генерация changelog с анализом Git репозитория");

    // Получаем текущую ветку
    let current_branch = git_repo.history.get_current_branch().await
        .unwrap_or_else(|_| "main".to_string());

    // Получаем последний тег
    let latest_tag = git_repo.tags.get_latest_tag().await?;

    // Определяем from и to для анализа
    let from_tag = command.since.as_ref().or_else(|| latest_tag.as_ref().map(|t| &t.name));
    let to_tag = command.to.as_deref();

    println!("📊 Анализ изменений: {:?} → {:?}", from_tag, to_tag);

    // Генерируем changelog через Git анализ
    let changelog = if command.use_git_analysis {
        // Используем улучшенный анализ через Git репозиторий
        let (analysis, _) = git_repo.get_full_analysis(from_tag.map(|s| s.as_str()), to_tag).await?;
        agent_manager.changelog_agent.generate_enhanced_changelog(&git_repo, &analysis).await?
    } else {
        // Используем Git репозиторий для получения данных
        agent_manager.changelog_agent.generate_changelog_from_repo(&git_repo, from_tag.map(|s| s.as_str()), to_tag).await?
    };

    // Выводим результат
    print_changelog_result(&changelog, command.verbose);

    // Если указан выходной файл, сохраняем результат
    if let Some(output_file) = &command.output {
        save_changelog_to_file(&changelog, output_file)?;
        println!("💾 Changelog сохранен в файл: {}", output_file.green());
    }

    Ok(())
}

/// Обработчик команды suggest-version
async fn handle_suggest_version_command(
    command: SuggestVersionCommand,
    agent_manager: LLMAgentManager,
    git_repo: GitRepository,
) -> Result<()> {
    println!("🔍 Анализ изменений для предложения версии");

    // Получаем текущую версию из последнего тега
    let current_version = if let Some(latest_tag) = git_repo.tags.get_latest_tag().await? {
        latest_tag.name
    } else {
        command.current_version.clone().unwrap_or_else(|| "1.0.0".to_string())
    };

    println!("📋 Текущая версия: {}", current_version.bright_blue());

    // Анализируем версию с использованием Git репозитория
    let analysis = if command.use_semantic_analysis {
        // Используем семантический анализ на основе Git анализа
        agent_manager.version_agent.suggest_semantic_version(&git_repo, &current_version).await?
    } else {
        // Используем Git репозиторий для анализа
        agent_manager.version_agent.suggest_version_from_repo(&git_repo, &current_version).await?
    };

    // Выводим результат
    print_version_analysis_result(&analysis, &current_version);

    // Если запрошено применение версии
    if command.apply {
        println!("🚀 Версия обновлена до: {}", analysis.suggested_version.green());
        // TODO: Здесь можно добавить логику применения версии (создание тега)
    }

    Ok(())
}

/// Обработчик команды release-notes
async fn handle_release_notes_command(
    _command: ReleaseNotesCommand,
    agent_manager: LLMAgentManager,
    git_repo: GitRepository,
) -> Result<()> {
    println!("📝 Генерация release notes с анализом Git");

    // Получаем информацию о последнем релизе
    let (analysis, _commits, latest_tag) = git_repo.get_changes_since_last_release().await?;

    // Определяем версию
    let version = if let Some(tag) = &latest_tag {
        // Предлагаем следующую версию
        git_repo.suggest_next_version(&tag.name).await?
    } else {
        "1.0.0".to_string() // Первая версия
    };

    // Генерируем changelog для release notes
    let changelog = git_repo.generate_changelog(latest_tag.as_ref().map(|t| t.name.as_str()), Some("HEAD")).await?;

    // Создаем информацию о плагине
    let plugin_info = PluginInfo {
        name: "Ride".to_string(), // TODO: Определять из конфигурации
        id: "ru.marslab.ide.ride".to_string(),
        version: version.clone(),
        description: Some("AI помощник для IntelliJ IDEA".to_string()),
    };

    // Генерируем release notes
    let release_notes = agent_manager.generate_release_notes(&version, &changelog, &plugin_info).await?;

    // Выводим результат
    print_release_notes_result(&release_notes, &analysis);

    // Если указан выходной файл, сохраняем результат
    if let Some(output_file) = &_command.output {
        save_release_notes_to_file(&release_notes, output_file)?;
        println!("💾 Release notes сохранены в файл: {}", output_file.green());
    }

    Ok(())
}

/// Выводит результат генерации changelog
fn print_changelog_result(changelog: &crate::core::llm::agents::GeneratedChangelog, verbose: bool) {
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

    if verbose {
        println!("📄 Полный changelog:");
        println!("{}", changelog.changelog);
    }

    println!("{}", "=".repeat(60).bright_black());
}

/// Выводит результат анализа версий
fn print_version_analysis_result(analysis: &crate::core::llm::agents::VersionAnalysis, current_version: &str) {
    println!("{}", "=".repeat(60).bright_black());
    println!("🔍 АНАЛИЗ ВЕРСИИ");
    println!("{}", "=".repeat(60).bright_black());

    println!("📋 Текущая версия: {}", current_version.bright_blue());
    println!("📈 Предлагаемая версия: {}", analysis.suggested_version.bright_green());
    println!("🎯 Уверенность: {:.1}%", analysis.confidence * 100.0);
    println!("📝 Обоснование: {}", analysis.reasoning);

    if !analysis.change_types.is_empty() {
        println!("📋 Типы изменений: {}", analysis.change_types.join(", "));
    }

    println!("{}", "=".repeat(60).bright_black());
}

/// Выводит результат генерации release notes
fn print_release_notes_result(
    notes: &crate::core::llm::agents::GeneratedReleaseNotes,
    analysis: &crate::git::ReleaseAnalysis,
) {
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

    // Добавляем статистику из анализа
    println!("\n📊 Статистика релиза:");
    println!("  • Всего коммитов: {}", analysis.total_commits);
    println!("  • Критических изменений: {}", analysis.breaking_changes.len());

    if !analysis.change_summary.is_empty() {
        println!("  • Типы изменений:");
        for (change_type, count) in &analysis.change_summary {
            println!("    - {}: {}", change_type.name(), count);
        }
    }

    println!("{}", "=".repeat(60).bright_black());
}

/// Сохраняет changelog в файл
fn save_changelog_to_file(changelog: &crate::core::llm::agents::GeneratedChangelog, file_path: &str) -> Result<()> {
    use std::fs;

    let content = format!("# CHANGELOG v{}\n\n{}", changelog.version, changelog.changelog);
    fs::write(file_path, content)
        .with_context(|| format!("Не удалось сохранить changelog в файл: {}", file_path))?;

    Ok(())
}

/// Сохраняет release notes в файл
fn save_release_notes_to_file(notes: &crate::core::llm::agents::GeneratedReleaseNotes, file_path: &str) -> Result<()> {
    use std::fs;

    let content = format!("# RELEASE NOTES v{}\n\n{}\n\n{}\n\n{}",
        notes.version,
        notes.title,
        notes.body,
        notes.subtitle);
    fs::write(file_path, content)
        .with_context(|| format!("Не удалось сохранить release notes в файл: {}", file_path))?;

    Ok(())
}