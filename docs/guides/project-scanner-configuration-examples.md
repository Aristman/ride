# 📋 Примеры конфигурации Project Scanner

## 🎯 Обзор

Этот документ содержит готовые примеры конфигурации Project Scanner Tool Agent для различных сценариев использования. Каждый пример включает описание, параметры конфигурации и ожидаемые результаты.

## 🏗️ Структура конфигурации

### Базовый шаблон

```kotlin
val scanStep = ToolPlanStep(
    description = "Описание сканирования",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", "/path/to/project")
        .set("include_patterns", listOf(/* паттерны включения */))
        .set("exclude_patterns", listOf(/* паттерны исключения */))
        // Дополнительные параметры...
)
```

## 📂 Типы проектов

### 1. Kotlin Gradle проект

```kotlin
val kotlinGradleScan = ToolPlanStep(
    description = "Сканирование Kotlin Gradle проекта",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "**/*.kt",
            "**/*.java",
            "*.gradle.kts",
            "gradle.properties"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            ".gradle/**",
            "**/generated/**",
            "*.tmp",
            "*.log"
        ))
        .set("max_directory_depth", 20)
)

// Ожидаемый результат:
// - Все Kotlin и Java исходники
// - Конфигурационные файлы Gradle
// - Без временных и сгенерированных файлов
```

### 2. Maven проект

```kotlin
val mavenScan = ToolPlanStep(
    description = "Сканирование Maven проекта",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/**/*.java",
            "src/**/*.kt",
            "src/**/*.scala",
            "src/**/*.groovy",
            "pom.xml",
            "*.properties"
        ))
        .set("exclude_patterns", listOf(
            "target/**",
            ".mvn/**",
            "mvnw*",
            "**/generated/**"
        ))
        .set("max_directory_depth", 15)
)

// Ожидаемый результат:
// - Исходники в директориях src/
// - Maven конфигурация (pom.xml)
// - Properties файлы
```

### 3. Node.js проект

```kotlin
val nodejsScan = ToolPlanStep(
    description = "Сканирование Node.js проекта",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/**/*.js",
            "src/**/*.ts",
            "src/**/*.jsx",
            "src/**/*.tsx",
            "lib/**/*.js",
            "package.json",
            "package-lock.json",
            "yarn.lock",
            "tsconfig.json",
            "webpack.config.js",
            "*.config.js"
        ))
        .set("exclude_patterns", listOf(
            "node_modules/**",
            "dist/**",
            "build/**",
            "coverage/**",
            ".nyc_output/**",
            "*.log"
        ))
)

// Ожидаемый результат:
// - JavaScript/TypeScript исходники
// - Конфигурационные файлы
// - Без node_modules и сборочных артефактов
```

### 4. Python проект

```kotlin
val pythonScan = ToolPlanStep(
    description = "Сканирование Python проекта",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "**/*.py",
            "**/*.pyx",
            "**/*.pyi",
            "requirements*.txt",
            "setup.py",
            "pyproject.toml",
            "Pipfile",
            "poetry.lock",
            "*.cfg",
            "*.ini"
        ))
        .set("exclude_patterns", listOf(
            "__pycache__/**",
            "*.pyc",
            "*.pyo",
            "*.pyd",
            ".pytest_cache/**",
            ".coverage",
            "htmlcov/**",
            "*.egg-info/**"
        ))
)

// Ожидаемый результат:
// - Python исходники
// - Конфигурационные файлы
// - Без кэша и скомпилированных файлов
```

### 5. Мультиязыковой проект

```kotlin
val multiLanguageScan = ToolPlanStep(
    description = "Сканирование мультиязыкового проекта",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            // Kotlin/Java
            "src/**/*.kt",
            "src/**/*.java",
            // JavaScript/TypeScript
            "frontend/**/*.js",
            "frontend/**/*.ts",
            "frontend/**/*.jsx",
            "frontend/**/*.tsx",
            // Python
            "scripts/**/*.py",
            "*.py",
            // Конфигурация
            "*.gradle*",
            "*.xml",
            "*.json",
            "*.yml",
            "*.yaml",
            "*.properties"
        ))
        .set("exclude_patterns", listOf(
            // Build artifacts
            "build/**",
            "target/**",
            "dist/**",
            "node_modules/**",
            ".gradle/**",
            "__pycache__/**",
            // Generated files
            "**/generated/**",
            "*.generated.*",
            // Temp files
            "*.tmp",
            "*.temp",
            "*.bak",
            "*.log",
            "*.lock"
        ))
        .set("max_directory_depth", 25)
)
```

