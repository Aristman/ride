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

    override suspend fun persistForSource(sourceRelativePath: String, tests: List<GeneratedTest>): List<Path> {
        if (tests.isEmpty()) return emptyList()
        val structure = structureProvider.getProjectStructure()
        return if (structure.buildSystem == BuildSystem.DART) {
            // Для Dart размещаем тест зеркально lib/ -> test/ и добавляем необходимые импорты
            val root = structure.root
            val testRoot = root.resolve("test")
            if (!Files.exists(testRoot)) Files.createDirectories(testRoot)

            val normalized = sourceRelativePath.trimStart('/')
            val sub = if (normalized.startsWith("lib/")) normalized.removePrefix("lib/") else normalized.substringAfterLast('/')
            val testRel = sub.removeSuffix(".dart") + "_test.dart"
            val targetFile = testRoot.resolve(testRel)
            if (!Files.exists(targetFile.parent)) Files.createDirectories(targetFile.parent)

            val pkgName = readDartPackageName(root)
            val saved = mutableListOf<Path>()
            for (t in tests) {
                val content = buildString {
                    if (!t.content.contains("package:test/test.dart")) {
                        appendLine("import 'package:test/test.dart';")
                    }
                    if (pkgName != null) {
                        appendLine("import 'package:${pkgName}/${sub}';")
                    }
                    appendLine()
                    append(t.content)
                }
                Files.writeString(
                    targetFile,
                    content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
                saved += targetFile.toList()
            }
            saved
        } else {
            persist(tests)
        }
    }

    private fun readDartPackageName(root: Path): String? {
        val pubspec = root.resolve("pubspec.yaml")
        return try {
            if (Files.exists(pubspec)) {
                val text = Files.readString(pubspec)
                Regex("^name:\s*([A-Za-z0-9_\-]+)", RegexOption.MULTILINE).find(text)?.groupValues?.getOrNull(1)
            } else null
        } catch (_: Throwable) { null }
    }
}
