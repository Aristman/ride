use anyhow::{Context, Result};
use tracing::info;
use colored::*;
use crate::config::parser::Config;
use crate::core::builder::PluginBuilder;
use crate::cli::build::BuildCommand;

/// Обработчик команды сборки
pub async fn handle_build_command(
    command: BuildCommand,
    config_file: &str,
) -> Result<()> {
    info!("🔨 Запуск команды сборки плагина");

    // Загружаем конфигурацию
    let config = Config::load_from_file(config_file)
        .with_context(|| format!("Не удалось загрузить конфигурацию из файла: {}", config_file))?;

    // Валидируем конфигурацию
    config.validate()
        .with_context(|| "Валидация конфигурации не пройдена")?;

    // Определяем корневую директорию проекта
    let project_root = std::env::current_dir()
        .context("Не удалось определить текущую директорию")?;

    println!("📁 Директория проекта: {}", project_root.display());
    println!("🔧 Профиль сборки: {}", command.profile);

    if let Some(ref version) = command.version {
        println!("🏷️  Версия: {}", version);
    }

    println!();

    // Создаем билдер
    let builder = PluginBuilder::new(config, project_root);

    // Выполняем сборку
    let result = builder.build(command.version, &command.profile).await?;

    // Выводим результаты
    print_build_result(&result);

    if result.success {
        println!("\n✅ Сборка успешно завершена!");
        Ok(())
    } else {
        println!("\n❌ Сборка завершилась с ошибками!");
        Err(anyhow::anyhow!("Сборка не удалась"))
    }
}

/// Выводит результат сборки в удобном формате
fn print_build_result(result: &crate::models::plugin::BuildResult) {
    println!("{}", "=".repeat(60).bright_black());
    println!("📊 РЕЗУЛЬТАТЫ СБОРКИ");
    println!("{}", "=".repeat(60).bright_black());

    // Статус
    let status = if result.success {
        "✅ УСПЕХ".green()
    } else {
        "❌ ОШИБКА".red()
    };
    println!("Статус: {}", status);

    // Время сборки
    println!("Время: {}", result.build_time.format("%Y-%m-%d %H:%M:%S"));

    // Артефакт
    if let Some(ref artifact) = result.artifact {
        println!("\n📦 АРТЕФАКТ:");
        println!("  Имя файла: {}", artifact.file_name.bright_blue());
        println!("  Размер: {} bytes", artifact.file_size);
        println!("  Версия: {}", artifact.version.bright_green());
        println!("  SHA256: {}", artifact.checksum_sha256.bright_black());
        println!("  Путь: {}", artifact.file_path.display());
    } else {
        println!("\n❌ Артефакт не создан");
    }

    // Логи
    if !result.logs.is_empty() {
        println!("\n📝 ЛОГИ СБОРКИ:");
        for (i, log) in result.logs.iter().take(10).enumerate() {
            println!("  {} {}", (i + 1).to_string().bright_black(), log);
        }
        if result.logs.len() > 10 {
            println!("  ... и еще {} сообщений", result.logs.len() - 10);
        }
    }

    // Ошибки
    if !result.errors.is_empty() {
        println!("\n❌ ОШИБКИ:");
        for (i, error) in result.errors.iter().enumerate() {
            println!("  {}. {}", (i + 1).to_string().bright_red(), error.bright_red());
        }
    }

    println!("{}", "=".repeat(60).bright_black());
}