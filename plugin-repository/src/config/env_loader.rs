use anyhow::{Context, Result};
use std::env;
use tracing::{info, warn};

/// Загрузчик переменных окружения из .env файла
pub struct EnvLoader;

impl EnvLoader {
    /// Загружает переменные окружения из .env файла
    pub fn load() -> Result<()> {
        match dotenv::dotenv() {
            Ok(path) => {
                info!("Загружен .env файл: {}", path.display());
                Ok(())
            }
            Err(e) => {
                warn!("Не удалось загрузить .env файл: {}. Используем переменные окружения системы.", e);
                Ok(())
            }
        }
    }

    /// Проверяет наличие всех необходимых переменных окружения
    pub fn validate_required_vars() -> Result<()> {
        let required_vars = vec![
            "REPOSITORY_URL",
            "SSH_HOST",
            "SSH_USER",
            "DEPLOY_PATH",
            "XML_PATH",
            "DEPLOY_PLUGIN_YANDEX_API_KEY",
            "DEPLOY_PLUGIN_YANDEX_FOLDER_ID",
        ];

        let missing_vars: Vec<&str> = required_vars
            .into_iter()
            .filter(|var| env::var(var).is_err())
            .collect();

        if !missing_vars.is_empty() {
            return Err(anyhow::anyhow!(
                "Отсутствуют необходимые переменные окружения: {}",
                missing_vars.join(", ")
            ));
        }

        info!("Все необходимые переменные окружения найдены");
        Ok(())
    }

    /// Получает переменную окружения или возвращает ошибку
    pub fn get_required_var(key: &str) -> Result<String> {
        env::var(key).with_context(|| format!("Отсутствует переменная окружения: {}", key))
    }

    /// Получает переменную окружения или значение по умолчанию
    pub fn get_var_or_default(key: &str, default: &str) -> String {
        env::var(key).unwrap_or_else(|_| default.to_string())
    }
}