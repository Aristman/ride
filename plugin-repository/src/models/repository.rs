use serde::{Deserialize, Serialize};
use chrono::{DateTime, Utc};
use crate::models::plugin::PluginInfo;
use crate::models::release::ReleaseInfo;

/// XML репозиторий плагинов
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginRepository {
    pub url: String,
    pub plugins: Vec<PluginInfo>,
    pub last_updated: DateTime<Utc>,
    pub version: String,
}

/// Запись в XML репозитории
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RepositoryPluginEntry {
    pub id: String,
    pub name: String,
    pub url: String,
    pub version: String,
    pub description: Option<String>,
    pub changelog: Option<String>,
    pub vendor: Option<String>,
    pub since_build: String,
    pub until_build: Option<String>,
    pub size: u64,
    pub checksum_sha256: String,
    pub publish_date: DateTime<Utc>,
}

/// Статус репозитория
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RepositoryStatus {
    pub url: String,
    pub accessible: bool,
    pub plugin_count: usize,
    pub total_size: u64,
    pub last_updated: DateTime<Utc>,
    pub errors: Vec<String>,
}

/// Запрос на обновление репозитория
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RepositoryUpdateRequest {
    pub plugin_info: PluginInfo,
    pub artifact_url: String,
    pub artifact_size: u64,
    pub checksum: String,
    pub update_strategy: UpdateStrategy,
}

/// Стратегия обновления
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum UpdateStrategy {
    Add,
    Update,
    Replace,
}

/// Результат обновления репозитория
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RepositoryUpdateResult {
    pub success: bool,
    pub plugin_id: String,
    pub version: String,
    pub action: String,
    pub backup_created: bool,
    pub errors: Vec<String>,
    pub warnings: Vec<String>,
}

/// Информация о доступном плагине в репозитории
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AvailablePlugin {
    pub id: String,
    pub name: String,
    pub versions: Vec<PluginVersionInfo>,
    pub latest_version: String,
    pub total_downloads: u64,
    pub last_updated: DateTime<Utc>,
}

/// Информация о версии плагина
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginVersionInfo {
    pub version: String,
    pub url: String,
    pub size: u64,
    pub checksum: String,
    pub publish_date: DateTime<Utc>,
    pub since_build: String,
    pub until_build: Option<String>,
    pub changelog: Option<String>,
    pub download_count: u64,
}

/// Сравнение версий плагина
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionComparison {
    pub current_version: Option<String>,
    pub latest_version: String,
    pub update_available: bool,
    pub is_major_update: bool,
    pub is_minor_update: bool,
    pub is_patch_update: bool,
    pub changelog: Option<String>,
}