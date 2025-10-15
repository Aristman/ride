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
    pub dir: Option<String>,
}

pub async fn create_file(
    State(config): State<Config>,
    Json(request): Json<CreateFileRequest>,
) -> Result<(StatusCode, Json<FileResponse>)> {
    request.validate().map_err(AppError::from)?;
    
    let response = FileService::create_file(&config, request).await?;
    Ok((StatusCode::CREATED, Json(response)))
}

pub async fn read_file(
    State(config): State<Config>,
    Path(path): Path<String>,
) -> Result<Json<FileContentResponse>> {
    let response = FileService::read_file(&config, &path).await?;
    Ok(Json(response))
}

pub async fn update_file(
    State(config): State<Config>,
    Path(path): Path<String>,
    Json(request): Json<UpdateFileRequest>,
) -> Result<Json<FileResponse>> {
    let response = FileService::update_file(&config, &path, request).await?;
    Ok(Json(response))
}

pub async fn delete_file(
    State(config): State<Config>,
    Path(path): Path<String>,
) -> Result<Json<DeleteResponse>> {
    let response = FileService::delete_file(&config, &path).await?;
    Ok(Json(response))
}

pub async fn list_files(
    State(config): State<Config>,
    Query(query): Query<ListQuery>,
) -> Result<Json<DirectoryListResponse>> {
    let response = FileService::list_files(&config, query.dir.as_deref()).await?;
    Ok(Json(response))
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::extract::State;
    use tempfile::TempDir;

    fn create_test_config() -> (Config, TempDir) {
        let temp_dir = TempDir::new().unwrap();
        let mut config = Config::default();
        config.base_dir = temp_dir.path().to_path_buf();
        config.blocked_paths.clear();
        (config, temp_dir)
    }

    #[tokio::test]
    async fn test_create_file_handler() {
        let (config, _temp_dir) = create_test_config();
        
        let request = CreateFileRequest {
            path: "test.txt".to_string(),
            content: "Hello".to_string(),
            overwrite: false,
        };
        
        let result = create_file(State(config), Json(request)).await;
        assert!(result.is_ok());
    }
}
