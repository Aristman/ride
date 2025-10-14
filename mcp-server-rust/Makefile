.PHONY: help build test run clean docker-build docker-run fmt clippy

help:
	@echo "Available commands:"
	@echo "  make build        - Build the project"
	@echo "  make test         - Run tests"
	@echo "  make run          - Run the server"
	@echo "  make clean        - Clean build artifacts"
	@echo "  make docker-build - Build Docker image"
	@echo "  make docker-run   - Run Docker container"
	@echo "  make fmt          - Format code"
	@echo "  make clippy       - Run clippy linter"

build:
	cargo build --release

test:
	cargo test

run:
	cargo run

clean:
	cargo clean

docker-build:
	docker build -t mcp-server-rust .

docker-run:
	docker-compose up -d

docker-stop:
	docker-compose down

fmt:
	cargo fmt

clippy:
	cargo clippy -- -D warnings

check:
	cargo check

watch:
	cargo watch -x run

audit:
	cargo audit
