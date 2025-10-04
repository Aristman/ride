package ru.marslab.ide.ride.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import ru.marslab.ide.ride.model.Message
import ru.marslab.ide.ride.model.MessageRole
import ru.marslab.ide.ride.service.ChatService
import ru.marslab.ide.ride.settings.ChatAppearanceListener
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.ui.ResponseFormatter
import ru.marslab.ide.ride.ui.chat.JcefChatView
import ru.marslab.ide.ride.theme.ThemeTokens

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.LinkedHashMap
import java.util.UUID
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.ide.ui.LafManagerListener

private const val isFinalLevel = 0.1

/**
 * Главная панель чата (гибрид Swing + JCEF)
 */
class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val chatService = service<ChatService>()
    private val settings = service<PluginSettings>()

    // Fallback HTML-история (для режима без JCEF)
    private val chatHistoryArea: JEditorPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        addHyperlinkListener { event ->
            if (event.eventType == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                val description = event.description
                if (description != null && description.startsWith(COPY_LINK_PREFIX)) {
                    val key = description.removePrefix(COPY_LINK_PREFIX)
                    codeBlockRegistry[key]?.let { copyCodeToClipboard(it) }
                }
            }
        }
    }

    fun clearHistoryAndRefresh() {
        chatService.clearHistory()
        initHtml()
        appendSystemMessage("История чата очищена.")
    }

    private val htmlBuffer = StringBuilder()
    private val codeBlockRegistry = LinkedHashMap<String, String>()
    private var loadingStart: Int = -1
    private var loadingEnd: Int = -1
    private lateinit var inputArea: JBTextArea
    private lateinit var sendButton: JButton
    private lateinit var clearButton: JButton
    private var lastRole: MessageRole? = null
    private lateinit var sessionsTabs: JBTabbedPane

    // JCEF (если доступен)
    private val useJcef: Boolean = true
    private var jcefView: JcefChatView? = runCatching {
        if (useJcef) {
            val view = JcefChatView()
            println("DEBUG: JCEF ChatView initialized successfully")
            view
        } else null
    }.getOrNull().also {
        if (it == null) println("DEBUG: JCEF ChatView initialization failed, using HTML fallback")
    }

    init {
        // Инициализация HTML/темы
        initHtml()
        subscribeToAppearanceChanges()
        jcefView?.setTheme(ThemeTokens.fromSettings(settings).toJcefMap())

        // Верхняя панель (тулбар + табы)
        val (topPanel, toolbar) = buildTopPanel()
        add(topPanel, BorderLayout.NORTH)
        // Назначаем targetComponent после добавления topPanel в иерархию
        toolbar.targetComponent = topPanel

        // Центральная область (JCEF или fallback HTML)
        add(buildCenterComponent(), BorderLayout.CENTER)

        // Нижняя панель (композер)
        add(buildBottomPanel(), BorderLayout.SOUTH)

        // История и табы при старте
        loadHistory()
        refreshTabs()

        if (!settings.isConfigured()) {
            appendSystemMessage("⚠️ Плагин не настроен. Перейдите в Settings → Tools → Ride для настройки API ключа.")
        }
    }

    private fun buildTopPanel(): Pair<JPanel, ActionToolbar> {
        val actionManager = ActionManager.getInstance()
        val toolbarGroup = (actionManager.getAction("Ride.ToolWindowActions") as? DefaultActionGroup)
            ?: DefaultActionGroup()
        val toolbar = actionManager.createActionToolbar("RideToolbar", toolbarGroup, true)

        sessionsTabs = JBTabbedPane()
        sessionsTabs.addChangeListener {
            val idx = sessionsTabs.selectedIndex
            val sessions = chatService.getSessions()
            if (idx in sessions.indices) {
                if (chatService.switchSession(sessions[idx].id)) {
                    refreshAppearance()
                }
            }
        }

        val panel = JPanel(BorderLayout())
        panel.add(toolbar.component, BorderLayout.NORTH)
        panel.add(sessionsTabs, BorderLayout.SOUTH)
        return panel to toolbar
    }

    private fun buildCenterComponent(): JComponent {
        return if (jcefView != null) {
            jcefView!!.getComponent()
        } else {
            JBScrollPane(chatHistoryArea).apply { preferredSize = Dimension(HISTORY_WIDTH, HISTORY_HEIGHT) }
        }
    }

    private fun buildBottomPanel(): JPanel {
        // Ввод
        inputArea = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            rows = INPUT_ROWS
            font = font.deriveFont(14f)
        }
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) = handleComposerKey(e)
        })
        val inputScrollPane = JBScrollPane(inputArea).apply { preferredSize = Dimension(HISTORY_WIDTH, INPUT_HEIGHT) }

        // Кнопки
        sendButton = JButton("Отправить").apply { addActionListener { sendMessage() } }
        clearButton = JButton("Очистить").apply { addActionListener { clearChat() } }
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(sendButton)
            add(Box.createHorizontalStrut(5))
            add(clearButton)
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            add(inputScrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
    }

    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) return
        inputArea.text = ""
        appendMessage(Message(content = text, role = MessageRole.USER))
        setUIEnabled(false)
        appendSystemMessage("⏳ Обработка запроса...")

        chatService.sendMessage(
            userMessage = text,
            project = project,
            onResponse = { message ->
                removeLastSystemMessage()
                appendMessage(message)
                setUIEnabled(true)
            },
            onError = { error ->
                removeLastSystemMessage()
                appendSystemMessage("❌ Ошибка: $error")
                setUIEnabled(true)
            }
        )
    }

    private fun clearChat() {
        val result = JOptionPane.showConfirmDialog(
            this,
            "Вы уверены, что хотите очистить историю чата?",
            "Подтверждение",
            JOptionPane.YES_NO_OPTION
        )
        if (result == JOptionPane.YES_OPTION) {
            chatService.clearHistory()
            initHtml()
            appendSystemMessage("История чата очищена.")
        }
    }

    private fun loadHistory() {
        val history = chatService.getHistory()
        if (history.isNotEmpty()) history.forEach { appendMessage(it, addToHistory = false) }
        else appendSystemMessage("👋 Привет! Я AI-ассистент для разработчиков. Чем могу помочь?")
    }

    private fun appendMessage(message: Message, addToHistory: Boolean = true) {
        val roleClass = when (message.role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
        }
        val prefix = when (message.role) {
            MessageRole.USER -> "👤 Вы"
            MessageRole.ASSISTANT -> {
                // Добавляем индикатор неопределенности для сообщений ассистента
                val isFinal = message.metadata["isFinal"] as? Boolean ?: true
                val uncertainty = message.metadata["uncertainty"] as? Double ?: 0.0
                val indicator = if (!isFinal) {
                    "❓"
                } else if (uncertainty > isFinalLevel) {
                    "⚠️"
                } else {
                    "✅"
                }
                "$indicator 🤖 Ассистент"
            }

            MessageRole.SYSTEM -> "ℹ️ Система"
        }

        // Форматируем контент в зависимости от роли сообщения
        val actualUncertainty = message.metadata["uncertainty"] as? Double ?: 0.0
        val bodyHtml = renderContentToHtml(message.content)
        val afterSystemClass =
            if (message.role == MessageRole.USER && lastRole == MessageRole.SYSTEM) " after-system" else ""

        // Добавляем статусную строку для сообщений ассистента
        val statusRow = if (message.role == MessageRole.ASSISTANT) {
            val isFinal = message.metadata["isFinal"] as? Boolean ?: true
            val wasParsed = message.metadata["parsedData"] as? Boolean ?: false
            val hasClarifyingQuestions = message.metadata["hasClarifyingQuestions"] as? Boolean ?: false

            val statusText = when {
                !isFinal || hasClarifyingQuestions -> {
                    "Требуются уточнения (неопределенность: ${(actualUncertainty * 100).toInt()}%)"
                }

                !wasParsed && actualUncertainty > isFinalLevel -> {
                    "Ответ с парсингом (неопределенность: ${(actualUncertainty * 100).toInt()}%)"
                }

                actualUncertainty > isFinalLevel -> {
                    "Ответ с низкой уверенностью (неопределенность: ${(actualUncertainty * 100).toInt()}%)"
                }

                else -> {
                    "Окончательный ответ"
                }
            }

            val statusClass = when {
                !isFinal || hasClarifyingQuestions -> "status-uncertain"
                !wasParsed -> "status-low-confidence"
                actualUncertainty > isFinalLevel -> "status-low-confidence"
                else -> "status-final"
            }

            // Добавляем иконку в зависимости от статуса парсинга
            val icon = if (!wasParsed) "⚠️" else "📊"

            "<div class='status $statusClass'>$icon $statusText</div>"
        } else {
            ""
        }

        val prefixDiv = if (message.role == MessageRole.USER)
            "<div class='prefix' align='right'><b>${escapeHtml(prefix)}</b>:</div>"
        else
            "<div class='prefix'><b>${escapeHtml(prefix)}</b>:</div>"
        val contentDiv = if (message.role == MessageRole.USER)
            "<div class='content' align='right'>$bodyHtml</div>"
        else
            "<div class='content'>$bodyHtml</div>"
        val chunk = """
            <div class='msg $roleClass$afterSystemClass'>
              $prefixDiv
              $contentDiv
              $statusRow
            </div>
        """.trimIndent()
        appendHtml(chunk)
        lastRole = message.role
    }

    private fun appendSystemMessage(text: String) {
        val isLoading = text.contains("Обработка запроса")
        val content = escapeHtml(text)
        val marker = if (isLoading) "<!--LOADING_MARKER-->" else ""
        val chunk = """
            <div class='msg system'>$marker<div class='prefix'><b>ℹ️ Система</b>:</div>
            <div class='content'>${content.replace("\n", "<br/>")}</div></div>
        """.trimIndent()
        if (isLoading) {
            val range = appendHtmlWithRange(chunk)
            loadingStart = range.first
            loadingEnd = range.second
        } else {
            appendHtml(chunk)
        }
        lastRole = MessageRole.SYSTEM
    }

    private fun removeLastSystemMessage() {
        if (loadingStart != -1 && loadingEnd != -1 && loadingStart < loadingEnd && loadingEnd <= htmlBuffer.length) {
            htmlBuffer.delete(loadingStart, loadingEnd)
            loadingStart = -1
            loadingEnd = -1
            if (jcefView != null) jcefView?.setBody(htmlBuffer.toString()) else refreshEditor()
        }
    }

    private fun setUIEnabled(enabled: Boolean) {
        inputArea.isEnabled = enabled
        sendButton.isEnabled = enabled
        if (enabled) {
            // Возвращаем фокус в поле ввода и ставим курсор в конец
            SwingUtilities.invokeLater {
                inputArea.requestFocusInWindow()
                inputArea.grabFocus()
                inputArea.requestFocus()
                inputArea.caretPosition = inputArea.document.length
            }
        }
    }

    /**
     * Обновляет вкладки сессий согласно ChatService
     */
    private fun refreshTabs() {
        val sessions = chatService.getSessions()
        sessionsTabs.removeAll()
        sessions.forEach { s -> sessionsTabs.addTab(s.title, JPanel()) }
        val current = chatService.getCurrentSessionId()
        val idx = sessions.indexOfFirst { it.id == current }
        if (idx >= 0) sessionsTabs.selectedIndex = idx
    }

    /** Создание новой сессии из действия тулбара */
    fun onNewSession() {
        chatService.createNewSession()
        refreshTabs()
        refreshAppearance()
    }

    private fun initHtml() {
        htmlBuffer.setLength(0)
        loadingStart = -1
        loadingEnd = -1
        lastRole = null
        codeBlockRegistry.clear()

        if (jcefView != null) {
            jcefView?.clear()
        } else {
            val fontSize = settings.chatFontSize
            val codeFontSize = (fontSize - 1).coerceAtLeast(10)
            val t = ThemeTokens.fromSettings(settings)

            htmlBuffer.append(
                """
                    <html>
                    <head>
                      <style>
                        body { font-family: Arial, sans-serif; font-size: ${fontSize}px; }
                        .msg { margin-top: 8px; margin-left: 8px; margin-right: 8px; margin-bottom: 12px; }
                        .prefix { color: ${t.prefix}; margin-bottom: 4px; }
                        .content { }
                        .msg.user .prefix { color: ${t.prefix}; margin-bottom: 6px; }
                        .msg.user .content { display: inline-block; background-color: ${t.userBg}; border: 1px solid ${t.userBorder}; padding: 10px 14px; color: inherit; text-align: left; border-radius: 12px; }
                        .msg.user .content table.code-block { margin-top: 12px; }
                        pre { background-color: ${t.codeBg}; color: ${t.codeText}; padding: 8px; border: 1px solid ${t.codeBorder}; margin: 0; white-space: pre-wrap; }
                        pre code { display: block; font-family: monospace; font-size: ${codeFontSize}px; color: ${t.codeText}; white-space: pre-wrap; }
                        code { font-family: monospace; font-size: ${codeFontSize}px; color: ${t.codeText}; }
                        table.code-block { width: 100%; border-collapse: collapse; margin-top: 8px; }
                        table.code-block td { padding: 0; }
                        td.code-lang { font-size: ${codeFontSize - 1}px; color: ${t.prefix}; padding: 4px 6px; }
                        td.code-copy-cell { text-align: right; padding: 4px 6px; }
                        a.code-copy-link { color: ${t.prefix}; text-decoration: none; display: inline-block; width: 20px; height: 20px; text-align: center; line-height: 20px; }
                        a.code-copy-link:hover { background-color: ${t.userBorder}; }
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
                      </style>
                    </head>
                    <body>
                """.trimIndent()
            )
            refreshEditor()
        }
    }

    private fun refreshEditor() {
        if (jcefView != null) {
            jcefView?.setBody(htmlBuffer.toString())
        } else {
            val html = StringBuilder(htmlBuffer).append("\n</body>\n</html>").toString()
            chatHistoryArea.text = html
            chatHistoryArea.caretPosition = chatHistoryArea.document.length
        }
    }

    private fun appendHtml(chunk: String) {
        if (jcefView != null) {
            htmlBuffer.append("\n").append(chunk)
            jcefView?.appendHtml(chunk)
        } else {
            val closing = "</body>\n</html>"
            val idx = htmlBuffer.indexOf(closing)
            if (idx != -1) htmlBuffer.delete(idx, htmlBuffer.length)
            htmlBuffer.append("\n").append(chunk)
            refreshEditor()
        }
    }

    private fun appendHtmlWithRange(chunk: String): Pair<Int, Int> {
        if (jcefView != null) {
            val start = htmlBuffer.length
            htmlBuffer.append("\n").append(chunk)
            val end = htmlBuffer.length
            jcefView?.appendHtml(chunk)
            return start to end
        } else {
            val closing = "</body>\n</html>"
            val idx = htmlBuffer.indexOf(closing)
            if (idx != -1) htmlBuffer.delete(idx, htmlBuffer.length)
            val start = htmlBuffer.length
            htmlBuffer.append("\n").append(chunk)
            val end = htmlBuffer.length
            refreshEditor()
            return start to end
        }
    }

    private fun renderContentToHtml(text: String): String {
        println("DEBUG: renderContentToHtml called with text length: ${text.length}")
        println("DEBUG: Input text preview: ${text.take(200)}...")
        val isJcefMode = jcefView != null
        println("DEBUG: JCEF mode: $isJcefMode")
        var result = text

        // Если контент уже представляет собой HTML (например, приходит из форматтера/схемы)
        // — вставляем как есть без экранирования и конвертации Markdown
        if (looksLikeHtml(result)) {
            println("DEBUG: Detected preformatted HTML content, bypassing markdown/escape pipeline")
            return result
        }

        // Сначала обработаем кодовые блоки, ДО экранирования HTML
        // Обработка кодовых блоков ``` (стандартный markdown)
        val tripleBacktickPattern = Regex("""```([\w#+.-]+)?[ \t]*\n?([\s\S]*?)```""", RegexOption.IGNORE_CASE)
        var lastIndex = 0
        val finalResult = StringBuilder()
        val codeBlocksFound = mutableListOf<String>()

        tripleBacktickPattern.findAll(result).forEach { m ->
            val pre = result.substring(lastIndex, m.range.first)
            if (isJcefMode) {
                finalResult.append(escapeHtml(pre))
            } else {
                finalResult.append(pre)
            }
            val langRaw = (m.groups[1]?.value ?: "").trim().lowercase()
            val normalizedLang = normalizeLanguage(langRaw)
            var code = (m.groups[2]?.value ?: "").trim('\n', '\r')
            // Удаляем общие отступы из кода
            code = removeCommonIndent(code)
            code = runCatching {
                when (normalizedLang) {
                    "json" -> prettyPrintJson(code)
                    else -> code
                }
            }.getOrDefault(code)
            val escaped = if (isJcefMode) {
                // В JCEF режиме используем специальный маркер для переносов строк
                // который будет заменён на \n в JavaScript перед вставкой
                code.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "&#10;")  // Используем HTML entity для перевода строки
            } else {
                escapeHtml(code)
            }
            println("DEBUG: escaped=$escaped")

            val langLabel = langRaw.ifBlank { "Текст" }
            val codeId = "code_${System.currentTimeMillis()}_${codeBlocksFound.size}"

            finalResult.append("<table class='code-block'>")
            finalResult.append("<tr><td class='code-lang'>").append(escapeHtml(langLabel)).append("</td>")
            finalResult.append("<td class='code-copy-cell'><a href='${COPY_LINK_PREFIX}$codeId' class='code-copy-link' title='Скопировать'><span class='code-copy-icon'>&#128203;</span></a></td></tr>")
            finalResult.append("<tr><td colspan='2'><pre><code class='language-$normalizedLang'>").append(escaped)
                .append("</code></pre></td></tr>")
            finalResult.append("</table>")
            codeBlocksFound.add("$normalizedLang: ${code.take(50)}...")
            lastIndex = m.range.last + 1
        }

        // Добавляем оставшийся текст после последнего кодового блока
        if (lastIndex < result.length) {
            val remainingText = result.substring(lastIndex)
            if (isJcefMode) {
                finalResult.append(escapeHtml(remainingText))
            } else {
                finalResult.append(remainingText)
            }
        }

        if (codeBlocksFound.isNotEmpty()) {
            println("DEBUG: Found ${codeBlocksFound.size} triple backtick code blocks: ${codeBlocksFound.joinToString(", ")}")
        }

        println("DEBUG: codeBlocksFound=$codeBlocksFound")
        println("DEBUG: finalResult=$finalResult")

        result = finalResult.toString()

        // Теперь обработаем одинарные обратные кавычки и преобразуем их в тройные
        val singleBacktickPattern = Regex("""`([^`\s]+)[ \t]*\n?((?:[^\n`]+\n?)+)`""")
        val singleBacktickMatches = singleBacktickPattern.findAll(result).count()
        if (singleBacktickMatches > 0) {
            println("DEBUG: Found $singleBacktickMatches single backtick code blocks, converting to triple backticks")
        }
        result = singleBacktickPattern.replace(result) { match ->
            val lang = match.groupValues[1]
            val code = match.groupValues[2].trim()
            println("DEBUG: Converting single backtick block: $lang, code: ${code.take(50)}...")
            val normalizedLang = normalizeLanguage(lang)
            val escapedCode = if (isJcefMode) {
                // В JCEF режиме используем HTML entity для переносов строк
                code.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "&#10;")
            } else {
                escapeHtml(code)
            }
            val codeId = "code_${System.currentTimeMillis()}_${codeBlocksFound.size}"

            "<table class='code-block'>" +
                    "<tr><td class='code-lang'>${escapeHtml(lang)}</td>" +
                    "<td class='code-copy-cell'><a href='${COPY_LINK_PREFIX}$codeId' class='code-copy-link' title='Скопировать'><span class='code-copy-icon'>&#128203;</span></a></td></tr>" +
                    "<tr><td colspan='2'><pre><code class='language-$normalizedLang'>$escapedCode</code></pre></td></tr>" +
                    "</table>"
        }

        // Также обработаем случай, когда код написан без переносов
        val inlineCodePattern = Regex("""`([^`\s]+)[ \t]*([^{`}]+?)`""")
        val inlineCodeMatches = inlineCodePattern.findAll(result).count()
        if (inlineCodeMatches > 0) {
            println("DEBUG: Found $inlineCodeMatches inline code patterns, converting to triple backticks")
        }
        result = inlineCodePattern.replace(result) { match ->
            val lang = match.groupValues[1]
            val code = match.groupValues[2].trim()
            println("DEBUG: Processing inline code: $lang, code: ${code.take(50)}...")
            // Если код содержит точки с запятой, фигурные скобки или ключевое слово function, это точно кодовый блок
            if (code.contains(';') || code.contains('{') || code.contains('}') ||
                code.contains("fun ") || code.contains("function ") || code.contains("return ") ||
                code.contains("class ") || code.contains("import ")
            ) {
                println("DEBUG: Converting inline code to block: $lang")
                val normalizedLang = normalizeLanguage(lang)
                val escapedCode = if (isJcefMode) {
                    // В JCEF режиме используем HTML entity для переносов строк
                    code.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\n", "&#10;")
                } else {
                    escapeHtml(code)
                }
                val codeId = "code_${System.currentTimeMillis()}_${codeBlocksFound.size}"

                "<table class='code-block'>" +
                        "<tr><td class='code-lang'>${escapeHtml(lang)}</td>" +
                        "<td class='code-copy-cell'><a href='${COPY_LINK_PREFIX}$codeId' class='code-copy-link' title='Скопировать'><span class='code-copy-icon'>&#128203;</span></a></td></tr>" +
                        "<tr><td colspan='2'><pre><code class='language-$normalizedLang'>$escapedCode</code></pre></td></tr>" +
                        "</table>"
            } else {
                // Иначе оставляем как инлайн-код
                "<code>$code</code>"
            }
        }

        // Экранируем HTML только для не-JCEF режима
        if (!isJcefMode) {
            result = escapeHtml(result)
        }

        // Обработка markdown-элементов

        // Жирный текст **текст**
        result = result.replace(Regex("""\*\*(.*?)\*\*""")) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }

        // Заголовки #, ##, ###
        result = result.replace(Regex("""^### (.*?)$""", RegexOption.MULTILINE)) { match ->
            "<h3>${match.groupValues[1]}</h3>"
        }
        result = result.replace(Regex("""^## (.*?)$""", RegexOption.MULTILINE)) { match ->
            "<h2>${match.groupValues[1]}</h2>"
        }
        result = result.replace(Regex("""^# (.*?)$""", RegexOption.MULTILINE)) { match ->
            "<h1>${match.groupValues[1]}</h1>"
        }

        // Курсив *текст* (только если не жирный)
        result = result.replace(Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")) { match ->
            "<em>${match.groupValues[1]}</em>"
        }

        // Курсив _текст_
        result = result.replace(Regex("""_(.*?)_""")) { match ->
            "<em>${match.groupValues[1]}</em>"
        }

        // Инлайн код `текст`
        result = result.replace(Regex("""`([^`]+)`""")) { match ->
            "<code>${match.groupValues[1]}</code>"
        }

        // Горизонтальная черта ---
        result = result.replace(Regex("""^---$""", RegexOption.MULTILINE), "<hr/>")

        // Списки (нумерованные и маркированные)
        val lines = result.split("\n").toMutableList()
        var inList = false
        var listType = "" // "ul" или "ol"
        val processedLines = mutableListOf<String>()

        for (i in lines.indices) {
            val line = lines[i].trim()

            // Нумерованный список
            if (Regex("""^\d+\.\s+.*""").matches(line)) {
                if (!inList || listType != "ol") {
                    if (inList) {
                        processedLines.add("</$listType>")
                    }
                    processedLines.add("<ol>")
                    inList = true
                    listType = "ol"
                }
                val content = line.replace(Regex("""^\d+\.\s+"""), "")
                processedLines.add("<li>$content</li>")
                continue
            }

            // Маркированный список
            if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList || listType != "ul") {
                    if (inList) {
                        processedLines.add("</$listType>")
                    }
                    processedLines.add("<ul>")
                    inList = true
                    listType = "ul"
                }
                val content = line.replace(Regex("""^[-*]\s+"""), "")
                processedLines.add("<li>$content</li>")
                continue
            }

            // Закрываем список если текущая строка не элемент списка
            if (inList && line.isNotBlank()) {
                processedLines.add("</$listType>")
                inList = false
                listType = ""
            }

            processedLines.add(line)
        }

        // Закрываем список в конце текста
        if (inList) {
            processedLines.add("</$listType>")
        }

        result = processedLines.joinToString(if (isJcefMode) "<br/>" else "\n")

        return result
    }

    /**
     * Простая эвристика: определяем, что строка уже содержит HTML-теги верхнего уровня
     */
    private fun looksLikeHtml(s: String): Boolean {
        val t = s.trimStart()
        if (!t.startsWith("<")) return false
        // Ищем распространённые блочные теги: p, ol, ul, li, h1-6, pre, code, table, div, span
        val htmlTagPattern = Regex("(?is)<\\s*(p|ol|ul|li|h[1-6]|pre|code|table|thead|tbody|tr|td|th|div|span)\\b")
        return htmlTagPattern.containsMatchIn(t)
    }

    private fun normalizeLanguage(lang: String): String {
        if (lang.isBlank()) return "text"
        val canonical = LANGUAGE_ALIASES[lang] ?: lang
        return if (canonical.isBlank()) "text" else canonical
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    /**
     * Удаляет общие начальные отступы из всех строк кода
     */
    private fun removeCommonIndent(code: String): String {
        if (code.isBlank()) return code

        val lines = code.split("\n")
        if (lines.isEmpty()) return code

        // Находим минимальный отступ среди непустых строк
        val minIndent = lines
            .filter { it.isNotBlank() }
            .map { line -> line.takeWhile { it.isWhitespace() }.length }
            .minOrNull() ?: 0

        println("DEBUG: removeCommonIndent - minIndent=$minIndent, lines count=${lines.size}")

        // Удаляем минимальный отступ из всех строк
        val result = lines.joinToString("\n") { line ->
            if (line.length >= minIndent) line.substring(minIndent) else line
        }

        println("DEBUG: removeCommonIndent - result has ${result.count { it == '\n' }} newlines")
        return result
    }

    private fun prettyPrintJson(input: String): String {
        val sb = StringBuilder()
        var indent = 0
        var inString = false
        var escape = false
        for (ch in input.trim()) {
            when {
                escape -> {
                    sb.append(ch); escape = false
                }

                ch == '\\' && inString -> {
                    sb.append(ch); escape = true
                }

                ch == '"' -> {
                    inString = !inString; sb.append(ch)
                }

                inString -> sb.append(ch)
                ch == '{' || ch == '[' -> {
                    sb.append(ch)
                    sb.append('\n')
                    indent++
                    sb.append("  ".repeat(indent))
                }

                ch == '}' || ch == ']' -> {
                    sb.append('\n')
                    indent = (indent - 1).coerceAtLeast(0)
                    sb.append("  ".repeat(indent))
                    sb.append(ch)
                }

                ch == ',' -> {
                    sb.append(ch)
                    sb.append('\n')
                    sb.append("  ".repeat(indent))
                }

                ch == ':' -> sb.append(": ")
                ch.isWhitespace() -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    companion object {
        private val HISTORY_WIDTH = 400
        private val HISTORY_HEIGHT = 400
        private val INPUT_HEIGHT = 80
        private val INPUT_ROWS = 3
        private val LANGUAGE_ALIASES = mapOf(
            "js" to "javascript",
            "ts" to "typescript",
            "tsx" to "typescript",
            "jsx" to "javascript",
            "py" to "python",
            "kt" to "kotlin",
            "kts" to "kotlin",
            "c++" to "cpp",
            "hpp" to "cpp",
            "h" to "c",
            "cs" to "csharp",
            "rb" to "ruby",
            "ps1" to "powershell",
            "sh" to "bash",
            "shell" to "bash",
            "sql" to "sql",
            "yml" to "yaml",
            "yaml" to "yaml",
            "md" to "markdown"
        )
        private const val COPY_LINK_PREFIX = "ride-copy:"
        private const val CODE_CACHE_LIMIT = 200
    }

    private fun subscribeToAppearanceChanges() {
        val connection = ApplicationManager.getApplication().messageBus.connect(project)
        connection.subscribe(ChatAppearanceListener.TOPIC, ChatAppearanceListener {
            SwingUtilities.invokeLater { scheduleRefreshAppearance() }
        })
        // Реакция на смену темы IDE
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
            SwingUtilities.invokeLater {
                jcefView?.setTheme(ThemeTokens.fromSettings(settings).toJcefMap())
                scheduleRefreshAppearance()
            }
        })
    }

    private fun handleComposerKey(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER) {
            if (e.isShiftDown) {
                // Вставляем перевод строки вместо отправки
                e.consume()
                val caret = inputArea.caretPosition
                val text = inputArea.text
                val sb = StringBuilder(text)
                sb.insert(caret, "\n")
                inputArea.text = sb.toString()
                inputArea.caretPosition = caret + 1
            } else {
                e.consume()
                sendMessage()
            }
        }
    }

    private fun refreshAppearance() {
        val history = chatService.getHistory()
        // Обновляем тему JCEF без перезагрузки страницы
        jcefView?.setTheme(jcefThemeTokens())
        initHtml()
        if (history.isNotEmpty()) history.forEach { appendMessage(it, addToHistory = false) }
        else appendSystemMessage("👋 Привет! Я AI-ассистент для разработчиков. Чем могу помочь?")
        if (!settings.isConfigured()) {
            appendSystemMessage("⚠️ Плагин не настроен. Перейдите в Settings → Tools → Ride для настройки API ключа.")
        }
    }

    /**
     * Дебаунс обновления внешнего вида, чтобы не перерисовывать UI слишком часто.
     */
    private var refreshTimer: javax.swing.Timer? = null
    private fun scheduleRefreshAppearance(delayMs: Int = 150) {
        if (refreshTimer == null) {
            refreshTimer = javax.swing.Timer(delayMs) {
                refreshAppearance()
            }.apply { isRepeats = false }
        }
        refreshTimer!!.stop()
        refreshTimer!!.initialDelay = delayMs
        refreshTimer!!.start()
    }

    /**
     * Токены темы для JCEF (CSS variables)
     */
    private fun jcefThemeTokens(): Map<String, String> = mapOf(
        // Общие
        "bg" to settings.chatCodeBackgroundColor, // временно используем фон код-блоков как общий, до ввода отдельного токена
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

    private fun registerCodeBlock(code: String): String {
        if (codeBlockRegistry.size >= CODE_CACHE_LIMIT) {
            val iterator = codeBlockRegistry.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        val key = "code-" + UUID.randomUUID().toString()
        codeBlockRegistry[key] = code
        return key
    }

    private fun copyCodeToClipboard(code: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(code), null)
    }
}
