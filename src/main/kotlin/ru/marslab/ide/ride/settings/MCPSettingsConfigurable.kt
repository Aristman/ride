package ru.marslab.ide.ride.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import ru.marslab.ide.ride.ui.mcp.MCPSettingsPanel
import javax.swing.JComponent

/**
 * Configurable для настроек MCP серверов
 */
class MCPSettingsConfigurable(private val project: Project) : Configurable {
    
    private var panel: MCPSettingsPanel? = null
    
    override fun getDisplayName(): String = "MCP Servers"
    
    override fun createComponent(): JComponent {
        panel = MCPSettingsPanel(project)
        return panel!!
    }
    
    override fun isModified(): Boolean {
        return panel?.isModified() ?: false
    }
    
    override fun apply() {
        panel?.apply()
    }
    
    override fun reset() {
        panel?.reset()
    }
    
    override fun disposeUIResources() {
        panel = null
    }
}
