use clap::Parser;

#[derive(Parser, Debug)]
pub struct DeployCommand {
    /// Принудительное развертывание
    #[arg(long)]
    pub force: bool,

    /// Откат при неудаче
    #[arg(long)]
    pub rollback_on_failure: bool,

    /// Пропуск валидации
    #[arg(long)]
    pub skip_validation: bool,
}