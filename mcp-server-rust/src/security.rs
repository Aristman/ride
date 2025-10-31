use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};

/// Calculate SHA256 checksum of file content
pub fn calculate_checksum(content: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(content);
    hex::encode(hasher.finalize())
}

/// Sanitize path to prevent directory traversal attacks
/// Now allows absolute paths when base_dir is root
pub fn sanitize_path(path: &str) -> Result<PathBuf, String> {
    let path = PathBuf::from(path);
    
    // Check for directory traversal attempts
    for component in path.components() {
        match component {
            std::path::Component::ParentDir => {
                return Err("Path traversal detected: '..' not allowed".to_string());
            }
            std::path::Component::RootDir => {
                // Allow absolute paths - they will be validated against base_dir later
                continue;
            }
            _ => {}
        }
    }
    
    Ok(path)
}

/// Validate file name
pub fn validate_filename(filename: &str) -> Result<(), String> {
    if filename.is_empty() {
        return Err("Filename cannot be empty".to_string());
    }
    
    // Check for invalid characters
    let invalid_chars = ['<', '>', ':', '"', '|', '?', '*', '\0'];
    if filename.chars().any(|c| invalid_chars.contains(&c)) {
        return Err("Filename contains invalid characters".to_string());
    }
    
    // Check for reserved names (Windows)
    let reserved_names = [
        "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5",
        "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4",
        "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    ];
    
    let name_upper = filename.to_uppercase();
    if reserved_names.contains(&name_upper.as_str()) {
        return Err(format!("'{}' is a reserved filename", filename));
    }
    
    Ok(())
}

/// Check if path is safe (within allowed directory)
pub fn is_safe_path(base: &Path, target: &Path) -> bool {
    if let (Ok(base_canonical), Ok(target_canonical)) = 
        (base.canonicalize(), target.canonicalize()) {
        target_canonical.starts_with(base_canonical)
    } else {
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_calculate_checksum() {
        let content = b"Hello, World!";
        let checksum = calculate_checksum(content);
        assert_eq!(checksum.len(), 64); // SHA256 produces 64 hex characters
    }

    #[test]
    fn test_sanitize_path_valid() {
        let result = sanitize_path("test/file.txt");
        assert!(result.is_ok());
    }

    #[test]
    fn test_sanitize_path_traversal() {
        let result = sanitize_path("../etc/passwd");
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("traversal"));
    }

    #[test]
    fn test_sanitize_path_absolute() {
        let result = sanitize_path("/etc/passwd");
        assert!(result.is_err());
    }

    #[test]
    fn test_validate_filename_valid() {
        assert!(validate_filename("test.txt").is_ok());
        assert!(validate_filename("my-file_123.json").is_ok());
    }

    #[test]
    fn test_validate_filename_invalid() {
        assert!(validate_filename("").is_err());
        assert!(validate_filename("test<file>.txt").is_err());
        assert!(validate_filename("file|name.txt").is_err());
    }

    #[test]
    fn test_validate_filename_reserved() {
        assert!(validate_filename("CON").is_err());
        assert!(validate_filename("PRN").is_err());
        assert!(validate_filename("AUX").is_err());
    }
}
