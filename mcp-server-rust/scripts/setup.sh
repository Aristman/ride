#!/bin/bash

# Setup script for MCP Server Rust

set -e

echo "🚀 Setting up MCP Server Rust..."

# Check if Rust is installed
if ! command -v cargo &> /dev/null; then
    echo "❌ Rust is not installed. Please install Rust from https://rustup.rs/"
    exit 1
fi

echo "✅ Rust is installed: $(rustc --version)"

# Create data directory
echo "📁 Creating data directory..."
mkdir -p data

# Copy example config if config doesn't exist
if [ ! -f config.toml ]; then
    echo "📝 Creating config.toml from example..."
    cp config.example.toml config.toml
    echo "⚠️  Please edit config.toml to configure the server"
fi

# Build the project
echo "🔨 Building the project..."
cargo build --release

echo "✅ Setup complete!"
echo ""
echo "To run the server:"
echo "  cargo run --release"
echo ""
echo "To run tests:"
echo "  cargo test"
echo ""
echo "To see all available commands:"
echo "  make help"
