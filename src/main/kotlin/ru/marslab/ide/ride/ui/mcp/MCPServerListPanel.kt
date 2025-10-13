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
class MCPServerListPanel(
        private val project: Project,
        private val onEditServer: (MCPServerConfig) -> Unit = {},
        private val onDeleteServer: (MCPServerConfig) -> Unit = {}
    ) : JPanel(BorderLayout()) {

    private val configService = MCPConfigService.getInstance(project)
    private val connectionManager = MCPConnectionManager.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val serversPanel = JPanel()
    private val serversScroll = JScrollPane(serversPanel)

    private var currentSettings: MCPSettings = MCPSettings.empty()
    private val serverItems = mutableListOf<MCPServerListItem>()
    private var isRefreshing = false

    init {
        setupUI()
        loadSettings()
    }

    private fun setupUI() {
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
                    },
                    onEditServer = onEditServer,
                    onDeleteServer = onDeleteServer
                )
                serverItems.add(serverItem)
                serversPanel.add(serverItem)
                serversPanel.add(Box.createVerticalStrut(5))
            }
        }

        serversPanel.revalidate()
        serversPanel.repaint()
    }

    fun refreshAllServersPublic(button: JButton? = null) {
        if (isRefreshing) return
        
        isRefreshing = true
        
        // Меняем иконку на процесс обновления
        SwingUtilities.invokeLater {
            button?.icon = AllIcons.Process.Step_1
        }

        scope.launch(Dispatchers.IO) {
            val servers = currentSettings.servers
            if (servers.isEmpty()) {
                SwingUtilities.invokeLater {
                    button?.icon = AllIcons.Actions.Refresh
                    isRefreshing = false
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
                SwingUtilities.invokeLater {
                    serverItems.forEach { it.refreshStatus() }
                }

                // Небольшая задержка между серверами
                delay(300)
            }

            // Возвращаем иконку обратно и сбрасываем флаг
            SwingUtilities.invokeLater {
                button?.icon = AllIcons.Actions.Refresh
                isRefreshing = false
            }
        }
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