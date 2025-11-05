package ru.marslab.ide.ride.testing

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.agent.LLMProviderFactory
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

        val content = sanitizeDartTest(
            provider.sendRequest(
                systemPrompt = systemPrompt,
                userMessage = userMessage,
                conversationHistory = emptyList(),
                parameters = params
            ).content
        )

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
        Ты — эксперт по написанию тестов на Dart/Flutter.
        Задача: сгенерировать полностью рабочий файл тестов Dart для переданного исходного кода.

        В начале пользовательского сообщения будут даны метаданные в виде комментариев:
        - // PACKAGE_NAME: <имя пакета>
        - // LIB_RELATIVE_PATH: <путь внутри lib/>
        - // PACKAGE_IMPORT: package:<имя_пакета>/<путь_внутри_lib>
        - // PUBSPEC_BEGIN ... PUBSPEC_END — содержимое pubspec.yaml (если доступно)

        СТРОГИЕ ТРЕБОВАНИЯ:
        - Выводи ТОЛЬКО содержимое файла теста Dart (без markdown и без тройных кавычек).
        - В начале файла укажи все НЕОБХОДИМЫЕ импорты.
        - Обязательно добавляй импорты из исходного файла если в тестах используются какие-либо классы.
        - Код под тестом импортируй строго значением из PACKAGE_IMPORT.
        - При выборе фреймворка тестирования ориентируйся на содержимое pubspec.yaml (если есть):
          * при наличии flutter_test в dev_dependencies используй import 'package:flutter_test/flutter_test.dart';
          * иначе используй стандартный import 'package:test/test.dart';
        - Никаких плейсхолдеров и комментариев вида "replace with..." — только точные пути.
        - Используй main()/group()/test() и осмысленные проверки на основе исходника.
        - Сгенерированный файл должен запускаться командой dart test/fluter test без ручных правок.
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
        return s
    }

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
            val pubspecText = readPubspec(root)
            return buildString {
                appendLine("// PACKAGE_NAME: ${pkgName ?: "<unknown>"}")
                appendLine("// LIB_RELATIVE_PATH: ${libRelative}")
                appendLine("// PACKAGE_IMPORT: ${importPath}")
                appendLine("// Use PACKAGE_IMPORT for importing the source under test in the generated test file.")
                if (pubspecText != null) {
                    appendLine("// PUBSPEC_BEGIN")
                    appendLine(pubspecText)
                    appendLine("// PUBSPEC_END")
                }
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

        private fun readPubspec(root: java.nio.file.Path): String? {
            val pubspec = root.resolve("pubspec.yaml")
            return try {
                if (java.nio.file.Files.exists(pubspec)) java.nio.file.Files.readString(pubspec) else null
            } catch (_: Throwable) { null }
        }
    }
}
