# 🚀 Ride - AI-ассистент для IntelliJ IDEA

<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Ride Logo" width="120" height="120">
</p>

<p align="center">
  <a href="#"><img src="https://img.shields.io/badge/build-passing-brightgreen" alt="Build Status"></a>
  <a href="#"><img src="https://img.shields.io/badge/version-1.0--SNAPSHOT-blue" alt="Version"></a>
  <a href="#"><img src="https://img.shields.io/badge/platform-IntelliJ%20IDEA-orange" alt="Platform"></a>
  <a href="#"><img src="https://img.shields.io/badge/kotlin-2.1.0-purple" alt="Kotlin"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="License"></a>
</p>

<p align="center">
  <strong>AI-ассистент для разработчиков с интеграцией Yandex GPT</strong><br>
  Помогает с вопросами о коде, отладке и разработке прямо в IDE
</p>

<p align="center">
  <a href="#-возможности">Возможности</a> •
  <a href="#-установка">Установка</a> •
  <a href="#️-настройка">Настройка</a> •
  <a href="#-использование">Использование</a> •
  <a href="#️-архитектура">Архитектура</a> •
  <a href="#-вклад-в-проект">Вклад</a>
</p>

---

## 📸 Демонстрация

```
┌─────────────────────────────────────────────┐
│  Ride Chat                              [×] │
├─────────────────────────────────────────────┤
│                                             │
│  👤 Вы:                                     │
│  Объясни, что такое корутины в Kotlin       │
│                                             │
│  🤖 Ассистент:                              │
│  Корутины в Kotlin - это легковесные        │
│  потоки, которые позволяют писать           │
│  асинхронный код в последовательном         │
│  стиле...                                   │
│                                             │
├─────────────────────────────────────────────┤
│  [Введите ваш вопрос...]        [Отправить] │
│                                  [Очистить] │
└─────────────────────────────────────────────┘
```

## ✨ Возможности

- 💬 **Интерактивный чат** с AI-ассистентом прямо в IDE
- 🤖 **Интеграция с Yandex GPT** для качественных ответов
- 📝 **История диалогов** в рамках сессии
- 🔒 **Безопасное хранение** API ключей через PasswordSafe
- ⚡ **Асинхронная обработка** запросов без блокировки UI
- 🎨 **Удобный интерфейс** с Tool Window
- 🏗️ **Модульная архитектура** с возможностью расширения
- 🧪 **Покрытие тестами** всех ключевых компонентов

## 🎯 Быстрый старт

1. **Установите плагин** (см. [Установка](#-установка))
2. **Получите API ключ** от [Yandex Cloud](https://console.cloud.yandex.ru)
3. **Настройте плагин** в `Settings → Tools → Ride`
4. **Откройте чат** через `View → Tool Windows → Ride Chat`
5. **Задайте вопрос** и получите ответ от AI! 🚀

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

## 🛠️ Технологический стек

| Категория       | Технология            | Версия     |
|-----------------|-----------------------|------------|
| **Язык**        | Kotlin                | 2.1.0      |
| **Platform**    | IntelliJ Platform     | 2025.1.4.1 |
| **HTTP Client** | Java HttpClient       | JDK 11+    |
| **JSON**        | kotlinx.serialization | 1.6.2      |
| **Async**       | Kotlin Coroutines     | Platform   |
| **Testing**     | JUnit + MockK         | Latest     |
| **Build**       | Gradle                | 8.14.3     |

## 📊 Статус проекта

- ✅ **Фаза 1**: Базовая функциональность - **Завершена**
- 🔄 **Фаза 2**: Расширенные возможности - В планах
- 📝 **Документация**: Полная
- 🧪 **Тесты**: Покрытие основных компонентов
- 🐛 **Известные проблемы**: Нет критических

## 📄 Лицензия

Этот проект распространяется под лицензией MIT. См. файл [LICENSE](LICENSE) для деталей.

## 🙏 Благодарности

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html) - за отличную документацию
- [Yandex Cloud](https://cloud.yandex.ru/) - за Yandex GPT API
- [Kotlin](https://kotlinlang.org/) - за прекрасный язык программирования

## 📧 Контакты и поддержка

- 🐛 **Нашли баг?** [Создайте issue](https://github.com/yourusername/ride/issues)
- 💡 **Есть идея?** [Обсудите в Discussions](https://github.com/yourusername/ride/discussions)
- 📧 **Email**: your.email@example.com
- 🌐 **GitHub**: [@yourusername](https://github.com/yourusername)

## ⭐ Поддержите проект

Если вам понравился Ride, поставьте ⭐ на GitHub!

---

<p align="center">
  <strong>Сделано с ❤️ для разработчиков</strong><br>
  <sub>Ride - ваш AI-помощник в мире кода</sub>
</p>
