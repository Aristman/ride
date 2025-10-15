package ru.marslab.ide.ride.ui.mcp

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import ru.marslab.ide.ride.mcp.MCPServerManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Панель отображения статуса MCP Server Rust
 */
class MCPServerStatusPanel : JBPanel<MCPServerStatusPanel>(BorderLayout()) {
    
    private val serverManager = MCPServerManager.getInstance()
    private val statusLabel = JBLabel()
    private val startButton = JButton("Start")
    private val stopButton = JButton("Stop")
    private val toolsPanel = JPanel()
    private var toolsExpanded = false
    
    init {
        setupUI()
        updateStatus()
    }
    
    private fun setupUI() {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(10)
        )
        
        // Заголовок
        val titleLabel = JBLabel("MCP Server (Rust)")
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.size + 2f)
        
        // Панель заголовка и статуса
        val headerPanel = JPanel(BorderLayout())
        headerPanel.add(titleLabel, BorderLayout.WEST)
        
        // Панель статуса и кнопок
        val statusPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        statusPanel.add(statusLabel)
        statusPanel.add(startButton)
        statusPanel.add(stopButton)
        
        headerPanel.add(statusPanel, BorderLayout.EAST)
        
        // Кнопка для разворачивания tools
        val expandButton = JButton("Show Tools")
        expandButton.addActionListener {
            toolsExpanded = !toolsExpanded
            expandButton.text = if (toolsExpanded) "Hide Tools" else "Show Tools"
            toolsPanel.isVisible = toolsExpanded
            revalidate()
            repaint()
        }
        
        // Настройка кнопок управления
        startButton.addActionListener {
            startButton.isEnabled = false
            ApplicationManager.getApplication().executeOnPooledThread {
                serverManager.ensureServerRunning()
                SwingUtilities.invokeLater {
                    updateStatus()
                }
            }
        }
        
        stopButton.addActionListener {
            stopButton.isEnabled = false
            ApplicationManager.getApplication().executeOnPooledThread {
                serverManager.stopServer()
                SwingUtilities.invokeLater {
                    updateStatus()
                }
            }
        }
        
        // Панель с tools
        setupToolsPanel()
        toolsPanel.isVisible = false
        
        // Сборка UI
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.add(headerPanel)
        mainPanel.add(Box.createVerticalStrut(5))
        mainPanel.add(expandButton)
        mainPanel.add(Box.createVerticalStrut(5))
        mainPanel.add(toolsPanel)
        
        add(mainPanel, BorderLayout.CENTER)
    }
    
    private fun setupToolsPanel() {
        toolsPanel.layout = GridBagLayout()
        toolsPanel.border = JBUI.Borders.empty(10, 20, 10, 10)
        
        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        gbc.insets = JBUI.insets(2)
        
        // Список доступных tools
        val tools = listOf(
            ToolInfo("create_file", "Create a new file", "path, content, overwrite"),
            ToolInfo("read_file", "Read file content", "path"),
            ToolInfo("update_file", "Update file content", "path, content"),
            ToolInfo("delete_file", "Delete a file", "path"),
            ToolInfo("list_files", "List files in directory", "dir (optional)"),
            ToolInfo("create_directory", "Create a directory", "path, recursive"),
            ToolInfo("delete_directory", "Delete a directory", "path"),
            ToolInfo("list_directory", "List directory contents", "path (optional)")
        )
        
        for (tool in tools) {
            val toolPanel = createToolPanel(tool)
            toolsPanel.add(toolPanel, gbc)
            gbc.gridy++
        }
    }
    
    private fun createToolPanel(tool: ToolInfo): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(5)
        
        // Иконка и название
        val namePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        val icon = when {
            tool.name.contains("file") -> AllIcons.FileTypes.Any_type
            tool.name.contains("directory") -> AllIcons.Nodes.Folder
            else -> AllIcons.Actions.Execute
        }
        namePanel.add(JLabel(icon))
        
        val nameLabel = JBLabel(tool.name)
        nameLabel.font = nameLabel.font.deriveFont(nameLabel.font.style or java.awt.Font.BOLD)
        namePanel.add(nameLabel)
        
        // Описание
        val descLabel = JBLabel(tool.description)
        descLabel.foreground = JBColor.GRAY
        
        // Параметры
        val paramsLabel = JBLabel("Parameters: ${tool.parameters}")
        paramsLabel.foreground = JBColor.DARK_GRAY
        paramsLabel.font = paramsLabel.font.deriveFont(paramsLabel.font.size - 1f)
        
        // Сборка
        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.add(namePanel)
        infoPanel.add(descLabel)
        infoPanel.add(paramsLabel)
        
        panel.add(infoPanel, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun updateStatus() {
        val isRunning = serverManager.isServerRunning()
        
        if (isRunning) {
            statusLabel.text = "Status: Running"
            statusLabel.icon = AllIcons.RunConfigurations.TestPassed
            statusLabel.foreground = JBColor.GREEN
            startButton.isEnabled = false
            stopButton.isEnabled = true
        } else {
            statusLabel.text = "Status: Stopped"
            statusLabel.icon = AllIcons.RunConfigurations.TestError
            statusLabel.foreground = JBColor.RED
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }
    
    private data class ToolInfo(
        val name: String,
        val description: String,
        val parameters: String
    )
}
