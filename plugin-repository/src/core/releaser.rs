use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::process::Command;
use tracing::{info, debug, warn, error};
use chrono::{DateTime, Utc};
use semver::Version;

use crate::git::GitRepository;
use crate::core::llm::agents::{LLMAgentManager, PluginInfo};
use crate::models::release::ReleaseInfo;
use crate::config::parser::ProjectConfig;

/// Менеджер релизов для автоматического управления версиями и публикацией
pub struct ReleaseManager {
    git_repo: GitRepository,
    agent_manager: LLMAgentManager,
    project_config: ProjectConfig,
}

/// Информация о планируемом релизе
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlannedRelease {
    pub version: String,
    pub version_type: VersionType,
    pub changes_count: usize,
    pub breaking_changes: usize,
    pub estimated_release_date: DateTime<Utc>,
    pub release_notes: Option<String>,
    pub changelog: Option<String>,
}

/// Тип версии по semver
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum VersionType {
    Major,
    Minor,
    Patch,
    PreRelease,
}

impl VersionType {
    pub fn increment(&self, current: &str) -> Result<String> {
        let mut version = Version::parse(current)
            .with_context(|| format!("Невозможно спарсить версию: {}", current))?;

        match self {
            VersionType::Major => {
                version.major += 1;
                version.minor = 0;
                version.patch = 0;
            },
            VersionType::Minor => {
                version.minor += 1;
                version.patch = 0;
            },
            VersionType::Patch => {
                version.patch += 1;
            },
            VersionType::PreRelease => {
                version.pre = semver::Prerelease::new("alpha.1")?;
            },
        }

        Ok(version.to_string())
    }

    pub fn from_analysis(analysis: &crate::git::ReleaseAnalysis) -> Self {
        if !analysis.breaking_changes.is_empty() {
            VersionType::Major
        } else if analysis.change_summary.contains_key(&crate::git::ChangeType::Feature) {
            VersionType::Minor
        } else {
            VersionType::Patch
        }
    }
}

/// Результат подготовки релиза
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReleasePreparationResult {
    pub success: bool,
    pub release: PlannedRelease,
    pub warnings: Vec<String>,
    pub errors: Vec<String>,
    pub validation_issues: Vec<String>,
}

impl ReleaseManager {
    /// Создает новый экземпляр менеджера релизов
    pub fn new(
        git_repo: GitRepository,
        agent_manager: LLMAgentManager,
        project_config: ProjectConfig,
    ) -> Self {
        Self {
            git_repo,
            agent_manager,
            project_config,
        }
    }

    /// Анализирует изменения и предлагает версию для следующего релиза
    pub async fn suggest_next_version(&self) -> Result<PlannedRelease> {
        info!("🔍 Анализ изменений для предложения версии");

        // Получаем анализ изменений с последнего релиза
        let (analysis, commits, latest_tag) = self.git_repo.get_changes_since_last_release().await?;

        // Определяем тип версии
        let version_type = VersionType::from_analysis(&analysis);

        // Определяем текущую версию
        let current_version = if let Some(tag) = latest_tag {
            tag.name.strip_prefix('v').unwrap_or(&tag.name).to_string()
        } else {
            "1.0.0".to_string()
        };

        // Предлагаем новую версию
        let suggested_version = version_type.increment(&current_version)
            .unwrap_or_else(|_| format!("{}.0.0", current_version.parse::<Version>().unwrap_or_else(|_| Version::new(1, 0, 0)).major + 1));

        info!("📋 Текущая версия: {}", current_version);
        info!("📈 Предлагаемая версия: {} ({:?})", suggested_version, version_type);
        info!("📊 Всего изменений: {}", analysis.total_commits);

        Ok(PlannedRelease {
            version: suggested_version,
            version_type,
            changes_count: analysis.total_commits,
            breaking_changes: analysis.breaking_changes.len(),
            estimated_release_date: Utc::now(),
            release_notes: None,
            changelog: None,
        })
    }

