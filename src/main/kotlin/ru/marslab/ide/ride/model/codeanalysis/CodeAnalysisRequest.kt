package ru.marslab.ide.ride.model.codeanalysis

import ru.marslab.ide.ride.model.llm.LLMParameters

/**
 * Запрос на анализ кода
 *
 * @property projectPath Путь к проекту
 * @property analysisTypes Типы анализа для выполнения
 * @property filePatterns Паттерны файлов для включения
 * @property excludePatterns Паттерны файлов для исключения
 * @property maxFilesPerBatch Максимальное количество файлов в одном батче
 * @property parameters Параметры LLM для анализа
 */
data class CodeAnalysisRequest(
    val projectPath: String,
    val analysisTypes: Set<AnalysisType>,
    val filePatterns: List<String> = listOf(
        "**/*.kt", "**/*.java",           // JVM
        "**/*.py",                         // Python
        "**/*.js", "**/*.jsx",            // JavaScript
        "**/*.ts", "**/*.tsx",            // TypeScript
        "**/*.go",                         // Go
        "**/*.rs",                         // Rust
        "**/*.c", "**/*.cpp", "**/*.h"    // C/C++
    ),
    val excludePatterns: List<String> = listOf(
        "**/build/**", "**/dist/**", "**/target/**",
        "**/node_modules/**", "**/.gradle/**",
        "**/test/**", "**/tests/**"
    ),
    val maxFilesPerBatch: Int = 10,
    val parameters: LLMParameters = LLMParameters.BALANCED
)
