package ru.marslab.ide.ride.testing

/**
 * Поставщик структуры проекта. В реальной реализации получает данные из A2A-шины
 * после запуска A2AProjectScannerToolAgent и кэширует их.
 */
interface ProjectStructureProvider {
    /**
     * Возвращает сведения о структуре проекта (корень, build-система, source/test каталоги).
     */
    suspend fun getProjectStructure(): ProjectStructure
}
