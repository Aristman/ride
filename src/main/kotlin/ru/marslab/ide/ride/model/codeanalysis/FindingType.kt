package ru.marslab.ide.ride.model.codeanalysis

/**
 * Тип найденной проблемы
 */
enum class FindingType {
    /** Баг или потенциальная ошибка */
    BUG,
    
    /** Code smell - плохая практика */
    CODE_SMELL,
    
    /** Проблема безопасности */
    SECURITY_ISSUE,
    
    /** Нарушение архитектуры */
    ARCHITECTURE_VIOLATION,
    
    /** Проблема производительности */
    PERFORMANCE_ISSUE,
    
    /** Отсутствующая документация */
    DOCUMENTATION_MISSING
}
