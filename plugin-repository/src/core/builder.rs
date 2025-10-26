use anyhow::{Context, Result};
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::process::Command as AsyncCommand;
use tokio::time::timeout;
use tracing::{info, warn, debug, error};
use indicatif::{ProgressBar, ProgressStyle};
use crate::models::plugin::{PluginArtifact, BuildResult};
use crate::config::parser::Config;
use sha2::{Sha256, Digest};

/// Система сборки плагинов
pub struct PluginBuilder {
    config: Config,
    project_root: PathBuf,
}

impl PluginBuilder {
    /// Создает новый экземпляр билдера
    pub fn new(config: Config, project_root: PathBuf) -> Self {
        Self {
            config,
            project_root,
        }
    }

    /// Собирает плагин с указанной версией
    pub async fn build(&self, version: Option<String>, profile: &str) -> Result<BuildResult> {
        info!("🔨 Начало сборки плагина");

        let start_time = std::time::Instant::now();
        let mut logs = Vec::new();
        let mut errors = Vec::new();

        // 1. Определяем тип проекта
        let project_type = self.detect_project_type().await?;
        logs.push(format!("📁 Тип проекта определен: {:?}", project_type));

        // 2. Валидация структуры проекта
        if let Err(e) = self.validate_project_structure(&project_type).await {
            let error_msg = format!("❌ Валидация структуры проекта не пройдена: {}", e);
            error!("{}", error_msg);
            errors.push(error_msg);
            return Ok(BuildResult {
                success: false,
                artifact: None,
                metadata: None,
                build_time: chrono::Utc::now(),
                logs,
                errors,
            });
        }

        // 3. Сборка
        let mut artifact = match self.build_plugin(&project_type, profile, &mut logs, &mut errors).await {
            Ok(artifact) => {
                logs.push("✅ Сборка завершена успешно".to_string());
                Some(artifact)
            }
            Err(e) => {
                let error_msg = format!("❌ Сборка не удалась: {}", e);
                error!("{}", error_msg);
                errors.push(error_msg);
                None
            }
        };

        // 3.1. Применяем версию из параметра: переименуем артефакт и обновим метаданные
        if let (Some(ref mut art), Some(ref ver)) = (&mut artifact, &version) {
            if let Some(path) = art.file_path.parent() {
                let old_name = art.file_name.clone();
                let new_name = Self::apply_version_to_filename(&old_name, ver);
                let new_path = path.join(&new_name);
                // Переименуем файл на диске
                if let Err(e) = std::fs::rename(&art.file_path, &new_path) {
                    warn!("Не удалось переименовать артефакт под версию {}: {}", ver, e);
                } else {
                    info!("Артефакт переименован: {} -> {}", old_name, new_name);
                    art.file_name = new_name;
                    art.file_path = new_path;
                    art.version = ver.clone();
                    // Размер/чексумма не меняются при rename
                }
            }
        }

        // 4. Валидация артефакта
        if let Some(ref artifact) = artifact {
            if let Err(e) = self.validate_artifact(artifact).await {
                let error_msg = format!("❌ Валидация артефакта не пройдена: {}", e);
                error!("{}", error_msg);
                errors.push(error_msg);
                return Ok(BuildResult {
                    success: false,
                    artifact: Some(artifact.clone()),
                    metadata: None,
                    build_time: chrono::Utc::now(),
                    logs,
                    errors,
                });
            }
        }

        let build_time = chrono::Utc::now();
        let duration = start_time.elapsed();

        logs.push(format!("⏱️ Время сборки: {:?}", duration));

        let success = artifact.is_some() && errors.is_empty();

        Ok(BuildResult {
            success,
            artifact,
            metadata: None, // TODO: Реализовать извлечение метаданных
            build_time,
            logs,
            errors,
        })
    }

    /// Определяет тип проекта (Gradle/Maven)
    async fn detect_project_type(&self) -> Result<ProjectType> {
        debug!("Определение типа проекта в директории: {:?}", self.project_root);

        // Проверяем Gradle
        if self.project_root.join("build.gradle").exists() ||
           self.project_root.join("build.gradle.kts").exists() {
            info!("📦 Обнаружен Gradle проект");
            return Ok(ProjectType::Gradle);
        }

        // Проверяем Maven
        if self.project_root.join("pom.xml").exists() {
            info!("📦 Обнаружен Maven проект");
            return Ok(ProjectType::Maven);
        }

        Err(anyhow::anyhow!(
            "Не удалось определить тип проекта. Отсутствуют build.gradle, build.gradle.kts или pom.xml"
        ))
    }

