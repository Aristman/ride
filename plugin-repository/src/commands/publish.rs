use anyhow::{Context, Result};
use colored::*;
use tracing::{info, warn};

use crate::cli::publish::PublishCommand;
use crate::config::parser::Config;
use crate::core::builder::PluginBuilder;
use crate::core::deployer::Deployer;
use crate::core::releaser::ReleaseManager;
use crate::core::llm::agents::LLMAgentManager;
use crate::git::GitRepository;

/// Обработчик команды полного цикла публикации
pub async fn handle_publish_command(cmd: PublishCommand, config_file: &str) -> Result<()> {
    info!("🧩 Запуск полного цикла публикации");

    // 1) Загрузка и (опционально) валидация конфигурации
    let config = Config::load_from_file(config_file)
        .with_context(|| format!("Не удалось загрузить конфигурацию из файла: {}", config_file))?;
    if !cmd.skip_validation {
        config.validate().context("Валидация конфигурации не пройдена")?;
    }

    let project_root = std::env::current_dir().context("Не удалось определить текущую директорию")?;
    let git_repo = GitRepository::new(&project_root);
    if !git_repo.is_valid_repository() {
        anyhow::bail!("Текущая директория не является git репозиторием");
    }

    // 2) Определение версии
    let version = if let Some(v) = cmd.version.clone() {
        v
    } else if cmd.auto_version {
        let agent_manager = LLMAgentManager::from_config(&config)
            .context("Не удалось создать LLM агент менеджер")?;
        let releaser = ReleaseManager::new(git_repo.clone(), agent_manager, config.project.clone());
        let prep = releaser.prepare_release(None).await?;
        if !prep.success {
            anyhow::bail!("Подготовка релиза не удалась");
        }
        prep.release.version
    } else {
        anyhow::bail!("Не указана версия. Используйте --version или --auto-version");
    };

    println!("{} Версия: {}", "🏷️", version.bright_green());

    // 3) Сборка артефакта с заданной версией
    let builder = PluginBuilder::new(config.clone(), project_root.clone());
    let build_res = builder.build(Some(version.clone()), &cmd.profile).await?;
    if !build_res.success {
        anyhow::bail!("Сборка завершилась с ошибками");
    }
    println!("{} Сборка завершена", "✅");

    // 4) Создание и публикация релиза (если не dry-run)
    let agent_manager = LLMAgentManager::from_config(&config)
        .context("Не удалось создать LLM агент менеджер")?;
    let releaser = ReleaseManager::new(git_repo.clone(), agent_manager, config.project.clone());

    if cmd.dry_run {
        println!("{} DRY RUN — релиз и деплой пропущены", "🧪");
        return Ok(());
    }

    println!("{} Создание релиза...", "🚀");
    let _tag = releaser.create_release(&version, None).await?;
    println!("{} Релиз создан", "✅");

    println!("{} Публикация релиза...", "📤");
    releaser.publish_release(&version).await?;
    println!("{} Релиз опубликован", "✅");

    // 5) Деплой
    let deployer = Deployer::new(config.clone());
    if !cmd.skip_validation {
        if let Err(e) = deployer.validate().await {
            if cmd.force {
                warn!("Валидация перед деплоем не пройдена: {} (продолжаем из-за --force)", e);
            } else {
                anyhow::bail!("Валидация перед деплоем не пройдена: {}", e);
            }
        }
    }

    println!("{} Деплой...", "🚚");
    deployer.deploy(cmd.force, cmd.rollback_on_failure).await?;
    println!("{} Деплой завершен", "✅");

    Ok(())
}
