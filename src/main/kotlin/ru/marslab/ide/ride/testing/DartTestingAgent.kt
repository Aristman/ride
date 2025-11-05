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
            temperature = (settings.temperature ?: 0.2f).coerceIn(0f, 1f),
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
        You are an expert Dart and Flutter test writer.
        Task: Generate a fully working Dart unit test file for the provided source code.

        Strict requirements:
        - Use package:test/test.dart.
        - Produce a single, runnable test file content only. No markdown, no code fences.
        - Include main()/group()/test() with meaningful assertions based on the source.
        - Avoid external dependencies beyond 'test'.
        - Do not include placeholder TODOs; create minimal but valid tests.
        - If the source defines classes or functions, import it as package import (the exact path will be injected by the tool on save).
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
}
