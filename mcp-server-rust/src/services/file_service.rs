use crate::{
    config::Config,
    error::{AppError, Result},
    models::*,
    security,
};
use std::path::{Path, PathBuf};
use tokio::fs;
use tokio::io::{AsyncReadExt, AsyncWriteExt};

pub struct FileService;

impl FileService {
    /// Create a new file
    pub async fn create_file(
        config: &Config,
        request: CreateFileRequest,
    ) -> Result<FileResponse> {
        // Sanitize and validate path
        let sanitized_path = security::sanitize_path(&request.path)
            .map_err(|e| AppError::InvalidInput(e))?;
        
        let full_path = config.base_dir.join(&sanitized_path);
        
        // Validate path is allowed
        if !config.is_path_allowed(&full_path) {
            return Err(AppError::PermissionDenied(format!(
                "Access to path '{}' is not allowed",
                request.path
            )));
        }
        
        // Validate extension
        if !config.is_extension_allowed(&full_path) {
            return Err(AppError::PermissionDenied(format!(
                "File extension not allowed: {:?}",
                full_path.extension()
            )));
        }
        
        // Check file size
        let content_bytes = request.content.as_bytes();
        if content_bytes.len() > config.max_file_size {
            return Err(AppError::FileTooLarge(
                content_bytes.len(),
                config.max_file_size,
            ));
        }
        
        // Check if file exists
        if full_path.exists() && !request.overwrite {
            return Err(AppError::InvalidInput(format!(
                "File '{}' already exists",
                request.path
            )));
        }
        
        // Create parent directories if needed
        if let Some(parent) = full_path.parent() {
            fs::create_dir_all(parent).await?;
        }
        
        // Write file
        let mut file = fs::File::create(&full_path).await?;
        file.write_all(content_bytes).await?;
        file.sync_all().await?;
        
        // Get file metadata
        let metadata = fs::metadata(&full_path).await?;
        let checksum = security::calculate_checksum(content_bytes);
        
        Ok(FileResponse {
            path: request.path,
            size: metadata.len(),
            created_at: format!("{:?}", metadata.created().ok()),
            modified_at: format!("{:?}", metadata.modified().ok()),
            is_readonly: metadata.permissions().readonly(),
            checksum,
        })
    }
    
    /// Read file content
    pub async fn read_file(config: &Config, path: &str) -> Result<FileContentResponse> {
        let sanitized_path = security::sanitize_path(path)
            .map_err(|e| AppError::InvalidInput(e))?;
        
        let full_path = config.base_dir.join(&sanitized_path);
        
        if !config.is_path_allowed(&full_path) {
            return Err(AppError::PermissionDenied(format!(
                "Access to path '{}' is not allowed",
                path
            )));
        }
        
        if !full_path.exists() {
            return Err(AppError::NotFound(format!("File '{}' not found", path)));
        }
        
        // Read file
        let mut file = fs::File::open(&full_path).await?;
        let mut content = Vec::new();
        file.read_to_end(&mut content).await?;
        
        // Check size limit
        if content.len() > config.max_file_size {
            return Err(AppError::FileTooLarge(content.len(), config.max_file_size));
        }
        
        let metadata = fs::metadata(&full_path).await?;
        let mime_type = mime_guess::from_path(&full_path)
            .first_or_octet_stream()
            .to_string();
        let checksum = security::calculate_checksum(&content);
        
        Ok(FileContentResponse {
            path: path.to_string(),
            content: String::from_utf8_lossy(&content).to_string(),
            size: metadata.len(),
            mime_type,
            checksum,
        })
    }
    
