package ru.marslab.ide.ride.ui.processor

import ru.marslab.ide.ride.ui.config.ChatPanelConfig

/**
 * Обработчик кодовых блоков для чата
 */
class CodeBlockProcessor {

    /**
     * Обрабатывает тройные обратные кавычки (```code```)
     */
    fun processTripleBackticks(text: String, isJcefMode: Boolean): ProcessedCodeResult {
        val pattern = Regex("""```([\w#+.-]+)?[ \t]*\n?([\s\S]*?)```""", RegexOption.IGNORE_CASE)
        var lastIndex = 0
        val finalResult = StringBuilder()
        val codeBlocksFound = mutableListOf<String>()

        pattern.findAll(text).forEach { match ->
            val pre = text.substring(lastIndex, match.range.first)
            if (isJcefMode) {
                finalResult.append(escapeHtml(pre))
            } else {
                finalResult.append(pre)
            }

            val langRaw = (match.groups[1]?.value ?: "").trim().lowercase()
            val normalizedLang = normalizeLanguage(langRaw)
            var code = (match.groups[2]?.value ?: "").trim('\n', '\r')

            // Удаляем общие отступы из кода
            code = removeCommonIndent(code)

            // Применяем pretty printing для JSON
            code = tryProcessJson(code)

            val escaped = escapeCodeForMode(code, isJcefMode)
            val langLabel = langRaw.ifBlank { "Текст" }
            val codeId = generateCodeId(codeBlocksFound.size)

            finalResult.append(createCodeBlockHtml(langLabel, codeId, normalizedLang, escaped))
            codeBlocksFound.add("$normalizedLang: ${code.take(50)}...")
            lastIndex = match.range.last + 1
        }

        // Добавляем оставшийся текст после последнего кодового блока
        if (lastIndex < text.length) {
            val remainingText = text.substring(lastIndex)
            if (isJcefMode) {
                finalResult.append(escapeHtml(remainingText))
            } else {
                finalResult.append(remainingText)
            }
        }

        return ProcessedCodeResult(
            processedText = finalResult.toString(),
            codeBlocksFound = codeBlocksFound
        )
    }

    /**
     * Обрабатывает одинарные обратные кавычки (`code`)
     */
    fun processSingleBackticks(text: String, isJcefMode: Boolean): String {
        val pattern = Regex("""`([^`\s]+)[ \t]*\n?((?:[^\n`]+\n?)+)`""")

        return pattern.replace(text) { match ->
            val lang = match.groupValues[1]
            val code = match.groupValues[2].trim()
            val normalizedLang = normalizeLanguage(lang)
            val escapedCode = escapeCodeForMode(code, isJcefMode)
            val codeId = generateCodeId(0)

            createCodeBlockHtml(lang, codeId, normalizedLang, escapedCode)
        }
    }

    /**
     * Обрабатывает инлайн код (`code`)
     */
    fun processInlineCode(text: String, isJcefMode: Boolean): String {
        val pattern = Regex("""`([^`\s]+)[ \t]*([^{`}]+?)`""")

        return pattern.replace(text) { match ->
            val lang = match.groupValues[1]
            val code = match.groupValues[2].trim()

            // Проверяем, является ли это полноценным кодовым блоком
            if (isCodeBlock(code)) {
                val normalizedLang = normalizeLanguage(lang)
                val escapedCode = escapeCodeForMode(code, isJcefMode)
                val codeId = generateCodeId(0)

                createCodeBlockHtml(lang, codeId, normalizedLang, escapedCode)
            } else {
                // Иначе оставляем как инлайн-код
                "<code>$code</code>"
            }
        }
    }

    /**
     * Нормализует язык программирования
     */
    private fun normalizeLanguage(lang: String): String {
        if (lang.isBlank()) return ChatPanelConfig.DEFAULT_LANGUAGE
        val canonical = ChatPanelConfig.LANGUAGE_ALIASES[lang] ?: lang
        return if (canonical.isBlank()) ChatPanelConfig.DEFAULT_LANGUAGE else canonical
    }

