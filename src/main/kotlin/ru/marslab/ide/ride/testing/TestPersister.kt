package ru.marslab.ide.ride.testing

import java.nio.file.Path

/**
 * Сохранение сгенерированных тестов в структуру проекта.
 */
interface TestPersister {
    /**
     * Сохраняет тестовые файлы и возвращает их пути.
     */
    suspend fun persist(tests: List<GeneratedTest>): List<Path>
}
