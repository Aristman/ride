package ru.marslab.ide.ride.ui.renderer

import ru.marslab.ide.ride.model.agent.AgentOutputType
import ru.marslab.ide.ride.model.agent.FormattedOutput
import ru.marslab.ide.ride.model.agent.FormattedOutputBlock
import ru.marslab.ide.ride.model.llm.TokenUsage
import ru.marslab.ide.ride.ui.config.ChatPanelConfig
import ru.marslab.ide.ride.ui.processor.MarkdownProcessor
import ru.marslab.ide.ride.ui.templates.*

/**
 * Единый рендерер контента чата, объединяющий обработку markdown и форматированного вывода агентов
 */
class ChatContentRenderer {

    private val markdownProcessor = MarkdownProcessor()

    // HTML шаблоны для разных типов блоков
    private val terminalTemplate = TerminalBlockTemplate()
    private val codeBlockTemplate = CodeBlockTemplate()
    private val toolResultTemplate = ToolResultTemplate()
    private val structuredBlockTemplate = StructuredBlockTemplate()
    private val interactionScriptsTemplate = InteractionScriptsTemplate()

    /**
     * Рендерит контент сообщения в HTML
     */
    fun renderContentToHtml(text: String, isJcefMode: Boolean): String {
        // Если контент уже HTML — только линкуем пути и возвращаем
        if (markdownProcessor.looksLikeHtml(text)) {
            return linkifyFilePaths(text)
        }

        var result = text

        // Экранируем HTML только для не-JCEF режима (до обработки markdown)
        if (!isJcefMode) {
            result = escapeHtml(result)
        }

        // Помечаем пути токенами, чтобы markdown не ломал их (подчёркивания/курсив и т.п.)
        result = markFilePaths(result)

        // Обрабатываем markdown-элементы
        result = markdownProcessor.processMarkdown(result, isJcefMode)

        // Заменяем токены на кликабельные элементы
        result = replaceFileTokensWithLinks(result)

        // Доп. обработка (fallback): попытаться линковать оставшиеся совпадения в чистых сегментах HTML
        result = linkifyFilePaths(result)

        // Fallback: если после всех преобразований контент стал пустым (возможные побочные эффекты markdown),
        // показываем безопасно экранированный исходный текст, чтобы пользователь не видел пустое сообщение
        if (result.isBlank()) {
            val safe = if (isJcefMode) escapeHtml(text) else escapeHtml(text)
            return "<span class=\"plain-text\">${'$'}safe</span>"
        }

        // Усиление: любые уже существующие <a href="path.ext"> помечаем internal-командой
        result = enhanceAnchorFileLinks(result)

        return result
    }

    /**
     * Рендерит форматированный вывод агентов в HTML
     */
    fun renderFormattedOutput(formattedOutput: FormattedOutput): String {
        return try {
            val sortedBlocks = formattedOutput.blocks.sortedBy { it.order }
            val htmlParts = mutableListOf<String>()

            htmlParts.add("<div class=\"agent-output-container\">")

            sortedBlocks.forEach { block ->
                val blockHtml = when (block.type) {
                    AgentOutputType.TERMINAL -> renderTerminalBlock(block)
                    AgentOutputType.CODE_BLOCKS -> renderCodeBlock(block)
                    AgentOutputType.TOOL_RESULT -> renderToolResultBlock(block)
                    AgentOutputType.MARKDOWN -> renderMarkdownBlock(block)
                    AgentOutputType.STRUCTURED -> renderStructuredBlock(block)
                    AgentOutputType.HTML -> renderHtmlBlock(block)
                }
                htmlParts.add(blockHtml)
            }

            htmlParts.add("</div>")
            htmlParts.joinToString("\n")

        } catch (e: Exception) {
            // Fallback на сырой контент в случае ошибки
            formattedOutput.rawContent ?: "<div class=\"error\">Error rendering formatted output</div>"
        }
    }

    /**
     * Находит уже сформированные markdown/HTML ссылки <a href="...">, которые указывают на локальные пути,
     * и дополняет их атрибутами data-open-command и onclick, чтобы клик обрабатывался нашим JS.
     */
    private fun enhanceAnchorFileLinks(html: String): String {
        if (html.isBlank()) return html
        // Поиск <a ... href="...ext" ...>...</a> где href без протокола и содержит допустимое расширение
        val anchorRegex = Regex(
            pattern = """
                (?is)
                <a\s+([^>]*?)href\s*=\s*"([^"]+)"([^>]*)>(.*?)</a>
            """.trimIndent()
        )
        return anchorRegex.replace(html) { mr ->
            val preAttrs = mr.groupValues[1]
            val href = mr.groupValues[2]
            val postAttrs = mr.groupValues[3]
            val inner = mr.groupValues[4]

            // Игнорируем внешние ссылки (http/https/mailto)
            val lowerHref = href.lowercase()
            val isExternal = lowerHref.startsWith("http:") || lowerHref.startsWith("https:") || lowerHref.startsWith("mailto:")
            if (isExternal) return@replace mr.value

            // Проверяем, что href выглядит как путь к файлу
            if (!filePathRegex.containsMatchIn(href)) return@replace mr.value

            val cmd = "open?path=${'$'}href&startLine=1&endLine=1"
            """
            <a ${preAttrs.trim()} href="#" data-open-command="${cmd}" data-tooltip="${href}" onclick="window.openSourceFile('${cmd}'); return false;" ${postAttrs.trim()}>${inner}</a>
            """.trimIndent()
        }
    }

