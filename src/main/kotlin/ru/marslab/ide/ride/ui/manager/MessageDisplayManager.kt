package ru.marslab.ide.ride.ui.manager

import ru.marslab.ide.ride.model.Message
import ru.marslab.ide.ride.model.MessageRole
import ru.marslab.ide.ride.ui.config.ChatPanelConfig
import ru.marslab.ide.ride.ui.renderer.ChatContentRenderer
import java.util.*
import com.intellij.openapi.components.service
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.service.ChatService

/**
 * Управляет отображением сообщений в чате
 */
class MessageDisplayManager(
    private val htmlDocumentManager: HtmlDocumentManager,
    private val contentRenderer: ChatContentRenderer
) {

    private var lastRole: MessageRole? = null
    private val codeBlockRegistry = LinkedHashMap<String, String>()
    private val jcefView = htmlDocumentManager.getJcefView()
    private val settings = service<PluginSettings>()

    /**
     * Отображает сообщение в чате
     */
    fun displayMessage(message: Message, addToHistory: Boolean = true) {
        when (message.role) {
            MessageRole.USER -> displayUserMessage(message)
            MessageRole.ASSISTANT -> displayAssistantMessage(message)
            MessageRole.SYSTEM -> displaySystemMessage(message.content)
        }

        lastRole = message.role
    }

    /**
     * Отображает системное сообщение
     */
    fun displaySystemMessage(text: String) {
        val isLoading = text.contains(ChatPanelConfig.Messages.PROCESSING_REQUEST)
        val htmlContent = contentRenderer.createSystemMessageHtml(text, isLoading)

        if (isLoading) {
            htmlDocumentManager.appendHtmlWithRange(htmlContent)
        } else {
            htmlDocumentManager.appendHtml(htmlContent)
        }

        lastRole = MessageRole.SYSTEM
    }

    /**
     * Удаляет последнее системное сообщение
     */
    fun removeLastSystemMessage() {
        htmlDocumentManager.removeLastSystemMessage()
    }

    /**
     * Очищает все сообщения и переинициализирует документ
     */
    fun clearAllMessages() {
        htmlDocumentManager.initialize()
        lastRole = null
        codeBlockRegistry.clear()
    }

    /**
     * Перерисовывает все сообщения (например, при смене темы)
     */
    fun redrawMessages(messages: List<Message>) {
        clearAllMessages()

        if (messages.isNotEmpty()) {
            messages.forEach { displayMessage(it, addToHistory = false) }
        } else {
            displaySystemMessage(ChatPanelConfig.Messages.WELCOME)
        }
    }

    /**
     * Отображает сообщение пользователя
     */
    private fun displayUserMessage(message: Message) {
        val prefix = "${ChatPanelConfig.Icons.USER} ${ChatPanelConfig.Prefixes.USER}"
        val bodyHtml = contentRenderer.renderContentToHtml(message.content, isJcefMode())
        val isAfterSystem = lastRole == MessageRole.SYSTEM

        val messageHtml = contentRenderer.createMessageBlock(
            role = ChatPanelConfig.RoleClasses.USER,
            prefix = prefix,
            content = bodyHtml,
            isUser = true,
            isAfterSystem = isAfterSystem
        )

        htmlDocumentManager.appendHtml(messageHtml)
    }

    /**
     * Отображает сообщение ассистента
     */
    private fun displayAssistantMessage(message: Message) {
        val prefix = createAssistantPrefix(message)
        val bodyHtml = contentRenderer.renderContentToHtml(message.content, isJcefMode())
        val statusHtml = createAssistantStatusHtml(message)
        val isAfterSystem = lastRole == MessageRole.SYSTEM

        val messageHtml = contentRenderer.createMessageBlock(
            role = ChatPanelConfig.RoleClasses.ASSISTANT,
            prefix = prefix,
            content = bodyHtml,
            statusHtml = statusHtml,
            isUser = false,
            isAfterSystem = isAfterSystem
        )

        htmlDocumentManager.appendHtml(messageHtml)
    }

    /**
     * Создает префикс для сообщения ассистента с индикатором неопределенности
     */
    private fun createAssistantPrefix(message: Message): String {
        val isFinal = message.metadata["isFinal"] as? Boolean ?: true
        val uncertainty = message.metadata["uncertainty"] as? Double ?: 0.0
        val providerName = runCatching { service<ChatService>().getCurrentProviderName() }.getOrDefault("")

        val indicator = when {
            !isFinal -> ChatPanelConfig.Icons.QUESTION
            uncertainty > ChatPanelConfig.IS_FINAL_LEVEL -> ChatPanelConfig.Icons.WARNING
            else -> ChatPanelConfig.Icons.SUCCESS
        }

        val providerSuffix = if (settings.showProviderName && providerName.isNotBlank()) " ($providerName)" else ""
        return "$indicator ${ChatPanelConfig.Icons.ASSISTANT} ${ChatPanelConfig.Prefixes.ASSISTANT}$providerSuffix"
    }

    /**
     * Создает HTML для статусной строки сообщения ассистента
     */
    private fun createAssistantStatusHtml(message: Message): String {
        // Проверяем, включен ли анализ неопределенности
        val enableUncertaintyAnalysis = settings.enableUncertaintyAnalysis
        
        val isFinal = message.metadata["isFinal"] as? Boolean ?: true
        val uncertainty = message.metadata["uncertainty"] as? Double ?: 0.0
        val wasParsed = message.metadata["parsedData"] as? Boolean ?: false
        val hasClarifyingQuestions = message.metadata["hasClarifyingQuestions"] as? Boolean ?: false
        val responseTimeMs = message.metadata["responseTimeMs"] as? Long
        val tokensUsed = message.metadata["tokensUsed"] as? Int
        val tokenUsage = message.metadata["tokenUsage"] as? ru.marslab.ide.ride.model.TokenUsage

        return contentRenderer.createStatusHtml(
            isFinal = isFinal,
            uncertainty = uncertainty,
            wasParsed = wasParsed,
            hasClarifyingQuestions = hasClarifyingQuestions,
            responseTimeMs = responseTimeMs,
            tokensUsed = tokensUsed,
            showUncertaintyStatus = enableUncertaintyAnalysis,
            tokenUsage = tokenUsage
        )
    }

    /**
     * Проверяет, используется ли JCEF режим
     */
    private fun isJcefMode(): Boolean = jcefView != null

    /**
     * Регистрирует кодовый блок для возможности копирования
     */
    fun registerCodeBlock(code: String): String {
        if (codeBlockRegistry.size >= ChatPanelConfig.CODE_CACHE_LIMIT) {
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

    /**
     * Возвращает зарегистрированный кодовый блок по ключу
     */
    fun getCodeBlock(key: String): String? = codeBlockRegistry[key]

    /**
     * Обрабатывает клик по ссылке для копирования кода
     */
    fun handleCopyLink(linkDescription: String): Boolean {
        if (linkDescription.startsWith(ChatPanelConfig.COPY_LINK_PREFIX)) {
            val key = linkDescription.removePrefix(ChatPanelConfig.COPY_LINK_PREFIX)
            val code = getCodeBlock(key)
            if (code != null) {
                copyCodeToClipboard(code)
                return true
            }
        }
        return false
    }

    /**
     * Копирует код в буфер обмена
     */
    private fun copyCodeToClipboard(code: String) {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(java.awt.datatransfer.StringSelection(code), null)
    }
}