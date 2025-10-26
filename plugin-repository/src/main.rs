use clap::{Parser, Subcommand};
use anyhow::Result;

mod cli;
mod commands;
mod core;
mod config;
mod git;
mod models;
mod utils;

use tracing_subscriber;

#[derive(Parser, Debug)]
#[command(
    name = "deploy-pugin",
    about = "CLI приложение для автоматизации публикации плагинов с LLM-интеграцией",
    version = "0.1.0",
    author = "Ride Team"
)]
struct Args {
    #[command(subcommand)]
    command: Commands,

    /// Файл конфигурации
    #[arg(short, long, default_value = "config.toml")]
    config: String,

    /// Уровень логирования
    #[arg(short, long, default_value = "info")]
    log_level: String,
}

#[derive(Subcommand, Debug)]
enum Commands {
    /// Сборка плагина
    Build(cli::build::BuildCommand),
    /// Полный пайплайн релиза
    Release(cli::release::ReleaseCommand),
    /// Полный цикл публикации (build -> release -> deploy)
    Publish(cli::publish::PublishCommand),
    /// Развертывание в репозиторий
    Deploy(cli::deploy::DeployCommand),
    /// LLM команды
    Ai(cli::ai::AiCommand),
    /// Валидация
    Validate(cli::validate::ValidateCommand),
    /// Статус
    Status(cli::status::StatusCommand),
}

#[tokio::main]
async fn main() -> Result<()> {
    let args = Args::parse();

    // Инициализация логирования
    tracing_subscriber::fmt()
        .with_max_level(match args.log_level.as_str() {
            "debug" => tracing::Level::DEBUG,
            "info" => tracing::Level::INFO,
            "warn" => tracing::Level::WARN,
            "error" => tracing::Level::ERROR,
            _ => tracing::Level::INFO,
        })
        .init();

    // Загрузка переменных окружения из .env файла
    dotenv::dotenv().ok();
    // Дополнительная попытка: загрузить .env из поддиректории, если запускаем из корня монорепозитория
    if std::env::var("DEPLOY_PLUGIN_YANDEX_FOLDER_ID").is_err() || std::env::var("DEPLOY_PLUGIN_YANDEX_API_KEY").is_err() {
        let _ = dotenv::from_filename("plugin-repository/.env");
    }

    // Обработка команд
    match args.command {
        Commands::Build(cmd) => {
            commands::build::handle_build_command(cmd, &args.config).await
        }
        Commands::Release(cmd) => {
            commands::release::handle_release_command(cmd, &args.config).await
        }
        Commands::Publish(cmd) => {
            commands::publish::handle_publish_command(cmd, &args.config).await
        }
        Commands::Deploy(cmd) => {
            commands::deploy::handle_deploy_command(cmd, &args.config).await
        }
        Commands::Ai(cmd) => {
            commands::ai::handle_ai_command(cmd, &args.config).await
        }
        Commands::Validate(cmd) => {
            commands::validate::handle_validate_command(cmd, &args.config).await
        }
        Commands::Status(cmd) => {
            commands::status::handle_status_command(cmd, &args.config).await
        }
    }
}
