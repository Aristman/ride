"""
API эндпоинты MCP сервера файловой системы.
"""

import asyncio
from datetime import datetime
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Depends, Query, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
import uvicorn

from .config import ServerConfig, load_config, get_default_config_path
from .models import (
    FileCreateRequest, FileUpdateRequest, DirectoryCreateRequest,
    FileContent, FileListResponse, ApiResponse, ErrorResponse,
    HealthResponse, WatchEvent, BatchOperationRequest,
    BatchOperationResponse
)
from .service import FileSystemService
from . import __version__


app = FastAPI(
    title="Ride Filesystem MCP Server",
    description="MCP сервер для безопасного доступа к локальной файловой системе",
    version=__version__,
    docs_url="/docs",
    redoc_url="/redoc"
)

# Глобальные переменные
config: ServerConfig = None
file_service: FileSystemService = None


def get_file_service() -> FileSystemService:
    """Получить экземпляр файлового сервиса."""
    global file_service
    if file_service is None:
        raise HTTPException(status_code=500, detail="Сервис не инициализирован")
    return file_service


@app.on_event("startup")
async def startup_event():
    """Инициализация при запуске сервера."""
    global config, file_service

    config_path = get_default_config_path()
    config = load_config(config_path)
    file_service = FileSystemService(config)


@app.on_event("shutdown")
async def shutdown_event():
    """Очистка при остановке сервера."""
    global file_service
    if file_service:
        file_service.cleanup()


# CORS Middleware
@app.middleware("http")
async def add_cors_headers(request, call_next):
    """Добавить CORS заголовки."""
    response = await call_next(request)

    origin = request.headers.get("origin")
    if origin in config.cors_origins or "*" in config.cors_origins:
        response.headers["Access-Control-Allow-Origin"] = origin
        response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
        response.headers["Access-Control-Allow-Headers"] = "Content-Type, Authorization"
        response.headers["Access-Control-Allow-Credentials"] = "true"

    return response


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """
    Проверить состояние сервера.

    Returns:
        HealthResponse: Информация о состоянии сервера
    """
    uptime = asyncio.get_event_loop().time() if hasattr(asyncio.get_event_loop(), 'time') else 0

    return HealthResponse(
        version=__version__,
        uptime_seconds=uptime,
        base_dir=config.base_dir,
        max_file_size=config.max_file_size,
        allowed_extensions=config.allowed_extensions
    )


@app.post("/files", response_model=dict)
async def create_file(
    request: FileCreateRequest,
    service: FileSystemService = Depends(get_file_service)
):
    """
    Создать новый файл.

    Args:
        request: Данные для создания файла
        service: Файловый сервис

    Returns:
        dict: Результат операции
    """
    result = await service.create_file(request)

    if result.success:
        return {
            "success": True,
            "message": f"Файл '{request.path}' успешно создан",
            "data": result.dict(exclude_none=True)
        }
    else:
        raise HTTPException(status_code=400, detail=result.message)


