use clap::Parser;

#[derive(Parser, Debug)]
pub struct ReleaseCommand {
    /// Предварительный запуск без реальных изменений
    #[arg(long)]
    pub dry_run: bool,

    /// Автоматическое определение версии
    #[arg(long)]
    pub auto_version: bool,

    /// Указать конкретную версию для релиза
    #[arg(long)]
    pub version: Option<String>,

    /// Создать релиз без публикации
    #[arg(long)]
    pub no_publish: bool,

    /// Пропустить валидацию
    #[arg(long)]
    pub skip_validation: bool,

    /// Сохранить release notes в файл
    #[arg(long)]
    pub save_notes: Option<String>,

    /// Сохранить changelog в файл
    #[arg(long)]
    pub save_changelog: Option<String>,

    /// Уровень детализации вывода
    #[arg(short, long)]
    pub verbose: bool,

    /// Отобразить историю релизов
    #[arg(long)]
    pub history: bool,

    /// Количество последних релизов для отображения
    #[arg(long, default_value = "10")]
    pub limit: usize,

    /// Откатить указанный релиз
    #[arg(long)]
    pub rollback: Option<String>,

    /// Принудительно создать релиз (игнорировать предупреждения)
    #[arg(long)]
    pub force: bool,
}