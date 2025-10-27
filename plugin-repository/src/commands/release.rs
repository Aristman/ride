use anyhow::{Context, Result};
use tracing::{info, warn, error};
use colored::*;
use std::fs;

use crate::config::parser::Config;
use crate::cli::release::ReleaseCommand;
use crate::core::releaser::ReleaseManager;
use crate::git::GitRepository;
use crate::core::llm::agents::LLMAgentManager;

/// Обработчик команды release
pub async fn handle_release_command(
    command: ReleaseCommand,
    config_file: &str,
) -> Result<()> {
    info!("🚀 Запуск команды релиза");

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

    // Создаем менеджер релизов
    let release_manager = ReleaseManager::new(
        git_repo.clone(),
        agent_manager,
        config.project.clone(),
    );

    // Обрабатываем флаги
    if let Some(version) = command.rollback {
        return handle_rollback(&release_manager, &version, command.verbose).await;
    }

    if command.history {
        return handle_history(&release_manager, command.limit, command.verbose).await;
    }

    // Основной процесс релиза
    handle_release_process(&release_manager, command).await
}

/// Обработка основного процесса релиза
async fn handle_release_process(
    release_manager: &ReleaseManager,
    command: ReleaseCommand,
) -> Result<()> {
    info!("📋 Подготовка релиза");

    if command.verbose {
        println!("{} 🚀 Подготовка релиза", "=".repeat(60).bright_black());
    }

    // Подготавливаем релиз
    let preparation_result = release_manager.prepare_release(command.version.clone()).await?;

    // Отображаем результат подготовки
    display_preparation_result(&preparation_result, command.verbose);

    // Проверяем готовность
    if !preparation_result.success {
        error!("❌ Подготовка релиза завершилась с ошибками");
        return Err(anyhow::anyhow!("Подготовка релиза не удалась"));
    }

    // Валидация
    if !command.skip_validation && !preparation_result.validation_issues.is_empty() && !command.force {
        warn!("⚠️ Найдены проблемы валидации:");
        for issue in &preparation_result.validation_issues {
            println!("  • {}", issue.yellow());
        }

        if !command.dry_run {
            println!("\nИспользуйте --force для игнорирования или --skip-validation для пропуска валидации");
            return Err(anyhow::anyhow!("Валидация не пройдена"));
        }
    }

    // Dry run режим
    if command.dry_run {
        println!("\n🔍 DRY RUN MODE - релиз не будет создан");
        if command.verbose {
            println!("Используйте команду без --dry-run для создания реального релиза");
        }
        return Ok(());
    }

    // Создание релиза
    println!("\n🏷️ Создание релиза...");
    let tag_name = release_manager.create_release(&preparation_result.release.version, None).await?;

    println!("✅ Релиз {} создан", tag_name.green());

    // Сохранение файлов
    save_artifacts(&preparation_result, &command)?;

    // Публикация
    if !command.no_publish {
        println!("\n📤 Публикация релиза...");
        release_manager.publish_release(&preparation_result.release.version).await?;
        println!("✅ Релиз опубликован");
    } else {
        println!("📦 Релиз создан локально (опция --no-publish)");
    }

    // Финальное сообщение
    println!("\n{}", "=".repeat(60).bright_black());
    println!("🎉 Релиз {} успешно завершен!", preparation_result.release.version.green());
    println!("{}", "=".repeat(60).bright_black());

    Ok(())
}

/// Обработка отката релиза
async fn handle_rollback(
    release_manager: &ReleaseManager,
    version: &str,
    verbose: bool,
) -> Result<()> {
    warn!("⏪ Откат релиза v{}", version);

    if verbose {
        println!("🔍 Проверка существования релиза v{}", version);
    }

    release_manager.rollback_release(version).await?;

    println!("✅ Релиз v{} откачен", version.green());
    Ok(())
}

