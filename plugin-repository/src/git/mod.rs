//! Модуль для работы с Git репозиторием и анализа изменений
//!
//! Предоставляет функционал для:
//! - Анализа истории коммитов
//! - Работы с git тегами
//! - Детекции типов изменений
//! - Интеграции с LLM агентами для генерации контента

pub mod history;
pub mod tags;
pub mod analyzer;
pub mod error;

pub use history::{GitHistory, GitCommit, ChangeType};
pub use tags::{GitTags, GitTag};
pub use analyzer::{ChangeAnalyzer, ChangeAnalysis, ReleaseAnalysis, ImpactLevel, VersionBump};
pub use error::{GitError, GitOperationResult, GitErrorHandler, GitValidator, ValidationResult, RecoveryAction};

use anyhow::Result;
use std::path::Path;

/// Единый интерфейс для работы с Git репозиторием
#[derive(Debug, Clone)]
pub struct GitRepository {
    pub path: std::path::PathBuf,
    pub history: GitHistory,
    pub tags: GitTags,
    pub analyzer: ChangeAnalyzer,
    error_handler: GitErrorHandler,
    validator: GitValidator,
}

impl GitRepository {
    /// Создает новый экземпляр репозитория
    pub fn new<P: AsRef<Path>>(repository_path: P) -> Self {
        let path = repository_path.as_ref().to_path_buf();

        Self {
            path: path.clone(),
            history: GitHistory::new(&path),
            tags: GitTags::new(&path),
            analyzer: ChangeAnalyzer::new(&path),
            error_handler: GitErrorHandler::new(&path),
            validator: GitValidator::new(&path),
        }
    }

    /// Проверяет, является ли директория git репозиторием
    pub fn is_valid_repository(&self) -> bool {
        self.history.is_git_repository()
    }

    /// Получает полную информацию о последних изменениях
    pub async fn get_full_analysis(&self, from_tag: Option<&str>, to_tag: Option<&str>) -> Result<(ReleaseAnalysis, Vec<GitCommit>)> {
        let analysis = self.analyzer.analyze_changes(from_tag, to_tag).await?;
        let commits = self.history.get_commits_between(from_tag, to_tag).await?;
        Ok((analysis, commits))
    }

    /// Получает сводку изменений с последнего тега
    pub async fn get_changes_since_last_release(&self) -> Result<(ReleaseAnalysis, Vec<GitCommit>, Option<GitTag>)> {
        let latest_tag = self.tags.get_latest_tag().await?;

        let (analysis, commits) = if let Some(ref tag) = latest_tag {
            self.get_full_analysis(Some(&tag.name), Some("HEAD")).await?
        } else {
            let analysis = self.analyzer.get_recent_summary(20).await?;
            let commits = self.history.get_recent_commits(20).await?;
            (analysis, commits)
        };

        Ok((analysis, commits, latest_tag))
    }

    /// Получает форматированный changelog для релиза
    pub async fn generate_changelog(&self, from_tag: Option<&str>, to_tag: Option<&str>) -> Result<String> {
        let (analysis, commits) = self.get_full_analysis(from_tag, to_tag).await?;

        let mut changelog = String::new();

        // Заголовок
        if let (Some(from), Some(to)) = (from_tag, to_tag) {
            changelog.push_str(&format!("## Изменения с {} по {}\n\n", from, to));
        } else if let Some(to) = to_tag {
            changelog.push_str(&format!("## Изменения для {}\n\n", to));
        } else {
            changelog.push_str("## Последние изменения\n\n");
        }

        // Группируем коммиты по типам изменений
        let mut grouped_commits: std::collections::HashMap<ChangeType, Vec<&GitCommit>> = std::collections::HashMap::new();

        for commit in &commits {
            let change_type = ChangeType::from_message(&commit.message);
            grouped_commits.entry(change_type).or_insert_with(Vec::new).push(commit);
        }

        // Выводим группы в правильном порядке
        let type_order = [
            ChangeType::Breaking,
            ChangeType::Feature,
            ChangeType::Fix,
            ChangeType::Improvement,
            ChangeType::Refactoring,
            ChangeType::Documentation,
            ChangeType::Testing,
            ChangeType::Chore,
            ChangeType::Other,
        ];

        for change_type in &type_order {
            if let Some(commits_of_type) = grouped_commits.get(change_type) {
                if !commits_of_type.is_empty() {
                    changelog.push_str(&format!("### {} {}\n\n", change_type.emoji(), change_type.name()));

                    for commit in commits_of_type {
                        changelog.push_str(&format!("- {} ({}): {}\n",
                            commit.short_hash,
                            commit.date.format("%Y-%m-%d"),
                            commit.message));
                    }
                    changelog.push('\n');
                }
            }
        }

        // Добавляем статистику
        changelog.push_str("---\n");
        changelog.push_str(&format!("**Статистика:** {} коммитов\n", commits.len()));

        if !analysis.breaking_changes.is_empty() {
            changelog.push_str(&format!("**⚠️ Критических изменений:** {}\n", analysis.breaking_changes.len()));
        }

        Ok(changelog)
    }

