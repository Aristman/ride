# Роадмап: Реализация чата с клиентом (Фаза 1)

## Цель
Создать базовый функционирующий чат с интеграцией Yandex GPT

## Этапы реализации

### 1. Базовая инфраструктура ✅
- [x] Создать модели данных (Message, ChatContext, AgentResponse, LLMParameters, LLMResponse)
- [x] Создать enum MessageRole
- [x] Добавить зависимости в build.gradle.kts (Ktor Client, kotlinx.serialization)

### 2. Integration Layer - Yandex GPT Provider ✅
- [x] Создать интерфейс LLMProvider
- [x] Создать модели для Yandex GPT API (YandexGPTRequest, YandexGPTResponse)
- [x] Реализовать YandexGPTProvider с HTTP клиентом
- [x] Добавить обработку ошибок и retry логику
- [ ] Покрыть unit тестами

### 3. Agent Layer ✅
- [x] Создать интерфейс Agent
- [x] Реализовать ChatAgent с dependency injection LLMProvider
- [x] Реализовать AgentFactory
- [ ] Покрыть unit тестами

### 4. Service Layer ✅
- [x] Создать MessageHistory для хранения сообщений
- [x] Создать ChatService как Application Service
- [x] Интегрировать Agent в ChatService
- [ ] Покрыть unit тестами

### 5. Configuration Layer ✅
- [x] Создать PluginSettings (Persistent State Component)
- [x] Реализовать хранение API ключа через PasswordSafe
- [x] Создать SettingsConfigurable для UI настроек
- [x] Добавить валидацию настроек

### 6. UI Layer ✅
- [x] Создать ChatToolWindowFactory
- [x] Реализовать ChatPanel с основными компонентами
- [x] Добавить MessageRenderer для отображения сообщений (встроено в ChatPanel)
- [x] Создать InputPanel для ввода текста (встроено в ChatPanel)
- [x] Интегрировать с ChatService
- [ ] Создать инструментальные UI тесты

### 7. Конфигурация плагина ✅
- [x] Обновить plugin.xml (Tool Window, Settings, Actions)
- [ ] Добавить иконки
- [x] Настроить метаданные плагина

### 8. Интеграция и тестирование
- [ ] Интеграционные тесты для полного flow
- [ ] Ручное тестирование в IDE
- [ ] Проверка обработки ошибок
- [ ] Тестирование с реальным Yandex GPT API

### 9. Документация и финализация
- [ ] Обновить README с инструкциями по настройке
- [ ] Добавить примеры использования
- [ ] Создать CHANGELOG
- [ ] Финальный коммит

## Критерии готовности

✅ Пользователь может открыть Tool Window с чатом  
✅ Пользователь может ввести сообщение и отправить  
✅ Сообщение отправляется в Yandex GPT  
✅ Ответ отображается в чате  
✅ История сообщений сохраняется в рамках сессии  
✅ Настройки API ключа работают корректно  
✅ Все тесты проходят успешно  
✅ Обработка ошибок работает корректно

## Примечания

- Используем Kotlin Coroutines для асинхронности
- Все сетевые запросы через Ktor Client
- UI обновления только в EDT
- API ключи только в PasswordSafe
- Логирование через IntelliJ Logger

---

**Дата создания:** 2025-10-01  
**Статус:** В работе  
**Текущий этап:** Начало реализации
