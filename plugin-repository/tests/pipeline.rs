use assert_cmd::prelude::*;
use predicates::prelude::*;
use std::process::Command;
use std::fs;

fn write_config(dir: &std::path::Path) {
    let cfg = r#"[project]
name = "ride"
id = "ru.marslab.ide.ride"
type = "intellij"

[build]
gradle_task = "buildPlugin"
output_dir = "build/distributions"

[repository]
url = "${REPOSITORY_URL}"
ssh_host = "${SSH_HOST}"
ssh_user = "${SSH_USER}"
deploy_path = "${DEPLOY_PATH}"
xml_path = "${XML_PATH}"

[llm]
provider = "yandexgpt"
temperature = 0.3
max_tokens = 2000

[yandexgpt]
api_key = "${DEPLOY_PLUGIN_YANDEX_API_KEY}"
folder_id = "${DEPLOY_PLUGIN_YANDEX_FOLDER_ID}"
model = "yandexgpt/latest"

[llm_agents]
changelog_agent = { model = "yandexgpt", temperature = 0.3 }
version_agent = { model = "yandexgpt-lite", temperature = 0.1 }
release_agent = { model = "yandexgpt", temperature = 0.4 }

[git]
main_branch = "main"
tag_prefix = "v"
"#;
    fs::write(dir.join("config.toml"), cfg).expect("write config");
}

#[test]
fn pipeline_status_validate_smoke() {
    let tmp = tempfile::tempdir().expect("tempdir");
    let dir = tmp.path();

    // init git repo
    assert_cmd::cargo::CommandCargoExt::cargo_bin("deploy-pugin").ok();
    // Create minimal git repo structure
    Command::new("git").current_dir(dir).args(["init"]).assert().success();
    fs::write(dir.join("README.md"), "test").unwrap();
    Command::new("git").current_dir(dir).args(["add", "."]).assert().success();
    Command::new("git").current_dir(dir).args(["commit", "-m", "init"]).assert().success();

    write_config(dir);

    // env for config substitution
    let mut cmd = Command::cargo_bin("deploy-pugin").unwrap();
    cmd.current_dir(dir)
        .env("REPOSITORY_URL", "http://example.com/updatePlugins.xml")
        .env("SSH_HOST", "example.com")
        .env("SSH_USER", "user")
        .env("DEPLOY_PATH", "/tmp/plugins/")
        .env("XML_PATH", "/tmp/updatePlugins.xml")
        .env("DEPLOY_PLUGIN_YANDEX_API_KEY", "test_key")
        .env("DEPLOY_PLUGIN_YANDEX_FOLDER_ID", "test_folder")
        .args(["status", "--repository", "--releases", "--format", "json"])
        .assert()
        .success()
        .stdout(predicate::str::contains("["));

    // validate full
    let mut cmd2 = Command::cargo_bin("deploy-pugin").unwrap();
    cmd2.current_dir(dir)
        .env("REPOSITORY_URL", "http://example.com/updatePlugins.xml")
        .env("SSH_HOST", "example.com")
        .env("SSH_USER", "user")
        .env("DEPLOY_PATH", "/tmp/plugins/")
        .env("XML_PATH", "/tmp/updatePlugins.xml")
        .env("DEPLOY_PLUGIN_YANDEX_API_KEY", "test_key")
        .env("DEPLOY_PLUGIN_YANDEX_FOLDER_ID", "test_folder")
        .args(["validate", "--full"])
        .assert()
        .success();
}
