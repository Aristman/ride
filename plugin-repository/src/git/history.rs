use anyhow::{Context, Result};
use std::path::Path;
use std::process::Command;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use tracing::{info, debug, warn};

/// Модель git коммита
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GitCommit {
    pub hash: String,
    pub short_hash: String,
    pub message: String,
    pub author: String,
    pub email: String,
    pub date: DateTime<Utc>,
    pub files_changed: u32,
    pub insertions: u32,
    pub deletions: u32,
}

/// Анализатор git истории
#[derive(Debug, Clone)]
pub struct GitHistory {
    repository_path: std::path::PathBuf,
}

impl GitHistory {
    /// Создает новый экземпляр анализатора
    pub fn new<P: AsRef<Path>>(repository_path: P) -> Self {
        Self {
            repository_path: repository_path.as_ref().to_path_buf(),
        }
    }

    /// Получает историю коммитов между двумя точками
    pub async fn get_commits_between(&self, from_ref: Option<&str>, to_ref: Option<&str>) -> Result<Vec<GitCommit>> {
        info!("📜 Получение истории коммитов между {:?} и {:?}", from_ref, to_ref);

        let range = match (from_ref, to_ref) {
            (Some(from), Some(to)) => format!("{}..{}", from, to),
            (Some(from), None) => format!("{}..HEAD", from),
            (None, Some(to)) => format!("HEAD..{}", to),
            (None, None) => "HEAD".to_string(),
        };

        debug!("Диапазон коммитов: {}", range);

        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&["log", "--pretty=format:%H|%h|%s|%an|%ae|%ai", "--numstat", &range])
            .output()
            .context("Ошибка выполнения git log")?;

        if !output.status.success() {
            let error_msg = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!(
                "Git log завершился с ошибкой: {}",
                error_msg
            ));
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        let commits = self.parse_git_log(&stdout)?;

        info!("Получено {} коммитов", commits.len());
        Ok(commits)
    }

    /// Получает последние N коммитов
    pub async fn get_recent_commits(&self, limit: u32) -> Result<Vec<GitCommit>> {
        info!("📜 Получение последних {} коммитов", limit);

        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&["log", "--pretty=format:%H|%h|%s|%an|%ae|%ai", "--numstat", &format!("-{}", limit)])
            .output()
            .context("Ошибка выполнения git log")?;

        if !output.status.success() {
            let error_msg = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!(
                "Git log завершился с ошибкой: {}",
                error_msg
            ));
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        let commits = self.parse_git_log(&stdout)?;

        info!("Получено {} коммитов", commits.len());
        Ok(commits)
    }

    /// Получает коммиты, изменяющие определённые файлы
    pub async fn get_commits_for_files(&self, file_patterns: &[&str]) -> Result<Vec<GitCommit>> {
        info!("📜 Получение коммитов для файлов: {:?}", file_patterns);

        let mut args = vec![
            "log",
            "--pretty=format:%H|%h|%s|%an|%ae|%ai",
            "--numstat",
            "--",
        ];

        args.extend(file_patterns.iter());

        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&args)
            .output()
            .context("Ошибка выполнения git log")?;

        if !output.status.success() {
            let error_msg = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!(
                "Git log завершился с ошибкой: {}",
                error_msg
            ));
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        let commits = self.parse_git_log(&stdout)?;

        info!("Получено {} коммитов для файлов", commits.len());
        Ok(commits)
    }

    /// Парсит вывод git log
    fn parse_git_log(&self, log_output: &str) -> Result<Vec<GitCommit>> {
        let mut commits = Vec::new();
        let mut current_commit: Option<GitCommit> = None;

        for line in log_output.lines() {
            if line.trim().is_empty() {
                continue;
            }

            // Проверяем, является ли строка заголовком коммита
            if line.contains('|') && line.chars().filter(|c| *c == '|').count() >= 5 {
                // Сохраняем предыдущий коммит, если он был
                if let Some(commit) = current_commit.take() {
                    commits.push(commit);
                }

                // Парсим новый заголовок коммита
                let parts: Vec<&str> = line.split('|').collect();
                if parts.len() >= 6 {
                    let date_str = parts[5];
                    let date = DateTime::parse_from_rfc3339(date_str)
                        .unwrap_or_else(|_| DateTime::parse_from_str(date_str, "%Y-%m-%d %H:%M:%S %z").unwrap_or_else(|_| Utc::now().into()))
                        .with_timezone(&Utc);

                    current_commit = Some(GitCommit {
                        hash: parts[0].to_string(),
                        short_hash: parts[1].to_string(),
                        message: parts[2].to_string(),
                        author: parts[3].to_string(),
                        email: parts[4].to_string(),
                        date,
                        files_changed: 0,
                        insertions: 0,
                        deletions: 0,
                    });
                }
            } else if let Some(ref mut commit) = current_commit {
                // Парсим статистику файлов
                if let Some((insertions, deletions)) = self.parse_file_stats_line(line) {
                    commit.insertions += insertions;
                    commit.deletions += deletions;
                    commit.files_changed += 1;
                }
            }
        }

        // Добавляем последний коммит
        if let Some(commit) = current_commit {
            commits.push(commit);
        }

        Ok(commits)
    }

    /// Парсит строку статистики файлов
    fn parse_file_stats_line(&self, line: &str) -> Option<(u32, u32)> {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() >= 2 {
            let (insertions, deletions) = (parts[0], parts[1]);

            let insertions = if insertions == "-" {
                0
            } else {
                insertions.parse().unwrap_or(0)
            };

            let deletions = if deletions == "-" {
                0
            } else {
                deletions.parse().unwrap_or(0)
            };

            Some((insertions, deletions))
        } else {
            None
        }
    }

    /// Получает форматированный changelog из git истории
    pub async fn get_formatted_changelog(&self, from_ref: Option<&str>, to_ref: Option<&str>) -> Result<String> {
        let commits = self.get_commits_between(from_ref, to_ref).await?;

        let mut changelog = String::new();

        for commit in &commits {
            changelog.push_str(&format!(
                "- {} ({}): {}\n",
                commit.short_hash,
                commit.date.format("%Y-%m-%d"),
                commit.message
            ));
        }

        Ok(changelog)
    }

    /// Проверяет, является ли репозиторий git репозиторием
    pub fn is_git_repository(&self) -> bool {
        self.repository_path.join(".git").exists()
    }

    /// Получает текущую ветку
    pub async fn get_current_branch(&self) -> Result<String> {
        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&["rev-parse", "--abbrev-ref", "HEAD"])
            .output()
            .context("Ошибка определения текущей ветки")?;

        if !output.status.success() {
            return Err(anyhow::anyhow!("Не удалось определить текущую ветку"));
        }

        let branch = String::from_utf8_lossy(&output.stdout).trim().to_string();
        Ok(branch)
    }

    /// Получает информацию о тегах
    pub async fn get_tags(&self) -> Result<Vec<String>> {
        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&["tag", "--sort=-version:refname"])
            .output()
            .context("Ошибка получения тегов")?;

        if !output.status.success() {
            return Err(anyhow::anyhow!("Не удалось получить список тегов"));
        }

        let tags: Vec<String> = String::from_utf8_lossy(&output.stdout)
            .lines()
            .filter_map(|line| {
                let line = line.trim();
                if line.starts_with('v') && line.chars().all(|c| c.is_alphanumeric() || c == '.' || c == '-') {
                    Some(line.to_string())
                } else {
                    None
                }
            })
            .collect();

        Ok(tags)
    }
}

