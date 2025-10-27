# API Обзор (модули и публичные точки)

## CLI (команды)
- `build` — сборка плагина
- `release` — подготовка и публикация релиза
- `deploy` — выкладка артефактов и обновление XML
- `ai` — LLM операции (changelog, suggest-version, release-notes)
- `validate` — валидация конфигурации
- `status` — статус репозитория и история релизов

## Core
- `core::builder::PluginBuilder`
  - `build(version: Option<String>, profile: &str) -> BuildResult`
- `core::releaser::ReleaseManager`
  - `prepare_release(version: Option<String>) -> ReleasePreparationResult`
  - `create_release(version: &str, message: Option<&str>) -> Tag`
  - `publish_release(version: &str)`
  - `get_release_history(limit: Option<usize>) -> Vec<ReleaseInfo>`
- `core::deployer::Deployer`
  - `validate()`
  - `deploy(force: bool, rollback_on_failure: bool)`
  - `atomic_update_xml(path, content)`
  - `upload_artifact(local, remote)` (feature `ssh`)

## Git
- `git::GitRepository`
  - `is_valid_repository()`
  - `history.get_current_branch()`
  - `tags.get_all_tags()` / `get_latest_tag()`
  - `tags.get_commits_between_tags()`

## LLM
- `core::llm::agents::LLMAgentManager::from_config(config)`
- `YandexGPTClient::chat_completion(prompt)`
- `YandexGPTClient::chat_completion_with_retry(prompt, max_retries)`

## Models
- `models::plugin::{BuildResult, PluginArtifact}`
- `models::release::{ReleaseInfo, SemanticVersion}`
- `models::repository::{RepositoryStatus, PluginRepository}`
