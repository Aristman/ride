package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для блока кода
 */
class CodeBlockTemplate : BaseHtmlTemplate() {

    companion object {
        private const val TEMPLATE_FILE = "code-block.html"
        private var cachedTemplate: String? = null

        private fun getTemplate(): String {
            return cachedTemplate ?: TemplateLoader.loadHtmlTemplate(TEMPLATE_FILE).also {
                cachedTemplate = it
            }
        }
    }

    fun render(
        content: String,
        language: String = "",
        fileName: String? = null
    ): String {
        val variables = mutableMapOf(
            "content" to content,
            "language" to if (language.isNotEmpty()) language else "text"
        )

        if (fileName != null) {
            variables["fileName"] = fileName
        }

        return processTemplate(getTemplate(), variables)
    }

    override fun render(variables: Map<String, Any>): String {
        return render(
            content = variables["content"] as? String ?: "",
            language = variables["language"] as? String ?: "",
            fileName = variables["fileName"] as? String
        )
    }
}