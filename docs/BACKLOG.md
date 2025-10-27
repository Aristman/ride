# Бэклог задач

Список задач и идей для будущей реализации.

## Список всех задач

- [ ] Стабилизировать все тесты
- [ ] Поддержка OAuth для MCP серверов
- [ ] Отслеживание использования токенов
- [ ] Форматирование ответов - поддержка дополнительных форматов
- [ ] Интеграция MCP с Agent - автоматический вызов MCP tools
- [ ] Доработка инструмента деплоя (deploy-pugin CLI): полный цикл, секреты, метаданные

## Стабилизировать все тесты

**Приоритет:** Высокий  
**Статус:** Запланировано  
**Оценка:** ~8 часов

### Описание
Привести все существующие тесты в рабочее состояние. В настоящее время часть тестов не компилируется из-за несовместимости между JUnit 4 и JUnit 5, а также из-за использования устаревших API.

### Проблемы
1. **Смешанное использование JUnit 4 и JUnit 5**
   - Новые MCP тесты используют JUnit 4
   - Старые тесты используют JUnit 5 (jupiter)
   - Нужно привести к единому стандарту

2. **Ошибки компиляции в существующих тестах**
   - `ResponseFormattingIntegrationTest` - unresolved references
   - `JsonResponseParserTest`, `XmlResponseParserTest`, `TextResponseParserTest` - unresolved references
   - `HuggingFaceProviderTest` - использует JUnit 5
   - `ResponseModelsTest` - проблемы с типами

3. **Устаревшие API**
   - Некоторые тесты используют методы, которые были изменены или удалены

### Задачи
- [ ] Выбрать единый стандарт тестирования (JUnit 4 или JUnit 5)
- [ ] Обновить все тесты для использования выбранного стандарта
- [ ] Исправить ошибки компиляции в существующих тестах
- [ ] Обновить импорты и зависимости
- [ ] Убедиться, что все тесты проходят успешно
- [ ] Добавить в CI/CD проверку всех тестов

### Технические детали

**Вариант 1: Миграция на JUnit 4**
- Преимущества: новые MCP тесты уже используют JUnit 4
- Недостатки: JUnit 4 устаревший, меньше возможностей
- Действия: обновить старые тесты с JUnit 5 на JUnit 4

**Вариант 2: Миграция на JUnit 5** (рекомендуется)
- Преимущества: современный фреймворк, больше возможностей
- Недостатки: нужно обновить новые MCP тесты
- Действия: обновить MCP тесты с JUnit 4 на JUnit 5

**Рекомендация:** Использовать JUnit 5 как современный стандарт

### Файлы, требующие исправления
1. `src/test/kotlin/ru/marslab/ide/ride/agent/integration/ResponseFormattingIntegrationTest.kt`
2. `src/test/kotlin/ru/marslab/ide/ride/agent/parser/JsonResponseParserTest.kt`
3. `src/test/kotlin/ru/marslab/ide/ride/agent/parser/TextResponseParserTest.kt`
4. `src/test/kotlin/ru/marslab/ide/ride/agent/parser/XmlResponseParserTest.kt`
5. `src/test/kotlin/ru/marslab/ide/ride/agent/validation/JsonResponseValidatorTest.kt`
6. `src/test/kotlin/ru/marslab/ide/ride/agent/validation/XmlResponseValidatorTest.kt`
7. `src/test/kotlin/ru/marslab/ide/ride/integration/llm/impl/HuggingFaceProviderTest.kt`
8. `src/test/kotlin/ru/marslab/ide/ride/model/ResponseModelsTest.kt`
9. Все MCP тесты (если выбран вариант 2)

### Критерии завершения
- ✅ Все тесты компилируются без ошибок
- ✅ Все тесты используют единый фреймворк (JUnit 4 или 5)
- ✅ `./gradlew test` проходит успешно
- ✅ Покрытие тестами не уменьшилось
- ✅ Документация обновлена

### Зависимости
- Нет критических зависимостей

### Риски
- Возможны регрессии при обновлении тестов
- Может потребоваться обновление mock-библиотек

---

## Поддержка OAuth для MCP серверов

**Приоритет:** Средний  
**Статус:** Запланировано  
**Оценка:** ~16 часов

### Описание
Добавить поддержку OAuth авторизации для MCP серверов, чтобы можно было подключаться к Remote GitHub MCP Server и другим сервисам, требующим OAuth flow.

### Проблема
В настоящее время плагин поддерживает только:
- Basic Authentication (через headers)
- Bearer tokens (статичные)

Но многие MCP серверы (например, Remote GitHub MCP Server на `https://api.githubcopilot.com/mcp/`) требуют полноценный OAuth 2.0 flow с:
- Authorization Code flow
- Session management
- Token refresh

