package ru.marslab.ide.ride.ui.builder

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import ru.marslab.ide.ride.service.ChatService
import ru.marslab.ide.ride.ui.ChatPanel
import ru.marslab.ide.ride.ui.components.ClosableTabbedPane
import ru.marslab.ide.ride.ui.config.ChatPanelConfig
import ru.marslab.ide.ride.ui.dialogs.CloseChatConfirmationDialog
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
    private val htmlDocumentManager: HtmlDocumentManager,
    private var chatPanel: (() -> ChatPanel)? = null
) {

    /**
     * Создает верхнюю панель с тулбаром и вкладками сессий
     */
    fun buildTopPanel(): TopPanelComponents {
        val actionManager = ActionManager.getInstance()
        val toolbarGroup = (actionManager.getAction("Ride.ToolWindowActions") as? DefaultActionGroup)
            ?: DefaultActionGroup()
        val toolbar = actionManager.createActionToolbar("RideToolbar", toolbarGroup, true)

        val sessionsTabs = ClosableTabbedPane()
        sessionsTabs.addChangeListener {
            val idx = sessionsTabs.selectedIndex
            val sessions = chatService.getSessions()
            if (idx in sessions.indices) {
                if (chatService.switchSession(sessions[idx].id)) {
                    // Обновляем отображение сообщений для новой сессии
                    chatPanel?.invoke()?.refreshAppearance()
                }
            }
        }

        sessionsTabs.closeListener = object : ClosableTabbedPane.CloseListener {
            override fun onTabClose(index: Int) {
                val sessions = chatService.getSessions()
                if (index !in sessions.indices) return
                val session = sessions[index]
                val parent = chatPanel?.invoke()
                val dialog = CloseChatConfirmationDialog(parent)
                val result = dialog.showAndGet()
                when (result.action) {
                    CloseChatConfirmationDialog.Action.CLOSE -> {
                        chatService.removeSession(session.id, deleteFromStorage = true)
                    }

                    CloseChatConfirmationDialog.Action.HIDE -> {
                        chatService.removeSession(session.id, deleteFromStorage = false)
                    }

                    CloseChatConfirmationDialog.Action.CANCEL -> return
                }
                refreshTabs(sessionsTabs)
                parent?.refreshAppearance()
            }
        }

        // Label для отображения размера контекста
        val contextSizeLabel = JLabel("Контекст: 0 токенов")
        contextSizeLabel.border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
        contextSizeLabel.font = contextSizeLabel.font.deriveFont(11f)
        contextSizeLabel.foreground = java.awt.Color(0x9aa0a6) // Серый цвет

        // Панель с toolbar и label
        val toolbarPanel = JPanel(BorderLayout())
        toolbarPanel.add(toolbar.component, BorderLayout.WEST)
        toolbarPanel.add(contextSizeLabel, BorderLayout.EAST)

        val panel = JPanel(BorderLayout())
        panel.add(toolbarPanel, BorderLayout.NORTH)
        panel.add(sessionsTabs, BorderLayout.SOUTH)

        return TopPanelComponents(
            panel = panel,
            toolbar = toolbar,
            sessionsTabs = sessionsTabs,
            contextSizeLabel = contextSizeLabel
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
        val micButton = JButton("🎤").apply {
            toolTipText = "Запись голоса"
            isEnabled = true
        }

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(sendButton)
            add(Box.createHorizontalStrut(5))
            add(clearButton)
            add(Box.createHorizontalStrut(5))
            add(micButton)
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
            clearButton = clearButton,
            micButton = micButton
        )
    }

    /**
     * Обновляет вкладки сессий
     */
    fun refreshTabs(sessionsTabs: JBTabbedPane) {
        val sessions = chatService.getSessions()
        sessionsTabs.removeAll()
        sessions.forEach { session ->
            when (sessionsTabs) {
                is ClosableTabbedPane -> sessionsTabs.addClosableTab(session.title, JPanel())
                else -> sessionsTabs.addTab(session.title, JPanel())
            }
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

