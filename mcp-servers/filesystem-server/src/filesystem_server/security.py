"""
Модуль безопасности для файловых операций.
"""

import os
import hashlib
import mimetypes
from pathlib import Path
from typing import List, Optional, Set, Tuple

from .config import ServerConfig


class SecurityManager:
    """Менеджер безопасности для файловых операций."""

    def __init__(self, config: ServerConfig):
        """
        Инициализировать менеджер безопасности.

        Args:
            config: Конфигурация сервера
        """
        self.config = config
        self.base_dir = Path(config.base_dir).resolve()
        self._blocked_paths_set = {Path(p).resolve() for p in config.blocked_paths}
        self._allowed_extensions_set = {ext.lower() for ext in config.allowed_extensions}

    def is_path_safe(self, path: Path) -> bool:
        """
        Проверить безопасность пути.

        Args:
            path: Путь для проверки

        Returns:
            bool: True если путь безопасный
        """
        try:
            # Нормализуем путь
            normalized_path = path.resolve()

            # 1. Проверка на выход за пределы base_dir
            try:
                relative_path = normalized_path.relative_to(self.base_dir)
            except ValueError:
                return False

            # 2. Проверка на запрещенные компоненты пути
            if '..' in path.parts:
                return False

            # 3. Проверка на заблокированные пути
            for blocked_path in self._blocked_paths_set:
                try:
                    if normalized_path.is_relative_to(blocked_path):
                        return False
                except AttributeError:
                    # Для Python < 3.9
                    try:
                        normalized_path.relative_to(blocked_path)
                        return False
                    except ValueError:
                        pass

            # 4. Проверка скрытых файлов (начинаются с .)
            if any(part.startswith('.') for part in relative_path.parts):
                return False

            return True

        except (OSError, ValueError):
            return False

    def validate_file_extension(self, file_path: Path) -> bool:
        """
        Проверить разрешенное расширение файла.

        Args:
            file_path: Путь к файлу

        Returns:
            bool: True если расширение разрешено
        """
        if not self._allowed_extensions_set:
            return True

        extension = file_path.suffix.lower().lstrip('.')
        return extension in self._allowed_extensions_set

    def validate_file_size(self, file_size: int) -> bool:
        """
        Проверить размер файла.

        Args:
            file_size: Размер файла в байтах

        Returns:
            bool: True если размер допустим
        """
        return 0 <= file_size <= self.config.max_file_size

    def get_safe_path(self, relative_path: str) -> Optional[Path]:
        """
        Получить безопасный абсолютный путь.

        Args:
            relative_path: Относительный путь

        Returns:
            Optional[Path]: Безопасный путь или None
        """
        try:
            # Очищаем путь от лишних символов
            clean_path = relative_path.strip().lstrip('/\\')
            safe_path = self.base_dir / clean_path

            if self.is_path_safe(safe_path):
                return safe_path
            return None

        except (OSError, ValueError):
            return None

    def calculate_checksum(self, content: str) -> str:
        """
        Рассчитать SHA256 хеш содержимого.

        Args:
            content: Содержимое для хеширования

        Returns:
            str: SHA256 хеш в hex формате
        """
        return hashlib.sha256(content.encode('utf-8')).hexdigest()

    def calculate_file_checksum(self, file_path: Path) -> Optional[str]:
        """
        Рассчитать SHA256 хеш файла.

        Args:
            file_path: Путь к файлу

        Returns:
            Optional[str]: SHA256 хеш или None при ошибке
        """
        try:
            hash_sha256 = hashlib.sha256()
            with open(file_path, 'rb') as f:
                for chunk in iter(lambda: f.read(4096), b""):
                    hash_sha256.update(chunk)
            return hash_sha256.hexdigest()
        except (OSError, IOError):
            return None

    def get_mime_type(self, file_path: Path) -> Optional[str]:
        """
        Определить MIME тип файла.

        Args:
            file_path: Путь к файлу

        Returns:
            Optional[str]: MIME тип или None
        """
        mime_type, _ = mimetypes.guess_type(str(file_path))
        return mime_type

    def sanitize_filename(self, filename: str) -> str:
        """
        Очистить имя файла от небезопасных символов.

        Args:
            filename: Исходное имя файла

        Returns:
            str: Очищенное имя файла
        """
        # Удаляем небезопасные символы
        dangerous_chars = '<>:"/\\|?*'
        sanitized = ''.join(
            c for c in filename if c not in dangerous_chars and ord(c) >= 32
        )

        # Ограничиваем длину
        if len(sanitized) > 255:
            name, ext = os.path.splitext(sanitized)
            max_name_length = 255 - len(ext)
            sanitized = name[:max_name_length] + ext

        return sanitized.strip()

    def can_read_file(self, file_path: Path) -> bool:
        """
        Проверить возможность чтения файла.

        Args:
            file_path: Путь к файлу

        Returns:
            bool: True если файл можно читать
        """
        try:
            if not self.is_path_safe(file_path):
                return False

            if not file_path.exists():
                return False

            if not file_path.is_file():
                return False

            return os.access(file_path, os.R_OK)

        except (OSError, ValueError):
            return False

    def can_write_file(self, file_path: Path) -> bool:
        """
        Проверить возможность записи в файл.

        Args:
            file_path: Путь к файлу

        Returns:
            bool: True если в файл можно записывать
        """
        try:
            if not self.is_path_safe(file_path):
                return False

            # Если файл существует, проверяем права на запись
            if file_path.exists():
                return (file_path.is_file() and
                        os.access(file_path, os.W_OK))

            # Если файл не существует, проверяем права на запись в директорию
            parent_dir = file_path.parent
            return parent_dir.exists() and os.access(parent_dir, os.W_OK)

        except (OSError, ValueError):
            return False

    def can_delete_file(self, file_path: Path) -> bool:
        """
        Проверить возможность удаления файла.

        Args:
            file_path: Путь к файлу

        Returns:
            bool: True если файл можно удалить
        """
        try:
            if not self.is_path_safe(file_path):
                return False

            if not file_path.exists():
                return False

            return os.access(file_path.parent, os.W_OK)

        except (OSError, ValueError):
            return False

    def validate_path_operation(self, operation: str, path: Path) -> Tuple[bool, str]:
        """
        Валидировать путь для конкретной операции.

        Args:
            operation: Тип операции (read, write, delete, list)
            path: Путь для валидации

        Returns:
            Tuple[bool, str]: (результат валидации, сообщение об ошибке)
        """
        if not self.is_path_safe(path):
            return False, f"Небезопасный путь: {path}"

        # Дополнительные проверки в зависимости от операции
        if operation == "read":
            if not self.can_read_file(path):
                return False, f"Невозможно прочитать файл: {path}"
        elif operation == "write":
            if not self.can_write_file(path):
                return False, f"Невозможно записать в файл: {path}"
            if not self.validate_file_extension(path):
                return False, f"Запрещенное расширение файла: {path.suffix}"
        elif operation == "delete":
            if not self.can_delete_file(path):
                return False, f"Невозможно удалить файл: {path}"

        return True, ""

    def get_security_info(self) -> dict:
        """
        Получить информацию о настройках безопасности.

        Returns:
            dict: Информация о безопасности
        """
        return {
            "base_dir": str(self.base_dir),
            "max_file_size": self.config.max_file_size,
            "allowed_extensions": list(self._allowed_extensions_set),
            "blocked_paths": [str(p) for p in self._blocked_paths_set],
            "has_extension_filter": bool(self._allowed_extensions_set),
            "security_enabled": True
        }