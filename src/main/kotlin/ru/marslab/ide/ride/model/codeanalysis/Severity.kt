package ru.marslab.ide.ride.model.codeanalysis

/**
 * Уровень серьезности проблемы
 */
enum class Severity {
    /** Критическая проблема, требует немедленного исправления */
    CRITICAL,

    /** Высокий приоритет */
    HIGH,

    /** Средний приоритет */
    MEDIUM,

    /** Низкий приоритет */
    LOW,

    /** Информационное сообщение */
    INFO
}