    /**
     * Экранирует HTML в зависимости от режима
     */
    private fun escapeCodeForMode(code: String, isJcefMode: Boolean): String {
        return if (isJcefMode) {
            // В JCEF режиме используем HTML entity для переносов строк
            code.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "&#10;")
        } else {
            escapeHtml(code)
        }
    }

    /**
     * Экранирует HTML символы
     */
    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    /**
     * Удаляет общие начальные отступы из всех строк кода
     */
    private fun removeCommonIndent(code: String): String {
        if (code.isBlank()) return code

        val lines = code.split("\n")
        if (lines.isEmpty()) return code

        // Находим минимальный отступ среди непустых строк
        val minIndent = lines
            .filter { it.isNotBlank() }
            .minOfOrNull { line -> line.takeWhile { it.isWhitespace() }.length } ?: 0

        // Удаляем минимальный отступ из всех строк
        return lines.joinToString("\n") { line ->
            if (line.length >= minIndent) line.substring(minIndent) else line
        }
    }

    /**
     * Пытается отформатировать JSON, если это возможно
     */
    private fun tryProcessJson(code: String): String {
        return try {
            when (normalizeLanguage("")) {
                "json" -> prettyPrintJson(code)
                else -> code
            }
        } catch (e: Exception) {
            code
        }
    }

    /**
     * Pretty print для JSON
     */
    private fun prettyPrintJson(input: String): String {
        val sb = StringBuilder()
        var indent = 0
        var inString = false
        var escape = false

        for (ch in input.trim()) {
            when {
                escape -> {
                    sb.append(ch)
                    escape = false
                }

                ch == '\\' && inString -> {
                    sb.append(ch)
                    escape = true
                }

                ch == '"' -> {
                    inString = !inString
                    sb.append(ch)
                }

                inString -> sb.append(ch)
                ch == '{' || ch == '[' -> {
                    sb.append(ch)
                    sb.append('\n')
                    indent++
                    sb.append("  ".repeat(indent))
                }

                ch == '}' || ch == ']' -> {
                    sb.append('\n')
                    indent = (indent - 1).coerceAtLeast(0)
                    sb.append("  ".repeat(indent))
                    sb.append(ch)
                }

                ch == ',' -> {
                    sb.append(ch)
                    sb.append('\n')
                    sb.append("  ".repeat(indent))
                }

                ch == ':' -> sb.append(": ")
                ch.isWhitespace() -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Проверяет, является ли текст полноценным кодовым блоком
     */
    private fun isCodeBlock(code: String): Boolean {
        return code.contains(';') ||
                code.contains('{') ||
                code.contains('}') ||
                code.contains("fun ") ||
                code.contains("function ") ||
                code.contains("return ") ||
                code.contains("class ") ||
                code.contains("import ")
    }

    /**
     * Генерирует ID для кодового блока
     */
    private fun generateCodeId(index: Int): String {
        return "code_${System.currentTimeMillis()}_$index"
    }

    /**
     * Создает HTML для кодового блока
     */
    private fun createCodeBlockHtml(
        langLabel: String,
        codeId: String,
        normalizedLang: String,
        escapedCode: String
    ): String {
        return "<table class='code-block'><tr><td class='code-lang'>${escapeHtml(langLabel)}</td><td class='code-copy-cell'><a href='${ChatPanelConfig.COPY_LINK_PREFIX}$codeId' class='code-copy-link' title='Скопировать'><span class='code-copy-icon'>${ChatPanelConfig.Icons.COPY_CODE}</span></a></td></tr><tr><td colspan='2'><pre><code class='language-$normalizedLang'>$escapedCode</code></pre></td></tr></table>"
    }
}

/**
 * Результат обработки кодовых блоков
 */
data class ProcessedCodeResult(
    val processedText: String,
    val codeBlocksFound: List<String>
)