    /**
     * Рендерит терминальный блок
     */
    private fun renderTerminalBlock(block: FormattedOutputBlock): String {
        val metadata = block.metadata
        val command = metadata["command"] as? String ?: ""
        val exitCode = metadata["exitCode"] as? Int ?: 0
        val executionTime = metadata["executionTime"] as? Long ?: 0L
        val success = metadata["success"] as? Boolean ?: true

        return terminalTemplate.render(
            command = command,
            exitCode = exitCode,
            executionTime = executionTime,
            success = success,
            content = block.content
        )
    }

    /**
     * Рендерит блок кода
     */
    private fun renderCodeBlock(block: FormattedOutputBlock): String {
        val language = block.metadata["language"] as? String ?: ""
        val fileName = block.metadata["fileName"] as? String

        return codeBlockTemplate.render(
            content = block.content,
            language = language,
            fileName = fileName
        )
    }

    /**
     * Рендерит блок результата инструмента
     */
    private fun renderToolResultBlock(block: FormattedOutputBlock): String {
        val toolName = block.metadata["toolName"] as? String ?: "Unknown Tool"
        val operationType = block.metadata["operationType"] as? String ?: ""
        val success = block.metadata["success"] as? Boolean ?: true

        return toolResultTemplate.render(
            content = block.content,
            toolName = toolName,
            operationType = operationType,
            success = success
        )
    }

    /**
     * Рендерит markdown блок
     */
    private fun renderMarkdownBlock(block: FormattedOutputBlock): String {
        return try {
            renderContentToHtml(block.content, true)
        } catch (e: Exception) {
            // Fallback если рендерер недоступен
            "<div class=\"markdown-block\"><div class=\"markdown-content\">${terminalTemplate.escapeHtml(block.content)}</div></div>"
        }
    }

    /**
     * Рендерит структурированный блок
     */
    private fun renderStructuredBlock(block: FormattedOutputBlock): String {
        val format = block.metadata["format"] as? String ?: "json"

        return structuredBlockTemplate.render(
            content = block.content,
            format = format
        )
    }

    /**
     * Рендерит HTML блок (без дополнительной обработки)
     */
    private fun renderHtmlBlock(block: FormattedOutputBlock): String {
        val cssClasses = block.cssClasses.joinToString(" ")
        return if (cssClasses.isNotEmpty()) {
            "<div class=\"$cssClasses\">${block.content}</div>"
        } else {
            block.content
        }
    }

    // =========================
    // File path auto-linking
    // =========================

