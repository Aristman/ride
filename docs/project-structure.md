# Структура проекта Ride

## Организация пакетов

```
src/main/kotlin/ru/marslab/ide/ride/
│
├── agent/                          # Слой агентов
│   ├── Agent.kt                    # Интерфейс агента
│   ├── AgentFactory.kt             # Фабрика агентов
│   └── impl/
│       └── ChatAgent.kt            # Универсальная реализация агента для чата
│
├── integration/                    # Слой интеграции с внешними сервисами
│   └── llm/
│       ├── LLMProvider.kt          # Интерфейс LLM провайдера
│       ├── LLMProviderFactory.kt   # Фабрика провайдеров
│       └── impl/
│           └── YandexGPTProvider.kt # Реализация для Yandex GPT API
│
├── model/                          # Модели данных
│   ├── Message.kt                  # Модель сообщения
│   ├── MessageRole.kt              # Enum для роли сообщения
│   ├── ChatContext.kt              # Контекст чата
│   ├── AgentResponse.kt            # Ответ агента
│   ├── LLMParameters.kt            # Параметры для LLM
│   └── LLMResponse.kt              # Ответ от LLM провайдера
│
├── service/                        # Бизнес-логика
│   ├── ChatService.kt              # Центральный сервис чата
│   └── MessageHistory.kt           # Управление историей сообщений
│
├── settings/                       # Настройки плагина
│   ├── PluginSettings.kt           # Хранение настроек
│   ├── PluginSettingsState.kt      # State для персистентности
│   └── SettingsConfigurable.kt     # UI для настроек
│
├── ui/                             # Пользовательский интерфейс
│   ├── ChatToolWindowFactory.kt    # Фабрика для Tool Window
│   ├── ChatPanel.kt                # Главная панель чата
│   └── components/
│       ├── MessageRenderer.kt      # Рендеринг сообщений
│       ├── InputPanel.kt           # Панель ввода
│       └── ChatHistoryPanel.kt     # Панель истории
│
└── util/                           # Утилиты
    ├── CoroutineUtils.kt           # Утилиты для корутин
    ├── NotificationUtils.kt        # Утилиты для уведомлений
    └── ValidationUtils.kt          # Валидация данных
```

## Ресурсы

```
src/main/resources/
│
├── META-INF/
│   ├── plugin.xml                  # Конфигурация плагина
│   └── pluginIcon.svg              # Иконка плагина
│
└── messages/
    └── RideBundle.properties       # Локализация (опционально)
```

## Тесты

```
src/test/kotlin/ru/marslab/ide/ride/
│
├── agent/
│   └── YandexGPTAgentTest.kt
│
├── integration/
│   └── llm/
│       └── YandexGPTProviderTest.kt
│
├── service/
│   ├── ChatServiceTest.kt
│   └── MessageHistoryTest.kt
│
└── model/
    └── MessageTest.kt
```

## Конфигурационные файлы

```
ride/
├── build.gradle.kts                # Gradle конфигурация
├── settings.gradle.kts             # Gradle настройки
├── gradle.properties               # Gradle свойства
├── .gitignore                      # Git ignore
└── docs/                           # Документация
    ├── architecture.md             # Архитектура (этот документ)
    ├── project-structure.md        # Структура проекта
    ├── api-integration.md          # Документация по интеграции с API
    └── development-guide.md        # Руководство для разработчиков
```

## Описание ключевых файлов

### Agent Layer

**Agent.kt** - Базовый интерфейс для всех агентов
- Определяет контракт для обработки запросов
- Позволяет легко добавлять новые типы агентов

**ChatAgent.kt** - Универсальная реализация агента для чата
- НЕ привязан к конкретному LLM провайдеру
- Получает LLMProvider через конструктор (Dependency Injection)
- Делегирует запросы в настроенный провайдер
- Обрабатывает контекст и формирует промпты

### Integration Layer

**LLMProvider.kt** - Абстракция для работы с LLM
- Унифицированный интерфейс для разных провайдеров
- Упрощает переключение между провайдерами

**YandexGPTProvider.kt** - HTTP клиент для Yandex GPT
- Аутентификация через API ключ
- Обработка ошибок и retry логика
- Rate limiting

### Service Layer

**ChatService.kt** - Координатор чата
- Application Service (singleton)
- Управляет взаимодействием между UI и Agent
- Обрабатывает асинхронные операции

**MessageHistory.kt** - Хранилище сообщений
- In-memory хранение истории
- API для добавления/получения сообщений
- Возможность ограничения размера истории

### UI Layer

**ChatToolWindowFactory.kt** - Регистрация Tool Window
- Создает Tool Window в IDE
- Определяет расположение и иконку

**ChatPanel.kt** - Главный UI компонент
- Содержит все элементы интерфейса чата
- Обрабатывает пользовательский ввод
- Отображает сообщения

### Settings Layer

**PluginSettings.kt** - Настройки плагина
- Persistent State Component
- Хранит API ключ, параметры модели
- Автоматическое сохранение

**SettingsConfigurable.kt** - UI настроек
- Интегрируется в IDE Settings
- Валидация API ключа
- Настройка параметров генерации

### Model Layer

**Message.kt** - Модель сообщения
- Immutable data class
- Содержит всю информацию о сообщении

**ChatContext.kt** - Контекст для агента
- Передает информацию о проекте
- История предыдущих сообщений
- Текущий файл и выделенный текст

## Зависимости между слоями

```
UI Layer
   ↓
Service Layer
   ↓
Agent Layer
   ↓
Integration Layer
   ↓
External APIs
```

**Правила зависимостей**:
- Верхние слои зависят от нижних
- Нижние слои НЕ знают о верхних
- Зависимости через интерфейсы
- Configuration Layer доступен всем слоям

## Naming Conventions

- **Интерфейсы**: без префикса `I` (например, `Agent`, не `IAgent`)
- **Реализации**: описательные имена (например, `YandexGPTAgent`)
- **Фабрики**: суффикс `Factory` (например, `AgentFactory`)
- **Сервисы**: суффикс `Service` (например, `ChatService`)
- **UI компоненты**: описательные имена с суффиксом типа (`ChatPanel`, `ChatToolWindowFactory`)
- **Модели**: простые существительные (например, `Message`, `ChatContext`)

## Соглашения по коду

1. **Kotlin стиль**: следуем официальному Kotlin coding conventions
2. **Coroutines**: используем для всех асинхронных операций
3. **Null safety**: избегаем nullable типов где возможно
4. **Immutability**: предпочитаем `val` вместо `var`, data classes
5. **Dependency Injection**: через IntelliJ Platform Services
