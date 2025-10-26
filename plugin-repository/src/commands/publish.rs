use anyhow::{Context, Result};
use colored::*;
use tracing::{info, warn};

use crate::cli::publish::PublishCommand;
use crate::config::parser::Config;
use crate::core::builder::PluginBuilder;
use crate::core::deployer::Deployer;
use crate::core::releaser::ReleaseManager;
use crate::core::llm::agents::LLMAgentManager;
use crate::git::GitRepository;
use std::path::{Path, PathBuf};

/// Обработчик команды полного цикла публикации
pub async fn handle_publish_command(cmd: PublishCommand, config_file: &str) -> Result<()> {
    info!("🧩 Запуск полного цикла публикации");

    // 1) Загрузка и (опционально) валидация конфигурации
    let config = Config::load_from_file(config_file)
        .with_context(|| format!("Не удалось загрузить конфигурацию из файла: {}", config_file))?;
    if !cmd.skip_validation {
        config.validate().context("Валидация конфигурации не пройдена")?;
    }

    let project_root = std::env::current_dir().context("Не удалось определить текущую директорию")?;
    let git_repo = GitRepository::new(&project_root);
    if !git_repo.is_valid_repository() {
        anyhow::bail!("Текущая директория не является git репозиторием");
    }

    // Инициализируем LLM/Release менеджеры один раз
    let agent_manager = LLMAgentManager::from_config(&config)
        .context("Не удалось создать LLM агент менеджер")?;
    let releaser = ReleaseManager::new(git_repo.clone(), agent_manager, config.project.clone());

    // 2) Определение версии
    let version = if let Some(v) = cmd.version.clone() {
        v
    } else if cmd.auto_version {
        let prep = releaser.prepare_release(None).await?;
        if !prep.success {
            anyhow::bail!("Подготовка релиза не удалась");
        }
        prep.release.version
    } else {
        anyhow::bail!("Не указана версия. Используйте --version или --auto-version");
    };

    println!("{} Версия: {}", "🏷️", version.bright_green());

    // 3) Обогащение plugin.xml (по умолчанию) до сборки, чтобы мета попала в артефакт
    if !cmd.no_ai {
        if let Err(e) = enrich_plugin_xml(&project_root, &config, &version, Some((&git_repo, &releaser))).await {
            warn!("AI/обогащение plugin.xml: {} (продолжаем)", e);
        }
    } else {
        // Даже без AI заполним минимальные отсутствующие поля (id/name/version) без генерации описания
        if let Err(e) = enrich_plugin_xml(&project_root, &config, &version, None).await {
            warn!("Обогащение plugin.xml без AI: {} (продолжаем)", e);
        }
    }

    // 4) Сборка артефакта с заданной версией
    let builder = PluginBuilder::new(config.clone(), project_root.clone());
    let build_res = builder.build(Some(version.clone()), &cmd.profile).await?;
    if !build_res.success {
        anyhow::bail!("Сборка завершилась с ошибками");
    }
    println!("{} Сборка завершена", "✅");

    // 5) Создание и публикация релиза (если не dry-run)

    if cmd.dry_run {
        println!("{} DRY RUN — релиз и деплой пропущены", "🧪");
        return Ok(());
    }

    // По умолчанию обогащаем релиз данными от LLM, если не отключено флагом
    let mut release_message: Option<String> = None;
    if !cmd.no_ai {
        match releaser.prepare_release(Some(version.clone())).await {
            Ok(prep) => {
                if let Some(notes) = prep.release.release_notes {
                    release_message = Some(notes);
                } else if let Some(changelog) = prep.release.changelog {
                    release_message = Some(format!("Changelog for v{}\n\n{}", version, changelog));
                }
            }
            Err(e) => {
                warn!("AI-обогащение пропущено: {}", e);
            }
        }
    } else {
        info!("AI-обогащение отключено флагом --no-ai");
    }

    println!("{} Создание релиза...", "🚀");
    let _tag = releaser.create_release(&version, release_message).await?;
    println!("{} Релиз создан", "✅");

    println!("{} Публикация релиза...", "📤");
    releaser.publish_release(&version).await?;
    println!("{} Релиз опубликован", "✅");

    // 6) Деплой
    let deployer = Deployer::new(config.clone());
    if !cmd.skip_validation {
        if let Err(e) = deployer.validate().await {
            if cmd.force {
                warn!("Валидация перед деплоем не пройдена: {} (продолжаем из-за --force)", e);
            } else {
                anyhow::bail!("Валидация перед деплоем не пройдена: {}", e);
            }
        }
    }

    println!("{} Деплой...", "🚚");
    deployer.deploy(cmd.force, cmd.rollback_on_failure).await?;
    println!("{} Деплой завершен", "✅");

    Ok(())
}

