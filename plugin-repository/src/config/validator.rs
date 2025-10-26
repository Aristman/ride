use anyhow::{Context, Result};
use crate::config::parser::Config;
use tracing::info;

/// Валидатор конфигурации
pub struct ConfigValidator;

impl ConfigValidator {
    /// Полная валидация конфигурации
    pub fn validate(config: &Config) -> Result<()> {
        info!("Начало валидации конфигурации");

        // Валидация проекта
        Self::validate_project(&config.project)?;

        // Валидация сборки
        Self::validate_build(&config.build)?;

        // Валидация репозитория
        Self::validate_repository(&config.repository)?;

        // Валидация LLM конфигурации
        Self::validate_llm(&config.llm)?;

        // Валидация YandexGPT
        Self::validate_yandexgpt(&config.yandexgpt)?;

        // Валидация агентов
        Self::validate_agents(&config.llm_agents)?;

        // Валидация Git конфигурации
        Self::validate_git(&config.git)?;

        info!("Валидация конфигурации успешно завершена");
        Ok(())
    }

    fn validate_project(project: &crate::config::parser::ProjectConfig) -> Result<()> {
        if project.name.is_empty() {
            return Err(anyhow::anyhow!("Имя проекта не может быть пустым"));
        }

        if project.id.is_empty() {
            return Err(anyhow::anyhow!("ID проекта не может быть пустым"));
        }

        if !["intellij", "android-studio"].contains(&project.project_type.as_str()) {
            return Err(anyhow::anyhow!(
                "Тип проекта должен быть 'intellij' или 'android-studio'"
            ));
        }

        Ok(())
    }

    fn validate_build(build: &crate::config::parser::BuildConfig) -> Result<()> {
        if build.gradle_task.is_empty() {
            return Err(anyhow::anyhow!("Gradle задача не может быть пустой"));
        }

        if build.output_dir.is_empty() {
            return Err(anyhow::anyhow!("Директория вывода не может быть пустой"));
        }

        Ok(())
    }

    fn validate_repository(repository: &crate::config::parser::RepositoryConfig) -> Result<()> {
        if !repository.url.starts_with("http") {
            return Err(anyhow::anyhow!(
                "URL репозитория должен начинаться с http или https"
            ));
        }

        if repository.ssh_host.is_empty() {
            return Err(anyhow::anyhow!("SSH хост не может быть пустым"));
        }

        if repository.ssh_user.is_empty() {
            return Err(anyhow::anyhow!("SSH пользователь не может быть пустым"));
        }

        if repository.deploy_path.is_empty() {
            return Err(anyhow::anyhow!("Путь деплоя не может быть пустым"));
        }

        if repository.xml_path.is_empty() {
            return Err(anyhow::anyhow!("Путь к XML файлу не может быть пустым"));
        }

        Ok(())
    }

    fn validate_llm(llm: &crate::config::parser::LlmConfig) -> Result<()> {
        if !["yandexgpt", "openai", "anthropic"].contains(&llm.provider.as_str()) {
            return Err(anyhow::anyhow!(
                "LLM провайдер должен быть 'yandexgpt', 'openai' или 'anthropic'"
            ));
        }

        if llm.temperature < 0.0 || llm.temperature > 2.0 {
            return Err(anyhow::anyhow!(
                "Температура должна быть в диапазоне от 0.0 до 2.0"
            ));
        }

        if llm.max_tokens == 0 {
            return Err(anyhow::anyhow!("Максимальное количество токенов не может быть 0"));
        }

        Ok(())
    }

    fn validate_yandexgpt(yandexgpt: &crate::config::parser::YandexGptConfig) -> Result<()> {
        if yandexgpt.api_key.is_empty() {
            return Err(anyhow::anyhow!("API ключ YandexGPT не может быть пустым"));
        }

        if yandexgpt.folder_id.is_empty() {
            return Err(anyhow::anyhow!("Folder ID YandexGPT не может быть пустым"));
        }

        if !["yandexgpt", "yandexgpt-lite"].contains(&yandexgpt.model.as_str()) {
            return Err(anyhow::anyhow!(
                "Модель YandexGPT должна быть 'yandexgpt' или 'yandexgpt-lite'"
            ));
        }

        Ok(())
    }

    fn validate_agents(agents: &crate::config::parser::LlmAgentsConfig) -> Result<()> {
        let agent_configs = [
            (&agents.changelog_agent, "changelog_agent"),
            (&agents.version_agent, "version_agent"),
            (&agents.release_agent, "release_agent"),
        ];

        for (agent_config, name) in agent_configs {
            if agent_config.temperature < 0.0 || agent_config.temperature > 2.0 {
                return Err(anyhow::anyhow!(
                    "Температура для {} должна быть в диапазоне от 0.0 до 2.0",
                    name
                ));
            }
        }

        Ok(())
    }

    fn validate_git(git: &crate::config::parser::GitConfig) -> Result<()> {
        if git.main_branch.is_empty() {
            return Err(anyhow::anyhow!("Основная ветка не может быть пустой"));
        }

        Ok(())
    }
}