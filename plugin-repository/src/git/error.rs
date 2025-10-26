use anyhow::Result;
use thiserror::Error;
use tracing::{error, warn, debug, info};

/// Специфичные ошибки для Git операций
#[derive(Error, Debug)]
pub enum GitError {
    #[error("Git репозиторий не найден в директории: {path}")]
    RepositoryNotFound { path: String },

    #[error("Git команда завершилась с ошибкой: {command}")]
    CommandFailed { command: String, message: String },

    #[error("Некорректный ref: {ref_name}")]
    InvalidRef { ref_name: String },

    #[error("Тег не найден: {tag_name}")]
    TagNotFound { tag_name: String },

    #[error("Конфликт при слиянии: {conflict_details}")]
    MergeConflict { conflict_details: String },

    #[error("Рабочая директория не чиста, есть незакоммиченные изменения")]
    WorkingDirectoryDirty,

    #[error("Удаленный репозиторий недоступен: {remote_url}")]
    RemoteUnavailable { remote_url: String },

    #[error("Недостаточно прав для выполнения операции: {operation}")]
    InsufficientPermissions { operation: String },

    #[error("Некорректный формат версии: {version}")]
    InvalidVersionFormat { version: String },

    #[error("История изменений пуста или недоступна")]
    EmptyHistory,

    #[error("Ошибка парсинга git вывода: {output}")]
    ParseError { output: String },

    #[error("Таймаут выполнения git операции: {operation}")]
    TimeoutError { operation: String },

    #[error("Файл или директория заблокированы: {path}")]
    LockedFile { path: String },

    #[error("Пространство на диске закончилось")]
    DiskSpaceExhausted,

    #[error("Сетевая ошибка: {details}")]
    NetworkError { details: String },

    #[error("Внутренняя ошибка Git: {message}")]
    InternalError { message: String },
}

/// Результат выполнения Git операции с расширенной информацией
#[derive(Debug, Clone)]
pub struct GitOperationResult<T> {
    pub success: bool,
    pub data: Option<T>,
    pub warning: Option<String>,
    pub execution_time: std::time::Duration,
}

impl<T> GitOperationResult<T> {
    /// Создает успешный результат
    pub fn success(data: T, execution_time: std::time::Duration) -> Self {
        Self {
            success: true,
            data: Some(data),
            warning: None,
            execution_time,
        }
    }

    /// Создает успешный результат с предупреждением
    pub fn success_with_warning(data: T, warning: String, execution_time: std::time::Duration) -> Self {
        Self {
            success: true,
            data: Some(data),
            warning: Some(warning),
            execution_time,
        }
    }

    /// Создает неуспешный результат
    pub fn failure(execution_time: std::time::Duration) -> Self {
        Self {
            success: false,
            data: None,
            warning: None,
            execution_time,
        }
    }
}

/// Обработчик ошибок Git операций
#[derive(Debug, Clone)]
pub struct GitErrorHandler {
    repository_path: std::path::PathBuf,
}

impl GitErrorHandler {
    /// Создает новый обработчик ошибок
    pub fn new<P: AsRef<std::path::Path>>(repository_path: P) -> Self {
        Self {
            repository_path: repository_path.as_ref().to_path_buf(),
        }
    }

    /// Безопасно выполняет git операцию с обработкой ошибок
    pub async fn safe_execute<T, F, Fut>(&self, operation: F, operation_name: &str) -> Result<GitOperationResult<T>>
    where
        F: FnOnce() -> Fut,
        Fut: std::future::Future<Output = Result<T>>,
    {
        let start_time = std::time::Instant::now();

        debug!("Начинаю выполнение Git операции: {}", operation_name);

        match operation().await {
            Ok(data) => {
                let execution_time = start_time.elapsed();
                info!("✅ Git операция '{}' выполнена успешно за {:?}", operation_name, execution_time);

                // Проверяем на предупреждения
                let warning = self.check_for_warnings(&operation_name).await;

                match warning {
                    Some(w) => {
                        warn!("⚠️ Предупреждение при выполнении '{}': {}", operation_name, w);
                        Ok(GitOperationResult::success_with_warning(data, w, execution_time))
                    }
                    None => Ok(GitOperationResult::success(data, execution_time)),
                }
            }
            Err(e) => {
                let execution_time = start_time.elapsed();
                error!("❌ Ошибка выполнения Git операции '{}': {}", operation_name, e);

                // Классифицируем ошибку и пытаемся восстановиться
                let handled_error = self.handle_error(&e, operation_name).await?;

                Err(handled_error.into())
            }
        }
    }

