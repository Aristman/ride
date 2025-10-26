use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use tracing::{info, debug, error, warn};
use super::yandexgpt::{YandexGPTClient, YandexGPTConfig, YandexGPTClientFactory};
use super::prompts::*;

/// Базовый трейт для LLM агентов
pub trait LLMAgent {
    async fn generate_response(&self, input: &str) -> Result<String>;
    fn get_agent_name(&self) -> &'static str;
}

/// Информация о версии для анализа
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionInfo {
    pub current_version: String,
    pub new_version: Option<String>,
    pub branch: String,
    pub git_log: Option<String>,
    pub changes_count: usize,
}

/// Результат анализа версий
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionAnalysis {
    pub suggested_version: String,
    pub reasoning: String,
    pub confidence: f32,
    pub change_types: Vec<String>,
}

/// Changelog сгенерированный AI
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GeneratedChangelog {
    pub version: String,
    pub changelog: String,
    pub sections: Vec<ChangelogSection>,
    pub total_changes: usize,
}

/// Секция changelog
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChangelogSection {
    pub title: String,
    pub changes: Vec<String>,
    pub emoji: String,
}

/// Release notes сгенерированные AI
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GeneratedReleaseNotes {
    pub title: String,
    pub subtitle: String,
    pub highlights: Vec<String>,
    pub body: String,
    pub version: String,
}

/// Агент для генерации changelog
pub struct ChangelogAgent {
    client: YandexGPTClient,
    cache: HashMap<String, String>,
}

impl ChangelogAgent {
    pub fn new(client: YandexGPTClient) -> Self {
        Self {
            client,
            cache: HashMap::new(),
        }
    }

    /// Генерирует changelog на основе git истории
    pub async fn generate_changelog(&self, version_info: &VersionInfo) -> Result<GeneratedChangelog> {
        info!("🤖 Генерация changelog для версии {:?}", version_info.new_version);

        let git_log = version_info.git_log.as_deref().unwrap_or("Нет доступной истории изменений");

        let prompt = CHANGELOG_PROMPT
            .replace("{new_version}", &version_info.new_version.as_deref().unwrap_or("unknown"))
            .replace("{old_version}", &version_info.current_version)
            .replace("{branch}", &version_info.branch)
            .replace("{git_log}", git_log);

        debug!("Отправка промпта в YandexGPT: {}", &prompt[..prompt.len().min(200)]);

        let response = self.client.chat_completion_with_retry(&prompt, 3).await
            .context("Ошибка генерации changelog")?;

        // Парсим ответ на секции
        let sections = self.parse_changelog_sections(&response);
        let total_changes = sections.iter().map(|s| s.changes.len()).sum();

        Ok(GeneratedChangelog {
            version: version_info.new_version.clone().unwrap_or_else(|| "unknown".to_string()),
            changelog: response.clone(),
            sections,
            total_changes,
        })
    }

    /// Парсит changelog на секции
    fn parse_changelog_sections(&self, changelog: &str) -> Vec<ChangelogSection> {
        let mut sections = Vec::new();
        let mut current_section = None;
        let mut current_changes: Vec<String> = Vec::new();

        for line in changelog.lines() {
            let line = line.trim();

            // Определяем секции по эмодзи и заголовкам
            if line.starts_with("🚀") || line.contains("Новые возможности") || line.contains("Новые функции") {
                if let Some(section) = current_section.take() {
                    sections.push(section);
                }
                current_changes.clear();
                current_section = Some(ChangelogSection {
                    title: "🚀 Новые возможности".to_string(),
                    changes: Vec::new(),
                    emoji: "🚀".to_string(),
                });
            } else if line.starts_with("🐛") || line.contains("Исправления") {
                if let Some(section) = current_section.take() {
                    sections.push(section);
                }
                current_changes.clear();
                current_section = Some(ChangelogSection {
                    title: "🐛 Исправления".to_string(),
                    changes: Vec::new(),
                    emoji: "🐛".to_string(),
                });
            } else if line.starts_with("🔧") || line.contains("Улучшения") {
                if let Some(section) = current_section.take() {
                    sections.push(section);
                }
                current_changes.clear();
                current_section = Some(ChangelogSection {
                    title: "🔧 Улучшения".to_string(),
                    changes: Vec::new(),
                    emoji: "🔧".to_string(),
                });
            } else if line.starts_with("💥") || line.contains("Критические изменения") {
                if let Some(section) = current_section.take() {
                    sections.push(section);
                }
                current_changes.clear();
                current_section = Some(ChangelogSection {
                    title: "💥 Критические изменения".to_string(),
                    changes: Vec::new(),
                    emoji: "💥".to_string(),
                });
            } else if line.starts_with("- ") || line.starts_with("* ") {
                let change = line.strip_prefix("- ").unwrap_or(line.strip_prefix("* ").unwrap_or(line)).to_string();
                if let Some(ref mut section) = current_section {
                    section.changes.push(change);
                }
            }
        }

        if let Some(section) = current_section {
            sections.push(section);
        }

        // Если секции не определены, создаем общую секцию
        if sections.is_empty() {
            sections.push(ChangelogSection {
                title: "📋 Изменения".to_string(),
                changes: changelog.lines()
                    .filter(|line| line.trim().starts_with("- ") || line.trim().starts_with("* "))
                    .map(|line| line.trim().strip_prefix("- ").unwrap_or(line.strip_prefix("* ").unwrap_or(line)).to_string())
                    .collect(),
                emoji: "📋".to_string(),
            });
        }

        sections
    }
}

