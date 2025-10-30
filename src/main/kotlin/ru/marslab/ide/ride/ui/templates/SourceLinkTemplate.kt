package ru.marslab.ide.ride.ui.templates

import ru.marslab.ide.ride.model.rag.RagChunkWithSource
import ru.marslab.ide.ride.ui.config.ChatPanelConfig

/**
 * Шаблон для отображения source links в RAG ответах
 */
object SourceLinkTemplate {

    /**
     * Создает HTML для source links блока
     */
    fun createSourceLinksHtml(chunks: List<RagChunkWithSource>): String {
        if (chunks.isEmpty()) return ""

        val html = StringBuilder()
        html.appendLine("<div class=\"${ChatPanelConfig.CSS.SOURCE_LINKS_CONTAINER}\">")
        html.appendLine("<div class=\"${ChatPanelConfig.CSS.SOURCE_LINKS_HEADER}\">📎 Источники:</div>")

        chunks.forEachIndexed { index, chunk ->
            val fileName = extractFileName(chunk.source.path)
            val lineRange = if (chunk.source.startLine == chunk.source.endLine) {
                "строка ${chunk.source.startLine}"
            } else {
                "строки ${chunk.source.startLine}-${chunk.source.endLine}"
            }

            html.appendLine("<div class=\"${ChatPanelConfig.CSS.SOURCE_LINK_ITEM}\">")
            html.appendLine("<div class=\"${ChatPanelConfig.CSS.SOURCE_LINK_INDEX}\">${index + 1}.</div>")
            html.appendLine("<div class=\"${ChatPanelConfig.CSS.SOURCE_LINK_CONTENT}\">")
            html.appendLine("<div class=\"${ChatPanelConfig.CSS.SOURCE_LINK_FILE}\">$fileName</div>")
            html.appendLine("<div class=\"${ChatPanelConfig.CSS.SOURCE_LINK_LINES}\">$lineRange</div>")
            html.appendLine("<div class=\"${ChatPanelConfig.CSS.SOURCE_LINK_ACTION}\" onclick=\"openSourceFile('${chunk.openAction.command}')\" title=\"Открыть файл\">")
            html.appendLine("🔗 Открыть")
            html.appendLine("</div>")
            html.appendLine("</div>")
            html.appendLine("</div>")
        }

        html.appendLine("</div>")

        // Добавляем JavaScript для обработки кликов
        html.appendLine(createSourceLinksScript())

        return html.toString()
    }

    /**
     * Создает JavaScript код для обработки source links
     */
    private fun createSourceLinksScript(): String {
        return """
        <script>
        function openSourceFile(command) {
            // Отправляем команду в Java через JCEF bridge
            if (window.java) {
                window.java.openSourceFile(command);
            } else {
                console.log('Source link command:', command);
            }
        }
        </script>
        """.trimIndent()
    }

    /**
     * Извлекает имя файла из полного пути
     */
    private fun extractFileName(path: String): String {
        return path.substringAfterLast("/")
    }

    /**
     * Создает CSS стили для source links
     */
    fun createSourceLinksStyles(): String {
        return """
        .${ChatPanelConfig.CSS.SOURCE_LINKS_CONTAINER} {
            margin: 12px 0;
            padding: 12px;
            background: rgba(109, 143, 216, 0.1);
            border: 1px solid rgba(109, 143, 216, 0.3);
            border-radius: 8px;
            font-family: var(--font-family, ${ChatPanelConfig.Fonts.DEFAULT_FAMILY});
            font-size: var(--font-size, ${ChatPanelConfig.Fonts.DEFAULT_SIZE}px);
        }

        .${ChatPanelConfig.CSS.SOURCE_LINKS_HEADER} {
            font-weight: bold;
            color: var(--text-primary, #e6e6e6);
            margin-bottom: 8px;
            font-size: 12px;
        }

        .${ChatPanelConfig.CSS.SOURCE_LINK_ITEM} {
            display: flex;
            align-items: center;
            padding: 6px 0;
            border-bottom: 1px solid rgba(109, 143, 216, 0.1);
        }

        .${ChatPanelConfig.CSS.SOURCE_LINK_ITEM}:last-child {
            border-bottom: none;
        }

        .${ChatPanelConfig.CSS.SOURCE_LINK_INDEX} {
            color: var(--text-secondary, #9aa0a6);
            font-size: 11px;
            width: 20px;
            flex-shrink: 0;
        }

        .${ChatPanelConfig.CSS.SOURCE_LINK_CONTENT} {
            flex-grow: 1;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
        }

        .${ChatPanelConfig.CSS.SOURCE_LINK_FILE} {
            color: var(--text-primary, #e6e6e6);
            font-size: 11px;
            font-weight: 500;
        }

        .${ChatPanelConfig.CSS.SOURCE_LINK_LINES} {
            color: var(--text-secondary, #9aa0a6);
            font-size: 10px;
        }

        .${ChatPanelConfig.CSS.SOURCE_LINK_ACTION} {
            color: #6d8fd8;
            font-size: 11px;
            cursor: pointer;
            padding: 2px 6px;
            border-radius: 4px;
            text-decoration: none;
            white-space: nowrap;
            transition: background-color 0.2s ease;
        }

        .${ChatPanelConfig.CSS.SOURCE_LINK_ACTION}:hover {
            background-color: rgba(109, 143, 216, 0.2);
        }
        """.trimIndent()
    }
}