package ru.marslab.ide.ride.ui.mcp

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPSettings
import ru.marslab.ide.ride.service.mcp.MCPConfigService
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager
import java.awt.BorderLayout
import javax.swing.*

/**
 * Панель настроек MCP серверов
 */
class MCPSettingsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val configService = MCPConfigService.getInstance(project)
    private val connectionManager = MCPConnectionManager.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var serverListPanel = MCPServerListPanel(project)

    private var originalSettings: MCPSettings = MCPSettings.empty()
    private var currentSettings: MCPSettings = MCPSettings.empty()

    init {
        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        // Создаем панель с кнопками
        val buttonPanel = JPanel()
        buttonPanel.border = JBUI.Borders.empty(5)

        val addButton = JButton("Add Server")
        addButton.icon = AllIcons.General.Add
        addButton.addActionListener { addServer() }

        val editButton = JButton("Edit Server")
        editButton.icon = AllIcons.Actions.Edit
        editButton.addActionListener { editServer() }

        val removeButton = JButton("Remove Server")
        removeButton.icon = AllIcons.General.Remove
        removeButton.addActionListener { removeServer() }

        buttonPanel.add(addButton)
        buttonPanel.add(editButton)
        buttonPanel.add(removeButton)

        add(buttonPanel, BorderLayout.NORTH)
        add(serverListPanel, BorderLayout.CENTER)
    }
    
    private fun loadSettings() {
        originalSettings = configService.loadConfig()
        currentSettings = originalSettings
        updateServerList()
    }

    private fun updateServerList() {
        serverListPanel.dispose()
        serverListPanel = MCPServerListPanel(project)
        removeAll()
        setupUI()
        revalidate()
        repaint()
    }

    private fun addServer() {
        val dialog = MCPServerDialog(project, null)
        if (dialog.showAndGet()) {
            val newServer = dialog.getServerConfig()
            if (newServer != null) {
                try {
                    currentSettings = currentSettings.addServer(newServer)
                    serverListPanel.addServer(newServer)
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
        // Для простоты покажем диалог выбора сервера для редактирования
        val servers = currentSettings.servers
        if (servers.isEmpty()) {
            Messages.showInfoMessage("No servers to edit", "Edit Server")
            return
        }

        val serverNames = servers.map { it.name }.toTypedArray()
        val selectedName = Messages.showEditableChooseDialog(
            "Select server to edit:",
            "Edit Server",
            Messages.getQuestionIcon(),
            serverNames,
            serverNames.firstOrNull(),
            null
        )

        if (selectedName != null) {
            val server = servers.find { it.name == selectedName }
            if (server != null) {
                val dialog = MCPServerDialog(project, server)
                if (dialog.showAndGet()) {
                    val updatedServer = dialog.getServerConfig()
                    if (updatedServer != null) {
                        try {
                            currentSettings = currentSettings.updateServer(server.name, updatedServer)
                            serverListPanel.updateServer(server.name, updatedServer)
                        } catch (e: IllegalArgumentException) {
                            Messages.showErrorDialog(
                                e.message ?: "Failed to update server",
                                "Error"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun removeServer() {
        val servers = currentSettings.servers
        if (servers.isEmpty()) {
            Messages.showInfoMessage("No servers to remove", "Remove Server")
            return
        }

        val serverNames = servers.map { it.name }.toTypedArray()
        val selectedName = Messages.showEditableChooseDialog(
            "Select server to remove:",
            "Remove Server",
            Messages.getQuestionIcon(),
            serverNames,
            serverNames.firstOrNull(),
            null
        )

        if (selectedName != null) {
            val result = Messages.showYesNoDialog(
                "Are you sure you want to remove server '$selectedName'?",
                "Remove Server",
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                currentSettings = currentSettings.removeServer(selectedName)
                serverListPanel.removeServer(selectedName)
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
            
            // Переинициализируем подключения без всплывающих окон
            connectionManager.initializeConnections()
            updateServerList()
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
        updateServerList()
    }

    fun dispose() {
        serverListPanel.dispose()
        scope.cancel()
    }
}