/// Тип изменения для анализа
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Hash)]
pub enum ChangeType {
    Feature,
    Fix,
    Breaking,
    Improvement,
    Documentation,
    Testing,
    Refactoring,
    Chore,
    Other,
}

impl ChangeType {
    /// Определяет тип изменения из сообщения коммита
    pub fn from_message(message: &str) -> Self {
        let message_lower = message.to_lowercase();

        if message_lower.contains("break") || message_lower.contains("breaking") ||
           message_lower.contains("!:") || message_lower.starts_with("feat!") {
            ChangeType::Breaking
        } else if message_lower.starts_with("feat") || message_lower.contains("добавлен") ||
                  message_lower.contains("новый") || message_lower.contains("new feature") {
            ChangeType::Feature
        } else if message_lower.starts_with("fix") || message_lower.contains("исправлен") ||
                  message_lower.contains("фикс") || message_lower.contains("bug") {
            ChangeType::Fix
        } else if message_lower.starts_with("refactor") || message_lower.contains("рефакторинг") {
            ChangeType::Refactoring
        } else if message_lower.starts_with("docs") || message_lower.contains("документация") ||
                  message_lower.contains("документацию") {
            ChangeType::Documentation
        } else if message_lower.starts_with("test") || message_lower.contains("тест") {
            ChangeType::Testing
        } else if message_lower.starts_with("chore") || message_lower.contains("улучшение") {
            ChangeType::Improvement
        } else {
            ChangeType::Other
        }
    }

    /// Возвращает эмодзи для типа изменения
    pub fn emoji(&self) -> &'static str {
        match self {
            ChangeType::Feature => "🚀",
            ChangeType::Fix => "🐛",
            ChangeType::Breaking => "💥",
            ChangeType::Improvement => "🔧",
            ChangeType::Documentation => "📝",
            ChangeType::Testing => "🧪",
            ChangeType::Refactoring => "♻️",
            ChangeType::Chore => "🧹",
            ChangeType::Other => "📋",
        }
    }

    /// Возвращает название типа изменения
    pub fn name(&self) -> &'static str {
        match self {
            ChangeType::Feature => "Новые возможности",
            ChangeType::Fix => "Исправления",
            ChangeType::Breaking => "Критические изменения",
            ChangeType::Improvement => "Улучшения",
            ChangeType::Documentation => "Документация",
            ChangeType::Testing => "Тестирование",
            ChangeType::Refactoring => "Рефакторинг",
            ChangeType::Chore => "Обслуживание",
            ChangeType::Other => "Другое",
        }
    }
}