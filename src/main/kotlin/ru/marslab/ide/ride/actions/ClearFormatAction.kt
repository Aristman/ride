package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import ru.marslab.ide.ride.service.ChatService

/**
 * Action для сброса формата ответа к TEXT (по умолчанию)
 */
class ClearFormatAction : AnAction("Clear Format (Text)") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val chatService = service<ChatService>()
        chatService.clearResponseFormat()
        
        Messages.showInfoMessage(
            e.project,
            "Формат сброшен. Ответы будут в обычном текстовом формате.",
            "Формат изменён"
        )
    }
}
