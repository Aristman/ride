use anyhow::{Context, Result};
use std::path::Path;
use std::process::Command;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use tracing::{info, debug, warn};
use super::history::GitCommit;

/// Модель git тега
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GitTag {
    pub name: String,
    pub commit_hash: String,
    pub commit_message: String,
    pub author: String,
    pub date: DateTime<Utc>,
    pub is_annotated: bool,
}

/// Менеджер работы с git тегами
#[derive(Debug, Clone)]
pub struct GitTags {
    repository_path: std::path::PathBuf,
}

impl GitTags {
    /// Создает новый экземпляр менеджера тегов
    pub fn new<P: AsRef<Path>>(repository_path: P) -> Self {
        Self {
            repository_path: repository_path.as_ref().to_path_buf(),
        }
    }

    /// Получает все теги в репозитории
    pub async fn get_all_tags(&self) -> Result<Vec<GitTag>> {
        info!("🏷️ Получение всех тегов репозитория");

        // Получаем список тегов с информацией о коммитах
        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&[
                "tag", "--sort=-version:refname", "--format=%(refname:short)%00%(objectname)%00%(contents:subject)%00%(authorname)%00%(creatordate)",
            ])
            .output()
            .context("Ошибка получения списка тегов")?;

        if !output.status.success() {
            let error_msg = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!(
                "Git tag завершился с ошибкой: {}",
                error_msg
            ));
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        let tags = self.parse_tags_output(&stdout)?;

