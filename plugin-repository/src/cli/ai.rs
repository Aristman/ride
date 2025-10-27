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

    /// Конечный тег для анализа
    #[arg(long)]
    pub to: Option<String>,

    /// Подробный вывод
    #[arg(long)]
    pub verbose: bool,

    /// Использовать улучшенный Git анализ
    #[arg(long)]
    pub use_git_analysis: bool,

    /// Сохранить changelog в файл
    #[arg(long)]
    pub output: Option<String>,
}

#[derive(Parser, Debug)]
pub struct SuggestVersionCommand {
    /// Текущая версия для анализа
    #[arg(long)]
    pub current_version: Option<String>,

    /// Анализ коммитов для определения версии
    #[arg(long)]
    pub analyze_commits: bool,

    /// Использовать семантический анализ версий
    #[arg(long)]
    pub use_semantic_analysis: bool,

    /// Применить предложенную версию (создать тег)
    #[arg(long)]
    pub apply: bool,
}

#[derive(Parser, Debug)]
pub struct ReleaseNotesCommand {
    /// Шаблон для генерации
    #[arg(long)]
    pub template: Option<String>,

    /// Сохранить release notes в файл
    #[arg(long)]
    pub output: Option<String>,
}