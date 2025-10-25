#!/bin/bash

# ============================================================================
# JetBrains Plugin Repository Deployment Script
# Минималистичный скрипт для быстрого развертывания репозитория плагинов
# Автор: Claude Code Assistant
# Версия: 1.0
# ============================================================================

set -euo pipefail

# ============================================================================
# КОНФИГУРАЦИЯ
# ============================================================================

# IP адрес или доменное имя сервера
# Если не установлено, пытаемся определить автоматически
DOMAIN_NAME="${DOMAIN_NAME:-$(ip route get 1.1.1.1 | awk '{print $7}' | head -1)}"

# Базовый путь репозитория
REPO_BASE_PATH="/var/www/plugins"

# Временная директория для генерации файлов
TEMP_DIR="/tmp/plugin-repo-$$"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ============================================================================
# ФУНКЦИИ
# ============================================================================

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_root() {
    if [[ $EUID -ne 0 ]]; then
        print_error "Этот скрипт должен быть запущен с правами root"
        print_info "Используйте: sudo $0"
        exit 1
    fi
}

update_system() {
    print_info "Обновление пакетов системы..."
    apt update -qq
    apt upgrade -y -qq
    print_success "Система обновлена"
}

install_packages() {
    print_info "Установка необходимых пакетов..."
    apt install -y nginx curl unzip -qq
    print_success "Пакеты установлены"
}

setup_directories() {
    print_info "Создание структуры директорий..."

    # Создаем основную структуру
    mkdir -p "$REPO_BASE_PATH"/{archives,backups}

    # Устанавливаем права
    chown -R www-data:www-data "$REPO_BASE_PATH"
    chmod -R 755 "$REPO_BASE_PATH"

    print_success "Структура директорий создана"
}

create_nginx_config() {
    print_info "Настройка nginx..."

    cat > "/etc/nginx/sites-available/plugin-repository" << EOF
server {
    listen 80;
    server_name $DOMAIN_NAME;

    root $REPO_BASE_PATH;
    index updatePlugins.xml;

    # Правильные MIME-типы
    include mime.types;
    types {
        application/xml xml;
        application/zip zip;
    }

    location / {
        try_files \$uri \$uri/ =404;
    }

    # Кэширование для статических файлов
    location ~* \.(zip)$ {
        expires 1d;
        add_header Cache-Control "public";
    }

    # Без кэширования для XML
    location ~* \.(xml)$ {
        expires -1;
        add_header Cache-Control "no-cache, must-revalidate";
    }

    access_log /var/log/nginx/plugin-repository.access.log;
    error_log /var/log/nginx/plugin-repository.error.log;
}
EOF

    # Активируем сайт
    ln -sf "/etc/nginx/sites-available/plugin-repository" "/etc/nginx/sites-enabled/"
    rm -f "/etc/nginx/sites-enabled/default"

    # Проверяем конфигурацию и перезапускаем nginx
    nginx -t && systemctl reload nginx
    print_success "Nginx настроен и перезапущен"
}

create_demo_plugin() {
    print_info "Создание демо-плагина..."

    # Временная директория для плагина
    local plugin_temp="$TEMP_DIR/demo-plugin"
    mkdir -p "$plugin_temp"

    # Создаем структуру плагина
    cat > "$plugin_temp/META-INF/plugin.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<idea-plugin>
    <id>com.example.demo.plugin</id>
    <name>Demo Plugin</name>
    <description>Пример плагина для учебного репозитория</description>
    <vendor>Example Company</vendor>
    <idea-version since-build="211.0" until-build="241.*"/>
    <depends>com.intellij.modules.platform</depends>
</idea-plugin>
EOF

    # Создаем простейший класс плагина
    mkdir -p "$plugin_temp/src/main/java/com/example/demo"
    cat > "$plugin_temp/src/main/java/com/example/demo/DemoPlugin.java" << 'EOF'
package com.example.demo;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

public class DemoPlugin implements ApplicationComponent {
    @Override
    public void initComponent() {
        System.out.println("Demo Plugin initialized!");
    }
}
EOF

    # Архивируем плагин
    cd "$plugin_temp"
    zip -r "$REPO_BASE_PATH/archives/demo-plugin-1.0.0.zip" . > /dev/null 2>&1
    cd - > /dev/null

    print_success "Демо-плагин создан и архивирован"
}