## 🔍 Специализированные сценарии

### 1. Анализ только исходного кода

```kotlin
val sourceCodeOnlyScan = ToolPlanStep(
    description = "Анализ только исходного кода",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/**/*.kt",
            "src/**/*.java",
            "src/**/*.scala",
            "src/**/*.groovy",
            "src/**/*.js",
            "src/**/*.ts",
            "src/**/*.py",
            "src/**/*.cpp",
            "src/**/*.c",
            "src/**/*.h"
        ))
        .set("exclude_patterns", listOf(
            "**/generated/**",
            "**/test/**", // Исключаем тесты
            "**/tests/**"
        ))
)

// Используйте для: анализа кодовой базы, метрик кода, поиска зависимостей
```

### 2. Анализ тестовых файлов

```kotlin
val testFilesScan = ToolPlanStep(
    description = "Анализ тестовых файлов",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "**/test/**/*.kt",
            "**/test/**/*.java",
            "**/test/**/*.js",
            "**/test/**/*.ts",
            "**/test/**/*.py",
            "**/*Test*.kt",
            "**/*Test*.java",
            "**/*Test*.js",
            "**/*Test*.py",
            "**/*Spec*.kt",
            "**/*Spec*.js"
        ))
        .set("exclude_patterns", listOf(
            "**/generated/**",
            "**/resources/**"
        ))
)

// Используйте для: анализа покрытия тестами, качества тестов
```

### 3. Анализ конфигурационных файлов

```kotlin
val configFilesScan = ToolPlanStep(
    description = "Анализ конфигурационных файлов",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            // Build configs
            "*.gradle*",
            "pom.xml",
            "package.json",
            "Cargo.toml",
            "requirements.txt",
            "setup.py",
            "pyproject.toml",
            // Config files
            "*.properties",
            "*.yml",
            "*.yaml",
            "*.xml",
            "*.json",
            "*.toml",
            "*.ini",
            "*.cfg",
            "*.conf",
            // IDE configs
            ".vscode/**",
            ".idea/**",
            ".eclipse/**"
        ))
        .set("exclude_patterns", listOf(
            "*.lock",
            "*.log",
            "*.tmp"
        ))
)

// Используйте для: анализа зависимостей, конфигураций сборки, настроек
```

### 4. Поиск документации

```kotlin
val documentationScan = ToolPlanStep(
    description = "Поиск документации",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "**/*.md",
            "**/*.rst",
            "**/*.txt",
            "docs/**",
            "doc/**",
            "README*",
            "CHANGELOG*",
            "LICENSE*",
            "CONTRIBUTING*"
        ))
        .set("exclude_patterns", listOf(
            "**/node_modules/**",
            "**/target/**",
            "**/build/**"
        ))
)

// Используйте для: анализа документации, генерации отчетов
```

## 📊 Пагинация и большие проекты

### 1. Пагинация для больших проектов

```kotlin
fun scanLargeProjectInPages(projectPath: String) {
    val batchSize = 200
    var currentPage = 1
    var allFiles = mutableListOf<String>()
    var hasMore = true

    while (hasMore) {
        val pageScan = ToolPlanStep(
            description = "Сканирование страницы $currentPage",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", projectPath)
                .set("include_patterns", listOf(
                    "**/*.kt",
                    "**/*.java",
                    "**/*.js",
                    "**/*.ts"
                ))
                .set("exclude_patterns", listOf(
                    "build/**",
                    "node_modules/**",
                    ".gradle/**"
                ))
                .set("page", currentPage)
                .set("batch_size", batchSize)
        )

        val result = scanner.executeStep(pageScan, context)
        val json = result.output.get<Map<String, Any>>("json")
        val files = json?.get("files") as? List<String>
        val batch = json?.get("batch") as? Map<String, Any>

        files?.let { allFiles.addAll(it) }
        hasMore = batch?.get("has_more") as? Boolean ?: false
        currentPage++

        println("Обработано страница $currentPage: ${files?.size} файлов")
    }

    println("Всего найдено файлов: ${allFiles.size}")
    return allFiles
}
```

### 2. Ограничение по глубине

```kotlin
val depthLimitedScan = ToolPlanStep(
    description = "Сканирование с ограниченной глубиной",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf("**/*"))
        .set("exclude_patterns", listOf(
            "build/**",
            ".gradle/**",
            "node_modules/**"
        ))
        .set("max_directory_depth", 8) // Только до 8 уровней вложенности
)

// Используйте для: быстрого обзора структуры, избегания слишком глубоких директорий
```

