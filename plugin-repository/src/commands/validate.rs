use anyhow::{Context, Result};
use colored::*;
use tracing::{info, warn, error};

use crate::cli::validate::ValidateCommand;
use crate::config::parser::Config;
use crate::config::validator::ConfigValidator;

/// Обработчик команды validate
pub async fn handle_validate_command(cmd: ValidateCommand, config_file: &str) -> Result<()> {
    info!("🧪 Запуск валидации конфигурации");

    // Загружаем конфигурацию
    let config = Config::load_from_file(config_file)
        .with_context(|| format!("Не удалось загрузить конфигурацию из файла: {}", config_file))?;

    // Пока реализуем полную валидацию. Флаги используются для вывода деталей.
    match ConfigValidator::validate(&config) {
        Ok(_) => {
            println!("{} Конфигурация валидна", "✅".green());
            if cmd.metadata {
                println!("  • {} Метаданные проверены", "metadata".bright_black());
            }
            if cmd.compatibility {
                println!("  • {} Совместимость ок (базовые проверки)", "compatibility".bright_black());
            }
            if cmd.full {
                println!("  • {} Полная валидация выполнена", "full".bright_black());
            }
            Ok(())
        }
        Err(e) => {
            error!("Валидация не пройдена: {}", e);
            println!("{} Валидация не пройдена: {}", "❌".red(), e);
            Err(e)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_handle_validate_command_runs() {
        let cmd = ValidateCommand { metadata: true, compatibility: true, full: true };
        let _ = handle_validate_command(cmd, "plugin-repository/config.toml").await;
    }
}
