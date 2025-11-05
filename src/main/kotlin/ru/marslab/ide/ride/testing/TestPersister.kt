package ru.marslab.ide.ride.testing

import java.nio.file.Path
/**
 * Сохранение сгенерированных тестов в структуру проекта.
 */
interface TestPersister {
    /**
     * Сохраняет сгенерированные тесты в проект согласно структуре.
     * Возвращает пути к сохранённым файлам.
     */
    suspend fun persist(tests: List<GeneratedTest>): List<java.nio.file.Path>

    /**
     * Сохраняет сгенерированные тесты с учётом исходного файла (relativePath внутри workspace).
     * По умолчанию делегирует на persist(). Реализации могут учитывать путь для выбора папки/импорта.
     */
    suspend fun persistForSource(sourceRelativePath: String, tests: List<GeneratedTest>): List<java.nio.file.Path> = persist(tests)
}
