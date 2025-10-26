// Заглушка для прогресс-баров
#[cfg(not(no_std))]
use indicatif::{ProgressBar as IndicatifBar, ProgressStyle};

/// Простой враппер над indicatif::ProgressBar
#[derive(Debug)]
pub struct ProgressBar {
    inner: Option<IndicatifBar>,
}

#[cfg(not(no_std))]
impl ProgressBar {
    pub fn new_spinner() -> Self {
        let bar = IndicatifBar::new_spinner();
        bar.set_style(
            ProgressStyle::with_template("{spinner:.green} {msg}")
                .unwrap()
                .tick_chars("⠁⠂⠄⡀⢀⠠⠐⠈ "),
        );
        Self { inner: Some(bar) }
    }

    pub fn set_message<S: Into<String>>(&self, msg: S) {
        if let Some(ref bar) = self.inner {
            bar.set_message(msg.into());
        }
    }

    pub fn tick(&self) {
        if let Some(ref bar) = self.inner {
            bar.tick();
        }
    }

    pub fn finish_with_message<S: Into<String>>(&self, msg: S) {
        if let Some(ref bar) = self.inner {
            bar.finish_with_message(msg.into());
        }
    }
}

#[cfg(not(no_std))]
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_progress_bar_api() {
        let pb = ProgressBar::new_spinner();
        pb.set_message("Работаем...");
        pb.tick();
        pb.finish_with_message("Готово");
    }
}