    /// Рекомендует следующую версию на основе анализа изменений
    pub async fn suggest_next_version(&self, current_version: &str) -> Result<String> {
        let (analysis, _, _) = self.get_changes_since_last_release().await?;

        // Базовая логика версионирования
        match analysis.recommended_version_bump {
            VersionBump::Major => {
                if semver::Version::parse(current_version).is_ok() {
                    let mut version = semver::Version::parse(current_version).unwrap();
                    version.major += 1;
                    version.minor = 0;
                    version.patch = 0;
                    Ok(version.to_string())
                } else {
                    Ok(format!("{}.0.0",
                        current_version.split('.').next().unwrap_or("1").parse::<u32>().unwrap_or(1) + 1))
                }
            }
            VersionBump::Minor => {
                if semver::Version::parse(current_version).is_ok() {
                    let mut version = semver::Version::parse(current_version).unwrap();
                    version.minor += 1;
                    version.patch = 0;
                    Ok(version.to_string())
                } else {
                    let parts: Vec<&str> = current_version.split('.').collect();
                    if parts.len() >= 2 {
                        let major = parts[0].parse::<u32>().unwrap_or(1);
                        let minor = parts[1].parse::<u32>().unwrap_or(0) + 1;
                        Ok(format!("{}.{}.0", major, minor))
                    } else {
                        Ok(format!("{}.1.0", parts[0].parse::<u32>().unwrap_or(1)))
                    }
                }
            }
            VersionBump::Patch => {
                if semver::Version::parse(current_version).is_ok() {
                    let mut version = semver::Version::parse(current_version).unwrap();
                    version.patch += 1;
                    Ok(version.to_string())
                } else {
                    let parts: Vec<&str> = current_version.split('.').collect();
                    if parts.len() >= 3 {
                        let major = parts[0].parse::<u32>().unwrap_or(1);
                        let minor = parts[1].parse::<u32>().unwrap_or(0);
                        let patch = parts[2].parse::<u32>().unwrap_or(0) + 1;
                        Ok(format!("{}.{}.{}", major, minor, patch))
                    } else if parts.len() == 2 {
                        let major = parts[0].parse::<u32>().unwrap_or(1);
                        let minor = parts[1].parse::<u32>().unwrap_or(0);
                        Ok(format!("{}.{}.1", major, minor))
                    } else {
                        Ok(format!("{}.0.1", parts[0].parse::<u32>().unwrap_or(1)))
                    }
                }
            }
            VersionBump::Custom(version) => Ok(version),
        }
    }

    /// Безопасно выполняет операцию с валидацией и обработкой ошибок
    pub async fn safe_execute_operation<T, F, Fut>(&self, operation: F, operation_name: &str) -> Result<GitOperationResult<T>>
    where
        F: FnOnce() -> Fut,
        Fut: std::future::Future<Output = Result<T>>,
    {
        // Сначала валидируем состояние репозитория
        let validation = self.validator.validate_repository_state().await?;
        if !validation.is_valid {
            return Err(anyhow::anyhow!("Валидация репозитория не пройдена: {:?}", validation.issues));
        }

        // Выводим предупреждения если они есть
        if !validation.warnings.is_empty() {
            tracing::warn!("Предупреждения валидации: {:?}", validation.warnings);
        }

        // Выполняем операцию с обработкой ошибок
        self.error_handler.safe_execute(operation, operation_name).await
    }