    /// Валидирует структуру проекта
    async fn validate_project_structure(&self, project_type: &ProjectType) -> Result<()> {
        debug!("Валидация структуры проекта: {:?}", project_type);

        match project_type {
            ProjectType::Gradle => {
                // Проверяем Gradle wrapper
                if !self.project_root.join("gradlew").exists() {
                    warn!("⚠️ Gradle wrapper не найден, будет использоваться системный gradle");
                }

                // Проверяем src/main/kotlin или src/main/java
                let kotlin_src = self.project_root.join("src/main/kotlin");
                let java_src = self.project_root.join("src/main/java");

                if !kotlin_src.exists() && !java_src.exists() {
                    return Err(anyhow::anyhow!(
                        "Не найдена директория с исходниками (src/main/kotlin или src/main/java)"
                    ));
                }
            }
            ProjectType::Maven => {
                // Проверяем стандартную Maven структуру
                let src_main = self.project_root.join("src/main/java");
                if !src_main.exists() {
                    return Err(anyhow::anyhow!(
                        "Не найдена директория с исходниками (src/main/java)"
                    ));
                }
            }
        }

        Ok(())
    }

    /// Выполняет сборку плагина
    async fn build_plugin(
        &self,
        project_type: &ProjectType,
        profile: &str,
        logs: &mut Vec<String>,
        errors: &mut Vec<String>,
    ) -> Result<PluginArtifact> {
        let progress = ProgressBar::new_spinner();
        progress.set_style(
            ProgressStyle::default_spinner()
                .template("{spinner:.green} [{elapsed_precise}] {msg}")
                .unwrap()
        );
        progress.set_message("🔨 Сборка плагина...");
        progress.enable_steady_tick(Duration::from_millis(100));

        let result = match project_type {
            ProjectType::Gradle => self.build_gradle(profile, logs, errors).await,
            ProjectType::Maven => self.build_maven(profile, logs, errors).await,
        };

        progress.finish_with_message("✅ Сборка завершена");

        result
    }

    /// Сборка Gradle проекта
    async fn build_gradle(
        &self,
        profile: &str,
        logs: &mut Vec<String>,
        errors: &mut Vec<String>,
    ) -> Result<PluginArtifact> {
        info!("🔨 Запуск Gradle сборки с профилем: {}", profile);
        logs.push(format!("Запуск Gradle сборки: gradle {}", self.config.build.gradle_task));

        let gradle_cmd = if self.project_root.join("gradlew").exists() {
            "./gradlew"
        } else {
            "gradle"
        };

        let mut args: Vec<&str> = vec![&self.config.build.gradle_task];
        if !self.config.build.build_args.is_empty() {
            args.extend(self.config.build.build_args.iter().map(|s| s.as_str()));
        }

        let mut cmd = AsyncCommand::new(gradle_cmd);
        cmd.current_dir(&self.project_root)
           .args(&args);

        debug!("Выполняем команду: {:?}", cmd);

        let output = timeout(Duration::from_secs(300), cmd.output()).await
            .context("Таймаут сборки (5 минут)")?
            .context("Ошибка выполнения команды сборки")?;

        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);

        if !stdout.is_empty() {
            for line in stdout.lines().take(20) {
                logs.push(format!("📝 {}", line));
            }
        }

        if !stderr.is_empty() {
            for line in stderr.lines().take(10) {
                if line.to_lowercase().contains("error") || line.to_lowercase().contains("failed") {
                    errors.push(format!("❌ {}", line));
                } else {
                    logs.push(format!("⚠️ {}", line));
                }
            }
        }

        if !output.status.success() {
            return Err(anyhow::anyhow!(
                "Gradle сборка завершилась с кодом {}: {}",
                output.status,
                stderr.lines().next().unwrap_or("нет вывода ошибок")
            ));
        }