    /// Проверяет наличие предупреждений после операции
    async fn check_for_warnings(&self, operation_name: &str) -> Option<String> {
        match operation_name {
            "get_commits_between" | "get_recent_commits" => {
                // Проверяем, есть ли очень много коммитов
                if let Ok(commit_count) = self.get_commit_count().await {
                    if commit_count > 1000 {
                        return Some(format!("Обнаружено большое количество коммитов: {}. Производительность может быть снижена.", commit_count));
                    }
                }
            }
            "create_tag" => {
                // Проверяем, существует ли уже тег
                if let Ok(existing_tags) = self.get_existing_tags().await {
                    // Здесь можно добавить логику проверки дубликатов
                }
            }
            _ => {}
        }
        None
    }

    /// Обрабатывает и классифицирует ошибки
    async fn handle_error(&self, error: &anyhow::Error, operation_name: &str) -> Result<GitError> {
        let error_string = error.to_string().to_lowercase();

        // Классифицируем тип ошибки
        if error_string.contains("not a git repository") || error_string.contains("файл не найден") {
            return Ok(GitError::RepositoryNotFound {
                path: self.repository_path.display().to_string(),
            });
        }

        if error_string.contains("permission denied") || error_string.contains("доступ запрещен") {
            return Ok(GitError::InsufficientPermissions {
                operation: operation_name.to_string(),
            });
        }

        if error_string.contains("ambiguous") || error_string.contains("unknown revision") {
            return Ok(GitError::InvalidRef {
                ref_name: operation_name.to_string(),
            });
        }

        if error_string.contains("fatal: not a valid object name") {
            return Ok(GitError::TagNotFound {
                tag_name: operation_name.to_string(),
            });
        }

        if error_string.contains("working tree has modifications") || error_string.contains("незакоммиченные изменения") {
            return Ok(GitError::WorkingDirectoryDirty);
        }

        if error_string.contains("merge conflict") || error_string.contains("конфликт слияния") {
            return Ok(GitError::MergeConflict {
                conflict_details: error_string,
            });
        }

        if error_string.contains("network") || error_string.contains("connection") {
            return Ok(GitError::NetworkError {
                details: error_string,
            });
        }

        if error_string.contains("timeout") || error_string.contains("таймаут") {
            return Ok(GitError::TimeoutError {
                operation: operation_name.to_string(),
            });
        }

        if error_string.contains("locked") || error_string.contains("заблокирован") {
            return Ok(GitError::LockedFile {
                path: self.repository_path.display().to_string(),
            });
        }

        if error_string.contains("no space") || error_string.contains("диск заполнен") {
            return Ok(GitError::DiskSpaceExhausted);
        }

        // Общая ошибка команды Git
        Ok(GitError::CommandFailed {
            command: operation_name.to_string(),
            message: error.to_string(),
        })
    }

    /// Пытается восстановиться после ошибки
    pub async fn attempt_recovery(&self, error: &GitError) -> Result<RecoveryAction> {
        match error {
            GitError::WorkingDirectoryDirty => {
                debug!("Попытка восстановления: проверка статуса рабочей директории");
                if self.can_stash_changes().await? {
                    Ok(RecoveryAction::StashChanges)
                } else {
                    Ok(RecoveryAction::AbortOperation)
                }
            }
            GitError::NetworkError { .. } => {
                debug!("Попытка восстановления: проверка сетевого подключения");
                if self.check_network_connectivity().await? {
                    Ok(RecoveryAction::RetryOperation)
                } else {
                    Ok(RecoveryAction::AbortOperation)
                }
            }
            GitError::LockedFile { .. } => {
                debug!("Попытка восстановления: ожидание разблокировки файла");
                Ok(RecoveryAction::WaitAndRetry)
            }
            GitError::TimeoutError { .. } => {
                debug!("Попытка восстановления: повтор с увеличенным таймаутом");
                Ok(RecoveryAction::RetryWithTimeout)
            }
            _ => Ok(RecoveryAction::AbortOperation),
        }
    }

    /// Возвращает количество коммитов в репозитории
    async fn get_commit_count(&self) -> Result<usize> {
        // Здесь должна быть реальная реализация
        Ok(0)
    }

    /// Возвращает существующие теги
    async fn get_existing_tags(&self) -> Result<Vec<String>> {
        // Здесь должна быть реальная реализация
        Ok(Vec::new())
    }

    /// Проверяет, можно ли спрятать изменения
    async fn can_stash_changes(&self) -> Result<bool> {
        // Здесь должна быть реальная реализация
        Ok(true)
    }