impl LLMAgent for ChangelogAgent {
    async fn generate_response(&self, input: &str) -> Result<String> {
        self.client.chat_completion_with_retry(input, 3).await
    }

    fn get_agent_name(&self) -> &'static str {
        "ChangelogAgent"
    }
}

/// Агент для анализа версий
pub struct VersionAgent {
    client: YandexGPTClient,
    cache: HashMap<String, String>,
}

impl VersionAgent {
    pub fn new(client: YandexGPTClient) -> Self {
        Self {
            client,
            cache: HashMap::new(),
        }
    }

    /// Анализирует изменения и предлагает версию
    pub async fn suggest_version(&self, version_info: &VersionInfo) -> Result<VersionAnalysis> {
        info!("🤖 Анализ изменений для предложения версии");

        let git_log = version_info.git_log.as_deref().unwrap_or("Нет доступной истории изменений");

        let prompt = VERSION_PROMPT
            .replace("{current_version}", &version_info.current_version)
            .replace("{change_types}", &self.analyze_change_types(git_log))
            .replace("{breaking_changes}", &self.count_breaking_changes(git_log).to_string());

        debug!("Отправка промпта в YandexGPT: {}", &prompt[..prompt.len().min(200)]);

        let response = self.client.chat_completion_with_retry(&prompt, 3).await
            .context("Ошибка анализа версий")?;

        // Парсим ответ: "1.2.3: обоснование"
        if let Some(colon_pos) = response.find(':') {
            let version = response[..colon_pos].trim().to_string();
            let reasoning = response[colon_pos + 1..].trim().to_string();

            Ok(VersionAnalysis {
                suggested_version: version,
                reasoning,
                confidence: 0.8, // TODO: Улучшить анализ уверенности
                change_types: self.extract_change_types(git_log),
            })
        } else {
            Ok(VersionAnalysis {
                suggested_version: "1.0.0".to_string(),
                reasoning: response,
                confidence: 0.5,
                change_types: vec!["other".to_string()],
            })
        }
    }

    /// Анализирует типы изменений в git логе
    fn analyze_change_types(&self, git_log: &str) -> String {
        let mut types = Vec::new();

        if git_log.to_lowercase().contains("feat") || git_log.to_lowercase().contains("добавлен") {
            types.push("features");
        }
        if git_log.to_lowercase().contains("fix") || git_log.to_lowercase().contains("исправлен") {
            types.push("fixes");
        }
        if git_log.to_lowercase().contains("break") || git_log.to_lowercase().contains("breaking") {
            types.push("breaking_changes");
        }

        types.join(", ")
    }

    /// Считает критические изменения
    fn count_breaking_changes(&self, git_log: &str) -> usize {
        git_log.to_lowercase().matches("break").count() +
        git_log.to_lowercase().matches("breaking").count()
    }

    /// Извлекает типы изменений
    fn extract_change_types(&self, git_log: &str) -> Vec<String> {
        let mut types = Vec::new();

        if git_log.to_lowercase().contains("feat") {
            types.push("feature".to_string());
        }
        if git_log.to_lowercase().contains("fix") {
            types.push("fix".to_string());
        }
        if git_log.to_lowercase().contains("break") || git_log.to_lowercase().contains("breaking") {
            types.push("breaking".to_string());
        }
        if types.is_empty() {
            types.push("other".to_string());
        }

        types
    }
}

impl LLMAgent for VersionAgent {
    async fn generate_response(&self, input: &str) -> Result<String> {
        self.client.chat_completion_with_retry(input, 3).await
    }

    fn get_agent_name(&self) -> &'static str {
        "VersionAgent"
    }
}

/// Агент для генерации release notes
pub struct ReleaseAgent {
    client: YandexGPTClient,
    cache: HashMap<String, String>,
}

impl ReleaseAgent {
    pub fn new(client: YandexGPTClient) -> Self {
        Self {
            client,
            cache: HashMap::new(),
        }
    }

