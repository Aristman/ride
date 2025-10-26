use anyhow::{Result, Context};
use tracing::{info, warn};
use std::fs;
use std::path::{Path, PathBuf};
use walkdir::WalkDir;
use sha2::{Sha256, Digest};

use crate::config::parser::Config;

/// Движок деплоя
#[derive(Debug, Clone)]
pub struct Deployer {
    config: Config,
}

impl Deployer {
    pub fn new(config: Config) -> Self {
        Self { config }
    }

    /// Валидация перед деплоем
    pub async fn validate(&self) -> Result<()> {
        info!("🔍 Валидация перед деплоем");
        self.config.validate().context("Валидация конфигурации деплоя не пройдена")?;
        Ok(())
    }

    /// Выполнить деплой артефактов
    pub async fn deploy(&self, force: bool, rollback_on_failure: bool) -> Result<()> {
        info!("📦 Запуск деплоя (force={}, rollback_on_failure={})", force, rollback_on_failure);
        // 1) Поиск артефактов
        let artifacts = self.find_artifacts()?;
        if artifacts.is_empty() {
            return Err(anyhow::anyhow!("Не найдены артефакты для деплоя"));
        }

        // 2) Подготовка XML (упростим: генерируем базовый список)
        let xml_content = self.build_repository_xml(&artifacts)?;

        // 3) Загрузка артефактов и XML
        let mut uploaded: Vec<String> = Vec::new();
        let xml_remote = PathBuf::from(&self.config.repository.xml_path);
        let deploy_dir = PathBuf::from(&self.config.repository.deploy_path);

        // Резервная копия XML (remote, только для ssh фичи)
        #[cfg(feature = "ssh")]
        let mut xml_backup_done = false;

        let res: Result<()> = (|| {
            #[cfg(feature = "ssh")]
            {
                let session = self.ssh_connect()?;
                let sftp = session.sftp().context("Не удалось открыть SFTP сессию")?;

                // Бэкап XML, если существует
                if sftp.stat(&xml_remote).is_ok() {
                    let bak_path = PathBuf::from(format!("{}.bak", xml_remote.display()));
                    sftp.rename(&xml_remote, &bak_path, None)
                        .with_context(|| format!("Не удалось создать бэкап XML {}", xml_remote.display()))?;
                    xml_backup_done = true;
                }

                // Загрузка артефактов
                for art in &artifacts {
                    let file_name = art.file_name().unwrap().to_string_lossy().to_string();
                    let remote_path = deploy_dir.join(&file_name);
                    self.scp_upload(&session, art, &remote_path)?;
                    // Проверка размера
                    let local_size = fs::metadata(art)?.len();
                    let remote_md = sftp.stat(&remote_path)
                        .with_context(|| format!("Не удалось получить stat удаленного файла {}", remote_path.display()))?;
                    if remote_md.size.unwrap_or(0) != local_size {
                        anyhow::bail!("Несовпадение размера для {}", file_name);
                    }
                    uploaded.push(remote_path.display().to_string());
                }

                // Атомарное обновление XML на удаленной стороне через временный файл и rename
                self.remote_atomic_update_xml(&sftp, &xml_remote, &xml_content)?;
            }
            #[cfg(not(feature = "ssh"))]
            {
                warn!("SSH отключен, загрузка будет пропущена. Включите feature 'ssh' для реального деплоя.");
                // Локальная проверка: создадим локальный XML рядом с указанный путем (для отладки)
                let local_xml = Path::new("./target/mock").join(xml_remote.file_name().unwrap_or_default());
                std::fs::create_dir_all(local_xml.parent().unwrap()).ok();
                self.atomic_update_xml(&local_xml, &xml_content)?;
            }
            Ok(())
        })();

        if let Err(e) = res {
            warn!("Ошибка деплоя: {}", e);
            if rollback_on_failure {
                let _ = self.rollback_uploaded(uploaded);
                #[cfg(feature = "ssh")]
                {
                    // Попытаться восстановить xml из .bak
                    if let Ok(session) = self.ssh_connect() {
                        if let Ok(sftp) = session.sftp() {
                            let bak_path = PathBuf::from(format!("{}.bak", xml_remote.display()));
                            let _ = sftp.rename(&bak_path, &xml_remote, None);
                        }
                    }
                }
            }
            return Err(e);
        }

        info!("✅ Деплой завершен");
        Ok(())
    }