    /// Update file content
    pub async fn update_file(
        config: &Config,
        path: &str,
        request: UpdateFileRequest,
    ) -> Result<FileResponse> {
        let sanitized_path = security::sanitize_path(path)
            .map_err(|e| AppError::InvalidInput(e))?;
        
        let full_path = config.base_dir.join(&sanitized_path);
        
        if !config.is_path_allowed(&full_path) {
            return Err(AppError::PermissionDenied(format!(
                "Access to path '{}' is not allowed",
                path
            )));
        }
        
        if !full_path.exists() {
            return Err(AppError::NotFound(format!("File '{}' not found", path)));
        }
        
        let content_bytes = request.content.as_bytes();
        if content_bytes.len() > config.max_file_size {
            return Err(AppError::FileTooLarge(
                content_bytes.len(),
                config.max_file_size,
            ));
        }
        
        // Write file
        let mut file = fs::File::create(&full_path).await?;
        file.write_all(content_bytes).await?;
        file.sync_all().await?;
        
        let metadata = fs::metadata(&full_path).await?;
        let checksum = security::calculate_checksum(content_bytes);
        
        Ok(FileResponse {
            path: path.to_string(),
            size: metadata.len(),
            created_at: format!("{:?}", metadata.created().ok()),
            modified_at: format!("{:?}", metadata.modified().ok()),
            is_readonly: metadata.permissions().readonly(),
            checksum,
        })
    }
    
    /// Delete file
    pub async fn delete_file(config: &Config, path: &str) -> Result<DeleteResponse> {
        let sanitized_path = security::sanitize_path(path)
            .map_err(|e| AppError::InvalidInput(e))?;
        
        let full_path = config.base_dir.join(&sanitized_path);
        
        if !config.is_path_allowed(&full_path) {
            return Err(AppError::PermissionDenied(format!(
                "Access to path '{}' is not allowed",
                path
            )));
        }
        
        if !full_path.exists() {
            return Err(AppError::NotFound(format!("File '{}' not found", path)));
        }
        
        fs::remove_file(&full_path).await?;
        
        Ok(DeleteResponse {
            success: true,
            message: format!("File '{}' deleted successfully", path),
        })
    }
    
    /// List files in directory
    pub async fn list_files(config: &Config, dir_path: Option<&str>) -> Result<DirectoryListResponse> {
        let base_path = if let Some(path) = dir_path {
            let sanitized = security::sanitize_path(path)
                .map_err(|e| AppError::InvalidInput(e))?;
            config.base_dir.join(sanitized)
        } else {
            config.base_dir.clone()
        };
        
        if !config.is_path_allowed(&base_path) {
            return Err(AppError::PermissionDenied(
                "Access to directory is not allowed".to_string(),
            ));
        }
        
        if !base_path.exists() {
            return Err(AppError::NotFound("Directory not found".to_string()));
        }
        
        let mut files = Vec::new();
        let mut directories = Vec::new();
        
        let mut entries = fs::read_dir(&base_path).await?;
        
        while let Some(entry) = entries.next_entry().await? {
            let path = entry.path();
            let metadata = entry.metadata().await?;
            let name = entry.file_name().to_string_lossy().to_string();
            
            if metadata.is_file() {
                files.push(FileInfo {
                    name: name.clone(),
                    path: path.to_string_lossy().to_string(),
                    size: metadata.len(),
                    modified_at: format!("{:?}", metadata.modified().ok()),
                    is_readonly: metadata.permissions().readonly(),
                });
            } else if metadata.is_dir() {
                directories.push(DirectoryInfo {
                    name: name.clone(),
                    path: path.to_string_lossy().to_string(),
                    modified_at: format!("{:?}", metadata.modified().ok()),
                });
            }
        }
        
        Ok(DirectoryListResponse {
            path: dir_path.unwrap_or(".").to_string(),
            files,
            directories,
        })
    }
    
