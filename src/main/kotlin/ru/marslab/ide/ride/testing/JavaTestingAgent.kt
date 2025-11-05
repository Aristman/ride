package ru.marslab.ide.ride.testing

/**
 * Простой генератор JUnit5-тестов для Java.
 * MVP: формирует базовый тест-класс с одним пустым тестом.
 */
class JavaTestingAgent : LanguageTestingAgent {
    override fun supports(filePath: String): Boolean = filePath.endsWith(".java", ignoreCase = true)

    override suspend fun generate(sourceContent: String): List<GeneratedTest> {
        val pkg = extractPackage(sourceContent)
        val className = extractPrimaryClass(sourceContent) ?: "Generated"
        val testClass = className + "Test"
        val fileName = "$testClass.java"

        val content = buildString {
            if (!pkg.isNullOrBlank()) appendLine("package $pkg;")
            appendLine()
            appendLine("import org.junit.jupiter.api.Test;")
            appendLine("import static org.junit.jupiter.api.Assertions.*;")
            appendLine()
            appendLine("public class $testClass {")
            appendLine("    @Test")
            appendLine("    void placeholder() {")
            appendLine("        // TODO: implement")
            appendLine("        assertTrue(true);")
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
        val m = Regex("^\\s*package\\s+([a-zA-Z0-9_.]+);", RegexOption.MULTILINE).find(text)
        return m?.groupValues?.getOrNull(1)
    }

    private fun extractPrimaryClass(text: String): String? {
        val re = Regex("(?m)^(?:public\\s+)?class\\s+([A-Za-z0-9_]+)")
        return re.find(text)?.groupValues?.getOrNull(1)
    }
}