    /// Готовит полный релиз с генерацией контента
    pub async fn prepare_release(&self, version: Option<String>) -> Result<ReleasePreparationResult> {
        info!("🚀 Подготовка релиза");

        let mut result = ReleasePreparationResult {
            success: true,
            release: if let Some(v) = version {
                // Если версия указана, используем её
                PlannedRelease {
                    version: v.clone(),
                    version_type: VersionType::Patch, // Будет определено позже
                    changes_count: 0,
                    breaking_changes: 0,
                    estimated_release_date: Utc::now(),
                    release_notes: None,
                    changelog: None,
                }
            } else {
                // Иначе предлагаем автоматически
                self.suggest_next_version().await?
            },
            warnings: Vec::new(),
            errors: Vec::new(),
            validation_issues: Vec::new(),
        };

        // Получаем анализ изменений
        let (analysis, commits, latest_tag) = self.git_repo.get_changes_since_last_release().await?;

        result.release.changes_count = analysis.total_commits;
        result.release.breaking_changes = analysis.breaking_changes.len();

        // Генерируем changelog
        match self.generate_changelog(&result.release.version, latest_tag.as_ref()).await {
            Ok(changelog) => {
                result.release.changelog = Some(changelog.clone());
                info!("✅ Changelog сгенерирован");
            },
            Err(e) => {
                result.errors.push(format!("Ошибка генерации changelog: {}", e));
                result.success = false;
            }
        }

        // Генерируем release notes
        match self.generate_release_notes(&result.release.version, &result.release.changelog).await {
            Ok(notes) => {
                result.release.release_notes = Some(notes.clone());
                info!("✅ Release notes сгенерированы");
            },
            Err(e) => {
                result.warnings.push(format!("Предупреждение генерации release notes: {}", e));
            }
        }

        // Валидация
        let validation_result = self.validate_release_readiness(&analysis).await?;
        result.validation_issues = validation_result.issues;

        if validation_result.is_ready {
            info!("✅ Релиз готов к публикации");
        } else {
            result.warnings.push("Релиз имеет проблемы готовности".to_string());
        }

        Ok(result)
    }

    /// Создает релиз с тегом и аннотацией
    pub async fn create_release(&self, version: &str, message: Option<String>) -> Result<String> {
        info!("🏷️ Создание релиза v{}", version);

        // Проверяем, что такая версия еще не существует
        if self.tag_exists(version).await? {
            return Err(anyhow::anyhow!("Тег v{} уже существует", version));
        }

        // Создаем аннотированный тег
        let tag_message = message.unwrap_or_else(|| format!("Release v{}", version));

        let output = Command::new("git")
            .args(&["tag", "-a", &format!("v{}", version), "-m", &tag_message])
            .output()
            .context("Ошибка создания тега")?;

        if !output.status.success() {
            let error = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!("Git ошибка создания тега: {}", error));
        }