        info!("Получено {} тегов", tags.len());
        Ok(tags)
    }

    /// Получает последний тег
    pub async fn get_latest_tag(&self) -> Result<Option<GitTag>> {
        info!("🏷️ Получение последнего тега");

        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&[
                "describe", "--tags", "--abbrev=0"
            ])
            .output()
            .context("Ошибка получения последнего тега")?;

        if !output.status.success() {
            let error_msg = String::from_utf8_lossy(&output.stderr);
            warn!("Не удалось получить последний тег: {}", error_msg);
            return Ok(None);
        }

        let tag_name = String::from_utf8_lossy(&output.stdout).trim().to_string();

        if tag_name.is_empty() {
            return Ok(None);
        }

        // Получаем полную информацию о теге
        self.get_tag_info(&tag_name).await.map(Some)
    }

    /// Получает информацию о конкретном теге
    pub async fn get_tag_info(&self, tag_name: &str) -> Result<GitTag> {
        debug!("Получение информации о теге: {}", tag_name);

        // Используем короткий формат одной строки, без diff и аннотаций
        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&[
                "show", "-s", "--no-patch", "--pretty=%H|%s|%an|%cI", tag_name
            ])
            .output()
            .context("Ошибка получения информации о теге")?;

        if !output.status.success() {
            let error_msg = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!(
                "Не удалось получить информацию о теге {}: {}",
                tag_name, error_msg
            ));
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        let line_opt = stdout
            .lines()
            .map(|l| l.trim())
            .find(|l| !l.is_empty() && l.matches('|').count() >= 3);

        let line = line_opt
            .ok_or_else(|| anyhow::anyhow!(
                "Пустой или некорректный ответ от git show для тега {}: {}",
                tag_name, stdout.trim()
            ))?;

        let parts: Vec<&str> = line.split('|').collect();
        if parts.len() < 4 {
            return Err(anyhow::anyhow!(
                "Некорректный формат вывода git show для тега {}: {}",
                tag_name, line
            ));
        }

        let commit_hash = parts[0].to_string();
        let commit_message = parts[1].to_string();
        let author = parts[2].to_string();
        let date_str = parts[3];

        // %cI выдаёт ISO 8601 (RFC3339), разбираем строго; при сбое — используем текущее время
        let date = DateTime::parse_from_rfc3339(date_str)
            .map(|d| d.with_timezone(&Utc))
            .unwrap_or_else(|_| Utc::now());

        // Проверяем, является ли тег аннотированным
        let is_annotated = self.is_annotated_tag(tag_name).await?;

        Ok(GitTag {
            name: tag_name.to_string(),
            commit_hash,
            commit_message,
            author,
            date,
            is_annotated,
        })
    }

    /// Создает новый тег
    pub async fn create_tag(&self, tag_name: &str, message: Option<&str>) -> Result<()> {
        info!("🏷️ Создание тега: {}", tag_name);

        let mut args = vec!["tag"];

        if let Some(msg) = message {
            args.push("-a");
            args.push("-m");
            args.push(msg);
        }

        args.push(tag_name);

        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&args)
            .output()
            .context("Ошибка создания тега")?;

        if !output.status.success() {
            let error_msg = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!(
                "Не удалось создать тег {}: {}",
                tag_name, error_msg
            ));
        }

        info!("✅ Тег {} успешно создан", tag_name);
        Ok(())
    }

    /// Удаляет тег
    pub async fn delete_tag(&self, tag_name: &str) -> Result<()> {
        info!("🗑️ Удаление тега: {}", tag_name);

        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&["tag", "-d", tag_name])
            .output()
            .context("Ошибка удаления тега")?;

        if !output.status.success() {
            let error_msg = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!(
                "Не удалось удалить тег {}: {}",
                tag_name, error_msg
            ));
        }

        info!("✅ Тег {} успешно удален", tag_name);
        Ok(())
    }

    /// Получает коммиты между двумя тегами
    pub async fn get_commits_between_tags(&self, from_tag: &str, to_tag: &str) -> Result<Vec<GitCommit>> {
        info!("📜 Получение коммитов между тегами {}..{}", from_tag, to_tag);

        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&[
                "log",
                "--pretty=format:%H|%h|%s|%an|%ae|%ai",
                "--numstat",
                &format!("{}..{}", from_tag, to_tag)
            ])
            .output()
            .context("Ошибка получения коммитов между тегами")?;

        if !output.status.success() {
            let error_msg = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!(
                "Не удалось получить коммиты между тегами: {}",
                error_msg
            ));
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        let commits = self.parse_git_log(&stdout)?;

        info!("Получено {} коммитов между тегами", commits.len());
        Ok(commits)
    }

    /// Получает коммиты от последнего тега до HEAD
    pub async fn get_commits_since_last_tag(&self) -> Result<Vec<GitCommit>> {
        if let Some(latest_tag) = self.get_latest_tag().await? {
            self.get_commits_between_tags(&latest_tag.name, "HEAD").await
        } else {
            // Если тегов нет, возвращаем последние коммиты
            super::GitHistory::new(&self.repository_path).get_recent_commits(10).await
        }
    }

    /// Возвращает следующий номер версии на основе существующих тегов
    pub async fn suggest_next_version(&self, current_pattern: &str) -> Result<String> {
        info!("🔍 Предложение следующей версии на основе тегов");

        let tags = self.get_all_tags().await?;
        let matching_tags: Vec<&GitTag> = tags.iter()
            .filter(|tag| tag.name.starts_with(current_pattern))
            .collect();

        if matching_tags.is_empty() {
            return Ok(format!("{}0.1.0", current_pattern));
        }

        // Находим последнюю версию
        let latest_tag = matching_tags.first().unwrap();
        let current_version = &latest_tag.name;

        // Простое инкрементирование версии
        if let Some(version_number) = current_version.strip_prefix(current_pattern) {
            if let Some(version) = self.increment_version(version_number) {
                return Ok(format!("{}{}", current_pattern, version));
            }
        }

        Ok(format!("{}1.0.0", current_pattern))
    }

    /// Инкрементирует версию по semantic versioning
    fn increment_version(&self, version: &str) -> Option<String> {
        let parts: Vec<&str> = version.split('.').collect();
        if parts.len() != 3 {
            return None;
        }

        let major: u32 = parts[0].parse().ok()?;
        let minor: u32 = parts[1].parse().ok()?;
        let patch: u32 = parts[2].parse().ok()?;

        // Простое правило: инкрементируем patch версию
        Some(format!("{}.{}.{}", major, minor, patch + 1))
    }

    /// Проверяет, является ли тег аннотированным
    async fn is_annotated_tag(&self, tag_name: &str) -> Result<bool> {
        let output = Command::new("git")
            .current_dir(&self.repository_path)
            .args(&["cat-file", "-p", &format!("refs/tags/{}", tag_name)])
            .output()
            .context("Ошибка проверки типа тега")?;

        // Аннотированные теги содержат информацию о теге
        Ok(output.status.success())
    }

    /// Парсит вывод git log (используется в GitHistory)
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

    /// Парсит вывод git tags
    fn parse_tags_output(&self, output: &str) -> Result<Vec<GitTag>> {
        let mut tags = Vec::new();

        for line in output.lines() {
            if line.trim().is_empty() {
                continue;
            }

            // Формат: tag_name|commit_hash|message|author|date
            let parts: Vec<&str> = line.split('\x00').collect();
            if parts.len() < 5 {
                continue;
            }

            let tag_name = parts[0].trim().to_string();
            let commit_hash = parts[1].trim().to_string();
            let commit_message = parts[2].trim().to_string();
            let author = parts[3].trim().to_string();
            let date_str = parts[4].trim();

            let date = DateTime::parse_from_rfc3339(date_str)
                .unwrap_or_else(|_| DateTime::parse_from_str(date_str, "%Y-%m-%d %H:%M:%S %z").unwrap_or_else(|_| Utc::now().into()))
                .with_timezone(&Utc);

            // Проверяем, является ли тег аннотированным
            let is_annotated = false; // TODO: сделать async при необходимости

            tags.push(GitTag {
                name: tag_name,
                commit_hash,
                commit_message,
                author,
                date,
                is_annotated,
            });
        }

        Ok(tags)
    }
}