create_update_plugins_xml() {
    print_info "Создание updatePlugins.xml..."

    cat > "$REPO_BASE_PATH/updatePlugins.xml" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="com.example.demo.plugin"
          url="http://$DOMAIN_NAME/archives/demo-plugin-1.0.0.zip"
          version="1.0.0">
    <name>Demo Plugin</name>
    <description>Пример плагина для учебного репозитория</description>
    <vendor>Example Company</vendor>
    <idea-version since-build="211.0" until-build="241.*"/>
  </plugin>
</plugins>
EOF

    chown www-data:www-data "$REPO_BASE_PATH/updatePlugins.xml"
    chmod 644 "$REPO_BASE_PATH/updatePlugins.xml"

    print_success "updatePlugins.xml создан"
}

create_readme() {
    print_info "Создание README..."

    cat > "$REPO_BASE_PATH/README.md" << EOF
# JetBrains Plugin Repository

Минималистичный репозиторий плагинов JetBrains для обучения и тестирования.

## Структура

- \`archives/\` - ZIP-архивы плагинов
- \`backups/\` - Резервные копии
- \`updatePlugins.xml\` - Индексный файл репозитория

## URL репозитория

\`\`\`
http://$DOMAIN_NAME/updatePlugins.xml
\`\`\`

## Управление

Для добавления нового плагина используйте скрипт \`add-plugin.sh\`:

\`\`\`bash
sudo /opt/plugin-repo/add-plugin.sh plugin-id "Plugin Name" "Description" "Vendor" /path/to/plugin.zip
\`\`\`

## Проверка работоспособности

\`\`\`bash
curl http://$DOMAIN_NAME/updatePlugins.xml
\`\`\`

---
*Развернуто автоматически: $(date)*
EOF

    print_success "README создан"
}

show_results() {
    echo
    echo "==============================================================================="
    print_success "РАВЕРТЫВАНИЕ ЗАВЕРШЕНО!"
    echo "==============================================================================="
    echo
    print_info "URL репозитория:"
    echo -e "${GREEN}http://$DOMAIN_NAME/updatePlugins.xml${NC}"
    echo
    print_info "Путь к директории с плагинами:"
    echo -e "${GREEN}$REPO_BASE_PATH/archives/${NC}"
    echo
    print_info "Пример команды для добавления плагина:"
    echo -e "${YELLOW}sudo /opt/plugin-repo/add-plugin.sh com.my.plugin \"My Plugin\" \"Description\" \"My Company\" /path/to/plugin.zip${NC}"
    echo
    print_info "Инструкция по настройке в IntelliJ IDEA:"
    echo "1. File → Settings → Plugins"
    echo "2. Нажать ⚙️ → Manage Plugin Repositories..."
    echo "3. Нажать + и добавить URL: http://$DOMAIN_NAME/updatePlugins.xml"
    echo "4. Найти и установить плагины из репозитория"
    echo
}

cleanup() {
    rm -rf "$TEMP_DIR"
}

# ============================================================================
# ОСНОВНОЙ СКРИПТ
# ============================================================================

main() {
    print_info "Начало развертывания репозитория плагинов JetBrains..."
    print_info "Целевой сервер: $DOMAIN_NAME"
    echo

    # Проверяем права root
    check_root

    # Устанавливаем обработчик очистки
    trap cleanup EXIT

    # Создаем временную директорию
    mkdir -p "$TEMP_DIR"

    # Основные шаги развертывания
    update_system
    install_packages
    setup_directories
    create_nginx_config
    create_demo_plugin
    create_update_plugins_xml
    create_readme

    # Показываем результаты
    show_results

    print_success "Развертывание завершено успешно!"
}

# Запуск
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi