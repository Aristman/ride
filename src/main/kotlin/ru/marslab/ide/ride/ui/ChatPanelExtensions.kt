package ru.marslab.ide.ride.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.service.ChatService

/**
 * Расширения для ChatPanel для работы с оркестратором
 */

/**
 * Отправляет сообщение через систему двух агентов (PlannerAgent + ExecutorAgent)
 * 
 * Использование: добавьте префикс "/plan " к вашему сообщению
 * Например: "/plan создай простое веб-приложение"
 */
fun sendMessageWithOrchestratorMode(
    project: Project,
    text: String,
    onStepComplete: (Message) -> Unit,
    onError: (String) -> Unit,
    onComplete: () -> Unit
) {
    val chatService = service<ChatService>()
    
    chatService.sendMessageWithOrchestrator(
        userMessage = text,
        project = project,
        onStepComplete = { message ->
            onStepComplete(message)
            
            // Проверяем, завершены ли все шаги
            val metadata = message.metadata
            if (metadata.containsKey("totalTasks")) {
                // Это финальное сообщение AllComplete
                onComplete()
            }
        },
        onError = { error ->
            onError(error)
            onComplete()
        }
    )
}
