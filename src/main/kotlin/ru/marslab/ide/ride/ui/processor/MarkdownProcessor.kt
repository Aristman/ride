package ru.marslab.ide.ride.ui.processor

/**
 * Обработчик Markdown для конвертации в HTML
 */
class MarkdownProcessor {

    /**
     * Конвертирует Markdown текст в HTML
     */
    fun processMarkdown(text: String, isJcefMode: Boolean): String {
        var result = text

        // Обрабатываем различные элементы markdown в правильном порядке

        // 1. Обработка заголовков (начала строки)
        result = processHeaders(result)

        // 2. Обработка жирного текста **text**
        result = processBoldText(result)

        // 3. Обработка курсива _text_ и *text*
        result = processItalicText(result)

        // 4. Обработка горизонтальной черты
        result = processHorizontalRule(result)

        // 5. Обработка списков
        result = processLists(result, isJcefMode)

        // 6. Обработка инлайн кода `code`
        result = processInlineCode(result)

        return result
    }

    /**
     * Обрабатывает заголовки #, ##, ###
     */
    private fun processHeaders(text: String): String {
        var result = text

        // Заголовки уровня 3
        result = result.replace(Regex("""^### (.*?)$""", RegexOption.MULTILINE)) { match ->
            "<h3>${match.groupValues[1]}</h3>"
        }

        // Заголовки уровня 2
        result = result.replace(Regex("""^## (.*?)$""", RegexOption.MULTILINE)) { match ->
            "<h2>${match.groupValues[1]}</h2>"
        }

        // Заголовки уровня 1
        result = result.replace(Regex("""^# (.*?)$""", RegexOption.MULTILINE)) { match ->
            "<h1>${match.groupValues[1]}</h1>"
        }

        return result
    }

    /**
     * Обрабатывает жирный текст **text**
     */
    private fun processBoldText(text: String): String {
        return text.replace(Regex("""\*\*(.*?)\*\*""")) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }
    }

    /**
     * Обрабатывает курсивный текст *text* и _text_
     */
    private fun processItalicText(text: String): String {
        var result = text

        // Курсив _текст_
        result = result.replace(Regex("""_(.*?)_""")) { match ->
            "<em>${match.groupValues[1]}</em>"
        }

        // Курсив *текст* (только если не жирный)
        result = result.replace(Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")) { match ->
            "<em>${match.groupValues[1]}</em>"
        }

        return result
    }

    /**
     * Обрабатывает горизонтальную черту ---
     */
    private fun processHorizontalRule(text: String): String {
        return text.replace(Regex("""^---$""", RegexOption.MULTILINE), "<hr/>")
    }

    /**
     * Обрабатывает списки (нумерованные и маркированные)
     */
    private fun processLists(text: String, isJcefMode: Boolean): String {
        val lines = text.split("\n").toMutableList()
        var inList = false
        var listType = "" // "ul" или "ol"
        val result = StringBuilder()
        var currentListContent = StringBuilder()

        for (i in lines.indices) {
            val line = lines[i].trim()

            // Нумерованный список
            if (Regex("""^\d+\.\s+.*""").matches(line)) {
                if (!inList || listType != "ol") {
                    if (inList) {
                        result.append(currentListContent).append("</$listType>")
                        currentListContent.clear()
                    }
                    result.append("<ol>")
                    inList = true
                    listType = "ol"
                }
                val content = line.replace(Regex("""^\d+\.\s+"""), "")
                currentListContent.append("<li>$content</li>")
                continue
            }

            // Маркированный список
            if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList || listType != "ul") {
                    if (inList) {
                        result.append(currentListContent).append("</$listType>")
                        currentListContent.clear()
                    }
                    result.append("<ul>")
                    inList = true
                    listType = "ul"
                }
                val content = line.replace(Regex("""^[-*]\s+"""), "")
                currentListContent.append("<li>$content</li>")
                continue
            }

            // Закрываем список если текущая строка не элемент списка
            if (inList && !line.startsWith("- ") && !line.startsWith("* ") && !Regex("""^\d+\.\s+.*""").matches(line)) {
                result.append(currentListContent).append("</$listType>")
                currentListContent.clear()
                inList = false
                listType = ""
            }

            // Добавляем обычный текст с переносами строк
            if (line.isNotBlank()) {
                result.append(line)
                // Добавляем перенос строки для следующей строки
                if (i < lines.size - 1) {
                    val nextLine = lines[i + 1].trim()
                    // Если следующая строка - не элемент списка, добавляем перенос
                    if (!nextLine.startsWith("- ") && !nextLine.startsWith("* ") && !Regex("""^\d+\.\s+.*""").matches(
                            nextLine
                        )
                    ) {
                        result.append(if (isJcefMode) "<br/>" else "\n")
                    }
                }
            } else if (!inList) {
                // Пустая строка между абзацами (только если не в списке)
                result.append(if (isJcefMode) "<br/>" else "\n")
            }
        }

        // Закрываем список в конце текста
        if (inList) {
            result.append(currentListContent).append("</$listType>")
        }

        return result.toString()
    }

    /**
     * Обрабатывает инлайн код `code`
     */
    private fun processInlineCode(text: String): String {
        return text.replace(Regex("""`([^`]+)`""")) { match ->
            "<code>${match.groupValues[1]}</code>"
        }
    }

    /**
     * Проверяет, похож ли текст на уже отформатированный HTML
     */
    fun looksLikeHtml(text: String): Boolean {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("<")) return false

        // Ищем распространённые блочные теги
        val htmlTagPattern = Regex("(?is)<\\s*(p|ol|ul|li|h[1-6]|pre|code|table|thead|tbody|tr|td|th|div|span)\\b")
        return htmlTagPattern.containsMatchIn(trimmed)
    }
}