use anyhow::{Context, Result};
use colored::*;
use tracing::{info, warn};

use crate::cli::status::StatusCommand;
use crate::config::parser::Config;
use crate::core::releaser::ReleaseManager;
use crate::git::GitRepository;

/// Обработчик команды status
pub async fn handle_status_command(cmd: StatusCommand, config_file: &str) -> Result<()> {
    info!("📊 Запуск команды статуса");

    let config = Config::load_from_file(config_file)
        .with_context(|| format!("Не удалось загрузить конфигурацию из файла: {}", config_file))?;

    // Git repo из текущей директории
    let current_dir = std::env::current_dir().context("Не удалось получить текущую директорию")?;
    let git_repo = GitRepository::new(&current_dir);

    if cmd.repository || (!cmd.releases) {
        // Минимальная сводка по репозиторию
        let is_repo = git_repo.is_valid_repository();
        println!("{} Репозиторий: {}", "📁", if is_repo { "OK".green().to_string() } else { "NOT A GIT REPO".red().to_string() });
        if is_repo {
            if let Ok(branch) = git_repo.history.get_current_branch().await {
                println!("  • Текущая ветка: {}", branch.bright_blue());
            }
            if let Ok(mut tags) = git_repo.tags.get_all_tags().await {
                // берём только первые 5
                tags.truncate(5);
                println!("  • Теги: {}", tags.iter().map(|t| t.name.clone()).collect::<Vec<_>>().join(", "));
            }
        }
    }

    if cmd.releases {
        let agent_manager = crate::core::llm::agents::LLMAgentManager::from_config(&config)
            .with_context(|| "Не удалось создать LLM агент менеджер")?;
        let release_manager = ReleaseManager::new(git_repo.clone(), agent_manager, config.project.clone());
        match release_manager.get_release_history(Some(5)).await {
            Ok(list) => {
                println!("\n{} Последние релизы:", "🏷️");
                if cmd.format == "json" {
                    let json = serde_json::to_string_pretty(&list).unwrap_or_else(|_| "[]".to_string());
                    println!("{}", json);
                } else {
                    for (i, r) in list.iter().enumerate() {
                        println!("{}. {} ({})", i + 1, r.tag.bright_blue(), r.version.bright_green());
                    }
                }
            }
            Err(e) => {
                warn!("Не удалось получить историю релизов: {}", e);
            }
        }
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_handle_status_command_runs() {
        let cmd = StatusCommand { releases: true, repository: true, format: "table".to_string() };
        let _ = handle_status_command(cmd, "plugin-repository/config.toml").await;
    }
}
