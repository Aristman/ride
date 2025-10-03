package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema
import ru.marslab.ide.ride.service.ChatService

/**
 * Action для установки JSON формата ответа
 */
class SetJsonFormatAction : AnAction("JSON Format") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val chatService = service<ChatService>()
        
        // Запрашиваем схему у пользователя
        val schemaInput = Messages.showInputDialog(
            e.project,
            "Введите JSON схему (или оставьте пустым для базовой):",
            "JSON Schema",
            Messages.getQuestionIcon(),
            """{"answer": "string", "confidence": 0.0}""",
            null
        )
        
        if (schemaInput != null) {
            val schema = if (schemaInput.isBlank()) {
                ResponseSchema.json("{}", "Структурированный JSON ответ")
            } else {
                ResponseSchema.json(schemaInput, "Пользовательская JSON схема")
            }
            
            chatService.setResponseFormat(ResponseFormat.JSON, schema)
            
            Messages.showInfoMessage(
                e.project,
                "JSON формат установлен. Следующие ответы будут в JSON.",
                "Формат изменён"
            )
        }
    }
}
