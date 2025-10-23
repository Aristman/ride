package ru.marslab.ide.ride.ui.mcp

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.marslab.ide.ride.model.mcp.MCPMethod
import ru.marslab.ide.ride.model.mcp.MCPServerStatus
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Панель для отображения методов MCP серверов
 *
 * Показывает expandable список серверов с их методами
 */
class MCPMethodsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val connectionManager = MCPConnectionManager.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val serversPanel = JPanel()
    private val scrollPane = JBScrollPane(serversPanel)

    init {
        serversPanel.layout = BoxLayout(serversPanel, BoxLayout.Y_AXIS)
        serversPanel.border = JBUI.Borders.empty(10)

        add(scrollPane, BorderLayout.CENTER)

        // Добавляем кнопку обновления
        val refreshButton = JButton("Refresh Servers")
        refreshButton.addActionListener { refreshServers() }

        val toolbarPanel = JPanel(BorderLayout())
        toolbarPanel.border = JBUI.Borders.empty(5)
        toolbarPanel.add(refreshButton, BorderLayout.EAST)

        add(toolbarPanel, BorderLayout.NORTH)

        // Загружаем серверы
        refreshServers()
    }

    private fun refreshServers() {
        scope.launch {
            val statuses = connectionManager.getAllStatuses()

            SwingUtilities.invokeLater {
                serversPanel.removeAll()

                if (statuses.isEmpty()) {
                    val emptyLabel = JBLabel("No MCP servers configured. Add servers in Settings → MCP Servers")
                    emptyLabel.border = JBUI.Borders.empty(20)
                    serversPanel.add(emptyLabel)
                } else {
                    statuses.forEach { status ->
                        serversPanel.add(createServerPanel(status))
                        serversPanel.add(Box.createVerticalStrut(5))
                    }
                }

                serversPanel.revalidate()
                serversPanel.repaint()
            }
        }
    }

    private fun createServerPanel(status: MCPServerStatus): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(5, 10)

        // Заголовок сервера
        val headerPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(2)

        // Иконка статуса
        val statusIcon = when {
            status.connected -> "✓"
            status.hasError() -> "✗"
            else -> "○"
        }
        val statusColor = when {
            status.connected -> JBUI.CurrentTheme.Link.Foreground.ENABLED
            status.hasError() -> JBUI.CurrentTheme.Link.Foreground.DISABLED
            else -> JBUI.CurrentTheme.Label.disabledForeground()
        }

        val statusLabel = JBLabel(statusIcon)
        statusLabel.foreground = statusColor
        gbc.gridx = 0
        headerPanel.add(statusLabel, gbc)

        // Имя сервера
        val nameLabel = JBLabel("<html><b>${status.name}</b></html>")
        gbc.gridx = 1
        gbc.weightx = 1.0
        headerPanel.add(nameLabel, gbc)

        // Количество методов
        val methodCountLabel = JBLabel("(${status.methods.size} methods)")
        methodCountLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
        gbc.gridx = 2
        gbc.weightx = 0.0
        headerPanel.add(methodCountLabel, gbc)

        panel.add(headerPanel, BorderLayout.NORTH)

        // Список методов (expandable)
        if (status.connected && status.hasMethods()) {
            val methodsPanel = createMethodsPanel(status)
            methodsPanel.isVisible = false // Скрыто по умолчанию

            // Toggle при клике на заголовок
            headerPanel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            headerPanel.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    methodsPanel.isVisible = !methodsPanel.isVisible
                    panel.revalidate()
                    panel.repaint()
                }
            })

            panel.add(methodsPanel, BorderLayout.CENTER)
        } else if (status.hasError()) {
            val errorLabel = JBLabel("<html><font color='red'>Error: ${status.error}</font></html>")
            errorLabel.border = JBUI.Borders.emptyLeft(20)
            panel.add(errorLabel, BorderLayout.CENTER)
        } else if (!status.connected) {
            val disconnectedLabel = JBLabel("Disconnected")
            disconnectedLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            disconnectedLabel.border = JBUI.Borders.emptyLeft(20)
            panel.add(disconnectedLabel, BorderLayout.CENTER)
        }

        return panel
    }

    private fun createMethodsPanel(status: MCPServerStatus): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.emptyLeft(20)

        status.methods.forEach { method ->
            panel.add(createMethodRow(status.name, method))
            panel.add(Box.createVerticalStrut(3))
        }

        return panel
    }

    private fun createMethodRow(serverName: String, method: MCPMethod): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(3, 5)

        // Информация о методе
        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)

        val nameLabel = JBLabel(method.name)
        infoPanel.add(nameLabel)

        if (method.hasDescription()) {
            val descLabel = JBLabel("<html><font color='gray'>${method.description}</font></html>")
            infoPanel.add(descLabel)
        }

        panel.add(infoPanel, BorderLayout.CENTER)

        // Кнопка вызова
        val callButton = JButton("Call")
        callButton.addActionListener {
            callMethod(serverName, method)
        }
        panel.add(callButton, BorderLayout.EAST)

        return panel
    }

    private fun callMethod(serverName: String, method: MCPMethod) {
        val dialog = MCPMethodCallDialog(project, serverName, method, connectionManager)
        dialog.show()
    }
}
