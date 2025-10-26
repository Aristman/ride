use clap::Parser;

#[derive(Parser, Debug)]
pub struct ReleaseCommand {
    /// Предварительный запуск без реальных изменений
    #[arg(long)]
    pub dry_run: bool,

    /// Автоматическое определение версии
    #[arg(long)]
    pub auto_version: bool,

    /// Уровень детализации вывода
    #[arg(short, long)]
    pub verbose: bool,
}