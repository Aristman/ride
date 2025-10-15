# Examples

Примеры использования MCP Server Rust API.

## Содержание

- [cURL](#curl)
- [JavaScript/TypeScript](#javascripttypescript)
- [Python](#python)
- [Rust](#rust)
- [Go](#go)

## cURL

### Базовые операции

```bash
# Health check
curl http://localhost:3000/health

# Создать файл
curl -X POST http://localhost:3000/files \
  -H "Content-Type: application/json" \
  -d '{
    "path": "hello.txt",
    "content": "Hello, World!",
    "overwrite": false
  }'

# Прочитать файл
curl http://localhost:3000/files/hello.txt

# Обновить файл
curl -X PUT http://localhost:3000/files/hello.txt \
  -H "Content-Type: application/json" \
  -d '{"content": "Updated content!"}'

# Удалить файл
curl -X DELETE http://localhost:3000/files/hello.txt

# Список файлов
curl http://localhost:3000/files

# Список файлов в поддиректории
curl "http://localhost:3000/files?dir=subdirectory"
```

### Работа с директориями

```bash
# Создать директорию
curl -X POST http://localhost:3000/directories \
  -H "Content-Type: application/json" \
  -d '{
    "path": "my_folder",
    "recursive": false
  }'

# Создать вложенные директории
curl -X POST http://localhost:3000/directories \
  -H "Content-Type: application/json" \
  -d '{
    "path": "parent/child/grandchild",
    "recursive": true
  }'

# Удалить директорию
curl -X DELETE http://localhost:3000/directories/my_folder
```

### Обработка ошибок

```bash
# Попытка создать существующий файл
curl -X POST http://localhost:3000/files \
  -H "Content-Type: application/json" \
  -d '{
    "path": "existing.txt",
    "content": "content",
    "overwrite": false
  }'
# Вернет 400 Bad Request

# Попытка прочитать несуществующий файл
curl http://localhost:3000/files/nonexistent.txt
# Вернет 404 Not Found
```

## JavaScript/TypeScript

### Базовый клиент

```typescript
const API_BASE = 'http://localhost:3000';

interface FileResponse {
  path: string;
  size: number;
  created_at: string;
  modified_at: string;
  is_readonly: boolean;
  checksum: string;
}

interface FileContentResponse {
  path: string;
  content: string;
  size: number;
  mime_type: string;
  checksum: string;
}

class MCPClient {
  private baseUrl: string;

  constructor(baseUrl: string = API_BASE) {
    this.baseUrl = baseUrl;
  }

  async createFile(
    path: string,
    content: string,
    overwrite: boolean = false
  ): Promise<FileResponse> {
    const response = await fetch(`${this.baseUrl}/files`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path, content, overwrite })
    });

    if (!response.ok) {
      throw new Error(`Failed to create file: ${response.statusText}`);
    }

    return response.json();
  }

  async readFile(path: string): Promise<FileContentResponse> {
    const response = await fetch(`${this.baseUrl}/files/${path}`);

    if (!response.ok) {
      throw new Error(`Failed to read file: ${response.statusText}`);
    }

    return response.json();
  }

  async updateFile(path: string, content: string): Promise<FileResponse> {
    const response = await fetch(`${this.baseUrl}/files/${path}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content })
    });

    if (!response.ok) {
      throw new Error(`Failed to update file: ${response.statusText}`);
    }

    return response.json();
  }

  async deleteFile(path: string): Promise<void> {
    const response = await fetch(`${this.baseUrl}/files/${path}`, {
      method: 'DELETE'
    });

    if (!response.ok) {
      throw new Error(`Failed to delete file: ${response.statusText}`);
    }
  }

  async listFiles(dir?: string): Promise<any> {
    const url = dir 
      ? `${this.baseUrl}/files?dir=${encodeURIComponent(dir)}`
      : `${this.baseUrl}/files`;
    
    const response = await fetch(url);

    if (!response.ok) {
      throw new Error(`Failed to list files: ${response.statusText}`);
    }

    return response.json();
  }
}