    /// Проверяет сетевое подключение
    async fn check_network_connectivity(&self) -> Result<bool> {
        // Здесь должна быть реальная реализация
        Ok(true)
    }
}

/// Действие по восстановлению после ошибки
#[derive(Debug, Clone)]
pub enum RecoveryAction {
    /// Повторить операцию
    RetryOperation,
    /// Повторить с увеличенным таймаутом
    RetryWithTimeout,
    /// Подождать и повторить
    WaitAndRetry,
    /// Спрятать изменения и продолжить
    StashChanges,
    /// Отменить операцию
    AbortOperation,
}

/// Валидатор для Git операций
#[derive(Debug, Clone)]
pub struct GitValidator {
    repository_path: std::path::PathBuf,
}

impl GitValidator {
    /// Создает новый валидатор
    pub fn new<P: AsRef<std::path::Path>>(repository_path: P) -> Self {
        Self {
            repository_path: repository_path.as_ref().to_path_buf(),
        }
    }

    /// Валидирует состояние репозитория перед операцией
    pub async fn validate_repository_state(&self) -> Result<ValidationResult> {
        let mut issues = Vec::new();
        let mut warnings = Vec::new();

        // Проверяем, является ли директория git репозиторием
        if !self.is_git_repository().await {
            issues.push("Директория не является Git репозиторием".to_string());
            return Ok(ValidationResult {
                is_valid: false,
                issues,
                warnings,
            });
        }

        // Проверяем наличие незакоммиченных изменений
        if self.has_uncommitted_changes().await {
            warnings.push("Есть незакоммиченные изменения".to_string());
        }

        // Проверяем доступность удаленных репозиториев
        if let Ok(remotes) = self.get_remotes().await {
            for remote in remotes {
                if !self.is_remote_available(&remote).await {
                    warnings.push(format!("Удаленный репозиторий '{}' недоступен", remote));
                }
            }
        }

        // Проверяем наличие дискового пространства
        if let Ok(space_info) = self.get_disk_space_info().await {
            if space_info.free_bytes < 100 * 1024 * 1024 { // < 100MB
                warnings.push("Мало свободного места на диске".to_string());
            }
        }

        let is_valid = issues.is_empty();

        Ok(ValidationResult {
            is_valid,
            issues,
            warnings,
        })
    }

    /// Проверяет, является ли директория git репозиторием
    async fn is_git_repository(&self) -> bool {
        self.repository_path.join(".git").exists()
    }

    /// Проверяет наличие незакоммиченных изменений
    async fn has_uncommitted_changes(&self) -> bool {
        // Здесь должна быть реальная реализация
        false
    }

    /// Получает список удаленных репозиториев
    async fn get_remotes(&self) -> Result<Vec<String>> {
        // Здесь должна быть реальная реализация
        Ok(vec!["origin".to_string()])
    }

    /// Проверяет доступность удаленного репозитория
    async fn is_remote_available(&self, remote: &str) -> bool {
        // Здесь должна быть реальная реализация
        true
    }

    /// Получает информацию о дисковом пространстве
    async fn get_disk_space_info(&self) -> Result<DiskSpaceInfo> {
        // Здесь должна быть реальная реализация
        Ok(DiskSpaceInfo {
            total_bytes: 1024 * 1024 * 1024, // 1GB
            free_bytes: 512 * 1024 * 1024,    // 512MB
        })
    }
}

/// Результат валидации
#[derive(Debug, Clone)]
pub struct ValidationResult {
    pub is_valid: bool,
    pub issues: Vec<String>,
    pub warnings: Vec<String>,
}

/// Информация о дисковом пространстве
#[derive(Debug, Clone)]
pub struct DiskSpaceInfo {
    pub total_bytes: u64,
    pub free_bytes: u64,
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_git_error_creation() {
        let error = GitError::RepositoryNotFound {
            path: "/tmp".to_string(),
        };
        assert!(error.to_string().contains("Git репозиторий не найден"));
    }

    #[test]
    fn test_operation_result_creation() {
        let duration = std::time::Duration::from_millis(100);
        let result = GitOperationResult::success("test_data".to_string(), duration);
        assert!(result.success);
        assert_eq!(result.data, Some("test_data".to_string()));
    }

    #[test]
    fn test_validation_result() {
        let result = ValidationResult {
            is_valid: false,
            issues: vec!["Issue 1".to_string()],
            warnings: vec!["Warning 1".to_string()],
        };
        assert!(!result.is_valid);
        assert_eq!(result.issues.len(), 1);
        assert_eq!(result.warnings.len(), 1);
    }
}