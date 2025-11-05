package ru.marslab.ide.ride.testing

import java.nio.file.Path

/**
 * Сведения о структуре проекта, необходимые для генерации и запуска тестов.
 */
data class ProjectStructure(
    val root: Path,
    val buildSystem: BuildSystem?,
    val testSourceDirs: List<Path>,
    val mainSourceDirs: List<Path>,
)

enum class BuildSystem {
    GRADLE, MAVEN, DART
}
