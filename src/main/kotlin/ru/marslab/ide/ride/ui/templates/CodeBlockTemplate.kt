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
        // Значение языка по умолчанию как в прежней логике
        val actualLanguage = if (language.isBlank()) "text" else language

        val variables = buildMap<String, Any> {
            put("language", actualLanguage)
            // Контент кода должен быть экранирован; processTemplate экранирует по умолчанию
            put("content", content)
            // Для условной секции {{#fileName}} ... {{/fileName}} пустое значение скрывает блок
            fileName?.takeIf { it.isNotBlank() }?.let { put("fileName", it) }
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