/// Обработка истории релизов
async fn handle_history(
    release_manager: &ReleaseManager,
    limit: usize,
    verbose: bool,
) -> Result<()> {
    info!("📚 Получение истории релизов");

    let releases = release_manager.get_release_history(Some(limit)).await?;

    if releases.is_empty() {
        println!("📭 Релизы не найдены");
        return Ok(());
    }

    println!("{}", "=".repeat(60).bright_black());
    println!("📚 ИСТОРИЯ РЕЛИЗОВ (последние {})", releases.len());
    println!("{}", "=".repeat(60).bright_black());

    for (index, release) in releases.iter().enumerate() {
        println!("\n{}. {} ({})",
            index + 1,
            release.tag.bright_blue(),
            release.version.bright_green()
        );

        if verbose {
            println!("   📅 Дата: {}", release.date.format("%Y-%m-%d %H:%M:%S"));
            println!("   📝 Коммит: {}", release.commit);
            if let Some(message) = &release.message {
                println!("   💬 Сообщение: {}", message);
            }
            println!("   📊 Изменений: {}", release.changes_count);
        }
    }

    println!("\n{}", "=".repeat(60).bright_black());
    Ok(())
}

/// Отображение результата подготовки релиза
fn display_preparation_result(result: &crate::core::releaser::ReleasePreparationResult, verbose: bool) {
    println!("\n{}", "=".repeat(60).bright_black());
    println!("📋 ПОДГОТОВКА РЕЛИЗА v{}", result.release.version.bright_green());
    println!("{}", "=".repeat(60).bright_black());

    println!("📈 Версия: {} ({:?})", result.release.version.bright_green(), result.release.version_type);
    println!("📊 Изменений: {}", result.release.changes_count);
    println!("💥 Критических: {}", result.release.breaking_changes);
    println!("📅 Дата: {}", result.release.estimated_release_date.format("%Y-%m-%d %H:%M:%S"));

    if verbose {
        // Отображаем release notes
        if let Some(notes) = &result.release.release_notes {
            println!("\n📝 RELEASE NOTES:");
            println!("{}", "-".repeat(40).bright_black());
            println!("{}", notes);
        }

        // Отображаем changelog
        if let Some(changelog) = &result.release.changelog {
            println!("\n📋 CHANGELOG:");
            println!("{}", "-".repeat(40).bright_black());
            println!("{}", changelog);
        }
    }

    // Отображаем предупреждения
    if !result.warnings.is_empty() {
        println!("\n⚠️ ПРЕДУПРЕЖДЕНИЯ:");
        for warning in &result.warnings {
            println!("  • {}", warning.yellow());
        }
    }

    // Отображаем ошибки
    if !result.errors.is_empty() {
        println!("\n❌ ОШИБКИ:");
        for error in &result.errors {
            println!("  • {}", error.red());
        }
    }

    // Отображаем проблемы валидации
    if !result.validation_issues.is_empty() {
        println!("\n🔍 ПРОБЛЕМЫ ВАЛИДАЦИИ:");
        for issue in &result.validation_issues {
            println!("  • {}", issue.bright_yellow());
        }
    }

    println!("{}", "=".repeat(60).bright_black());
}

/// Сохранение артефактов релиза
fn save_artifacts(
    result: &crate::core::releaser::ReleasePreparationResult,
    command: &ReleaseCommand,
) -> Result<()> {
    // Сохраняем release notes
    if let Some(file_path) = &command.save_notes {
        if let Some(notes) = &result.release.release_notes {
            fs::write(file_path, notes)
                .with_context(|| format!("Не удалось сохранить release notes в файл: {}", file_path))?;
            println!("💾 Release notes сохранены: {}", file_path.green());
        }
    }

    // Сохраняем changelog
    if let Some(file_path) = &command.save_changelog {
        if let Some(changelog) = &result.release.changelog {
            let content = format!("# CHANGELOG v{}\n\n{}", result.release.version, changelog);
            fs::write(file_path, content)
                .with_context(|| format!("Не удалось сохранить changelog в файл: {}", file_path))?;
            println!("💾 Changelog сохранен: {}", file_path.green());
        }
    }

    Ok(())
}