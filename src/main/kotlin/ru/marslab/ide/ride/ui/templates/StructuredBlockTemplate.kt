package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для структурированного блока (JSON/XML)
 */
class StructuredBlockTemplate : BaseHtmlTemplate() {

    companion object {
        private const val TEMPLATE_FILE = "structured-block.html"
        private var cachedTemplate: String? = null

        private fun getTemplate(): String {
            return cachedTemplate ?: TemplateLoader.loadHtmlTemplate(TEMPLATE_FILE).also {
                cachedTemplate = it
            }
        }
    }

    fun render(
        content: String,
        format: String = "json"
    ): String {
        val variables = mapOf(
            "content" to content,
            "format" to format
        )

        return processTemplate(getTemplate(), variables)
    }

    override fun render(variables: Map<String, Any>): String {
        return render(
            content = variables["content"] as? String ?: "",
            format = variables["format"] as? String ?: "json"
        )
    }
}