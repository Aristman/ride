package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для терминального блока
 */
class TerminalBlockTemplate : BaseHtmlTemplate() {

    fun render(
        command: String,
        exitCode: Int,
        executionTime: Long,
        success: Boolean,
        content: String
    ): String {
        return buildString {
            appendLine("<div class=\"terminal-output\">")
            appendLine("  <div class=\"terminal-header\">")
            appendLine("    <div class=\"terminal-title\">")
            appendLine("      <span class=\"terminal-icon\">🖥️</span>")
            appendLine("      <span class=\"terminal-text\">Terminal Output</span>")
            appendLine("    </div>")
            appendLine("    <div class=\"terminal-status\">")
            if (success) {
                appendLine("      <span class=\"status-success\">✅ Success</span>")
            } else {
                appendLine("      <span class=\"status-error\">❌ Error</span>")
            }
            appendLine("    </div>")
            appendLine("  </div>")

            if (command.isNotEmpty()) {
                appendLine("  <div class=\"terminal-info\">")
                appendLine("    <div class=\"terminal-command\">")
                appendLine("      <span class=\"command-label\">Command:</span>")
                appendLine("      <span class=\"command-value\">${escapeHtml(command)}</span>")
                appendLine("    </div>")
                appendLine("    <div class=\"terminal-exit-code\">")
                appendLine("      <span class=\"exit-code-label\">Exit Code:</span>")
                appendLine("      <span class=\"exit-code-value\">$exitCode</span>")
                appendLine("    </div>")
                if (executionTime > 0) {
                    appendLine("    <div class=\"terminal-execution-time\">")
                    appendLine("      <span class=\"execution-time-label\">Execution Time:</span>")
                    appendLine("      <span class=\"execution-time-value\">${executionTime}ms</span>")
                    appendLine("    </div>")
                }
                appendLine("  </div>")
            }

            appendLine("  <div class=\"terminal-body\">")
            if (content.trim().isNotEmpty()) {
                appendLine("    <pre class=\"terminal-content\">${escapeHtml(content)}</pre>")
            } else {
                appendLine("    <pre class=\"terminal-content\">(No output)</pre>")
            }
            appendLine("  </div>")
            appendLine("</div>")
        }
    }

    override fun render(variables: Map<String, Any>): String {
        return render(
            command = variables["command"] as? String ?: "",
            exitCode = variables["exitCode"] as? Int ?: 0,
            executionTime = variables["executionTime"] as? Long ?: 0L,
            success = variables["success"] as? Boolean ?: true,
            content = variables["content"] as? String ?: ""
        )
    }
}