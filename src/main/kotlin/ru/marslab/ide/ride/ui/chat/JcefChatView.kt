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
    private val atSuggestJsQuery: JBCefJSQuery = JBCefJSQuery.create(browser)

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
                    // Регистрируем обработчики для @-пикера
                    registerAtPickerHandlers()
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

        // Обработчик: запрос подсказок для @-пикера. В аргументе приходит строка запроса после '@'
        atSuggestJsQuery.addHandler { query ->
            try {
                val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
                val json = if (project == null) {
                    "[]"
                } else {
                    val svc = AtPickerSuggestionService()
                    val items = svc.suggestFiles(project, query ?: "")
                    items.joinToString(prefix = "[", postfix = "]") { s ->
                        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                    }
                }
                com.intellij.ui.jcef.JBCefJSQuery.Response(json)
            } catch (_: Exception) {
                com.intellij.ui.jcef.JBCefJSQuery.Response("[]")
            }
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

    /**
     * Регистрирует JS для @-пикера: отслеживание ввода, запрос подсказок, отрисовка поповера и вставка токена.
     */
    private fun registerAtPickerHandlers() {
        val js = """
            (function(){
              function getInput(){
                // 1) Явный селектор
                let el = document.getElementById('ride-chat-input');
                if (el) return el;
                // 2) Фокусированный элемент, если это input/textarea/contenteditable
                const ae = document.activeElement;
                if (ae && (ae.tagName === 'INPUT' || ae.tagName === 'TEXTAREA' || ae.isContentEditable)) return ae;
                // 3) Первый подходящий элемент в DOM
                el = document.querySelector('input[type="text"], textarea, [contenteditable="true"]');
                return el || null;
              }

              let input = getInput();
              let overlayRoot = document.getElementById('ride-overlay-root');
              if (!overlayRoot) {
                overlayRoot = document.createElement('div');
                overlayRoot.id = 'ride-overlay-root';
                document.body.appendChild(overlayRoot);
              }

              let picker = null;
              let items = [];
              let sel = 0;
              let active = false;

              function ensurePicker(){
                if (picker) return picker;
                picker = document.createElement('div');
                picker.id = 'ride-at-picker';
                picker.style.position = 'fixed';
                picker.style.left = '8px';
                picker.style.right = '8px';
                picker.style.bottom = '56px';
                picker.style.maxHeight = '40vh';
                picker.style.overflow = 'auto';
                picker.style.background = 'var(--ride-input-bg, #2b2d30)';
                picker.style.border = '1px solid var(--ride-border, #3a3d40)';
                picker.style.borderRadius = '8px';
                picker.style.boxShadow = '0 6px 18px rgba(0,0,0,0.35)';
                picker.style.fontSize = '12px';
                picker.style.zIndex = 9999;
                overlayRoot.appendChild(picker);
                return picker;
              }

              function hide(){ if (picker){ picker.style.display='none'; active=false; } }
              function show(){ ensurePicker().style.display='block'; active=true; }

              function render(){
                const el = ensurePicker();
                el.innerHTML = '';
                items.forEach((it, idx) => {
                  const row = document.createElement('div');
                  row.textContent = it;
                  row.style.padding = '6px 10px';
                  row.style.cursor = 'pointer';
                  row.style.background = (idx===sel) ? 'rgba(80,120,220,0.35)' : 'transparent';
                  row.onclick = () => { insert(it); };
                  row.onmouseenter = () => { sel = idx; render(); };
                  el.appendChild(row);
                });
              }

              function insert(text){
                // Вставляем строго @<workspace-relative-path>
                input = getInput();
                if (!input) { hide(); return; }
                const val = (input.value !== undefined) ? input.value : (input.textContent || '');
                const caret = input.selectionStart ?? val.length;
                // Ищем последнюю позицию '@' перед кареткой
                const atPos = val.lastIndexOf('@', caret-1);
                if (atPos >= 0){
                  const before = val.substring(0, atPos);
                  const after = val.substring(caret);
                  const next = before + '@' + text + after;
                  if (input.value !== undefined) input.value = next; else input.textContent = next;
                  // Ставим курсор после вставленного токена
                  const newPos = (before + '@' + text).length;
                  input.setSelectionRange(newPos, newPos);
                }
                hide();
                input.focus();
              }

              async function fetchSuggestions(q){
                try {
                  const res = await ${atSuggestJsQuery.inject("q")};
                  items = JSON.parse(res || '[]');
                  sel = 0;
                  render();
                } catch (e) { console.error('[RIDE] suggest error', e); }
              }

              function handleInput(){
                input = getInput();
                if (!input) { hide(); return; }
                const val = (input.value !== undefined) ? input.value : (input.textContent || '');
                const caret = input.selectionStart ?? val.length;
                const atPos = val.lastIndexOf('@', caret-1);
                if (atPos < 0){ hide(); return; }
                const afterAt = val.substring(atPos+1, caret);
                if (afterAt.length === 0){ show(); fetchSuggestions(''); return; }
                show();
                fetchSuggestions(afterAt);
              }

              function attachListeners(target){
                if (!target) return;
                target.addEventListener('input', ()=>{
                  const v = (target.value !== undefined) ? target.value : (target.textContent || '');
                  if (v.includes('@')) handleInput(); else hide();
                });
                target.addEventListener('keydown', (e)=>{
                  if (!active) return;
                  if (e.key === 'ArrowDown'){ sel = Math.min(sel+1, items.length-1); render(); e.preventDefault(); }
                  else if (e.key === 'ArrowUp'){ sel = Math.max(sel-1, 0); render(); e.preventDefault(); }
                  else if (e.key === 'Enter'){ if (items[sel]) { insert(items[sel]); e.preventDefault(); } }
                  else if (e.key === 'Escape'){ hide(); }
                });
              }

              // Начальная привязка и авто-перепривязка при смене фокуса
              attachListeners(input);
              document.addEventListener('focusin', ()=>{
                const el = getInput();
                if (el && el !== input){ input = el; attachListeners(input); }
              });
              document.addEventListener('keydown', (e)=>{
                // Если ввели '@' — запускаем обработку даже без события input
                if (e.key === '@') { setTimeout(handleInput, 0); }
              });

              // Отслеживаем появление инпута в DOM (если рендерится позже)
              const mo = new MutationObserver(()=>{
                const el = getInput();
                if (el && el !== input){ input = el; attachListeners(input); }
              });
              mo.observe(document.documentElement, { childList: true, subtree: true });

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