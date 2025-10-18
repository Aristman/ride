package ru.marslab.ide.ride.ui.templates

/**
 * Базовый интерфейс для HTML-шаблонов
 */
interface HtmlTemplate {
    fun render(variables: Map<String, Any> = emptyMap()): String
}

/**
 * Базовый класс для HTML-шаблонов с утилитами
 */
abstract class BaseHtmlTemplate : HtmlTemplate {
    /**
     * Экранирует HTML-символы
     */
    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    /**
     * Подставляет переменные в шаблон
     */
    protected fun substituteVariables(template: String, variables: Map<String, Any>): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{{$key}}", value.toString())
        }
        return result
    }
}