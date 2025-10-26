use clap::{Parser, Subcommand};

#[derive(Parser, Debug)]
pub struct AiCommand {
    #[command(subcommand)]
    pub subcommand: AiSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum AiSubcommand {
    /// Генерация changelog
    Changelog(ChangelogCommand),
    /// Предложение версии
    SuggestVersion(SuggestVersionCommand),
    /// Генерация release notes
    ReleaseNotes(ReleaseNotesCommand),
}

#[derive(Parser, Debug)]
pub struct ChangelogCommand {
    /// Начальный тег для анализа
    #[arg(long)]
    pub since: Option<String>,

    /// Подробный вывод
    #[arg(long)]
    pub verbose: bool,
}

#[derive(Parser, Debug)]
pub struct SuggestVersionCommand {
    /// Анализ коммитов для определения версии
    #[arg(long)]
    pub analyze_commits: bool,
}

#[derive(Parser, Debug)]
pub struct ReleaseNotesCommand {
    /// Шаблон для генерации
    #[arg(long)]
    pub template: Option<String>,
}