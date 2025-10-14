package ru.marslab.ide.ride.startup

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.mcp.MCPServerManager

/**
 * Listener для событий жизненного цикла приложения
 * Останавливает MCP Server при закрытии IDE
 */
class RideApplicationListener : AppLifecycleListener {
    private val logger = Logger.getInstance(RideApplicationListener::class.java)
    
    override fun appWillBeClosed(isRestart: Boolean) {
        logger.info("Ride plugin shutting down...")
        
        try {
            val serverManager = MCPServerManager.getInstance()
            serverManager.stopServer()
            logger.info("MCP Server stopped successfully")
        } catch (e: Exception) {
            logger.error("Error stopping MCP Server", e)
        }
    }
}
