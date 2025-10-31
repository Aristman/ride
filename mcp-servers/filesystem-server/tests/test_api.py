"""
Тесты API MCP сервера файловой системы.
"""

import pytest
import tempfile
import shutil
from pathlib import Path
import httpx
import asyncio

from filesystem_server.main import create_app
from filesystem_server.config import ServerConfig


@pytest.fixture
def temp_dir():
    """Временная директория для тестов."""
    temp_dir = tempfile.mkdtemp()
    yield Path(temp_dir)
    shutil.rmtree(temp_dir)


@pytest.fixture
def test_config(temp_dir):
    """Тестовая конфигурация."""
    return ServerConfig(
        host="127.0.0.1",
        port=3001,
        base_dir=str(temp_dir),
        max_file_size=1024 * 1024,  # 1MB
        allowed_extensions=["txt", "md", "json"],
        log_level="debug"
    )


@pytest.fixture
def app(test_config):
    """FastAPI приложение для тестов."""
    import filesystem_server.api as api_module
    api_module.config = test_config
    import filesystem_server.service as service_module
    service_module.file_service = None

    app = create_app()
    return app


@pytest.fixture
async def client(app):
    """HTTP клиент для тестов."""
    async with httpx.AsyncClient(app=app, base_url="http://test") as client:
        yield client


class TestHealthEndpoint:
    """Тесты health check эндпоинта."""

    @pytest.mark.asyncio
    async def test_health_check(self, client):
        """Тест успешного health check."""
        response = await client.get("/health")
        assert response.status_code == 200

        data = response.json()
        assert data["status"] == "healthy"
        assert "version" in data
        assert "uptime_seconds" in data
        assert "base_dir" in data


class TestFileOperations:
    """Тесты файловых операций."""

    @pytest.mark.asyncio
    async def test_create_file(self, client, test_config):
        """Тест создания файла."""
        content = "Hello, World!"

        response = await client.post("/files", json={
            "path": "test.txt",
            "content": content,
            "overwrite": False
        })

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "test.txt" in data["message"]

        # Проверяем, что файл действительно создан
        file_path = Path(test_config.base_dir) / "test.txt"
        assert file_path.exists()
        assert file_path.read_text() == content

    @pytest.mark.asyncio
    async def test_create_file_with_overwrite(self, client, test_config):
        """Тест перезаписи файла."""
        # Создаем файл
        await client.post("/files", json={
            "path": "overwrite.txt",
            "content": "Original content",
            "overwrite": False
        })

        # Перезаписываем
        response = await client.post("/files", json={
            "path": "overwrite.txt",
            "content": "New content",
            "overwrite": True
        })

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True

        # Проверяем содержимое
        file_path = Path(test_config.base_dir) / "overwrite.txt"
        assert file_path.read_text() == "New content"

    @pytest.mark.asyncio
    async def test_create_file_already_exists(self, client):
        """Тест попытки создать существующий файл."""
        # Создаем файл
        await client.post("/files", json={
            "path": "exists.txt",
            "content": "Content",
            "overwrite": False
        })

        # Пытаемся создать снова без перезаписи
        response = await client.post("/files", json={
            "path": "exists.txt",
            "content": "New content",
            "overwrite": False
        })

        assert response.status_code == 400
        data = response.json()
        assert data["detail"] == "Файл уже существует"

    @pytest.mark.asyncio
    async def test_create_file_forbidden_extension(self, client):
        """Тест создания файла с запрещенным расширением."""
        response = await client.post("/files", json={
            "path": "test.exe",
            "content": "Executable content",
            "overwrite": False
        })

        assert response.status_code == 400
        data = response.json()
        assert "Запрещенное расширение" in data["detail"]

    @pytest.mark.asyncio
    async def test_read_file(self, client, test_config):
        """Тест чтения файла."""
        # Создаем файл
        content = "File content for reading"
        file_path = Path(test_config.base_dir) / "read.txt"
        file_path.write_text(content)

        # Читаем через API
        response = await client.get("/files/read.txt")
        assert response.status_code == 200

        data = response.json()
        assert data["content"] == content
        assert data["path"] == "read.txt"
        assert data["size"] == len(content.encode())
        assert "checksum" in data

    @pytest.mark.asyncio
    async def test_read_nonexistent_file(self, client):
        """Тест чтения несуществующего файла."""
        response = await client.get("/files/nonexistent.txt")
        assert response.status_code == 404
        data = response.json()
        assert "не найден" in data["detail"].lower()

    @pytest.mark.asyncio
    async def test_update_file(self, client, test_config):
        """Тест обновления файла."""
        # Создаем файл
        original_content = "Original content"
        file_path = Path(test_config.base_dir) / "update.txt"
        file_path.write_text(original_content)

        # Обновляем через API
        new_content = "Updated content"
        response = await client.put("/files/update.txt", json={
            "content": new_content,
            "encoding": "utf-8"
        })

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True

        # Проверяем обновленное содержимое
        assert file_path.read_text() == new_content

    @pytest.mark.asyncio
    async def test_update_nonexistent_file(self, client):
        """Тест обновления несуществующего файла."""
        response = await client.put("/files/nonexistent.txt", json={
            "content": "Content",
            "encoding": "utf-8"
        })

        assert response.status_code == 400
        data = response.json()
        assert "не найден" in data["detail"].lower()

    @pytest.mark.asyncio
    async def test_update_file_create_if_missing(self, client, test_config):
        """Тест обновления с созданием если отсутствует."""
        response = await client.put("/files/createme.txt", json={
            "content": "New content",
            "create_if_missing": True
        })

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True

        # Проверяем что файл создан
        file_path = Path(test_config.base_dir) / "createme.txt"
        assert file_path.exists()
        assert file_path.read_text() == "New content"

    @pytest.mark.asyncio
    async def test_delete_file(self, client, test_config):
        """Тест удаления файла."""
        # Создаем файл
        file_path = Path(test_config.base_dir) / "delete.txt"
        file_path.write_text("To be deleted")

        # Удаляем через API
        response = await client.delete("/files/delete.txt")
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True

        # Проверяем что файл удален
        assert not file_path.exists()

    @pytest.mark.asyncio
    async def test_delete_nonexistent_file(self, client):
        """Тест удаления несуществующего файла."""
        response = await client.delete("/files/nonexistent.txt")
        assert response.status_code == 400
        data = response.json()
        assert "не найден" in data["detail"].lower()


