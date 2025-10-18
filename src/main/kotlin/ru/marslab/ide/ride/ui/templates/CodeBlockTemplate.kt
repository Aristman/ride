package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для блока кода
 */
class CodeBlockTemplate : BaseHtmlTemplate() {

    fun render(
        content: String,
        language: String = "",
        fileName: String? = null
    ): String {
        return buildString {
            appendLine("<div class=\"code-block-container\">")
            appendLine("  <div class=\"code-block-header\">")
            appendLine("    <div class=\"code-block-info\">")
            if (language.isNotEmpty()) {
                appendLine("      <span class=\"code-language\">$language</span>")
            } else {
                appendLine("      <span class=\"code-language\">text</span>")
            }
            if (fileName != null) {
                appendLine("      <span class=\"code-filename\">$fileName</span>")
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
            appendLine("    <pre class=\"code-content\"><code class=\"language-$language\">${escapeHtml(content)}</code></pre>")
            appendLine("  </div>")
            appendLine("</div>")
        }
    }

    override fun render(variables: Map<String, Any>): String {
        return render(
            content = variables["content"] as? String ?: "",
            language = variables["language"] as? String ?: "",
            fileName = variables["fileName"] as? String
        )
    }
}