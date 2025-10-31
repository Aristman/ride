#!/bin/bash

# Скрипт для запуска MCP сервера файловой системы

# Получаем директорию проекта
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MCP_SERVER_DIR="$PROJECT_DIR/mcp-servers/filesystem-server"

# Проверяем наличие MCP сервера
if [ ! -d "$MCP_SERVER_DIR" ]; then
    echo "Ошибка: Директория MCP сервера не найдена: $MCP_SERVER_DIR"
    exit 1
fi

# Проверяем наличие Python
if ! command -v python3 &> /dev/null; then
    echo "Ошибка: Python3 не найден. Установите Python3 для запуска MCP сервера."
    exit 1
fi

# Проверяем наличие pip
if ! command -v pip3 &> /dev/null && ! python3 -m pip --version &> /dev/null; then
    echo "Ошибка: pip3 не найден. Установите pip3 для запуска MCP сервера."
    exit 1
fi

# Переходим в директорию MCP сервера
cd "$MCP_SERVER_DIR"

# Проверяем наличие файла зависимостей
if [ ! -f "pyproject.toml" ]; then
    echo "Ошибка: Файл pyproject.toml не найден"
    exit 1
fi

# Устанавливаем зависимости, если нужно
echo "Проверка зависимостей MCP сервера..."
if ! python3 -c "import fastapi, uvicorn, aiofiles" &> /dev/null; then
    echo "Установка зависимостей MCP сервера..."
    python3 -m pip install --user fastapi uvicorn aiofiles pydantic watchdog toml click
    if [ $? -ne 0 ]; then
        echo "Ошибка: Не удалось установить зависимости"
        exit 1
    fi
fi

# Запускаем MCP сервер
echo "Запуск MCP сервера файловой системы..."
echo "Директория проекта: $PROJECT_DIR"
echo "Директория MCP сервера: $MCP_SERVER_DIR"

# Устанавливаем переменные окружения
export RIDE_FS_HOST=127.0.0.1
export RIDE_FS_PORT=3001
export RIDE_FS_BASE_DIR="$PROJECT_DIR"
export RIDE_FS_MAX_FILE_SIZE=10485760
export RIDE_FS_ALLOWED_EXTENSIONS="kt,java,py,js,ts,json,md,txt,xml,yaml,yml,gradle,properties"
export RIDE_FS_LOG_LEVEL=info

# Запускаем сервер
python3 -m filesystem_server.main serve \
    --host 127.0.0.1 \
    --port 3001 \
    --base-dir "$PROJECT_DIR" \
    --log-level info \
    --max-file-size 10485760 \
    --allowed-extensions "kt,java,py,js,ts,json,md,txt,xml,yaml,yml,gradle,properties"