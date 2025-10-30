package ru.marslab.ide.ride.ui.bridge

import com.intellij.openapi.diagnostic.Logger

/**
 * Исторический мост для source links. В текущей версии заменен на JBCefJSQuery
 * в `JcefChatView`. Держим файл как no-op для обратной совместимости и чтобы
 * не ломать сборку, если где-то остались ссылки на тип.
 */
class SourceLinkBridge {
    private val logger = Logger.getInstance(SourceLinkBridge::class.java)

    /**
     * Возвращает комментарий о том, что мост отключен (для дебага).
     */
    fun info(): String = "SourceLinkBridge is disabled; use JBCefJSQuery in JcefChatView"
}