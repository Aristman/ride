package ru.marslab.ide.ride.testing

/**
 * Тип тестового фреймворка для проекта/модуля.
 */
enum class TestFramework {
    JUNIT5,
    KOTEST,
    TESTNG,
    NONE
}

/**
 * Детектор тестового фреймворка. Для JVM-части анализирует зависимости и плагины сборочной системы.
 */
interface TestFrameworkDetector {
    /**
     * Возвращает обнаруженный тестовый фреймворк (или NONE, если не найден).
     */
    suspend fun detect(): TestFramework

    /**
     * Возвращает текст с инструкциями по добавлению зависимостей выбранного фреймворка в проект.
     * Может учитывать `BuildSystem` из `ProjectStructure`.
     */
    suspend fun suggestAddInstructions(framework: TestFramework): String
}
