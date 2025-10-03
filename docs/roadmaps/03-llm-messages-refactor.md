---
description: Рефакторинг передачи сообщений LLM через параметры и формирование messages в провайдере
---

- [x] Проанализировать текущий интерфейс `LLMProvider` и реализацию `YandexGPTProvider`
- [x] Изменить сигнатуру `LLMProvider.sendRequest(systemPrompt, userMessage, assistantHistory, parameters)`
- [x] Адаптировать `YandexGPTProvider` для сборки `messages` со схемой: `system` + `assistant*` + `user`
- [x] Обновить `ChatAgent` для передачи параметров вместо текстового промпта
- [x] Обновить юнит- и интеграционные тесты на новую сигнатуру
- [x] Добавить отдельные юнит-тесты на сбор `assistantHistory` и расширение системного промпта схемой
- [x] Прогнать все тесты
- [x] Сделать коммит логического блока изменений
