package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.UncertaintyResponseSchema
import ru.marslab.ide.ride.service.ChatService

/**
 * Action для установки формата ответа с анализом неопределенности (JSON)
 */
class SetUncertaintyFormatAction : AnAction("Set Uncertainty Format"), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val chatService = e.project?.service<ChatService>() ?: return
        val schema = UncertaintyResponseSchema.createJsonSchema()
        chatService.setResponseFormat(ResponseFormat.JSON, schema)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}