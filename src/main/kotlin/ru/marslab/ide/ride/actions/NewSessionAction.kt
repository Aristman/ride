package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import ru.marslab.ide.ride.ui.ChatPanel
import ru.marslab.ide.ride.service.ChatService
import com.intellij.openapi.components.service

/**
 * Очистка текущей сессии чата в активной панели.
 */
class NewSessionAction : AnAction("New Session", "Start a new chat session", com.intellij.icons.AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
        val component = e.getData(com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT)
        val panel = component?.let { findChatPanel(it) }
        if (panel != null) {
            // Создаём новую сессию и обновляем вкладки
            panel.onNewSession()
        } else {
            // На случай если панель не найдена (редко), создадим сессию через сервис
            service<ChatService>().createNewSession()
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
