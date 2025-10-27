#!/bin/bash
# 🚀 Скрипт быстрой настройки окружения

echo "🔧 Настройка окружения deploy-pugin..."

# Копируем пример .env
if [ ! -f .env ]; then
    cp .env.example .env
    echo "✅ Создан файл .env - отредактируйте его с вашими данными"
else
    echo "⚠️  Файл .env уже существует"
fi

# Копируем конфигурацию
if [ ! -f config.toml ]; then
    cp config.toml.example config.toml
    echo "✅ Создан файл config.toml"
else
    echo "⚠️  Файл config.toml уже существует"
fi

echo ""
echo "🔒 ВАЖНО: Отредактируйте .env файл с вашими секретными данными!"
echo "📝 Не добавляйте .env в git репозиторий!"
echo ""
echo "🚀 Команда для запуска:"
echo "   cargo run -- release --dry-run"