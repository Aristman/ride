use serde::{Deserialize, Serialize};
use validator::Validate;

#[derive(Debug, Serialize, Deserialize, Validate)]
pub struct CreateFileRequest {
    #[validate(length(min = 1, max = 255))]
    pub path: String,
    
    pub content: String,
    
    #[serde(default)]
    pub overwrite: bool,
}

#[derive(Debug, Serialize, Deserialize, Validate)]
pub struct UpdateFileRequest {
    pub content: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct FileResponse {
    pub path: String,
    pub size: u64,
    pub created_at: String,
    pub modified_at: String,
    pub is_readonly: bool,
    pub checksum: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct FileContentResponse {
    pub path: String,
    pub content: String,
    pub size: u64,
    pub mime_type: String,
    pub checksum: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DirectoryListResponse {
    pub path: String,
    pub files: Vec<FileInfo>,
    pub directories: Vec<DirectoryInfo>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct FileInfo {
    pub name: String,
    pub path: String,
    pub size: u64,
    pub modified_at: String,
    pub is_readonly: bool,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DirectoryInfo {
    pub name: String,
    pub path: String,
    pub modified_at: String,
}

#[derive(Debug, Serialize, Deserialize, Validate)]
pub struct CreateDirectoryRequest {
    #[validate(length(min = 1, max = 255))]
    pub path: String,
    
    #[serde(default)]
    pub recursive: bool,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DirectoryResponse {
    pub path: String,
    pub created_at: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DeleteResponse {
    pub success: bool,
    pub message: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct HealthResponse {
    pub status: String,
    pub version: String,
    pub uptime_seconds: u64,
}

#[cfg(test)]
mod tests {
    use super::*;
    use validator::Validate;

    #[test]
    fn test_create_file_request_validation() {
        let valid_request = CreateFileRequest {
            path: "test.txt".to_string(),
            content: "Hello, World!".to_string(),
            overwrite: false,
        };
        assert!(valid_request.validate().is_ok());

        let invalid_request = CreateFileRequest {
            path: "".to_string(),
            content: "Hello".to_string(),
            overwrite: false,
        };
        assert!(invalid_request.validate().is_err());
    }

    #[test]
    fn test_create_directory_request_validation() {
        let valid_request = CreateDirectoryRequest {
            path: "test_dir".to_string(),
            recursive: true,
        };
        assert!(valid_request.validate().is_ok());
    }
}
