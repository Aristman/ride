use clap::Parser;

#[derive(Parser, Debug)]
#[command(
    about = "Полный цикл публикации: build -> release -> deploy",
    long_about = "Выполняет сборку артефакта с указанной или автоматически определенной версией, создает git-релиз и деплоит в репозиторий."
)]
pub struct PublishCommand {
    /// Версия плагина (приоритетнее auto-version)
    #[arg(long)]
    pub version: Option<String>,

    /// Автоматически определить следующую версию и создать релиз
    #[arg(long)]
    pub auto_version: bool,

    /// Профиль сборки
    #[arg(short, long, default_value = "release")]
    pub profile: String,

    /// Принудительное создание релиза/деплой
    #[arg(long)]
    pub force: bool,

    /// Откат деплоя при неудаче
    #[arg(long)]
    pub rollback_on_failure: bool,

    /// Пропустить валидацию конфигурации
    #[arg(long)]
    pub skip_validation: bool,

    /// Пробный запуск (release с --dry-run, без деплоя)
    #[arg(long)]
    pub dry_run: bool,
}
