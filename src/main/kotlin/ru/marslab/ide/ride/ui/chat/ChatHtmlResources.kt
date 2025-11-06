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
                    <!-- Overlay root for popovers/menus (e.g., @-picker). Input поле уже существует в основном UI и доступно по селектору #ride-chat-input -->
                    <div id="ride-overlay-root"></div>
                    <script>${js}</script>
                </body>
            </html>
        """.trimIndent()
    }
}