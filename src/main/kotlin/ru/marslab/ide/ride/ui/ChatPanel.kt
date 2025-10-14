package ru.marslab.ide.ride.ui

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.model.chat.MessageRole
import ru.marslab.ide.ride.service.ChatService
import ru.marslab.ide.ride.settings.ChatAppearanceListener
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.ui.builder.BottomPanelComponents
import ru.marslab.ide.ride.ui.builder.ChatUiBuilder
import ru.marslab.ide.ride.ui.builder.TopPanelComponents
import ru.marslab.ide.ride.ui.chat.JcefChatView
import ru.marslab.ide.ride.ui.config.ChatPanelConfig
import ru.marslab.ide.ride.ui.manager.HtmlDocumentManager
import ru.marslab.ide.ride.ui.manager.MessageDisplayManager
import ru.marslab.ide.ride.ui.renderer.ChatContentRenderer
import java.awt.BorderLayout
import javax.swing.*

/**
 * Главная панель чата (гибрид Swing + JCEF)
 * Рефакторенная версия с разделением ответственности
 */
class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val chatService = service<ChatService>()
    private val settings = service<PluginSettings>()

    // Компоненты UI
    private lateinit var uiBuilder: ChatUiBuilder
    private lateinit var htmlDocumentManager: HtmlDocumentManager
    private lateinit var messageDisplayManager: MessageDisplayManager
    private lateinit var contentRenderer: ChatContentRenderer

    // UI компоненты
    private lateinit var bottomComponents: BottomPanelComponents
    private lateinit var topComponents: TopPanelComponents

    // JCEF (если доступен)
    private val useJcef: Boolean = true
    private var jcefView: JcefChatView? = runCatching {
        if (useJcef) {
            // Проверяем доступность JCEF
            try {
                Class.forName("com.intellij.ui.jcef.JBCefBrowser")
                println("DEBUG: JCEF classes are available")
                
                val view = JcefChatView()
                println("✓ JCEF ChatView initialized successfully - подсветка кода будет доступна")
                view
            } catch (e: ClassNotFoundException) {
                println("✗ JCEF classes not found: ${e.message}")
                throw e
            }
        } else null
    }.getOrElse { e ->
        println("✗ JCEF ChatView initialization failed, using HTML fallback")
        println("  Причина: ${e.javaClass.simpleName}: ${e.message}")
        println("  Подсветка кода будет недоступна в fallback режиме")
        e.printStackTrace()
        null
    }

    init {
        initializeComponents()
        buildUI()
        loadInitialState()
    }

    /**
     * Инициализирует компоненты
     */
    private fun initializeComponents() {
        // Инициализируем менеджеры и рендереры
        htmlDocumentManager = HtmlDocumentManager(settings, jcefView)
        contentRenderer = ChatContentRenderer()
        messageDisplayManager = MessageDisplayManager(htmlDocumentManager, contentRenderer)
        uiBuilder = ChatUiBuilder(chatService, htmlDocumentManager) { this }

        // Инициализация HTML/темы
        htmlDocumentManager.initialize()
        subscribeToAppearanceChanges()
    }

    /**
     * Строит пользовательский интерфейс
     */
    private fun buildUI() {
        // Верхняя панель (тулбар + табы)
        topComponents = uiBuilder.buildTopPanel()
        add(topComponents.panel, BorderLayout.NORTH)
        // Назначаем targetComponent после добавления topPanel в иерархию
        topComponents.toolbar.targetComponent = topComponents.panel

        // Центральная область (JCEF или fallback HTML)
        add(uiBuilder.buildCenterComponent(), BorderLayout.CENTER)

        // Нижняя панель (композер)
        bottomComponents = uiBuilder.buildBottomPanel(
            onSendMessage = { sendMessage() },
            onClearChat = { clearChat() }
        )
        add(bottomComponents.panel, BorderLayout.SOUTH)
    }

    /**
     * Загружает начальное состояние
     */
    private fun loadInitialState() {
        // История и табы при старте
        loadHistory()
        refreshTabs()

        if (!settings.isConfigured()) {
            messageDisplayManager.displaySystemMessage(ChatPanelConfig.Messages.CONFIGURATION_WARNING)
        }
        
        // Уведомляем о режиме отображения
        if (jcefView == null) {
            messageDisplayManager.displaySystemMessage(
                "⚠️ JCEF недоступен - используется упрощенный режим отображения без подсветки кода. " +
                "Для полноценной подсветки синтаксиса убедитесь, что используется JetBrains Runtime (JBR)."
            )
        } else {
            messageDisplayManager.displaySystemMessage("✓ JCEF активен - доступна подсветка синтаксиса кода")
        }
    }

    /**
     * Очищает историю и обновляет интерфейс
     */
    fun clearHistoryAndRefresh() {
        chatService.clearHistory()
        messageDisplayManager.clearAllMessages()
        messageDisplayManager.displaySystemMessage(ChatPanelConfig.Messages.HISTORY_CLEARED)
    }

    /**
     * Отправляет сообщение пользователя
     */
    private fun sendMessage() {
        val text = bottomComponents.inputArea.text.trim()
        if (text.isEmpty()) return

        bottomComponents.inputArea.text = ""
        messageDisplayManager.displayMessage(Message(content = text, role = MessageRole.USER))
        setUIEnabled(false)
        messageDisplayManager.displaySystemMessage(ChatPanelConfig.Messages.PROCESSING_REQUEST)

        if (text.startsWith("/plan ")) {
            val actualMessage = text.removePrefix("/plan ").trim()
            sendMessageWithOrchestratorMode(
                project = project,
                text = actualMessage,
                onStepComplete = { message ->
                    messageDisplayManager.removeLastSystemMessage()
                    messageDisplayManager.displayMessage(message)
                    updateContextSize()
                },
                onError = { error ->
                    messageDisplayManager.removeLastSystemMessage()
                    messageDisplayManager.displaySystemMessage("Ошибка: $error")
                    setUIEnabled(true)
                },
                onComplete = {
                    updateContextSize()
                    setUIEnabled(true)
                }
            )
        } else {
            // Используем sendMessageWithTools для поддержки MCP операций
            chatService.sendMessageWithTools(
                userMessage = text,
                project = project,
                onResponse = { message ->
                    // Системные сообщения (о сжатии) приходят первыми
                    if (message.role == MessageRole.SYSTEM) {
                        messageDisplayManager.displayMessage(message)
                        // Обновляем счётчик с небольшой задержкой, чтобы история успела обновиться
                        SwingUtilities.invokeLater {
                            updateContextSize()
                        }
                    } else {
                        // Ответ ассистента
                        messageDisplayManager.removeLastSystemMessage()
                        messageDisplayManager.displayMessage(message)
                        updateContextSize()
                        setUIEnabled(true)
                    }
                },
                onError = { error ->
                    messageDisplayManager.removeLastSystemMessage()
                    messageDisplayManager.displaySystemMessage("${ChatPanelConfig.Icons.ERROR} Ошибка: $error")
                    setUIEnabled(true)
                },
                onToolExecution = { toolInfo ->
                    // Показываем индикатор выполнения tool
                    messageDisplayManager.displaySystemMessage("🔧 $toolInfo")
                }
            )
        }
    }

    /**
     * Очищает чат с подтверждением
     */
    private fun clearChat() {
        if (uiBuilder.showClearChatConfirmation(this)) {
            chatService.clearHistory()
            messageDisplayManager.clearAllMessages()
            messageDisplayManager.displaySystemMessage(ChatPanelConfig.Messages.HISTORY_CLEARED)
        }
    }

    /**
     * Загружает историю сообщений
     */
    private fun loadHistory() {
        val history = chatService.getHistory()
        if (history.isNotEmpty()) {
            history.forEach { messageDisplayManager.displayMessage(it, addToHistory = false) }
        } else {
            messageDisplayManager.displaySystemMessage(ChatPanelConfig.Messages.WELCOME)
        }
    }

    /**
     * Обновляет вкладки сессий
     */
    private fun refreshTabs() {
        uiBuilder.refreshTabs(topComponents.sessionsTabs)
    }

    /** Создание новой сессии из действия тулбара */
    fun onNewSession() {
        chatService.createNewSession()
        refreshTabs()
        refreshAppearance()
    }

    /**
     * Подписывается на изменения внешнего вида
     */
    private fun subscribeToAppearanceChanges() {
        val connection = ApplicationManager.getApplication().messageBus.connect(project)
        connection.subscribe(ChatAppearanceListener.TOPIC, ChatAppearanceListener {
            SwingUtilities.invokeLater { scheduleRefreshAppearance() }
        })
        // Реакция на смену темы IDE
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
            SwingUtilities.invokeLater {
                htmlDocumentManager.updateTheme()
                scheduleRefreshAppearance()
            }
        })
    }

    /**
     * Включает/выключает UI элементы ввода
     */
    private fun setUIEnabled(enabled: Boolean) {
        uiBuilder.setInputEnabled(
            bottomComponents.inputArea,
            bottomComponents.sendButton,
            enabled
        )
    }

    /**
     * Обновляет внешний вид (перерисовывает сообщения)
     */
    fun refreshAppearance() {
        val history = chatService.getHistory()
        htmlDocumentManager.updateTheme()
        messageDisplayManager.redrawMessages(history)
        updateContextSize()

        if (!settings.isConfigured()) {
            messageDisplayManager.displaySystemMessage(ChatPanelConfig.Messages.CONFIGURATION_WARNING)
        }
    }

    /**
     * Обновляет отображение размера контекста в токенах
     */
    private fun updateContextSize() {
        val history = chatService.getHistory()
        val tokenCounter = chatService.getTokenCounter()
        
        // Подсчитываем токены в истории
        val conversationHistory = history
            .filter { it.role != MessageRole.SYSTEM }
            .map { message ->
                ru.marslab.ide.ride.model.ConversationMessage(
                    content = message.content,
                    role = when (message.role) {
                        MessageRole.USER -> ru.marslab.ide.ride.model.ConversationRole.USER
                        MessageRole.ASSISTANT -> ru.marslab.ide.ride.model.ConversationRole.ASSISTANT
                        MessageRole.SYSTEM -> ru.marslab.ide.ride.model.ConversationRole.SYSTEM
                    }
                )
            }
        
        // Получаем системный промпт из настроек (как в ChatAgent)
        val systemPrompt = """
            Ты - AI-ассистент для разработчиков в IntelliJ IDEA.
            
            Твоя задача:
            - Помогать с вопросами о программировании
            - Объяснять концепции и паттерны
            - Предлагать решения проблем
            - Отвечать чётко и по существу
            
            Важно:
            - Используй примеры кода когда это уместно
            - Форматируй код в блоках с указанием языка
            - Будь конкретным и практичным
        """.trimIndent()
        
        val contextTokens = tokenCounter.countRequestTokens(
            systemPrompt = systemPrompt,
            userMessage = "",
            conversationHistory = conversationHistory
        )
        
        // Обновляем label
        topComponents.contextSizeLabel.text = "Контекст: $contextTokens токенов"
    }

    /**
     * Дебаунс обновления внешнего вида, чтобы не перерисовывать UI слишком часто
     */
    private var refreshTimer: javax.swing.Timer? = null
    private fun scheduleRefreshAppearance(delayMs: Int = ChatPanelConfig.Delays.APPEARANCE_REFRESH_MS) {
        if (refreshTimer == null) {
            refreshTimer = javax.swing.Timer(delayMs) {
                refreshAppearance()
            }.apply { isRepeats = false }
        }
        refreshTimer!!.stop()
        refreshTimer!!.initialDelay = delayMs
        refreshTimer!!.start()
    }
}