    /// Генерирует release notes
    pub async fn generate_release_notes(&self, version: &str, changelog: &str, plugin_info: &PluginInfo) -> Result<GeneratedReleaseNotes> {
        info!("🤖 Генерация release notes для версии {}", version);

        let prompt = RELEASE_NOTES_PROMPT
            .replace("{plugin_name}", &plugin_info.name)
            .replace("{plugin_id}", &plugin_info.id)
            .replace("{version}", version)
            .replace("{changelog}", changelog);

        debug!("Отправка промпта в YandexGPT: {}", &prompt[..prompt.len().min(200)]);

        let response = self.client.chat_completion_with_retry(&prompt, 3).await
            .context("Ошибка генерации release notes")?;

        // Парсим ответ на структуру
        let (title, highlights, body) = self.parse_release_notes(&response);

        Ok(GeneratedReleaseNotes {
            title,
            subtitle: format!("Версия {} теперь доступна!", version),
            highlights,
            body,
            version: version.to_string(),
        })
    }

    /// Парсит release notes на компоненты
    fn parse_release_notes(&self, notes: &str) -> (String, Vec<String>, String) {
        let mut title = format!("Вышла новая версия плагина");
        let mut highlights = Vec::new();
        let mut body_lines = Vec::new();
        let mut _in_highlights = false;

        for line in notes.lines() {
            let line = line.trim();

            if line.starts_with("#") || line.contains("🎉") || line.contains("🚀") {
                title = line.replace("#", "").replace("🎉", "").replace("🚀", "").trim().to_string();
            } else if line.starts_with("•") || line.starts_with("-") || line.starts_with("*") {
                highlights.push(line.trim_start_matches(&['•', '-', '*'][..]).trim().to_string());
                _in_highlights = true;
            } else if line.is_empty() {
                _in_highlights = false;
            } else {
                body_lines.push(line);
            }
        }

        if title.is_empty() {
            title = "🎉 Вышла новая версия плагина".to_string();
        }

        (title, highlights, body_lines.join("\n"))
    }
}

impl LLMAgent for ReleaseAgent {
    async fn generate_response(&self, input: &str) -> Result<String> {
        self.client.chat_completion_with_retry(input, 3).await
    }

    fn get_agent_name(&self) -> &'static str {
        "ReleaseAgent"
    }
}

/// Информация о плагине для генерации контента
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginInfo {
    pub name: String,
    pub id: String,
    pub version: String,
    pub description: Option<String>,
}

/// Менеджер LLM агентов
pub struct LLMAgentManager {
    changelog_agent: ChangelogAgent,
    version_agent: VersionAgent,
    release_agent: ReleaseAgent,
}

impl LLMAgentManager {
    /// Создает менеджер агентов из конфигурации
    pub fn from_config(config: &crate::config::parser::Config) -> Result<Self> {
        let yandex_config = YandexGPTConfig {
            api_key: config.yandexgpt.api_key.clone(),
            folder_id: config.yandexgpt.folder_id.clone(),
            model: config.yandexgpt.model.clone(),
            temperature: 0.3,
            max_tokens: 2000,
            timeout: std::time::Duration::from_secs(30),
        };

        let client = YandexGPTClient::new(yandex_config);

        Ok(Self {
            changelog_agent: ChangelogAgent::new(client.clone()),
            version_agent: VersionAgent::new(client.clone()),
            release_agent: ReleaseAgent::new(client),
        })
    }

    /// Создает менеджер из переменных окружения
    pub fn from_env() -> Result<Self> {
        let client = YandexGPTClientFactory::from_env()?;

        Ok(Self {
            changelog_agent: ChangelogAgent::new(client.clone()),
            version_agent: VersionAgent::new(client.clone()),
            release_agent: ReleaseAgent::new(client),
        })
    }

    /// Генерирует changelog
    pub async fn generate_changelog(&self, version_info: &VersionInfo) -> Result<GeneratedChangelog> {
        self.changelog_agent.generate_changelog(version_info).await
    }

    /// Предлагает версию
    pub async fn suggest_version(&self, version_info: &VersionInfo) -> Result<VersionAnalysis> {
        self.version_agent.suggest_version(version_info).await
    }

    /// Генерирует release notes
    pub async fn generate_release_notes(&self, version: &str, changelog: &str, plugin_info: &PluginInfo) -> Result<GeneratedReleaseNotes> {
        self.release_agent.generate_release_notes(version, changelog, plugin_info).await
    }

    /// Проверяет доступность всех агентов
    pub async fn health_check(&self) -> Result<bool> {
        match self.changelog_agent.client.health_check().await {
            Ok(true) => {
                info!("✅ Все LLM агенты доступны");
                Ok(true)
            }
            Ok(false) => {
                warn!("⚠️ LLM агенты недоступны");
                Ok(false)
            }
            Err(e) => {
                error!("❌ Ошибка проверки доступности LLM агентов: {}", e);
                Ok(false)
            }
        }
    }
}