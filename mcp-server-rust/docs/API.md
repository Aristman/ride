# API Documentation

## Base URL

```
http://localhost:3000
```

## Authentication

Currently, the API does not require authentication. In production, consider adding:
- API Keys
- JWT tokens
- OAuth2

## Endpoints

### Health Check

Check server health and status.

**Endpoint:** `GET /health`

**Response:**
```json
{
  "status": "healthy",
  "version": "0.1.0",
  "uptime_seconds": 3600
}
```

**Status Codes:**
- `200 OK` - Server is healthy

---

### Create File

Create a new file with content.

**Endpoint:** `POST /files`

**Request Body:**
```json
{
  "path": "example.txt",
  "content": "File content here",
  "overwrite": false
}
```

**Parameters:**
- `path` (string, required): Relative path to the file (1-255 characters)
- `content` (string, required): File content
- `overwrite` (boolean, optional): Whether to overwrite existing file (default: false)

**Response:**
```json
{
  "path": "example.txt",
  "size": 17,
  "created_at": "2024-01-01T12:00:00Z",
  "modified_at": "2024-01-01T12:00:00Z",
  "is_readonly": false,
  "checksum": "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e"
}
```

**Status Codes:**
- `201 Created` - File created successfully
- `400 Bad Request` - Invalid input (validation error)
- `403 Forbidden` - Permission denied (path not allowed)
- `413 Payload Too Large` - File size exceeds limit

**Errors:**
```json
{
  "error": "INVALID_INPUT",
  "message": "Invalid input provided",
  "details": "File 'example.txt' already exists"
}
```

---

### Read File

Read file content.

**Endpoint:** `GET /files/:path`

**Parameters:**
- `path` (string, required): Relative path to the file

**Response:**
```json
{
  "path": "example.txt",
  "content": "File content here",
  "size": 17,
  "mime_type": "text/plain",
  "checksum": "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e"
}
```

**Status Codes:**
- `200 OK` - File read successfully
- `404 Not Found` - File not found
- `403 Forbidden` - Permission denied
- `413 Payload Too Large` - File too large to read

---

### Update File

Update existing file content.

**Endpoint:** `PUT /files/:path`

**Parameters:**
- `path` (string, required): Relative path to the file

**Request Body:**
```json
{
  "content": "Updated content"
}
```

**Response:**
```json
{
  "path": "example.txt",
  "size": 15,
  "created_at": "2024-01-01T12:00:00Z",
  "modified_at": "2024-01-01T12:05:00Z",
  "is_readonly": false,
  "checksum": "b7e23ec29af22b0b4e41da31e868d57226121c84f0a94e2e5e3e4f9c5e8f5f5f"
}
```

**Status Codes:**
- `200 OK` - File updated successfully
- `404 Not Found` - File not found
- `403 Forbidden` - Permission denied
- `413 Payload Too Large` - File size exceeds limit

---

### Delete File

Delete a file.

**Endpoint:** `DELETE /files/:path`

**Parameters:**
- `path` (string, required): Relative path to the file

**Response:**
```json
{
  "success": true,
  "message": "File 'example.txt' deleted successfully"
}
```

**Status Codes:**
- `200 OK` - File deleted successfully
- `404 Not Found` - File not found
- `403 Forbidden` - Permission denied

---

### List Files

List files and directories in a directory.

**Endpoint:** `GET /files`

**Query Parameters:**
- `dir` (string, optional): Directory path (default: base directory)

**Response:**
```json
{
  "path": ".",
  "files": [
    {
      "name": "example.txt",
      "path": "/full/path/to/example.txt",
      "size": 17,
      "modified_at": "2024-01-01T12:00:00Z",
      "is_readonly": false
    }
  ],
  "directories": [
    {
      "name": "subdir",
      "path": "/full/path/to/subdir",
      "modified_at": "2024-01-01T12:00:00Z"
    }
  ]
}
```

**Status Codes:**
- `200 OK` - List retrieved successfully
- `404 Not Found` - Directory not found
- `403 Forbidden` - Permission denied

---

### Create Directory

Create a new directory.

**Endpoint:** `POST /directories`

**Request Body:**
```json
{
  "path": "new_directory",
  "recursive": true
}
```

**Parameters:**
- `path` (string, required): Relative path to the directory (1-255 characters)
- `recursive` (boolean, optional): Create parent directories if needed (default: false)

