"""
Главная точка входа в MCP сервер файловой системы.
"""

import sys
import time
from pathlib import Path
from typing import Optional

import click
import uvicorn

from .config import ServerConfig, load_config, get_default_config_path
from .api import create_app, run_server
from . import __version__


@click.group()
@click.version_option(version=__version__)
def cli():
    """Ride Filesystem MCP Server - MCP сервер для доступа к файловой системе."""
    pass


@cli.command()
@click.option('--host', default='127.0.0.1', help='Хост для запуска сервера')
@click.option('--port', default=3001, type=int, help='Порт для запуска сервера')
@click.option('--config', type=click.Path(exists=True), help='Путь к файлу конфигурации')
@click.option('--base-dir', type=click.Path(), help='Базовая директория для файловых операций')
@click.option('--log-level', default='info', help='Уровень логирования (debug, info, warning, error)')
@click.option('--max-file-size', type=int, help='Максимальный размер файла в байтах')
@click.option('--allowed-extensions', help='Разрешенные расширения через запятую')
@click.option('--enable-watch', is_flag=True, help='Включить отслеживание изменений файлов')
def serve(
    host: str,
    port: int,
    config: Optional[str],
    base_dir: Optional[str],
    log_level: str,
    max_file_size: Optional[int],
    allowed_extensions: Optional[str],
    enable_watch: bool
):
    """Запустить MCP сервер."""
    try:
        # Загружаем конфигурацию
        config_path = config or get_default_config_path()
        server_config = load_config(config_path)

        # Применяем параметры командной строки
        if base_dir:
            server_config.base_dir = base_dir
        if max_file_size:
            server_config.max_file_size = max_file_size
        if allowed_extensions:
            server_config.allowed_extensions = [ext.strip() for ext in allowed_extensions.split(',')]
        if enable_watch:
            server_config.enable_file_watch = enable_watch

        # Выводим информацию о запуске
        click.echo(f"🚀 Запуск Ride Filesystem MCP Server v{__version__}")
        click.echo(f"📁 Базовая директория: {server_config.base_dir}")
        click.echo(f"🌐 Сервер доступен по адресу: http://{host}:{port}")
        click.echo(f"📚 Документация API: http://{host}:{port}/docs")
        click.echo(f"🔒 Безопасность: включена")
        click.echo(f"📊 Отслеживание файлов: {'включено' if server_config.enable_file_watch else 'выключено'}")

        if server_config.allowed_extensions:
            click.echo(f"📄 Разрешенные расширения: {', '.join(server_config.allowed_extensions)}")

        click.echo("-" * 50)

        # Запускаем сервер
        run_server(
            host=host,
            port=port,
            config_file=config_path,
            log_level=log_level
        )

    except KeyboardInterrupt:
        click.echo("\n👋 Сервер остановлен")
    except Exception as e:
        click.echo(f"❌ Ошибка запуска сервера: {e}", err=True)
        sys.exit(1)


@cli.command()
@click.option('--config', type=click.Path(exists=True), help='Путь к файлу конфигурации')
def init(config: Optional[str]):
    """Инициализировать конфигурацию по умолчанию."""
    try:
        config_path = config or get_default_config_path()

        # Создаем директорию для конфига
        Path(config_path).parent.mkdir(parents=True, exist_ok=True)

        # Создаем конфигурацию по умолчанию
        server_config = ServerConfig()

        # Сохраняем в TOML
        import toml
        config_data = {
            "server": server_config.dict(exclude_none=True)
        }

        with open(config_path, 'w', encoding='utf-8') as f:
            toml.dump(config_data, f)

        click.echo(f"✅ Конфигурация создана: {config_path}")
        click.echo(f"📁 Базовая директория: {server_config.base_dir}")
        click.echo(f"🔧 Порт: {server_config.port}")
        click.echo(f"📊 Максимальный размер файла: {server_config.max_file_size} байт")

    except Exception as e:
        click.echo(f"❌ Ошибка создания конфигурации: {e}", err=True)
        sys.exit(1)


@cli.command()
@click.option('--config', type=click.Path(exists=True), help='Путь к файлу конфигурации')
def status(config: Optional[str]):
    """Показать статус и текущую конфигурацию."""
    try:
        config_path = config or get_default_config_path()
        server_config = load_config(config_path)

        click.echo(f"📊 Ride Filesystem MCP Server v{__version__}")
        click.echo("-" * 40)
        click.echo(f"📁 Базовая директория: {server_config.base_dir}")
        click.echo(f"🌐 Хост: {server_config.host}")
        click.echo(f"🔌 Порт: {server_config.port}")
        click.echo(f"📊 Максимальный размер файла: {server_config.max_file_size:,} байт")
        click.echo(f"📚 Уровень логирования: {server_config.log_level}")
        click.echo(f"🔍 Отслеживание файлов: {'включено' if server_config.enable_file_watch else 'выключено'}")

        if server_config.allowed_extensions:
            click.echo(f"📄 Разрешенные расширения: {', '.join(server_config.allowed_extensions)}")
        else:
            click.echo("📄 Разрешенные расширения: все")

        click.echo(f"🚫 Заблокированных путей: {len(server_config.blocked_paths)}")

        # Проверяем доступность базовой директории
        base_path = Path(server_config.base_dir)
        if base_path.exists() and base_path.is_dir():
            click.echo(f"✅ Базовая директория доступна")
            try:
                # Считаем файлы
                file_count = len(list(base_path.rglob('*')))
                click.echo(f"📁 Файлов в директории: {file_count}")
            except Exception:
                click.echo(f"⚠️ Не удалось посчитать файлы")
        else:
            click.echo(f"❌ Базовая директория не существует или недоступна")

    except Exception as e:
        click.echo(f"❌ Ошибка получения статуса: {e}", err=True)
        sys.exit(1)


@cli.command()
@click.option('--config', type=click.Path(exists=True), help='Путь к файлу конфигурации')
def validate(config: Optional[str]):
    """Проверить конфигурацию на валидность."""
    try:
        config_path = config or get_default_config_path()
        server_config = load_config(config_path)

        errors = []

        # Проверяем базовую директорию
        base_path = Path(server_config.base_dir)
        if not base_path.exists():
            try:
                base_path.mkdir(parents=True, exist_ok=True)
                click.echo("✅ Базовая директория создана")
            except Exception as e:
                errors.append(f"Не удалось создать базовую директорию: {e}")
        elif not base_path.is_dir():
            errors.append(f"Базовый путь не является директорией: {server_config.base_dir}")

        # Проверяем порт
        if not (1 <= server_config.port <= 65535):
            errors.append(f"Недопустимый порт: {server_config.port}")

        # Проверяем расширения
        for ext in server_config.allowed_extensions:
            if not ext.replace('.', '').isalnum():
                errors.append(f"Недопустимое расширение: {ext}")

        if errors:
            click.echo("❌ Найдены ошибки в конфигурации:")
            for error in errors:
                click.echo(f"  - {error}")
            sys.exit(1)
        else:
            click.echo("✅ Конфигурация валидна")

    except Exception as e:
        click.echo(f"❌ Ошибка валидации конфигурации: {e}", err=True)
        sys.exit(1)


@cli.command()
@click.option('--config', type=click.Path(exists=True), help='Путь к файлу конфигурации')
def test_connection(config: Optional[str]):
    """Тестировать подключение к серверу."""
    try:
        import httpx

        config_path = config or get_default_config_path()
        server_config = load_config(config_path)

        server_url = f"http://{server_config.host}:{server_config.port}"

        with httpx.Client(timeout=5.0) as client:
            response = client.get(f"{server_url}/health")

        if response.status_code == 200:
            data = response.json()
            click.echo("✅ Сервер доступен")
            click.echo(f"📊 Версия: {data.get('version', 'unknown')}")
            click.echo(f"⏱️ Время работы: {data.get('uptime_seconds', 0):.1f} сек")
            click.echo(f"📁 Базовая директория: {data.get('base_dir', 'unknown')}")
        else:
            click.echo(f"❌ Сервер вернул статус: {response.status_code}")

    except httpx.ConnectError:
        click.echo("❌ Не удалось подключиться к серверу. Возможно, он не запущен.")
    except Exception as e:
        click.echo(f"❌ Ошибка проверки подключения: {e}", err=True)


if __name__ == "__main__":
    cli()