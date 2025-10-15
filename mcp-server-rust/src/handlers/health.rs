use axum::{http::StatusCode, Json};
use crate::models::HealthResponse;

static START_TIME: std::sync::OnceLock<std::time::Instant> = std::sync::OnceLock::new();

pub async fn health_check() -> (StatusCode, Json<HealthResponse>) {
    let start = START_TIME.get_or_init(|| std::time::Instant::now());
    let uptime = start.elapsed().as_secs();
    
    (
        StatusCode::OK,
        Json(HealthResponse {
            status: "healthy".to_string(),
            version: env!("CARGO_PKG_VERSION").to_string(),
            uptime_seconds: uptime,
        }),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_health_check() {
        let (status, response) = health_check().await;
        assert_eq!(status, StatusCode::OK);
        assert_eq!(response.status, "healthy");
    }
}
