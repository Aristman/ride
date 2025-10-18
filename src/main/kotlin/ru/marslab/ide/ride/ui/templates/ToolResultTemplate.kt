package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для блока результата инструмента
 */
class ToolResultTemplate : BaseHtmlTemplate() {

    companion object {
        private const val TEMPLATE_FILE = "tool-result.html"
        private var cachedTemplate: String? = null

        private fun getTemplate(): String {
            return cachedTemplate ?: TemplateLoader.loadHtmlTemplate(TEMPLATE_FILE).also {
                cachedTemplate = it
            }
        }
    }

    fun render(
        content: String,
        toolName: String,
        operationType: String = "",
        success: Boolean = true
    ): String {
        val variables = mutableMapOf(
            "content" to content,
            "toolName" to toolName,
            "success" to success
        )

        if (operationType.isNotEmpty()) {
            variables["operationType"] = operationType
        }

        return processTemplate(getTemplate(), variables)
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