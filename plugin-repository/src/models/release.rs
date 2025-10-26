use serde::{Deserialize, Serialize};
use chrono::{DateTime, Utc};
use crate::models::plugin::PluginArtifact;

/// Информация о релизе
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReleaseInfo {
    pub version: String,
    pub plugin_version: String,
    pub changelog: String,
    pub release_notes: Option<String>,
    pub artifact: PluginArtifact,
    pub publish_time: DateTime<Utc>,
    pub git_tag: Option<String>,
    pub git_commit: Option<String>,
}

/// Запрос на создание релиза
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReleaseRequest {
    pub version: String,
    pub changelog: Option<String>,
    pub release_notes: Option<String>,
    pub artifact_path: String,
    pub git_tag: Option<String>,
    pub dry_run: bool,
}

/// Статус релиза
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ReleaseStatus {
    Pending,
    Building,
    Testing,
    Publishing,
    Completed,
    Failed,
    RolledBack,
}

/// Результат операции релиза
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReleaseResult {
    pub status: ReleaseStatus,
    pub version: String,
    pub message: String,
    pub artifact_url: Option<String>,
    pub publish_time: Option<DateTime<Utc>>,
    pub errors: Vec<String>,
    pub warnings: Vec<String>,
}

/// История изменений между версиями
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChangelogEntry {
    pub version: String,
    pub changes: Vec<ChangeEntry>,
    pub release_date: Option<DateTime<Utc>>,
}

/// Запись об изменении
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChangeEntry {
    pub r#type: ChangeType,
    pub description: String,
    pub commit_hash: Option<String>,
    pub author: Option<String>,
    pub files: Vec<String>,
}

/// Тип изменения
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ChangeType {
    Feature,
    Fix,
    Improvement,
    Breaking,
    Documentation,
    Testing,
    Other,
}

/// Семантическая версия
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SemanticVersion {
    pub major: u32,
    pub minor: u32,
    pub patch: u32,
    pub prerelease: Option<String>,
    pub build: Option<String>,
}

impl SemanticVersion {
    pub fn new(major: u32, minor: u32, patch: u32) -> Self {
        Self {
            major,
            minor,
            patch,
            prerelease: None,
            build: None,
        }
    }

    pub fn from_string(version: &str) -> Result<Self, semver::Error> {
        let semver = semver::Version::parse(version)?;
        Ok(Self {
            major: semver.major as u32,
            minor: semver.minor as u32,
            patch: semver.patch as u32,
            prerelease: if semver.pre.is_empty() { None } else { Some(semver.pre.to_string()) },
            build: if semver.build.is_empty() { None } else { Some(semver.build.to_string()) },
        })
    }

    pub fn to_string(&self) -> String {
        let mut version = format!("{}.{}.{}", self.major, self.minor, self.patch);
        if let Some(prerelease) = &self.prerelease {
            version.push('-');
            version.push_str(prerelease);
        }
        if let Some(build) = &self.build {
            version.push('+');
            version.push_str(build);
        }
        version
    }
}

/// Предложение следующей версии
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionSuggestion {
    pub suggested_version: SemanticVersion,
    pub current_version: SemanticVersion,
    pub reasoning: String,
    pub confidence: f32,
    pub changes_analyzed: Vec<ChangeEntry>,
}