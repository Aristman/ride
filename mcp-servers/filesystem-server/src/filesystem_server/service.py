"""
Сервис файловых операций.
"""

import os
import shutil
import asyncio
from datetime import datetime
from pathlib import Path
from typing import List, Optional, Dict, Any, Tuple

import aiofiles
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

from .config import ServerConfig
from .models import (
    FileInfo, FileContent, FileOperationResult, WatchEvent,
    FileListResponse, ApiResponse
)
from .security import SecurityManager


class FileSystemEventHandler(FileSystemEventHandler):
    """Обработчик событий файловой системы."""

    def __init__(self, server):
        self.server = server
        self.base_dir = Path(server.config.base_dir).resolve()

    def _should_process_event(self, event):
        """Проверить, нужно ли обрабатывать событие."""
        path = Path(event.src_path).resolve()
        try:
            path.relative_to(self.base_dir)
            return not any(part.startswith('.') for part in path.parts)
        except ValueError:
            return False

    def on_created(self, event):
        """Обработчик создания файла/директории."""
        if self._should_process_event(event):
            watch_event = WatchEvent(
                event_type="created",
                path=event.src_path,
                is_directory=event.is_directory
            )
            asyncio.create_task(self.server._notify_watch_event(watch_event))

    def on_modified(self, event):
        """Обработчик изменения файла."""
        if self._should_process_event(event) and not event.is_directory:
            file_size = os.path.getsize(event.src_path) if os.path.exists(event.src_path) else None
            watch_event = WatchEvent(
                event_type="modified",
                path=event.src_path,
                is_directory=False,
                file_size=file_size
            )
            asyncio.create_task(self.server._notify_watch_event(watch_event))

    def on_deleted(self, event):
        """Обработчик удаления файла/директории."""
        if self._should_process_event(event):
            watch_event = WatchEvent(
                event_type="deleted",
                path=event.src_path,
                is_directory=event.is_directory
            )
            asyncio.create_task(self.server._notify_watch_event(watch_event))

    def on_moved(self, event):
        """Обработчик перемещения файла/директории."""
        if self._should_process_event(event):
            watch_event = WatchEvent(
                event_type="moved",
                path=event.dest_path,
                is_directory=event.is_directory,
                old_path=event.src_path
            )
            asyncio.create_task(self.server._notify_watch_event(watch_event))


class FileSystemService:
    """Сервис для работы с файловой системой."""

    def __init__(self, config: ServerConfig):
        """
        Инициализировать сервис.

        Args:
            config: Конфигурация сервера
        """
        self.config = config
        self.security = SecurityManager(config)
        self.base_dir = Path(config.base_dir).resolve()
        self._watch_subscribers: List[asyncio.Queue] = []
        self._observer: Optional[Observer] = None

        if config.enable_file_watch:
            self._start_file_watcher()

    def _start_file_watcher(self):
        """Запустить отслеживание изменений файлов."""
        try:
            event_handler = FileSystemEventHandler(self)
            self._observer = Observer()
            self._observer.schedule(event_handler, str(self.base_dir), recursive=True)
            self._observer.start()
        except Exception as e:
            print(f"Не удалось запустить отслеживание файлов: {e}")

    async def _notify_watch_event(self, event: WatchEvent):
        """Уведомить подписчиков о событии файла."""
        for queue in self._watch_subscribers:
            try:
                await queue.put(event)
            except Exception:
                pass

    def subscribe_to_watch_events(self) -> asyncio.Queue:
        """Подписаться на события отслеживания файлов."""
        queue = asyncio.Queue(maxsize=1000)
        self._watch_subscribers.append(queue)
        return queue

    def unsubscribe_from_watch_events(self, queue: asyncio.Queue):
        """Отписаться от событий отслеживания файлов."""
        if queue in self._watch_subscribers:
            self._watch_subscribers.remove(queue)

    async def create_file(self, request: 'FileCreateRequest') -> FileOperationResult:
        """
        Создать файл.

        Args:
            request: Запрос на создание файла

        Returns:
            FileOperationResult: Результат операции
        """
        file_path = self.security.get_safe_path(request.path)
        if not file_path:
            return FileOperationResult(
                path=request.path,
                operation="create",
                success=False,
                message="Небезопасный путь"
            )

        # Проверяем расширение
        if not self.security.validate_file_extension(file_path):
            return FileOperationResult(
                path=request.path,
                operation="create",
                success=False,
                message=f"Запрещенное расширение файла: {file_path.suffix}"
            )

        try:
            # Проверяем размер содержимого
            content_size = len(request.content.encode(request.encoding))
            if not self.security.validate_file_size(content_size):
                return FileOperationResult(
                    path=request.path,
                    operation="create",
                    success=False,
                    message=f"Превышен максимальный размер файла: {self.config.max_file_size} байт"
                )

            # Проверяем существование файла
            if file_path.exists() and not request.overwrite:
                return FileOperationResult(
                    path=request.path,
                    operation="create",
                    success=False,
                    message="Файл уже существует"
                )

            # Создаем директорию если нужно
            file_path.parent.mkdir(parents=True, exist_ok=True)

            # Записываем файл
            async with aiofiles.open(file_path, 'w', encoding=request.encoding) as f:
                await f.write(request.content)

            # Получаем информацию о файле
            stat = file_path.stat()
            checksum = self.security.calculate_checksum(request.content)

            return FileOperationResult(
                path=str(file_path.relative_to(self.base_dir)),
                operation="create",
                success=True,
                size=content_size,
                checksum=checksum,
                created_at=datetime.fromtimestamp(stat.st_ctime),
                modified_at=datetime.fromtimestamp(stat.st_mtime)
            )

        except Exception as e:
            return FileOperationResult(
                path=request.path,
                operation="create",
                success=False,
                message=f"Ошибка создания файла: {str(e)}"
            )

    async def read_file(self, relative_path: str) -> FileContent:
        """
        Прочитать файл.

        Args:
            relative_path: Относительный путь к файлу

        Returns:
            FileContent: Содержимое файла с метаданными
        """
        file_path = self.security.get_safe_path(relative_path)
        if not file_path:
            raise ValueError(f"Небезопасный путь: {relative_path}")

        if not self.security.can_read_file(file_path):
            raise PermissionError(f"Нет доступа к файлу: {relative_path}")

        try:
            async with aiofiles.open(file_path, 'r', encoding='utf-8') as f:
                content = await f.read()

            file_size = len(content.encode('utf-8'))
            if not self.security.validate_file_size(file_size):
                raise ValueError(f"Превышен максимальный размер файла: {self.config.max_file_size}")

            checksum = self.security.calculate_checksum(content)
            mime_type = self.security.get_mime_type(file_path)

            return FileContent(
                path=str(file_path.relative_to(self.base_dir)),
                content=content,
                size=file_size,
                mime_type=mime_type,
                encoding="utf-8",
                checksum=checksum
            )

        except Exception as e:
            raise IOError(f"Ошибка чтения файла {relative_path}: {str(e)}")

    async def update_file(self, relative_path: str, request: 'FileUpdateRequest') -> FileOperationResult:
        """
        Обновить файл.

        Args:
            relative_path: Относительный путь к файлу
            request: Запрос на обновление

        Returns:
            FileOperationResult: Результат операции
        """
        file_path = self.security.get_safe_path(relative_path)
        if not file_path:
            return FileOperationResult(
                path=relative_path,
                operation="update",
                success=False,
                message="Небезопасный путь"
            )

        # Проверяем расширение
        if not self.security.validate_file_extension(file_path):
            return FileOperationResult(
                path=relative_path,
                operation="update",
                success=False,
                message=f"Запрещенное расширение файла: {file_path.suffix}"
            )

        try:
            # Если файла нет и разрешено создание
            if not file_path.exists():
                if not request.create_if_missing:
                    return FileOperationResult(
                        path=relative_path,
                        operation="update",
                        success=False,
                        message="Файл не найден"
                    )
                # Создаем директорию если нужно
                file_path.parent.mkdir(parents=True, exist_ok=True)

            # Проверяем размер содержимого
            content_size = len(request.content.encode(request.encoding))
            if not self.security.validate_file_size(content_size):
                return FileOperationResult(
                    path=relative_path,
                    operation="update",
                    success=False,
                    message=f"Превышен максимальный размер файла: {self.config.max_file_size} байт"
                )

            # Записываем файл
            async with aiofiles.open(file_path, 'w', encoding=request.encoding) as f:
                await f.write(request.content)

            # Получаем информацию о файле
            stat = file_path.stat()
            checksum = self.security.calculate_checksum(request.content)

            return FileOperationResult(
                path=str(file_path.relative_to(self.base_dir)),
                operation="update",
                success=True,
                size=content_size,
                checksum=checksum,
                modified_at=datetime.fromtimestamp(stat.st_mtime)
            )

        except Exception as e:
            return FileOperationResult(
                path=relative_path,
                operation="update",
                success=False,
                message=f"Ошибка обновления файла: {str(e)}"
            )

    async def delete_file(self, relative_path: str) -> FileOperationResult:
        """
        Удалить файл.

        Args:
            relative_path: Относительный путь к файлу

        Returns:
            FileOperationResult: Результат операции
        """
        file_path = self.security.get_safe_path(relative_path)
        if not file_path:
            return FileOperationResult(
                path=relative_path,
                operation="delete",
                success=False,
                message="Небезопасный путь"
            )

        if not self.security.can_delete_file(file_path):
            return FileOperationResult(
                path=relative_path,
                operation="delete",
                success=False,
                message="Нет прав на удаление файла"
            )

        try:
            if file_path.is_file():
                file_path.unlink()
            elif file_path.is_dir():
                shutil.rmtree(file_path)
            else:
                return FileOperationResult(
                    path=relative_path,
                    operation="delete",
                    success=False,
                    message="Файл или директория не найдены"
                )

            return FileOperationResult(
                path=str(file_path.relative_to(self.base_dir)),
                operation="delete",
                success=True
            )

        except Exception as e:
            return FileOperationResult(
                path=relative_path,
                operation="delete",
                success=False,
                message=f"Ошибка удаления: {str(e)}"
            )

    async def list_directory(self, relative_path: str = "") -> FileListResponse:
        """
        Получить список файлов и директорий.

        Args:
            relative_path: Относительный путь к директории

        Returns:
            FileListResponse: Список файлов и директорий
        """
        dir_path = self.security.get_safe_path(relative_path or ".")
        if not dir_path:
            raise ValueError(f"Небезопасный путь: {relative_path}")

        if not dir_path.exists():
            raise FileNotFoundError(f"Директория не найдена: {relative_path}")

        if not dir_path.is_dir():
            raise ValueError(f"Путь не является директорией: {relative_path}")

        try:
            files = []
            directories = []

            for item in dir_path.iterdir():
                if item.name.startswith('.'):
                    continue  # Пропускаем скрытые файлы

                relative_item_path = item.relative_to(self.base_dir)
                stat = item.stat()
                is_directory = item.is_dir()
                mime_type = None if is_directory else self.security.get_mime_type(item)

                file_info = FileInfo(
                    name=item.name,
                    path=str(relative_item_path),
                    size=0 if is_directory else stat.st_size,
                    modified_at=datetime.fromtimestamp(stat.st_mtime),
                    created_at=datetime.fromtimestamp(stat.st_ctime),
                    is_readonly=not os.access(item, os.W_OK),
                    is_directory=is_directory,
                    mime_type=mime_type,
                    checksum=None if is_directory else self.security.calculate_file_checksum(item)
                )

                if is_directory:
                    directories.append(file_info)
                else:
                    files.append(file_info)

            # Сортируем по имени
            files.sort(key=lambda x: x.name.lower())
            directories.sort(key=lambda x: x.name.lower())

            return FileListResponse(
                path=str(dir_path.relative_to(self.base_dir)),
                files=files,
                directories=directories,
                total_count=len(files) + len(directories)
            )

        except Exception as e:
            raise IOError(f"Ошибка чтения директории {relative_path}: {str(e)}")

    async def create_directory(self, request: 'DirectoryCreateRequest') -> FileOperationResult:
        """
        Создать директорию.

        Args:
            request: Запрос на создание директории

        Returns:
            FileOperationResult: Результат операции
        """
        dir_path = self.security.get_safe_path(request.path)
        if not dir_path:
            return FileOperationResult(
                path=request.path,
                operation="create_directory",
                success=False,
                message="Небезопасный путь"
            )

        try:
            dir_path.mkdir(parents=request.recursive, exist_ok=False)
            stat = dir_path.stat()

            return FileOperationResult(
                path=str(dir_path.relative_to(self.base_dir)),
                operation="create_directory",
                success=True,
                created_at=datetime.fromtimestamp(stat.st_ctime),
                modified_at=datetime.fromtimestamp(stat.st_mtime)
            )

        except FileExistsError:
            return FileOperationResult(
                path=request.path,
                operation="create_directory",
                success=False,
                message="Директория уже существует"
            )
        except Exception as e:
            return FileOperationResult(
                path=request.path,
                operation="create_directory",
                success=False,
                message=f"Ошибка создания директории: {str(e)}"
            )

    def get_file_info(self, relative_path: str) -> Optional[FileInfo]:
        """
        Получить информацию о файле.

        Args:
            relative_path: Относительный путь к файлу

        Returns:
            Optional[FileInfo]: Информация о файле или None
        """
        file_path = self.security.get_safe_path(relative_path)
        if not file_path or not file_path.exists():
            return None

        try:
            stat = file_path.stat()
            is_directory = file_path.is_dir()
            mime_type = None if is_directory else self.security.get_mime_type(file_path)

            return FileInfo(
                name=file_path.name,
                path=str(file_path.relative_to(self.base_dir)),
                size=0 if is_directory else stat.st_size,
                modified_at=datetime.fromtimestamp(stat.st_mtime),
                created_at=datetime.fromtimestamp(stat.st_ctime),
                is_readonly=not os.access(file_path, os.W_OK),
                is_directory=is_directory,
                mime_type=mime_type,
                checksum=None if is_directory else self.security.calculate_file_checksum(file_path)
            )
        except Exception:
            return None

    def cleanup(self):
        """Очистить ресурсы."""
        if self._observer:
            self._observer.stop()
            self._observer.join()
        self._watch_subscribers.clear()