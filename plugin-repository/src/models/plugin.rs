use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use chrono::{DateTime, Utc};

/// Информация о плагине
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginInfo {
    pub name: String,
    pub id: String,
    pub version: String,
    pub description: Option<String>,
    pub vendor: Option<String>,
    pub changelog: Option<String>,
    pub notes: Option<String>,
    pub since_build: String,
    pub until_build: Option<String>,
}

/// Артефакт сборки плагина
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginArtifact {
    pub file_path: PathBuf,
    pub file_name: String,
    pub file_size: u64,
    pub checksum_sha256: String,
    pub version: String,
    pub build_time: DateTime<Utc>,
}

/// Метаданные плагина из plugin.xml
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PluginMetadata {
    pub id: String,
    pub name: String,
    pub version: String,
    pub vendor: Option<VendorInfo>,
    pub description: Option<String>,
    pub changelog: Option<String>,
    pub idea_version: IdeaVersion,
    pub depends: Vec<Dependency>,
    pub extensions: Vec<Extension>,
}

/// Информация о разработчике
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VendorInfo {
    pub name: String,
    pub email: Option<String>,
    pub url: Option<String>,
}

/// Версия IntelliJ IDEA
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IdeaVersion {
    pub since_build: String,
    pub until_build: Option<String>,
}

/// Зависимость плагина
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Dependency {
    pub id: String,
    pub optional: bool,
}

/// Расширение плагина
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Extension {
    pub implementation_class: String,
    pub qualified_name: Option<String>,
    pub dynamic: bool,
}

/// Результат сборки плагина
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BuildResult {
    pub success: bool,
    pub artifact: Option<PluginArtifact>,
    pub metadata: Option<PluginMetadata>,
    pub build_time: DateTime<Utc>,
    pub logs: Vec<String>,
    pub errors: Vec<String>,
}