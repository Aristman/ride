package ru.marslab.ide.ride.ui.chat

import com.intellij.ui.jcef.JBCefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.browser.CefBrowser
import org.cef.callback.CefLoadHandlerErrorCode
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
    // Флаг готовности страницы и очередь скриптов для выполнения после загрузки
    private var isReady: Boolean = false
    private val pendingScripts = mutableListOf<String>()

    init {
        add(browser.component, BorderLayout.CENTER)
        // JCEF не может загружать внешние CSS/JS файлы из ресурсов плагина,
        // поэтому используем inline-версию HTML
        val html = ChatHtmlResources.createInlineHtml()
        browser.loadHTML(html)

        // Отслеживаем загрузку страницы, чтобы выполнить отложенные скрипты
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if (!isLoading) {
                    // Страница загружена — отмечаем готовность и выполняем очередь
                    isReady = true
                    flushPending()
                }
            }

            override fun onLoadError(
                browser: CefBrowser?,
                frameIdentifer: Long,
                errorCode: CefLoadHandlerErrorCode?,
                errorText: String?,
                failedUrl: String?
            ) {
                // В случае ошибки загрузки тоже отмечаем готовность, чтобы не зависать
                isReady = true
                flushPending()
            }
        }, browser.cefBrowser)
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
     * Добавляет или обновляет сообщение о статусе tool agent
     */
    fun addOrUpdateToolAgentStatus(html: String, statusId: String) {
        exec("""
            if (document.getElementById('status-${statusId}')) {
                // Обновляем существующий статус
                const existingElement = document.getElementById('status-${statusId}');
                existingElement.outerHTML = ${html.toJSString()};
            } else {
                // Добавляем новый статус
                window.__ride_appendHtml && window.__ride_appendHtml(${html.toJSString()});
            }
        """.trimIndent())
    }

    /**
     * Удаляет сообщение о статусе tool agent
     */
    fun removeToolAgentStatus(statusId: String) {
        exec("""
            const element = document.getElementById('status-${statusId}');
            if (element) {
                element.classList.add('fade-out');
                setTimeout(() => element.remove(), 300);
            }
        """.trimIndent())
    }

    /**
     * Переключает состояние развернутости результата
     */
    fun toggleOutput(statusId: String) {
        exec("window.toggleOutput && window.toggleOutput('output-${statusId}');")
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
        if (!isReady) {
            pendingScripts += script
            return
        }
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
    }

    private fun flushPending() {
        if (pendingScripts.isEmpty()) return
        // Выполняем скрипты в порядке добавления
        pendingScripts.forEach { s ->
            browser.cefBrowser.executeJavaScript(s, browser.cefBrowser.url, 0)
        }
        pendingScripts.clear()
    }
}

private fun String.toJSString(): String =
    this.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("\"", "\\\"")
        .let { "\"$it\"" }