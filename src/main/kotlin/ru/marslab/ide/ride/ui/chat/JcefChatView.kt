package ru.marslab.ide.ride.ui.chat

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
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
    private val openFileJsQuery: JBCefJSQuery = JBCefJSQuery.create(browser)

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
                    // Регистрируем JavaScript-функцию для открытия файлов из чата
                    registerOpenFileHandler()
                }
            }

        }, browser.cefBrowser)

        // Обработчик вызова из JS: приходит строка команды open?path=...&startLine=...&endLine=...
        openFileJsQuery.addHandler { command ->
            try {
                // Вызываем RagSourceLinkService для обработки команды
                ru.marslab.ide.ride.service.rag.RagSourceLinkService.getInstance()
                    .extractSourceInfo(command)?.let { openAction ->
                        ru.marslab.ide.ride.service.rag.RagSourceLinkService.getInstance()
                            .handleOpenAction(openAction)
                    }
            } catch (_: Exception) {
                // no-op: ошибки логируются в сервисе
            }
            null
        }
    }

    fun getComponent(): JComponent = this

    /**
     * Открывает DevTools для встроенного JCEF браузера
     */
    fun openDevTools() {
        runCatching { browser.openDevtools() }
    }

    fun clear() {
        exec("document.getElementById('messages').innerHTML = '';")
    }

    fun setBody(html: String) {
        exec("window.__ride_setBody && window.__ride_setBody(${html.toJSString()}); window.__ride_enhanceAnchors && window.__ride_enhanceAnchors();")
    }

    fun appendHtml(html: String) {
        exec("window.__ride_appendHtml && window.__ride_appendHtml(${html.toJSString()}); window.__ride_enhanceAnchors && window.__ride_enhanceAnchors();")
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
        exec(
            """
            if (document.getElementById('status-${statusId}')) {
                // Обновляем существующий статус
                const existingElement = document.getElementById('status-${statusId}');
                existingElement.outerHTML = ${html.toJSString()};
            } else {
                // Добавляем новый статус
                window.__ride_appendHtml && window.__ride_appendHtml(${html.toJSString()});
            }
        """.trimIndent()
        )
    }

    /**
     * Удаляет сообщение о статусе tool agent
     */
    fun removeToolAgentStatus(statusId: String) {
        exec(
            """
            const element = document.getElementById('status-${statusId}');
            if (element) {
                element.classList.add('fade-out');
                setTimeout(() => element.remove(), 300);
            }
        """.trimIndent()
        )
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
        exec(
            """
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
        """
        )
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

    /**
     * Регистрирует JS-функцию window.openSourceFile, которая вызывает JBCefJSQuery
     */
    private fun registerOpenFileHandler() {
        val js = """
            (function(){
              // Определяем функцию, если её ещё нет
              if (!window.openSourceFile) {
                window.openSourceFile = function(command){
                  try {
                    ${openFileJsQuery.inject("command")}
                  } catch (e) { console.error('[RIDE] openSourceFile error', e); }
                };
              }

              // Делегированный обработчик кликов по атрибуту data-open-command
              document.addEventListener('click', function(e){
                var el = e.target;
                // Ищем ближайший элемент с data-open-command
                while (el && el !== document) {
                  try {
                    if (el.getAttribute && el.getAttribute('data-open-command')) {
                      var cmd = el.getAttribute('data-open-command');
                      if (cmd) {
                        console.debug('[RIDE] open link via delegate:', cmd);
                        if (typeof window.openSourceFile === 'function') {
                          window.openSourceFile(cmd);
                        } else {
                          console.warn('[RIDE] window.openSourceFile is not defined');
                        }
                        e.preventDefault();
                        e.stopImmediatePropagation();
                        e.stopPropagation();
                        return false;
                      }
                    }
                  } catch (err) { console.error('[RIDE] delegate click error', err); }
                  el = el.parentNode;
                }
              }, true);
              console.log('[RIDE] Source link handler initialized');

              // Функция постпроцессинга якорных ссылок: дополняет <a href="path.ext"> атрибутами data-open-command/onclick
              window.__ride_enhanceAnchors = function(){
                try {
                  var anchors = document.querySelectorAll('.msg.assistant .content a[href]');
                  var fileExt = /(\.dart|\.kt|\.java|\.kts|\.md|\.markdown|\.yaml|\.yml|\.xml|\.gradle|\.rs|\.py|\.ts|\.tsx|\.js|\.jsx|\.go|\.rb|\.c|\.cpp|\.h|\.hpp|\.cs|\.json)$/i;
                  anchors.forEach(function(a){
                    var href = a.getAttribute('href') || '';
                    var isExternal = /^(https?:|mailto:)/i.test(href);
                    if (isExternal) return;
                    if (!fileExt.test(href)) return;
                    var cmd = 'open?path=' + href + '&startLine=1&endLine=1';
                    a.setAttribute('href', '#');
                    a.setAttribute('data-open-command', cmd);
                    a.setAttribute('data-tooltip', href);
                    a.setAttribute('onclick', "window.openSourceFile('"+cmd+"'); return false;");
                  });
                } catch (e) { console.error('[RIDE] enhanceAnchors error', e); }
              };

              // Запускаем постпроцессор сразу после инициализации
              window.__ride_enhanceAnchors();
            })();
        """.trimIndent()
        exec(js)
    }
}

private fun String.toJSString(): String =
    this.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("\"", "\\\"")
        .replace("_", "\\_")
        .let { "\"$it\"" }