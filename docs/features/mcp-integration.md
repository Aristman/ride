# MCP Integration - Интеграция MCP серверов

## Обзор

Плагин Ride поддерживает интеграцию с MCP (Model Context Protocol) серверами, позволяя расширять функциональность через внешние инструменты и сервисы.

## Возможности

- ✅ Подключение к MCP серверам через **stdio** (запуск процессов)
- ✅ Подключение к MCP серверам через **HTTP**
- ✅ Управление серверами через UI настроек
- ✅ Просмотр доступных методов каждого сервера
- ✅ Вызов методов MCP серверов с параметрами
- ✅ Визуальные индикаторы статуса подключения
- ✅ Автоматическая инициализация при запуске IDE

## Конфигурация

### Файл `.ride/mcp.json`

MCP серверы настраиваются через JSON файл в корне проекта:

```json
{
  "servers": [
    {
      "name": "filesystem",
      "type": "STDIO",
      "command": "node",
      "args": ["path/to/mcp-server-filesystem/dist/index.js"],
      "env": {
        "NODE_ENV": "production"
      },
      "enabled": true
    },
    {
      "name": "github",
      "type": "HTTP",
      "url": "http://localhost:3000/mcp",
      "enabled": true
    }
  ]
}
```

### Параметры конфигурации

#### Общие параметры

- **name** (string, обязательно) - Уникальное имя сервера
- **type** (string, обязательно) - Тип подключения: `STDIO` или `HTTP`
- **enabled** (boolean) - Включен ли сервер (по умолчанию `true`)

#### Параметры для STDIO

- **command** (string, обязательно) - Команда для запуска (например, `node`, `python`)
- **args** (array) - Аргументы командной строки
- **env** (object) - Переменные окружения

#### Параметры для HTTP

- **url** (string, обязательно) - URL эндпоинта (например, `http://localhost:3000/mcp`)
- **headers** (object, опционально) - HTTP заголовки (например, для авторизации)
  - В UI используйте формат: `Key: Value` или `Key=Value`, разделитель `;`
  - Пример: `Authorization: Bearer TOKEN;X-Custom: value`

## Использование

### 1. Настройка серверов через UI

1. Откройте **Settings → Tools → Ride → MCP Servers**
2. Нажмите **"+"** для добавления нового сервера
3. Заполните параметры:
   - **Name**: уникальное имя сервера
   - **Type**: выберите STDIO или HTTP
   - Для STDIO: укажите команду, аргументы и переменные окружения
   - Для HTTP: укажите URL
4. Нажмите **"Test Connection"** для проверки
5. Нажмите **"OK"** и **"Apply"** для сохранения

### 2. Просмотр методов

1. Откройте Tool Window **"MCP Methods"** (обычно справа в IDE)
2. Серверы отображаются со статусом:
   - ✓ **Connected** - сервер подключен
   - ✗ **Error** - ошибка подключения
   - ○ **Disconnected** - сервер отключен
3. Кликните на имя сервера для раскрытия списка методов

### 3. Вызов методов

1. В списке методов нажмите **"Call"** рядом с нужным методом
2. В открывшемся диалоге введите аргументы в формате JSON
3. Нажмите **"Call"** для выполнения
4. Результат отобразится в поле "Result"

## Примеры

### Пример 1: Файловая система (STDIO)

```json
{
  "name": "filesystem",
  "type": "STDIO",
  "command": "npx",
  "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/directory"],
  "enabled": true
}
```

**Доступные методы:**
- `read_file` - чтение файла
- `write_file` - запись в файл
- `list_directory` - список файлов в директории

### Пример 2: GitHub MCP Server (Локальный через Docker)

**Шаг 1: Запустите GitHub MCP Server в Docker**

```bash
# Создайте GitHub Personal Access Token на https://github.com/settings/personal-access-tokens/new
# Запустите сервер
docker run -d -p 3000:3000 \
  -e GITHUB_PERSONAL_ACCESS_TOKEN=your_token_here \
  ghcr.io/github/github-mcp-server:latest
```

**Шаг 2: Настройте в Ride**

Создайте `.ride/mcp.json`:

```json
{
  "servers": [
    {
      "name": "github",
      "type": "STDIO",
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "GITHUB_PERSONAL_ACCESS_TOKEN=your_token_here",
        "ghcr.io/github/github-mcp-server:latest"
      ],
      "enabled": true
    }
  ]
}
```

**Или через HTTP** (если сервер уже запущен):

```json
{
  "name": "github",
  "type": "HTTP",
  "url": "http://localhost:3000",
  "enabled": true
}
```

**Примечание:** 
- Замените `your_token_here` на ваш GitHub Personal Access Token
- **Важно:** Используйте переменную `GITHUB_PERSONAL_ACCESS_TOKEN`, а не `GITHUB_PAT`
- Remote GitHub MCP Server (`https://api.githubcopilot.com/mcp/`) требует OAuth и не поддерживается напрямую
- Используйте локальный Docker-сервер для полной функциональности

