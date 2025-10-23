package ru.marslab.ide.ride.ui.mcp

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPSettings
import ru.marslab.ide.ride.service.mcp.MCPConfigService
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Панель настроек MCP серверов
 */
class MCPSettingsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val configService = MCPConfigService.getInstance(project)
    private val connectionManager = MCPConnectionManager.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var serverListPanel: MCPServerListPanel

    private var originalSettings: MCPSettings = MCPSettings.empty()
    private var currentSettings: MCPSettings = MCPSettings.empty()

    init {
        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        // Создаем главную панель с вертикальным layout
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        // Добавляем панель статуса MCP Server Rust
        mainPanel.add(createMCPServerStatusPanel())
        mainPanel.add(Box.createVerticalStrut(10))

        // Инициализируем панель списка серверов до добавления в layout
        if (!::serverListPanel.isInitialized) {
            serverListPanel = MCPServerListPanel(
                project = project,
                onEditServer = { server -> editServer(server) },
                onDeleteServer = { server ->
                    currentSettings = currentSettings.removeServer(server.name)
                    serverListPanel.removeServer(server.name)
                    // сохраняем сразу
                    configService.saveConfig(currentSettings)
                },
                onServerToggle = { server ->
                    // обновляем текущие настройки и сохраняем
                    currentSettings = currentSettings.updateServer(server.name, server)
                    serverListPanel.updateServer(server.name, server)
                    configService.saveConfig(currentSettings)
                }
            )
        }

        // Создаем панель с кнопками
        val buttonPanel = JPanel()
        buttonPanel.border = JBUI.Borders.empty(5)

        // Размер кнопок под иконки
        val buttonSize = Dimension(28, 28)

        val addButton = JButton()
        addButton.icon = AllIcons.General.Add
        addButton.toolTipText = "Add Server"
        addButton.preferredSize = buttonSize
        addButton.addActionListener { addServer() }

        val refreshAllButton = JButton()
        refreshAllButton.icon = AllIcons.Actions.Refresh
        refreshAllButton.toolTipText = "Refresh All Servers"
        refreshAllButton.preferredSize = buttonSize
        refreshAllButton.addActionListener {
            serverListPanel.refreshAllServersPublic(refreshAllButton)
        }

        buttonPanel.add(addButton)
        buttonPanel.add(refreshAllButton)

        mainPanel.add(buttonPanel)
        mainPanel.add(serverListPanel)

        add(mainPanel, BorderLayout.CENTER)
    }

    private fun createMCPServerStatusPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)

        val statusPanel = MCPServerStatusPanel()
        panel.add(statusPanel, BorderLayout.CENTER)

        return panel
    }

    private fun loadSettings() {
        originalSettings = configService.loadConfig()
        currentSettings = originalSettings
        updateServerList()
    }

    private fun updateServerList() {
        serverListPanel.dispose()
        serverListPanel = MCPServerListPanel(
            project = project,
            onEditServer = { server -> editServer(server) },
            onDeleteServer = { server ->
                currentSettings = currentSettings.removeServer(server.name)
                serverListPanel.removeServer(server.name)
                configService.saveConfig(currentSettings)
            },
            onServerToggle = { server ->
                currentSettings = currentSettings.updateServer(server.name, server)
                serverListPanel.updateServer(server.name, server)
                configService.saveConfig(currentSettings)
            }
        )
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

    private fun editServer(server: MCPServerConfig) {
        val dialog = MCPServerDialog(project, server)
        if (dialog.showAndGet()) {
            val updatedServer = dialog.getServerConfig()
            if (updatedServer != null) {
                try {
                    currentSettings = currentSettings.updateServer(server.name, updatedServer)
                    serverListPanel.updateServer(server.name, updatedServer)
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
