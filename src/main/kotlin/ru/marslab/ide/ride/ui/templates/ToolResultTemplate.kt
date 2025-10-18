package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для блока результата инструмента
 */
class ToolResultTemplate : BaseHtmlTemplate() {

    fun render(
        content: String,
        toolName: String,
        operationType: String = "",
        success: Boolean = true
    ): String {
        return buildString {
            appendLine("<div class=\"tool-result-block\">")
            appendLine("  <div class=\"tool-result-header\">")
            appendLine("    <div class=\"tool-info\">")
            appendLine("      <span class=\"tool-icon\">🔧</span>")
            appendLine("      <span class=\"tool-name\">$toolName</span>")
            if (operationType.isNotEmpty()) {
                appendLine("      <span class=\"operation-type\">$operationType</span>")
            }
            appendLine("    </div>")
            appendLine("    <div class=\"tool-status\">")
            if (success) {
                appendLine("      <span class=\"status-success\">✅ Успешно</span>")
            } else {
                appendLine("      <span class=\"status-error\">❌ Ошибка</span>")
            }
            appendLine("    </div>")
            appendLine("  </div>")

            if (content.trim().isNotEmpty()) {
                appendLine("  <div class=\"tool-result-content\">")
                appendLine("    <div class=\"result-value\">${escapeHtml(content)}</div>")
                appendLine("  </div>")
            }

            appendLine("</div>")
        }
    }

    override fun render(variables: Map<String, Any>): String {
        return render(
            content = variables["content"] as? String ?: "",
            toolName = variables["toolName"] as? String ?: "Unknown Tool",
            operationType = variables["operationType"] as? String ?: "",
            success = variables["success"] as? Boolean ?: true
        )
    }
}