## ⏰ Поиск изменений

### 1. Изменения за последние 24 часа

```kotlin
fun findRecentChanges(projectPath: String) {
    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

    val recentChangesScan = ToolPlanStep(
        description = "Поиск изменений за последние 24 часа",
        agentType = AgentType.PROJECT_SCANNER,
        input = StepInput.empty()
            .set("project_path", projectPath)
            .set("include_patterns", listOf(
                "**/*.kt",
                "**/*.java",
                "**/*.js",
                "**/*.ts",
                "**/*.py"
            ))
            .set("since_ts", oneDayAgo)
    )

    val result = scanner.executeStep(recentChangesScan, context)
    val json = result.output.get<Map<String, Any>>("json")
    val delta = json?.get("delta") as? Map<String, Any>
    val changedFiles = delta?.get("changed_files") as? List<String>

    println("Найдено изменений: ${changedFiles?.size}")
    changedFiles?.forEach { file ->
        println("- $file")
    }
}
```

### 2. Инкрементальное сканирование

```kotlin
fun incrementalScan(projectPath: String, lastScanTime: Long) {
    val incrementalScan = ToolPlanStep(
        description = "Инкрементальное сканирование",
        agentType = AgentType.PROJECT_SCANNER,
        input = StepInput.empty()
            .set("project_path", projectPath)
            .set("include_patterns", listOf("**/*"))
            .set("exclude_patterns", listOf(
                "build/**",
                ".gradle/**",
                "node_modules/**"
            ))
            .set("since_ts", lastScanTime)
    )

    // Результат будет содержать только файлы, измененные после lastScanTime
}
```

## 🎯 Специализированные фильтры

### 1. Фильтр по размеру файлов

```kotlin
// Для поиска больших файлов
val largeFilesScan = ToolPlanStep(
    description = "Поиск больших файлов",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "**/*.kt",
            "**/*.java",
            "**/*.js",
            "**/*.ts"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            "node_modules/**"
        ))
    // Фильтрация по размеру будет выполняться на основе результатов
)

// После получения результатов:
val json = result.output.get<Map<String, Any>>("json")
val stats = json?.get("stats") as? Map<String, Any>
val fileAnalysis = stats?.get("file_analysis") as? Map<String, Any>

// Анализ больших файлов на основе статистики
```

### 2. Фильтр по языкам программирования

```kotlin
// Kotlin только
val kotlinOnlyScan = ToolPlanStep(
    description = "Только Kotlin файлы",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "**/*.kt",
            "*.kts"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            "**/generated/**"
        ))
)

// Java только
val javaOnlyScan = ToolPlanStep(
    description = "Только Java файлы",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "**/*.java"
        ))
        .set("exclude_patterns", listOf(
            "target/**",
            "**/generated/**"
        ))
)
```

### 3. Исключение тестовых файлов

```kotlin
val productionCodeOnlyScan = ToolPlanStep(
    description = "Только production код",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/main/**/*.kt",
            "src/main/**/*.java",
            "src/main/**/*.js",
            "src/main/**/*.ts",
            "src/main/**/*.py",
            "lib/**/*.kt",
            "lib/**/*.js"
        ))
        .set("exclude_patterns", listOf(
            "**/test/**",
            "**/tests/**",
            "**/*Test*",
            "**/*Spec*",
            "**/*test*",
            "**/generated/**"
        ))
)
```

## 🔧 Интеграционные конфигурации

### 1. Для Code Analysis Agent

```kotlin
val codeAnalysisConfig = ToolPlanStep(
    description = "Подготовка файлов для анализа кода",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/**/*.kt",
            "src/**/*.java",
            "src/**/*.js",
            "src/**/*.ts",
            "src/**/*.py"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            "target/**",
            "node_modules/**",
            "**/generated/**",
            "**/test/**"
        ))
        .set("max_directory_depth", 20)
)

// Оптимально для последующего анализа качества кода
```

### 2. Для Documentation Agent

```kotlin
val documentationConfig = ToolPlanStep(
    description = "Поиск файлов для генерации документации",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/**/*.kt",
            "src/**/*.java",
            "src/**/*.js",
            "src/**/*.ts",
            "**/*.md",
            "README*",
            "**/*.py" // Для docstring анализа
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            "node_modules/**",
            "**/test/**",
            "**/*test*"
        ))
)

// Оптимально для генерации документации по коду
```