    private val filePathRegex: Regex by lazy {
        Regex(
            pattern = """
                (?<![="'/])                    # не внутри атрибутов и не после слеша
                \b
                (?:[A-Za-z0-9_.-]+[/\\\\])* # ноль или более каталогов (/, \\)
                [A-Za-z0-9_.-]+                # имя файла
                \.
                (?:dart|kt|java|kts|md|markdown|yaml|yml|xml|gradle|rs|py|ts|tsx|js|jsx|go|rb|c|cpp|h|hpp|cs|json)
            """.trimIndent(),
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS)
        )
    }

    private fun markFilePaths(text: String): String {
        if (text.isBlank()) return text
        val sb = StringBuilder()
        var last = 0
        for (m in filePathRegex.findAll(text)) {
            val start = m.range.first
            val end = m.range.last + 1
            var path = m.value
            // Определяем начало текущей строки
            val lineStart = text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
            // Если строка начинается с '@' и сразу далее идёт путь — не маркируем (оставляем текст как есть)
            val isLeadingAt = lineStart == start - 1 && text.getOrNull(lineStart) == '@'

            sb.append(text, last, start)
            if (isLeadingAt) {
                // Просто вставляем исходный путь без токена
                sb.append(path)
            } else {
                // Снимаем завершающую пунктуацию, если она прилипла к пути в тексте
                val trailing = path.takeLast(1)
                if (trailing in listOf(".", ",", ";", ":", ")")) {
                    path = path.dropLast(1)
                    sb.append("<!--RIDEFILE:${path}-->")
                    sb.append(trailing)
                } else {
                    sb.append("<!--RIDEFILE:${path}-->")
                }
            }
            last = end
        }
        if (last < text.length) sb.append(text.substring(last))
        return sb.toString()
    }

    private fun replaceFileTokensWithLinks(html: String): String {
        if (html.isBlank()) return html
        val tokenRegex = Regex("<!--RIDEFILE:([^>]+?)-->")
        return tokenRegex.replace(html) { mr ->
            val raw = mr.groupValues[1]
            val path = raw.trim()
            val cmd = "open?path=${path}&startLine=1&endLine=1"
            """
            <a href="#"
               class="${ChatPanelConfig.CSS.SOURCE_LINK_ACTION}"
               data-open-command="${cmd}"
               data-tooltip="${path}"
               onclick="window.openSourceFile('${cmd}'); return false;">${path}</a>
            """.trimIndent()
        }
    }

    /**
     * Ищет в HTML строке упоминания путей к файлам и оборачивает их в кликабельный элемент,
     * который вызывает window.openSourceFile с командой open?path=...&startLine=1&endLine=1.
     * Содержимое внутри <code> и <pre> не изменяется.
     */
    private fun linkifyFilePaths(html: String): String {
        if (html.isBlank()) return html

        // Регулярное выражение для путей к файлам
        // Пример: lib/core/api/api_client.dart, src/main/kotlin/Foo.kt, README.md
        val filePattern = Regex(
            pattern = """
                (?<![="'/])                    # не внутри атрибутов и не после слеша
                \b
                (?:[A-Za-z0-9_.-]+/)*          # каталоги
                [A-Za-z0-9_.-]+                # имя файла
                \.
                (?:dart|kt|java|kts|md|markdown|yaml|yml|xml|gradle|rs|py|ts|tsx|js|jsx|go|rb|c|cpp|h|hpp|cs|json)
            """.trimIndent(),
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS)
        )

        // Не трогаем содержимое <code> и <pre>
        val codeTagRegex = Regex("(?is)<(code|pre)(?:\\b[^>]*)>.*?</\\1>")
        val sb = StringBuilder()
        var lastIndex = 0

        for (m in codeTagRegex.findAll(html)) {
            val before = html.substring(lastIndex, m.range.first)
            sb.append(linkifyPlainHtml(before, filePattern))
            sb.append(m.value) // оставляем код как есть
            lastIndex = m.range.last + 1
        }
        // Хвост после последнего <code>/<pre>
        if (lastIndex < html.length) {
            sb.append(linkifyPlainHtml(html.substring(lastIndex), filePattern))
        }

        return sb.toString()
    }

    /**
     * Заменяет все совпадения filePattern в plain HTML сегменте на кликабельные элементы.
     */
    private fun linkifyPlainHtml(segment: String, filePattern: Regex): String {
        if (segment.isEmpty()) return segment
        val sb = StringBuilder()
        var last = 0
        for (m in filePattern.findAll(segment)) {
            val start = m.range.first
            val end = m.range.last + 1
            var path = m.value
            // Проверяем, не начинается ли текущая строка с '@' перед путём
            val lineStart = segment.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
            val isLeadingAt = lineStart == start - 1 && segment.getOrNull(lineStart) == '@'

            sb.append(segment, last, start)
            if (isLeadingAt) {
                // Оставляем как есть — без линковки
                sb.append(path)
            } else {
                val trailing = path.takeLast(1)
                var suffix = ""
                if (trailing in listOf(".", ",", ";", ":", ")")) {
                    path = path.dropLast(1)
                    suffix = trailing
                }
                val cmd = "open?path=$path&startLine=1&endLine=1"
                sb.append(
                    """
                    <a href="#" class="${ChatPanelConfig.CSS.SOURCE_LINK_ACTION}" 
                       data-open-command="${cmd}" 
                       data-tooltip="${path}" 
                       onclick="window.openSourceFile('${cmd}'); return false;">${path}</a>${suffix}
                    """.trimIndent()
                )
            }
            last = end
        }
        if (last < segment.length) sb.append(segment.substring(last))
        return sb.toString()
    }

    /**
     * Рендерит несколько блоков с разделителями
     */
    fun renderMultipleBlocks(blocks: List<FormattedOutputBlock>): String {
        if (blocks.isEmpty()) return ""

        return buildString {
            appendLine("<div class=\"multi-block-container\">")

            blocks.sortedBy { it.order }.forEachIndexed { index, block ->
                appendLine("  <div class=\"block-item\" data-block-type=\"${block.type}\" data-order=\"${block.order}\">")

                val blockHtml = when (block.type) {
                    AgentOutputType.TERMINAL -> renderTerminalBlock(block)
                    AgentOutputType.CODE_BLOCKS -> renderCodeBlock(block)
                    AgentOutputType.TOOL_RESULT -> renderToolResultBlock(block)
                    AgentOutputType.MARKDOWN -> renderMarkdownBlock(block)
                    AgentOutputType.STRUCTURED -> renderStructuredBlock(block)
                    AgentOutputType.HTML -> renderHtmlBlock(block)
                }
                appendLine(blockHtml)

                appendLine("  </div>")

                // Добавляем разделитель между блоками (кроме последнего)
                if (index < blocks.size - 1) {
                    appendLine("  <div class=\"block-separator\"></div>")
                }
            }

            appendLine("</div>")
        }
    }

    /**
     * Создает JavaScript код для интерактивности элементов
     */
    fun createInteractionScripts(): String {
        return interactionScriptsTemplate.createScripts()
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