        info!("✅ Тег v{} создан", version);
        Ok(format!("v{}", version))
    }

    /// Публикует релиз (push тега)
    pub async fn publish_release(&self, version: &str) -> Result<()> {
        info!("📤 Публикация релиза v{}", version);

        let output = Command::new("git")
            .args(&["push", "origin", &format!("v{}", version)])
            .output()
            .context("Ошибка пуша тега")?;

        if !output.status.success() {
            let error = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!("Git ошибка пуша тега: {}", error));
        }

        info!("✅ Релиз v{} опубликован", version);
        Ok(())
    }

    /// Откатывает релиз (удаляет тег локально и удаленно)
    pub async fn rollback_release(&self, version: &str) -> Result<()> {
        warn!("⏪ Откат релиза v{}", version);

        // Удаляем локальный тег
        let _ = Command::new("git")
            .args(&["tag", "-d", &format!("v{}", version)])
            .output();

        // Удаляем удаленный тег
        let _ = Command::new("git")
            .args(&["push", "origin", "--delete", &format!("v{}", version)])
            .output();

        warn!("⚠️ Релиз v{} откачен", version);
        Ok(())
    }

    /// Проверяет существование тега
    async fn tag_exists(&self, version: &str) -> Result<bool> {
        let tags = self.git_repo.tags.get_all_tags().await?;
        Ok(tags.iter().any(|tag| tag.name == format!("v{}", version)))
    }

    /// Генерирует changelog для релиза
    async fn generate_changelog(&self, version: &str, from_tag: Option<&crate::git::GitTag>) -> Result<String> {
        let from_ref = from_tag.map(|t| t.name.as_str());

        self.git_repo.generate_changelog(from_ref, Some("HEAD")).await
    }

    /// Генерирует release notes через LLM
    async fn generate_release_notes(&self, version: &str, changelog: &Option<String>) -> Result<String> {
        let changelog_content = changelog.as_deref().unwrap_or("Нет изменений");

        let plugin_info = PluginInfo {
            name: self.project_config.name.clone(),
            id: self.project_config.id.clone(),
            version: version.to_string(),
            description: Some("AI помощник для IntelliJ IDEA".to_string()),
        };

        let notes = self
            .agent_manager
            .generate_release_notes(version, changelog_content, &plugin_info)
            .await?;

        // Преобразуем структурированные release notes в человекочитаемый Markdown
        let mut formatted = String::new();
        formatted.push_str(&format!("# {}\n\n", notes.title));
        if !notes.subtitle.is_empty() {
            formatted.push_str(&format!("{}\n\n", notes.subtitle));
        }
        if !notes.highlights.is_empty() {
            formatted.push_str("## Highlights\n");
            for h in notes.highlights {
                formatted.push_str(&format!("- {}\n", h));
            }
            formatted.push('\n');
        }
        formatted.push_str(&notes.body);

        Ok(formatted)
    }

    /// Валидирует готовность к релизу
    async fn validate_release_readiness(&self, analysis: &crate::git::ReleaseAnalysis) -> Result<ReleaseValidationResult> {
        let mut issues = Vec::new();
        let mut is_ready = true;

        // Проверяем наличие критических изменений без соответствующей версии
        if !analysis.breaking_changes.is_empty() {
            info!("🔍 Найдены критические изменения: {}", analysis.breaking_changes.len());
        }

        // Проверяем количество изменений
        if analysis.total_commits == 0 {
            issues.push("Нет изменений для релиза".to_string());
            is_ready = false;
        } else if analysis.total_commits < 3 {
            issues.push("Мало изменений для релиза (менее 3 коммитов)".to_string());
        }

        // Проверяем состояние Git репозитория
        if !self.is_working_tree_clean().await? {
            issues.push("Рабочая директория Git не чиста".to_string());
            is_ready = false;
        }

        Ok(ReleaseValidationResult {
            is_ready,
            issues,
        })
    }

    /// Проверяет чистоту рабочей директории Git
    async fn is_working_tree_clean(&self) -> Result<bool> {
        let output = Command::new("git")
            .args(&["status", "--porcelain"])
            .output()
            .context("Ошибка проверки статуса Git")?;

        Ok(String::from_utf8_lossy(&output.stdout).trim().is_empty())
    }

    /// Получает историю релизов
    pub async fn get_release_history(&self, limit: Option<usize>) -> Result<Vec<ReleaseInfo>> {
        let tags = self.git_repo.tags.get_all_tags().await?;
        let mut releases = Vec::new();

        let limit = limit.unwrap_or(tags.len());

        for (index, tag) in tags.iter().take(limit).enumerate() {
            let release = ReleaseInfo {
                version: tag.name.clone(),
                tag: tag.name.clone(),
                commit: tag.commit_hash.clone(),
                date: tag.date,
                message: Some(tag.commit_message.clone()),
                changes_count: self.count_commits_since_tag(&tag.name).await.unwrap_or(0),
            };

            releases.push(release);
        }

        Ok(releases)
    }

    /// Считает количество коммитов с указанного тега
    async fn count_commits_since_tag(&self, tag: &str) -> Result<usize> {
        let commits = self.git_repo.history.get_commits_between(Some(tag), None).await?;
        Ok(commits.len())
    }
}

/// Результат валидации релиза
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReleaseValidationResult {
    pub is_ready: bool,
    pub issues: Vec<String>,
}

/// Состояние релиза
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum ReleaseState {
    Draft,
    Ready,
    Published,
    Failed,
}

/// Информация о текущем релизе
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CurrentRelease {
    pub state: ReleaseState,
    pub version: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub notes: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version_increment_major() {
        let v = VersionType::Major.increment("1.2.3").unwrap();
        assert_eq!(v, "2.0.0");
    }

    #[test]
    fn test_version_increment_minor() {
        let v = VersionType::Minor.increment("1.2.3").unwrap();
        assert_eq!(v, "1.3.0");
    }

    #[test]
    fn test_version_increment_patch() {
        let v = VersionType::Patch.increment("1.2.3").unwrap();
        assert_eq!(v, "1.2.4");
    }

    #[test]
    fn test_version_increment_prerelease() {
        let v = VersionType::PreRelease.increment("1.2.3").unwrap();
        assert!(v.starts_with("1.2.3-"));
    }
}