    /// Безопасно получает полную информацию о последних изменениях
    pub async fn safe_get_full_analysis(&self, from_tag: Option<&str>, to_tag: Option<&str>) -> Result<GitOperationResult<(ReleaseAnalysis, Vec<GitCommit>)>> {
        self.safe_execute_operation(
            || async { self.get_full_analysis(from_tag, to_tag).await },
            "get_full_analysis",
        ).await
    }

    /// Безопасно получает сводку изменений с последнего тега
    pub async fn safe_get_changes_since_last_release(&self) -> Result<GitOperationResult<(ReleaseAnalysis, Vec<GitCommit>, Option<GitTag>)>> {
        self.safe_execute_operation(
            || async { self.get_changes_since_last_release().await },
            "get_changes_since_last_release",
        ).await
    }

    /// Безопасно генерирует changelog
    pub async fn safe_generate_changelog(&self, from_tag: Option<&str>, to_tag: Option<&str>) -> Result<GitOperationResult<String>> {
        self.safe_execute_operation(
            || async { self.generate_changelog(from_tag, to_tag).await },
            "generate_changelog",
        ).await
    }

    /// Валидирует состояние репозитория
    pub async fn validate(&self) -> Result<ValidationResult> {
        self.validator.validate_repository_state().await
    }

    /// Получает статистику по репозиторию
    pub async fn get_repository_stats(&self) -> Result<RepositoryStats> {
        let total_commits = self.history.get_recent_commits(1).await.map(|c| c.len() as u32).unwrap_or(0);
        let total_tags = self.tags.get_all_tags().await.map(|t| t.len() as u32).unwrap_or(0);

        let (analysis, _, _) = self.get_changes_since_last_release().await?;
        let recent_commits = analysis.total_commits as u32;

        let stats = RepositoryStats {
            total_commits,
            total_tags,
            recent_commits,
            last_analysis: analysis,
            repository_path: self.path.clone(),
            is_healthy: self.validator.validate_repository_state().await.map(|v| v.is_valid).unwrap_or(false),
        };

        Ok(stats)
    }
}

/// Статистика репозитория
#[derive(Debug, Clone)]
pub struct RepositoryStats {
    pub total_commits: u32,
    pub total_tags: u32,
    pub recent_commits: u32,
    pub last_analysis: ReleaseAnalysis,
    pub repository_path: std::path::PathBuf,
    pub is_healthy: bool,
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;
    use std::process::Command;

    fn create_test_repo() -> (TempDir, GitRepository) {
        let temp_dir = TempDir::new().unwrap();
        let repo_path = temp_dir.path();

        // Инициализируем git репозиторий
        Command::new("git")
            .arg("init")
            .current_dir(repo_path)
            .output()
            .expect("Failed to init git repo");

        Command::new("git")
            .args(&["config", "user.name", "Test User"])
            .current_dir(repo_path)
            .output()
            .expect("Failed to set git user");

        Command::new("git")
            .args(&["config", "user.email", "test@example.com"])
            .current_dir(repo_path)
            .output()
            .expect("Failed to set git email");

        let repo = GitRepository::new(repo_path);
        (temp_dir, repo)
    }

    #[test]
    fn test_repository_creation() {
        let (_temp_dir, repo) = create_test_repo();
        assert!(repo.is_valid_repository());
    }

    #[tokio::test]
    async fn test_change_analysis() {
        let (_temp_dir, repo) = create_test_repo();

        // Создаем тестовый файл и коммит
        let test_file = repo.path.join("test.txt");
        std::fs::write(&test_file, "Hello, World!").unwrap();

        Command::new("git")
            .args(&["add", "test.txt"])
            .current_dir(&repo.path)
            .output()
            .expect("Failed to add file");

        Command::new("git")
            .args(&["commit", "-m", "feat: add test file"])
            .current_dir(&repo.path)
            .output()
            .expect("Failed to commit");

        // Получаем анализ
        let analysis = repo.analyzer.get_recent_summary(10).await.unwrap();
        assert_eq!(analysis.total_commits, 1);
        assert!(analysis.change_summary.contains_key(&ChangeType::Feature));
    }
}