### Цели
- Поддержка OAuth 2.0 Authorization Code flow
- Безопасное хранение OAuth токенов
- Автоматический refresh токенов
- UI для OAuth авторизации
- Поддержка различных OAuth провайдеров (GitHub, Google, etc.)

### Функциональные требования

#### 1. OAuth Configuration
Добавить в `MCPServerConfig`:
```kotlin
data class MCPServerConfig(
    // ... существующие поля
    val authType: AuthType = AuthType.NONE,
    val oauthConfig: OAuthConfig? = null
)

enum class AuthType {
    NONE,           // Без авторизации
    BEARER_TOKEN,   // Статичный Bearer token (текущая реализация)
    OAUTH2          // OAuth 2.0 flow
}

data class OAuthConfig(
    val clientId: String,
    val clientSecret: String? = null, // Опционально для PKCE
    val authUrl: String,
    val tokenUrl: String,
    val scopes: List<String> = emptyList(),
    val redirectUri: String = "http://localhost:8080/oauth/callback"
)
```

#### 2. OAuth Service
Создать `OAuthService` для управления OAuth flow:
- `startAuthFlow()` - запуск OAuth авторизации
- `handleCallback()` - обработка callback с authorization code
- `exchangeCodeForToken()` - обмен code на access token
- `refreshToken()` - обновление токена
- `revokeToken()` - отзыв токена

#### 3. Token Storage
Безопасное хранение OAuth токенов:
- Использовать IntelliJ `PasswordSafe` для хранения токенов
- Хранить access token, refresh token, expiry time
- Автоматическое обновление при истечении

#### 4. OAuth UI Flow
- Открытие браузера для авторизации
- Локальный HTTP сервер для приема callback
- Индикатор прогресса авторизации
- Обработка ошибок и отмены

#### 5. UI Changes
Обновить `MCPServerDialog`:
- Выбор типа авторизации (None / Bearer Token / OAuth)
- Поля для OAuth конфигурации (Client ID, Auth URL, etc.)
- Кнопка "Authorize" для запуска OAuth flow
- Индикатор статуса авторизации

### Технические детали

**Компоненты:**
1. **OAuthService** - управление OAuth flow
2. **OAuthTokenStorage** - безопасное хранение токенов
3. **OAuthCallbackServer** - локальный HTTP сервер для callback
4. **OAuthHttpClient** - HTTP клиент с автоматическим refresh токенов
5. **OAuthConfigDialog** - UI для настройки OAuth

**OAuth Flow:**
```
1. Пользователь нажимает "Authorize" в настройках
2. Открывается браузер с auth URL
3. Пользователь авторизуется на сервере
4. Сервер редиректит на localhost:8080/oauth/callback?code=...
5. Локальный сервер получает code
6. Обмениваем code на access_token
7. Сохраняем токены в PasswordSafe
8. Используем access_token для MCP запросов
9. Автоматически обновляем при истечении
```

**PKCE Support:**
- Генерация code_verifier и code_challenge
- Поддержка OAuth без client_secret (более безопасно для desktop приложений)

### Примеры конфигурации

#### GitHub OAuth
```json
{
  "name": "github-remote",
  "type": "HTTP",
  "url": "https://api.githubcopilot.com/mcp/",
  "authType": "OAUTH2",
  "oauthConfig": {
    "clientId": "your_client_id",
    "authUrl": "https://github.com/login/oauth/authorize",
    "tokenUrl": "https://github.com/login/oauth/access_token",
    "scopes": ["repo", "user"]
  },
  "enabled": true
}
```

#### Google OAuth
```json
{
  "name": "google-service",
  "type": "HTTP",
  "url": "https://api.google.com/mcp/",
  "authType": "OAUTH2",
  "oauthConfig": {
    "clientId": "your_client_id.apps.googleusercontent.com",
    "authUrl": "https://accounts.google.com/o/oauth2/v2/auth",
    "tokenUrl": "https://oauth2.googleapis.com/token",
    "scopes": ["https://www.googleapis.com/auth/drive.readonly"]
  },
  "enabled": true
}
```

### Зависимости
- ✅ Базовая MCP интеграция завершена
- ✅ HTTP client с headers поддержкой
- Требуется: OAuth 2.0 библиотека или собственная реализация

### Библиотеки
Рассмотреть использование:
- **ScribeJava** - OAuth библиотека для Java
- **OkHttp** - для HTTP запросов
- Или собственная реализация на основе Java HttpClient

### Риски
- Сложность реализации OAuth flow
- Необходимость регистрации OAuth приложения для каждого сервиса
- Управление локальным HTTP сервером для callback
- Безопасность хранения токенов

