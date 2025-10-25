# 📖 Руководство по использованию Project Scanner Tool Agent

## 🎯 Обзор

Project Scanner Tool Agent - это мощный инструмент для сканирования файловой системы проектов с интеллектуальной фильтрацией и аналитикой. Он предоставляет структурированную информацию о файлах, директориях и метаданных проекта.

## 🚀 Быстрый старт

### Базовое использование

```kotlin
// Создание агента
val scanner = ProjectScannerToolAgent()

// Создание шага сканирования
val scanStep = ToolPlanStep(
    description = "Сканирование проекта",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", "/path/to/your/project")
        .set("include_patterns", listOf("**/*.kt", "**/*.java"))
)

// Выполнение сканирования
val context = ExecutionContext(projectPath = "/path/to/your/project")
val result = scanner.executeStep(scanStep, context)

// Получение результатов
if (result.success) {
    val json = result.output.get<Map<String, Any>>("json")
    val files = json?.get("files") as? List<String>
    val stats = json?.get("stats") as? Map<String, Any>

    println("Найдено файлов: ${files?.size}")
    println("Статистика: $stats")
}
```

## ⚙️ Конфигурация и параметры

### Основные параметры

| Параметр | Тип | Описание | Пример |
|----------|-----|----------|--------|
| `project_path` | String | Путь к проекту для сканирования | `/home/user/my-project` |
| `include_patterns` | List<String> | Паттерны включения файлов | `["**/*.kt", "**/*.java"]` |
| `exclude_patterns` | List<String> | Паттерны исключения файлов | `["build/**", "node_modules/**"]` |
| `max_directory_depth` | Int | Максимальная глубина сканирования | `10` |
| `page` | Int | Номер страницы для пагинации | `1` |
| `batch_size` | Int | Размер страницы | `500` |
| `since_ts` | Long | Временная метка для поиска изменений | `1698220800000` |

### Продвинутая конфигурация

```kotlin
val advancedScanStep = ToolPlanStep(
    description = "Продвинутое сканирование",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", "/path/to/project")
        .set("include_patterns", listOf("**/*.kt", "**/*.java", "**/*.xml"))
        .set("exclude_patterns", listOf(
            "build/**",
            ".gradle/**",
            "node_modules/**",
            "*.tmp",
            "*.log"
        ))
        .set("max_directory_depth", 15)
        .set("page", 1)
        .set("batch_size", 100)
)
```

## 📊 Структура ответа

### Основные поля

```json
{
  "project": {
    "path": "/path/to/project",
    "type": "GRADLE_KOTLIN"
  },
  "batch": {
    "page": 1,
    "batch_size": 500,
    "total": 1250,
    "has_more": true
  },
  "files": [
    "src/main/kotlin/Main.kt",
    "src/main/kotlin/Utils.kt",
    "build.gradle.kts"
  ],
  "stats": {
    "total_files": 1250,
    "total_lines": 45678,
    "languages": {
      "kotlin": 890,
      "java": 210,
      "xml": 150
    },
    "file_analysis": {
      "small_files": 980,
      "medium_files": 250,
      "large_files": 20
    }
  },
  "performance_metrics": {
    "total_scan_time_ms": 1250,
    "total_files_scanned": 15000,
    "cache_hit_rate": 0.75,
    "avg_time_per_file_us": 83.3,
    "index_memory_mb": 12.5
  },
  "delta": {
    "since_ts": 1698220800000,
    "changed_files": ["src/main/kotlin/NewFile.kt"]
  }
}
```

### Детальная статистика

#### Language Statistics
```json
{
  "languages": {
    "kotlin": {
      "count": 890,
      "lines": 34567,
      "percentage": 71.2
    },
    "java": {
      "count": 210,
      "lines": 8934,
      "percentage": 16.8
    }
  }
}
```

#### File Analysis
```json
{
  "file_analysis": {
    "by_size": {
      "small": {"< 1KB": 980},
      "medium": {"1KB - 10KB": 250},
      "large": {"> 10KB": 20}
    },
    "by_complexity": {
      "low": 1100,
      "medium": 130,
      "high": 20
    }
  }
}
```

## 🔍 Практические примеры

### Пример 1: Сканирование только исходного кода

```kotlin
val sourceOnlyScan = ToolPlanStep(
    description = "Сканирование исходного кода",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "**/*.kt",
            "**/*.java",
            "**/*.scala",
            "**/*.groovy"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            "out/**",
            "**/generated/**"
        ))
)
```