// Использование
async function example() {
  const client = new MCPClient();

  try {
    // Создать файл
    const created = await client.createFile('test.txt', 'Hello, World!');
    console.log('File created:', created);

    // Прочитать файл
    const content = await client.readFile('test.txt');
    console.log('File content:', content.content);

    // Обновить файл
    const updated = await client.updateFile('test.txt', 'Updated!');
    console.log('File updated:', updated);

    // Список файлов
    const files = await client.listFiles();
    console.log('Files:', files);

    // Удалить файл
    await client.deleteFile('test.txt');
    console.log('File deleted');
  } catch (error) {
    console.error('Error:', error);
  }
}
```

### React Hook

```typescript
import { useState, useEffect } from 'react';

function useFile(path: string) {
  const [content, setContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const fetchFile = async () => {
      try {
        setLoading(true);
        const response = await fetch(`http://localhost:3000/files/${path}`);
        const data = await response.json();
        setContent(data.content);
      } catch (err) {
        setError(err as Error);
      } finally {
        setLoading(false);
      }
    };

    fetchFile();
  }, [path]);

  return { content, loading, error };
}

// Использование в компоненте
function FileViewer({ path }: { path: string }) {
  const { content, loading, error } = useFile(path);

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error.message}</div>;

  return <pre>{content}</pre>;
}
```

## Python

### Базовый клиент

```python
import requests
from typing import Optional, Dict, Any

class MCPClient:
    def __init__(self, base_url: str = "http://localhost:3000"):
        self.base_url = base_url
        self.session = requests.Session()

    def create_file(
        self, 
        path: str, 
        content: str, 
        overwrite: bool = False
    ) -> Dict[str, Any]:
        """Create a new file."""
        response = self.session.post(
            f"{self.base_url}/files",
            json={
                "path": path,
                "content": content,
                "overwrite": overwrite
            }
        )
        response.raise_for_status()
        return response.json()

    def read_file(self, path: str) -> Dict[str, Any]:
        """Read file content."""
        response = self.session.get(f"{self.base_url}/files/{path}")
        response.raise_for_status()
        return response.json()

    def update_file(self, path: str, content: str) -> Dict[str, Any]:
        """Update file content."""
        response = self.session.put(
            f"{self.base_url}/files/{path}",
            json={"content": content}
        )
        response.raise_for_status()
        return response.json()

    def delete_file(self, path: str) -> Dict[str, Any]:
        """Delete a file."""
        response = self.session.delete(f"{self.base_url}/files/{path}")
        response.raise_for_status()
        return response.json()

    def list_files(self, dir: Optional[str] = None) -> Dict[str, Any]:
        """List files in directory."""
        params = {"dir": dir} if dir else {}
        response = self.session.get(f"{self.base_url}/files", params=params)
        response.raise_for_status()
        return response.json()

    def create_directory(
        self, 
        path: str, 
        recursive: bool = False
    ) -> Dict[str, Any]:
        """Create a directory."""
        response = self.session.post(
            f"{self.base_url}/directories",
            json={"path": path, "recursive": recursive}
        )
        response.raise_for_status()
        return response.json()

# Использование
def main():
    client = MCPClient()

    try:
        # Создать файл
        result = client.create_file("test.txt", "Hello, Python!")
        print(f"File created: {result}")

        # Прочитать файл
        content = client.read_file("test.txt")
        print(f"Content: {content['content']}")

        # Обновить файл
        updated = client.update_file("test.txt", "Updated content")
        print(f"File updated: {updated}")

        # Список файлов
        files = client.list_files()
        print(f"Files: {files}")

        # Удалить файл
        client.delete_file("test.txt")
        print("File deleted")

    except requests.exceptions.HTTPError as e:
        print(f"HTTP Error: {e}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()
```

### Async клиент (aiohttp)

```python
import aiohttp
import asyncio
from typing import Optional, Dict, Any

class AsyncMCPClient:
    def __init__(self, base_url: str = "http://localhost:3000"):
        self.base_url = base_url

    async def create_file(
        self, 
        path: str, 
        content: str, 
        overwrite: bool = False
    ) -> Dict[str, Any]:
        async with aiohttp.ClientSession() as session:
            async with session.post(
                f"{self.base_url}/files",
                json={"path": path, "content": content, "overwrite": overwrite}
            ) as response:
                response.raise_for_status()
                return await response.json()

    async def read_file(self, path: str) -> Dict[str, Any]:
        async with aiohttp.ClientSession() as session:
            async with session.get(f"{self.base_url}/files/{path}") as response:
                response.raise_for_status()
                return await response.json()

# Использование
async def main():
    client = AsyncMCPClient()
    
    result = await client.create_file("async_test.txt", "Hello, Async!")
    print(result)
    
    content = await client.read_file("async_test.txt")
    print(content)

if __name__ == "__main__":
    asyncio.run(main())
```

## Rust

### Клиент на Rust

```rust
use reqwest;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize)]
struct CreateFileRequest {
    path: String,
    content: String,
    overwrite: bool,
}

