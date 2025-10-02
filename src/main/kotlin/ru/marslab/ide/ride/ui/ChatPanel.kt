package ru.marslab.ide.ride.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import ru.marslab.ide.ride.model.Message
import ru.marslab.ide.ride.model.MessageRole
import ru.marslab.ide.ride.service.ChatService
import ru.marslab.ide.ride.settings.ChatAppearanceListener
import ru.marslab.ide.ride.settings.PluginSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.LinkedHashMap
import java.util.UUID
import javax.swing.*
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent

/**
 * Главная панель чата
 */
class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val chatService = service<ChatService>()
    private val settings = service<PluginSettings>()
    
    private val chatHistoryArea: JEditorPane
    private val htmlBuffer = StringBuilder()
    private val codeBlockRegistry = LinkedHashMap<String, String>()
    private var loadingStart: Int = -1
    private var loadingEnd: Int = -1
    private val inputArea: JBTextArea
    private val sendButton: JButton
    private val clearButton: JButton
    private var lastRole: MessageRole? = null
    
    init {
        // Область истории чата (HTML)
        chatHistoryArea = JEditorPane().apply {
            contentType = "text/html"
            isEditable = false
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    val description = event.description
                    if (description != null && description.startsWith(COPY_LINK_PREFIX)) {
                        val key = description.removePrefix(COPY_LINK_PREFIX)
                        codeBlockRegistry[key]?.let { copyCodeToClipboard(it) }
                    }
                }
            }
        }
        initHtml()
        subscribeToAppearanceChanges()
        
        val historyScrollPane = JBScrollPane(chatHistoryArea).apply {
            preferredSize = Dimension(400, 400)
        }
        
        // Область ввода
        inputArea = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            rows = 3
            font = font.deriveFont(14f)
        }
        
        // Обработка Enter для отправки
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    sendMessage()
                }
            }
        })
        
        val inputScrollPane = JBScrollPane(inputArea).apply {
            preferredSize = Dimension(400, 80)
        }
        
        // Кнопки
        sendButton = JButton("Отправить").apply {
            addActionListener { sendMessage() }
        }
        
        clearButton = JButton("Очистить").apply {
            addActionListener { clearChat() }
        }
        
        // Панель с кнопками
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(sendButton)
            add(Box.createHorizontalStrut(5))
            add(clearButton)
        }
        
        // Нижняя панель (ввод + кнопки)
        val bottomPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            add(inputScrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
        
        // Компоновка
        add(historyScrollPane, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)
        
        // Загружаем историю при открытии
        loadHistory()
        
        // Проверяем настройки
        if (!settings.isConfigured()) {
            appendSystemMessage("⚠️ Плагин не настроен. Перейдите в Settings → Tools → Ride для настройки API ключа.")
        }
    }
    
    /**
     * Отправляет сообщение пользователя
     */
    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isEmpty()) {
            return
        }
        
        // Очищаем поле ввода
        inputArea.text = ""
        
        // Отображаем сообщение пользователя
        appendMessage(Message(content = text, role = MessageRole.USER))
        
        // Блокируем UI во время обработки
        setUIEnabled(false)
        appendSystemMessage("⏳ Обработка запроса...")
        
        // Отправляем запрос (проверка настроек будет в фоновом потоке)
        chatService.sendMessage(
            userMessage = text,
            project = project,
            onResponse = { message ->
                // Удаляем сообщение о загрузке
                removeLastSystemMessage()
                // Отображаем ответ
                appendMessage(message)
                setUIEnabled(true)
            },
            onError = { error ->
                // Удаляем сообщение о загрузке
                removeLastSystemMessage()
                // Отображаем ошибку
                appendSystemMessage("❌ Ошибка: $error")
                setUIEnabled(true)
            }
        )
    }
    
    /**
     * Очищает историю чата
     */
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
    
    /**
     * Загружает историю сообщений
     */
    private fun loadHistory() {
        val history = chatService.getHistory()
        if (history.isNotEmpty()) {
            history.forEach { message ->
                appendMessage(message, addToHistory = false)
            }
        } else {
            appendSystemMessage("👋 Привет! Я AI-ассистент для разработчиков. Чем могу помочь?")
        }
    }
    
    /**
     * Добавляет сообщение в историю
     */
    private fun appendMessage(message: Message, addToHistory: Boolean = true) {
        val roleClass = when (message.role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> "system"
        }
        val prefix = when (message.role) {
            MessageRole.USER -> "👤 Вы"
            MessageRole.ASSISTANT -> "🤖 Ассистент"
            MessageRole.SYSTEM -> "ℹ️ Система"
        }
        val bodyHtml = renderContentToHtml(message.content)
        val afterSystemClass = if (message.role == MessageRole.USER && lastRole == MessageRole.SYSTEM) " after-system" else ""
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
            </div>
        """.trimIndent()
        appendHtml(chunk)
        lastRole = message.role
    }
    
    /**
     * Добавляет системное сообщение
     */
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
    
    /**
     * Удаляет последнее системное сообщение (для удаления "Обработка запроса...")
     */
    private fun removeLastSystemMessage() {
        if (loadingStart != -1 && loadingEnd != -1 && loadingStart < loadingEnd && loadingEnd <= htmlBuffer.length) {
            htmlBuffer.delete(loadingStart, loadingEnd)
            // сбрасываем индексы
            loadingStart = -1
            loadingEnd = -1
            refreshEditor()
        }
    }
    
    /**
     * Включает/выключает UI элементы
     */
    private fun setUIEnabled(enabled: Boolean) {
        inputArea.isEnabled = enabled
        sendButton.isEnabled = enabled
    }

    private fun initHtml() {
        htmlBuffer.setLength(0)
        loadingStart = -1
        loadingEnd = -1
        lastRole = null
        codeBlockRegistry.clear()
        val fontSize = settings.chatFontSize
        val codeFontSize = (fontSize - 1).coerceAtLeast(10)
        val prefixColor = settings.chatPrefixColor
        val codeBg = settings.chatCodeBackgroundColor
        val codeText = settings.chatCodeTextColor
        val codeBorder = settings.chatCodeBorderColor
        val userBg = settings.chatUserBackgroundColor
        val userBorder = settings.chatUserBorderColor

        htmlBuffer.append(
            """
                <html>
                <head>
                  <style>
                    body { font-family: Arial, sans-serif; font-size: ${fontSize}px; }
                    .msg { margin-top: 8px; margin-left: 8px; margin-right: 8px; margin-bottom: 12px; }
                    .prefix { color: $prefixColor; margin-bottom: 4px; }
                    .content { }
                    .msg.user .prefix { color: $prefixColor; margin-bottom: 6px; }
                    .msg.user .content { display: inline-block; background-color: $userBg; border: 1px solid $userBorder; padding: 10px 14px; color: inherit; text-align: left; }
                    .msg.user .content table.code-block { margin-top: 12px; }
                    /* Code block styling (configurable) */
                    pre { background-color: $codeBg; color: $codeText; padding: 8px; border: 1px solid $codeBorder; }
                    pre { margin: 0; }
                    code { font-family: monospace; font-size: ${codeFontSize}px; color: $codeText; }
                    table.code-block { width: 100%; border-collapse: collapse; margin-top: 8px; }
                    table.code-block td { padding: 0; }
                    td.code-lang { font-size: ${codeFontSize - 1}px; color: $prefixColor; padding: 4px 6px; }
                    td.code-copy-cell { text-align: right; padding: 4px 6px; }
                    a.code-copy-link { color: $prefixColor; text-decoration: none; display: inline-block; width: 20px; height: 20px; text-align: center; line-height: 20px; }
                    a.code-copy-link:hover { background-color: $userBorder; }
                    .code-copy-icon { font-size: ${codeFontSize}px; line-height: 1; font-family: 'Segoe UI Symbol', 'Apple Color Emoji', sans-serif; }
                    .msg.user { text-align: right; }
                    .msg.user .prefix { text-align: right; }
                    .msg.user .content { text-align: right; }
                    .msg.after-system { margin-top: 20px; }
                  </style>
                </head>
                <body>
            """.trimIndent()
        )
        refreshEditor()
    }

    private fun refreshEditor() {
        val html = StringBuilder(htmlBuffer).append("\n</body>\n</html>").toString()
        chatHistoryArea.text = html
        chatHistoryArea.caretPosition = chatHistoryArea.document.length
    }

    private fun appendHtml(chunk: String) {
        // Удаляем хвост </body></html> если уже добавляли
        val closing = "</body>\n</html>"
        val idx = htmlBuffer.indexOf(closing)
        if (idx != -1) {
            htmlBuffer.delete(idx, htmlBuffer.length)
        }
        htmlBuffer.append("\n").append(chunk)
        refreshEditor()
    }

    private fun appendHtmlWithRange(chunk: String): Pair<Int, Int> {
        // Удаляем хвост </body></html> если уже добавляли
        val closing = "</body>\n</html>"
        val idx = htmlBuffer.indexOf(closing)
        if (idx != -1) {
            htmlBuffer.delete(idx, htmlBuffer.length)
        }
        val start = htmlBuffer.length
        htmlBuffer.append("\n").append(chunk)
        val end = htmlBuffer.length
        refreshEditor()
        return start to end
    }

    private fun renderContentToHtml(text: String): String {
        // Наивная обработка fenced code blocks: ```lang\n...\n```
        val pattern = Regex("""```([\w#+.-]+)?[ \t]*\n?([\s\S]*?)```""", RegexOption.IGNORE_CASE)
        var lastIndex = 0
        val result = StringBuilder()
        pattern.findAll(text).forEach { m ->
            // Текст до блока
            val pre = text.substring(lastIndex, m.range.first)
            result.append(escapeHtml(pre).replace("\n", "<br/>"))
            val langRaw = (m.groups[1]?.value ?: "").trim().lowercase()
            val normalizedLang = normalizeLanguage(langRaw)
            var code = (m.groups[2]?.value ?: "").trim('\n', '\r')
            code = runCatching {
                when (normalizedLang) {
                    "json" -> prettyPrintJson(code)
                    else -> code
                }
            }.getOrDefault(code)
            val escaped = escapeHtml(code)
            val codeId = registerCodeBlock(code)
            val langLabel = normalizedLang.takeUnless { it.isBlank() || it == "text" }?.uppercase() ?: ""
            result.append("<table class='code-block'>")
            result.append("<tr><td class='code-lang'>").append(escapeHtml(langLabel)).append("</td>")
            result.append("<td class='code-copy-cell'><a href='${COPY_LINK_PREFIX}$codeId' class='code-copy-link' title='Скопировать'><span class='code-copy-icon'>&#128203;</span></a></td></tr>")
            result.append("<tr><td colspan='2'><pre><code class='lang-$normalizedLang'>").append(escaped).append("</code></pre></td></tr>")
            result.append("</table>")
            lastIndex = m.range.last + 1
        }
        // Хвост
        if (lastIndex < text.length) {
            result.append(escapeHtml(text.substring(lastIndex)).replace("\n", "<br/>"))
        }
        return result.toString()
    }

    private fun normalizeLanguage(lang: String): String {
        if (lang.isBlank()) return "text"
        val canonical = LANGUAGE_ALIASES[lang] ?: lang
        return if (canonical.isBlank()) "text" else canonical
    }

    private fun escapeHtml(s: String): String {
        return s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    // Простой pretty-print для JSON без внешних зависимостей
    private fun prettyPrintJson(input: String): String {
        val sb = StringBuilder()
        var indent = 0
        var inString = false
        var escape = false
        for (ch in input.trim()) {
            when {
                escape -> { sb.append(ch); escape = false }
                ch == '\\' && inString -> { sb.append(ch); escape = true }
                ch == '"' -> { inString = !inString; sb.append(ch) }
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
                ch == ':' -> {
                    sb.append(": ")
                }
                ch.isWhitespace() -> { /* skip */ }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    companion object {
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
        ApplicationManager.getApplication()
            .messageBus
            .connect(project)
            .subscribe(ChatAppearanceListener.TOPIC, ChatAppearanceListener {
                SwingUtilities.invokeLater { refreshAppearance() }
            })
    }

    private fun refreshAppearance() {
        val history = chatService.getHistory()
        initHtml()
        if (history.isNotEmpty()) {
            history.forEach { appendMessage(it, addToHistory = false) }
        } else {
            appendSystemMessage("👋 Привет! Я AI-ассистент для разработчиков. Чем могу помочь?")
        }
        if (!settings.isConfigured()) {
            appendSystemMessage("⚠️ Плагин не настроен. Перейдите в Settings → Tools → Ride для настройки API ключа.")
        }
    }

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