class TestDirectoryOperations:
    """Тесты операций с директориями."""

    @pytest.mark.asyncio
    async def test_create_directory(self, client, test_config):
        """Тест создания директории."""
        response = await client.post("/directories", json={
            "path": "test_dir",
            "recursive": True
        })

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True

        # Проверяем что директория создана
        dir_path = Path(test_config.base_dir) / "test_dir"
        assert dir_path.exists()
        assert dir_path.is_dir()

    @pytest.mark.asyncio
    async def test_create_nested_directory(self, client, test_config):
        """Тест создания вложенной директории."""
        response = await client.post("/directories", json={
            "path": "nested/deep/dir",
            "recursive": True
        })

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True

        # Проверяем что директория создана
        dir_path = Path(test_config.base_dir) / "nested/deep/dir"
        assert dir_path.exists()
        assert dir_path.is_dir()

    @pytest.mark.asyncio
    async def test_list_directory(self, client, test_config):
        """Тест получения списка файлов директории."""
        # Создаем тестовые файлы и директории
        base_path = Path(test_config.base_dir)
        (base_path / "file1.txt").write_text("content1")
        (base_path / "file2.md").write_text("content2")
        (base_path / "subdir").mkdir()

        response = await client.get("/files")
        assert response.status_code == 200

        data = response.json()
        assert data["path"] == ""
        assert len(data["files"]) == 2
        assert len(data["directories"]) == 1
        assert data["total_count"] == 3

        file_names = [f["name"] for f in data["files"]]
        assert "file1.txt" in file_names
        assert "file2.md" in file_names

        dir_names = [d["name"] for d in data["directories"]]
        assert "subdir" in dir_names

    @pytest.mark.asyncio
    async def test_list_subdirectory(self, client, test_config):
        """Тест получения списка поддиректории."""
        # Создаем структуру
        base_path = Path(test_config.base_dir)
        subdir = base_path / "subdir"
        subdir.mkdir()
        (subdir / "nested_file.txt").write_text("nested content")

        response = await client.get("/files?dir=subdir")
        assert response.status_code == 200

        data = response.json()
        assert data["path"] == "subdir"
        assert len(data["files"]) == 1
        assert data["files"][0]["name"] == "nested_file.txt"

    @pytest.mark.asyncio
    async def test_get_file_info(self, client, test_config):
        """Тест получения информации о файле."""
        # Создаем файл
        file_path = Path(test_config.base_dir) / "info.txt"
        content = "File for info"
        file_path.write_text(content)

        response = await client.get("/files/info.txt/info")
        assert response.status_code == 200

        data = response.json()
        assert data["name"] == "info.txt"
        assert data["path"] == "info.txt"
        assert data["size"] == len(content.encode())
        assert data["is_directory"] is False
        assert "modified_at" in data

    @pytest.mark.asyncio
    async def test_delete_directory(self, client, test_config):
        """Тест удаления директории."""
        # Создаем директорию с файлами
        dir_path = Path(test_config.base_dir) / "delete_dir"
        dir_path.mkdir()
        (dir_path / "file1.txt").write_text("content1")
        (dir_path / "file2.txt").write_text("content2")

        # Удаляем через API
        response = await client.delete("/directories/delete_dir")
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True

        # Проверяем что директория удалена
        assert not dir_path.exists()


class TestBatchOperations:
    """Тесты пакетных операций."""

    @pytest.mark.asyncio
    async def test_batch_operations_success(self, client):
        """Тест успешных пакетных операций."""
        operations = [
            {
                "type": "create_file",
                "data": {
                    "path": "batch1.txt",
                    "content": "Batch content 1"
                }
            },
            {
                "type": "create_file",
                "data": {
                    "path": "batch2.txt",
                    "content": "Batch content 2"
                }
            },
            {
                "type": "create_directory",
                "data": {
                    "path": "batch_dir",
                    "recursive": True
                }
            }
        ]

        response = await client.post("/batch", json={
            "operations": operations,
            "continue_on_error": True
        })

        assert response.status_code == 200
        data = response.json()
        assert data["total_operations"] == 3
        assert data["successful_operations"] == 3
        assert data["failed_operations"] == 0
        assert len(data["results"]) == 3

    @pytest.mark.asyncio
    async def test_batch_operations_with_errors(self, client):
        """Тест пакетных операций с ошибками."""
        operations = [
            {
                "type": "create_file",
                "data": {
                    "path": "valid.txt",
                    "content": "Valid content"
                }
            },
            {
                "type": "create_file",
                "data": {
                    "path": "invalid.exe",  # Запрещенное расширение
                    "content": "Invalid content"
                }
            }
        ]

        response = await client.post("/batch", json={
            "operations": operations,
            "continue_on_error": True
        })

        assert response.status_code == 200
        data = response.json()
        assert data["total_operations"] == 2
        assert data["successful_operations"] == 1
        assert data["failed_operations"] == 1
        assert len(data["errors"]) == 1


class TestSecurity:
    """Тесты безопасности."""

    @pytest.mark.asyncio
    async def test_path_traversal_protection(self, client):
        """Тест защиты от path traversal атак."""
        # Попытка выхода за пределы базовой директории
        response = await client.get("/files/../../../etc/passwd")
        assert response.status_code == 400
        data = response.json()
        assert "Небезопасный путь" in data["detail"]

    @pytest.mark.asyncio
    async def test_absolute_path_protection(self, client):
        """Тест защиты от абсолютных путей."""
        response = await client.post("/files", json={
            "path": "/etc/passwd",
            "content": "malicious content"
        })
        assert response.status_code == 400
        data = response.json()
        assert "относительным" in data["detail"].lower()

    @pytest.mark.asyncio
    async def test_hidden_files_protection(self, client, test_config):
        """Тест защиты от скрытых файлов."""
        # Создаем скрытый файл напрямую
        hidden_file = Path(test_config.base_dir) / ".hidden"
        hidden_file.write_text("hidden content")

        # Проверяем что он не появляется в списке
        response = await client.get("/files")
        assert response.status_code == 200
        data = response.json()

        file_names = [f["name"] for f in data["files"]]
        assert ".hidden" not in file_names


class TestSecurityInfo:
    """Тесты эндпоинта информации о безопасности."""

    @pytest.mark.asyncio
    async def test_security_info_endpoint(self, client, test_config):
        """Тест получения информации о безопасности."""
        response = await client.get("/config/security")
        assert response.status_code == 200

        data = response.json()
        assert data["security_enabled"] is True
        assert data["base_dir"] == test_config.base_dir
        assert data["max_file_size"] == test_config.max_file_size
        assert "allowed_extensions" in data
        assert "blocked_paths" in data