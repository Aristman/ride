package ru.marslab.ide.ride.ui.renderer

import ru.marslab.ide.ride.model.llm.TokenUsage
import ru.marslab.ide.ride.ui.config.ChatPanelConfig
import ru.marslab.ide.ride.ui.processor.CodeBlockProcessor
import ru.marslab.ide.ride.ui.processor.MarkdownProcessor

/**
 * Основной рендерер контента чата, объединяющий обработку кодовых блоков и markdown
 */
class ChatContentRenderer {

    private val codeBlockProcessor = CodeBlockProcessor()
    private val markdownProcessor = MarkdownProcessor()

    /**
     * Рендерит контент сообщения в HTML
     */
    fun renderContentToHtml(text: String, isJcefMode: Boolean): String {
        println("DEBUG: renderContentToHtml called with text length: ${text.length}")
        println("DEBUG: Input text preview: ${text.take(200)}...")
        println("DEBUG: JCEF mode: $isJcefMode")

        var result = text

        // Если контент уже представляет собой HTML - вставляем как есть
        if (markdownProcessor.looksLikeHtml(result)) {
            println("DEBUG: Detected preformatted HTML content, bypassing markdown/escape pipeline")
            return result
        }

        // 1. Обрабатываем тройные обратные кавычки (```code```) - единственный способ создать блок кода
        val tripleBacktickResult = codeBlockProcessor.processTripleBackticks(result, isJcefMode)
        result = tripleBacktickResult.processedText

        if (tripleBacktickResult.codeBlocksFound.isNotEmpty()) {
            println(
                "DEBUG: Found ${tripleBacktickResult.codeBlocksFound.size} triple backtick code blocks: ${
                    tripleBacktickResult.codeBlocksFound.joinToString(
                        ", "
                    )
                }"
            )
        }

        // 2. Экранируем HTML только для не-JCEF режима (до обработки markdown)
        if (!isJcefMode) {
            result = escapeHtml(result)
        }

        // 3. Обрабатываем markdown-элементы (включая инлайн-код `text`)
        result = markdownProcessor.processMarkdown(result, isJcefMode)

        println("DEBUG: Final rendered HTML preview: ${result.take(300)}...")

