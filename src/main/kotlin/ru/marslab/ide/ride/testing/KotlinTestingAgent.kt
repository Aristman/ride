package ru.marslab.ide.ride.testing

import java.nio.file.Files
import java.nio.file.Path

/**
 * Простой генератор JUnit5-тестов для Kotlin.
 * MVP: на основе имени класса и пакета формирует базовый тест-класс с одним пустым тестом.
 */
class KotlinTestingAgent : LanguageTestingAgent {
    override fun supports(filePath: String): Boolean = filePath.endsWith(".kt", ignoreCase = true)

    override suspend fun generate(sourceContent: String): List<GeneratedTest> {
        val pkg = extractPackage(sourceContent)
        val className = extractPrimaryClass(sourceContent) ?: "Generated"
        val testClass = className + "Test"
        val fileName = "$testClass.kt"

        val content = buildString {
            if (!pkg.isNullOrBlank()) appendLine("package $pkg")
            appendLine()
            appendLine("import org.junit.jupiter.api.Test")
            appendLine("import org.junit.jupiter.api.Assertions.*")
            appendLine()
            appendLine("class $testClass {")
            appendLine("    @Test")
            appendLine("    fun placeholder() {")
            appendLine("        // TODO: implement")
            appendLine("        assertTrue(true)")
            appendLine("    }")
            appendLine("}")
        }

        return listOf(
            GeneratedTest(
                targetPackage = pkg,
                className = testClass,
                fileName = fileName,
                content = content
            )
        )
    }

    private fun extractPackage(text: String): String? {
        val m = Regex("^\\s*package\\s+([a-zA-Z0-9_.]+)", RegexOption.MULTILINE).find(text)
        return m?.groupValues?.getOrNull(1)
    }

    private fun extractPrimaryClass(text: String): String? {
        // Находим имя первого public class/object/data class
        val re = Regex("(?m)^(?:public\\s+)?(?:data\\s+)?(?:class|object)\\s+([A-Za-z0-9_]+)")
        return re.find(text)?.groupValues?.getOrNull(1)
    }
}
