use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use tracing::{info, debug, warn};
use super::history::{GitHistory, GitCommit, ChangeType};

/// Анализатор изменений для определения типа и влияния коммитов
#[derive(Debug, Clone)]
pub struct ChangeAnalyzer {
    repository_path: std::path::PathBuf,
    git_history: GitHistory,
    change_patterns: HashMap<ChangeType, Vec<String>>,
}

/// Детальный анализ изменений
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChangeAnalysis {
    pub change_type: ChangeType,
    pub confidence: f32,
    pub affected_areas: Vec<String>,
    pub breaking_changes: bool,
    pub description: String,
    pub impact_level: ImpactLevel,
}

/// Уровень влияния изменений
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, Hash)]
pub enum ImpactLevel {
    Low,      // Низкое влияние (документация, тесты)
    Medium,   // Среднее влияние (багфиксы, улучшения)
    High,     // Высокое влияние (новые функции)
    Critical, // Критическое влияние (breaking changes)
}

/// Сводный анализ релиза
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReleaseAnalysis {
    pub version_from: String,
    pub version_to: Option<String>,
    pub total_commits: usize,
    pub change_summary: HashMap<ChangeType, usize>,
    pub impact_distribution: HashMap<ImpactLevel, usize>,
    pub breaking_changes: Vec<String>,
    pub recommended_version_bump: VersionBump,
    pub confidence: f32,
}

/// Рекомендация по изменению версии
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum VersionBump {
    Patch,   // 0.0.1 -> 0.0.2
    Minor,   // 0.1.0 -> 0.2.0
    Major,   // 1.0.0 -> 2.0.0
    Custom(String), // Кастомная версия
}

impl ChangeAnalyzer {
    /// Создает новый анализатор изменений
    pub fn new<P: AsRef<std::path::Path>>(repository_path: P) -> Self {
        let path = repository_path.as_ref().to_path_buf();
        let git_history = GitHistory::new(&path);

        let mut change_patterns = HashMap::new();

        // Паттерны для новых функций
        change_patterns.insert(ChangeType::Feature, vec![
            r"(?i)^(feat|feature)[\(\[].*[\)\]:]?".to_string(),
            r"(?i)добавлен".to_string(),
            r"(?i)новый".to_string(),
            r"(?i)new feature".to_string(),
            r"(?i)реализован".to_string(),
            r"(?i)создан".to_string(),
        ]);

        // Паттерны для исправлений
        change_patterns.insert(ChangeType::Fix, vec![
            r"(?i)^(fix|bugfix)[\(\[].*[\)\]:]?".to_string(),
            r"(?i)исправлен".to_string(),
            r"(?i)фикс".to_string(),
            r"(?i)bug".to_string(),
            r"(?i)ошибка".to_string(),
            r"(?i)проблема".to_string(),
        ]);

        // Паттерны для критических изменений
        change_patterns.insert(ChangeType::Breaking, vec![
            r"(?i)break".to_string(),
            r"(?i)breaking".to_string(),
            r"(?i)!:".to_string(),
            r"(?i)feat!".to_string(),
            r"(?i)критический".to_string(),
            r"(?i)несовместимый".to_string(),
        ]);

        // Паттерны для улучшений
        change_patterns.insert(ChangeType::Improvement, vec![
            r"(?i)^(improve|improvement)[\(\[].*[\)\]:]?".to_string(),
            r"(?i)улучшение".to_string(),
            r"(?i)оптимизация".to_string(),
            r"(?i)refactor".to_string(),
            r"(?i)рефакторинг".to_string(),
        ]);

        // Паттерны для документации
        change_patterns.insert(ChangeType::Documentation, vec![
            r"(?i)^(docs|doc)[\(\[].*[\)\]:]?".to_string(),
            r"(?i)документация".to_string(),
            r"(?i)документацию".to_string(),
            r"(?i)readme".to_string(),
        ]);

        // Паттерны для тестов
        change_patterns.insert(ChangeType::Testing, vec![
            r"(?i)^(test|tests)[\(\[].*[\)\]:]?".to_string(),
            r"(?i)тест".to_string(),
            r"(?i)тестирование".to_string(),
            r"(?i)spec".to_string(),
        ]);

        // Паттерны для рефакторинга
        change_patterns.insert(ChangeType::Refactoring, vec![
            r"(?i)^(refactor|refact)[\(\[].*[\)\]:]?".to_string(),
            r"(?i)рефакторинг".to_string(),
            r"(?i)реорганизация".to_string(),
            r"(?i)реструктуризация".to_string(),
        ]);

        // Паттерны для обслуживания
        change_patterns.insert(ChangeType::Chore, vec![
            r"(?i)^(chore|build|ci)[\(\[].*[\)\]:]?".to_string(),
            r"(?i)обслуживание".to_string(),
            r"(?i)обновление зависимостей".to_string(),
            r"(?i)настройка".to_string(),
        ]);

        Self {
            repository_path: path,
            git_history,
            change_patterns,
        }
    }