        return result
    }

    /**
     * Создает HTML для статусной строки сообщения ассистента
     */
    fun createStatusHtml(
        isFinal: Boolean,
        uncertainty: Double,
        wasParsed: Boolean,
        hasClarifyingQuestions: Boolean,
        responseTimeMs: Long? = null,
        tokensUsed: Int? = null,
        showUncertaintyStatus: Boolean = true,
        tokenUsage: TokenUsage? = null
    ): String {
        // Формируем метрики
        val metricsHtml = buildString {
            if (responseTimeMs != null || tokensUsed != null || tokenUsage != null) {
                append("<span class='metrics'>")
                if (responseTimeMs != null) {
                    val timeSeconds = responseTimeMs / 1000.0
                    append("⏱️ ${String.format("%.2f", timeSeconds)}s")
                }

                // Показываем детальную статистику токенов если доступна
                if (tokenUsage != null && tokenUsage.totalTokens > 0) {
                    if (responseTimeMs != null) append(" | ")
                    append("🔢 ${tokenUsage.totalTokens} токенов")
                    if (tokenUsage.inputTokens > 0 || tokenUsage.outputTokens > 0) {
                        append(" <span style='opacity: 0.7; font-size: 0.9em;'>(")
                        append("↑${tokenUsage.inputTokens} ↓${tokenUsage.outputTokens}")
                        append(")</span>")
                    }
                } else if (tokensUsed != null && tokensUsed > 0) {
                    // Fallback на старый формат
                    if (responseTimeMs != null) append(" | ")
                    append("🔢 ${tokensUsed} токенов")
                }
                append("</span>")
            }
        }

        // Если анализ неопределенности выключен, показываем только метрики с пробелом для сохранения высоты
        if (!showUncertaintyStatus) {
            return "<div class='status status-final'>&nbsp;$metricsHtml</div>"
        }

        // Иначе показываем полный статус с неопределенностью
        val actualUncertainty = uncertainty
        val uncertaintyPercent = (actualUncertainty * 100).toInt()

        val statusText = when {
            !isFinal || hasClarifyingQuestions -> {
                "${ChatPanelConfig.StatusTexts.REQUIRE_CLARIFICATION} ${
                    ChatPanelConfig.StatusTexts.UNCERTAINTY_TEMPLATE.format(
                        uncertaintyPercent
                    )
                }"
            }

            !wasParsed && actualUncertainty > ChatPanelConfig.IS_FINAL_LEVEL -> {
                "${ChatPanelConfig.StatusTexts.ANSWER_WITH_PARSING} ${
                    ChatPanelConfig.StatusTexts.UNCERTAINTY_TEMPLATE.format(
                        uncertaintyPercent
                    )
                }"
            }

            actualUncertainty > ChatPanelConfig.IS_FINAL_LEVEL -> {
                "${ChatPanelConfig.StatusTexts.LOW_CONFIDENCE_ANSWER} ${
                    ChatPanelConfig.StatusTexts.UNCERTAINTY_TEMPLATE.format(
                        uncertaintyPercent
                    )
                }"
            }

            else -> {
                ChatPanelConfig.StatusTexts.FINAL_ANSWER
            }
        }

        val statusClass = when {
            !isFinal || hasClarifyingQuestions -> ChatPanelConfig.StatusClasses.UNCERTAIN
            !wasParsed -> ChatPanelConfig.StatusClasses.LOW_CONFIDENCE
            actualUncertainty > ChatPanelConfig.IS_FINAL_LEVEL -> ChatPanelConfig.StatusClasses.LOW_CONFIDENCE
            else -> ChatPanelConfig.StatusClasses.FINAL
        }

        // Добавляем иконку в зависимости от статуса парсинга
        val icon = if (!wasParsed) ChatPanelConfig.Icons.WARNING else ChatPanelConfig.Icons.COPY

        return "<div class='status $statusClass'>$icon $statusText $metricsHtml</div>"
    }

    /**
     * Создает HTML для префикса сообщения
     */
    fun createPrefixHtml(role: String, prefix: String, isUser: Boolean): String {
        val escapedPrefix = escapeHtml(prefix)
        return if (isUser) {
            "<div class='prefix' align='right'><b>$escapedPrefix</b>:</div>"
        } else {
            "<div class='prefix'><b>$escapedPrefix</b>:</div>"
        }
    }

    /**
     * Создает HTML для контента сообщения
     */
    fun createContentHtml(content: String, isUser: Boolean): String {
        return if (isUser) {
            "<div class='content' align='right'>$content</div>"
        } else {
            "<div class='content'>$content</div>"
        }
    }

    /**
     * Создает полный HTML блок сообщения
     */
    fun createMessageBlock(
        role: String,
        prefix: String,
        content: String,
        statusHtml: String = "",
        isUser: Boolean = false,
        isAfterSystem: Boolean = false
    ): String {
        val roleClass = role
        val afterSystemClass = if (isAfterSystem) " ${ChatPanelConfig.RoleClasses.AFTER_SYSTEM}" else ""

        val prefixDiv = createPrefixHtml(role, prefix, isUser)
        val contentDiv = createContentHtml(content, isUser)

        return "<div class='msg $roleClass$afterSystemClass'>$prefixDiv$contentDiv$statusHtml</div>"
    }

    /**
     * Создает HTML для системного сообщения
     */
    fun createSystemMessageHtml(text: String, isLoading: Boolean = false): String {
        val content = escapeHtml(text).replace("\n", "<br/>")
        val marker = if (isLoading) "<!--LOADING_MARKER-->" else ""

        // Простое отображение без префикса, только серый текст
        return "<div class='msg ${ChatPanelConfig.RoleClasses.SYSTEM}'>$marker<div class='system-content'>$content</div></div>"
    }

    /**
     * Экранирует HTML символы
     */
    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}