@app.get("/files/{path:path}", response_model=FileContent)
async def read_file(
    path: str,
    service: FileSystemService = Depends(get_file_service)
):
    """
    Прочитать содержимое файла.

    Args:
        path: Относительный путь к файлу
        service: Файловый сервис

    Returns:
        FileContent: Содержимое файла с метаданными
    """
    try:
        return await service.read_file(path)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except PermissionError as e:
        raise HTTPException(status_code=403, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except IOError as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.put("/files/{path:path}", response_model=dict)
async def update_file(
    path: str,
    request: FileUpdateRequest,
    service: FileSystemService = Depends(get_file_service)
):
    """
    Обновить содержимое файла.

    Args:
        path: Относительный путь к файлу
        request: Данные для обновления
        service: Файловый сервис

    Returns:
        dict: Результат операции
    """
    result = await service.update_file(path, request)

    if result.success:
        return {
            "success": True,
            "message": f"Файл '{path}' успешно обновлен",
            "data": result.dict(exclude_none=True)
        }
    else:
        raise HTTPException(status_code=400, detail=result.message)


@app.delete("/files/{path:path}", response_model=dict)
async def delete_file(
    path: str,
    service: FileSystemService = Depends(get_file_service)
):
    """
    Удалить файл или директорию.

    Args:
        path: Относительный путь к файлу/директории
        service: Файловый сервис

    Returns:
        dict: Результат операции
    """
    result = await service.delete_file(path)

    if result.success:
        return {
            "success": True,
            "message": f"Файл '{path}' успешно удален",
            "data": result.dict(exclude_none=True)
        }
    else:
        raise HTTPException(status_code=400, detail=result.message)


@app.get("/files", response_model=FileListResponse)
async def list_files(
    dir: str = Query(default="", description="Относительный путь к директории"),
    service: FileSystemService = Depends(get_file_service)
):
    """
    Получить список файлов и директорий.

    Args:
        dir: Относительный путь к директории
        service: Файловый сервис

    Returns:
        FileListResponse: Список файлов и директорий
    """
    try:
        return await service.list_directory(dir)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except IOError as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/files/{path:path}/info")
async def get_file_info(
    path: str,
    service: FileSystemService = Depends(get_file_service)
):
    """
    Получить информацию о файле.

    Args:
        path: Относительный путь к файлу
        service: Файловый сервис

    Returns:
        dict: Информация о файле
    """
    file_info = service.get_file_info(path)

    if file_info:
        return file_info.dict(exclude_none=True)
    else:
        raise HTTPException(status_code=404, detail=f"Файл не найден: {path}")


@app.post("/directories", response_model=dict)
async def create_directory(
    request: DirectoryCreateRequest,
    service: FileSystemService = Depends(get_file_service)
):
    """
    Создать новую директорию.

    Args:
        request: Данные для создания директории
        service: Файловый сервис

    Returns:
        dict: Результат операции
    """
    result = await service.create_directory(request)

    if result.success:
        return {
            "success": True,
            "message": f"Директория '{request.path}' успешно создана",
            "data": result.dict(exclude_none=True)
        }
    else:
        raise HTTPException(status_code=400, detail=result.message)


@app.delete("/directories/{path:path}", response_model=dict)
async def delete_directory(
    path: str,
    service: FileSystemService = Depends(get_file_service)
):
    """
    Удалить директорию.

    Args:
        path: Относительный путь к директории
        service: Файловый сервис

    Returns:
        dict: Результат операции
    """
    result = await service.delete_file(path)

    if result.success:
        return {
            "success": True,
            "message": f"Директория '{path}' успешно удалена",
            "data": result.dict(exclude_none=True)
        }
    else:
        raise HTTPException(status_code=400, detail=result.message)


@app.post("/batch", response_model=BatchOperationResponse)
async def batch_operations(
    request: BatchOperationRequest,
    background_tasks: BackgroundTasks,
    service: FileSystemService = Depends(get_file_service)
):
    """
    Выполнить пакетные операции.

    Args:
        request: Запрос с операциями
        background_tasks: Фоновые задачи
        service: Файловый сервис

    Returns:
        BatchOperationResponse: Результаты операций
    """
    results = []
    errors = []
    successful = 0

    for operation in request.operations:
        op_type = operation.get("type")
        op_path = operation.get("path", "")

        try:
            if op_type == "create_file":
                create_request = FileCreateRequest(**operation.get("data", {}))
                result = await service.create_file(create_request)
            elif op_type == "update_file":
                update_request = FileUpdateRequest(**operation.get("data", {}))
                result = await service.update_file(op_path, update_request)
            elif op_type == "delete":
                result = await service.delete_file(op_path)
            elif op_type == "create_directory":
                dir_request = DirectoryCreateRequest(**operation.get("data", {}))
                result = await service.create_directory(dir_request)
            else:
                raise ValueError(f"Неизвестный тип операции: {op_type}")

            results.append({
                "path": op_path,
                "type": op_type,
                "success": result.success,
                "message": result.message if hasattr(result, 'message') else ""
            })

            if result.success:
                successful += 1
            else:
                errors.append({
                    "path": op_path,
                    "type": op_type,
                    "error": result.message if hasattr(result, 'message') else "Unknown error"
                })
                if not request.continue_on_error:
                    break

        except Exception as e:
            error_info = {
                "path": op_path,
                "type": op_type,
                "error": str(e)
            }
            errors.append(error_info)
            results.append({
                "path": op_path,
                "type": op_type,
                "success": False,
                "message": str(e)
            })
            if not request.continue_on_error:
                break

    return BatchOperationResponse(
        total_operations=len(request.operations),
        successful_operations=successful,
        failed_operations=len(request.operations) - successful,
        results=results,
        errors=errors
    )


@app.get("/watch/events")
async def watch_events():
    """
    Подписаться на события отслеживания файлов.

    Returns:
        StreamingResponse: Поток событий в формате Server-Sent Events
    """
    if not config.enable_file_watch:
        raise HTTPException(status_code=503, detail="Отслеживание файлов отключено")

    service = get_file_service()
    queue = service.subscribe_to_watch_events()

    async def event_stream():
        try:
            while True:
                event = await queue.get()
                if event is None:  # Сигнал завершения
                    break

                # Формируем SSE сообщение
                event_data = event.json()
                yield f"data: {event_data}\n\n"

        finally:
            service.unsubscribe_from_watch_events(queue)

    return StreamingResponse(
        event_stream(),
        media_type="text/plain",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "Content-Type": "text/event-stream"
        }
    )


@app.get("/config/security")
async def get_security_info():
    """
    Получить информацию о настройках безопасности.

    Returns:
        dict: Информация о безопасности
    """
    return file_service.security.get_security_info()


@app.exception_handler(HTTPException)
async def http_exception_handler(request, exc):
    """Обработчик HTTP исключений."""
    return ErrorResponse(
        message=exc.detail,
        error_code=str(exc.status_code),
        error_details={"path": str(request.url)}
    ).dict()


@app.exception_handler(Exception)
async def general_exception_handler(request, exc):
    """Обработчик общих исключений."""
    return ErrorResponse(
        message="Внутренняя ошибка сервера",
        error_code="500",
        error_details={"error": str(exc)}
    ).dict()


def create_app(config_file: Optional[str] = None) -> FastAPI:
    """
    Создать экземпляр FastAPI приложения.

    Args:
        config_file: Путь к файлу конфигурации

    Returns:
        FastAPI: Приложение
    """
    global config

    if config_file:
        config = load_config(config_file)

    return app


def run_server(
    host: str = "127.0.0.1",
    port: int = 3001,
    config_file: Optional[str] = None,
    log_level: str = "info"
):
    """
    Запустить сервер.

    Args:
        host: Хост для прослушивания
        port: Порт для прослушивания
        config_file: Путь к файлу конфигурации
        log_level: Уровень логирования
    """
    if config_file:
        load_config(config_file)

    uvicorn.run(
        "filesystem_server.api:app",
        host=host,
        port=port,
        log_level=log_level,
        reload=False
    )