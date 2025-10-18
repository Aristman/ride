package ru.marslab.ide.ride.ui.style

import com.intellij.openapi.diagnostic.thisLogger

/**
 * Управление общими CSS стилями для обоих режимов (JCEF и fallback)
 */
object CommonStyles {
    private val logger = thisLogger()

    /**
     * Загружает общие CSS стили из ресурсов
     */
    fun loadCommonCss(): String {
        return this::class.java.getResourceAsStream("/css/common-styles.css")
            ?.use { it.bufferedReader().readText() }
            ?.also {
                logger.debug("Общие CSS стили успешно загружены")
            }
            ?: run {
                logger.error("Не удалось загрузить common-styles.css")
                "/* Общие стили не найдены */"
            }
    }

    /**
     * Загружает CSS стили для форматированного вывода агентов
     */
    fun loadAgentOutputCss(): String {
        return this::class.java.getResourceAsStream("/css/agent-output-styles.css")
            ?.use { it.bufferedReader().readText() }
            ?.also {
                logger.debug("CSS стили для форматированного вывода успешно загружены")
            }
            ?: run {
                logger.error("Не удалось загрузить agent-output-styles.css")
                "/* Стили форматированного вывода не найдены */"
            }
    }

    /**
     * Возвращает объединенные стили для JCEF режима
     */
    fun getJcefStyles(): String {
        return """
            ${loadCommonCss()}

            ${loadAgentOutputCss()}
        """.trimIndent()
    }

    /**
     * Возвращает объединенные стили для fallback режима с заменой переменных
     */
    fun getFallbackStyles(themeReplacements: Map<String, String>): String {
        var styles = getJcefStyles()

        // Заменяем CSS переменные на конкретные значения для fallback режима
        themeReplacements.forEach { (variable, value) ->
            styles = styles.replace("var(--$variable)", value)
        }

        return styles
    }
}