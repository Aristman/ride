package ru.marslab.ide.ride.ui.builder

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import ru.marslab.ide.ride.service.ChatService
import ru.marslab.ide.ride.ui.config.ChatPanelConfig
import ru.marslab.ide.ride.ui.manager.HtmlDocumentManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * Строит UI компоненты для чата
 */
class ChatUiBuilder(
    private val chatService: ChatService,
    private val htmlDocumentManager: HtmlDocumentManager
) {

    /**
     * Создает верхнюю панель с тулбаром и вкладками сессий
     */
    fun buildTopPanel(): TopPanelComponents {
        val actionManager = ActionManager.getInstance()
        val toolbarGroup = (actionManager.getAction("Ride.ToolWindowActions") as? DefaultActionGroup)
            ?: DefaultActionGroup()
        val toolbar = actionManager.createActionToolbar("RideToolbar", toolbarGroup, true)

        val sessionsTabs = JBTabbedPane()
        sessionsTabs.addChangeListener {
            val idx = sessionsTabs.selectedIndex
            val sessions = chatService.getSessions()
            if (idx in sessions.indices) {
                if (chatService.switchSession(sessions[idx].id)) {
                    // В будущем можно добавить обновление внешнего вида
                }
            }
        }

        val panel = JPanel(BorderLayout())
        panel.add(toolbar.component, BorderLayout.NORTH)
        panel.add(sessionsTabs, BorderLayout.SOUTH)

        return TopPanelComponents(
            panel = panel,
            toolbar = toolbar,
            sessionsTabs = sessionsTabs
        )
    }

    /**
     * Создает центральную компонент для отображения сообщений
     */
    fun buildCenterComponent(): JComponent {
        return htmlDocumentManager.createContentComponent()
    }

    /**
     * Создает нижнюю панель с полем ввода и кнопками
     */
    fun buildBottomPanel(
        onSendMessage: () -> Unit,
        onClearChat: () -> Unit
    ): BottomPanelComponents {
        // Поле ввода
        val inputArea = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            rows = ChatPanelConfig.INPUT_ROWS
            font = font.deriveFont(14f)
        }

        // Обработчик клавиш
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) = handleInputKey(e, inputArea, onSendMessage)
        })

        val inputScrollPane = JBScrollPane(inputArea).apply {
            preferredSize = Dimension(
                ChatPanelConfig.HISTORY_WIDTH,
                ChatPanelConfig.INPUT_HEIGHT
            )
        }

        // Кнопки
        val sendButton = JButton("Отправить").apply {
            addActionListener { onSendMessage() }
        }
        val clearButton = JButton("Очистить").apply {
            addActionListener { onClearChat() }
        }

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(sendButton)
            add(Box.createHorizontalStrut(5))
            add(clearButton)
        }

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            add(inputScrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        return BottomPanelComponents(
            panel = panel,
            inputArea = inputArea,
            sendButton = sendButton,
            clearButton = clearButton
        )
    }

    /**
     * Обновляет вкладки сессий
     */
    fun refreshTabs(sessionsTabs: JBTabbedPane) {
        val sessions = chatService.getSessions()
        sessionsTabs.removeAll()
        sessions.forEach { session ->
            sessionsTabs.addTab(session.title, JPanel())
        }
        val current = chatService.getCurrentSessionId()
        val idx = sessions.indexOfFirst { it.id == current }
        if (idx >= 0) {
            sessionsTabs.selectedIndex = idx
        }
    }

    /**
     * Обрабатывает нажатия клавиш в поле ввода
     */
    private fun handleInputKey(
        e: KeyEvent,
        inputArea: JBTextArea,
        onSendMessage: () -> Unit
    ) {
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
                onSendMessage()
            }
        }
    }

    /**
     * Показывает диалог подтверждения очистки чата
     */
    fun showClearChatConfirmation(parentComponent: JComponent): Boolean {
        val result = JOptionPane.showConfirmDialog(
            parentComponent,
            ChatPanelConfig.Messages.CONFIRM_CLEAR_CHAT,
            ChatPanelConfig.Messages.CONFIRMATION_TITLE,
            JOptionPane.YES_NO_OPTION
        )
        return result == JOptionPane.YES_OPTION
    }

    /**
     * Устанавливает фокус на поле ввода и позиционирует курсор в конец
     */
    fun focusInputField(inputArea: JBTextArea) {
        SwingUtilities.invokeLater {
            inputArea.requestFocusInWindow()
            inputArea.grabFocus()
            inputArea.requestFocus()
            inputArea.caretPosition = inputArea.document.length
        }
    }

    /**
     * Включает/выключает UI элементы ввода
     */
    fun setInputEnabled(
        inputArea: JBTextArea,
        sendButton: JButton,
        enabled: Boolean
    ) {
        inputArea.isEnabled = enabled
        sendButton.isEnabled = enabled

        if (enabled) {
            focusInputField(inputArea)
        }
    }
}

