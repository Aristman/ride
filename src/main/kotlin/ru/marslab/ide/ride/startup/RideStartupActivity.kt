package ru.marslab.ide.ride.startup

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import ru.marslab.ide.ride.mcp.MCPServerManager

/**
 * Активность, выполняемая при запуске проекта
 * Запускает MCP Server автоматически
 */
class RideStartupActivity : ProjectActivity {
    private val logger = Logger.getInstance(RideStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        logger.info("Ride plugin starting up for project: ${project.basePath}")

        // Не запускаем MCP сервер автоматически при старте плагина
        // Сервер будет запущен по требованию при первом использовании файловых операций
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val serverManager = MCPServerManager.getInstance()
                
                // Только проверяем, работает ли внешний сервер
                if (serverManager.isServerRunning()) {
                    logger.info("External MCP Server is already running")
                    showNotification(
                        "Ride Plugin Ready",
                        "MCP server detected - file operations available",
                        NotificationType.INFORMATION
                    )
                } else {
                    logger.info("Ride plugin loaded for project: ${project.basePath}")
                    showNotification(
                        "Ride Plugin Ready", 
                        "File operations will be available when needed",
                        NotificationType.INFORMATION
                    )
                }
            } catch (e: Exception) {
                logger.error("Error starting MCP Server", e)
                showNotification(
                    "MCP Server Error",
                    "Failed to start: ${e.message}",
                    NotificationType.ERROR
                )
            }
        }
    }

    private fun showNotification(title: String, content: String, type: NotificationType) {
        val notification = Notification(
            "Ride",
            title,
            content,
            type
        )
        Notifications.Bus.notify(notification)
    }
}
