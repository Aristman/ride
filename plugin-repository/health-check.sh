#!/bin/bash

# ============================================================================
# Health Check Script for JetBrains Plugin Repository
# Скрипт для проверки работоспособности репозитория плагинов
# ============================================================================

set -euo pipefail

# Конфигурация
REPO_BASE_PATH="/var/www/plugins"
UPDATE_XML="$REPO_BASE_PATH/updatePlugins.xml"
ARCHIVES_DIR="$REPO_BASE_PATH/archives"

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

# Счетчики для итоговой статистики
TOTAL_CHECKS=0
PASSED_CHECKS=0

# Функция проверки
check() {
    local test_name="$1"
    local command="$2"
    local expected_result="${3:-0}"

    ((TOTAL_CHECKS++))
    echo -n "Проверка: $test_name... "

    if eval "$command" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ ОК${NC}"
        ((PASSED_CHECKS++))
        return 0
    else
        echo -e "${RED}✗ ОШИБКА${NC}"
        return 1
    fi
}

# Функция вывода предупреждения
check_with_warning() {
    local test_name="$1"
    local command="$2"

    ((TOTAL_CHECKS++))
    echo -n "Проверка: $test_name... "

    if eval "$command" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ ОК${NC}"
        ((PASSED_CHECKS++))
        return 0
    else
        echo -e "${YELLOW}⚠ ПРЕДУПРЕЖДЕНИЕ${NC}"
        return 1
    fi
}

# Проверка системных сервисов
check_services() {
    print_info "Проверка системных сервисов..."

    check "Nginx запущен" "systemctl is-active nginx"
    check "Nginx добавлен в автозапуск" "systemctl is-enabled nginx"
    check "Nginx конфигурация корректна" "nginx -t"
}

# Проверка файловой структуры
check_file_structure() {
    print_info "Проверка файловой структуры..."

    check "Директория репозитория существует" "test -d '$REPO_BASE_PATH'"
    check "updatePlugins.xml существует" "test -f '$UPDATE_XML'"
    check "Директория archives существует" "test -d '$ARCHIVES_DIR'"
    check "Директория backups существует" "test -d '$REPO_BASE_PATH/backups'"
}

# Проверка прав доступа
check_permissions() {
    print_info "Проверка прав доступа..."

    check "Права на директорию репозитория" "test '!' -w '$REPO_BASE_PATH' -o '$EUID' -eq '0'"
    check "Владелец директории - www-data" "test '$(stat -c '%U' '$REPO_BASE_PATH')' = 'www-data'"
    check "Права на updatePlugins.xml" "test -r '$UPDATE_XML'"
    check "Права на archives" "test -r '$ARCHIVES_DIR'"
}

# Проверка XML валидности
check_xml_validity() {
    print_info "Проверка XML валидности..."

    if command -v xmllint >/dev/null 2>&1; then
        check "XML файл валиден" "xmllint --noout '$UPDATE_XML'"
    else
        check_with_warning "Установлен xmllint для проверки XML" "false"
        print_warning "Установите libxml2-utils для полной проверки XML: apt install libxml2-utils"
    fi

    # Базовая проверка структуры XML
    check "XML содержит открывающий тег plugins" "grep -q '<plugins' '$UPDATE_XML'"
    check "XML содержит закрывающий тег plugins" "grep -q '</plugins>' '$UPDATE_XML'"
}