**Response:**
```json
{
  "path": "new_directory",
  "created_at": "2024-01-01T12:00:00Z"
}
```

**Status Codes:**
- `201 Created` - Directory created successfully
- `400 Bad Request` - Invalid input or directory already exists
- `403 Forbidden` - Permission denied

---

### Delete Directory

Delete a directory and all its contents.

**Endpoint:** `DELETE /directories/:path`

**Parameters:**
- `path` (string, required): Relative path to the directory

**Response:**
```json
{
  "success": true,
  "message": "Directory 'old_directory' deleted successfully"
}
```

**Status Codes:**
- `200 OK` - Directory deleted successfully
- `404 Not Found` - Directory not found
- `403 Forbidden` - Permission denied

---

### List Directories

List contents of a directory (same as List Files).

**Endpoint:** `GET /directories`

**Query Parameters:**
- `path` (string, optional): Directory path (default: base directory)

**Response:** Same as List Files

---

## Error Responses

All error responses follow this format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable message",
  "details": "Additional details (optional)"
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `NOT_FOUND` | 404 | Resource not found |
| `INVALID_INPUT` | 400 | Invalid input data |
| `PERMISSION_DENIED` | 403 | Access denied |
| `FILE_TOO_LARGE` | 413 | File exceeds size limit |
| `IO_ERROR` | 500 | File system error |
| `VALIDATION_ERROR` | 400 | Validation failed |
| `INTERNAL_ERROR` | 500 | Internal server error |

---

## Rate Limiting

Currently not implemented. Consider adding rate limiting in production:
- Per IP address
- Per API key
- Sliding window algorithm

---

## CORS

CORS is currently set to permissive mode. In production, configure specific origins:

```rust
CorsLayer::new()
    .allow_origin("https://yourdomain.com".parse::<HeaderValue>().unwrap())
    .allow_methods([Method::GET, Method::POST, Method::PUT, Method::DELETE])
    .allow_headers([CONTENT_TYPE])
```

---

## Examples

### cURL Examples

```bash
# Health check
curl http://localhost:3000/health

# Create file
curl -X POST http://localhost:3000/files \
  -H "Content-Type: application/json" \
  -d '{
    "path": "test.txt",
    "content": "Hello, World!",
    "overwrite": false
  }'

# Read file
curl http://localhost:3000/files/test.txt

# Update file
curl -X PUT http://localhost:3000/files/test.txt \
  -H "Content-Type: application/json" \
  -d '{"content": "Updated content"}'

# Delete file
curl -X DELETE http://localhost:3000/files/test.txt

# List files
curl http://localhost:3000/files?dir=subdirectory

# Create directory
curl -X POST http://localhost:3000/directories \
  -H "Content-Type: application/json" \
  -d '{"path": "new_dir", "recursive": true}'

# Delete directory
curl -X DELETE http://localhost:3000/directories/new_dir
```

### JavaScript/TypeScript Example

```typescript
const API_BASE = 'http://localhost:3000';

// Create file
async function createFile(path: string, content: string) {
  const response = await fetch(`${API_BASE}/files`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path, content, overwrite: false })
  });
  return response.json();
}

// Read file
async function readFile(path: string) {
  const response = await fetch(`${API_BASE}/files/${path}`);
  return response.json();
}

// Update file
async function updateFile(path: string, content: string) {
  const response = await fetch(`${API_BASE}/files/${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content })
  });
  return response.json();
}

// Delete file
async function deleteFile(path: string) {
  const response = await fetch(`${API_BASE}/files/${path}`, {
    method: 'DELETE'
  });
  return response.json();
}
```

### Python Example

```python
import requests

API_BASE = 'http://localhost:3000'

# Create file
def create_file(path: str, content: str):
    response = requests.post(
        f'{API_BASE}/files',
        json={'path': path, 'content': content, 'overwrite': False}
    )
    return response.json()

# Read file
def read_file(path: str):
    response = requests.get(f'{API_BASE}/files/{path}')
    return response.json()

# Update file
def update_file(path: str, content: str):
    response = requests.put(
        f'{API_BASE}/files/{path}',
        json={'content': content}
    )
    return response.json()

# Delete file
def delete_file(path: str):
    response = requests.delete(f'{API_BASE}/files/{path}')
    return response.json()
```
