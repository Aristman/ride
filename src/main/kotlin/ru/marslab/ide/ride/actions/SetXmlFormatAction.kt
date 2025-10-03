package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema
import ru.marslab.ide.ride.service.ChatService

/**
 * Action для установки XML формата ответа
 */
class SetXmlFormatAction : AnAction("XML Format") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val chatService = service<ChatService>()
        
        // Запрашиваем схему у пользователя
        val schemaInput = Messages.showInputDialog(
            e.project,
            "Введите XML схему (или оставьте пустым для базовой):",
            "XML Schema",
            Messages.getQuestionIcon(),
            "<response><answer>string</answer></response>",
            null
        )
        
        if (schemaInput != null) {
            val schema = if (schemaInput.isBlank()) {
                ResponseSchema.xml("<root></root>", "Структурированный XML ответ")
            } else {
                ResponseSchema.xml(schemaInput, "Пользовательская XML схема")
            }
            
            chatService.setResponseFormat(ResponseFormat.XML, schema)
            
            Messages.showInfoMessage(
                e.project,
                "XML формат установлен. Следующие ответы будут в XML.",
                "Формат изменён"
            )
        }
    }
}
