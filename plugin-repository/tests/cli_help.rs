use assert_cmd::prelude::*;
use std::process::Command;

#[test]
fn help_main_and_subcommands() {
    // main help
    let mut cmd = Command::cargo_bin("deploy-pugin").unwrap();
    cmd.arg("--help").assert().success();

    // subcommands help
    for sub in ["build", "release", "deploy", "ai", "validate", "status"] {
        let mut c = Command::cargo_bin("deploy-pugin").unwrap();
        c.args([sub, "--help"]).assert().success();
    }
}
