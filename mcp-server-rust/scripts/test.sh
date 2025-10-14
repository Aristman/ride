#!/bin/bash

# Test script for MCP Server Rust

set -e

echo "🧪 Running tests for MCP Server Rust..."

# Run unit tests
echo "📦 Running unit tests..."
cargo test --lib

# Run integration tests
echo "🔗 Running integration tests..."
cargo test --test '*'

# Run doc tests
echo "📚 Running doc tests..."
cargo test --doc

# Check formatting
echo "🎨 Checking code formatting..."
cargo fmt --check

# Run clippy
echo "📎 Running clippy..."
cargo clippy -- -D warnings

# Check for security vulnerabilities
if command -v cargo-audit &> /dev/null; then
    echo "🔒 Checking for security vulnerabilities..."
    cargo audit
else
    echo "⚠️  cargo-audit not installed. Install with: cargo install cargo-audit"
fi

echo "✅ All tests passed!"
