use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Основная конфигурация приложения
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfig {
    pub project: ProjectInfo,
    pub repository: RepositoryInfo,
    pub llm: LlmSettings,
    pub git: GitSettings,
}

/// Информация о проекте
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProjectInfo {
    pub name: String,
    pub id: String,
    pub r#type: ProjectType,
    pub version: Option<String>,
}

/// Тип проекта
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ProjectType {
    IntelliJ,
    AndroidStudio,
}

/// Информация о репозитории
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RepositoryInfo {
    pub url: String,
    pub ssh_host: String,
    pub ssh_user: String,
    pub ssh_private_key_path: Option<String>,
    pub deploy_path: String,
    pub xml_path: String,
}

/// Настройки LLM
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LlmSettings {
    pub provider: LlmProvider,
    pub temperature: f32,
    pub max_tokens: u32,
    pub agents: HashMap<String, AgentConfig>,
}

/// Провайдеры LLM
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum LlmProvider {
    YandexGpt,
    OpenAI,
    Anthropic,
}

/// Конфигурация агента
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentConfig {
    pub model: String,
    pub temperature: f32,
    pub max_tokens: Option<u32>,
}

/// Настройки Git
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GitSettings {
    pub main_branch: String,
    pub tag_prefix: String,
}