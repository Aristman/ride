use clap::Parser;

#[derive(Parser, Debug)]
pub struct StatusCommand {
    /// Показать последние релизы
    #[arg(long)]
    pub releases: bool,

    /// Показать статус репозитория
    #[arg(long)]
    pub repository: bool,

    /// Формат вывода
    #[arg(long, default_value = "table")]
    pub format: String,
}