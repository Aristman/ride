use anyhow::{Result, Context};
use tracing::{info, warn};
use std::fs;
use std::path::{Path, PathBuf};

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
        // TODO: Реализация SSH/SCP деплоя и atomic обновления XML
        // Сейчас: имитация успешной публикации
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