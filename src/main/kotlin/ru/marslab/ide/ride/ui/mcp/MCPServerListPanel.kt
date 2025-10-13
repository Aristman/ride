package ru.marslab.ide.ride.ui.mcp

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPSettings
import ru.marslab.ide.ride.service.mcp.MCPConfigService
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager
import java.awt.BorderLayout
import javax.swing.*

/**
 * Панель для отображения списка MCP серверов в виде карточек
 */
class MCPServerListPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val configService = MCPConfigService.getInstance(project)
    private val connectionManager = MCPConnectionManager.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val serversPanel = JPanel()
    private val serversScroll = JScrollPane(serversPanel)
    private val refreshAllButton = JButton("Refresh All Servers")

    private var currentSettings: MCPSettings = MCPSettings.empty()
    private val serverItems = mutableListOf<MCPServerListItem>()

    init {
        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        // Верхняя панель с кнопками
        val topPanel = JPanel(BorderLayout())
        topPanel.border = JBUI.Borders.empty(10)

        // Кнопка Refresh All
        refreshAllButton.icon = AllIcons.Actions.Refresh
        refreshAllButton.toolTipText = "Test all configured servers"
        refreshAllButton.addActionListener {
            refreshAllServers()
        }

        topPanel.add(refreshAllButton, BorderLayout.EAST)
        add(topPanel, BorderLayout.NORTH)

        // Панель со списком серверов
        serversPanel.layout = BoxLayout(serversPanel, BoxLayout.Y_AXIS)
        serversPanel.border = JBUI.Borders.empty(10)
        add(serversScroll, BorderLayout.CENTER)

        serversScroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        serversScroll.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        serversScroll.border = JBUI.Borders.empty()
    }

    private fun loadSettings() {
        currentSettings = configService.loadConfig()
        updateServersList()
    }

    private fun updateServersList() {
        // Очищаем старые элементы
        serverItems.forEach { it.dispose() }
        serverItems.clear()
        serversPanel.removeAll()

        if (currentSettings.servers.isEmpty()) {
            val emptyLabel = JLabel("No MCP servers configured")
            emptyLabel.border = JBUI.Borders.empty(20)
            serversPanel.add(emptyLabel)
        } else {
            currentSettings.servers.forEach { server ->
                val serverItem = MCPServerListItem(
                    project = project,
                    server = server,
                    connectionManager = connectionManager,
                    onRefreshComplete = {
                        // Можно добавить дополнительную логику после рефреша
                    }
                )
                serverItems.add(serverItem)
                serversPanel.add(serverItem)
                serversPanel.add(Box.createVerticalStrut(5))
            }
        }

        serversPanel.revalidate()
        serversPanel.repaint()
    }

    private fun refreshAllServers() {
        refreshAllButton.isEnabled = false
        refreshAllButton.icon = AllIcons.Process.Step_1
        refreshAllButton.text = "Refreshing..."

        scope.launch(Dispatchers.IO) {
            val servers = currentSettings.servers
            if (servers.isEmpty()) {
                withContext(Dispatchers.Main) {
                    resetRefreshButton()
                }
                return@launch
            }

            servers.forEach { server ->
                try {
                    connectionManager.connectServer(server)
                } catch (e: Exception) {
                    // Ошибки логируются в connectionManager
                }

                // Обновляем UI в реальном времени
                withContext(Dispatchers.Main) {
                    serverItems.forEach { it.refreshStatus() }
                }

                // Небольшая задержка между серверами
                delay(300)
            }

            // Сбрасываем кнопку после завершения
            withContext(Dispatchers.Main) {
                resetRefreshButton()
            }
        }
    }

    private fun resetRefreshButton() {
        refreshAllButton.isEnabled = true
        refreshAllButton.icon = AllIcons.Actions.Refresh
        refreshAllButton.text = "Refresh All Servers"
    }

    fun addServer(server: MCPServerConfig) {
        currentSettings = currentSettings.addServer(server)
        updateServersList()
    }

    fun updateServer(oldName: String, server: MCPServerConfig) {
        currentSettings = currentSettings.updateServer(oldName, server)
        updateServersList()
    }

    fun removeServer(serverName: String) {
        currentSettings = currentSettings.removeServer(serverName)
        updateServersList()
    }

    fun getServers(): List<MCPServerConfig> = currentSettings.servers

    fun dispose() {
        scope.cancel()
        serverItems.forEach { it.dispose() }
    }
}