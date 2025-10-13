package ru.marslab.ide.ride.ui.manager

import com.intellij.ui.components.JBScrollPane
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.theme.ThemeTokens
import ru.marslab.ide.ride.ui.chat.JcefChatView
import ru.marslab.ide.ride.ui.config.ChatPanelConfig
import java.awt.Dimension
import javax.swing.JEditorPane

/**
 * Управляет HTML-документом чата, поддерживая JCEF и fallback режимы
 */
class HtmlDocumentManager(
    private val settings: PluginSettings,
    private val jcefView: JcefChatView?
) {

    private val htmlBuffer = StringBuilder()
    private var loadingStart: Int = -1
    private var loadingEnd: Int = -1

    // Fallback HTML-компонент для режима без JCEF
    private val chatHistoryArea: JEditorPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
    }

    /**
     * Инициализирует HTML документ
     */
    fun initialize() {
        htmlBuffer.setLength(0)
        loadingStart = -1
        loadingEnd = -1

        if (jcefView != null) {
            jcefView.clear()
            jcefView.setTheme(ThemeTokens.fromSettings(settings).toJcefMap())
        } else {
            initializeFallbackHtml()
        }
    }

    /**
     * Добавляет HTML контент в документ
     */
    fun appendHtml(chunk: String) {
        if (jcefView != null) {
            htmlBuffer.append("\n").append(chunk)
            jcefView.appendHtml(chunk)
        } else {
            val closing = "</body>\n</html>"
            val idx = htmlBuffer.indexOf(closing)
            if (idx != -1) htmlBuffer.delete(idx, htmlBuffer.length)
            htmlBuffer.append("\n").append(chunk)
            refreshEditor()
        }
    }

    /**
     * Добавляет HTML контент и возвращает диапазон добавленного текста
     */
    fun appendHtmlWithRange(chunk: String): Pair<Int, Int> {
        if (jcefView != null) {
            loadingStart = htmlBuffer.length
            htmlBuffer.append("\n").append(chunk)
            loadingEnd = htmlBuffer.length
            jcefView.appendHtml(chunk)
            return loadingStart to loadingEnd
        } else {
            val closing = "</body>\n</html>"
            val idx = htmlBuffer.indexOf(closing)
            if (idx != -1) htmlBuffer.delete(idx, htmlBuffer.length)
            loadingStart = htmlBuffer.length
            htmlBuffer.append("\n").append(chunk)
            loadingEnd = htmlBuffer.length
            refreshEditor()
            return loadingStart to loadingEnd
        }
    }

    /**
     * Устанавливает полный HTML контент документа
     */
    fun setBody(html: String) {
        if (jcefView != null) {
            jcefView.setBody(html)
        } else {
            val fullHtml = StringBuilder(html).append("\n</body>\n</html>").toString()
            chatHistoryArea.text = fullHtml
            chatHistoryArea.caretPosition = chatHistoryArea.document.length
        }
    }

    /**
     * Удаляет последний системный блок (например, индикатор загрузки) с fade-out анимацией
     * ВАЖНО: Удаляет только сообщения с маркером загрузки, не трогает постоянные системные сообщения
     */
    fun removeLastSystemMessage() {
        if (jcefView != null) {
            // Для JCEF используем fade-out анимацию через JavaScript
            // Удаляем только сообщения с маркером загрузки, не трогаем постоянные системные сообщения
            jcefView.removeLoadingSystemMessage()
            // Очищаем буфер только если есть маркеры загрузки
            if (loadingStart != -1 && loadingEnd != -1 &&
                loadingStart < loadingEnd && loadingEnd <= htmlBuffer.length) {
                val loadingContent = htmlBuffer.substring(loadingStart, loadingEnd)
                // Удаляем только если это действительно сообщение загрузки
                if (loadingContent.contains("<!--LOADING_MARKER-->")) {
                    htmlBuffer.delete(loadingStart, loadingEnd)
                    loadingStart = -1
                    loadingEnd = -1
                }
            }
        } else {
            // Для fallback режима удаляем сразу
            if (loadingStart != -1 && loadingEnd != -1 &&
                loadingStart < loadingEnd && loadingEnd <= htmlBuffer.length) {
                val loadingContent = htmlBuffer.substring(loadingStart, loadingEnd)
                // Удаляем только если это действительно сообщение загрузки
                if (loadingContent.contains("<!--LOADING_MARKER-->")) {
                    htmlBuffer.delete(loadingStart, loadingEnd)
                    loadingStart = -1
                    loadingEnd = -1
                    refreshEditor()
                }
                loadingStart = -1
                loadingEnd = -1
                refreshEditor()
            }
        }
    }

    /**
     * Обновляет тему документа
     */
    fun updateTheme() {
        if (jcefView != null) {
            jcefView.setTheme(createJcefThemeTokens())
        } else {
            // Для fallback режима нужно переинициализировать HTML с новыми стилями
            val currentContent = htmlBuffer.toString()
            initialize()
            if (currentContent.isNotBlank()) {
                setBody(currentContent)
            }
        }
    }

    /**
     * Создает компонент для отображения контента
     */
    fun createContentComponent(): javax.swing.JComponent {
        return if (jcefView != null) {
            jcefView.getComponent()
        } else {
            JBScrollPane(chatHistoryArea).apply {
                preferredSize = Dimension(
                    ChatPanelConfig.HISTORY_WIDTH,
                    ChatPanelConfig.HISTORY_HEIGHT
                )
            }
        }
    }

    /**
     * Возвращает fallback HTML область, если она используется
     */
    fun getFallbackEditorPane(): JEditorPane? = if (jcefView == null) chatHistoryArea else null

    /**
     * Возвращает JCEF view, если он используется
     */
    fun getJcefView(): JcefChatView? = jcefView

    /**
     * Инициализирует HTML для fallback режима
     */
    private fun initializeFallbackHtml() {
        val fontSize = settings.chatFontSize
        val codeFontSize = (fontSize - 1).coerceAtLeast(10)
        val theme = ThemeTokens.fromSettings(settings)

        htmlBuffer.append(
            """
                <html>
                <head>
                  <style>
                    body { font-family: Arial, sans-serif; font-size: ${fontSize}px; }
                    .msg { margin-top: 8px; margin-left: 8px; margin-right: 8px; margin-bottom: 12px; }
                    .prefix { color: ${theme.prefix}; margin-bottom: 4px; }
                    .content { }
                    .msg.user .prefix { color: ${theme.prefix}; margin-bottom: 6px; }
                    .msg.user .content { display: inline-block; background-color: ${theme.userBg}; border: 1px solid ${theme.userBorder}; padding: 10px 14px; color: inherit; text-align: left; border-radius: 12px; }
                    .msg.user .content table.code-block { margin-top: 12px; }
                    pre { background-color: ${theme.codeBg}; color: ${theme.codeText}; padding: 8px; border: 1px solid ${theme.codeBorder}; margin: 0; white-space: pre-wrap; }
                    pre code { display: block; font-family: monospace; font-size: ${codeFontSize}px; color: ${theme.codeText}; white-space: pre-wrap; }
                    code { font-family: monospace; font-size: ${codeFontSize}px; color: ${theme.codeText}; }
                    table.code-block { width: 100%; border-collapse: collapse; margin-top: 8px; }
                    table.code-block td { padding: 0; }
                    td.code-lang { font-size: ${codeFontSize - 1}px; color: ${theme.prefix}; padding: 4px 6px; }
                    td.code-copy-cell { text-align: right; padding: 4px 6px; }
                    a.code-copy-link { color: ${theme.prefix}; text-decoration: none; display: inline-block; width: 20px; height: 20px; text-align: center; line-height: 20px; }
                    a.code-copy-link:hover { background-color: ${theme.userBorder}; }
                    .code-copy-icon { font-size: ${codeFontSize}px; line-height: 1; font-family: 'Segoe UI Symbol', 'Apple Color Emoji', sans-serif; }
                    .msg.user { text-align: right; }
                    .msg.user .prefix { text-align: right; }
                    /* Пузырь справа, но содержимое внутри по левому краю */
                    .msg.user .content { text-align: left; }
                    /* Корректные отступы и переносы для списков внутри пользовательского сообщения */
                    .msg.user .content ul,
                    .msg.user .content ol { margin: 8px 0; padding-left: 20px; padding-right: 0; list-style-position: outside; }
                    .msg.user .content li { margin: 4px 0; }
                    .msg.after-system { margin-top: 20px; }

                    /* Статусные строки для сообщений ассистента */
                    .status {
                        font-size: ${fontSize - 2}px;
                        margin-top: 6px;
                        padding: 4px 8px;
                        border-radius: 4px;
                        opacity: 0.8;
                    }
                    .status-final {
                        background-color: rgba(76, 175, 80, 0.2);
                        border: 1px solid rgba(76, 175, 80, 0.3);
                        color: #a5d6a7;
                    }
                    .status-low-confidence {
                        background-color: rgba(255, 152, 0, 0.15);
                        border: 1px solid rgba(255, 152, 0, 0.25);
                        color: #ffb74d;
                    }
                    .status-uncertain {
                        background-color: rgba(33, 150, 243, 0.2);
                        border: 1px solid rgba(33, 150, 243, 0.3);
                        color: #90caf9;
                    }
                    .status .metrics {
                        float: right;
                        font-size: ${fontSize - 4}px;
                        opacity: 0.7;
                        margin-left: 10px;
                    }
                    
                    /* Анимации fade-in/fade-out */
                    @keyframes fadeIn {
                        from {
                            opacity: 0;
                            transform: translateY(10px);
                        }
                        to {
                            opacity: 1;
                            transform: translateY(0);
                        }
                    }
                    
                    @keyframes fadeOut {
                        from {
                            opacity: 1;
                            transform: translateY(0);
                        }
                        to {
                            opacity: 0;
                            transform: translateY(-10px);
                        }
                    }
                    
                    .fade-in {
                        animation: fadeIn 0.3s ease-out;
                    }
                    
                    .fade-out {
                        animation: fadeOut 0.3s ease-out;
                        opacity: 0;
                    }
                    
                    .msg.assistant {
                        animation: fadeIn 0.4s ease-out;
                    }
                  </style>
                </head>
                <body>
            """.trimIndent()
        )
        refreshEditor()
    }

    /**
     * Обновляет fallback редактор
     */
    private fun refreshEditor() {
        if (jcefView == null) {
            val html = StringBuilder(htmlBuffer).append("\n</body>\n</html>").toString()
            chatHistoryArea.text = html
            chatHistoryArea.caretPosition = chatHistoryArea.document.length
        }
    }

    /**
     * Создает токены темы для JCEF
     */
    private fun createJcefThemeTokens(): Map<String, String> = mapOf(
        // Общие
        "bg" to settings.chatCodeBackgroundColor, // временно используем фон код-блоков как общий
        "textPrimary" to "#e6e6e6",
        "textSecondary" to "#9aa0a6",
        // Префиксы/метки
        "prefix" to settings.chatPrefixColor,
        // Пользовательские блоки
        "userBg" to settings.chatUserBackgroundColor,
        "userBorder" to settings.chatUserBorderColor,
        // Кодовые блоки
        "codeBg" to settings.chatCodeBackgroundColor,
        "codeText" to settings.chatCodeTextColor,
        "codeBorder" to settings.chatCodeBorderColor
    )
}