**Доступные методы:**
- `create_issue` - создание issue
- `search_repositories` - поиск репозиториев
- `get_file_contents` - получение содержимого файла
- `create_pull_request` - создание PR
- `fork_repository` - форк репозитория
- и [многие другие](https://github.com/github/github-mcp-server#tools)

### Пример вызова метода

**Метод:** `read_file`

**Аргументы:**
```json
{
  "path": "/path/to/file.txt"
}
```

**Результат:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "File contents here..."
    }
  ]
}
```

## JSON-RPC Протокол

MCP использует JSON-RPC 2.0 для коммуникации.

### Запрос списка методов

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list"
}
```

### Ответ со списком методов

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "read_file",
        "description": "Read file contents",
        "inputSchema": {
          "type": "object",
          "properties": {
            "path": {"type": "string"}
          },
          "required": ["path"]
        }
      }
    ]
  }
}
```

### Вызов метода

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "read_file",
    "arguments": {
      "path": "/path/to/file.txt"
    }
  }
}
```

## Архитектура

### Компоненты

1. **MCPServerConfig** - модель конфигурации сервера
2. **MCPConfigService** - управление конфигурацией (загрузка/сохранение)
3. **MCPClient** - интерфейс для подключения к серверам
   - **StdioMCPClient** - реализация для stdio
   - **HttpMCPClient** - реализация для HTTP
4. **MCPConnectionManager** - управление подключениями и статусами
5. **MCPSettingsPanel** - UI для настройки серверов
6. **MCPMethodsPanel** - UI для просмотра и вызова методов

### Поток данных

```
Конфигурация (.ride/mcp.json)
    ↓
MCPConfigService.loadConfig()
    ↓
MCPConnectionManager.initializeConnections()
    ↓
MCPClientFactory.createClient() → StdioMCPClient / HttpMCPClient
    ↓
client.connect() → запуск процесса / HTTP запрос
    ↓
client.listMethods() → получение списка методов
    ↓
MCPMethodsPanel → отображение в UI
    ↓
client.callMethod() → вызов метода
    ↓
Результат отображается в MCPMethodCallDialog
```

## Обработка ошибок

### Ошибки подключения

- **Timeout** - превышено время ожидания (30 секунд по умолчанию)
- **Process failed** - не удалось запустить процесс (для STDIO)
- **HTTP error** - ошибка HTTP запроса (для HTTP)

### Ошибки вызова методов

- **Method not found** - метод не найден на сервере
- **Invalid params** - невалидные параметры
- **Internal error** - внутренняя ошибка сервера

### Логирование

Все события логируются через IntelliJ Logger:
- Подключение/отключение серверов
- Вызовы методов
- Ошибки и исключения

Логи доступны в **Help → Show Log in Explorer**

## Безопасность

⚠️ **Важно:**
- MCP серверы запускают внешние процессы или делают HTTP запросы
- Используйте только доверенные серверы
- Проверяйте команды и URL перед добавлением
- Не передавайте чувствительные данные через MCP

## Troubleshooting

### Сервер не подключается

1. Проверьте, что команда/URL корректны
2. Для STDIO: убедитесь, что исполняемый файл доступен в PATH
3. Для HTTP: проверьте, что сервер запущен и доступен
4. Проверьте логи IDE для деталей ошибки

### Методы не отображаются

1. Убедитесь, что сервер подключен (зеленый статус)
2. Нажмите **"Refresh"** для обновления списка
3. Проверьте, что сервер поддерживает метод `tools/list`

### Ошибка при вызове метода

1. Проверьте формат JSON аргументов
2. Убедитесь, что все обязательные параметры указаны
3. Проверьте inputSchema метода для корректных типов

## Примеры MCP серверов

### Официальные серверы

- **@modelcontextprotocol/server-filesystem** - работа с файловой системой
- **@modelcontextprotocol/server-github** - интеграция с GitHub
- **@modelcontextprotocol/server-postgres** - работа с PostgreSQL
- **@modelcontextprotocol/server-brave-search** - поиск через Brave

### Установка MCP сервера (пример)

```bash
# Установка через npm
npm install -g @modelcontextprotocol/server-filesystem

# Или использование через npx (без установки)
npx -y @modelcontextprotocol/server-filesystem /path/to/directory
```

## Дополнительные ресурсы

- [MCP Specification](https://spec.modelcontextprotocol.io/)
- [MCP GitHub Repository](https://github.com/modelcontextprotocol)
- [Ride Plugin Documentation](../README.md)

---

**Дата создания:** 2025-10-13  
**Версия:** 1.0  
**Статус:** Готово к использованию
