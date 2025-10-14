mod config;
mod error;
mod handlers;
mod models;
mod security;
mod services;

use axum::{
    routing::{delete, get, post, put},
    Router,
};
use std::net::SocketAddr;
use tower_http::{cors::CorsLayer, trace::TraceLayer};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize tracing
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "mcp_server_rust=debug,tower_http=debug".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    // Load configuration
    let config = config::Config::load()?;
    tracing::info!("Configuration loaded: {:?}", config);

    // Build application router
    let app = Router::new()
        // Health check
        .route("/health", get(handlers::health::health_check))
        // File operations
        .route("/files", post(handlers::files::create_file))
        .route("/files/:path", get(handlers::files::read_file))
        .route("/files/:path", put(handlers::files::update_file))
        .route("/files/:path", delete(handlers::files::delete_file))
        .route("/files", get(handlers::files::list_files))
        // Directory operations
        .route("/directories", post(handlers::directories::create_directory))
        .route(
            "/directories/:path",
            delete(handlers::directories::delete_directory),
        )
        .route("/directories", get(handlers::directories::list_directories))
        // Add middleware
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http())
        .with_state(config);

    // Start server
    let addr = SocketAddr::from(([127, 0, 0, 1], 3000));
    tracing::info!("Starting MCP server on {}", addr);

    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}