#[derive(Debug, Deserialize)]
struct FileResponse {
    path: String,
    size: u64,
    checksum: String,
}

#[derive(Debug, Deserialize)]
struct FileContentResponse {
    path: String,
    content: String,
    size: u64,
    mime_type: String,
    checksum: String,
}

struct MCPClient {
    base_url: String,
    client: reqwest::Client,
}

impl MCPClient {
    fn new(base_url: &str) -> Self {
        Self {
            base_url: base_url.to_string(),
            client: reqwest::Client::new(),
        }
    }

    async fn create_file(
        &self,
        path: &str,
        content: &str,
    ) -> Result<FileResponse, reqwest::Error> {
        let request = CreateFileRequest {
            path: path.to_string(),
            content: content.to_string(),
            overwrite: false,
        };

        self.client
            .post(&format!("{}/files", self.base_url))
            .json(&request)
            .send()
            .await?
            .json()
            .await
    }

    async fn read_file(&self, path: &str) -> Result<FileContentResponse, reqwest::Error> {
        self.client
            .get(&format!("{}/files/{}", self.base_url, path))
            .send()
            .await?
            .json()
            .await
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let client = MCPClient::new("http://localhost:3000");

    // Создать файл
    let created = client.create_file("test.txt", "Hello, Rust!").await?;
    println!("Created: {:?}", created);

    // Прочитать файл
    let content = client.read_file("test.txt").await?;
    println!("Content: {}", content.content);

    Ok(())
}
```

## Go

### Клиент на Go

```go
package main

import (
    "bytes"
    "encoding/json"
    "fmt"
    "io"
    "net/http"
)

type CreateFileRequest struct {
    Path      string `json:"path"`
    Content   string `json:"content"`
    Overwrite bool   `json:"overwrite"`
}

type FileResponse struct {
    Path     string `json:"path"`
    Size     uint64 `json:"size"`
    Checksum string `json:"checksum"`
}

type MCPClient struct {
    BaseURL string
    Client  *http.Client
}

func NewMCPClient(baseURL string) *MCPClient {
    return &MCPClient{
        BaseURL: baseURL,
        Client:  &http.Client{},
    }
}

func (c *MCPClient) CreateFile(path, content string) (*FileResponse, error) {
    request := CreateFileRequest{
        Path:      path,
        Content:   content,
        Overwrite: false,
    }

    jsonData, err := json.Marshal(request)
    if err != nil {
        return nil, err
    }

    resp, err := c.Client.Post(
        c.BaseURL+"/files",
        "application/json",
        bytes.NewBuffer(jsonData),
    )
    if err != nil {
        return nil, err
    }
    defer resp.Body.Close()

    var result FileResponse
    if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
        return nil, err
    }

    return &result, nil
}

func main() {
    client := NewMCPClient("http://localhost:3000")

    result, err := client.CreateFile("test.txt", "Hello, Go!")
    if err != nil {
        fmt.Printf("Error: %v\n", err)
        return
    }

    fmt.Printf("Created: %+v\n", result)
}
```

## Дополнительные примеры

### Batch операции

```bash
# Создать несколько файлов
for i in {1..5}; do
  curl -X POST http://localhost:3000/files \
    -H "Content-Type: application/json" \
    -d "{\"path\":\"file$i.txt\",\"content\":\"Content $i\",\"overwrite\":false}"
done

# Прочитать все файлы
curl http://localhost:3000/files | jq '.files[] | .name'
```

### Работа с большими файлами

```python
# Чтение большого файла частями
def read_large_file(client, path, chunk_size=1024):
    content = client.read_file(path)
    data = content['content']
    
    for i in range(0, len(data), chunk_size):
        yield data[i:i+chunk_size]

# Использование
for chunk in read_large_file(client, "large_file.txt"):
    process_chunk(chunk)
```
