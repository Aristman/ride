package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import ru.marslab.ide.ride.service.ChatService

/**
 * Action для ручного открытия DevTools JCEF в окне чата.
 */
class OpenDevToolsAction : AnAction("DevTools", "Open JCEF DevTools", null) {
    override fun actionPerformed(e: AnActionEvent) {
        service<ChatService>().openDevToolsIfAvailable()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = service<ChatService>().isDevToolsAvailable()
    }
}
