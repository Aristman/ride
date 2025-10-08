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
        val js = """
            (function(){
              console.log('DEBUG: JCEF setBody called with HTML length:', ${html.length});
              const root = document.getElementById('messages');
              root.innerHTML = ${html.toJSString()};
              
              requestAnimationFrame(()=> { window.scrollTo(0, document.body.scrollHeight); });
              console.log('DEBUG: Calling __ride_initHl and __ride_highlightAll');
              window.__ride_initHl && window.__ride_initHl();
              window.__ride_highlightAll && window.__ride_highlightAll();
            })();
        """.trimIndent()
        exec(js)
    }

    fun appendHtml(html: String) {
        // Безопасно вставляем как строку: создаём временной контейнер и переносим его детей
        val js = """
            (function(){
              console.log('DEBUG: JCEF appendHtml called with HTML length:', ${html.length});
              const tmp = document.createElement('div');
              const htmlStr = ${html.toJSString()};
              console.log('DEBUG: HTML string preview:', htmlStr.substring(0, 200));
              tmp.innerHTML = htmlStr;
              console.log('DEBUG: tmp.innerHTML preview:', tmp.innerHTML.substring(0, 200));
              
              const root = document.getElementById('messages');
              
              // Запоминаем количество блоков кода до добавления
              const blocksBefore = root.querySelectorAll('pre code').length;
              
              // Добавляем новые элементы
              while (tmp.firstChild) root.appendChild(tmp.firstChild);
              
              requestAnimationFrame(()=> { window.scrollTo(0, document.body.scrollHeight); });
              console.log('DEBUG: Calling __ride_initHl in appendHtml');
              window.__ride_initHl && window.__ride_initHl();
              
              // Подсвечиваем все новые блоки кода
              const blocksAfter = root.querySelectorAll('pre code');
              console.log('DEBUG: Code blocks before:', blocksBefore, 'after:', blocksAfter.length);
              
              if (window.hljs && blocksAfter.length > blocksBefore){
                console.log('DEBUG: Highlighting', (blocksAfter.length - blocksBefore), 'new code blocks');
                // Подсвечиваем только новые блоки (с индекса blocksBefore до конца)
                for (let i = blocksBefore; i < blocksAfter.length; i++) {
                  try {
                    console.log('DEBUG: Highlighting block', i);
                    window.hljs.highlightElement(blocksAfter[i]);
                  } catch(e){
                    console.log('DEBUG: Error highlighting block', i, e);
                  }
                }
              }
            })();
        """.trimIndent()
        exec(js)
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