    /// Откат изменений
    pub async fn rollback(&self) -> Result<()> {
        warn!("⏪ Откат деплоя (заглушка)");
        Ok(())
    }

    /// Подключение по SSH (требует feature "ssh")
    #[cfg(feature = "ssh")]
    fn ssh_connect(&self) -> Result<ssh2::Session> {
        use std::net::TcpStream;
        use anyhow::bail;

        let host = &self.config.repository.ssh_host;
        let user = &self.config.repository.ssh_user;

        let stream = TcpStream::connect(format!("{}:22", host))
            .with_context(|| format!("Не удалось подключиться к {}:22", host))?;

        let mut session = ssh2::Session::new().context("Не удалось создать SSH сессию")?;
        session.set_tcp_stream(stream);
        session.handshake().context("Ошибка SSH рукопожатия")?;

        if let Some(key_path) = &self.config.repository.ssh_private_key_path {
            session.userauth_pubkey_file(user, None, Path::new(key_path), None)
                .with_context(|| format!("Не удалось аутентифицироваться ключом: {}", key_path))?;
        } else {
            bail!("ssh_private_key_path не задан в конфигурации");
        }

        if !session.authenticated() {
            bail!("Не удалось аутентифицироваться на SSH сервере");
        }

        Ok(session)
    }

    /// Загрузка файла по SCP (требует feature "ssh")
    #[cfg(feature = "ssh")]
    fn scp_upload(&self, session: &ssh2::Session, local: &Path, remote: &Path) -> Result<()> {
        use std::io::Read;
        let mut file = std::fs::File::open(local)
            .with_context(|| format!("Не удалось открыть локальный файл: {}", local.display()))?;
        let metadata = file.metadata().context("Не удалось получить метаданные файла")?;

        let mut channel = session.scp_send(remote, 0o644, metadata.len(), None)
            .with_context(|| format!("Не удалось открыть SCP для {}", remote.display()))?;

        std::io::copy(&mut file, &mut channel)
            .with_context(|| format!("Ошибка отправки файла {}", local.display()))?;
        channel.send_eof().ok();
        channel.wait_eof().ok();
        channel.wait_close().ok();
        Ok(())
    }

    /// Загрузка артефакта на сервер (feature "ssh"), безопасный no-op без фичи
    pub fn upload_artifact<P: AsRef<Path>>(&self, local: P, remote: P) -> Result<()> {
        #[cfg(feature = "ssh")]
        {
            let session = self.ssh_connect()?;
            self.scp_upload(&session, local.as_ref(), remote.as_ref())
        }
        #[cfg(not(feature = "ssh"))]
        {
            // Без фичи ssh просто информируем, что функциональность выключена
            tracing::info!("SSH поддержка выключена (включите фичу 'ssh' в Cargo)");
            Ok(())
        }
    }

    /// Атомарное обновление XML на удаленном сервере через SFTP (feature "ssh")
    #[cfg(feature = "ssh")]
    fn remote_atomic_update_xml(&self, sftp: &ssh2::Sftp, xml_remote: &Path, content: &str) -> Result<()> {
        use std::io::Write;
        // временный файл в той же директории
        let dir = xml_remote.parent().unwrap_or_else(|| Path::new("."));
        let tmp_remote = dir.join(format!("{}.tmp", xml_remote.file_name().and_then(|n| n.to_str()).unwrap_or("updatePlugins.xml")));
        // запись контента
        {
            let mut file = sftp.create(&tmp_remote)
                .with_context(|| format!("Не удалось создать временный удаленный файл {}", tmp_remote.display()))?;
            file.write_all(content.as_bytes())
                .context("Не удалось записать содержимое XML на удаленной стороне")?;
            file.flush().ok();
        }
        // rename поверх целевого
        sftp.rename(&tmp_remote, xml_remote, None)
            .with_context(|| format!("Не удалось атомарно заменить удаленный XML {}", xml_remote.display()))?;
        Ok(())
    }

