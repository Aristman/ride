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
        val variables = mapOf(
            "command" to command,
            "exitCode" to exitCode,
            "executionTime" to executionTime,
            "success" to success,
            "content" to content,
            "statusIcon" to if (success) "✅" else "❌",
            "statusText" to if (success) "Success" else "Error",
            "commandInfo" to command.isNotEmpty()
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