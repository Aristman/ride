package ru.marslab.ide.ride.ui.mcp

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPSettings
import ru.marslab.ide.ride.service.mcp.MCPConfigService
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Панель настроек MCP серверов
 */
class MCPSettingsPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val configService = MCPConfigService.getInstance(project)
    private val connectionManager = MCPConnectionManager.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val tableModel = MCPServersTableModel()
    private val table = JBTable(tableModel)
    
    private var originalSettings: MCPSettings = MCPSettings.empty()
    private var currentSettings: MCPSettings = MCPSettings.empty()
    
    init {
        setupTable()
        setupToolbar()
        loadSettings()
    }
    
    private fun setupTable() {
        table.setShowGrid(true)
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        
        // Устанавливаем рендереры для колонок
        table.columnModel.getColumn(2).cellRenderer = StatusCellRenderer()
        
        // Устанавливаем ширину колонок
        table.columnModel.getColumn(0).preferredWidth = 150 // Name
        table.columnModel.getColumn(1).preferredWidth = 80  // Type
        table.columnModel.getColumn(2).preferredWidth = 100 // Status
    }
    
    private fun setupToolbar() {
        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddAction { addServer() }
            .setEditAction { editServer() }
            .setRemoveAction { removeServer() }
            .addExtraAction(object : AnActionButton("Test Connection", "Test connection to server", 
                AllIcons.Actions.Execute) {
                override fun actionPerformed(e: AnActionEvent) {
                    testConnection()
                }
                
                override fun isEnabled(): Boolean {
                    return table.selectedRow >= 0
                }
            })
            .addExtraAction(object : AnActionButton("Refresh", "Refresh server status", 
                AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    refreshStatuses()
                }
            })
        
        add(decorator.createPanel(), BorderLayout.CENTER)
    }
    
    private fun loadSettings() {
        originalSettings = configService.loadConfig()
        currentSettings = originalSettings
        tableModel.setServers(currentSettings.servers)
        refreshStatuses()
    }
    
    private fun addServer() {
        val dialog = MCPServerDialog(project, null)
        if (dialog.showAndGet()) {
            val newServer = dialog.getServerConfig()
            if (newServer != null) {
                try {
                    currentSettings = currentSettings.addServer(newServer)
                    tableModel.setServers(currentSettings.servers)
                } catch (e: IllegalArgumentException) {
                    Messages.showErrorDialog(
                        project,
                        e.message ?: "Failed to add server",
                        "Error"
                    )
                }
            }
        }
    }
    
    private fun editServer() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return
        
        val server = tableModel.getServerAt(selectedRow)
        val dialog = MCPServerDialog(project, server)
        
        if (dialog.showAndGet()) {
            val updatedServer = dialog.getServerConfig()
            if (updatedServer != null) {
                try {
                    currentSettings = currentSettings.updateServer(server.name, updatedServer)
                    tableModel.setServers(currentSettings.servers)
                } catch (e: IllegalArgumentException) {
                    Messages.showErrorDialog(
                        project,
                        e.message ?: "Failed to update server",
                        "Error"
                    )
                }
            }
        }
    }
    
    private fun removeServer() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return
        
        val server = tableModel.getServerAt(selectedRow)
        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to remove server '${server.name}'?",
            "Remove Server",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            currentSettings = currentSettings.removeServer(server.name)
            tableModel.setServers(currentSettings.servers)
        }
    }
    
    private fun testConnection() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return
        
        val server = tableModel.getServerAt(selectedRow)
        
        scope.launch {
            try {
                val success = connectionManager.connectServer(server)
                SwingUtilities.invokeLater {
                    if (success) {
                        Messages.showInfoMessage(
                            project,
                            "Successfully connected to '${server.name}'",
                            "Connection Test"
                        )
                        refreshStatuses()
                    } else {
                        Messages.showErrorDialog(
                            project,
                            "Failed to connect to '${server.name}'",
                            "Connection Test"
                        )
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Error: ${e.message}",
                        "Connection Test"
                    )
                }
            }
        }
    }
    
    private fun refreshStatuses() {
        scope.launch {
            val statuses = connectionManager.getAllStatuses()
            SwingUtilities.invokeLater {
                tableModel.updateStatuses(statuses.associateBy { it.name })
            }
        }
    }
    
    fun isModified(): Boolean {
        return currentSettings != originalSettings
    }
    
    fun apply() {
        // Валидация
        val errors = currentSettings.validate()
        if (errors.isNotEmpty()) {
            val errorMessage = errors.joinToString("\n") { "${it.first}: ${it.second}" }
            Messages.showErrorDialog(
                project,
                "Configuration has errors:\n$errorMessage",
                "Validation Error"
            )
            return
        }
        
        // Сохранение
        if (configService.saveConfig(currentSettings)) {
            originalSettings = currentSettings
            Messages.showInfoMessage(
                project,
                "MCP configuration saved successfully",
                "Success"
            )
            
            // Переинициализируем подключения
            connectionManager.initializeConnections()
        } else {
            Messages.showErrorDialog(
                project,
                "Failed to save MCP configuration",
                "Error"
            )
        }
    }
    
    fun reset() {
        currentSettings = originalSettings
        tableModel.setServers(currentSettings.servers)
        refreshStatuses()
    }
}

/**
 * Модель таблицы для списка MCP серверов
 */
private class MCPServersTableModel : AbstractTableModel() {
    
    private val columnNames = arrayOf("Name", "Type", "Status")
    private var servers: List<MCPServerConfig> = emptyList()
    private var statuses: Map<String, ru.marslab.ide.ride.model.mcp.MCPServerStatus> = emptyMap()
    
    fun setServers(servers: List<MCPServerConfig>) {
        this.servers = servers
        fireTableDataChanged()
    }
    
    fun updateStatuses(statuses: Map<String, ru.marslab.ide.ride.model.mcp.MCPServerStatus>) {
        this.statuses = statuses
        fireTableDataChanged()
    }
    
    fun getServerAt(row: Int): MCPServerConfig = servers[row]
    
    override fun getRowCount(): Int = servers.size
    
    override fun getColumnCount(): Int = columnNames.size
    
    override fun getColumnName(column: Int): String = columnNames[column]
    
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val server = servers[rowIndex]
        return when (columnIndex) {
            0 -> server.name
            1 -> server.type.name
            2 -> {
                val status = statuses[server.name]
                when {
                    status == null -> "Unknown"
                    status.connected -> "Connected (${status.methods.size} methods)"
                    status.hasError() -> "Error: ${status.error}"
                    else -> "Disconnected"
                }
            }
            else -> ""
        }
    }
    
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
}

/**
 * Рендерер для колонки статуса с цветовыми индикаторами
 */
private class StatusCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        
        if (component is JLabel && value is String) {
            when {
                value.startsWith("Connected") -> {
                    component.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                    component.icon = AllIcons.RunConfigurations.TestPassed
                }
                value.startsWith("Error") -> {
                    component.foreground = JBUI.CurrentTheme.Link.Foreground.DISABLED
                    component.icon = AllIcons.RunConfigurations.TestError
                }
                value == "Disconnected" -> {
                    component.foreground = JBUI.CurrentTheme.Label.disabledForeground()
                    component.icon = AllIcons.RunConfigurations.TestIgnored
                }
                else -> {
                    component.icon = null
                }
            }
        }
        
        return component
    }
}

// Placeholder для иконок (нужно будет заменить на реальные)
private object AllIcons {
    object Actions {
        val Execute = null
        val Refresh = null
    }
    object RunConfigurations {
        val TestPassed = null
        val TestError = null
        val TestIgnored = null
    }
}