### Альтернативы
1. Использовать только локальные MCP серверы (текущее решение)
2. Попросить пользователя вручную получать токены
3. Интеграция с системным keychain/credential manager

### Критерии завершения
- ✅ OAuth 2.0 Authorization Code flow реализован
- ✅ PKCE поддержка для desktop приложений
- ✅ Безопасное хранение токенов в PasswordSafe
- ✅ Автоматический refresh токенов
- ✅ UI для OAuth конфигурации и авторизации
- ✅ Работает с Remote GitHub MCP Server
- ✅ Документация обновлена
- ✅ Unit тесты для OAuth компонентов

### Связанные задачи
- Интеграция MCP с Agent (потребует OAuth для некоторых серверов)

---

## Отслеживание использования токенов

**Приоритет:** Средний  
**Статус:** Запланировано  
**Оценка:** ~1 неделя

### Описание
Функция для сохранения и анализа истории использования токенов пользователем. Позволит отслеживать расходы на API и оптимизировать использование LLM.

### Цели
- Предоставить пользователю статистику использования токенов
- Помочь контролировать расходы на API
- Выявить паттерны использования для оптимизации

### Функциональные требования

#### 1. Сохранение истории
- Записывать каждый запрос к LLM с метаданными:
  - Timestamp
  - Количество токенов (input/output/total)
  - Провайдер и модель
  - ID сессии
  - Тип запроса (обычный/суммаризация)

#### 2. Хранилище данных
- Использовать локальную БД (SQLite или встроенное хранилище IntelliJ)
- Структура таблицы:
  ```sql
  CREATE TABLE token_usage (
      id INTEGER PRIMARY KEY,
      timestamp DATETIME,
      session_id TEXT,
      provider TEXT,
      model TEXT,
      input_tokens INTEGER,
      output_tokens INTEGER,
      total_tokens INTEGER,
      request_type TEXT,
      cost_estimate REAL
  )
  ```

#### 3. Статистика и отчёты
- **Дашборд использования:**
  - Токены за сегодня/неделю/месяц
  - График использования по времени
  - Распределение по типам запросов
  - Топ-5 самых затратных сессий

- **Экспорт данных:**
  - CSV для анализа в Excel
  - JSON для программной обработки

#### 4. Лимиты и уведомления
- Настройка дневных/месячных лимитов
- Уведомления при приближении к лимиту (80%, 90%, 100%)
- Возможность блокировки запросов при превышении

#### 5. Оценка стоимости
- Конфигурируемые цены за 1K токенов для разных провайдеров
- Автоматический расчёт примерной стоимости
- Отчёт по расходам за период

### UI/UX

Tool Window "Token Usage":
```
┌─────────────────────────────────────────┐
│  Token Usage Statistics          [⚙️]   │
├─────────────────────────────────────────┤
│                                         │
│  📊 Today:        12,450 tokens         │
│  💰 Est. Cost:    $0.25                 │
│                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│  📈 This Week:    85,320 tokens         │
│  📈 This Month:   342,150 tokens        │
│                                         │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  │
│                                         │
│  [View Details] [Export] [Settings]     │
│                                         │
└─────────────────────────────────────────┘
```

### Технические детали

**Компоненты:**
1. **TokenUsageTracker** - сервис для записи данных
2. **TokenUsageRepository** - работа с БД
3. **TokenUsageStatistics** - вычисление статистики
4. **TokenUsageToolWindow** - UI для отображения
5. **TokenUsageSettings** - настройки лимитов и цен

**Интеграция:**
- Автоматическая запись после каждого `LLMResponse`
- Хук в `ChatAgent.ask()` и других агентах
- Асинхронная запись для не блокирования UI

**Производительность:**
- Батчинг записей (каждые 5 секунд или 10 записей)
- Индексы на timestamp и session_id
- Автоматическая очистка старых данных (>6 месяцев)

### Зависимости
- ✅ Требует завершения работы над подсчётом токенов
- ✅ Требует стабильной работы `TokenUsage` модели

### Альтернативы
1. Использовать внешние сервисы для трекинга (LangSmith, Helicone)
2. Простой лог-файл вместо БД
3. Только базовая статистика без детальных отчётов

### Риски
- Увеличение размера хранилища при активном использовании
- Возможные проблемы с производительностью при большом объёме данных
- Необходимость GDPR-совместимости при хранении данных

### Связанные документы
- [Token Management](./features/token-management.md)

---

## Форматирование ответов

### Поддержка дополнительных форматов ответов
**Приоритет:** Средний  
**Статус:** Запланировано

Добавить поддержку дополнительных форматов для структурированных ответов агента:
- YAML
- TOML
- CSV
- Markdown Tables
- Custom DSL

