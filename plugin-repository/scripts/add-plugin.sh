#!/bin/bash

# ============================================================================
# Add Plugin Script for JetBrains Plugin Repository
# Скрипт для добавления нового плагина в репозиторий
# ============================================================================

set -euo pipefail

# Конфигурация
REPO_BASE_PATH="/var/www/plugins"
UPDATE_XML="$REPO_BASE_PATH/updatePlugins.xml"
ARCHIVES_DIR="$REPO_BASE_PATH/archives"
BACKUP_DIR="$REPO_BASE_PATH/backups"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Функции вывода
print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Гарантируем наличие нужных директорий и базового XML
ensure_layout() {
    mkdir -p "$ARCHIVES_DIR" "$BACKUP_DIR"
    if [[ ! -f "$UPDATE_XML" ]]; then
        print_warning "Файл $UPDATE_XML не найден — создаю базовую структуру"
        cat > "$UPDATE_XML" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
</plugins>
EOF
        chown www-data:www-data "$UPDATE_XML" || true
        chmod 644 "$UPDATE_XML" || true
    fi
}

# Проверка аргументов
check_args() {
    if [[ $# -ne 5 ]]; then
        echo "Использование: $0 <plugin-id> <plugin-name> <description> <vendor> <path-to-zip>"
        echo
        echo "Пример:"
        echo "  $0 com.my.plugin \"My Plugin\" \"Useful plugin\" \"My Company\" /tmp/my-plugin.zip"
        exit 1
    fi
}

# Проверка прав root
check_root() {
    if [[ $EUID -ne 0 ]]; then
        print_error "Этот скрипт должен быть запущен с правами root"
        exit 1
    fi
}

# Валидация ID плагина
validate_plugin_id() {
    local plugin_id="$1"
    if [[ ! "$plugin_id" =~ ^[a-zA-Z0-9.-]+$ ]]; then
        print_error "ID плагина может содержать только буквы, цифры, точки и дефисы"
        exit 1
    fi
}

# Проверка существования файла
check_zip_file() {
    local zip_file="$1"
    if [[ ! -f "$zip_file" ]]; then
        print_error "Файл $zip_file не найден"
        exit 1
    fi

    if [[ ! "$zip_file" =~ \.zip$ ]]; then
        print_error "Файл должен иметь расширение .zip"
        exit 1
    fi
}

# Извлечение версии из plugin.xml
extract_version() {
    local zip_file="$1"
    local temp_dir="/tmp/plugin-extract-$$"

    mkdir -p "$temp_dir"
    unzip -q "$zip_file" -d "$temp_dir"

    local plugin_xml
    plugin_xml=$(find "$temp_dir" -name "plugin.xml" | head -1)

    if [[ -z "$plugin_xml" ]]; then
        rm -rf "$temp_dir"
        print_error "plugin.xml не найден в архиве"
        exit 1
    fi

    local version
    version=$(grep -oP '(?<=<version>)[^<]+' "$plugin_xml" 2>/dev/null || echo "1.0.0")

    rm -rf "$temp_dir"
    echo "$version"
}

# Создание резервной копии
backup_xml() {
    local backup_file="$BACKUP_DIR/updatePlugins-$(date +%Y%m%d-%H%M%S).xml"
    if [[ -f "$UPDATE_XML" ]]; then
        cp "$UPDATE_XML" "$backup_file"
        print_info "Создана резервная копия: $backup_file"
    else
        print_warning "Пропускаю резервное копирование: $UPDATE_XML не найден"
    fi
}

# Добавление плагина в XML
add_plugin_to_xml() {
    local plugin_id="$1"
    local plugin_name="$2"
    local description="$3"
    local vendor="$4"
    local zip_name="$5"
    local domain="$6"
    local version="$7"

    # Экранируем специальные символы для XML
    local escaped_name="${plugin_name//&/&amp;}"
    local escaped_description="${description//&/&amp;}"
    local escaped_vendor="${vendor//&/&amp;}"

    # Создаем XML-элемент плагина
    local plugin_entry="  <plugin id=\"$plugin_id\"\n          url=\"http://$domain/archives/$zip_name\"\n          version=\"$version\">\n    <name>$escaped_name</name>\n    <description>$escaped_description</description>\n    <vendor>$escaped_vendor</vendor>\n    <idea-version since-build=\"211.0\" until-build=\"241.*\"/>\n  </plugin>"

    # Вставляем перед закрывающим тегом </plugins>
    sed -i "s|</plugins>|$plugin_entry\n</plugins>|" "$UPDATE_XML"

    print_success "Плагин добавлен в updatePlugins.xml"
}

# Основная функция
main() {
    check_args "$@"
    check_root

    local plugin_id="$1"
    local plugin_name="$2"
    local description="$3"
    local vendor="$4"
    local zip_file="$5"

    # Валидация
    validate_plugin_id "$plugin_id"
    check_zip_file "$zip_file"

    # Проверяем, что директория репозитория существует
    if [[ ! -d "$REPO_BASE_PATH" ]]; then
        print_error "Директория репозитория не найдена. Сначала запустите deploy-repository.sh"
        exit 1
    fi

    # Гарантируем наличие директорий и базового XML
    ensure_layout

    # Определяем домен
    local domain=""
    if [[ -f "$UPDATE_XML" ]]; then
        domain=$(grep -oP '(?<=url="http://)[^"]+(?=/archives/)' "$UPDATE_XML" | head -1 || true)
    fi
    if [[ -z "$domain" ]]; then
        domain=${DOMAIN_NAME:-$(ip route get 1.1.1.1 | awk '{print $7}' | head -1)}
        print_warning "Не удалось определить домен из XML, используется: $domain"
    fi

    # Извлекаем версию плагина
    print_info "Анализ плагина..."
    version=$(extract_version "$zip_file")
    print_info "Версия плагина: $version"

    # Имя файла архива
    local zip_name="${plugin_id}-${version}.zip"

    # Копируем архив в директорию репозитория
    print_info "Копирование архива..."
    cp "$zip_file" "$ARCHIVES_DIR/$zip_name"
    chown www-data:www-data "$ARCHIVES_DIR/$zip_name"
    chmod 644 "$ARCHIVES_DIR/$zip_name"

    # Создаем резервную копию XML
    backup_xml

    # Добавляем плагин в XML
    add_plugin_to_xml "$plugin_id" "$plugin_name" "$description" "$vendor" "$zip_name" "$domain" "$version"

    # Устанавливаем правильные права для XML
    chown www-data:www-data "$UPDATE_XML"
    chmod 644 "$UPDATE_XML"

    echo
    print_success "Плагин успешно добавлен в репозиторий!"
    echo
    print_info "Детали:"
    echo "  ID: $plugin_id"
    echo "  Название: $plugin_name"
    echo "  Версия: $version"
    echo "  Архив: $zip_name"
    echo "  URL: http://$domain/archives/$zip_name"
    echo
    print_info "Для проверки работоспособности:"
    echo -e "  curl ${YELLOW}http://$domain/updatePlugins.xml${NC}"
}

# Запуск
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi