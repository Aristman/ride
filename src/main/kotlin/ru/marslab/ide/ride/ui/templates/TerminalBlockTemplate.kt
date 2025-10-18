package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для терминального блока
 */
class TerminalBlockTemplate : BaseHtmlTemplate() {

    companion object {
        private const val TEMPLATE_FILE = "terminal-block.html"
        private var cachedTemplate: String? = null

        private fun getTemplate(): String {
            return cachedTemplate ?: TemplateLoader.loadHtmlTemplate(TEMPLATE_FILE).also {
                cachedTemplate = it
            }
        }
    }

    fun render(
        command: String,
        exitCode: Int,
        executionTime: Long,
        success: Boolean,
        content: String
    ): String {
        val executionTimeInfo = if (executionTime > 0) {
            """
    <div class="terminal-execution-time">
      <span class="execution-time-label">Execution Time:</span>
      <span class="execution-time-value">${executionTime}ms</span>
    </div>""".trimIndent()
        } else {
            ""
        }

        val outputContent = if (content.trim().isNotEmpty()) {
            """<pre class="terminal-content">${escapeHtml(content)}</pre>"""
        } else {
            """<pre class="terminal-content">(No output)</pre>"""
        }

        val variables = mapOf(
            "command" to command,
            "exitCode" to exitCode,
            "executionTime" to executionTime,
            "success" to success,
            "content" to content,
            "statusIcon" to if (success) "✅" else "❌",
            "statusText" to if (success) "Success" else "Error",
            "executionTimeInfo" to executionTimeInfo,
            "outputContent" to outputContent
        )

        return processTemplate(getTemplate(), variables)
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