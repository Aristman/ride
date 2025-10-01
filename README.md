# Ride - AI-ассистент для IntelliJ IDEA

[![Build](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Version](https://img.shields.io/badge/version-1.0--SNAPSHOT-blue)]()
[![Platform](https://img.shields.io/badge/platform-IntelliJ%20IDEA-orange)]()

AI-ассистент для разработчиков с интеграцией Yandex GPT. Помогает с вопросами о коде, отладке и разработке прямо в IDE.

## ✨ Возможности

- 💬 **Интерактивный чат** с AI-ассистентом прямо в IDE
- 🤖 **Интеграция с Yandex GPT** для качественных ответов
- 📝 **История диалогов** в рамках сессии
- 🔒 **Безопасное хранение** API ключей через PasswordSafe
- ⚡ **Асинхронная обработка** запросов без блокировки UI
- 🎨 **Удобный интерфейс** с Tool Window

## 📋 Требования

- **IntelliJ IDEA** 2025.1.4.1 или выше
- **JDK** 21
- **Yandex Cloud** аккаунт с доступом к Yandex GPT API

## 🚀 Установка

### Из исходников

1. Клонируйте репозиторий:
```bash
git clone https://github.com/yourusername/ride.git
cd ride
```

2. Соберите плагин:
```bash
./gradlew buildPlugin
```

3. Установите плагин в IntelliJ IDEA:
   - `File → Settings → Plugins → ⚙️ → Install Plugin from Disk...`
   - Выберите файл `build/distributions/ride-1.0-SNAPSHOT.zip`

### Для разработки

Запустите плагин в тестовой IDE:
```bash
./gradlew runIde
```

## ⚙️ Настройка

### 1. Получите API ключ Yandex GPT

1. Перейдите в [Yandex Cloud Console](https://console.cloud.yandex.ru)
2. Создайте сервисный аккаунт
3. Создайте API ключ для сервисного аккаунта
4. Скопируйте **API ключ** и **Folder ID** из настроек проекта

### 2. Настройте плагин

1. Откройте **Settings → Tools → Ride**
2. Введите:
   - **API Key** - ваш API ключ от Yandex Cloud
   - **Folder ID** - ID папки из Yandex Cloud
3. (Опционально) Настройте:
   - **System Prompt** - системный промпт для агента
   - **Temperature** - температура генерации (0.0 - 1.0)
   - **Max Tokens** - максимальное количество токенов в ответе

4. Нажмите **Apply** и **OK**

## 📖 Использование

### Открытие чата

1. Откройте Tool Window **"Ride Chat"** (обычно справа в IDE)
2. Или используйте: `View → Tool Windows → Ride Chat`

### Отправка сообщений

1. Введите ваш вопрос в поле ввода внизу окна чата
2. Нажмите **Enter** или кнопку **"Отправить"**
3. Дождитесь ответа от AI-ассистента

### Примеры запросов

```
👤 Вы: Объясни, что такое корутины в Kotlin

🤖 Ассистент: Корутины в Kotlin - это легковесные потоки...

👤 Вы: Как правильно обработать ошибки в корутинах?

🤖 Ассистент: Для обработки ошибок в корутинах используйте...
```

### Очистка истории

Нажмите кнопку **"Очистить"** для удаления истории чата.

## 🏗️ Архитектура

Плагин построен на модульной архитектуре с четким разделением ответственности:

```
┌─────────────────────────────────────┐
│         UI Layer                    │  ChatPanel, ChatToolWindow
├─────────────────────────────────────┤
│         Service Layer               │  ChatService, MessageHistory
├─────────────────────────────────────┤
│         Agent Layer                 │  Agent (interface), ChatAgent
├─────────────────────────────────────┤
│         Integration Layer           │  LLMProvider, YandexGPTProvider
├─────────────────────────────────────┤
│         Configuration Layer         │  PluginSettings
└─────────────────────────────────────┘
```

### Ключевые принципы

- ✅ **Dependency Inversion** - зависимости через интерфейсы
- ✅ **Single Responsibility** - каждый компонент имеет одну задачу
- ✅ **Open/Closed** - открыто для расширения, закрыто для модификации
- ✅ **Agent не привязан к LLM** - легко добавить новые провайдеры

Подробнее: [docs/architecture.md](docs/architecture.md)

## 🧪 Тестирование

### Запуск unit тестов

```bash
./gradlew test
```

### Покрытие тестами

- ✅ Модели данных (Message, ChatContext, LLMParameters)
- ✅ MessageHistory
- ✅ ChatAgent с mock провайдером
- ✅ Валидация параметров

## 🔧 Разработка

### Технологический стек

- **Язык**: Kotlin 2.1.0
- **Platform**: IntelliJ Platform 2025.1.4.1
- **HTTP Client**: Java HttpClient (JDK 11+)
- **JSON**: kotlinx.serialization
- **Async**: Kotlin Coroutines (из IntelliJ Platform)
- **Testing**: JUnit, MockK

### Структура проекта

```
ride/
├── src/main/kotlin/ru/marslab/ide/ride/
│   ├── agent/              # Агенты для обработки запросов
│   ├── integration/llm/    # Интеграция с LLM провайдерами
│   ├── model/              # Модели данных
│   ├── service/            # Бизнес-логика
│   ├── settings/           # Настройки плагина
│   └── ui/                 # Пользовательский интерфейс
├── src/test/kotlin/        # Unit тесты
└── docs/                   # Документация
```

### Добавление нового LLM провайдера

1. Реализуйте интерфейс `LLMProvider`:
```kotlin
class OpenAIProvider : LLMProvider {
    override suspend fun sendRequest(prompt: String, parameters: LLMParameters): LLMResponse {
        // Ваша реализация
    }
}
```

2. Обновите `AgentFactory` для создания агента с новым провайдером

3. Агент автоматически будет работать с новым провайдером!

## 🐛 Известные проблемы

### Конфликт корутин

**Проблема**: Не используйте Ktor Client - он вызывает конфликт корутин с IntelliJ Platform.

**Решение**: Используйте Java HttpClient или `com.intellij.util.io.HttpRequests`.

Подробнее в [docs/api-integration.md](docs/api-integration.md)

## 📝 Changelog

См. [CHANGELOG.md](CHANGELOG.md) для истории изменений.

## 🤝 Вклад в проект

Мы приветствуем вклад в проект! Пожалуйста:

1. Форкните репозиторий
2. Создайте ветку для вашей фичи (`git checkout -b feature/amazing-feature`)
3. Закоммитьте изменения (`git commit -m 'feat: add amazing feature'`)
4. Запушьте в ветку (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

## 📄 Лицензия

Этот проект распространяется под лицензией MIT. См. файл [LICENSE](LICENSE) для деталей.

## 🙏 Благодарности

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Yandex Cloud](https://cloud.yandex.ru/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

## 📧 Контакты

- **Автор**: MarsLab
- **Email**: your.email@example.com
- **GitHub**: [https://github.com/yourusername/ride](https://github.com/yourusername/ride)

---

**Сделано с ❤️ для разработчиков**