### Пример 2: Поиск изменений за последние 24 часа

```kotlin
val yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

val recentChanges = ToolPlanStep(
    description = "Поиск недавних изменений",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf("**/*"))
        .set("since_ts", yesterday)
)

// Результат будет содержать только измененные файлы
val changedFiles = result.output.get<Map<String, Any>>("json")
    ?.get("delta") as? Map<String, Any>
val files = changedFiles?.get("changed_files") as? List<String>
```

### Пример 3: Пагинация для больших проектов

```kotlin
fun scanLargeProject(projectPath: String) {
    var page = 1
    val batchSize = 200
    var allFiles = mutableListOf<String>()

    do {
        val scanStep = ToolPlanStep(
            description = "Сканирование страницы $page",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", projectPath)
                .set("include_patterns", listOf("**/*.kt", "**/*.java"))
                .set("page", page)
                .set("batch_size", batchSize)
        )

        val result = scanner.executeStep(scanStep, context)
        val json = result.output.get<Map<String, Any>>("json")
        val files = json?.get("files") as? List<String>
        val batch = json?.get("batch") as? Map<String, Any>
        val hasMore = batch?.get("has_more") as? Boolean ?: false

        files?.let { allFiles.addAll(it) }
        page++

    } while (hasMore == true)

    println("Всего найдено файлов: ${allFiles.size}")
}
```

### Пример 4: Анализ проекта по типам

```kotlin
fun analyzeProjectByType(projectPath: String) {
    val configFiles = ToolPlanStep(
        description = "Анализ конфигурационных файлов",
        agentType = AgentType.PROJECT_SCANNER,
        input = StepInput.empty()
            .set("project_path", projectPath)
            .set("include_patterns", listOf(
                "**/*.gradle*",
                "**/*.xml",
                "**/*.properties",
                "**/*.yml",
                "**/*.yaml"
            ))
    )

    val testFiles = ToolPlanStep(
        description = "Анализ тестовых файлов",
        agentType = AgentType.PROJECT_SCANNER,
        input = StepInput.empty()
            .set("project_path", projectPath)
            .set("include_patterns", listOf(
                "**/test/**/*.kt",
                "**/test/**/*.java",
                "**/*Test.kt",
                "**/*Test.java",
                "**/*Spec.kt"
            ))
    )

    // Анализ результатов...
}
```

## 🔄 Система подписок на изменения

### Создание подписки

```kotlin
val callback = { delta: DeltaUpdate ->
    println("Обнаружены изменения в проекте:")
    delta.changedFiles.forEach { file ->
        println(" - $file")
    }
}

val subscriptionId = scanner.createDeltaSubscription(
    agentId = "my-agent",
    projectPath = "/path/to/project",
    callback = callback
)

println("Подписка создана: $subscriptionId")
```

### Отмена подписки

```kotlin
val cancelled = scanner.cancelDeltaSubscription(subscriptionId)
if (cancelled) {
    println("Подписка отменена")
}
```

## 📈 Метрики производительности

### Мониторинг производительности

```kotlin
val result = scanner.executeStep(scanStep, context)
val json = result.output.get<Map<String, Any>>("json")
val metrics = json?.get("performance_metrics") as? Map<String, Any>

metrics?.let {
    val scanTime = it["total_scan_time_ms"] as? Long
    val filesScanned = it["total_files_scanned"] as? Long
    val cacheHitRate = it["cache_hit_rate"] as? Double
    val avgTimePerFile = it["avg_time_per_file_us"] as? Double
    val memoryUsage = it["index_memory_mb"] as? Double

    println("Время сканирования: ${scanTime}ms")
    println("Файлов обработано: $filesScanned")
    println("Hit rate кэша: ${String.format("%.1f", cacheHitRate?.times(100))}%")
    println("Среднее время на файл: ${avgTimePerFile}μs")
    println("Использование памяти индексами: ${memoryUsage}MB")
}
```

### Оптимизация производительности

1. **Используйте кэширование** - повторные сканирования работают быстрее
2. **Ограничивайте глубину** для больших проектов: `max_directory_depth = 10`
3. **Используйте пагинацию** для проектов с >1000 файлов
4. **Фильтруйте на стороне паттернов**, а не в коде

## ⚠️ Лучшие практики и рекомендации

### 1. Выбор правильных паттернов

