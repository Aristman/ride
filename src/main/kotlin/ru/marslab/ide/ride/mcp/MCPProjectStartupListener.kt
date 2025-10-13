package ru.marslab.ide.ride.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager

/**
 * Слушатель для инициализации MCP серверов при старте проекта
 */
@Suppress("DEPRECATION")
class MCPProjectStartupListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        // Инициализируем MCP подключения для открытого проекта
        val connectionManager = project.getService(MCPConnectionManager::class.java)
        connectionManager.initializeConnections()
    }

    override fun projectClosed(project: Project) {
        // Очищаем MCP подключения при закрытии проекта
        val connectionManager = project.getService(MCPConnectionManager::class.java)
        connectionManager.disconnectAll()
    }
}