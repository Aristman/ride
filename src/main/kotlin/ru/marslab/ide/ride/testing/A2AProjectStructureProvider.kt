package ru.marslab.ide.ride.testing

import com.intellij.openapi.project.ProjectManager
import java.nio.file.Path
import java.nio.file.Paths
import java.io.File

/**
 * Базовая реализация ProjectStructureProvider.
 * Пока использует прямой анализ файлов системы проекта (fallback),
 * позднее может быть заменена на чтение данных из A2A-шины после запуска A2AProjectScannerToolAgent.
 */
class A2AProjectStructureProvider : ProjectStructureProvider {
    override suspend fun getProjectStructure(): ProjectStructure {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        val rootPath = project?.basePath ?: System.getProperty("user.dir")
        val root = Paths.get(rootPath)

        val buildSystem = detectBuildSystem(root)
        val mainDirs = detectMainSourceDirs(root)
        val testDirs = detectTestSourceDirs(root)

        return ProjectStructure(
            root = root,
            buildSystem = buildSystem,
            testSourceDirs = testDirs,
            mainSourceDirs = mainDirs
        )
    }

    private fun detectBuildSystem(root: Path): BuildSystem? {
        val r = root.toFile()
        val isGradle = File(r, "build.gradle.kts").exists() || File(r, "build.gradle").exists()
        val isMaven = File(r, "pom.xml").exists()
        val isDart = File(r, "pubspec.yaml").exists()
        return when {
            isGradle -> BuildSystem.GRADLE
            isMaven -> BuildSystem.MAVEN
            isDart -> BuildSystem.DART
            else -> null
        }
    }

    private fun detectMainSourceDirs(root: Path): List<Path> {
        val dirs = mutableListOf<Path>()
        val candidates = listOf(
            "src/main/kotlin",
            "src/main/java",
            "lib" // dart
        )
        candidates.map { root.resolve(it) }.forEach { p -> if (p.toFile().exists()) dirs += p }
        return dirs
    }

    private fun detectTestSourceDirs(root: Path): List<Path> {
        val dirs = mutableListOf<Path>()
        val candidates = listOf(
            "src/test/kotlin",
            "src/test/java",
            "test" // dart
        )
        candidates.map { root.resolve(it) }.forEach { p -> if (p.toFile().exists()) dirs += p }
        if (dirs.isEmpty()) {
            // если тестовых директорий нет — вернем дефолт для выбранного языка позже на этапе сохранения
        }
        return dirs
    }
}
