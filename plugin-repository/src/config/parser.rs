use anyhow::{Context, Result};
use serde::Deserialize;
use std::collections::HashMap;
use std::fs;
use tracing::info;

/// Структура для хранения конфигурации
#[derive(Debug, Deserialize, Clone)]
pub struct Config {
    pub project: ProjectConfig,
    pub build: BuildConfig,
    pub repository: RepositoryConfig,
    pub llm: LlmConfig,
    pub yandexgpt: YandexGptConfig,
    #[serde(default)]
    pub openai: Option<OpenAiConfig>,
    #[serde(default)]
    pub anthropic: Option<AnthropicConfig>,
    pub llm_agents: LlmAgentsConfig,
    pub git: GitConfig,
}

#[derive(Debug, Deserialize, Clone)]
pub struct ProjectConfig {
    pub name: String,
    pub id: String,
    #[serde(rename = "type")]
    pub project_type: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct BuildConfig {
    #[serde(rename = "gradle_task")]
    pub gradle_task: String,
    #[serde(rename = "output_dir")]
    pub output_dir: String,
    #[serde(default)]
    pub build_args: Vec<String>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct RepositoryConfig {
    pub url: String,
    #[serde(rename = "ssh_host")]
    pub ssh_host: String,
    #[serde(rename = "ssh_user")]
    pub ssh_user: String,
    #[serde(rename = "ssh_private_key_path")]
    pub ssh_private_key_path: Option<String>,
    #[serde(rename = "deploy_path")]
    pub deploy_path: String,
    #[serde(rename = "xml_path")]
    pub xml_path: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LlmConfig {
    pub provider: String,
    pub temperature: f32,
    #[serde(rename = "max_tokens")]
    pub max_tokens: u32,
}

#[derive(Debug, Deserialize, Clone)]
pub struct YandexGptConfig {
    #[serde(rename = "api_key")]
    pub api_key: String,
    #[serde(rename = "folder_id")]
    pub folder_id: String,
    pub model: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct OpenAiConfig {
    #[serde(rename = "api_key")]
    pub api_key: String,
    pub model: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct AnthropicConfig {
    #[serde(rename = "api_key")]
    pub api_key: String,
    pub model: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct LlmAgentsConfig {
    #[serde(rename = "changelog_agent")]
    pub changelog_agent: AgentConfig,
    #[serde(rename = "version_agent")]
    pub version_agent: AgentConfig,
    #[serde(rename = "release_agent")]
    pub release_agent: AgentConfig,
}

#[derive(Debug, Deserialize, Clone)]
pub struct AgentConfig {
    pub model: String,
    pub temperature: f32,
}

#[derive(Debug, Deserialize, Clone)]
pub struct GitConfig {
    #[serde(rename = "main_branch")]
    pub main_branch: String,
    #[serde(rename = "tag_prefix")]
    pub tag_prefix: String,
}

impl Config {
    /// Загружает конфигурацию из TOML файла с подстановкой переменных окружения
    pub fn load_from_file(file_path: &str) -> Result<Self> {
        info!("Загрузка конфигурации из файла: {}", file_path);

        let content = fs::read_to_string(file_path)
            .with_context(|| format!("Не удалось прочитать файл конфигурации: {}", file_path))?;

        // Подстановка переменных окружения
        let processed_content = Self::substitute_env_vars(&content);

        let config: Config = toml::from_str(&processed_content)
            .with_context(|| "Ошибка парсинга TOML конфигурации")?;

        info!("Конфигурация успешно загружена");
        Ok(config)
    }

    /// Подставляет переменные окружения в формате ${VAR_NAME}
    fn substitute_env_vars(content: &str) -> String {
        let mut result = content.to_string();

        // Используем регулярное выражение для поиска ${VAR_NAME}
        let re = regex::Regex::new(r"\$\{([^}]+)\}").unwrap();

        result = re.replace_all(&result, |caps: &regex::Captures| {
            let var_name = &caps[1];
            std::env::var(var_name).unwrap_or_else(|_| {
                tracing::warn!("Переменная окружения не найдена: {}", var_name);
                format!("${{{}}}", var_name) // Оставляем как есть, если переменная не найдена
            })
        }).to_string();

        result
    }

    /// Валидирует конфигурацию
    pub fn validate(&self) -> Result<()> {
        // Проверка основных полей
        if self.project.name.is_empty() {
            return Err(anyhow::anyhow!("Имя проекта не может быть пустым"));
        }

        if self.project.id.is_empty() {
            return Err(anyhow::anyhow!("ID проекта не может быть пустым"));
        }

        // Проверка URL репозитория
        if !self.repository.url.starts_with("http") {
            return Err(anyhow::anyhow!("URL репозитория должен начинаться с http/https"));
        }

        info!("Валидация конфигурации пройдена успешно");
        Ok(())
    }
}