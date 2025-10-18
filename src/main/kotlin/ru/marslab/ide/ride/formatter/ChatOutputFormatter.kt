package ru.marslab.ide.ride.formatter

import ru.marslab.ide.ride.model.agent.AgentOutputType
import ru.marslab.ide.ride.model.agent.FormattedOutput
import ru.marslab.ide.ride.model.agent.FormattedOutputBlock

/**
 * Форматтер для вывода чата с множественными блоками
 */
class ChatOutputFormatter {

    /**
     * Форматирует markdown-контент в FormattedOutput с множественными блоками
     */
    fun formatAsHtml(content: String): FormattedOutput {
        val blocks = extractBlocks(content)
        return FormattedOutput.multiple(blocks)
    }

    /**
     * Извлекает различные типы блоков из markdown-контента
     */
    fun extractBlocks(markdown: String): List<FormattedOutputBlock> {
        val blocks = mutableListOf<FormattedOutputBlock>()
        var order = 0

        // Регулярные выражения для разных типов блоков
        val codeBlockRegex = """```(\w+)?\s*\n([\s\S]*?)\n```""".toRegex()
        val inlineCodeRegex = """`([^`]+)`""".toRegex()
        val headerRegex = """^(#{1,6})\s+(.+)$""".toRegex(RegexOption.MULTILINE)
        val listRegex = """^(\s*)([-*+]|\d+\.)\s+(.+)$""".toRegex(RegexOption.MULTILINE)

        // Разделяем контент на блоки кода и текст
        var lastIndex = 0
        codeBlockRegex.findAll(markdown).forEach { match ->
            // Добавляем текст до блока кода
            val beforeText = markdown.substring(lastIndex, match.range.first)
            if (beforeText.trim().isNotEmpty()) {
                val textBlocks = extractTextBlocks(beforeText, order)
                blocks.addAll(textBlocks)
                order += textBlocks.size
            }

            // Добавляем блок кода
            val language = match.groupValues[1].ifEmpty { "text" }
            val code = match.groupValues[2].trimEnd()

            blocks.add(FormattedOutputBlock.codeBlock(
                content = code,
                language = language,
                order = order++
            ))

            lastIndex = match.range.last + 1
        }

        // Добавляем оставшийся текст
        val afterText = markdown.substring(lastIndex)
        if (afterText.trim().isNotEmpty()) {
            val textBlocks = extractTextBlocks(afterText, order)
            blocks.addAll(textBlocks)
        }

        // Если блоки не найдены, создаем один markdown блок
        if (blocks.isEmpty()) {
            blocks.add(FormattedOutputBlock.markdown(markdown, 0))
        }

        return blocks
    }

    /**
     * Извлекает текстовые блоки (заголовки, списки, параграфы)
     */
    private fun extractTextBlocks(text: String, startOrder: Int): List<FormattedOutputBlock> {
        val blocks = mutableListOf<FormattedOutputBlock>()
        val lines = text.split("\n")
        var currentBlock = StringBuilder()
        var currentType = "paragraph"
        var order = startOrder

        for (line in lines) {
            val trimmedLine = line.trim()

            // Проверяем на заголовок
            if (trimmedLine.matches(Regex("^#{1,6}\\s+.+$"))) {
                // Сохраняем предыдущий блок
                if (currentBlock.isNotEmpty()) {
                    blocks.add(createTextBlock(currentBlock.toString(), currentType, order++))
                    currentBlock.clear()
                }
                currentBlock.append(line)
                currentType = "header"
            }
            // Проверяем на список
            else if (trimmedLine.matches(Regex("^[-*+]\\s+.+$")) || trimmedLine.matches(Regex("^\\d+\\.\\s+.+$"))) {
                if (currentBlock.isNotEmpty() && currentType != "list") {
                    blocks.add(createTextBlock(currentBlock.toString(), currentType, order++))
                    currentBlock.clear()
                }
                currentBlock.append(line).append("\n")
                currentType = "list"
            }
            // Пустая строка - разделитель блоков
            else if (trimmedLine.isEmpty()) {
                if (currentBlock.isNotEmpty()) {
                    blocks.add(createTextBlock(currentBlock.toString(), currentType, order++))
                    currentBlock.clear()
                }
                currentType = "paragraph"
            }
            // Обычный текст
            else {
                if (currentBlock.isNotEmpty() && currentType != "paragraph" && currentType != "list") {
                    blocks.add(createTextBlock(currentBlock.toString(), currentType, order++))
                    currentBlock.clear()
                }
                if (currentType == "list" || currentBlock.isNotEmpty()) {
                    currentBlock.append("\n")
                }
                currentBlock.append(line)
                currentType = "paragraph"
            }
        }

        // Сохраняем последний блок
        if (currentBlock.isNotEmpty()) {
            blocks.add(createTextBlock(currentBlock.toString(), currentType, order))
        }

        return blocks
    }