# Проверка плагинов
check_plugins() {
    print_info "Проверка плагинов в репозитории..."

    local plugin_count
    plugin_count=$(grep -c '<plugin' "$UPDATE_XML" 2>/dev/null || echo "0")

    if [[ $plugin_count -gt 0 ]]; then
        print_success "Найдено плагинов: $plugin_count"

        # Проверяем каждый плагин
        while IFS= read -r line; do
            if [[ $line =~ id=\"([^\"]+)\" ]]; then
                local plugin_id="${BASH_REMATCH[1]}"
                local plugin_url
                plugin_url=$(echo "$line" | grep -oP 'url="[^"]+')

                if [[ $plugin_url =~ http://([^/]+)/archives/([^"]+) ]]; then
                    local domain="${BASH_REMATCH[1]}"
                    local filename="${BASH_REMATCH[2]}"

                    check "Архив плагина $plugin_id существует" "test -f '$ARCHIVES_DIR/$filename'"

                    # Проверяем доступность через HTTP
                    if command -v curl >/dev/null 2>&1; then
                        check_with_warning "Плагин $plugin_id доступен по HTTP" "curl -s -f 'http://$domain/archives/$filename' >/dev/null"
                    fi
                fi
            fi
        done < <(grep '<plugin' "$UPDATE_XML")
    else
        print_warning "Плагины не найдены в репозитории"
    fi
}

# Проверка сетевой доступности
check_network() {
    print_info "Проверка сетевой доступности..."

    # Определяем домен из XML
    local domain
    domain=$(grep -oP '(?<=url="http://)[^"]+(?=/archives/)' "$UPDATE_XML" | head -1)

    if [[ -n "$domain" ]]; then
        check "Домен определен: $domain" "true"

        # Проверяем доступность порта 80
        if command -v netstat >/dev/null 2>&1; then
            check "Порт 80 открыт" "netstat -ln | grep -E ':80\s'"
        elif command -v ss >/dev/null 2>&1; then
            check "Порт 80 открыт" "ss -ln | grep -E ':80\s'"
        fi

        # Проверяем доступность XML через HTTP
        if command -v curl >/dev/null 2>&1; then
            check "updatePlugins.xml доступен по HTTP" "curl -s -f 'http://$domain/updatePlugins.xml' >/dev/null"
            check "Содержимое XML загружается" "curl -s 'http://$domain/updatePlugins.xml' | grep -q '<plugins>'"
        else
            check_with_warning "Установлен curl для проверки HTTP" "false"
        fi
    else
        print_warning "Не удалось определить домен из XML"
    fi
}

# Проверка дискового пространства
check_disk_space() {
    print_info "Проверка дискового пространства..."

    local disk_usage
    disk_usage=$(df "$REPO_BASE_PATH" | awk 'NR==2 {print $5}' | sed 's/%//')

    if [[ $disk_usage -lt 90 ]]; then
        check "Дисковое пространство в норме (${disk_usage}%)" "true"
    elif [[ $disk_usage -lt 95 ]]; then
        check_with_warning "Дисковое пространство критичное (${disk_usage}%)" "true"
    else
        check "Дисковое пространство критическое (${disk_usage}%)" "false"
    fi
}

# Показать статистику
show_stats() {
    echo
    echo "==============================================================================="
    print_info "ИТОГИ ПРОВЕРКИ"
    echo "==============================================================================="
    echo
    echo "Всего проверок: $TOTAL_CHECKS"
    echo "Успешно: $PASSED_CHECKS"
    echo "Неудачно: $((TOTAL_CHECKS - PASSED_CHECKS))"
    echo

    local success_rate=0
    if [[ $TOTAL_CHECKS -gt 0 ]]; then
        success_rate=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))
    fi

    if [[ $success_rate -eq 100 ]]; then
        print_success "Все проверки пройдены! Репозиторий работает корректно."
    elif [[ $success_rate -ge 80 ]]; then
        print_warning "Большинство проверок пройдены (${success_rate}%), но есть проблемы."
    else
        print_error "Множество проблем обнаружено (${success_rate}%). Рекомендуется проверить репозиторий."
    fi
}

# Показать рекомендации
show_recommendations() {
    echo
    print_info "РЕКОМЕНДАЦИИ ПО УЛУЧШЕНИЮ:"
    echo

    # Проверяем наличие инструментов
    if ! command -v xmllint >/dev/null 2>&1; then
        echo "  • Установите libxml2-utils для проверки XML:"
        echo "    sudo apt install libxml2-utils"
    fi

    if ! command -v curl >/dev/null 2>&1; then
        echo "  • Установите curl для проверки HTTP доступности:"
        echo "    sudo apt install curl"
    fi

    # Проверяем бэкапы
    local backup_count
    backup_count=$(find "$REPO_BASE_PATH/backups" -name "*.xml" 2>/dev/null | wc -l)

    if [[ $backup_count -eq 0 ]]; then
        echo "  • Настройте регулярное резервное копирование updatePlugins.xml"
    else
        echo "  ✓ Резервные копии: $backup_count"
    fi

    echo
    print_info "ПОЛЕЗНЫЕ КОМАНДЫ:"
    echo "  • Проверить логи nginx:     tail -f /var/log/nginx/plugin-repository*.log"
    echo "  • Тестировать XML:          curl http://\$(hostname -I | awk '{print \$1}')/updatePlugins.xml"
    echo "  • Проверить плагины:        ls -la $ARCHIVES_DIR/"
    echo "  • Вручную проверить права:  ls -la $REPO_BASE_PATH/"
}

# Основная функция
main() {
    echo "==============================================================================="
    print_info "ПРОВЕРКА РАБОТОСПОСОБНОСТИ РЕПОЗИТОРИЯ ПЛАГИНОВ JETBRAINS"
    echo "==============================================================================="
    echo

    # Проверяем, что директория существует
    if [[ ! -d "$REPO_BASE_PATH" ]]; then
        print_error "Директория репозитория не найдена: $REPO_BASE_PATH"
        print_info "Сначала запустите deploy-repository.sh"
        exit 1
    fi

    # Выполняем все проверки
    check_services
    check_file_structure
    check_permissions
    check_xml_validity
    check_plugins
    check_network
    check_disk_space

    # Показываем статистику и рекомендации
    show_stats
    show_recommendations
}

# Запуск
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi