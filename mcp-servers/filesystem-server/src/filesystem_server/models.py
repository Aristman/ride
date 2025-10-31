"""
Модели данных для MCP сервера файловой системы.
"""

import os
from datetime import datetime
from pathlib import Path
from typing import List, Optional, Dict, Any, Union

from pydantic import BaseModel, Field, validator


class FileInfo(BaseModel):
    """Информация о файле."""

    name: str = Field(..., description="Имя файла")
    path: str = Field(..., description="Полный путь к файлу")
    size: int = Field(..., description="Размер файла в байтах")
    modified_at: datetime = Field(..., description="Время последнего изменения")
    created_at: Optional[datetime] = Field(None, description="Время создания")
    is_readonly: bool = Field(False, description="Файл только для чтения")
    is_directory: bool = Field(False, description="Это директория")
    mime_type: Optional[str] = Field(None, description="MIME тип файла")
    checksum: Optional[str] = Field(None, description="SHA256 хеш файла")

    @validator('path')
    def validate_path(cls, v):
        return str(Path(v).as_posix())

    @validator('size')
    def validate_size(cls, v):
        if v < 0:
            raise ValueError("Размер файла не может быть отрицательным")
        return v


class FileContent(BaseModel):
    """Содержимое файла с метаданными."""

    path: str = Field(..., description="Путь к файлу")
    content: str = Field(..., description="Содержимое файла")
    size: int = Field(..., description="Размер содержимого в байтах")
    mime_type: Optional[str] = Field(None, description="MIME тип файла")
    encoding: str = Field(default="utf-8", description="Кодировка файла")
    checksum: Optional[str] = Field(None, description="SHA256 хеш содержимого")

    @validator('size')
    def validate_size(cls, v):
        if v < 0:
            raise ValueError("Размер содержимого не может быть отрицательным")
        return v


class FileCreateRequest(BaseModel):
    """Запрос на создание файла."""

    path: str = Field(..., description="Относительный путь к файлу")
    content: str = Field(default="", description="Содержимое файла")
    overwrite: bool = Field(default=False, description="Разрешить перезапись существующего файла")
    encoding: str = Field(default="utf-8", description="Кодировка файла")

    @validator('path')
    def validate_path(cls, v):
        if not v or v.strip() == "":
            raise ValueError("Путь к файлу не может быть пустым")
        if v.startswith('/') or v.startswith('\\'):
            raise ValueError("Путь должен быть относительным")
        if '..' in v:
            raise ValueError("Путь не должен содержать '..'")
        return v.strip()


class FileUpdateRequest(BaseModel):
    """Запрос на обновление файла."""

    content: str = Field(..., description="Новое содержимое файла")
    encoding: str = Field(default="utf-8", description="Кодировка файла")
    create_if_missing: bool = Field(default=False, description="Создать файл если отсутствует")


class DirectoryCreateRequest(BaseModel):
    """Запрос на создание директории."""

    path: str = Field(..., description="Относительный путь к директории")
    recursive: bool = Field(default=True, description="Создавать родительские директории")

    @validator('path')
    def validate_path(cls, v):
        if not v or v.strip() == "":
            raise ValueError("Путь к директории не может быть пустым")
        if v.startswith('/') or v.startswith('\\'):
            raise ValueError("Путь должен быть относительным")
        if '..' in v:
            raise ValueError("Путь не должен содержать '..'")
        return v.strip()


class FileListResponse(BaseModel):
    """Ответ со списком файлов и директорий."""

    path: str = Field(..., description="Путь к директории")
    files: List[FileInfo] = Field(default_factory=list, description="Список файлов")
    directories: List[FileInfo] = Field(default_factory=list, description="Список поддиректорий")
    total_count: int = Field(..., description="Общее количество элементов")

    @validator('total_count')
    def calculate_total(cls, v, values):
        files = values.get('files', [])
        directories = values.get('directories', [])
        return len(files) + len(directories)


class ApiResponse(BaseModel):
    """Базовый API ответ."""

    success: bool = Field(True, description="Успешность операции")
    message: str = Field(..., description="Сообщение")
    data: Optional[Dict[str, Any]] = Field(None, description="Дополнительные данные")


class ErrorResponse(ApiResponse):
    """Ответ с ошибкой."""

    success: bool = Field(False, description="Операция не удалась")
    error_code: str = Field(..., description="Код ошибки")
    error_details: Optional[Dict[str, Any]] = Field(None, description="Детали ошибки")


class HealthResponse(BaseModel):
    """Ответ health check."""

    status: str = Field("healthy", description="Статус сервера")
    version: str = Field(..., description="Версия сервера")
    uptime_seconds: float = Field(..., description="Время работы в секундах")
    base_dir: str = Field(..., description="Базовая директория")
    max_file_size: int = Field(..., description="Максимальный размер файла")
    allowed_extensions: List[str] = Field(default_factory=list, description="Разрешенные расширения")


class FileOperationResult(BaseModel):
    """Результат файловой операции."""

    path: str = Field(..., description="Путь к файлу")
    operation: str = Field(..., description="Тип операции")
    success: bool = Field(True, description="Успешность операции")
    size: Optional[int] = Field(None, description="Размер файла")
    checksum: Optional[str] = Field(None, description="SHA256 хеш")
    created_at: Optional[datetime] = Field(None, description="Время создания")
    modified_at: Optional[datetime] = Field(None, description="Время изменения")


class WatchEvent(BaseModel):
    """Событие отслеживания файлов."""

    event_type: str = Field(..., description="Тип события (created, modified, deleted, moved)")
    path: str = Field(..., description="Путь к файлу/директории")
    timestamp: datetime = Field(default_factory=datetime.now, description="Время события")
    is_directory: bool = Field(False, description="Это директория")
    old_path: Optional[str] = Field(None, description="Старый путь (для перемещения)")
    file_size: Optional[int] = Field(None, description="Размер файла")


class BatchOperationRequest(BaseModel):
    """Запрос на пакетную операцию."""

    operations: List[Dict[str, Any]] = Field(..., description="Список операций")
    continue_on_error: bool = Field(default=True, description="Продолжать при ошибках")


class BatchOperationResponse(BaseModel):
    """Ответ на пакетную операцию."""

    total_operations: int = Field(..., description="Всего операций")
    successful_operations: int = Field(..., description="Успешных операций")
    failed_operations: int = Field(..., description="Неудачных операций")
    results: List[Dict[str, Any]] = Field(..., description="Результаты операций")
    errors: List[Dict[str, Any]] = Field(default_factory=list, description="Ошибки")