        // Ищем созданный артефакт
        self.find_artifact().await
    }

    /// Сборка Maven проекта
    async fn build_maven(
        &self,
        profile: &str,
        logs: &mut Vec<String>,
        errors: &mut Vec<String>,
    ) -> Result<PluginArtifact> {
        info!("🔨 Запуск Maven сборки с профилем: {}", profile);
        logs.push("Запуск Maven сборки: mvn package".to_string());

        let mut cmd = AsyncCommand::new("mvn");
        cmd.current_dir(&self.project_root)
           .args(&["package", "-DskipTests"]);

        if profile != "release" {
            cmd.arg("-P").arg(profile);
        }

        debug!("Выполняем команду: {:?}", cmd);

        let output = timeout(Duration::from_secs(300), cmd.output()).await
            .context("Таймаут сборки (5 минут)")?
            .context("Ошибка выполнения команды сборки")?;

        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);

        if !stdout.is_empty() {
            for line in stdout.lines().take(20) {
                logs.push(format!("📝 {}", line));
            }
        }

        if !stderr.is_empty() {
            for line in stderr.lines().take(10) {
                if line.to_lowercase().contains("error") || line.to_lowercase().contains("failed") {
                    errors.push(format!("❌ {}", line));
                } else {
                    logs.push(format!("⚠️ {}", line));
                }
            }
        }

        if !output.status.success() {
            return Err(anyhow::anyhow!(
                "Maven сборка завершилась с кодом {}: {}",
                output.status,
                stderr.lines().next().unwrap_or("нет вывода ошибок")
            ));
        }

        // Ищем созданный артефакт
        self.find_artifact().await
    }

    /// Ищет созданный артефакт сборки
    async fn find_artifact(&self) -> Result<PluginArtifact> {
        let output_dir = self.project_root.join(&self.config.build.output_dir);
        debug!("Поиск артефактов в директории: {:?}", output_dir);

        if !output_dir.exists() {
            return Err(anyhow::anyhow!(
                "Директория сборки не найдена: {:?}",
                output_dir
            ));
        }

        let mut zip_files = Vec::new();
        for entry in std::fs::read_dir(&output_dir)? {
            let entry = entry?;
            let path = entry.path();

            if path.is_file() {
                if let Some(extension) = path.extension() {
                    if extension == "zip" {
                        zip_files.push(path);
                    }
                }
            }
        }

        if zip_files.is_empty() {
            return Err(anyhow::anyhow!(
                "ZIP артефакты не найдены в директории {:?}",
                output_dir
            ));
        }

        // Берем самый свежий файл
        zip_files.sort_by_key(|path| {
            std::fs::metadata(path).and_then(|m| m.modified()).unwrap_or(std::time::UNIX_EPOCH)
        });

        let artifact_path = zip_files.last().unwrap();
        let file_name = artifact_path.file_name()
            .ok_or_else(|| anyhow::anyhow!("Неверное имя файла"))?
            .to_string_lossy()
            .to_string();

        let metadata = std::fs::metadata(artifact_path)?;
        let file_size = metadata.len();

        // Вычисляем SHA256
        let checksum = self.calculate_checksum(artifact_path)?;

        info!("✅ Найден артефакт: {} ({} bytes)", file_name, file_size);

        // Извлекаем версию из имени файла
        let version = self.extract_version_from_filename(&file_name)
            .unwrap_or_else(|| "unknown".to_string());

        Ok(PluginArtifact {
            file_path: artifact_path.clone(),
            file_name,
            file_size,
            checksum_sha256: checksum,
            version,
            build_time: chrono::Utc::now(),
        })
    }

    /// Вычисляет SHA256 checksum файла
    fn calculate_checksum(&self, file_path: &Path) -> Result<String> {
        let mut file = std::fs::File::open(file_path)?;
        let mut hasher = Sha256::new();
        std::io::copy(&mut file, &mut hasher)?;

        let result = hasher.finalize();
        Ok(format!("{:x}", result))
    }

    /// Извлекает версию из имени файла
    fn extract_version_from_filename(&self, filename: &str) -> Option<String> {
        // Ищем паттерн plugin-name-version.zip
        let re = regex::Regex::new(r"-(\d+\.\d+\.\d+(?:-[a-zA-Z0-9]+)*)\.zip$").ok()?;

        if let Some(captures) = re.captures(filename) {
            captures.get(1).map(|m| m.as_str().to_string())
        } else {
            None
        }
    }

    /// Формирует имя файла с заданной версией. Если версия в имени найдена — заменяет, иначе вставляет перед .zip
    fn apply_version_to_filename(filename: &str, version: &str) -> String {
        let re = regex::Regex::new(r"-(\d+\.\d+\.\d+(?:-[a-zA-Z0-9]+)*)\.zip$").ok();
        if let Some(re) = re {
            if re.is_match(filename) {
                return re.replace(filename, format!("-{}.zip", version)).to_string();
            }
        }
        // Если шаблон не совпал, пытаемся вставить перед .zip
        if let Some(stripped) = filename.strip_suffix(".zip") {
            return format!("{}-{}.zip", stripped, version);
        }
        // fallback: просто добавить суффикс
        format!("{}-{}.zip", filename, version)
    }

    /// Валидирует артефакт
    async fn validate_artifact(&self, artifact: &PluginArtifact) -> Result<()> {
        debug!("Валидация артефакта: {}", artifact.file_name);

        // Проверяем существование файла
        if !artifact.file_path.exists() {
            return Err(anyhow::anyhow!(
                "Артефакт не найден: {:?}",
                artifact.file_path
            ));
        }

        // Проверяем размер файла
        if artifact.file_size == 0 {
            return Err(anyhow::anyhow!(
                "Артефакт имеет нулевой размер"
            ));
        }

        // Проверяем структуру ZIP архива
        self.validate_zip_structure(&artifact.file_path).await?;

        // Проверяем наличие plugin.xml
        self.validate_plugin_xml(&artifact.file_path).await?;

        info!("✅ Артефакт успешно валидирован");
        Ok(())
    }

    /// Валидирует структуру ZIP архива
    async fn validate_zip_structure(&self, zip_path: &Path) -> Result<()> {
        let file = std::fs::File::open(zip_path)?;
        let archive = zip::ZipArchive::new(file)?;

        if archive.len() == 0 {
            return Err(anyhow::anyhow!("ZIP архив пуст"));
        }

        debug!("✅ ZIP архив содержит {} файлов", archive.len());
        Ok(())
    }

    /// Валидирует наличие plugin.xml в архиве (включая проверку внутри JAR файлов)
    async fn validate_plugin_xml(&self, zip_path: &Path) -> Result<()> {
        let file = std::fs::File::open(zip_path)?;
        let mut archive = zip::ZipArchive::new(file)?;

        // 1) Проверяем верхний уровень архива
        for i in 0..archive.len() {
            let file = archive.by_index(i)?;
            if file.name().ends_with("plugin.xml") || file.name().ends_with("META-INF/plugin.xml") {
                debug!("✅ Найден plugin.xml в корне архива");
                return Ok(());
            }
        }

        // 2) Проверяем JAR-файлы внутри архива (обычно в lib/)
        for i in 0..archive.len() {
            let mut entry = archive.by_index(i)?;
            let name = entry.name().to_string();
            if name.ends_with(".jar") {
                // Читаем jar в память и открываем как zip
                let mut buf = Vec::with_capacity(entry.size() as usize);
                std::io::copy(&mut entry, &mut buf)?;
                let cursor = std::io::Cursor::new(buf);
                if let Ok(mut jar) = zip::ZipArchive::new(cursor) {
                    for j in 0..jar.len() {
                        let inner = jar.by_index(j)?;
                        if inner.name().ends_with("META-INF/plugin.xml") {
                            debug!("✅ Найден plugin.xml внутри JAR: {}", name);
                            return Ok(());
                        }
                    }
                }
            }
        }

        Err(anyhow::anyhow!("plugin.xml не найден в архиве плагина (ни в корне, ни внутри JAR файлов)"))
    }
}

/// Тип проекта для сборки
#[derive(Debug, Clone)]
pub enum ProjectType {
    Gradle,
    Maven,
}

// Добавляем зависимость zip в Cargo.toml
use zip;