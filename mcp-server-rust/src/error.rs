use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use std::fmt;

#[derive(Debug, Serialize, Deserialize)]
pub struct ErrorResponse {
    pub error: String,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub details: Option<String>,
}

#[derive(Debug)]
pub enum AppError {
    NotFound(String),
    InvalidInput(String),
    PermissionDenied(String),
    FileTooLarge(usize, usize), // actual, max
    IoError(std::io::Error),
    ValidationError(String),
    InternalError(String),
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AppError::NotFound(msg) => write!(f, "Not found: {}", msg),
            AppError::InvalidInput(msg) => write!(f, "Invalid input: {}", msg),
            AppError::PermissionDenied(msg) => write!(f, "Permission denied: {}", msg),
            AppError::FileTooLarge(actual, max) => {
                write!(f, "File too large: {} bytes (max: {} bytes)", actual, max)
            }
            AppError::IoError(err) => write!(f, "IO error: {}", err),
            AppError::ValidationError(msg) => write!(f, "Validation error: {}", msg),
            AppError::InternalError(msg) => write!(f, "Internal error: {}", msg),
        }
    }
}

impl std::error::Error for AppError {}

impl From<std::io::Error> for AppError {
    fn from(err: std::io::Error) -> Self {
        match err.kind() {
            std::io::ErrorKind::NotFound => AppError::NotFound(err.to_string()),
            std::io::ErrorKind::PermissionDenied => AppError::PermissionDenied(err.to_string()),
            _ => AppError::IoError(err),
        }
    }
}

impl From<validator::ValidationErrors> for AppError {
    fn from(err: validator::ValidationErrors) -> Self {
        AppError::ValidationError(err.to_string())
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error_type, message, details) = match self {
            AppError::NotFound(msg) => (
                StatusCode::NOT_FOUND,
                "NOT_FOUND",
                "Resource not found",
                Some(msg),
            ),
            AppError::InvalidInput(msg) => (
                StatusCode::BAD_REQUEST,
                "INVALID_INPUT",
                "Invalid input provided",
                Some(msg),
            ),
            AppError::PermissionDenied(msg) => (
                StatusCode::FORBIDDEN,
                "PERMISSION_DENIED",
                "Permission denied",
                Some(msg),
            ),
            AppError::FileTooLarge(actual, max) => (
                StatusCode::PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "File size exceeds limit",
                Some(format!("File size: {} bytes, max: {} bytes", actual, max)),
            ),
            AppError::IoError(err) => (
                StatusCode::INTERNAL_SERVER_ERROR,
                "IO_ERROR",
                "File system operation failed",
                Some(err.to_string()),
            ),
            AppError::ValidationError(msg) => (
                StatusCode::BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed",
                Some(msg),
            ),
            AppError::InternalError(msg) => (
                StatusCode::INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Internal server error",
                Some(msg),
            ),
        };

        let error_response = ErrorResponse {
            error: error_type.to_string(),
            message: message.to_string(),
            details,
        };

        (status, Json(error_response)).into_response()
    }
}

pub type Result<T> = std::result::Result<T, AppError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_display() {
        let err = AppError::NotFound("file.txt".to_string());
        assert_eq!(err.to_string(), "Not found: file.txt");
    }

    #[test]
    fn test_file_too_large_error() {
        let err = AppError::FileTooLarge(1000, 500);
        assert!(err.to_string().contains("1000"));
        assert!(err.to_string().contains("500"));
    }
}
