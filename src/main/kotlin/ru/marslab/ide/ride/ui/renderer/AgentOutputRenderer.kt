package ru.marslab.ide.ride.ui.renderer

import ru.marslab.ide.ride.model.agent.AgentOutputType
import ru.marslab.ide.ride.model.agent.FormattedOutput
import ru.marslab.ide.ride.model.agent.FormattedOutputBlock

/**
 * Рендерер для форматированного вывода агентов
 */
class AgentOutputRenderer {
    // Убираем зависимость от MessageDisplayManager для избежания циклической зависимости

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

        return buildString {
            appendLine("<div class=\"terminal-output\">")
            appendLine("  <div class=\"terminal-header\">")
            appendLine("    <div class=\"terminal-title\">")
            appendLine("      <span class=\"terminal-icon\">🖥️</span>")
            appendLine("      <span class=\"terminal-text\">Terminal Output</span>")
            appendLine("    </div>")
            appendLine("    <div class=\"terminal-status\">")
            if (success) {
                appendLine("      <span class=\"status-success\">✅ Success</span>")
            } else {
                appendLine("      <span class=\"status-error\">❌ Error</span>")
            }
            appendLine("    </div>")
            appendLine("  </div>")

            if (command.isNotEmpty()) {
                appendLine("  <div class=\"terminal-info\">")
                appendLine("    <div class=\"terminal-command\">")
                appendLine("      <span class=\"command-label\">Command:</span>")
                appendLine("      <span class=\"command-value\">${escapeHtml(command)}</span>")
                appendLine("    </div>")
                appendLine("    <div class=\"terminal-exit-code\">")
                appendLine("      <span class=\"exit-code-label\">Exit Code:</span>")
                appendLine("      <span class=\"exit-code-value\">$exitCode</span>")
                appendLine("    </div>")
                if (executionTime > 0) {
                    appendLine("    <div class=\"terminal-execution-time\">")
                    appendLine("      <span class=\"execution-time-label\">Execution Time:</span>")
                    appendLine("      <span class=\"execution-time-value\">${executionTime}ms</span>")
                    appendLine("    </div>")
                }
                appendLine("  </div>")
            }

            appendLine("  <div class=\"terminal-body\">")
            if (block.content.trim().isNotEmpty()) {
                appendLine("    <pre class=\"terminal-content\">${escapeHtml(block.content)}</pre>")
            } else {
                appendLine("    <pre class=\"terminal-content\">(No output)</pre>")
            }
            appendLine("  </div>")
            appendLine("</div>")
        }
    }

    /**
     * Рендерит блок кода
     */
    private fun renderCodeBlock(block: FormattedOutputBlock): String {
        val language = block.metadata["language"] as? String ?: ""
        val fileName = block.metadata["fileName"] as? String

        return buildString {
            appendLine("<div class=\"code-block-container\">")
            appendLine("  <div class=\"code-block-header\">")
            appendLine("    <div class=\"code-block-info\">")
            if (language.isNotEmpty()) {
                appendLine("      <span class=\"code-language\">$language</span>")
            } else {
                appendLine("      <span class=\"code-language\">text</span>")
            }
            if (fileName != null) {
                appendLine("      <span class=\"code-filename\">$fileName</span>")
            }
            appendLine("    </div>")
            appendLine("    <div class=\"code-block-actions\">")
            appendLine("      <button class=\"code-copy-btn\" onclick=\"copyCodeBlock(this)\" title=\"Copy code\">")
            appendLine("        <span class=\"copy-icon\">📋</span>")
            appendLine("        <span class=\"copy-text\">Copy</span>")
            appendLine("      </button>")
            appendLine("    </div>")
            appendLine("  </div>")
            appendLine("  <div class=\"code-block-body\">")
            appendLine("    <pre class=\"code-content\"><code class=\"language-$language\">${escapeHtml(block.content)}</code></pre>")
            appendLine("  </div>")
            appendLine("</div>")
        }
    }

    /**
     * Рендерит блок результата инструмента
     */
    private fun renderToolResultBlock(block: FormattedOutputBlock): String {
        val toolName = block.metadata["toolName"] as? String ?: "Unknown Tool"
        val operationType = block.metadata["operationType"] as? String ?: ""
        val success = block.metadata["success"] as? Boolean ?: true

        return buildString {
            appendLine("<div class=\"tool-result-block\">")
            appendLine("  <div class=\"tool-result-header\">")
            appendLine("    <div class=\"tool-info\">")
            appendLine("      <span class=\"tool-icon\">🔧</span>")
            appendLine("      <span class=\"tool-name\">$toolName</span>")
            if (operationType.isNotEmpty()) {
                appendLine("      <span class=\"operation-type\">$operationType</span>")
            }
            appendLine("    </div>")
            appendLine("    <div class=\"tool-status\">")
            if (success) {
                appendLine("      <span class=\"status-success\">✅ Успешно</span>")
            } else {
                appendLine("      <span class=\"status-error\">❌ Ошибка</span>")
            }
            appendLine("    </div>")
            appendLine("  </div>")

            if (block.content.trim().isNotEmpty()) {
                appendLine("  <div class=\"tool-result-content\">")
                appendLine("    <div class=\"result-value\">${escapeHtml(block.content)}</div>")
                appendLine("  </div>")
            }

            appendLine("</div>")
        }
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
            "<div class=\"markdown-block\"><div class=\"markdown-content\">${escapeHtml(block.content)}</div></div>"
        }
    }

    /**
     * Рендерит структурированный блок
     */
    private fun renderStructuredBlock(block: FormattedOutputBlock): String {
        val format = block.metadata["format"] as? String ?: "json"

        return buildString {
            appendLine("<div class=\"structured-block\">")
            appendLine("  <div class=\"structured-header\">")
            appendLine("    <span class=\"format-label\">$format</span>")
            appendLine("    <button class=\"toggle-structured\" onclick=\"toggleStructured(this)\">▼</button>")
            appendLine("  </div>")
            appendLine("  <div class=\"structured-content\">")
            appendLine("    <pre class=\"structured-data\"><code class=\"language-$format\">${escapeHtml(block.content)}</code></pre>")
            appendLine("  </div>")
            appendLine("</div>")
        }
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
     * Экранирует HTML-символы
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
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
        return """
            <script>
            function copyCodeBlock(button) {
                const container = button.closest('.code-block-container');
                const codeElement = container.querySelector('.code-content code');
                const text = codeElement.textContent;

                navigator.clipboard.writeText(text).then(() => {
                    const originalText = button.querySelector('.copy-text').textContent;
                    button.querySelector('.copy-text').textContent = 'Copied!';
                    button.style.background = 'var(--success-color, #4ade80)';

                    setTimeout(() => {
                        button.querySelector('.copy-text').textContent = originalText;
                        button.style.background = '';
                    }, 2000);
                }).catch(err => {
                    console.error('Failed to copy text: ', err);
                });
            }

            function toggleStructured(button) {
                const container = button.closest('.structured-block');
                const content = container.querySelector('.structured-content');
                const arrow = button.querySelector('.toggle-structured');

                if (content.style.display === 'none') {
                    content.style.display = 'block';
                    arrow.textContent = '▼';
                } else {
                    content.style.display = 'none';
                    arrow.textContent = '▶';
                }
            }

            function toggleMetadata(button) {
                const container = button.closest('.tool-metadata');
                const content = container.querySelector('.metadata-content');
                const arrow = button.querySelector('.metadata-arrow');

                if (content.style.display === 'none') {
                    content.style.display = 'block';
                    arrow.textContent = '▼';
                } else {
                    content.style.display = 'none';
                    arrow.textContent = '▶';
                }
            }

            function viewFullContent(button) {
                const container = button.closest('.tool-code-content');
                const preview = container.querySelector('.code-preview-body');
                const fullContent = container.querySelector('.full-code-content');

                if (fullContent) {
                    preview.style.display = 'none';
                    fullContent.style.display = 'block';
                    button.textContent = 'Hide';
                } else {
                    preview.style.display = 'block';
                    button.textContent = 'View Full';
                }
            }
            </script>
        """.trimIndent()
    }
}