/// Обновляет/создает META-INF/plugin.xml, заполняя отсутствующие поля.
async fn enrich_plugin_xml(
    project_root: &Path,
    config: &Config,
    version: &str,
    ai_context: Option<(&GitRepository, &ReleaseManager)>,
) -> Result<()> {
    use xmltree::{Element, XMLNode};
    use std::fs;

    // Путь к plugin.xml (Gradle/Maven одинаково)
    let meta_inf = project_root.join("src/main/resources/META-INF");
    fs::create_dir_all(&meta_inf).ok();
    let plugin_xml_path = meta_inf.join("plugin.xml");

    // Скелет, если файла нет
    let mut root: Element = if plugin_xml_path.exists() {
        let data = fs::read_to_string(&plugin_xml_path)
            .with_context(|| format!("Не удалось прочитать {}", plugin_xml_path.display()))?;
        Element::parse(data.as_bytes()).with_context(|| "Ошибка парсинга plugin.xml")?
    } else {
        Element::new("idea-plugin")
    };

    // Вспомогательные функции
    fn get_child_mut<'a>(root: &'a mut Element, name: &str) -> Option<&'a mut Element> {
        root.get_mut_child(name)
    }
    fn ensure_text_child(root: &mut Element, name: &str, text: &str) {
        if root.get_child(name).is_none() {
            let mut el = Element::new(name);
            el.children.push(XMLNode::Text(text.to_string()));
            root.children.push(XMLNode::Element(el));
        }
    }

    // Заполняем только отсутствующее
    ensure_text_child(&mut root, "id", &config.project.id);
    ensure_text_child(&mut root, "name", &config.project.name);
    ensure_text_child(&mut root, "version", version);

    // vendor: оставляем как есть, не создаем автоматически (берем из проекта)

    // idea-version: не трогаем, если нет явных значений в проекте
    // (требование: брать since-build/until-build из проекта, не генерировать)

    // Описание/ноты — только если отсутствуют
    let mut description_to_set: Option<String> = None;
    let mut changelog_to_set: Option<String> = None;

    if root.get_child("description").is_none() || root.get_child("change-notes").is_none() {
        if let Some((_git, releaser)) = ai_context {
            if let Ok(prep) = releaser.prepare_release(Some(version.to_string())).await {
                if root.get_child("description").is_none() {
                    // Используем release notes как описание, если отдельного генератора нет
                    if let Some(notes) = prep.release.release_notes.clone() {
                        description_to_set = Some(notes);
                    }
                }
                if root.get_child("change-notes").is_none() {
                    if let Some(changelog) = prep.release.changelog.clone() {
                        changelog_to_set = Some(changelog);
                    }
                }
            }
        }
    }

    if root.get_child("description").is_none() {
        if let Some(desc) = description_to_set {
            let mut el = Element::new("description");
            el.children.push(XMLNode::CData(desc));
            root.children.push(XMLNode::Element(el));
        }
    }
    if root.get_child("change-notes").is_none() {
        if let Some(ch) = changelog_to_set {
            let mut el = Element::new("change-notes");
            el.children.push(XMLNode::CData(ch));
            root.children.push(XMLNode::Element(el));
        }
    }

    // Сохраняем
    let mut buf = Vec::new();
    root.write(&mut buf).with_context(|| "Не удалось сериализовать plugin.xml")?;
    fs::write(&plugin_xml_path, buf).with_context(|| format!("Не удалось сохранить {}", plugin_xml_path.display()))?;

    info!(
        "Обновлен META-INF/plugin.xml (id/name/version/description/change-notes при отсутствии)"
    );
    Ok(())
}
