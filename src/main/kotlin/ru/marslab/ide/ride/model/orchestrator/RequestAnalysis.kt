package ru.marslab.ide.ride.model.orchestrator

/**
 * Типы задач, которые может выполнять оркестратор
 */
enum class TaskType {
    /** Анализ кода и поиск проблем */
    CODE_ANALYSIS,

    /** Рефакторинг кода */
    REFACTORING,

    /** Исправление багов */
    BUG_FIX,

    /** Генерация отчетов */
    REPORT_GENERATION,

    /** Сложные многошаговые задачи */
    COMPLEX_MULTI_STEP,

    /** Простые запросы */
    SIMPLE_QUERY,

    /** Архитектурный анализ */
    ARCHITECTURE_ANALYSIS,

    /** Тестирование */
    TESTING,

    /** Документирование */
    DOCUMENTATION,

    /** Миграция кода */
    MIGRATION,

    /** Оптимизация производительности */
    PERFORMANCE_OPTIMIZATION
}

/**
 * Уровень сложности задачи
 */
enum class ComplexityLevel {
    LOW,      // Простая задача (< 5 минут)
    MEDIUM,   // Средняя задача (5-15 минут)
    HIGH,     // Сложная задача (15-30 минут)
    VERY_HIGH // Очень сложная задача (> 30 минут)
}

/**
 * Типы агентов-инструментов
 */
enum class AgentType {
    PROJECT_SCANNER,
    CODE_CHUNKER,
    BUG_DETECTION,
    CODE_QUALITY,
    ARCHITECTURE_ANALYSIS,
    CODE_FIXER,
    REPORT_GENERATOR,
    USER_INTERACTION,
    FILE_OPERATIONS,
    GIT_OPERATIONS,
    TEST_GENERATOR,
    DOCUMENTATION_GENERATOR,
    PERFORMANCE_ANALYZER
}

/**
 * Контекст выполнения задачи
 */
data class ExecutionContext(
    val projectPath: String? = null,
    val selectedFiles: List<String> = emptyList(),
    val selectedDirectories: List<String> = emptyList(),
    val gitBranch: String? = null,
    val additionalContext: Map<String, Any> = emptyMap()
)

/**
 * Результат анализа пользовательского запроса
 */
data class RequestAnalysis(
    val taskType: TaskType,
    val requiredTools: Set<AgentType>,
    val context: ExecutionContext,
    val parameters: Map<String, Any>,
    val requiresUserInput: Boolean,
    val estimatedComplexity: ComplexityLevel,
    val estimatedSteps: Int,
    val confidence: Double = 1.0,
    val reasoning: String = ""
)

/**
 * Пользовательский запрос для анализа
 */
data class UserRequest(
    val originalRequest: String,
    val context: ExecutionContext = ExecutionContext(),
    val conversationHistory: List<String> = emptyList(),
    val userId: String? = null
)