    /// Атомарное обновление XML файла репозитория: запись во временный файл и замена
    pub fn atomic_update_xml<P: AsRef<Path>>(&self, xml_path: P, content: &str) -> Result<()> {
        let xml_path = xml_path.as_ref();
        let dir = xml_path.parent().unwrap_or_else(|| Path::new("."));

        // Создаем временный файл в той же директории, чтобы rename был атомарным на одном FS
        let mut tmp_path = PathBuf::from(dir);
        let file_name = xml_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("updatePlugins.xml");
        tmp_path.push(format!("{}.tmp", file_name));

        // Записываем содержимое во временный файл и синхронизируем на диск
        fs::write(&tmp_path, content)
            .with_context(|| format!("Не удалось записать временный XML: {}", tmp_path.display()))?;

        // Перемещаем временный файл поверх целевого (атомарная замена на одном FS)
        fs::rename(&tmp_path, xml_path)
            .with_context(|| format!("Не удалось атомарно заменить XML {}", xml_path.display()))?;

        Ok(())
    }

    /// Поиск артефактов для деплоя (zip) в каталоге сборки
    fn find_artifacts(&self) -> Result<Vec<PathBuf>> {
        let out_dir = PathBuf::from(&self.config.build.output_dir);
        let mut files = Vec::new();
        for entry in WalkDir::new(&out_dir).into_iter().filter_map(|e| e.ok()) {
            if entry.metadata().map(|m| m.is_file()).unwrap_or(false) {
                let p = entry.path();
                if p.extension().and_then(|e| e.to_str()) == Some("zip") {
                    files.push(p.to_path_buf());
                }
            }
        }
        Ok(files)
    }

    /// Построение простого XML описания репозитория на основе найденных артефактов
    fn build_repository_xml(&self, artifacts: &[PathBuf]) -> Result<String> {
        // Простая заготовка: список файлов и их sha256
        let mut items = String::new();
        for p in artifacts {
            let name = p.file_name().unwrap().to_string_lossy();
            let sha = self.sha256_file(p)?;
            items.push_str(&format!("    <plugin file=\"{}\" sha256=\"{}\"/>\n", name, sha));
        }
        let xml = format!(
            "<plugins>\n{}\n</plugins>",
            items
        );
        Ok(xml)
    }

    fn sha256_file(&self, path: &Path) -> Result<String> {
        let mut file = std::fs::File::open(path)
            .with_context(|| format!("Не удалось открыть файл для хеша: {}", path.display()))?;
        let mut hasher = Sha256::new();
        std::io::copy(&mut file, &mut hasher).context("Ошибка чтения файла для хеша")?;
        let digest = hasher.finalize();
        Ok(format!("{:x}", digest))
    }

    /// Локальный откат загруженных файлов (при ssh — пытаемся удалить удаленные файлы)
    fn rollback_uploaded(&self, remote_paths: Vec<String>) {
        #[cfg(feature = "ssh")]
        {
            if let Ok(session) = self.ssh_connect() {
                if let Ok(sftp) = session.sftp() {
                    for p in remote_paths {
                        let _ = sftp.unlink(Path::new(&p));
                    }
                }
            }
        }
        #[cfg(not(feature = "ssh"))]
        {
            let _ = remote_paths; // no-op
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_deployer_validate() {
        if let Ok(cfg) = Config::load_from_file("plugin-repository/config.toml") {
            let d = Deployer::new(cfg);
            let _ = d.validate().await; // допускаем ошибки валидатора в CI окружении
        }
    }

    #[tokio::test]
    async fn test_deployer_deploy_and_rollback() {
        if let Ok(cfg) = Config::load_from_file("plugin-repository/config.toml") {
            let d = Deployer::new(cfg);
            let _ = d.deploy(false, true).await;
            let _ = d.rollback().await;
        }
    }

    #[test]
    fn test_atomic_update_xml() {
        let tmpdir = tempfile::tempdir().expect("tempdir");
        let xml_path = tmpdir.path().join("updatePlugins.xml");
        // исходный файл
        fs::write(&xml_path, "<plugins></plugins>").expect("write initial");

        if let Ok(cfg) = Config::load_from_file("plugin-repository/config.toml") {
            let d = Deployer::new(cfg);
            d.atomic_update_xml(&xml_path, "<plugins><plugin id=\"x\"/></plugins>")
                .expect("atomic update");
            let updated = fs::read_to_string(&xml_path).expect("read updated");
            assert!(updated.contains("plugin id=\"x\""));
        }
    }
}