# Тестирование EnhancedChatAgent в плагине

## Обзор

`EnhancedChatAgent` автоматически определяет сложность задачи и выбирает подходящий способ обработки:
- **Простые вопросы** → базовый `ChatAgent` (быстрый ответ)
- **Сложные задачи** → `EnhancedAgentOrchestrator` (многошаговое выполнение)

## Способы тестирования

### 1. Через изменение AgentFactory (рекомендуется)

Замените в `ChatService.kt` создание агента:

```kotlin
// Было:
private var agent: Agent = AgentFactory.createChatAgent()

// Стало:
private var agent: Agent = AgentFactory.createEnhancedChatAgent()
```

**Файл:** `src/main/kotlin/ru/marslab/ide/ride/service/ChatService.kt`  
**Строки:** 49, 619

### 2. Временное переключение для тестирования

Добавьте настройку в `PluginSettings`:

```kotlin
var useEnhancedAgent: Boolean = false
```

Затем в `AgentFactory.createChatAgent()`:

```kotlin
fun createChatAgent(): Agent {
    val settings = service<PluginSettings>()
    
    // Если включен enhanced режим
    if (settings.useEnhancedAgent) {
        return createEnhancedChatAgent()
    }
    
    // Обычный режим
    // ... существующий код
}
```

## Тестовые сценарии

### Простые вопросы (должны использовать ChatAgent)

Эти запросы НЕ должны запускать оркестратор:

1. **"Что такое Kotlin?"**
   - Ожидается: быстрый ответ без "Выполненные шаги"
   
2. **"Объясни принцип SOLID"**
   - Ожидается: прямой ответ от ChatAgent

3. **"Как работает lateinit?"**
   - Ожидается: простое объяснение

### Сложные задачи (должны использовать Orchestrator)

Эти запросы ДОЛЖНЫ запускать оркестратор:

1. **"Проанализируй весь проект и найди все баги в коде"**
   - Ожидается: 
     - Сообщение "## Результат выполнения задачи"
     - Раздел "### Выполненные шаги:"
     - Несколько шагов выполнения

2. **"Проверь качество кода в проекте и создай отчёт"**
   - Ожидается: многошаговое выполнение с прогрессом

3. **"Найди ошибки в файле Main.kt и предложи исправления"**
   - Ожидается: использование оркестратора

4. **"Оптимизируй производительность кода в пакете services"**
   - Ожидается: сложный анализ через оркестратор

## Критерии определения сложности

`EnhancedChatAgent` анализирует запрос по следующим критериям:

### Ключевые слова сложных задач:
- "проанализируй", "найди баги", "оптимизируй"
- "рефактор", "создай отчет", "проверь качество"
- "архитектур", "сканируй", "исследуй", "улучши"

### Упоминание файлов/проекта:
- "файл", "проект", "код"

### Длина запроса:
- Запросы > 100 символов с упоминанием файлов считаются сложными

### Определение типа задачи:
- **BUG_FIX**: содержит "баг", "ошибк"
- **CODE_ANALYSIS**: содержит "качеств", "code smell"
- **ARCHITECTURE_ANALYSIS**: содержит "архитектур"
- **REFACTORING**: содержит "рефактор"

## Проверка в IDE

### Шаг 1: Запустите плагин

```bash
./gradlew runIde
```

### Шаг 2: Откройте чат

- Перейдите в `Tools → Ride Chat`
- Или используйте боковую панель

### Шаг 3: Тестируйте запросы

#### Простой вопрос:
```
Что такое корутины в Kotlin?
```

**Ожидаемый результат:**
- Быстрый ответ
- НЕТ раздела "Выполненные шаги"
- НЕТ упоминания оркестратора

#### Сложная задача:
```
Проанализируй весь проект и найди все потенциальные баги в коде
```

**Ожидаемый результат:**
```markdown
## Результат выполнения задачи

### Выполненные шаги:
- 📋 Планирование: Создан план анализа проекта
- 🔍 Задача 1: Сканирование проекта
- 🔍 Задача 2: Поиск багов
- ✅ Все задачи выполнены: Анализ завершён

[Результаты анализа...]
```

### Шаг 4: Проверьте логи

Откройте `Help → Show Log in Files` и найдите:

```
EnhancedChatAgent processing request
Simple task, using base ChatAgent
```

или

```
EnhancedChatAgent processing request
Complex task detected, using orchestrator
```

## Отладка

### Включите debug логирование

В `EnhancedChatAgent.kt` добавьте:

```kotlin
override suspend fun ask(request: AgentRequest): AgentResponse {
    logger.info("EnhancedChatAgent processing request: ${request.request}")
    
    val taskComplexity = analyzeTaskComplexity(request.request, request.context)
    logger.info("Task complexity: isComplex=${taskComplexity.isComplex}, " +
                "type=${taskComplexity.taskType}, " +
                "steps=${taskComplexity.estimatedSteps}")
    
    // ... остальной код
}
```

### Проверьте вызовы оркестратора

Добавьте логирование в `useOrchestrator`:

```kotlin
private suspend fun useOrchestrator(request: AgentRequest): AgentResponse {
    logger.info("Using orchestrator for complex task")
    val steps = mutableListOf<String>()
    // ...
}
```

## Возобновление планов

Для тестирования возобновления планов:

### 1. План должен запросить ввод пользователя

```kotlin
val context = ChatContext(
    project = project,
    additionalContext = mapOf("resume_plan_id" to "plan-123")
)
```

### 2. Отправьте ответ пользователя

```
Да, продолжай
```

**Ожидаемый результат:**
```markdown
## ✅ План возобновлён

### Выполненные шаги:
- 📋 Планирование: План восстановлен
- 🔍 Задача 3: Продолжение выполнения
- ✅ Все задачи выполнены

[Результаты...]
```

## Известные ограничения

1. **Тесты с моками**: 2 из 9 тестов падают из-за проблем с моками `Project`
2. **UI интеграция**: Пока нет UI элементов для интерактивного ввода
3. **Прогресс**: Отображение прогресса в чате не реализовано

## Следующие шаги

После успешного тестирования:

1. Замените `createChatAgent()` на `createEnhancedChatAgent()` в `ChatService`
2. Добавьте UI элементы для интерактивного ввода
3. Реализуйте отображение прогресса
4. Добавьте retry/loop механизмы
5. Интегрируйте с UncertaintyAnalyzer

## Полезные команды

```bash
# Запуск всех тестов EnhancedChatAgent
./gradlew test --tests "ru.marslab.ide.ride.agent.impl.EnhancedChatAgentTest"

# Запуск плагина в IDE
./gradlew runIde

# Сборка плагина
./gradlew buildPlugin

# Просмотр логов
tail -f build/idea-sandbox/system/log/idea.log | grep EnhancedChatAgent
```
