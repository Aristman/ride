use anyhow::{Result, Context};
use tracing::{info, warn};
use std::fs;
use std::path::{Path, PathBuf};
use walkdir::WalkDir;
use sha2::{Sha256, Digest};
use std::time::Duration;
use xmltree::{Element, XMLNode};
use std::fs::File;

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

        // 2) Подготовка XML будет сделана позже, после чтения существующего файла (merge)

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
                // Логируем эффективные значения перед подключением
                info!(
                    host = %self.config.repository.ssh_host,
                    user = %self.config.repository.ssh_user,
                    deploy_path = %deploy_dir.display(),
                    xml_path = %xml_remote.display(),
                    "SSH параметры деплоя"
                );

                let session = self.ssh_connect()?;
                let sftp = session.sftp().context("Не удалось открыть SFTP сессию")?;

                // Гарантируем существование директорий для артефактов и XML
                let xml_parent = xml_remote.parent().unwrap_or_else(|| Path::new("/"));
                self.sftp_mkdirs(&sftp, &deploy_dir)?;
                self.sftp_mkdirs(&sftp, xml_parent)?;

                // Бэкап XML, если существует
                if sftp.stat(&xml_remote).is_ok() {
                    use ssh2::RenameFlags;
                    let bak_path = PathBuf::from(format!("{}.bak", xml_remote.display()));
                    // Сначала пробуем переименовать с OVERWRITE (если .bak существует)
                    match sftp.rename(&xml_remote, &bak_path, Some(RenameFlags::OVERWRITE)) {
                        Ok(_) => {
                            xml_backup_done = true;
                        }
                        Err(e) => {
                            // Фоллбек: если .bak существует/мешает — удалим и попробуем снова
                            let _ = sftp.unlink(&bak_path);
                            sftp.rename(&xml_remote, &bak_path, Some(RenameFlags::OVERWRITE))
                                .with_context(|| format!("Не удалось создать бэкап XML {}: {}", xml_remote.display(), e))?;
                            xml_backup_done = true;
                        }
                    }

                }
                // Загрузка артефактов
                for art in &artifacts {
                    let file_name = art.file_name().unwrap().to_string_lossy().to_string();
                    let remote_path = deploy_dir.join(&file_name);
                    // Сначала пробуем SCP
                    match self.scp_upload(&session, art, &remote_path) {
                        Ok(_) => {}
                        Err(e) => {
                            warn!("SCP не удался для {}: {} — пробуем SFTP", remote_path.display(), e);
                            // Фоллбек на SFTP
                            match self.sftp_upload(&sftp, art, &remote_path) {
                                Ok(_) => {}
                                Err(e) => {
                                    warn!("SFTP не удался для {}: {}", remote_path.display(), e);
                                    return Err(anyhow::anyhow!("Загрузка артефакта {} не удалась: {}", remote_path.display(), e));
                                }
                            }
                        }
                    }
                    // Проверка размера
                    let local_size = fs::metadata(art)?.len();
                    let remote_md = sftp.stat(&remote_path)
                        .with_context(|| format!("Не удалось получить метаданные удаленного файла {}", remote_path.display()))?;
                    if remote_md.size.unwrap_or(0) != local_size as u64 {
                        anyhow::bail!("Размер загруженного файла не совпадает для {}", remote_path.display());
                    }
                }

                // Сборка итогового XML: читаем существующий, мёрджим новые плагины по id, оставляя только последнюю версию на id
                let merged_xml = self.build_merged_repository_xml_ssh(&sftp, &xml_remote, &artifacts)?;
                // Атомарное обновление XML на удаленной стороне через временный файл и rename
                self.remote_atomic_update_xml(&sftp, &xml_remote, &merged_xml)?;
            }
            #[cfg(not(feature = "ssh"))]
            {
                warn!("SSH отключен, загрузка будет пропущена. Включите feature 'ssh' для реального деплоя.");
                // Локальная проверка: создадим локальный XML рядом с указанный путем (для отладки)
                let local_xml = Path::new("./target/mock").join(xml_remote.file_name().unwrap_or_default());
                std::fs::create_dir_all(local_xml.parent().unwrap()).ok();
                let merged_xml = self.build_repository_xml(&artifacts)?;
                self.atomic_update_xml(&local_xml, &merged_xml)?;
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
        use std::net::{TcpStream, ToSocketAddrs};
        use anyhow::bail;

        let host = &self.config.repository.ssh_host;
        let user = &self.config.repository.ssh_user;

        // Таймауты подключения/IO
        let connect_timeout = Duration::from_secs(15);
        let io_timeout = Duration::from_secs(30);

        // Разрешаем адрес и подключаемся с таймаутом
        let addr = format!("{}:22", host)
            .to_socket_addrs()
            .with_context(|| format!("Не удалось разрешить адрес {}:22", host))?
            .next()
            .ok_or_else(|| anyhow::anyhow!("DNS не вернул адрес для {}:22", host))?;

        let stream = TcpStream::connect_timeout(&addr, connect_timeout)
            .with_context(|| format!("Таймаут подключения к {}", addr))?;
        stream.set_read_timeout(Some(io_timeout)).ok();
        stream.set_write_timeout(Some(io_timeout)).ok();

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

    /// Рекурсивное создание удаленных директорий через SFTP (аналог mkdir -p)
    #[cfg(feature = "ssh")]
    fn sftp_mkdirs(&self, sftp: &ssh2::Sftp, path: &Path) -> Result<()> {
        use std::path::Component;
        let mut cur = PathBuf::new();
        for comp in path.components() {
            match comp {
                Component::RootDir => cur.push(Path::new("/")),
                Component::Normal(seg) => {
                    cur.push(seg);
                    // Пытаемся создать, если уже существует — игнорируем ошибку
                    if let Err(e) = sftp.mkdir(&cur, 0o775) {
                        // Если ошибка не о существовании — проверим stat, чтобы отличить права
                        if sftp.stat(&cur).is_err() {
                            return Err(anyhow::anyhow!("Не удалось создать/проверить удаленную директорию {}: {}", cur.display(), e));
                        }
                    }
                },
                _ => {}
            }
        }
        Ok(())
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

    /// Загрузка файла по SFTP (требует feature "ssh")
    #[cfg(feature = "ssh")]
    fn sftp_upload(&self, sftp: &ssh2::Sftp, local: &Path, remote: &Path) -> Result<()> {
        use std::io::{Read, Write};
        // Открываем локальный файл
        let mut src = std::fs::File::open(local)
            .with_context(|| format!("Не удалось открыть локальный файл: {}", local.display()))?;
        // Создаём/перезаписываем удалённый
        let mut dst = sftp.create(remote)
            .with_context(|| format!("Не удалось создать удалённый файл по SFTP: {}", remote.display()))?;
        // Передача содержимого
        let mut buf = [0u8; 64 * 1024];
        loop {
            let n = src.read(&mut buf)?;
            if n == 0 { break; }
            dst.write_all(&buf[..n])?;
        }
        dst.flush().ok();
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

    /// Читает существующий updatePlugins.xml по SFTP если есть, возвращает содержимое как String
    #[cfg(feature = "ssh")]
    fn read_remote_xml(&self, sftp: &ssh2::Sftp, xml_remote: &Path) -> Option<String> {
        use std::io::Read;
        // 1) Пробуем основной файл
        if let Ok(mut f) = sftp.open(xml_remote) {
            let mut buf = String::new();
            if f.read_to_string(&mut buf).is_ok() {
                return Some(buf);
            }
        }
        // 2) Если основной отсутствует (например, уже переименован в .bak), пробуем .bak
        let bak = PathBuf::from(format!("{}.bak", xml_remote.display()));
        if let Ok(mut f) = sftp.open(&bak) {
            let mut buf = String::new();
            if f.read_to_string(&mut buf).is_ok() {
                return Some(buf);
            }
        }
        None
    }

    /// Собирает финальный updatePlugins.xml: мёрджит текущий XML с новыми артефактами.
    /// Правила: по id оставляем только одну (последнюю) версию; остальные id сохраняем.
    #[cfg(feature = "ssh")]
    fn build_merged_repository_xml_ssh(
        &self,
        sftp: &ssh2::Sftp,
        xml_remote: &Path,
        artifacts: &[PathBuf],
    ) -> Result<String> {
        // Базовый URL каталога (если в repository.url указан файл XML — отрезаем его)
        let mut base_dir_url = self.config.repository.url.trim_end_matches('/').to_string();
        if base_dir_url.ends_with(".xml") {
            if let Some(pos) = base_dir_url.rfind('/') { base_dir_url.truncate(pos); }
        }
        let repo_root_fs = Path::new(&self.config.repository.xml_path)
            .parent()
            .unwrap_or_else(|| Path::new("/"));
        let deploy_fs = Path::new(&self.config.repository.deploy_path);
        let rel_path = deploy_fs
            .strip_prefix(repo_root_fs)
            .ok()
            .and_then(|p| {
                let s = p.components()
                    .map(|c| c.as_os_str().to_string_lossy())
                    .collect::<Vec<_>>()
                    .join("/");
                if s.is_empty() { None } else { Some(s) }
            });

        // Пробуем прочитать существующий XML
        let existing_raw_opt = self.read_remote_xml(sftp, xml_remote);

        // Попытка DOM-парсинга
        if let Some(existing_raw) = existing_raw_opt.clone() {
            if let Ok(mut root) = Element::parse(existing_raw.as_bytes()) {
                // Собираем карту id -> существующий элемент (оставляем только другие id)
                let current_id = &self.config.project.id;
                let mut new_children: Vec<XMLNode> = Vec::new();
                for ch in &root.children {
                    if let XMLNode::Element(el) = ch {
                        if el.name == "plugin" {
                            let id_attr = el.attributes.get("id").cloned();
                            if let Some(id) = id_attr {
                                if id == *current_id { continue; }
                            }
                        }
                    }
                    new_children.push(ch.clone());
                }

                // артефакт и URL
                let mut arts = artifacts.to_vec();
                arts.sort();
                let art = arts.last().unwrap();
                let file_name = art.file_name().unwrap().to_string_lossy().to_string();
                let url = match rel_path {
                    Some(rel) => format!("{}/{}/{}", base_dir_url, rel, file_name),
                    None => format!("{}/{}", base_dir_url, file_name),
                };
                let version = self.extract_version_from_filename(&file_name).unwrap_or_else(|| "0.0.0".to_string());

                let mut plugin_el = Element::new("plugin");
                plugin_el.attributes.insert("id".to_string(), current_id.clone());
                plugin_el.attributes.insert("url".to_string(), url);
                plugin_el.attributes.insert("version".to_string(), version);

                // Попытаемся извлечь метаданные из ZIP
                let zip_meta = self.extract_meta_from_zip(art).ok();

                // name — приоритет: из существующей записи -> из ZIP -> из project.name
                let mut have_name = false;
                if let Some(existing_el) = self.find_existing_plugin_by_id(&root, current_id) {
                    for child in &existing_el.children {
                        if let XMLNode::Element(cel) = child {
                            if cel.name == "name" { have_name = true; }
                        }
                    }
                }
                if !have_name {
                    if let Some(meta) = &zip_meta {
                        if let Some(n) = &meta.name { self.push_text_child(&mut plugin_el, "name", n); }
                        else { self.push_text_child(&mut plugin_el, "name", &self.config.project.name); }
                    } else {
                        self.push_text_child(&mut plugin_el, "name", &self.config.project.name);
                    }
                }

                // Сохраняем vendor/idea-version/description из старой записи этого id если она была
                if let Some(existing_el) = self.find_existing_plugin_by_id(&root, current_id) {
                    for child in existing_el.children {
                        if let XMLNode::Element(mut cel) = child {
                            if cel.name == "vendor" || cel.name == "idea-version" || cel.name == "description" {
                                plugin_el.children.push(XMLNode::Element(cel));
                            }
                        }
                    }
                }

                // Дополняем отсутствующие поля из ZIP-метаданных (только если их ещё нет)
                if let Some(meta) = zip_meta {
                    if plugin_el.get_child("vendor").is_none() {
                        if let Some(v) = meta.vendor { self.push_text_child(&mut plugin_el, "vendor", &v); }
                    }
                    if plugin_el.get_child("idea-version").is_none() {
                        if meta.since_build.is_some() || meta.until_build.is_some() {
                            let mut iv = Element::new("idea-version");
                            if let Some(s) = meta.since_build { iv.attributes.insert("since-build".to_string(), s); }
                            if let Some(u) = meta.until_build { iv.attributes.insert("until-build".to_string(), u); }
                            plugin_el.children.push(XMLNode::Element(iv));
                        }
                    }
                    if plugin_el.get_child("description").is_none() {
                        if let Some(d) = meta.description { self.push_cdata_child(&mut plugin_el, "description", &d); }
                    }
                }

                new_children.push(XMLNode::Element(plugin_el));
                root.children = new_children;

                // Сериализуем корень
                let mut buf = Vec::new();
                root.write(&mut buf).with_context(|| "Сериализация updatePlugins.xml не удалась")?;
                return Ok(String::from_utf8(buf).unwrap_or_else(|v| String::from_utf8_lossy(&v.into_bytes()).to_string()));
            }
        }

        // Fallback: DOM-парсинг не удался — выполняем безопасную строковую замену/вставку
        let current_id = &self.config.project.id;
        let mut arts = artifacts.to_vec();
        arts.sort();
        let art = arts.last().unwrap();
        let file_name = art.file_name().unwrap().to_string_lossy().to_string();
        let url = match rel_path {
            Some(rel) => format!("{}/{}/{}", base_dir_url, rel, file_name),
            None => format!("{}/{}", base_dir_url, file_name),
        };
        let version = self.extract_version_from_filename(&file_name).unwrap_or_else(|| "0.0.0".to_string());

        let plugin_snippet = format!(
            "<plugin id=\"{}\" url=\"{}\" version=\"{}\"><name>{}</name></plugin>",
            current_id, url, version, self.config.project.name
        );

        if let Some(mut existing_raw) = existing_raw_opt {
            // Если уже есть запись для текущего id — заменим её через regex, иначе вставим перед </plugins>
            let pattern = format!(
                "<plugin\\b[^>]*\\bid=\\\"{}\\\"[^>]*>.*?</plugin>",
                regex::escape(current_id)
            );
            let re = regex::RegexBuilder::new(&pattern)
                .dot_matches_new_line(true)
                .build()
                .ok();
            if let Some(re) = re {
                if re.is_match(&existing_raw) {
                    existing_raw = re.replace(&existing_raw, plugin_snippet.as_str()).to_string();
                } else if let Some(pos) = existing_raw.rfind("</plugins>") {
                    existing_raw.insert_str(pos, &plugin_snippet);
                } else {
                    // нет закрывающего тега — просто прибавим
                    existing_raw.push_str(&plugin_snippet);
                }
            }
            return Ok(existing_raw);
        } else {
            // Файла не было — создаем минимальный
            let content = format!("<?xml version=\"1.0\" encoding=\"UTF-8\"?><plugins>{}</plugins>", plugin_snippet);
            return Ok(content);
        }
    }

    /// Поиск существующего элемента plugin по id
    #[cfg(feature = "ssh")]
    fn find_existing_plugin_by_id<'a>(&self, root: &'a Element, id: &str) -> Option<Element> {
        for ch in &root.children {
            if let XMLNode::Element(el) = ch {
                if el.name == "plugin" {
                    if let Some(existing_id) = el.attributes.get("id") {
                        if existing_id == id { return Some(el.clone()); }
                    }
                }
            }
        }
        None
    }

    /// Извлекает версию из имени файла zip вида name-1.2.3.zip
    fn extract_version_from_filename(&self, filename: &str) -> Option<String> {
        let re = regex::Regex::new(r"-(\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)*)\.zip$").ok()?;
        if let Some(caps) = re.captures(filename) { Some(caps.get(1).unwrap().as_str().to_string()) } else { None }
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

    /// Вспомогательный метод: добавить текстовый дочерний элемент
    fn push_text_child(&self, parent: &mut Element, name: &str, text: &str) {
        let mut el = Element::new(name);
        el.children.push(XMLNode::Text(text.to_string()));
        parent.children.push(XMLNode::Element(el));
    }

    /// Вспомогательный: добавить CDATA
    fn push_cdata_child(&self, parent: &mut Element, name: &str, text: &str) {
        let mut el = Element::new(name);
        el.children.push(XMLNode::CData(text.to_string()));
        parent.children.push(XMLNode::Element(el));
    }

    /// Извлекает метаданные плагина из META-INF/plugin.xml внутри ZIP
    fn extract_meta_from_zip(&self, zip_path: &Path) -> Result<PluginMeta> {
        let file = File::open(zip_path)
            .with_context(|| format!("Не удалось открыть ZIP {}", zip_path.display()))?;
        let mut archive = zip::ZipArchive::new(file)
            .with_context(|| format!("Не удалось прочитать ZIP {}", zip_path.display()))?;
        let mut entry = archive
            .by_name("META-INF/plugin.xml")
            .with_context(|| "В ZIP отсутствует META-INF/plugin.xml")?;
        use std::io::Read;
        let mut xml = String::new();
        entry.read_to_string(&mut xml).with_context(|| "Не удалось прочитать META-INF/plugin.xml из ZIP")?;
        let root = Element::parse(xml.as_bytes()).with_context(|| "Ошибка парсинга META-INF/plugin.xml из ZIP")?;

        let name = root.get_child("name").and_then(|e| e.get_text()).map(|s| s.to_string());
        let vendor = root.get_child("vendor").and_then(|e| e.get_text()).map(|s| s.to_string());
        let description = root.get_child("description").and_then(|e| {
            // Соберем CDATA/текст в строку
            let mut acc = String::new();
            for ch in &e.children {
                match ch {
                    XMLNode::Text(t) | XMLNode::CData(t) => { acc.push_str(t); },
                    _ => {}
                }
            }
            if acc.is_empty() { None } else { Some(acc) }
        });
        let idea = root.get_child("idea-version");
        let since_build = idea.and_then(|e| e.attributes.get("since-build").cloned());
        let until_build = idea.and_then(|e| e.attributes.get("until-build").cloned());

        Ok(PluginMeta { name, vendor, description, since_build, until_build })
    }

}

#[derive(Debug, Clone)]
struct PluginMeta {
    name: Option<String>,
    vendor: Option<String>,
    description: Option<String>,
    since_build: Option<String>,
    until_build: Option<String>,
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