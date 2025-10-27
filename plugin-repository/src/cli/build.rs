use clap::Parser;

#[derive(Parser, Debug)]
pub struct BuildCommand {
    /// Версия плагина для сборки
    #[arg(short, long)]
    pub version: Option<String>,

    /// Профиль сборки
    #[arg(short, long, default_value = "release")]
    pub profile: String,
}