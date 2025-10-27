# CI/CD: deploy-pugin

## Цели
- Автоматическая сборка и тестирование
- Быстрый фидбек по PR

## GitHub Actions (пример)
```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
      - name: Cache cargo
        uses: actions/cache@v4
        with:
          path: |
            ~/.cargo/registry
            ~/.cargo/git
            target
          key: ${{ runner.os }}-cargo-${{ hashFiles('**/Cargo.lock') }}
      - name: Build
        run: |
          cd plugin-repository
          cargo build --verbose
      - name: Test
        run: |
          cd plugin-repository
          cargo test --all --verbose
```

## Секреты для релизных пайплайнов
- Храните `DEPLOY_PLUGIN_YANDEX_API_KEY`, `DEPLOY_PLUGIN_YANDEX_FOLDER_ID` в секретах CI
- Не логируйте сырые ключи в вывод
- Для SSH деплоя добавьте приватный ключ как секрет и разверните его в рантайме
