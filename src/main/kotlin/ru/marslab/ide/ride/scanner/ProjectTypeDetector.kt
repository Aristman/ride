package ru.marslab.ide.ride.scanner

import ru.marslab.ide.ride.model.scanner.ProjectType
import java.io.File
import java.nio.file.Path

/**
 * Детектор типа проекта на основе файлов конфигурации
 */
object ProjectTypeDetector {

    private val projectDetectors = listOf(
        ProjectTypeDetectorEntry(
            type = ProjectType.MAVEN,
            files = listOf("pom.xml"),
            directories = emptyList(),
            description = "Maven проект"
        ),
        ProjectTypeDetectorEntry(
            type = ProjectType.GRADLE_KOTLIN,
            files = listOf("build.gradle.kts", "settings.gradle.kts"),
            directories = emptyList(),
            description = "Gradle с Kotlin DSL"
        ),
        ProjectTypeDetectorEntry(
            type = ProjectType.GRADLE,
            files = listOf("build.gradle", "settings.gradle"),
            directories = emptyList(),
            description = "Gradle проект"
        ),
        ProjectTypeDetectorEntry(
            type = ProjectType.SPRING_BOOT,
            files = listOf("pom.xml", "build.gradle", "build.gradle.kts"),
            directories = listOf("src/main/java"),
            additionalCheck = { path ->
                val pomXml = path.resolve("pom.xml").toFile()
                if (pomXml.exists()) {
                    val content = pomXml.readText()
                    content.contains("spring-boot-starter") || content.contains("spring-boot")
                } else {
                    false
                }
            },
            description = "Spring Boot приложение"
        ),
        ProjectTypeDetectorEntry(
            type = ProjectType.ANDROID,
            files = listOf("build.gradle", "build.gradle.kts"),
            directories = listOf("app/src/main", "src/main/java"),
            additionalCheck = { path ->
                val buildFile = listOf("build.gradle", "build.gradle.kts")
                    .map { path.resolve(it).toFile() }
                    .firstOrNull { it.exists() }
                buildFile?.readText()?.contains("com.android.application") == true
            },
            description = "Android приложение"
        ),
        ProjectTypeDetectorEntry(
            type = ProjectType.PYTHON,
            files = listOf("requirements.txt", "setup.py", "pyproject.toml", "Pipfile"),
            directories = listOf("__pycache__", ".venv", "venv"),
            additionalCheck = { path ->
                val pythonFiles = path.toFile().walkTopDown()
                    .maxDepth(2)
                    .filter { it.extension == "py" }
                    .count()
                pythonFiles > 0
            },
            description = "Python проект"
        ),
        ProjectTypeDetectorEntry(
            type = ProjectType.NODE_JS,
            files = listOf("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml"),
            directories = listOf("node_modules"),
            additionalCheck = { path ->
                val packageJson = path.resolve("package.json").toFile()
                packageJson.exists()
            },
            description = "Node.js проект"
        ),
        ProjectTypeDetectorEntry(
            type = ProjectType.RUST,
            files = listOf("Cargo.toml", "Cargo.lock"),
            directories = listOf("target"),
            additionalCheck = { path ->
                val cargoToml = path.resolve("Cargo.toml").toFile()
                cargoToml.exists()
            },
            description = "Rust проект"
        )
    )

    /**
     * Определяет тип проекта по указанному пути
     */
    fun detectProjectType(projectPath: Path): ProjectType {
        val projectFile = projectPath.toFile()
        if (!projectFile.exists() || !projectFile.isDirectory) {
            return ProjectType.UNKNOWN
        }

        // Проверяем каждый детектор
        for (detector in projectDetectors) {
            if (matchesDetector(projectPath, detector)) {
                return detector.type
            }
        }

        // Если ничего не определилось, проверяем наличие исходников
        return if (hasSourceFiles(projectPath)) {
            ProjectType.GENERIC
        } else {
            ProjectType.UNKNOWN
        }
    }

    /**
     * Проверяет, соответствует ли проект детектору
     */
    private fun matchesDetector(projectPath: Path, detector: ProjectTypeDetectorEntry): Boolean {
        val path = projectPath.toFile()

        // Проверяем файлы
        val hasRequiredFiles = detector.files.any { fileName ->
            path.resolve(fileName).exists()
        }

        if (!hasRequiredFiles && detector.files.isNotEmpty()) {
            return false
        }

        // Проверяем директории
        val hasRequiredDirs = detector.directories.all { dirName ->
            path.resolve(dirName).exists()
        }

        if (!hasRequiredDirs && detector.directories.isNotEmpty()) {
            return false
        }

        // Дополнительная проверка
        detector.additionalCheck?.let { check ->
            return check(projectPath)
        }

        return true
    }

    /**
     * Проверяет наличие исходных файлов в проекте
     */
    private fun hasSourceFiles(projectPath: Path): Boolean {
        val sourceExtensions = setOf(
            "java", "kt", "scala", "groovy", // JVM
            "py", "pyx", // Python
            "js", "ts", "jsx", "tsx", // JavaScript/TypeScript
            "c", "cpp", "cc", "cxx", "h", "hpp", // C/C++
            "rs", // Rust
            "go", // Go
            "php", "rb", "swift", "dart" // Другие языки
        )

        return projectPath.toFile().walkTopDown()
            .maxDepth(3)
            .filter { it.isFile }
            .any { it.extension.lowercase() in sourceExtensions }
    }

    /**
     * Получает все возможные типы проектов для указанного пути
     */
    fun detectPossibleProjectTypes(projectPath: Path): List<ProjectType> {
        val projectFile = projectPath.toFile()
        if (!projectFile.exists() || !projectFile.isDirectory) {
            return listOf(ProjectType.UNKNOWN)
        }

        val matchedTypes = mutableListOf<ProjectType>()

        for (detector in projectDetectors) {
            if (matchesDetector(projectPath, detector)) {
                matchedTypes.add(detector.type)
            }
        }

        if (matchedTypes.isEmpty()) {
            return if (hasSourceFiles(projectPath)) {
                listOf(ProjectType.GENERIC)
            } else {
                listOf(ProjectType.UNKNOWN)
            }
        }

        return matchedTypes
    }
}

/**
 * Вспомогательный класс для определения типа проекта
 */
private data class ProjectTypeDetectorEntry(
    val type: ProjectType,
    val files: List<String>,
    val directories: List<String>,
    val additionalCheck: ((Path) -> Boolean)? = null,
    val description: String
)