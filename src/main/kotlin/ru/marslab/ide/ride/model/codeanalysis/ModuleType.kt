package ru.marslab.ide.ride.model.codeanalysis

/**
 * Тип модуля в проекте
 */
enum class ModuleType {
    /** Доменная логика */
    DOMAIN,
    
    /** UI слой */
    UI,
    
    /** Сервисный слой */
    SERVICE,
    
    /** Интеграции с внешними системами */
    INTEGRATION,
    
    /** Утилиты */
    UTIL,
    
    /** Тесты */
    TEST
}