    /**
     * Создает текстовый блок определенного типа
     */
    private fun createTextBlock(content: String, type: String, order: Int): FormattedOutputBlock {
        val metadata = mutableMapOf<String, Any>()
        metadata["blockType"] = type

        val cssClasses = mutableListOf<String>()
        cssClasses.add("text-block")
        cssClasses.add("$type-block")

        return FormattedOutputBlock(
            type = AgentOutputType.MARKDOWN,
            content = content.trim(),
            cssClasses = cssClasses,
            metadata = metadata,
            order = order
        )
    }

    /**
     * Создает текстовый блок
     */
    fun createTextBlock(text: String, order: Int = 0): FormattedOutputBlock {
        return FormattedOutputBlock.markdown(text, order)
    }

    /**
     * Создает блок кода
     */
    fun createCodeBlock(code: String, language: String = "", order: Int = 0): FormattedOutputBlock {
        return FormattedOutputBlock.codeBlock(code, language, order)
    }

    /**
     * Создает блок списка
     */
    fun createListBlock(items: List<String>, ordered: Boolean = false, order: Int = 0): FormattedOutputBlock {
        val content = buildString {
            if (ordered) {
                items.forEachIndexed { index, item ->
                    appendLine("${index + 1}. $item")
                }
            } else {
                items.forEach { item ->
                    appendLine("- $item")
                }
            }
        }

        val metadata = mapOf(
            "blockType" to "list",
            "ordered" to ordered,
            "itemCount" to items.size
        )

        return FormattedOutputBlock(
            type = AgentOutputType.MARKDOWN,
            content = content,
            cssClasses = listOf("text-block", "list-block"),
            metadata = metadata,
            order = order
        )
    }

    /**
     * Создает блок заголовка
     */
    fun createHeaderBlock(text: String, level: Int = 1, order: Int = 0): FormattedOutputBlock {
        val headerPrefix = "#".repeat(level.coerceIn(1, 6))
        val content = "$headerPrefix $text"

        val metadata = mapOf(
            "blockType" to "header",
            "level" to level
        )

        return FormattedOutputBlock(
            type = AgentOutputType.MARKDOWN,
            content = content,
            cssClasses = listOf("text-block", "header-block", "h$level"),
            metadata = metadata,
            order = order
        )
    }

    /**
     * Обрабатывает специальные случаи множественных блоков кода
     */
    fun processMultipleCodeBlocks(content: String): FormattedOutput {
        val blocks = mutableListOf<FormattedOutputBlock>()
        var order = 0

        // Ищем множественные блоки кода с разными языками
        val codeBlockRegex = """```(\w+)?\s*\n([\s\S]*?)\n```""".toRegex()
        val codeBlocks = codeBlockRegex.findAll(content).toList()

        if (codeBlocks.size > 1) {
            // Множественные блоки кода - создаем контейнер
            var lastIndex = 0

            codeBlocks.forEachIndexed { index, match ->
                // Текст между блоками
                val beforeText = content.substring(lastIndex, match.range.first).trim()
                if (beforeText.isNotEmpty()) {
                    blocks.add(createTextBlock(beforeText, order++))
                }

                // Блок кода
                val language = match.groupValues[1].ifEmpty { "text" }
                val code = match.groupValues[2].trimEnd()

                blocks.add(createCodeBlock(code, language, order++))
                lastIndex = match.range.last + 1
            }

            // Оставшийся текст
            val afterText = content.substring(lastIndex).trim()
            if (afterText.isNotEmpty()) {
                blocks.add(createTextBlock(afterText, order))
            }
        } else {
            // Один блок кода или без блоков
            blocks.addAll(extractBlocks(content))
        }

        return FormattedOutput.multiple(blocks)
    }

    /**
     * Создает блок для смешанного контента (текст + код + списки)
     */
    fun createMixedContentBlock(textParts: List<String>, codeBlocks: List<Pair<String, String>>): FormattedOutput {
        val blocks = mutableListOf<FormattedOutputBlock>()
        var order = 0

        // Чередуем текстовые части и блоки кода
        val allParts = mutableListOf<Pair<String, String>>() // (type, content)

        // Добавляем текстовые части
        textParts.forEach { text ->
            if (text.trim().isNotEmpty()) {
                allParts.add("text" to text)
            }
        }

        // Добавляем блоки кода
        codeBlocks.forEach { (language, code) ->
            allParts.add("code" to "$language\n$code")
        }

        // Создаем блоки
        allParts.forEach { (type, content) ->
            when (type) {
                "code" -> {
                    val parts = content.split("\n", limit = 2)
                    val language = parts[0]
                    val code = if (parts.size > 1) parts[1] else ""
                    blocks.add(createCodeBlock(code, language, order++))
                }
                "text" -> {
                    blocks.add(createTextBlock(content, order++))
                }
            }
        }

        return if (blocks.size == 1) {
            FormattedOutput.single(blocks.first())
        } else {
            FormattedOutput.multiple(blocks)
        }
    }

    /**
     * Создает простой текстовый формат (fallback)
     */
    fun formatAsText(content: String): FormattedOutput {
        return FormattedOutput.markdown(content)
    }
}