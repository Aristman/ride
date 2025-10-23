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
     * Подставляет переменные в шаблон с поддержкой условных блоков
     * Формат: {{variable}} для подстановки, {{#condition}}...{{/condition}} для условных блоков
     */
    protected fun processTemplate(template: String, variables: Map<String, Any>): String {
        var result = template
        var hasChanges: Boolean
        var previousResult: String

        // Многократная обработка для поддержки вложенных условных блоков
        do {
            hasChanges = false
            previousResult = result

            // Обработка условных блоков
            // {{#condition}}...{{/condition}} - показывается если condition не null/empty/false
            val conditionalRegex = Regex("""\{\{#(\w+)\}\}(.*?)\{\{/\1\}\}""", setOf(RegexOption.DOT_MATCHES_ALL))
            result = conditionalRegex.replace(result) { match ->
                val condition = match.groupValues[1]
                val content = match.groupValues[2]
                val value = variables[condition]

                when {
                    value == null || value == false || value.toString().isEmpty() -> ""
                    value is Collection<*> && value.isEmpty() -> ""
                    else -> {
                        hasChanges = true
                        content
                    }
                }
            }

            // Обработка отрицательных условных блоков
            // {{^condition}}...{{/condition}} - показывается если condition null/empty/false
            val negativeConditionalRegex =
                Regex("""\{\{(\^)\n?(\w+)\}\}(.*?)\{\{/(\2)\}\}""", setOf(RegexOption.DOT_MATCHES_ALL))
            result = negativeConditionalRegex.replace(result) { match ->
                val condition = match.groupValues[2]
                val content = match.groupValues[3]
                val value = variables[condition]

                when {
                    value == null || value == false || value.toString().isEmpty() -> {
                        hasChanges = true
                        content
                    }

                    value is Collection<*> && value.isEmpty() -> {
                        hasChanges = true
                        content
                    }

                    else -> ""
                }
            }

        } while (hasChanges && result != previousResult)

        // Подстановка переменных (без экранирования HTML для специальных переменных)
        variables.forEach { (key, value) ->
            val stringValue = value.toString()
            val escapedValue = when (key) {
                "executionTimeInfo", "outputContent" -> stringValue // Эти переменные уже содержат HTML
                else -> escapeHtml(stringValue)
            }
            result = result.replace("{{$key}}", escapedValue)
        }

        return result
    }
}