package ru.marslab.ide.ride.ui.chat

import com.intellij.openapi.diagnostic.thisLogger
import ru.marslab.ide.ride.ui.style.CommonStyles

/**
 * Управление HTML-ресурсами чата
 */
internal object ChatHtmlResources {
    private val logger = thisLogger()

    /**
     * Загружает CSS стили из ресурсов
     */
    fun loadCss(): String {
        return CommonStyles.getJcefStyles()
    }

    /**
     * Загружает JavaScript код из ресурсов
     */
    fun loadJs(): String {
        return this::class.java.getResourceAsStream("/chat/chat.js")
            ?.use { it.bufferedReader().readText() }
            ?: error("Не удалось загрузить chat.js")
    }

    /**
     * Создает полный HTML с инжектированными стилями и скриптами
     * Используется когда JCEF не может загрузить внешние файлы ресурсов
     */
    fun createInlineHtml(): String {
        val css = loadCss()
        val js = loadJs()

        return """
            <!DOCTYPE html>
            <html>
                <head>
                    <meta charset='utf-8'>
                    <style>${css}</style>
                </head>
                <body>
                    <div id="messages"></div>
                    <!-- Overlay root for popovers/menus (e.g., @-picker) -->
                    <div id="ride-overlay-root"></div>
                    <!-- Chat input bar -->
                    <div id="ride-input-bar" style="position: fixed; left: 0; right: 0; bottom: 0; padding: 8px; background: var(--ride-bg, #1e1f22); border-top: 1px solid var(--ride-border, #2b2d30);">
                        <input id="ride-chat-input" type="text" placeholder="Type a message…" 
                               style="width: 100%; box-sizing: border-box; padding: 8px 10px; border-radius: 6px; border: 1px solid var(--ride-border, #2b2d30); background: var(--ride-input-bg, #2b2d30); color: var(--ride-fg, #ddd); outline: none;" />
                    </div>
                    <script>${js}</script>
                </body>
            </html>
        """.trimIndent()
    }
}