    /// Create directory
    pub async fn create_directory(
        config: &Config,
        request: CreateDirectoryRequest,
    ) -> Result<DirectoryResponse> {
        let sanitized_path = security::sanitize_path(&request.path)
            .map_err(|e| AppError::InvalidInput(e))?;
        
        let full_path = config.base_dir.join(&sanitized_path);
        
        if !config.is_path_allowed(&full_path) {
            return Err(AppError::PermissionDenied(format!(
                "Access to path '{}' is not allowed",
                request.path
            )));
        }
        
        if full_path.exists() {
            return Err(AppError::InvalidInput(format!(
                "Directory '{}' already exists",
                request.path
            )));
        }
        
        if request.recursive {
            fs::create_dir_all(&full_path).await?;
        } else {
            fs::create_dir(&full_path).await?;
        }
        
        let metadata = fs::metadata(&full_path).await?;
        
        Ok(DirectoryResponse {
            path: request.path,
            created_at: format!("{:?}", metadata.created().ok()),
        })
    }
    
    /// Delete directory
    pub async fn delete_directory(config: &Config, path: &str) -> Result<DeleteResponse> {
        let sanitized_path = security::sanitize_path(path)
            .map_err(|e| AppError::InvalidInput(e))?;
        
        let full_path = config.base_dir.join(&sanitized_path);
        
        if !config.is_path_allowed(&full_path) {
            return Err(AppError::PermissionDenied(format!(
                "Access to path '{}' is not allowed",
                path
            )));
        }
        
        if !full_path.exists() {
            return Err(AppError::NotFound(format!(
                "Directory '{}' not found",
                path
            )));
        }
        
        fs::remove_dir_all(&full_path).await?;
        
        Ok(DeleteResponse {
            success: true,
            message: format!("Directory '{}' deleted successfully", path),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    fn create_test_config() -> (Config, TempDir) {
        let temp_dir = TempDir::new().unwrap();
        let mut config = Config::default();
        config.base_dir = temp_dir.path().to_path_buf();
        config.blocked_paths.clear(); // Clear for testing
        (config, temp_dir)
    }

    #[tokio::test]
    async fn test_create_and_read_file() {
        let (config, _temp_dir) = create_test_config();
        
        let request = CreateFileRequest {
            path: "test.txt".to_string(),
            content: "Hello, World!".to_string(),
            overwrite: false,
        };
        
        let result = FileService::create_file(&config, request).await;
        assert!(result.is_ok());
        
        let read_result = FileService::read_file(&config, "test.txt").await;
        assert!(read_result.is_ok());
        assert_eq!(read_result.unwrap().content, "Hello, World!");
    }

    #[tokio::test]
    async fn test_update_file() {
        let (config, _temp_dir) = create_test_config();
        
        // Create file first
        let create_request = CreateFileRequest {
            path: "test.txt".to_string(),
            content: "Original".to_string(),
            overwrite: false,
        };
        FileService::create_file(&config, create_request).await.unwrap();
        
        // Update file
        let update_request = UpdateFileRequest {
            content: "Updated".to_string(),
        };
        let result = FileService::update_file(&config, "test.txt", update_request).await;
        assert!(result.is_ok());
        
        // Verify update
        let read_result = FileService::read_file(&config, "test.txt").await;
        assert_eq!(read_result.unwrap().content, "Updated");
    }

    #[tokio::test]
    async fn test_delete_file() {
        let (config, _temp_dir) = create_test_config();
        
        // Create file first
        let request = CreateFileRequest {
            path: "test.txt".to_string(),
            content: "Test".to_string(),
            overwrite: false,
        };
        FileService::create_file(&config, request).await.unwrap();
        
        // Delete file
        let result = FileService::delete_file(&config, "test.txt").await;
        assert!(result.is_ok());
        
        // Verify deletion
        let read_result = FileService::read_file(&config, "test.txt").await;
        assert!(read_result.is_err());
    }

    #[tokio::test]
    async fn test_create_directory() {
        let (config, _temp_dir) = create_test_config();
        
        let request = CreateDirectoryRequest {
            path: "test_dir".to_string(),
            recursive: false,
        };
        
        let result = FileService::create_directory(&config, request).await;
        assert!(result.is_ok());
    }
}
