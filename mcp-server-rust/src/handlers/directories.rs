use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    Json,
};
use serde::Deserialize;
use validator::Validate;

use crate::{
    config::Config,
    error::{AppError, Result},
    models::*,
    services::FileService,
};

#[derive(Debug, Deserialize)]
pub struct ListQuery {
    pub path: Option<String>,
}

pub async fn create_directory(
    State(config): State<Config>,
    Json(request): Json<CreateDirectoryRequest>,
) -> Result<(StatusCode, Json<DirectoryResponse>)> {
    request.validate().map_err(AppError::from)?;
    
    let response = FileService::create_directory(&config, request).await?;
    Ok((StatusCode::CREATED, Json(response)))
}

pub async fn delete_directory(
    State(config): State<Config>,
    Path(path): Path<String>,
) -> Result<Json<DeleteResponse>> {
    let response = FileService::delete_directory(&config, &path).await?;
    Ok(Json(response))
}

pub async fn list_directories(
    State(config): State<Config>,
    Query(query): Query<ListQuery>,
) -> Result<Json<DirectoryListResponse>> {
    let response = FileService::list_files(&config, query.path.as_deref()).await?;
    Ok(Json(response))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    fn create_test_config() -> (Config, TempDir) {
        let temp_dir = TempDir::new().unwrap();
        let mut config = Config::default();
        config.base_dir = temp_dir.path().to_path_buf();
        config.blocked_paths.clear();
        (config, temp_dir)
    }

    #[tokio::test]
    async fn test_create_directory_handler() {
        let (config, _temp_dir) = create_test_config();
        
        let request = CreateDirectoryRequest {
            path: "test_dir".to_string(),
            recursive: false,
        };
        
        let result = create_directory(State(config), Json(request)).await;
        assert!(result.is_ok());
    }
}
