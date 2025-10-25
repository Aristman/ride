#!/bin/bash

# ============================================================================
# Update Plugin Version Script
# Скрипт для обновления версии существующего плагина в репозитории
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

# Проверка аргументов
check_args() {
    if [[ $# -ne 2 ]]; then
        echo "Использование: $0 <plugin-id> <path-to-new-zip>"
        echo
        echo "Пример:"
        echo "  $0 com.my.plugin /tmp/my-plugin-2.0.0.zip"
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

# Извлечение информации из плагина
extract_plugin_info() {
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
    local name
    local description
    local vendor

    version=$(grep -oP '(?<=<version>)[^<]+' "$plugin_xml" 2>/dev/null || echo "1.0.0")
    name=$(grep -oP '(?<=<name>)[^<]+' "$plugin_xml" 2>/dev/null || echo "Unknown Plugin")
    description=$(grep -oP '(?<=<description>)[^<]+' "$plugin_xml" 2>/dev/null || echo "No description")
    vendor=$(grep -oP '(?<=<vendor>)[^<]+' "$plugin_xml" 2>/dev/null || echo "Unknown Vendor")

    rm -rf "$temp_dir"

    echo "$version|$name|$description|$vendor"
}

# Проверка существования плагина в XML
check_plugin_exists() {
    local plugin_id="$1"

    if ! grep -q "id=\"$plugin_id\"" "$UPDATE_XML"; then
        print_error "Плагин с ID '$plugin_id' не найден в репозитории"
        print_info "Используйте add-plugin.sh для добавления нового плагина"
        exit 1
    fi
}

# Создание резервной копии
backup_xml() {
    local backup_file="$BACKUP_DIR/updatePlugins-$(date +%Y%m%d-%H%M%S).xml"
    cp "$UPDATE_XML" "$backup_file"
    print_info "Создана резервная копия: $backup_file"
}

# Обновление записи плагина в XML
update_plugin_in_xml() {
    local plugin_id="$1"
    local new_version="$2"
    local plugin_name="$3"
    local description="$4"
    local vendor="$5"
    local zip_name="$6"
    local domain="$7"

    # Экранируем специальные символы
    local escaped_name="${plugin_name//&/&amp;}"
    local escaped_description="${description//&/&amp;}"
    local escaped_vendor="${vendor//&/&amp;}"

    # Создаем новую XML-запись
    local new_plugin_entry="  <plugin id=\"$plugin_id\"
          url=\"http://$domain/archives/$zip_name\"
          version=\"$new_version\">
    <name>$escaped_name</name>
    <description>$escaped_description</description>
    <vendor>$escaped_vendor</vendor>
    <idea-version since-build=\"211.0\" until-build=\"241.*\"/>
  </plugin>"

    # Находим и заменяем старую запись
    local start_line
    local end_line

    start_line=$(grep -n "id=\"$plugin_id\"" "$UPDATE_XML" | cut -d: -f1)
    end_line=$(sed -n "${start_line},\$p" "$UPDATE_XML" | grep -n "</plugin>" | head -1 | cut -d: -f1)
    end_line=$((start_line + end_line - 1))

    # Заменяем строки
    sed -i "${start_line},${end_line}c\\$new_plugin_entry" "$UPDATE_XML"

    print_success "XML-запись плагина обновлена"
}

# Удаление старых архивов (опционально)
cleanup_old_versions() {
    local plugin_id="$1"
    local current_version="$2"

    print_info "Поиск старых версий плагина..."

    # Находим и удаляем старые архивы
    find "$ARCHIVES_DIR" -name "${plugin_id}-*.zip" ! -name "${plugin_id}-${current_version}.zip" -type f | while read old_zip; do
        print_warning "Удаление старой версии: $(basename "$old_zip")"
        rm -f "$old_zip"
    done
}

# Основная функция
main() {
    check_args "$@"
    check_root

    local plugin_id="$1"
    local zip_file="$2"

    # Валидация
    check_zip_file "$zip_file"

    # Проверяем, что директория репозитория существует
    if [[ ! -d "$REPO_BASE_PATH" ]]; then
        print_error "Директория репозитория не найдена. Сначала запустите deploy-repository.sh"
        exit 1
    fi

    # Проверяем, что плагин существует
    check_plugin_exists "$plugin_id"

    # Определяем домен
    local domain
    domain=$(grep -oP '(?<=url="http://)[^"]+(?=/archives/)' "$UPDATE_XML" | head -1)
    if [[ -z "$domain" ]]; then
        domain=$(ip route get 1.1.1.1 | awk '{print $7}' | head -1)
        print_warning "Не удалось определить домен из XML, используется: $domain"
    fi

    # Извлекаем информацию из нового архива
    print_info "Анализ нового архива..."
    local plugin_info
    plugin_info=$(extract_plugin_info "$zip_file")

    local new_version
    local plugin_name
    local description
    local vendor

    IFS='|' read -r new_version plugin_name description vendor <<< "$plugin_info"

    print_info "Новая версия: $new_version"

    # Проверяем, не добавляем ли мы ту же версию
    local old_version
    old_version=$(grep "id=\"$plugin_id\"" -A1 "$UPDATE_XML" | grep "version=" | grep -oP '(?<=version=")[^"]+')

    if [[ "$old_version" == "$new_version" ]]; then
        print_warning "Плагин уже имеет версию $new_version"
        print_info "Если вы хотите заменить файл, удалите старый архив вручную и запустите скрипт заново"
        exit 1
    fi

    # Имя нового архива
    local zip_name="${plugin_id}-${new_version}.zip"

    # Копируем новый архив
    print_info "Копирование нового архива..."
    cp "$zip_file" "$ARCHIVES_DIR/$zip_name"
    chown www-data:www-data "$ARCHIVES_DIR/$zip_name"
    chmod 644 "$ARCHIVES_DIR/$zip_name"

    # Создаем резервную копию XML
    backup_xml

    # Обновляем запись в XML
    update_plugin_in_xml "$plugin_id" "$new_version" "$plugin_name" "$description" "$vendor" "$zip_name" "$domain"

    # Устанавливаем правильные права для XML
    chown www-data:www-data "$UPDATE_XML"
    chmod 644 "$UPDATE_XML"

    # Удаляем старые версии (опционально - раскомментировать при необходимости)
    # cleanup_old_versions "$plugin_id" "$new_version"

    echo
    print_success "Версия плагина успешно обновлена!"
    echo
    print_info "Детали обновления:"
    echo "  ID: $plugin_id"
    echo "  Старая версия: $old_version"
    echo "  Новая версия: $new_version"
    echo "  Новый архив: $zip_name"
    echo "  URL: http://$domain/archives/$zip_name"
    echo
    print_info "Для проверки работоспособности:"
    echo -e "  curl ${YELLOW}http://$domain/updatePlugins.xml${NC}"
}

# Запуск
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi