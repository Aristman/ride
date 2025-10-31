"""
Конфигурация MCP сервера файловой системы.
"""

import os
import sys
from pathlib import Path
from typing import List, Optional

from pydantic import BaseModel, Field, validator
import toml


class ServerConfig(BaseModel):
    """Конфигурация сервера."""

    host: str = Field(default="127.0.0.1", description="Хост для запуска сервера")
    port: int = Field(default=3001, description="Порт для запуска сервера")
    log_level: str = Field(default="info", description="Уровень логирования")
    base_dir: str = Field(default="./data", description="Базовая директория для файловых операций")
    max_file_size: int = Field(default=10 * 1024 * 1024, description="Максимальный размер файла в байтах")
    allowed_extensions: List[str] = Field(default_factory=list, description="Разрешенные расширения файлов")
    blocked_paths: List[str] = Field(
        default_factory=lambda: [
            "/etc", "/sys", "/proc", "/boot", "/usr/bin", "/bin", "/sbin",
            "C:\\Windows", "C:\\Program Files", "C:\\Program Files (x86)"
        ],
        description="Заблокированные пути"
    )
    enable_file_watch: bool = Field(default=False, description="Включить отслеживание изменений файлов")
    cors_origins: List[str] = Field(default_factory=lambda: ["http://localhost:63342"], description="CORS origins")

    @validator("port")
    def validate_port(cls, v):
        if not 1 <= v <= 65535:
            raise ValueError("Порт должен быть в диапазоне 1-65535")
        return v

    @validator("base_dir")
    def validate_base_dir(cls, v):
        path = Path(v).resolve()
        if not path.exists():
            path.mkdir(parents=True, exist_ok=True)
        elif not path.is_dir():
            raise ValueError(f"base_dir должен быть директорией: {path}")
        return str(path)

    @validator("allowed_extensions")
    def validate_extensions(cls, v):
        if v:
            return [ext.lower().lstrip('.') for ext in v]
        return v


def load_config(config_path: Optional[str] = None) -> ServerConfig:
    """
    Загрузить конфигурацию из файла или переменных окружения.

    Args:
        config_path: Путь к файлу конфигурации TOML

    Returns:
        ServerConfig: Объект конфигурации
    """
    config_data = {}

    # 1. Загрузка из файла конфигурации
    if config_path and os.path.exists(config_path):
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                file_config = toml.load(f)
                if 'server' in file_config:
                    config_data.update(file_config['server'])
        except Exception as e:
            print(f"Предупреждение: не удалось загрузить конфигурацию из {config_path}: {e}")

    # 2. Переменные окружения имеют приоритет
    env_mappings = {
        'RIDE_FS_HOST': 'host',
        'RIDE_FS_PORT': 'port',
        'RIDE_FS_LOG_LEVEL': 'log_level',
        'RIDE_FS_BASE_DIR': 'base_dir',
        'RIDE_FS_MAX_FILE_SIZE': 'max_file_size',
        'RIDE_FS_ALLOWED_EXTENSIONS': 'allowed_extensions',
        'RIDE_FS_BLOCKED_PATHS': 'blocked_paths',
        'RIDE_FS_ENABLE_WATCH': 'enable_file_watch',
        'RIDE_FS_CORS_ORIGINS': 'cors_origins',
    }

    for env_var, config_key in env_mappings.items():
        value = os.getenv(env_var)
        if value:
            if config_key in ['port', 'max_file_size']:
                config_data[config_key] = int(value)
            elif config_key in ['enable_file_watch']:
                config_data[config_key] = value.lower() in ('true', '1', 'yes', 'on')
            elif config_key in ['allowed_extensions', 'blocked_paths', 'cors_origins']:
                config_data[config_key] = [item.strip() for item in value.split(',')]
            else:
                config_data[config_key] = value

    return ServerConfig(**config_data)


def get_default_config_path() -> str:
    """
    Получить путь к файлу конфигурации по умолчанию.

    Returns:
        str: Путь к файлу config.toml
    """
    # Ищем config.toml в текущей директории, затем в ~/.ride/
    current_dir = Path.cwd()
    home_dir = Path.home()

    possible_paths = [
        current_dir / "config.toml",
        current_dir / "ride-fs-config.toml",
        home_dir / ".ride" / "filesystem-server-config.toml",
        home_dir / ".ride" / "config.toml",
    ]

    for path in possible_paths:
        if path.exists():
            return str(path)

    # Если не найдено, используем путь в ~/.ride/
    default_path = home_dir / ".ride" / "filesystem-server-config.toml"
    default_path.parent.mkdir(parents=True, exist_ok=True)
    return str(default_path)