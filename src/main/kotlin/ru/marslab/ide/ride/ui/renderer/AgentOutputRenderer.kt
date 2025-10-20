package ru.marslab.ide.ride.ui.renderer

import ru.marslab.ide.ride.model.agent.AgentOutputType
import ru.marslab.ide.ride.model.agent.FormattedOutput
import ru.marslab.ide.ride.model.agent.FormattedOutputBlock
import ru.marslab.ide.ride.ui.templates.*
import ru.marslab.ide.ride.ui.renderer.ChatContentRenderer

/**
 * Рендерер для форматированного вывода агентов
 */
class AgentOutputRenderer {
    // Убираем зависимость от MessageDisplayManager для избежания циклической зависимости

    // HTML шаблоны для разных типов блоков
    private val terminalTemplate = TerminalBlockTemplate()
    private val codeBlockTemplate = CodeBlockTemplate()
    private val toolResultTemplate = ToolResultTemplate()
    private val structuredBlockTemplate = StructuredBlockTemplate()
    private val interactionScriptsTemplate = InteractionScriptsTemplate()

    /**
     * Рендерит форматированный вывод в HTML
     */
    fun render(formattedOutput: FormattedOutput): String {
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
        // Используем существующий ChatContentRenderer для markdown
        return try {
            val contentRenderer = ChatContentRenderer()
            contentRenderer.renderContentToHtml(block.content, true)
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
}