**Хорошо:**
```kotlin
// Конкретные и эффективные паттерны
"**/*.kt",
"src/main/**/*.java",
"**/resources/**/*.xml"
```

**Плохо:**
```kotlin
// Слишком общие паттерны
"**/*",
"*"
```

### 2. Управление памятью

```kotlin
// Для больших проектов используйте пагинацию
if (estimatedFiles > 1000) {
    step.input.set("batch_size", 100)
    step.input.set("page", currentPage)
}

// Регулярно очищайте подписки
scanner.cancelDeltaSubscription(subscriptionId)
```

### 3. Обработка ошибок

```kotlin
val result = scanner.executeStep(step, context)
if (!result.success) {
    logger.error("Ошибка сканирования: ${result.error}")

    // Попробуйте с более мягкими параметрами
    val retryStep = step.copy(
        input = step.input
            .set("max_directory_depth", 5)
            .set("exclude_patterns", listOf("build/**", ".gradle/**"))
    )

    val retryResult = scanner.executeStep(retryStep, context)
    if (retryResult.success) {
        // Обработка результатов повторной попытки
    }
}
```

### 4. Типичные сценарии использования

#### Анализ структуры проекта
```kotlin
fun analyzeProjectStructure(projectPath: String) {
    val scanStep = ToolPlanStep(
        description = "Анализ структуры проекта",
        agentType = AgentType.PROJECT_SCANNER,
        input = StepInput.empty()
            .set("project_path", projectPath)
            .set("include_patterns", listOf("**/*"))
            .set("exclude_patterns", listOf(
                "build/**",
                ".gradle/**",
                "node_modules/**",
                "*.tmp",
                "*.log"
            ))
            .set("max_directory_depth", 20)
    )

    val result = scanner.executeStep(scanStep, context)
    // Анализ результатов...
}
```

#### Поиск конфигурационных файлов
```kotlin
fun findConfigFiles(projectPath: String) {
    val configStep = ToolPlanStep(
        description = "Поиск конфигурационных файлов",
        agentType = AgentType.PROJECT_SCANNER,
        input = StepInput.empty()
            .set("project_path", projectPath)
            .set("include_patterns", listOf(
                "**/*.properties",
                "**/*.yml",
                "**/*.yaml",
                "**/*.xml",
                "**/*.json",
                "*.gradle*",
                "pom.xml"
            ))
    )

    val result = scanner.executeStep(configStep, context)
    // Обработка найденных конфигурационных файлов...
}
```

## 🔗 Интеграция с другими агентами

### Использование ProjectScannerAgentBridge

```kotlin
// Удобный API для других агентов
val bridge = ProjectScannerAgentBridge()

// Сканирование проекта
val scanResult = bridge.scanProject(
    projectPath = "/path/to/project",
    includePatterns = listOf("**/*.kt"),
    excludePatterns = listOf("build/**")
)

// Подписка на изменения
val subscription = bridge.subscribeToFileChanges(
    agentId = "code-analyzer",
    projectPath = "/path/to/project"
) { changedFiles ->
    // Обработка изменений
    analyzeFiles(changedFiles)
}

// Автоматическая очистка
use(subscription) {
    // Работа с подпиской
    // Автоматически отписаться при выходе из блока
}
```

## 🐛 Устранение проблем

### Частые проблемы

1. **OutOfMemoryError на больших проектах**
   - Решение: Используйте пагинацию и ограничьте глубину сканирования

2. **Медленное сканирование сетевых директорий**
   - Решение: Увеличьте таймауты и используйте кэширование

3. **Некорректные паттерны**
   - Решение: Проверьте синтаксис glob паттернов

### Диагностика

```kotlin
// Включите детальное логирование
val result = scanner.executeStep(step, context)
val metrics = result.output.get<Map<String, Any>>("json")
    ?.get("performance_metrics") as? Map<String, Any>

if (metrics != null) {
    println("Диагностическая информация:")
    metrics.forEach { (key, value) ->
        println("$key: $value")
    }
}
```

---

## 📚 Дополнительные ресурсы

- [Roadmap Project Scanner](../roadmaps/16-enhanced-project-scanner.md)
- [API документация](../api/response-formats.md)
- [Примеры интеграции](../guides/project-scanner-integration.md)
- [Тестирование](../src/test/kotlin/ru/marslab/ide/ride/agent/tools/)

---

<p align="center">
  <sub>Руководство обновлено: 2025-10-25</sub>
</p>