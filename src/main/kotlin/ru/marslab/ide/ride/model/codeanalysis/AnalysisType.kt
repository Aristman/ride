package ru.marslab.ide.ride.model.codeanalysis

/**
 * Типы анализа кода
 */
enum class AnalysisType {
    /** Поиск багов и потенциальных проблем */
    BUG_DETECTION,
    
    /** Анализ архитектуры проекта */
    ARCHITECTURE,
    
    /** Анализ качества кода */
    CODE_QUALITY,
    
    /** Анализ зависимостей */
    DEPENDENCIES,
    
    /** Поиск уязвимостей безопасности */
    SECURITY,
    
    /** Проверка документации */
    DOCUMENTATION,
    
    /** Все типы анализа */
    ALL
}
