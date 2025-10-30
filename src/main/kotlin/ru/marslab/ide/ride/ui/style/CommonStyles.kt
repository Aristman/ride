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
     * Загружает CSS стили для source links
     */
    fun loadSourceLinksCss(): String {
        return """
        .source-links-container {
            margin: 12px 0;
            padding: 12px;
            background: rgba(109, 143, 216, 0.1);
            border: 1px solid rgba(109, 143, 216, 0.3);
            border-radius: 8px;
            font-family: var(--font-family, JetBrains Mono, Consolas, Monaco, monospace);
            font-size: var(--font-size, 12px);
        }

        .source-links-header {
            font-weight: bold;
            color: var(--text-primary, #e6e6e6);
            margin-bottom: 8px;
            font-size: 12px;
        }

        .source-link-item {
            display: flex;
            align-items: center;
            padding: 6px 0;
            border-bottom: 1px solid rgba(109, 143, 216, 0.1);
        }

        .source-link-item:last-child {
            border-bottom: none;
        }

        .source-link-index {
            color: var(--text-secondary, #9aa0a6);
            font-size: 11px;
            width: 20px;
            flex-shrink: 0;
        }

        .source-link-content {
            flex-grow: 1;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
        }

        .source-link-file {
            color: var(--text-primary, #e6e6e6);
            font-size: 11px;
            font-weight: 500;
        }

        .source-link-lines {
            color: var(--text-secondary, #9aa0a6);
            font-size: 10px;
        }

        .source-link-action {
            color: #6d8fd8;
            font-size: 11px;
            cursor: pointer;
            padding: 2px 6px;
            border-radius: 4px;
            text-decoration: none;
            white-space: nowrap;
            transition: background-color 0.2s ease;
        }

        .source-link-action:hover {
            background-color: rgba(109, 143, 216, 0.2);
        }
        """.trimIndent()
    }

    /**
     * Возвращает объединенные стили для JCEF режима
     */
    fun getJcefStyles(): String {
        return """
            ${loadCommonCss()}

            ${loadAgentOutputCss()}

            ${loadSourceLinksCss()}
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