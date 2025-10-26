use clap::Parser;

#[derive(Parser, Debug)]
#[command(
    about = "Статус локального git-репозитория и релизов",
    long_about = "Показывает сводку по текущему git-репозиторию (ветка, теги) и список последних релизов. Поддерживает форматы вывода: table, json."
)]
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