package ru.marslab.ide.ride.testing

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Сохранение тестов в файловую систему на основе структуры проекта.
 */
class FileSystemTestPersister(
    private val structureProvider: ProjectStructureProvider
) : TestPersister {

    override suspend fun persist(tests: List<GeneratedTest>): List<Path> {
        if (tests.isEmpty()) return emptyList()
        val structure = structureProvider.getProjectStructure()

        // Выбираем директорию для тестов
        val targetRoot: Path = when {
            structure.testSourceDirs.isNotEmpty() -> structure.testSourceDirs.first()
            // Создадим дефолт при отсутствии каталога тестов
            structure.buildSystem == BuildSystem.GRADLE || structure.buildSystem == BuildSystem.MAVEN ->
                structure.root.resolve("src/test/kotlin")
            structure.buildSystem == BuildSystem.DART ->
                structure.root.resolve("test")
            else -> structure.root.resolve("src/test/kotlin")
        }

        if (!Files.exists(targetRoot)) Files.createDirectories(targetRoot)

        val saved = mutableListOf<Path>()
        for (t in tests) {
            val pkgPath = (t.targetPackage?.replace('.', '/') ?: "")
            val dir = if (pkgPath.isNotBlank()) targetRoot.resolve(pkgPath) else targetRoot
            if (!Files.exists(dir)) Files.createDirectories(dir)
            val file = dir.resolve(t.fileName)
            Files.writeString(
                file,
                t.content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
            saved += file.toList()
        }
        return saved
    }
}
