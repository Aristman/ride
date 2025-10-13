package ru.marslab.ide.ride.ui.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPServerType
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Диалог для добавления/редактирования MCP сервера
 */
class MCPServerDialog(
    private val project: Project,
    private val existingServer: MCPServerConfig?
) : DialogWrapper(project) {
    
    private lateinit var nameField: JBTextField
    private lateinit var typeComboBox: ComboBox<MCPServerType>
    private lateinit var enabledCheckBox: JBCheckBox
    
    // Поля для STDIO
    private lateinit var commandField: JBTextField
    private lateinit var argsField: JBTextField
    private lateinit var envField: JBTextField
    
    // Поля для HTTP
    private lateinit var urlField: JBTextField
    
    private lateinit var cardPanel: JPanel
    private lateinit var cardLayout: CardLayout
    
    private var serverConfig: MCPServerConfig? = null
    
    init {
        title = if (existingServer == null) "Add MCP Server" else "Edit MCP Server"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        cardLayout = CardLayout()
        cardPanel = JPanel(cardLayout)
        
        // Создаем панели для разных типов
        val stdioPanel = createStdioPanel()
        val httpPanel = createHttpPanel()
        
        cardPanel.add(stdioPanel, MCPServerType.STDIO.name)
        cardPanel.add(httpPanel, MCPServerType.HTTP.name)
        
        return panel {
            row("Name:") {
                nameField = textField()
                    .columns(30)
                    .component
            }
            
            row("Type:") {
                typeComboBox = comboBox(MCPServerType.values().toList())
                    .component
                
                typeComboBox.addActionListener {
                    val selectedType = typeComboBox.selectedItem as? MCPServerType
                    if (selectedType != null) {
                        cardLayout.show(cardPanel, selectedType.name)
                    }
                }
            }
            
            row {
                cell(cardPanel)
                    .align(AlignX.FILL)
            }
            
            row {
                enabledCheckBox = checkBox("Enabled")
                    .component
            }
        }.apply {
            // Загружаем данные существующего сервера
            existingServer?.let { server ->
                nameField.text = server.name
                typeComboBox.selectedItem = server.type
                enabledCheckBox.isSelected = server.enabled
                
                when (server.type) {
                    MCPServerType.STDIO -> {
                        commandField.text = server.command ?: ""
                        argsField.text = server.args.joinToString(" ")
                        envField.text = server.env.entries.joinToString(";") { "${it.key}=${it.value}" }
                    }
                    MCPServerType.HTTP -> {
                        urlField.text = server.url ?: ""
                    }
                }
                
                cardLayout.show(cardPanel, server.type.name)
            } ?: run {
                // Значения по умолчанию для нового сервера
                enabledCheckBox.isSelected = true
                typeComboBox.selectedItem = MCPServerType.STDIO
                cardLayout.show(cardPanel, MCPServerType.STDIO.name)
            }
        }
    }
    
    private fun createStdioPanel(): JComponent {
        return panel {
            row("Command:") {
                commandField = textField()
                    .columns(30)
                    .comment("Executable command (e.g., 'node', 'python')")
                    .component
            }
            
            row("Arguments:") {
                argsField = textField()
                    .columns(30)
                    .comment("Space-separated arguments (e.g., 'path/to/script.js --option')")
                    .component
            }
            
            row("Environment:") {
                envField = textField()
                    .columns(30)
                    .comment("Semicolon-separated env vars (e.g., 'KEY1=value1;KEY2=value2')")
                    .component
            }
        }
    }
    
    private fun createHttpPanel(): JComponent {
        return panel {
            row("URL:") {
                urlField = textField()
                    .columns(30)
                    .comment("HTTP endpoint URL (e.g., 'http://localhost:3000/mcp')")
                    .component
            }
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()
        if (name.isEmpty()) {
            return ValidationInfo("Name is required", nameField)
        }
        
        val type = typeComboBox.selectedItem as? MCPServerType
            ?: return ValidationInfo("Type is required", typeComboBox)
        
        return when (type) {
            MCPServerType.STDIO -> {
                val command = commandField.text.trim()
                if (command.isEmpty()) {
                    ValidationInfo("Command is required for STDIO type", commandField)
                } else {
                    null
                }
            }
            MCPServerType.HTTP -> {
                val url = urlField.text.trim()
                if (url.isEmpty()) {
                    ValidationInfo("URL is required for HTTP type", urlField)
                } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    ValidationInfo("URL must start with http:// or https://", urlField)
                } else {
                    null
                }
            }
        }
    }
    
    override fun doOKAction() {
        val name = nameField.text.trim()
        val type = typeComboBox.selectedItem as MCPServerType
        val enabled = enabledCheckBox.isSelected
        
        serverConfig = when (type) {
            MCPServerType.STDIO -> {
                val command = commandField.text.trim()
                val args = argsField.text.trim()
                    .split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                val env = parseEnvironment(envField.text.trim())
                
                MCPServerConfig(
                    name = name,
                    type = type,
                    command = command,
                    args = args,
                    env = env,
                    enabled = enabled
                )
            }
            MCPServerType.HTTP -> {
                val url = urlField.text.trim()
                
                MCPServerConfig(
                    name = name,
                    type = type,
                    url = url,
                    enabled = enabled
                )
            }
        }
        
        super.doOKAction()
    }
    
    private fun parseEnvironment(envString: String): Map<String, String> {
        if (envString.isEmpty()) return emptyMap()
        
        return envString.split(";")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0].trim() to parts[1].trim()
                } else {
                    null
                }
            }
            .toMap()
    }
    
    fun getServerConfig(): MCPServerConfig? = serverConfig
}
