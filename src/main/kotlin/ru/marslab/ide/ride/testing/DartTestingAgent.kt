package ru.marslab.ide.ride.testing

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.agent.LLMProviderFactory
import ru.marslab.ide.ride.model.chat.ConversationMessage
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.settings.PluginSettings

/**
 * LLM-агент генерации тестов для Dart.
 * Формирует системный промпт и запрашивает у LLM корректный содержимый *_test.dart.
 * Сохранение имени/пути теста делает персистер (зеркалит lib/ -> test/),
 * поэтому здесь возвращаем контент и служебные имена.
 */
class DartTestingAgent : LanguageTestingAgent {
    override fun supports(filePath: String): Boolean = filePath.endsWith(".dart", ignoreCase = true)

    override suspend fun generate(sourceContent: String): List<GeneratedTest> {
        val baseName = "unit" // имя файла определяет персистер по исходному пути

        val systemPrompt = buildSystemPrompt()
        val userMessage = buildUserMessage(sourceContent)

        val provider = LLMProviderFactory.createLLMProvider()
        val settings = service<PluginSettings>()
        val params = LLMParameters(
            temperature = settings.temperature,
            maxTokens = settings.maxTokens
        )

        val content = if (provider.isAvailable()) {
            val resp = provider.sendRequest(
                systemPrompt = systemPrompt,
                userMessage = userMessage,
                conversationHistory = emptyList<ConversationMessage>(),
                parameters = params
            )
            sanitizeDartTest(resp.content)
        } else {
            // Fallback: минимальный валидный шаблон
            defaultTestTemplate(baseName)
        }

        val testClass = snakeToCamel(baseName) + "Test"
        val testFileName = baseName + "_test.dart"

        return listOf(
            GeneratedTest(
                targetPackage = null,
                className = testClass,
                fileName = testFileName,
                content = content,
            )
        )
    }

    private fun buildSystemPrompt(): String = """
        You are an expert Dart/Flutter test writer.
        Task: Generate a fully working Dart test file for the provided source code.

        Context metadata will be provided at the beginning of the user message as comments:
        - // PACKAGE_NAME: <name>
        - // LIB_RELATIVE_PATH: <path under lib/>
        - // PACKAGE_IMPORT: package:<name>/<lib_relative_path>

        STRICT REQUIREMENTS:
        - Output ONLY the Dart test file content (no markdown, no backticks).
        - Put all required imports at the top, including:
          * import 'package:test/test.dart';
          * import using the exact value from PACKAGE_IMPORT to import the code under test.
        - No placeholder paths, no comments like "replace with..."; paths must be exact as provided.
        - Use main()/group()/test() with meaningful assertions based on the source code.
        - Avoid external dependencies beyond 'test'.
        - The file must be runnable by `dart test` as-is.
    """.trimIndent()

    private fun buildUserMessage(sourceContent: String): String = buildString {
        appendLine("Source Dart file content:")
        appendLine("""
            ---BEGIN SOURCE---
            $sourceContent
            ---END SOURCE---
        """.trimIndent())
        appendLine()
        appendLine("Generate the test file content now:")
    }

    private fun sanitizeDartTest(raw: String): String {
        // Удаляем возможные Markdown-кодфенсы и лишние префиксы
        var s = raw.trim()
        s = s.removePrefix("```dart").removePrefix("```").removeSuffix("```").trim()
        // Гарантируем наличие main()
        return if (!s.contains("void main()")) defaultTestTemplate("unit") else s
    }

    private fun defaultTestTemplate(baseName: String): String = """
        import 'package:test/test.dart';

        void main() {
          group('$baseName', () {
            test('example', () {
              expect(1 + 1, 2);
            });
          });
        }
    """.trimIndent()

    private fun snakeToCamel(name: String): String = name.split('_').joinToString("") { part ->
        if (part.isEmpty()) "" else part.replaceFirstChar { it.uppercaseChar() }
    }

    companion object {
        /**
         * Обогащает исходник метаданными для LLM: PACKAGE_NAME / LIB_RELATIVE_PATH / PACKAGE_IMPORT
         */
        fun enrichWithDartMetadata(root: java.nio.file.Path, filePath: String, source: String): String {
            val libRelative = filePath.removePrefix("./").let { if (it.startsWith("lib/")) it.removePrefix("lib/") else it }
            val pkgName = readDartPackageName(root)
            val importPath = if (pkgName != null) "package:${pkgName}/${libRelative}" else libRelative
            return buildString {
                appendLine("// PACKAGE_NAME: ${pkgName ?: "<unknown>"}")
                appendLine("// LIB_RELATIVE_PATH: ${libRelative}")
                appendLine("// PACKAGE_IMPORT: ${importPath}")
                appendLine("// Use PACKAGE_IMPORT for importing the source under test in the generated test file.")
                appendLine()
                append(source)
            }
        }

        private fun readDartPackageName(root: java.nio.file.Path): String? {
            val pubspec = root.resolve("pubspec.yaml")
            return try {
                if (java.nio.file.Files.exists(pubspec)) {
                    val text = java.nio.file.Files.readString(pubspec)
                    Regex("^name:\\s*([A-Za-z0-9_\\-]+)", RegexOption.MULTILINE).find(text)?.groupValues?.getOrNull(1)
                } else null
            } catch (_: Throwable) { null }
        }
    }
}
