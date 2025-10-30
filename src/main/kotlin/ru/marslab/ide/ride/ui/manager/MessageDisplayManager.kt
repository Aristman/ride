package ru.marslab.ide.ride.ui.manager

import com.intellij.openapi.components.service
import ru.marslab.ide.ride.model.agent.FormattedOutput
import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.model.chat.MessageRole
import ru.marslab.ide.ride.model.llm.TokenUsage
import ru.marslab.ide.ride.service.ChatService
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.ui.config.ChatPanelConfig
import ru.marslab.ide.ride.ui.renderer.ChatContentRenderer
import ru.marslab.ide.ride.ui.templates.SourceLinkTemplate
import ru.marslab.ide.ride.service.rag.RagSourceLinkService
import java.util.*

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

        // Определяем тип сообщения из metadata
        val messageType = message.metadata["type"] as? String
        val isProgress = message.metadata["isProgress"] as? Boolean ?: false

        // Выбираем способ рендеринга в зависимости от типа сообщения
        val bodyHtml = when {
            // Сообщения прогресса tool agents уже содержат готовый HTML
            messageType == "tool_agent_progress" || isProgress -> {
                message.content
            }
            // Проверяем наличие форматированного вывода
            message.metadata["formattedOutput"] is FormattedOutput -> {
                val formattedOutput = message.metadata["formattedOutput"] as FormattedOutput
                try {
                    contentRenderer.renderFormattedOutput(formattedOutput)
                } catch (e: Exception) {
                    // Fallback на стандартный рендеринг в случае ошибки
                    contentRenderer.renderContentToHtml(message.content, isJcefMode())
                }
            }
            // Стандартный рендеринг для обычных сообщений
            else -> {
                contentRenderer.renderContentToHtml(message.content, isJcefMode())
            }
        }

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

        // Добавляем source links если они есть
        val sourceLinksHtml = createSourceLinksHtml(message)
        if (sourceLinksHtml.isNotEmpty()) {
            htmlDocumentManager.appendHtml(sourceLinksHtml)
        }
    }

    /**
     * Создает HTML для source links если они есть в метаданных
     */
    private fun createSourceLinksHtml(message: Message): String {
        val sourceLinkService = RagSourceLinkService.getInstance()
        if (!sourceLinkService.isSourceLinksEnabled()) {
            return ""
        }

        // Проверяем наличие RAG метаданных с source links
        val ragSourceLinksEnabled = message.metadata["ragSourceLinksEnabled"] as? Boolean ?: false
        if (!ragSourceLinksEnabled) {
            return ""
        }

        val ragSourceLinksChunks = message.metadata["ragSourceLinksChunks"] as? List<*>
        if (ragSourceLinksChunks.isNullOrEmpty()) {
            return ""
        }

        try {
            // Конвертируем в List<RagChunkWithSource>
            val chunksWithSources = ragSourceLinksChunks.mapNotNull { chunk ->
                // Проверяем, что объект имеет нужные поля
                if (chunk is ru.marslab.ide.ride.model.rag.RagChunkWithSource) {
                    chunk
                } else null
            }

            if (chunksWithSources.isEmpty()) {
                return ""
            }

            // Создаем HTML для source links
            return SourceLinkTemplate.createSourceLinksHtml(chunksWithSources)
        } catch (e: Exception) {
            // В случае ошибки логируем и возвращаем пустую строку
            return ""
        }
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
        val tokenUsage = message.metadata["tokenUsage"] as? TokenUsage

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