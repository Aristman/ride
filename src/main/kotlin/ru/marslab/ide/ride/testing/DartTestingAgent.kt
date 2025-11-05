package ru.marslab.ide.ride.testing

import java.nio.file.Paths

/**
 * Агент генерации тестов для Dart.
 * Генерирует файл test/<name>_test.dart с пакетом `package:test/test.dart`.
 */
class DartTestingAgent : LanguageTestingAgent {
    override fun supports(filePath: String): Boolean = filePath.endsWith(".dart", ignoreCase = true)

    override suspend fun generate(sourceContent: String): List<GeneratedTest> {
        // Находим предполагаемое имя тестируемого юнита по эвристике: имя файла без расширения
        val fileName = extractFileName(sourceContent) // может быть null, fallback по пути отсутствует здесь
        val baseName = (fileName ?: "unit").replace(Regex("[^A-Za-z0-9_]"), "_")
        val testClass = snakeToCamel(baseName) + "Test"
        val testFileName = baseName + "_test.dart"

        val content = buildString {
            appendLine("import 'package:test/test.dart';")
            // Предполагаем, что тестируемый код импортируется пользователем при необходимости
            appendLine()
            appendLine("void main() {")
            appendLine("  group('$baseName', () {")
            appendLine("    test('example', () {")
            appendLine("      // TODO: implement assertions")
            appendLine("      expect(1 + 1, 2);")
            appendLine("    });")
            appendLine("  });")
            appendLine("}")
        }

        return listOf(
            GeneratedTest(
                targetPackage = null, // для Dart пакеты не используются как каталоги для test/
                className = testClass,
                fileName = testFileName,
                content = content,
            )
        )
    }

    private fun extractFileName(sourceContent: String): String? {
        // В Dart имя файла не содержится в самом исходнике. Оставим эвристику пустой.
        return null
    }

    private fun snakeToCamel(name: String): String = name.split('_').joinToString("") { part ->
        if (part.isEmpty()) "" else part.replaceFirstChar { it.uppercaseChar() }
    }
}
