use clap::Parser;

#[derive(Parser, Debug)]
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