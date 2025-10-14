package ru.marslab.ide.ride.ui.mcp

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Фабрика для создания Tool Window с MCP методами
 */
class MCPToolWindowFactory : ToolWindowFactory, DumbAware {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mcpMethodsPanel = MCPMethodsPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(mcpMethodsPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