**Зависимости:**
- Требует завершения задачи 2 (базовая поддержка JSON/XML/Text)

**Технические детали:**
- Добавить новые значения в enum `ResponseFormat`
- Реализовать соответствующие парсеры
- Добавить шаблоны промптов для каждого формата
- Покрыть тестами

---

## Интеграция MCP с Agent

**Приоритет:** Средний  
**Статус:** Запланировано  
**Оценка:** ~4 часа

### Описание
Интеграция MCP серверов с ChatAgent для автоматического вызова MCP tools агентом.

### Задачи
- [ ] Добавить в `ChatAgent` поддержку MCP tools
  - Метод `getAvailableTools()` - получение доступных MCP методов
  - Метод `callTool()` - вызов MCP метода из агента
- [ ] Добавить в системный промпт информацию о доступных MCP tools
- [ ] Реализовать автоматический вызов MCP методов агентом
- [ ] Добавить форматирование результатов MCP вызовов

### Зависимости
- Требует завершения базовой интеграции MCP (задачи 1-6 из roadmap 05)
- Требует работающего `MCPConnectionManager`

### Технические детали
- Agent должен получать список доступных tools из `MCPConnectionManager`
- При необходимости вызова tool, agent делегирует вызов в MCP client
- Результаты вызова форматируются и добавляются в контекст
- Поддержка tool calling в системном промпте

---

**Дата создания:** 2025-10-02  
**Последнее обновление:** 2025-10-13  
**Последнее добавление:** OAuth поддержка для MCP серверов (2025-10-13)

---

## Доработка инструмента деплоя (deploy-pugin CLI)

**Приоритет:** Высокий  
**Статус:** Запланировано  
**Оценка:** ~2–3 дня

### Описание
Завершить и укрепить полноценный цикл публикации плагинов через CLI `deploy-pugin`:
- Надёжная работа с секретами (API-ключи, folder_id, SSH), безопасная загрузка `.env`.
- Корректное формирование `updatePlugins.xml` с мёрджем без потери других плагинов.
- Правильные URL до артефактов (учёт `repository.url` как директории, относительный путь от `xml_path` к `deploy_path`).
- Автоматическое заполнение метаданных плагина из ZIP `META-INF/plugin.xml` (name, vendor, idea-version, description) — только если поля отсутствуют.
- Устойчивость к временным окнам бэкапа (`.bak`), парсинг git тегов.

### Задачи
- [ ] Секреты и конфигурация
  - [ ] Единая схема загрузки `.env` (локальный и в `plugin-repository/.env`), обработка пробелов, плейсхолдеров `${...}`.
  - [ ] Валидация обязательных переменных: `YANDEX_API_KEY`, `YANDEX_FOLDER_ID`, `SSH_*`.
- [ ] Публикация/деплой
  - [ ] Атомарное обновление `updatePlugins.xml` с бэкапом `*.bak` и чтением исходника из `.bak` при необходимости.
  - [ ] Мёрдж: сохранять все существующие плагины, заменять/добавлять только текущий `id`.
  - [ ] Формирование корректных `url` на основе `repository.url`, `xml_path`, `deploy_path`.
  - [ ] Извлечение метаданных из ZIP `META-INF/plugin.xml` и заполнение отсутствующих полей.
- [ ] Интеграция LLM (опционально)
  - [ ] Генерация release notes и описаний плагина при включённом AI, корректный `model_uri` для YandexGPT.
- [ ] Надёжность
  - [ ] Устойчивый парсинг git тегов (`git show -s --no-patch --pretty=%H|%s|%an|%cI`).
  - [ ] Логи обрезать по символам (UTF‑8 safe) без паник.
- [ ] Тесты и документация
  - [ ] Интеграционный тест мёрджа `updatePlugins.xml` (с демо‑плагином в репозитории).
  - [ ] Тест корректности URL при разных `deploy_path`/`xml_path`.
  - [ ] Моки для LLM и тест корректности `model_uri`.
  - [ ] Обновить README/Docs: конфигурация, переменные окружения, сценарии `publish`.

### Критерии завершения
- ✅ При деплое не удаляются записи о других плагинах; добавляется/обновляется запись только текущего `id`.
- ✅ URL до артефактов корректен и ведёт на файл в `deploy_path`.
- ✅ `updatePlugins.xml` при повреждении/бэкапе читается через `.bak`, мёрдж работает стабильно.
- ✅ Метаданные подтягиваются из ZIP при их отсутствии.
- ✅ Секреты безопасно подхватываются из `.env`, плейсхолдеры не просачиваются в конфиг/логи.
- ✅ Документация и тесты покрывают критические сценарии.
