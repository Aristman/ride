package ru.marslab.ide.ride.ui.chat

import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Простое JCEF-представление для чата.
 * Принимает готовые HTML-фрагменты сообщений и добавляет их в контейнер.
 * Управляет темing через CSS variables.
 */
class JcefChatView : JPanel(BorderLayout()) {
    private val browser = JBCefBrowser()

    init {
        add(browser.component, BorderLayout.CENTER)
        // JCEF не может загружать внешние CSS/JS файлы из ресурсов плагина,
        // поэтому используем inline-версию HTML
        val html = ChatHtmlResources.createInlineHtml()
        browser.loadHTML(html)
    }

    fun getComponent(): JComponent = this

    fun clear() {
        exec("document.getElementById('messages').innerHTML = '';")
    }

    fun setBody(html: String) {
        exec("window.__ride_setBody && window.__ride_setBody(${html.toJSString()});")
    }

    fun appendHtml(html: String) {
        exec("window.__ride_appendHtml && window.__ride_appendHtml(${html.toJSString()});")
    }

    fun setTheme(tokens: Map<String, String>) {
        val setLines = tokens.entries.joinToString("\n") { (k, v) ->
            "document.documentElement.style.setProperty('--${k}', '${v}');"
        }
        exec(setLines)
    }

    /**
     * Удаляет элементы с fade-out анимацией
     */
    fun removeElementWithFade(selector: String) {
        exec("window.removeElementWithFade && window.removeElementWithFade('$selector');")
    }

    /**
     * Удаляет только системные сообщения с маркером загрузки
     * Постоянные системные сообщения (о сжатии истории и т.д.) остаются
     */
    fun removeLoadingSystemMessage() {
        exec("""
            (function() {
                const messages = document.querySelectorAll('.msg.system');
                for (let i = messages.length - 1; i >= 0; i--) {
                    if (messages[i].innerHTML.includes('<!--LOADING_MARKER-->')) {
                        messages[i].classList.add('fade-out');
                        setTimeout(() => messages[i].remove(), 300);
                        break;
                    }
                }
            })();
        """)
    }

    private fun exec(script: String) {
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
    }
}

private fun String.toJSString(): String =
    this.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("\"", "\\\"")
        .let { "\"$it\"" }