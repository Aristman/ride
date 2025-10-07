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
        browser.loadHTML(buildBaseHtml())
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

    private fun buildBaseHtml(): String {
        // CSS переменные; значения могут быть переопределены через setTheme
        val css = """
            :root{
              --bg: #0f1115; /* fallback */
              --textPrimary: #e6e6e6;
              --textSecondary: #9aa0a6;
              --userBg: #e3ecff;
              --userBorder: #6d8fd8;
              --codeBg: #2b2b2b;
              --codeText: #e6e6e6;
              --codeBorder: #444444;
              --prefix: #6b6b6b;
            }
            html,body { margin:0; padding:0; background:var(--bg); color:var(--textPrimary); font: 13px -apple-system, Segoe UI, Roboto, Arial, sans-serif; }
            #messages { padding: 8px; }
            .msg { margin: 8px 8px 12px 8px; }
            .prefix { color: var(--prefix); margin-bottom: 4px; }
            .content { }
            .msg.user { text-align: right; }
            .msg.user .prefix { text-align: right; }
            /* Пузырь справа, содержимое внутри по левому краю */
            .msg.user .content { display:inline-block; background: var(--userBg); border:1px solid var(--userBorder); padding:10px 14px; color: inherit; text-align: left; border-radius: 12px; }
            /* Корректные отступы для списков внутри пользовательского сообщения */
            .msg.user .content ul,
            .msg.user .content ol { margin: 8px 0; padding-left: 20px; padding-right: 0; list-style-position: outside; }
            .msg.user .content li { margin: 4px 0; }
            table.code-block { width:100%; border-collapse: collapse; margin-top: 8px; }
            table.code-block td { padding:0; }
            td.code-lang { font-size: 12px; color: var(--prefix); padding: 4px 6px; }
            td.code-copy-cell { text-align: right; padding: 4px 6px; }
            a.code-copy-link { color: var(--prefix); text-decoration: none; display:inline-block; width:20px; height:20px; text-align:center; line-height:20px; }
            pre { background: var(--codeBg); color: var(--codeText); padding:8px; border:1px solid var(--codeBorder); margin:0; overflow:auto; white-space: pre; }
            pre code { display: block; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; white-space: pre; }
            code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; }

            /* Статусные строки для сообщений ассистента */
            .status {
                font-size: 11px;
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
                background-color: rgba(255, 152, 0, 0.2);
                border: 1px solid rgba(255, 152, 0, 0.3);
                color: #ffcc80;
            }
            .status-uncertain {
                background-color: rgba(33, 150, 243, 0.2);
                border: 1px solid rgba(33, 150, 243, 0.3);
                color: #90caf9;
            }
            .status .metrics {
                float: right;
                font-size: 10px;
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

            /* Стили для Markdown */
            h1, h2, h3 {
                color: var(--textPrimary);
                margin-top: 16px;
                margin-bottom: 8px;
            }
            h1 { font-size: 1.5em; }
            h2 { font-size: 1.3em; }
            h3 { font-size: 1.1em; }
            strong { font-weight: bold; }
            em { font-style: italic; }
            code {
                background-color: rgba(255, 255, 255, 0.1);
                padding: 2px 4px;
                border-radius: 3px;
                font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
            }
            hr {
                border: none;
                border-top: 1px solid rgba(255, 255, 255, 0.2);
                margin: 16px 0;
            }
            ul, ol {
                margin: 4px 0;
                padding-left: 20px;
            }
            li {
                margin: 2px 0;
            }
            p {
                margin: 4px 0;
            }
        """.trimIndent()

        val js = """
            // Функция для удаления элемента с fade-out анимацией
            window.removeElementWithFade = function(selector) {
                const elements = document.querySelectorAll(selector);
                elements.forEach(el => {
                    el.classList.add('fade-out');
                    setTimeout(() => {
                        el.remove();
                    }, 300); // Длительность анимации
                });
            };
            
            document.addEventListener('click', (e)=>{
              const a = e.target.closest('a.code-copy-link');
              if (!a) return;
              e.preventDefault();
              const table = a.closest('table.code-block');
              const code = table && table.querySelector('pre code');
              if (code) {
                try { navigator.clipboard.writeText(code.textContent || ''); } catch(_) {}
              }
            });

            // Lazy load highlight.js once
            window.__ride_initHl = function(){
              if (window.__ride_hlLoaded) return;
              console.log('DEBUG: Initializing highlight.js');
              const addCss = (href)=>{
                console.log('DEBUG: Adding CSS', href);
                const l = document.createElement('link'); l.rel='stylesheet'; l.href=href; document.head.appendChild(l);
              };
              const addJs = (src, onload)=>{
                console.log('DEBUG: Adding JS', src);
                const s = document.createElement('script'); s.src=src; s.onload=onload; document.head.appendChild(s);
              };
              addCss('https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css');
              addJs('https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js', function(){
                console.log('DEBUG: highlight.js loaded successfully');
                window.__ride_hlLoaded = true;
                window.__ride_highlightAll();
              });
            };
            window.__ride_highlightAll = function(){
              if (!window.hljs) return;
              document.querySelectorAll('pre code').forEach(function(block){ try{ window.hljs.highlightElement(block); }catch(_){ } });
            };
        """.trimIndent()

        return "<!DOCTYPE html><html><head><meta charset='utf-8'><style>${css}</style></head><body><div id=\"messages\"></div><script>${js}</script></body></html>"
    }
}

private fun String.toJSString(): String =
    this.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("\"", "\\\"")
        .let { "\"$it\"" }
