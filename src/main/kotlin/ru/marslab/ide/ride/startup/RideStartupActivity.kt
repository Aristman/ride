package ru.marslab.ide.ride.startup

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager

/**
 * Активность, выполняемая при запуске проекта
 * Запускает MCP Server автоматически
 */
class RideStartupActivity : ProjectActivity {
    private val logger = Logger.getInstance(RideStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        logger.info("Ride plugin starting up...")

        // Инициализировать MCP подключения
        try {
            val connectionManager = MCPConnectionManager.getInstance(project)
            connectionManager.initializeConnections()

            logger.info("MCP connections initialized")
            showNotification(
                "MCP Ready",
                "File system operations are now available",
                NotificationType.INFORMATION
            )
        } catch (e: Exception) {
            logger.error("Error initializing MCP connections", e)
            showNotification(
                "MCP Error",
                "Failed to initialize: ${e.message}",
                NotificationType.ERROR
            )
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
