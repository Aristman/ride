use clap::Parser;

#[derive(Parser, Debug)]
#[command(
    about = "Проверка конфигурации и окружения",
    long_about = "Проверяет корректность config.toml и переменных окружения. Доступны частичные проверки: метаданные и совместимость."
)]
pub struct ValidateCommand {
    /// Валидация метаданных плагина
    #[arg(long)]
    pub metadata: bool,

    /// Проверка совместимости
    #[arg(long)]
    pub compatibility: bool,

    /// Полная валидация
    #[arg(long)]
    pub full: bool,
}