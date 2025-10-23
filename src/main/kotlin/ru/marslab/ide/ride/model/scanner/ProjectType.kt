package ru.marslab.ide.ride.model.scanner

/**
 * Тип проекта для определения специфических правил фильтрации
 */
enum class ProjectType(val displayName: String, val description: String) {
    MAVEN("Maven", "Java проект с Maven сборкой"),
    GRADLE("Gradle", "Проект с Gradle сборкой"),
    GRADLE_KOTLIN("Gradle Kotlin", "Kotlin проект с Gradle Kotlin DSL"),
    PYTHON("Python", "Python проект"),
    NODE_JS("Node.js", "Node.js/JavaScript проект"),
    RUST("Rust", "Rust проект"),
    SPRING_BOOT("Spring Boot", "Spring Boot приложение"),
    ANDROID("Android", "Android приложение"),
    GENERIC("Generic", "Универсальный тип проекта"),
    UNKNOWN("Unknown", "Неизвестный тип проекта")
}

/**
 * Конфигурация фильтрации для конкретного типа проекта
 */
data class ProjectFilterConfig(
    val projectType: ProjectType,
    val excludePatterns: List<String>,
    val includePatterns: List<String> = emptyList(),
    val maxFileSize: Long = 10 * 1024 * 1024, // 10MB по умолчанию
    val maxDirectoryDepth: Int = Int.MAX_VALUE,
    val binaryExtensions: Set<String> = emptySet()
)

/**
 * Расширенные настройки сканирования
 */
data class ScanSettings(
    val forceRescan: Boolean = false,
    val includeHiddenFiles: Boolean = false,
    val maxFileSize: Long? = null,
    val maxDirectoryDepth: Int? = null,
    val excludePatterns: List<String> = emptyList(),
    val includePatterns: List<String> = emptyList(),
    val modifiedAfter: Long? = null, // timestamp
    val modifiedBefore: Long? = null, // timestamp
    val followSymlinks: Boolean = false,
    val calculateFileHashes: Boolean = false,
    val countLinesOfCode: Boolean = false
)