package ru.marslab.ide.ride.testing

/**
 * Запуск тестов и нормализация вывода в TestRunResult.
 */
interface TestRunner {
    /**
     * Выполняет запуск тестов (всех или по классу/пакету) и возвращает сводку.
     */
    suspend fun run(scope: String? = null): TestRunResult
}
