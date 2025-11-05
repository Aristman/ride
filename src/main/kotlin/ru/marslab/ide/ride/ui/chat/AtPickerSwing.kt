package ru.marslab.ide.ride.ui.chat

import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBTextArea
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import com.intellij.openapi.diagnostic.Logger
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener

/**
 * Swing-вариант @-пикера для основного поля ввода (JBTextArea).
 * Открывается при вводе символа '@' и фильтрует список по последующим символам до пробела/переноса.
 */
class AtPickerSwing(
    private val input: JBTextArea,
) {
    private val logger = Logger.getInstance(AtPickerSwing::class.java)
    private val suggestionService = AtPickerSuggestionService()
    private val popup = JPopupMenu()
    private val list = JList(DefaultListModel<String>())

    private var visible = false
    private var atStartOffset: Int = -1
    private var lastQuery: String = ""
    private var cacheAllFiles: List<String> = emptyList() // workspace-relative paths

    init {
        val scroll = JScrollPane(list)
        scroll.border = BorderFactory.createEmptyBorder()
        popup.layout = BoxLayout(popup, BoxLayout.Y_AXIS)
        popup.add(scroll)
        list.visibleRowCount = 10
        val pref = scroll.preferredSize
        scroll.preferredSize = java.awt.Dimension(pref.width.coerceAtLeast(420), 220)

        list.isFocusable = false
        popup.isFocusable = false
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) insertSelected()
            }
        })

        input.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                if (e.keyChar == '@') {
                    // Показать сразу при вводе '@' с пустым запросом
                    logger.debug("AtPickerSwing: '@' typed, opening popup")
                    SwingUtilities.invokeLater { handleChange() }
                }
            }
            override fun keyPressed(e: KeyEvent) {
                if (!visible) return
                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> { logger.debug("AtPickerSwing: DOWN"); moveSelection(1); e.consume() }
                    KeyEvent.VK_UP -> { logger.debug("AtPickerSwing: UP"); moveSelection(-1); e.consume() }
                    KeyEvent.VK_ENTER -> { logger.debug("AtPickerSwing: ENTER"); insertSelected(sync = true); e.consume() }
                    KeyEvent.VK_SPACE -> { logger.debug("AtPickerSwing: SPACE"); insertSelected(sync = true); e.consume() }
                    KeyEvent.VK_ESCAPE -> { logger.debug("AtPickerSwing: ESC"); hidePopup(); e.consume() }
                }
            }
        })

        // Глобальные биндинги клавиш: перехватываем даже если caret двигается
        fun bindKey(keyStroke: KeyStroke, action: () -> Unit) {
            input.registerKeyboardAction({ _ -> if (visible) { logger.debug("AtPickerSwing: bind ${keyStroke}"); action() } }, keyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW)
        }
        bindKey(KeyStroke.getKeyStroke("UP")) { moveSelection(-1) }
        bindKey(KeyStroke.getKeyStroke("DOWN")) { moveSelection(1) }
        bindKey(KeyStroke.getKeyStroke("ENTER")) { insertSelected() }
        bindKey(KeyStroke.getKeyStroke("ESCAPE")) { hidePopup() }

        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = handleChange()
            override fun removeUpdate(e: DocumentEvent) = handleChange()
            override fun changedUpdate(e: DocumentEvent) = handleChange()
        })

        // Если курсор переставили сразу за '@' — показать попап с пустым запросом
        input.addCaretListener(object : CaretListener {
            override fun caretUpdate(e: CaretEvent) {
                val caret = input.caretPosition
                val text = input.text
                if (caret in 1..text.length && text[caret - 1] == '@') {
                    atStartOffset = caret - 1
                    logger.debug("AtPickerSwing: caret after '@' -> show popup")
                    if (cacheAllFiles.isEmpty()) preloadAllFiles { filterAndRender("") } else filterAndRender("")
                }
            }
        })
    }

    private fun handleChange() {
        var caret = input.caretPosition
        val text = input.text
        if (caret > text.length) caret = text.length
        if (caret <= 0 || text.isEmpty()) { hidePopup(); return }

        // Ищем ближайший '@' слева до разделителя
        var i = caret - 1
        var foundAt = -1
        while (i >= 0) {
            val ch = text[i]
            if (ch == '@') { foundAt = i; break }
            if (ch.isWhitespace()) break
            i--
        }
        if (foundAt < 0) { hidePopup(); return }

        atStartOffset = foundAt
        val query = text.substring(foundAt + 1, caret)
        atStartOffset = foundAt
        logger.debug("AtPickerSwing: handleChange query='${query}'")
        if (cacheAllFiles.isEmpty()) preloadAllFiles { filterAndRender(query) } else filterAndRender(query)
    }

    private fun preloadAllFiles(onReady: () -> Unit) {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            val items = try { suggestionService.suggestFiles(project, "", 2000) } catch (t: Throwable) { logger.warn("AtPickerSwing: preloadAllFiles error: ${t.message}", t); emptyList() }
            SwingUtilities.invokeLater {
                cacheAllFiles = items
                logger.debug("AtPickerSwing: preloadAllFiles loaded=${items.size}")
                onReady()
            }
        }
    }

    private fun filterAndRender(query: String) {
        lastQuery = query
        val q = query.trim()
        val filtered = if (q.isBlank()) cacheAllFiles else cacheAllFiles.filter { path ->
            val name = path.substringAfterLast('/')
            name.contains(q, ignoreCase = true) || camelCaseMatch(name, q)
        }

        val model = list.model as DefaultListModel<String>
        model.removeAllElements()
        filtered.forEach { model.addElement(it) }
        logger.debug("AtPickerSwing: filterAndRender q='${q}' size=${filtered.size}")
        if (model.size() == 0) { hidePopup(); return }
        if (!visible) showPopup()
        list.selectedIndex = 0
    }

    private fun showPopup() {
        try {
            val caretPos = input.caretPosition
            val rect = input.modelToView(caretPos)
            popup.pack()
            val size = if (popup.preferredSize.height > 0) popup.preferredSize.height else 220
            // Всегда выше поля ввода (над верхней границей компонента), относительно компонента допускается отрицательная координата
            val y = - (size + 6)
            val point = Point(rect.x.coerceAtLeast(0), y)
            val local = SwingUtilities.convertPoint(input, point, input)
            popup.show(input, local.x, local.y)
            this.visible = true
            // сообщаем в инпут, что попап активен
            input.putClientProperty("ride.atpicker.visible", true)
            logger.debug("AtPickerSwing: popup shown at (${local.x}, ${local.y}) height=${size}")
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun camelCaseMatch(text: String, pattern: String): Boolean {
        if (pattern.isBlank()) return true
        val caps = text.filter { it.isUpperCase() }
        return caps.contains(pattern.replace(" ", ""), ignoreCase = true)
    }

    private fun hidePopup() {
        popup.isVisible = false
        visible = false
        input.putClientProperty("ride.atpicker.visible", false)
        logger.debug("AtPickerSwing: popup hidden")
    }

    private fun moveSelection(delta: Int) {
        val max = (list.model.size - 1).coerceAtLeast(0)
        val next = (if (list.selectedIndex < 0) 0 else list.selectedIndex) + delta
        val idx = next.coerceIn(0, max)
        list.selectedIndex = idx
        if (idx >= 0) list.ensureIndexIsVisible(idx)
        logger.debug("AtPickerSwing: moveSelection idx=${idx}")
    }

    private fun insertSelected(sync: Boolean = false) {
        if (list.selectedIndex < 0 && list.model.size > 0) list.selectedIndex = 0
        val value = list.selectedValue ?: return
        if (atStartOffset < 0) return
        val text = input.text
        val caret = input.caretPosition
        val before = text.substring(0, atStartOffset)
        val after = text.substring(caret)
        val next = before + "@" + value + after
        val apply: () -> Unit = {
            input.text = next
            input.caretPosition = (before + "@" + value).length
            input.requestFocusInWindow()
            input.grabFocus()
        }
        if (sync) apply() else SwingUtilities.invokeLater { apply() }
        logger.debug("AtPickerSwing: insertSelected value='${value}' at=${atStartOffset}")
        hidePopup()
    }
}
