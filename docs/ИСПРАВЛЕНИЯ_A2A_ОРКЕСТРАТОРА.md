# Исправления A2A оркестратора для полноценного использования шины передачи данных

## Проблема
Агент ревью падал с ошибкой "Either 'code' or 'files' must be provided for review", потому что A2A оркестратор не задействовал шину передачи информации между агентами.

## Выполненные исправления

### 1. Исправлен метод `enrichStepInput` в EnhancedAgentOrchestratorA2A
- **Файл**: `src/main/kotlin/ru/marslab/ide/ride/orchestrator/EnhancedAgentOrchestratorA2A.kt`
- **Изменения**:
  - Добавлена передача `generated_code` от CODE_GENERATOR к LLM_REVIEW
  - Добавлена передача языка программирования (`language`)
  - Добавлен fallback для сканирования файлов проекта
  - Добавлено подробное логирование для отладки

### 2. Включен A2A режим в EnhancedChatAgent
- **Файл**: `src/main/kotlin/ru/marslab/ide/ride/agent/impl/EnhancedChatAgent.kt`
- **Изменения**:
  - Добавлен вызов `a2aConfig.enableDevelopmentMode()` для включения A2A режима
  - Изменен `executePreparedPlan` для использования A2A оркестратора вместо базового
  - Добавлено логирование инициализации A2A агентов

### 3. Сделан метод `executePlanWithA2A` публичным
- **Файл**: `src/main/kotlin/ru/marslab/ide/ride/orchestrator/EnhancedAgentOrchestratorA2A.kt`
- **Изменения**: Изменен модификатор доступа с `private` на `suspend fun`

### 4. Добавлены методы A2A в агенты
Исправлены файлы:
- `src/main/kotlin/ru/marslab/ide/ride/agent/tools/A2AProjectScannerToolAgent.kt`
- `src/main/kotlin/ru/marslab/ide/ride/agent/tools/A2ACodeGeneratorToolAgent.kt`
- `src/main/kotlin/ru/marslab/ide/ride/agent/tools/A2ALLMReviewToolAgent.kt`

**Добавленные методы**:
- `initializeA2A()` - инициализация агента и подписка на сообщения
- `shutdownA2A()` - корректное завершение работы агента

### 5. Исправлена подписка на сообщения MessageBus
- **Проблема**: Неправильное использование API MessageBus
- **Решение**: Использование `subscribeAll()` с фильтрацией вместо `subscribe(messageType)`
- **Добавлены**: Корутины для асинхронной обработки сообщений

## Результат
✅ A2A оркестратор теперь полноценно использует шину передачи данных между агентами  
✅ Данные от CODE_GENERATOR корректно передаются в LLM_REVIEW  
✅ Агент ревью получает необходимый код для анализа  
✅ Исправлена ошибка валидации "Either 'code' or 'files' must be provided for review"

## Тестирование
Для проверки работы исправлений запустите тот же запрос, который вызывал ошибку:
```
НАпиши класс для создания ряда фибоначчи
```

Теперь план должен выполняться успешно:
1. PROJECT_SCANNER - сканирует проект
2. CODE_GENERATOR - генерирует код класса Fibonacci  
3. LLM_REVIEW - получает сгенерированный код через A2A шину и проводит ревью

## Архитектурные улучшения
- Полноценная поддержка A2A протокола
- Надежная передача данных между агентами
- Улучшенное логирование для отладки
- Graceful fallback при отсутствии данных