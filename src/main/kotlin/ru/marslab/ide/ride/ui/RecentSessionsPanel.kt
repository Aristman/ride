package ru.marslab.ide.ride.ui

import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.SimpleListCellRenderer
import ru.marslab.ide.ride.model.ChatSession
import ru.marslab.ide.ride.service.ChatService
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import java.awt.BorderLayout

/**
 * Панель последних сессий (минимальный вариант).
 */
class RecentSessionsPanel(private val chatService: ChatService, private val onSelect: (ChatSession) -> Unit) : JPanel(BorderLayout()) {
    private val listModel = javax.swing.DefaultListModel<ChatSession>()
    private val list = JBList(listModel)

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = SimpleListCellRenderer.create<ChatSession> { label, value, _ ->
            label.text = value.title
        }
        list.addListSelectionListener {
            val s = list.selectedValue ?: return@addListSelectionListener
            onSelect(s)
        }
        add(JBScrollPane(list), BorderLayout.CENTER)
        refresh()
    }

    fun refresh(selectCurrent: Boolean = true) {
        val sessions = chatService.getSessions()
        listModel.clear()
        sessions.forEach { listModel.addElement(it) }
        if (selectCurrent) {
            val currentId = chatService.getCurrentSessionId()
            val idx = sessions.indexOfFirst { it.id == currentId }
            if (idx >= 0) list.selectedIndex = idx
        }
    }
}
