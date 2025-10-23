package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import ru.marslab.ide.ride.ui.ChatPanel

/**
 * Перезагрузка текущей сессии чата (обновление сообщений).
 */
class ReloadSessionAction :
    AnAction("Reload", "Reload current chat session", com.intellij.icons.AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
        val component = e.getData(com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT)
        val panel = component?.let { findChatPanel(it) }
        if (panel != null) {
            // Перезагружаем текущую сессию (обновляем сообщения)
            panel.refreshAppearance()
        } else {
            // Если панель не найдена, ничего не делаем
        }
    }

    private fun findChatPanel(component: java.awt.Component): ChatPanel? {
        var c: java.awt.Component? = component
        while (c != null) {
            if (c is ChatPanel) return c
            c = c.parent
        }
        return null
    }
}