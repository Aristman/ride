package ru.marslab.ide.ride.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import ru.marslab.ide.ride.model.Message
import ru.marslab.ide.ride.model.MessageRole
import ru.marslab.ide.ride.service.ChatService
import ru.marslab.ide.ride.settings.PluginSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * Главная панель чата
 */
class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val chatService = service<ChatService>()
    private val settings = service<PluginSettings>()
    
    private val chatHistoryArea: JTextArea
    private val inputArea: JBTextArea
    private val sendButton: JButton
    private val clearButton: JButton
    
    init {
        // Область истории чата
        chatHistoryArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = font.deriveFont(14f)
        }
        
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
        
        // Проверяем настройки
        if (!settings.isConfigured()) {
            JOptionPane.showMessageDialog(
                this,
                "Пожалуйста, настройте API ключ в Settings → Tools → Ride",
                "Настройки не заданы",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        
        // Очищаем поле ввода
        inputArea.text = ""
        
        // Отображаем сообщение пользователя
        appendMessage(Message(content = text, role = MessageRole.USER))
        
        // Блокируем UI во время обработки
        setUIEnabled(false)
        appendSystemMessage("⏳ Обработка запроса...")
        
        // Отправляем запрос
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
            chatHistoryArea.text = ""
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
        val prefix = when (message.role) {
            MessageRole.USER -> "👤 Вы"
            MessageRole.ASSISTANT -> "🤖 Ассистент"
            MessageRole.SYSTEM -> "ℹ️ Система"
        }
        
        val text = "$prefix:\n${message.content}\n\n"
        chatHistoryArea.append(text)
        chatHistoryArea.caretPosition = chatHistoryArea.document.length
    }
    
    /**
     * Добавляет системное сообщение
     */
    private fun appendSystemMessage(text: String) {
        chatHistoryArea.append("ℹ️ $text\n\n")
        chatHistoryArea.caretPosition = chatHistoryArea.document.length
    }
    
    /**
     * Удаляет последнее системное сообщение (для удаления "Обработка запроса...")
     */
    private fun removeLastSystemMessage() {
        val text = chatHistoryArea.text
        val lastSystemIndex = text.lastIndexOf("ℹ️ ⏳ Обработка запроса...")
        if (lastSystemIndex != -1) {
            val endIndex = text.indexOf("\n\n", lastSystemIndex)
            if (endIndex != -1) {
                chatHistoryArea.text = text.substring(0, lastSystemIndex) + text.substring(endIndex + 2)
            }
        }
    }
    
    /**
     * Включает/выключает UI элементы
     */
    private fun setUIEnabled(enabled: Boolean) {
        inputArea.isEnabled = enabled
        sendButton.isEnabled = enabled
    }
}
