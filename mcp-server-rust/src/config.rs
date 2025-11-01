use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    /// Base directory for file operations
    pub base_dir: PathBuf,
    
    /// Maximum file size in bytes (default: 10MB)
    pub max_file_size: usize,
    
    /// Allowed file extensions (empty = all allowed)
    pub allowed_extensions: Vec<String>,
    
    /// Blocked paths (for security)
    pub blocked_paths: Vec<String>,
    
    /// Enable verbose logging
    pub verbose: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            base_dir: PathBuf::from("./data"),
            max_file_size: 10 * 1024 * 1024, // 10MB
            allowed_extensions: vec![],
            blocked_paths: vec![
                String::from("/etc"),
                String::from("/sys"),
                String::from("/proc"),
                String::from("C:\\Windows"),
                String::from("C:\\System32"),
            ],
            verbose: false,
        }
    }
}

impl Config {
    pub fn load() -> anyhow::Result<Self> {
        // Try to load from config file, fallback to default
        let config_path = std::env::var("MCP_CONFIG_PATH")
            .unwrap_or_else(|_| "config.toml".to_string());

        let mut config = if std::path::Path::new(&config_path).exists() {
            let content = std::fs::read_to_string(&config_path)?;
            toml::from_str(&content)?
        } else {
            tracing::warn!("Config file not found, using defaults");
            Self::default()
        };

        // Override base_dir with environment variable if set
        if let Ok(base_dir_env) = std::env::var("MCP_BASE_DIR") {
            config.base_dir = PathBuf::from(base_dir_env);
            tracing::info!("Base directory overridden by MCP_BASE_DIR: {:?}", config.base_dir);
        }

        Ok(config)
    }

    /// Validate if path is allowed
    pub fn is_path_allowed(&self, path: &std::path::Path) -> bool {
        let path_str = path.to_string_lossy();
        
        // Check if path is in blocked list
        for blocked in &self.blocked_paths {
            if path_str.starts_with(blocked) {
                return false;
            }
        }
        
        // Check if path is within base directory
        if let Ok(canonical) = path.canonicalize() {
            if let Ok(base_canonical) = self.base_dir.canonicalize() {
                return canonical.starts_with(base_canonical);
            }
        }
        
        true
    }

    /// Validate file extension
    pub fn is_extension_allowed(&self, path: &std::path::Path) -> bool {
        if self.allowed_extensions.is_empty() {
            return true;
        }

        if let Some(ext) = path.extension() {
            let ext_str = ext.to_string_lossy().to_lowercase();
            self.allowed_extensions
                .iter()
                .any(|allowed| allowed.to_lowercase() == ext_str)
        } else {
            false
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = Config::default();
        assert_eq!(config.max_file_size, 10 * 1024 * 1024);
        assert!(!config.verbose);
    }

    #[test]
    fn test_blocked_paths() {
        let config = Config::default();
        let blocked_path = PathBuf::from("/etc/passwd");
        // Note: This test may behave differently on Windows
        #[cfg(unix)]
        assert!(!config.is_path_allowed(&blocked_path));
    }

    #[test]
    fn test_allowed_extensions() {
        let mut config = Config::default();
        config.allowed_extensions = vec!["txt".to_string(), "md".to_string()];
        
        assert!(config.is_extension_allowed(&PathBuf::from("test.txt")));
        assert!(config.is_extension_allowed(&PathBuf::from("test.md")));
        assert!(!config.is_extension_allowed(&PathBuf::from("test.exe")));
    }
}
