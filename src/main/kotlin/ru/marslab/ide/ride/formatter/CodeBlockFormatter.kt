package ru.marslab.ide.ride.formatter

import ru.marslab.ide.ride.model.agent.AgentOutputType
import ru.marslab.ide.ride.model.agent.FormattedOutput
import ru.marslab.ide.ride.model.agent.FormattedOutputBlock

/**
 * Форматтер для блоков кода
 */
class CodeBlockFormatter {

    /**
     * Форматирует markdown-контент с блоками кода в FormattedOutput
     */
    fun formatAsHtml(content: String): FormattedOutput {
        val blocks = extractBlocks(content)
        return FormattedOutput.multiple(blocks)
    }

    /**
     * Извлекает блоки кода и текстовые блоки из markdown-контента
     */
    fun extractBlocks(markdown: String): List<FormattedOutputBlock> {
        val blocks = mutableListOf<FormattedOutputBlock>()
        var order = 0

        // Регулярное выражение для поиска блоков кода
        val codeBlockRegex = """```(\w+)?\s*\n([\s\S]*?)\n```""".toRegex()

        var lastIndex = 0
        codeBlockRegex.findAll(markdown).forEach { match ->
            // Добавляем текст до блока кода
            val beforeText = markdown.substring(lastIndex, match.range.first).trim()
            if (beforeText.isNotEmpty()) {
                blocks.add(FormattedOutputBlock.markdown(beforeText, order++))
            }

            // Добавляем блок кода
            val language = match.groupValues[1].ifEmpty { "text" }
            val code = match.groupValues[2].trimEnd()

            blocks.add(FormattedOutputBlock.codeBlock(
                content = code,
                language = language,
                order = order++
            ))

            lastIndex = match.range.last + 1
        }

        // Добавляем оставшийся текст после последнего блока кода
        val afterText = markdown.substring(lastIndex).trim()
        if (afterText.isNotEmpty()) {
            blocks.add(FormattedOutputBlock.markdown(afterText, order++))
        }

        // Если блоки кода не найдены, создаем один markdown блок
        if (blocks.isEmpty()) {
            blocks.add(FormattedOutputBlock.markdown(markdown, 0))
        }

        return blocks
    }

    /**
     * Создает HTML-представление блока кода
     */
    fun createCodeBlock(code: String, language: String = ""): String {
        return buildString {
            appendLine("<div class=\"code-block-container\">")
            appendLine("  <div class=\"code-block-header\">")
            appendLine("    <div class=\"code-block-info\">")
            if (language.isNotEmpty()) {
                appendLine("      <span class=\"code-language\">$language</span>")
            } else {
                appendLine("      <span class=\"code-language\">text</span>")
            }
            appendLine("    </div>")
            appendLine("    <div class=\"code-block-actions\">")
            appendLine("      <button class=\"code-copy-btn\" onclick=\"copyCodeBlock(this)\" title=\"Copy code\">")
            appendLine("        <span class=\"copy-icon\">📋</span>")
            appendLine("        <span class=\"copy-text\">Copy</span>")
            appendLine("      </button>")
            appendLine("    </div>")
            appendLine("  </div>")
            appendLine("  <div class=\"code-block-body\">")
            appendLine("    <pre class=\"code-content\"><code class=\"language-$language\">${escapeHtml(code)}</code></pre>")
            appendLine("  </div>")
            appendLine("</div>")
        }
    }

    /**
     * Создает HTML-представление для нескольких блоков кода и текста
     */
    fun wrapInTemplate(blocks: List<FormattedOutputBlock>, text: String? = null): String {
        return buildString {
            appendLine("<div class=\"multi-block-container\">")

            blocks.forEachIndexed { index, block ->
                appendLine("  <div class=\"block-item\" data-block-type=\"${block.type}\" data-order=\"${block.order}\">")

                when (block.type) {
                    AgentOutputType.CODE_BLOCKS -> {
                        val language = block.metadata["language"] as? String ?: ""
                        append(createCodeBlock(block.content, language))
                    }
                    AgentOutputType.MARKDOWN -> {
                        appendLine("    <div class=\"markdown-block\">")
                        appendLine("      <div class=\"markdown-content\">${block.content}</div>")
                        appendLine("    </div>")
                    }
                    else -> {
                        appendLine("    <div class=\"generic-block\">")
                        appendLine("      <div class=\"generic-content\">${block.content}</div>")
                        appendLine("    </div>")
                    }
                }

                appendLine("  </div>")

                // Добавляем разделитель между блоками (кроме последнего)
                if (index < blocks.size - 1) {
                    appendLine("  <div class=\"block-separator\"></div>")
                }
            }

            if (text != null && text.isNotEmpty()) {
                appendLine("  <div class=\"text-block\">")
                appendLine("    <div class=\"text-content\">$text</div>")
                appendLine("  </div>")
            }

            appendLine("</div>")
        }
    }

    /**
     * Экранирует HTML-символы
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    /**
     * Извлекает отдельные блоки кода из текста
     */
    data class CodeBlock(
        val language: String,
        val content: String,
        val startIndex: Int,
        val endIndex: Int
    )

    fun extractCodeBlocks(markdown: String): List<CodeBlock> {
        val blocks = mutableListOf<CodeBlock>()
        val codeBlockRegex = """```(\w+)?\s*\n([\s\S]*?)\n```""".toRegex()

        codeBlockRegex.findAll(markdown).forEach { match ->
            val language = match.groupValues[1].ifEmpty { "text" }
            val content = match.groupValues[2].trimEnd()

            blocks.add(CodeBlock(
                language = language,
                content = content,
                startIndex = match.range.first,
                endIndex = match.range.last
            ))
        }

        return blocks
    }

    /**
     * Разделяет текст на части между блоками кода
     */
    fun splitByCodeBlocks(markdown: String): List<String> {
        val parts = mutableListOf<String>()
        val codeBlocks = extractCodeBlocks(markdown)

        var lastIndex = 0
        codeBlocks.forEach { block ->
            val beforeText = markdown.substring(lastIndex, block.startIndex).trim()
            if (beforeText.isNotEmpty()) {
                parts.add(beforeText)
            }
            lastIndex = block.endIndex + 1
        }

        val afterText = markdown.substring(lastIndex).trim()
        if (afterText.isNotEmpty()) {
            parts.add(afterText)
        }

        return parts
    }

    /**
     * Создает простой текстовый формат (fallback)
     */
    fun formatAsText(content: String): FormattedOutput {
        return FormattedOutput.markdown(content)
    }
}