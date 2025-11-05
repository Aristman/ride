package ru.marslab.ide.ride.ui.chat

import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBTextArea
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Swing-вариант @-пикера для основного поля ввода (JBTextArea).
 * Открывается при вводе символа '@' и фильтрует список по последующим символам до пробела/переноса.
 */
class AtPickerSwing(
    private val input: JBTextArea,
) {
    private val suggestionService = AtPickerSuggestionService()
    private val popup = JPopupMenu()
    private val list = JList(DefaultListModel<String>())

    private var visible = false
    private var atStartOffset: Int = -1
    private var lastQuery: String = ""

    init {
        val scroll = JScrollPane(list)
        scroll.border = BorderFactory.createEmptyBorder()
        popup.layout = BoxLayout(popup, BoxLayout.Y_AXIS)
        popup.add(scroll)
        list.visibleRowCount = 10
        val pref = scroll.preferredSize
        scroll.preferredSize = java.awt.Dimension(pref.width.coerceAtLeast(420), 220)

        list.isFocusable = false
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) insertSelected()
            }
        })

        input.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                if (e.keyChar == '@') {
                    // Показать сразу при вводе '@' с пустым запросом
                    SwingUtilities.invokeLater { handleChange() }
                }
            }
        })

        // Глобальные биндинги клавиш при фокусе на input, чтобы стрелки не уходили в textarea
        fun bindKey(key: String, action: () -> Unit) {
            val im = input.getInputMap(JComponent.WHEN_FOCUSED)
            val am = input.actionMap
            im.put(KeyStroke.getKeyStroke(key), key)
            am.put(key, object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    if (!visible) return
                    action()
                }
            })
        }
        bindKey("UP") { moveSelection(-1) }
        bindKey("DOWN") { moveSelection(1) }
        bindKey("ENTER") { insertSelected() }
        bindKey("ESCAPE") { hidePopup() }

        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = handleChange()
            override fun removeUpdate(e: DocumentEvent) = handleChange()
            override fun changedUpdate(e: DocumentEvent) = handleChange()
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
        showAndUpdateAsync(query)
    }

    private fun showAndUpdateAsync(query: String) {
        lastQuery = query
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        // Фетчим подсказки в пуле потоков, чтобы не блокировать EDT
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            val items = try { suggestionService.suggestFiles(project, query) } catch (_: Throwable) { emptyList() }
            SwingUtilities.invokeLater {
                // Если за время запроса строка изменилась — обновление отменяем
                if (lastQuery != query) return@invokeLater
                val model = list.model as DefaultListModel<String>
                model.removeAllElements()
                items.forEach { model.addElement(it) }
                if (model.size() == 0) { hidePopup(); return@invokeLater }
                if (!visible) showPopup()
                list.selectedIndex = 0
            }
        }
    }

    private fun showPopup() {
        try {
            val caretPos = input.caretPosition
            val rect = input.modelToView(caretPos)
            popup.pack()
            val size = if (popup.preferredSize.height > 0) popup.preferredSize.height else 220
            val visible = input.visibleRect
            val spaceAbove = rect.y - visible.y
            val showAbove = spaceAbove > size + 6
            val point = if (showAbove) Point(rect.x, rect.y - (size + 6)) else Point(rect.x, rect.y + rect.height + 6)
            val local = SwingUtilities.convertPoint(input, point, input)
            popup.show(input, local.x, local.y)
            visible = true
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun hidePopup() {
        popup.isVisible = false
        visible = false
    }

    private fun moveSelection(delta: Int) {
        val max = (list.model.size - 1).coerceAtLeast(0)
        val next = (if (list.selectedIndex < 0) 0 else list.selectedIndex) + delta
        val idx = next.coerceIn(0, max)
        list.selectedIndex = idx
        if (idx >= 0) list.ensureIndexIsVisible(idx)
    }

    private fun insertSelected() {
        val value = list.selectedValue ?: return
        if (atStartOffset < 0) return
        val text = input.text
        val caret = input.caretPosition
        val before = text.substring(0, atStartOffset)
        val after = text.substring(caret)
        input.text = before + "@" + value + after
        input.caretPosition = (before + "@" + value).length
        hidePopup()
        input.requestFocusInWindow()
    }
}
