package ru.marslab.ide.ride.testing

/**
 * Оркестратор генерации и запуска unit-тестов для выбранного файла исходников.
 */
interface TestingAgentOrchestrator {
    /**
     * Полный цикл: анализ файла, генерация тестов, сохранение и запуск.
     * @param filePath относительный путь внутри workspace (начиная от корня проекта)
     */
    suspend fun generateAndRun(filePath: String): TestRunResult
}
