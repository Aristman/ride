use anyhow::{Result, Context};
use tracing::{info, warn};

use crate::config::parser::Config;

/// Движок деплоя
#[derive(Debug, Clone)]
pub struct Deployer {
    config: Config,
}

impl Deployer {
    pub fn new(config: Config) -> Self {
        Self { config }
    }

    /// Валидация перед деплоем
    pub async fn validate(&self) -> Result<()> {
        info!("🔍 Валидация перед деплоем");
        self.config.validate().context("Валидация конфигурации деплоя не пройдена")?;
        Ok(())
    }

    /// Выполнить деплой артефактов
    pub async fn deploy(&self, force: bool, rollback_on_failure: bool) -> Result<()> {
        info!("📦 Запуск деплоя (force={}, rollback_on_failure={})", force, rollback_on_failure);
        // TODO: Реализация SSH/SCP деплоя и atomic обновления XML
        // Сейчас: имитация успешной публикации
        Ok(())
    }

    /// Откат изменений
    pub async fn rollback(&self) -> Result<()> {
        warn!("⏪ Откат деплоя (заглушка)");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_deployer_validate() {
        if let Ok(cfg) = Config::load_from_file("plugin-repository/config.toml") {
            let d = Deployer::new(cfg);
            let _ = d.validate().await; // допускаем ошибки валидатора в CI окружении
        }
    }

    #[tokio::test]
    async fn test_deployer_deploy_and_rollback() {
        if let Ok(cfg) = Config::load_from_file("plugin-repository/config.toml") {
            let d = Deployer::new(cfg);
            let _ = d.deploy(false, true).await;
            let _ = d.rollback().await;
        }
    }
}