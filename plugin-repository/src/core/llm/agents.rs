use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use tracing::{info, debug, error, warn};
use super::yandexgpt::{YandexGPTClient, YandexGPTConfig, YandexGPTClientFactory};
use super::prompts::*;
use crate::git::{GitRepository, GitCommit, ReleaseAnalysis, ChangeType};

#[inline]
fn preview(s: &str, n: usize) -> String {
    s.chars().take(n).collect::<String>()
}

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

        debug!("Отправка промпта в YandexGPT: {}", preview(&prompt, 200));

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

    /// Генерирует changelog на основе GitRepository анализа
    pub async fn generate_changelog_from_repo(&self, repo: &GitRepository, from_tag: Option<&str>, to_tag: Option<&str>) -> Result<GeneratedChangelog> {
        info!("🤖 Генерация changelog на основе анализа репозитория");

        let (_, commits) = repo.get_full_analysis(from_tag, to_tag).await?;
        let version = to_tag.unwrap_or("HEAD").to_string();

        // Формируем git лог из коммитов
        let git_log = commits.iter()
            .map(|commit| format!("{}: {}", commit.short_hash, commit.message))
            .collect::<Vec<_>>()
            .join("\n");

        let old_version = from_tag.unwrap_or("previous").to_string();
        let branch = if repo.history.is_git_repository() {
            repo.history.get_current_branch().await.unwrap_or_else(|_| "main".to_string())
        } else {
            "main".to_string()
        };

        let version_info = VersionInfo {
            current_version: old_version,
            new_version: Some(version),
            branch,
            git_log: Some(git_log),
            changes_count: commits.len(),
        };

        self.generate_changelog(&version_info).await
    }

    /// Генерирует улучшенный changelog с учетом анализа типов изменений
    pub async fn generate_enhanced_changelog(&self, repo: &GitRepository, analysis: &ReleaseAnalysis) -> Result<GeneratedChangelog> {
        info!("🤖 Генерация улучшенного changelog с учетом анализа");

        // Получаем детальную информацию о коммитах
        let commits = repo.history.get_recent_commits(50).await?;

        // Группируем коммиты по типам изменений
        let mut grouped_commits: HashMap<ChangeType, Vec<&GitCommit>> = HashMap::new();
        for commit in &commits {
            let change_type = ChangeType::from_message(&commit.message);
            grouped_commits.entry(change_type).or_insert_with(Vec::new).push(commit);
        }

        // Создаем структурированный changelog
        let mut changelog_content = String::new();
        let mut sections = Vec::new();
        let mut total_changes = 0;

        // Заголовок
        let version = &analysis.version_to.as_deref().unwrap_or("latest");
        changelog_content.push_str(&format!("## Изменения {}\n\n", version));

        // Секции изменений в правильном порядке
        let section_order = [
            (ChangeType::Breaking, "💥", "Критические изменения"),
            (ChangeType::Feature, "🚀", "Новые возможности"),
            (ChangeType::Fix, "🐛", "Исправления"),
            (ChangeType::Improvement, "🔧", "Улучшения"),
            (ChangeType::Refactoring, "♻️", "Рефакторинг"),
            (ChangeType::Documentation, "📝", "Документация"),
            (ChangeType::Testing, "🧪", "Тестирование"),
            (ChangeType::Chore, "🧹", "Обслуживание"),
            (ChangeType::Other, "📋", "Другое"),
        ];

        for (change_type, emoji, title) in &section_order {
            if let Some(commits_of_type) = grouped_commits.get(change_type) {
                if !commits_of_type.is_empty() {
                    let section_title = format!("{} {}", emoji, title);
                    changelog_content.push_str(&format!("### {}\n\n", section_title));

                    let mut changes = Vec::new();
                    for commit in commits_of_type {
                        let change_desc = format!("- {} ({}): {}",
                            commit.short_hash,
                            commit.date.format("%Y-%m-%d"),
                            commit.message);
                        changelog_content.push_str(&change_desc);
                        changelog_content.push('\n');

                        changes.push(commit.message.clone());
                    }
                    changelog_content.push('\n');

                    sections.push(ChangelogSection {
                        title: section_title,
                        changes,
                        emoji: emoji.to_string(),
                    });

                    total_changes += commits_of_type.len();
                }
            }
        }

        // Добавляем статистику
        changelog_content.push_str("---\n");
        changelog_content.push_str(&format!("**Статистика:** {} коммитов\n", analysis.total_commits));

        for (change_type, count) in &analysis.change_summary {
            changelog_content.push_str(&format!("- {}: {}\n", change_type.name(), count));
        }

        if !analysis.breaking_changes.is_empty() {
            changelog_content.push_str(&format!("\n**⚠️ Критические изменения:** {}\n", analysis.breaking_changes.len()));
        }

        Ok(GeneratedChangelog {
            version: analysis.version_to.as_deref().unwrap_or("latest").to_string(),
            changelog: changelog_content,
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

        debug!("Отправка промпта в YandexGPT: {}", preview(&prompt, 200));

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

    /// Предлагает версию на основе анализа репозитория
    pub async fn suggest_version_from_repo(&self, repo: &GitRepository, current_version: &str) -> Result<VersionAnalysis> {
        info!("🤖 Предложение версии на основе анализа репозитория");

        let (analysis, commits, latest_tag) = repo.get_changes_since_last_release().await?;

        // Используем встроенную логику версионирования как основу
        let suggested_version = repo.suggest_next_version(current_version).await?;

        // Получаем git лог для LLM анализа
        let git_log = commits.iter()
            .map(|commit| format!("{}: {}", commit.short_hash, commit.message))
            .collect::<Vec<_>>()
            .join("\n");

        // Создаем промпт для LLM с контекстом
        let change_types = analysis.change_summary.keys()
            .map(|ct| format!("{:?}", ct))
            .collect::<Vec<_>>()
            .join(", ");

        let prompt = VERSION_PROMPT
            .replace("{current_version}", current_version)
            .replace("{change_types}", &change_types)
            .replace("{breaking_changes}", &analysis.breaking_changes.len().to_string());

        debug!("Отправка промпта в YandexGPT для версионного анализа");

        let response = self.client.chat_completion_with_retry(&prompt, 2).await
            .context("Ошибка LLM анализа версий")?;

        // Комбинируем результат LLM с анализом репозитория
        let reasoning = format!("Анализ на основе {} коммитов. {}",
            analysis.total_commits,
            response.trim());

        Ok(VersionAnalysis {
            suggested_version,
            reasoning,
            confidence: analysis.confidence,
            change_types: analysis.change_summary.keys()
                .map(|ct| format!("{:?}", ct))
                .collect(),
        })
    }

    /// Предлагает версию с учетом семантического анализа
    pub async fn suggest_semantic_version(&self, repo: &GitRepository, current_version: &str) -> Result<VersionAnalysis> {
        info!("🤖 Семантический анализ версий");

        let (analysis, _, _) = repo.get_changes_since_last_release().await?;

        // Определяем тип изменения на основе анализа
        let recommended_bump = &analysis.recommended_version_bump;

        let (suggested_version, reasoning) = match recommended_bump {
            crate::git::VersionBump::Major => {
                let version = self.increment_major(current_version);
                let reason = format!("Обнаружены {} критических изменений. Требуется обновление MAJOR версии.",
                    analysis.breaking_changes.len());
                (version, reason)
            }
            crate::git::VersionBump::Minor => {
                let version = self.increment_minor(current_version);
                let features_count = analysis.change_summary.get(&ChangeType::Feature).unwrap_or(&0);
                let reason = format!("Добавлено {} новых функций. Рекомендуется обновление MINOR версии.",
                    features_count);
                (version, reason)
            }
            crate::git::VersionBump::Patch => {
                let version = self.increment_patch(current_version);
                let fixes_count = analysis.change_summary.get(&ChangeType::Fix).unwrap_or(&0);
                let reason = format!("Исправлено {} ошибок. Достаточно обновления PATCH версии.",
                    fixes_count);
                (version, reason)
            }
            crate::git::VersionBump::Custom(ref version) => {
                (version.clone(), "Использована кастомная версия".to_string())
            }
        };

        Ok(VersionAnalysis {
            suggested_version,
            reasoning,
            confidence: analysis.confidence,
            change_types: analysis.change_summary.keys()
                .map(|ct| format!("{:?}", ct))
                .collect(),
        })
    }

    /// Инкрементирует MAJOR версию
    fn increment_major(&self, version: &str) -> String {
        if let Ok(mut semver) = semver::Version::parse(version) {
            semver.major += 1;
            semver.minor = 0;
            semver.patch = 0;
            semver.to_string()
        } else {
            format!("{}.0.0", version.split('.').next().unwrap_or("1").parse::<u32>().unwrap_or(1) + 1)
        }
    }

    /// Инкрементирует MINOR версию
    fn increment_minor(&self, version: &str) -> String {
        if let Ok(mut semver) = semver::Version::parse(version) {
            semver.minor += 1;
            semver.patch = 0;
            semver.to_string()
        } else {
            let parts: Vec<&str> = version.split('.').collect();
            if parts.len() >= 2 {
                let major = parts[0].parse::<u32>().unwrap_or(1);
                let minor = parts[1].parse::<u32>().unwrap_or(0) + 1;
                format!("{}.{}.0", major, minor)
            } else {
                format!("{}.1.0", parts[0].parse::<u32>().unwrap_or(1))
            }
        }
    }

    /// Инкрементирует PATCH версию
    fn increment_patch(&self, version: &str) -> String {
        if let Ok(mut semver) = semver::Version::parse(version) {
            semver.patch += 1;
            semver.to_string()
        } else {
            let parts: Vec<&str> = version.split('.').collect();
            if parts.len() >= 3 {
                let major = parts[0].parse::<u32>().unwrap_or(1);
                let minor = parts[1].parse::<u32>().unwrap_or(0);
                let patch = parts[2].parse::<u32>().unwrap_or(0) + 1;
                format!("{}.{}.{}", major, minor, patch)
            } else {
                format!("{}.0.1", parts[0].parse::<u32>().unwrap_or(1))
            }
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

        debug!("Отправка промпта в YandexGPT: {}", preview(&prompt, 200));

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
    pub(crate) changelog_agent: ChangelogAgent,
    pub(crate) version_agent: VersionAgent,
    pub(crate) release_agent: ReleaseAgent,
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

    /// Генерирует полный пакет контента для релиза на основе анализа репозитория
    pub async fn generate_release_package(&self, repo: &GitRepository, current_version: &str, plugin_info: &PluginInfo) -> Result<ReleasePackage> {
        info!("🤖 Генерация полного пакета для релиза");

        // 1. Анализируем изменения
        let (analysis, commits, _) = repo.get_changes_since_last_release().await?;

        // 2. Предлагаем новую версию
        let version_analysis = self.version_agent.suggest_semantic_version(repo, current_version).await?;
        let new_version = &version_analysis.suggested_version;

        // 3. Генерируем changelog
        let changelog = self.changelog_agent.generate_enhanced_changelog(repo, &analysis).await?;

        // 4. Генерируем release notes
        let release_notes = self.release_agent.generate_release_notes(
            new_version,
            &changelog.changelog,
            plugin_info,
        ).await?;

        // 5. Создаем сводный анализ
        let summary = ReleaseSummary {
            current_version: current_version.to_string(),
            new_version: new_version.clone(),
            total_commits: analysis.total_commits as u32,
            change_summary: analysis.change_summary.clone(),
            breaking_changes_count: analysis.breaking_changes.len(),
            confidence: analysis.confidence,
            readiness_score: self.calculate_readiness_score(&analysis),
        };

        Ok(ReleasePackage {
            version: new_version.clone(),
            changelog,
            release_notes,
            version_analysis,
            release_summary: summary,
            commits_analyzed: commits,
        })
    }

    /// Анализирует готовность к релизу
    pub async fn analyze_release_readiness(&self, repo: &GitRepository, version: &str) -> Result<ReadinessReport> {
        info!("🔍 Анализ готовности к релизу версии {}", version);

        let (analysis, _, _) = repo.get_changes_since_last_release().await?;

        // Проверяем критические изменения
        let has_breaking_changes = !analysis.breaking_changes.is_empty();

        // Оцениваем сложность изменений
        let complexity_score = self.calculate_complexity_score(&analysis);

        // Проверяем наличие тестов
        let has_tests = analysis.change_summary.contains_key(&ChangeType::Testing);

        // Проверяем документацию
        let has_docs = analysis.change_summary.contains_key(&ChangeType::Documentation);

        // Рассчитываем общую готовность
        let readiness_score = self.calculate_readiness_score(&analysis);

        let readiness_level = match readiness_score {
            score if score >= 0.9 => ReadinessLevel::Ready,
            score if score >= 0.7 => ReadinessLevel::ReadyWithConcerns,
            score if score >= 0.5 => ReadinessLevel::NeedsAttention,
            _ => ReadinessLevel::NotReady,
        };

        let mut recommendations = Vec::new();

        if has_breaking_changes {
            recommendations.push("Обновите миграционную документацию для breaking changes".to_string());
        }

        if !has_tests && analysis.total_commits > 5 {
            recommendations.push("Рассмотрите добавление тестов для новых функций".to_string());
        }

        if !has_docs && analysis.change_summary.contains_key(&ChangeType::Feature) {
            recommendations.push("Добавьте документацию для новых функций".to_string());
        }

        if complexity_score > 0.8 {
            recommendations.push("Высокая сложность изменений - рассмотрите поэтапный релиз".to_string());
        }

        Ok(ReadinessReport {
            version: version.to_string(),
            readiness_level,
            readiness_score,
            has_breaking_changes,
            complexity_score,
            has_tests,
            has_docs,
            recommendations,
            analysis_summary: format!("Анализ {} коммитов с уверенностью {:.1}%",
                analysis.total_commits, analysis.confidence * 100.0),
        })
    }

    /// Рассчитывает оценку готовности к релизу
    fn calculate_readiness_score(&self, analysis: &ReleaseAnalysis) -> f32 {
        let mut score = 0.5; // Базовый балл

        // Уменьшаем балл за критические изменения
        if !analysis.breaking_changes.is_empty() {
            score -= 0.3;
        }

        // Увеличиваем балл за наличие тестов
        if analysis.change_summary.contains_key(&ChangeType::Testing) {
            score += 0.2;
        }

        // Увеличиваем балл за документацию
        if analysis.change_summary.contains_key(&ChangeType::Documentation) {
            score += 0.1;
        }

        // Увеличиваем балл за рефакторинг (улучшение качества)
        if analysis.change_summary.contains_key(&ChangeType::Refactoring) {
            score += 0.1;
        }

        // Уменьшаем балл за большое количество коммитов (риск)
        if analysis.total_commits > 20 {
            score -= 0.1;
        }

        // Учитываем уверенность анализа
        score = score * analysis.confidence + (1.0 - analysis.confidence) * 0.3;

        score.min(1.0).max(0.0)
    }

    /// Рассчитывает сложность изменений
    fn calculate_complexity_score(&self, analysis: &ReleaseAnalysis) -> f32 {
        let mut score = 0.0;

        // Критические изменения увеличивают сложность
        score += analysis.breaking_changes.len() as f32 * 0.3;

        // Новые функции увеличивают сложность
        if let Some(features) = analysis.change_summary.get(&ChangeType::Feature) {
            score += *features as f32 * 0.1;
        }

        // Большое количество коммитов увеличивает сложность
        if analysis.total_commits > 10 {
            score += (analysis.total_commits - 10) as f32 * 0.02;
        }

        // Рефакторинг может указывать на сложность
        if let Some(refactors) = analysis.change_summary.get(&ChangeType::Refactoring) {
            score += *refactors as f32 * 0.05;
        }

        score.min(1.0)
    }
}

/// Полный пакет для релиза
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReleasePackage {
    pub version: String,
    pub changelog: GeneratedChangelog,
    pub release_notes: GeneratedReleaseNotes,
    pub version_analysis: VersionAnalysis,
    pub release_summary: ReleaseSummary,
    pub commits_analyzed: Vec<GitCommit>,
}

/// Сводка по релизу
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReleaseSummary {
    pub current_version: String,
    pub new_version: String,
    pub total_commits: u32,
    pub change_summary: std::collections::HashMap<ChangeType, usize>,
    pub breaking_changes_count: usize,
    pub confidence: f32,
    pub readiness_score: f32,
}

/// Отчет о готовности к релизу
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReadinessReport {
    pub version: String,
    pub readiness_level: ReadinessLevel,
    pub readiness_score: f32,
    pub has_breaking_changes: bool,
    pub complexity_score: f32,
    pub has_tests: bool,
    pub has_docs: bool,
    pub recommendations: Vec<String>,
    pub analysis_summary: String,
}

/// Уровень готовности к релизу
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ReadinessLevel {
    Ready,                    // Готов к релизу
    ReadyWithConcerns,       // Готов, но с замечаниями
    NeedsAttention,          // Требует внимания
    NotReady,                // Не готов к релизу
}

impl ReadinessLevel {
    /// Возвращает эмодзи для уровня готовности
    pub fn emoji(&self) -> &'static str {
        match self {
            ReadinessLevel::Ready => "✅",
            ReadinessLevel::ReadyWithConcerns => "⚠️",
            ReadinessLevel::NeedsAttention => "🔶",
            ReadinessLevel::NotReady => "🔴",
        }
    }

    /// Возвращает название уровня
    pub fn name(&self) -> &'static str {
        match self {
            ReadinessLevel::Ready => "Готов к релизу",
            ReadinessLevel::ReadyWithConcerns => "Готов с замечаниями",
            ReadinessLevel::NeedsAttention => "Требует внимания",
            ReadinessLevel::NotReady => "Не готов к релизу",
        }
    }
}