### 3. Для Dependency Analysis

```kotlin
val dependencyAnalysisConfig = ToolPlanStep(
    description = "Анализ зависимостей проекта",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "*.gradle*",
            "pom.xml",
            "package.json",
            "yarn.lock",
            "package-lock.json",
            "requirements.txt",
            "setup.py",
            "pyproject.toml",
            "Cargo.toml",
            "composer.json",
            "Gemfile"
        ))
        .set("exclude_patterns", listOf(
            "*.lock",
            "*.log"
        ))
)

// Только конфигурационные файлы для анализа зависимостей
```

## 🎨 Кастомные сценарии

### 1. Поиск дубликатов кода

```kotlin
val duplicateCodeConfig = ToolPlanStep(
    description = "Подготовка для поиска дубликатов",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/**/*.kt",
            "src/**/*.java",
            "src/**/*.js",
            "src/**/*.ts"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            "**/generated/**",
            "**/test/**"
        ))
        .set("max_directory_depth", 15)
)
```

### 2. Анализ безопасности

```kotlin
val securityAnalysisConfig = ToolPlanStep(
    description = "Подготовка для security анализа",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/**/*.kt",
            "src/**/*.java",
            "src/**/*.js",
            "src/**/*.ts",
            "src/**/*.py",
            "*.xml",
            "*.json",
            "*.properties",
            "*.yml",
            "*.yaml"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            "node_modules/**",
            "**/test/**"
        ))
)
```

### 3. Миграция проекта

```kotlin
val migrationConfig = ToolPlanStep(
    description = "Анализ проекта для миграции",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "**/*", // Все файлы для полного анализа
            "*.gradle*",
            "pom.xml",
            "package.json",
            "*.xml",
            "*.properties"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            "target/**",
            "node_modules/**",
            ".gradle/**",
            "*.log",
            "*.tmp"
        ))
        .set("max_directory_depth", 20)
)
```

## ⚡ Оптимизированные конфигурации

### 1. Быстрое сканирование

```kotlin
val fastScanConfig = ToolPlanStep(
    description = "Быстрое сканирование проекта",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/**/*.kt",
            "src/**/*.java"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            ".gradle/**",
            "**/generated/**",
            "**/test/**"
        ))
        .set("max_directory_depth", 10)
        .set("batch_size", 500)
)

// Оптимально для быстрого обзора проекта
```

### 2. Комплексное сканирование

```kotlin
val comprehensiveScanConfig = ToolPlanStep(
    description = "Комплексное сканирование проекта",
    agentType = AgentType.PROJECT_SCANNER,
    input = StepInput.empty()
        .set("project_path", projectPath)
        .set("include_patterns", listOf(
            "src/**/*",
            "test/**/*",
            "*.gradle*",
            "*.xml",
            "*.json",
            "*.properties",
            "*.md"
        ))
        .set("exclude_patterns", listOf(
            "build/**",
            ".gradle/**",
            "node_modules/**",
            "*.log",
            "*.tmp"
        ))
        .set("max_directory_depth", 25)
        .set("batch_size", 200)
)

// Полный анализ проекта с метриками
```

## 🚨 Частые ошибки и их решения

### 1. Слишком много результатов

**Проблема:** Сканер возвращает слишком много файлов
```kotlin
// Плохо:
"**/*"

// Хорошо:
"src/**/*.kt",
"src/**/*.java",
"*.gradle*"
```

### 2. OutOfMemoryError

**Проблема:** Недостаточно памяти для больших проектов
```kotlin
// Добавьте ограничения:
.set("max_directory_depth", 15)
.set("batch_size", 100)
.set("page", 1)
```

### 3. Медленное сканирование

**Проблема:** Слишком глубокое сканирование
```kotlin
// Ограничьте глубину и исключите лишние директории:
.set("max_directory_depth", 10)
.set("exclude_patterns", listOf(
    "build/**",
    ".gradle/**",
    "node_modules/**",
    ".git/**"
))
```

---

## 📚 Дополнительные ресурсы

- [Руководство по использованию](project-scanner-usage-guide.md)
- [Roadmap Project Scanner](../roadmaps/16-enhanced-project-scanner.md)
- [API документация](../api/response-formats.md)

---

<p align="center">
  <sub>Примеры конфигурации обновлены: 2025-10-25</sub>
</p>