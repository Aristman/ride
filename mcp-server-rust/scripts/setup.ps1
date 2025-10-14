# Setup script for MCP Server Rust (PowerShell)

Write-Host "🚀 Setting up MCP Server Rust..." -ForegroundColor Green

# Check if Rust is installed
if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Rust is not installed. Please install Rust from https://rustup.rs/" -ForegroundColor Red
    exit 1
}

$rustVersion = cargo --version
Write-Host "✅ Rust is installed: $rustVersion" -ForegroundColor Green

# Create data directory
Write-Host "📁 Creating data directory..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path "data" | Out-Null

# Copy example config if config doesn't exist
if (-not (Test-Path "config.toml")) {
    Write-Host "📝 Creating config.toml from example..." -ForegroundColor Cyan
    Copy-Item "config.example.toml" "config.toml"
    Write-Host "⚠️  Please edit config.toml to configure the server" -ForegroundColor Yellow
}

# Build the project
Write-Host "🔨 Building the project..." -ForegroundColor Cyan
cargo build --release

Write-Host "`n✅ Setup complete!" -ForegroundColor Green
Write-Host "`nTo run the server:" -ForegroundColor Cyan
Write-Host "  cargo run --release"
Write-Host "`nTo run tests:" -ForegroundColor Cyan
Write-Host "  cargo test"
Write-Host "`nTo see all available commands:" -ForegroundColor Cyan
Write-Host "  make help"
