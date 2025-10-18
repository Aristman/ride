package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для структурированного блока (JSON/XML)
 */
class StructuredBlockTemplate : BaseHtmlTemplate() {

    fun render(
        content: String,
        format: String = "json"
    ): String {
        return buildString {
            appendLine("<div class=\"structured-block\">")
            appendLine("  <div class=\"structured-header\">")
            appendLine("    <span class=\"format-label\">$format</span>")
            appendLine("    <button class=\"toggle-structured\" onclick=\"toggleStructured(this)\">▼</button>")
            appendLine("  </div>")
            appendLine("  <div class=\"structured-content\">")
            appendLine("    <pre class=\"structured-data\"><code class=\"language-$format\">${escapeHtml(content)}</code></pre>")
            appendLine("  </div>")
            appendLine("</div>")
        }
    }

    override fun render(variables: Map<String, Any>): String {
        return render(
            content = variables["content"] as? String ?: "",
            format = variables["format"] as? String ?: "json"
        )
    }
}