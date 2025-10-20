package ru.marslab.ide.ride.ui.templates

/**
 * HTML-шаблон для JavaScript кода интерактивности элементов
 */
class InteractionScriptsTemplate : BaseHtmlTemplate() {

    companion object {
        private const val SCRIPT_FILE = "interaction-scripts.js"
        private var cachedScript: String? = null

        private fun getScript(): String {
            return cachedScript ?: TemplateLoader.loadHtmlTemplate(SCRIPT_FILE).also {
                cachedScript = it
            }
        }
    }

    fun createScripts(): String {
        return getScript()
    }

    override fun render(variables: Map<String, Any>): String {
        return createScripts()
    }
}