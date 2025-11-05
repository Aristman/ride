package ru.marslab.ide.ride.testing

import ru.marslab.ide.ride.agent.LLMProviderFactory
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.settings.PluginSettings
import com.intellij.openapi.components.service

/**
 * LLM-агент генерации тестов для Java. Импорты и содержимое полностью формирует LLM.
 */
class JavaTestingAgent : LanguageTestingAgent {
    override fun supports(filePath: String): Boolean = filePath.endsWith(".java", ignoreCase = true)

    override suspend fun generate(sourceContent: String): List<GeneratedTest> {
        val pkg = extractPackage(sourceContent)
        val className = extractPrimaryClass(sourceContent) ?: "Generated"
        val testClass = className + "Test"
        val fileName = "$testClass.java"

        val provider = LLMProviderFactory.createLLMProvider()
        val settings = service<PluginSettings>()
        val params = LLMParameters(
            temperature = settings.temperature,
            maxTokens = settings.maxTokens
        )

        val systemPrompt = buildSystemPrompt()
        val userMessage = buildUserMessage(pkg, className, sourceContent)

        val content = sanitizeJavaTest(
            provider.sendRequest(
                systemPrompt = systemPrompt,
                userMessage = userMessage,
                conversationHistory = emptyList(),
                parameters = params
            ).content
        )

        return listOf(
            GeneratedTest(
                targetPackage = pkg,
                className = testClass,
                fileName = fileName,
                content = content
            )
        )
    }

    private fun buildSystemPrompt(): String = """
        You are an expert Java/JVM test writer.
        Task: Generate a fully working JUnit 5 test file for the provided Java source.

        STRICT REQUIREMENTS:
        - Output ONLY the Java test file content (no markdown, no backticks).
        - Put all REQUIRED imports at the top.
        - Use package declaration matching the original source package if provided.
        - Use JUnit 5 API (org.junit.jupiter.*) and meaningful assertions.
        - The file must compile and run with standard Gradle/Maven JUnit 5 setup.
    """.trimIndent()

    private fun buildUserMessage(pkg: String?, className: String, sourceContent: String): String = buildString {
        if (!pkg.isNullOrBlank()) appendLine("// PACKAGE_NAME: $pkg")
        appendLine("// SOURCE_CLASS_NAME: $className")
        appendLine("Source Java file content:")
        appendLine("""
            ---BEGIN SOURCE---
            $sourceContent
            ---END SOURCE---
        """.trimIndent())
        appendLine()
        appendLine("Generate the test file content now:")
    }

    private fun sanitizeJavaTest(raw: String): String {
        var s = raw.trim()
        s = s.removePrefix("```java").removePrefix("```").removeSuffix("```").trim()
        return s
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
