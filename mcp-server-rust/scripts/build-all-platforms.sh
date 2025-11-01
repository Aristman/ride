#!/bin/bash

# Скрипт для кросс-компиляции MCP сервера для всех платформ

set -e

echo "🔧 Building MCP Server for all platforms..."

# Создаем директорию для бинарников
mkdir -p ../src/main/resources/mcp-server

# Устанавливаем targets если нужно
echo "📦 Installing Rust targets..."
rustup target add x86_64-unknown-linux-gnu
rustup target add x86_64-pc-windows-gnu
rustup target add x86_64-apple-darwin
rustup target add aarch64-apple-darwin

# Linux x64
echo "🐧 Building for Linux x64..."
cargo build --release --target x86_64-unknown-linux-gnu
cp target/x86_64-unknown-linux-gnu/release/mcp-server-rust ../src/main/resources/mcp-server/mcp-server-linux-x64

# Windows x64 (требует mingw-w64)
echo "🪟 Building for Windows x64..."
if command -v x86_64-w64-mingw32-gcc &> /dev/null; then
    cargo build --release --target x86_64-pc-windows-gnu
    cp target/x86_64-pc-windows-gnu/release/mcp-server-rust.exe ../src/main/resources/mcp-server/mcp-server-windows-x64.exe
else
    echo "⚠️  mingw-w64 not found, skipping Windows build"
    echo "   Install with: sudo apt-get install gcc-mingw-w64-x86-64"
fi

# macOS (только если запускается на macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "🍎 Building for macOS x64..."
    cargo build --release --target x86_64-apple-darwin
    cp target/x86_64-apple-darwin/release/mcp-server-rust ../src/main/resources/mcp-server/mcp-server-macos-x64
    
    echo "🍎 Building for macOS ARM64..."
    cargo build --release --target aarch64-apple-darwin
    cp target/aarch64-apple-darwin/release/mcp-server-rust ../src/main/resources/mcp-server/mcp-server-macos-arm64
else
    echo "⚠️  Not on macOS, skipping macOS builds"
fi

echo "✅ Build complete! Binaries are in ../src/main/resources/mcp-server/"
ls -la ../src/main/resources/mcp-server/