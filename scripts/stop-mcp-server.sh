#!/bin/bash

# Скрипт для остановки MCP сервера файловой системы

# Ищем процессы MCP сервера
echo "Поиск процессов MCP сервера..."

# Ищем процессы по имени или порту
MCP_PIDS=$(lsof -ti:3001 2>/dev/null)

if [ -z "$MCP_PIDS" ]; then
    # Альтернативный поиск по имени процесса
    MCP_PIDS=$(pgrep -f "filesystem_server.main" 2>/dev/null)
fi

if [ -z "$MCP_PIDS" ]; then
    # Еще один вариант поиска
    MCP_PIDS=$(pgrep -f "ride-filesystem-server" 2>/dev/null)
fi

if [ -z "$MCP_PIDS" ]; then
    echo "MCP сервер не найден или уже остановлен"
    exit 0
fi

echo "Найдены процессы MCP сервера: $MCP_PIDS"

# Останавливаем процессы
for pid in $MCP_PIDS; do
    echo "Остановка процесса $pid..."
    kill -TERM "$pid" 2>/dev/null

    # Ждем немного
    sleep 2

    # Проверяем, что процесс остановился
    if kill -0 "$pid" 2>/dev/null; then
        echo "Принудительная остановка процесса $pid..."
        kill -KILL "$pid" 2>/dev/null
    fi
done

echo "MCP сервер остановлен"