    /// Анализирует отдельный коммит
    pub async fn analyze_commit(&self, commit: &GitCommit) -> Result<ChangeAnalysis> {
        debug!("Анализ коммита: {}", commit.short_hash);

        let change_type = self.detect_change_type(&commit.message);
        let confidence = self.calculate_confidence(&commit.message, &change_type);
        let affected_areas = self.extract_affected_areas(&commit.message);
        let breaking_changes = self.is_breaking_change(&commit.message);
        let impact_level = self.determine_impact_level(&change_type, &commit);
        let description = self.generate_description(&commit.message, &change_type);

        Ok(ChangeAnalysis {
            change_type,
            confidence,
            affected_areas,
            breaking_changes,
            description,
            impact_level,
        })
    }

    /// Анализирует изменения между двумя точками
    pub async fn analyze_changes(&self, from_ref: Option<&str>, to_ref: Option<&str>) -> Result<ReleaseAnalysis> {
        info!("📊 Анализ изменений между {:?} и {:?}", from_ref, to_ref);

        let commits = self.git_history.get_commits_between(from_ref, to_ref).await?;
        let total_commits = commits.len();

        let mut change_summary = HashMap::new();
        let mut impact_distribution = HashMap::new();
        let mut breaking_changes = Vec::new();

        debug!("Анализ {} коммитов", total_commits);

        for commit in &commits {
            let analysis = self.analyze_commit(commit).await?;

            *change_summary.entry(analysis.change_type.clone()).or_insert(0) += 1;
            *impact_distribution.entry(analysis.impact_level.clone()).or_insert(0) += 1;

            if analysis.breaking_changes {
                breaking_changes.push(format!("{}: {}", commit.short_hash, commit.message));
            }
        }

        let recommended_bump = self.recommend_version_bump(&change_summary, &breaking_changes);
        let confidence = self.calculate_analysis_confidence(&change_summary, total_commits);

        Ok(ReleaseAnalysis {
            version_from: from_ref.unwrap_or("HEAD").to_string(),
            version_to: to_ref.map(|s| s.to_string()),
            total_commits,
            change_summary,
            impact_distribution,
            breaking_changes,
            recommended_version_bump: recommended_bump,
            confidence,
        })
    }

    /// Определяет тип изменения по сообщению коммита
    fn detect_change_type(&self, message: &str) -> ChangeType {
        for (change_type, patterns) in &self.change_patterns {
            for pattern in patterns {
                if regex::Regex::new(pattern).unwrap().is_match(message) {
                    return change_type.clone();
                }
            }
        }
        ChangeType::Other
    }

    /// Рассчитывает уверенность в определении типа изменения
    fn calculate_confidence(&self, message: &str, change_type: &ChangeType) -> f32 {
        if let Some(patterns) = self.change_patterns.get(change_type) {
            let matches = patterns.iter()
                .filter(|pattern| regex::Regex::new(pattern).unwrap().is_match(message))
                .count();

            match matches {
                0 => 0.3, // Низкая уверенность
                1 => 0.7, // Средняя уверенность
                _ => 0.9, // Высокая уверенность
            }
        } else {
            0.5
        }
    }

    /// Извлекает затронутые области из сообщения коммита
    fn extract_affected_areas(&self, message: &str) -> Vec<String> {
        let mut areas = Vec::new();

        // Ищем упоминания компонентов в скобках
        if let Some(captures) = regex::Regex::new(r"\(([^)]+)\)").unwrap().captures(message) {
            if let Some(area) = captures.get(1) {
                areas.push(area.as_str().to_string());
            }
        }

        // Ищем упоминания файлов/модулей
        let file_patterns = [
            r"src/([a-zA-Z0-9_/]+)",
            r"([a-zA-Z0-9_]+)\.(java|kt|rs|py|js|ts)",
            r"module\s+([a-zA-Z0-9_]+)",
        ];

        for pattern in &file_patterns {
            if let Some(captures) = regex::Regex::new(pattern).unwrap().captures(message) {
                if let Some(area) = captures.get(1) {
                    areas.push(area.as_str().to_string());
                }
            }
        }

        areas
    }

    /// Проверяет, является ли изменение критическим
    fn is_breaking_change(&self, message: &str) -> bool {
        let breaking_patterns = [
            r"(?i)break",
            r"(?i)breaking",
            r"(?i)!:",
            r"(?i)feat!",
            r"(?i)deprecate",
            r"(?i)remove",
            r"(?i)delete",
            r"(?i)несовместимый",
            r"(?i)критический",
        ];

        for pattern in &breaking_patterns {
            if regex::Regex::new(pattern).unwrap().is_match(message) {
                return true;
            }
        }

        false
    }

    /// Определяет уровень влияния изменений
    fn determine_impact_level(&self, change_type: &ChangeType, commit: &GitCommit) -> ImpactLevel {
        match change_type {
            ChangeType::Breaking => ImpactLevel::Critical,
            ChangeType::Feature => {
                if commit.insertions > 100 {
                    ImpactLevel::High
                } else {
                    ImpactLevel::Medium
                }
            }
            ChangeType::Fix => ImpactLevel::Medium,
            ChangeType::Refactoring | ChangeType::Improvement => ImpactLevel::Medium,
            ChangeType::Documentation | ChangeType::Testing => ImpactLevel::Low,
            ChangeType::Chore => ImpactLevel::Low,
            ChangeType::Other => {
                if commit.insertions > 50 || commit.deletions > 50 {
                    ImpactLevel::Medium
                } else {
                    ImpactLevel::Low
                }
            }
        }
    }

    /// Генерирует описание изменения
    fn generate_description(&self, message: &str, change_type: &ChangeType) -> String {
        // Убираем технические префиксы и оставляем только описание
        let cleaned = regex::Regex::new(r"^(feat|fix|docs|style|refactor|test|chore|build|ci|perf)(\([^)]*\))?:\s*")
            .unwrap()
            .replace(message, "");

        let description = cleaned.trim();

        if description.is_empty() {
            format!("{}: {}", change_type.emoji(), change_type.name())
        } else {
            format!("{}: {}", change_type.emoji(), description)
        }
    }

    /// Рекомендует изменение версии
    fn recommend_version_bump(&self, change_summary: &HashMap<ChangeType, usize>, breaking_changes: &[String]) -> VersionBump {
        // Если есть критические изменения - major version
        if !breaking_changes.is_empty() || change_summary.contains_key(&ChangeType::Breaking) {
            return VersionBump::Major;
        }

        // Если есть новые функции - minor version
        if change_summary.get(&ChangeType::Feature).unwrap_or(&0) > &0 {
            return VersionBump::Minor;
        }

        // В остальных случаях - patch version
        VersionBump::Patch
    }

    /// Рассчитывает уверенность в анализе
    fn calculate_analysis_confidence(&self, change_summary: &HashMap<ChangeType, usize>, total_commits: usize) -> f32 {
        if total_commits == 0 {
            return 0.0;
        }

        let classified_commits: usize = change_summary.values().sum();
        let ratio = classified_commits as f32 / total_commits as f32;

        // Чем выше процент классифицированных коммитов, тем выше уверенность
        match ratio {
            r if r >= 0.9 => 0.9,
            r if r >= 0.7 => 0.7,
            r if r >= 0.5 => 0.5,
            _ => 0.3,
        }
    }

    /// Получает сводку последних изменений
    pub async fn get_recent_summary(&self, limit: u32) -> Result<ReleaseAnalysis> {
        info!("📊 Получение сводки последних {} коммитов", limit);

        let commits = self.git_history.get_recent_commits(limit).await?;

        if commits.is_empty() {
            return Ok(ReleaseAnalysis {
                version_from: "HEAD".to_string(),
                version_to: None,
                total_commits: 0,
                change_summary: HashMap::new(),
                impact_distribution: HashMap::new(),
                breaking_changes: Vec::new(),
                recommended_version_bump: VersionBump::Patch,
                confidence: 0.0,
            });
        }

        // Анализируем от самого старого к самому новому
        let oldest_commit = commits.last().unwrap();
        self.analyze_changes(Some(&oldest_commit.hash), Some("HEAD")).await
    }

    /// Форматирует анализ для вывода в консоль
    pub fn format_analysis(&self, analysis: &ReleaseAnalysis) -> String {
        let mut output = String::new();

        output.push_str(&format!("📊 Анализ изменений с {} по {}\n",
            analysis.version_from,
            analysis.version_to.as_deref().unwrap_or("HEAD")));
        output.push_str(&format!("📈 Всего коммитов: {}\n", analysis.total_commits));
        output.push_str(&format!("🎯 Уверенность анализа: {:.1}%\n\n", analysis.confidence * 100.0));

        output.push_str("🏷️ Типы изменений:\n");
        for (change_type, count) in &analysis.change_summary {
            output.push_str(&format!("  {} {}: {}\n",
                change_type.emoji(),
                change_type.name(),
                count));
        }

        output.push_str("\n📊 Уровень влияния:\n");
        for (impact_level, count) in &analysis.impact_distribution {
            let emoji = match impact_level {
                ImpactLevel::Low => "🟢",
                ImpactLevel::Medium => "🟡",
                ImpactLevel::High => "🟠",
                ImpactLevel::Critical => "🔴",
            };
            let name = match impact_level {
                ImpactLevel::Low => "Низкое",
                ImpactLevel::Medium => "Среднее",
                ImpactLevel::High => "Высокое",
                ImpactLevel::Critical => "Критическое",
            };
            output.push_str(&format!("  {} {}: {}\n", emoji, name, count));
        }

        if !analysis.breaking_changes.is_empty() {
            output.push_str("\n⚠️ Критические изменения:\n");
            for change in &analysis.breaking_changes {
                output.push_str(&format!("  • {}\n", change));
            }
        }

        let bump_name = match analysis.recommended_version_bump {
            VersionBump::Patch => "Patch (0.0.x)",
            VersionBump::Minor => "Minor (0.x.0)",
            VersionBump::Major => "Major (x.0.0)",
            VersionBump::Custom(ref version) => version,
        };
        output.push_str(&format!("\n🚀 Рекомендуемое изменение версии: {}\n", bump_name));

        output
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;

    fn create_test_commit(message: &str, insertions: u32, deletions: u32) -> GitCommit {
        GitCommit {
            hash: "abc123".to_string(),
            short_hash: "abc123".to_string(),
            message: message.to_string(),
            author: "Test Author".to_string(),
            email: "test@example.com".to_string(),
            date: Utc::now(),
            files_changed: 1,
            insertions,
            deletions,
        }
    }

    #[tokio::test]
    async fn test_change_type_detection() {
        let analyzer = ChangeAnalyzer::new("/tmp");

        // Тест определения новых функций
        let feature_commit = create_test_commit("feat: add new authentication system", 50, 0);
        let analysis = analyzer.analyze_commit(&feature_commit).await.unwrap();
        assert!(matches!(analysis.change_type, ChangeType::Feature));

        // Тест определения исправлений
        let fix_commit = create_test_commit("fix: resolve login issue", 10, 5);
        let analysis = analyzer.analyze_commit(&fix_commit).await.unwrap();
        assert!(matches!(analysis.change_type, ChangeType::Fix));

        // Тест определения критических изменений
        let breaking_commit = create_test_commit("feat!: remove deprecated API", 100, 200);
        let analysis = analyzer.analyze_commit(&breaking_commit).await.unwrap();
        assert!(matches!(analysis.change_type, ChangeType::Breaking));
        assert!(analysis.breaking_changes);
    }

    #[tokio::test]
    async fn test_impact_level_determination() {
        let analyzer = ChangeAnalyzer::new("/tmp");

        // Критические изменения
        let breaking_commit = create_test_commit("feat!: breaking change", 10, 10);
        let analysis = analyzer.analyze_commit(&breaking_commit).await.unwrap();
        assert_eq!(analysis.impact_level, ImpactLevel::Critical);

        // Большая новая функция
        let big_feature = create_test_commit("feat: add major feature", 150, 0);
        let analysis = analyzer.analyze_commit(&big_feature).await.unwrap();
        assert_eq!(analysis.impact_level, ImpactLevel::High);

        // Документация
        let docs_commit = create_test_commit("docs: update README", 20, 5);
        let analysis = analyzer.analyze_commit(&docs_commit).await.unwrap();
        assert_eq!(analysis.impact_level, ImpactLevel::Low);
    }

    #[tokio::test]
    async fn test_version_bump_recommendation() {
        let analyzer = ChangeAnalyzer::new("/tmp");

        let mut change_summary = HashMap::new();

        // Только исправления -> patch
        change_summary.insert(ChangeType::Fix, 3);
        let bump = analyzer.recommend_version_bump(&change_summary, &[]);
        assert!(matches!(bump, VersionBump::Patch));

        // Новые функции -> minor
        change_summary.insert(ChangeType::Feature, 2);
        let bump = analyzer.recommend_version_bump(&change_summary, &[]);
        assert!(matches!(bump, VersionBump::Minor));

        // Критические изменения -> major
        change_summary.insert(ChangeType::Breaking, 1);
        let bump = analyzer.recommend_version_bump(&change_summary, &[]);
        assert!(matches!(bump, VersionBump::Major));
    }
}