use anyhow::{Context, Result};
use tracing::{info, warn, error};
use crate::cli::deploy::DeployCommand;
use crate::config::parser::Config;
use crate::core::deployer::Deployer;

/// Обработчик команды deploy
pub async fn handle_deploy_command(
    command: DeployCommand,
    config_file: &str,
) -> Result<()> {
    info!("📦 Запуск команды деплоя");

    // Загружаем конфигурацию
    let config = Config::load_from_file(config_file)
        .with_context(|| format!("Не удалось загрузить конфигурацию из файла: {}", config_file))?;

    let deployer = Deployer::new(config.clone());

    // Валидация
    if !command.skip_validation {
        if let Err(e) = deployer.validate().await {
            error!("Валидация перед деплоем не пройдена: {}", e);
            if !command.force {
                warn!("Используйте --force для игнорирования валидации");
                return Err(anyhow::anyhow!("Валидация не пройдена"));
            }
            warn!("Продолжаем с --force, несмотря на ошибки валидации");
        }
    }

    // Выполняем деплой
    if let Err(e) = deployer.deploy(command.force, command.rollback_on_failure).await {
        error!("Ошибка деплоя: {}", e);
        if command.rollback_on_failure {
            warn!("Пробуем откатить изменения...");
            let _ = deployer.rollback().await;
        }
        return Err(e);
    }

    info!("✅ Деплой завершен");
    Ok(())
}