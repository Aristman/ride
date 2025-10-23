package ru.marslab.ide.ride.ui.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.marslab.ide.ride.model.mcp.MCPMethod
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Диалог для вызова метода MCP сервера
 */
class MCPMethodCallDialog(
    private val project: Project,
    private val serverName: String,
    private val method: MCPMethod,
    private val connectionManager: MCPConnectionManager
) : DialogWrapper(project) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private lateinit var argumentsField: JBTextArea
    private lateinit var resultArea: JBTextArea
    private lateinit var statusLabel: JLabel

    init {
        title = "Call Method: ${method.name}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 500)

        // Верхняя панель с информацией
        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.border = JBUI.Borders.empty(10)

        infoPanel.add(JLabel("<html><b>Server:</b> $serverName</html>"))
        infoPanel.add(Box.createVerticalStrut(5))
        infoPanel.add(JLabel("<html><b>Method:</b> ${method.name}</html>"))

        if (method.hasDescription()) {
            infoPanel.add(Box.createVerticalStrut(5))
            infoPanel.add(JLabel("<html><b>Description:</b> ${method.description}</html>"))
        }

        panel.add(infoPanel, BorderLayout.NORTH)

        // Центральная панель с полями ввода/вывода
        val centerPanel = JPanel(BorderLayout())
        centerPanel.border = JBUI.Borders.empty(10)

        // Поле для аргументов
        val argumentsPanel = JPanel(BorderLayout())
        argumentsPanel.add(JLabel("Arguments (JSON):"), BorderLayout.NORTH)

        argumentsField = JBTextArea()
        argumentsField.lineWrap = true
        argumentsField.wrapStyleWord = true
        argumentsField.text = if (method.hasInputSchema()) {
            "{\n  \n}"
        } else {
            "{}"
        }

        val argumentsScroll = JBScrollPane(argumentsField)
        argumentsScroll.preferredSize = Dimension(580, 150)
        argumentsPanel.add(argumentsScroll, BorderLayout.CENTER)

        centerPanel.add(argumentsPanel, BorderLayout.NORTH)

        // Поле для результата
        val resultPanel = JPanel(BorderLayout())
        resultPanel.border = JBUI.Borders.emptyTop(10)
        resultPanel.add(JLabel("Result:"), BorderLayout.NORTH)

        resultArea = JBTextArea()
        resultArea.isEditable = false
        resultArea.lineWrap = true
        resultArea.wrapStyleWord = true
        resultArea.text = "Click 'Call' to execute the method"

        val resultScroll = JBScrollPane(resultArea)
        resultScroll.preferredSize = Dimension(580, 200)
        resultPanel.add(resultScroll, BorderLayout.CENTER)

        centerPanel.add(resultPanel, BorderLayout.CENTER)

        panel.add(centerPanel, BorderLayout.CENTER)

        // Нижняя панель со статусом
        statusLabel = JLabel(" ")
        statusLabel.border = JBUI.Borders.empty(5, 10)
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    override fun createActions(): Array<Action> {
        val callAction = object : DialogWrapperAction("Call") {
            override fun doAction(e: java.awt.event.ActionEvent) {
                callMethod()
            }
        }

        return arrayOf(callAction, cancelAction)
    }

    private fun callMethod() {
        val argumentsText = argumentsField.text.trim()

        // Парсим аргументы
        val arguments: JsonElement? = try {
            if (argumentsText.isEmpty() || argumentsText == "{}") {
                null
            } else {
                json.parseToJsonElement(argumentsText)
            }
        } catch (e: Exception) {
            statusLabel.text = "Error: Invalid JSON - ${e.message}"
            statusLabel.foreground = JBUI.CurrentTheme.Link.Foreground.DISABLED
            return
        }

        // Вызываем метод
        statusLabel.text = "Calling method..."
        statusLabel.foreground = JBUI.CurrentTheme.Label.foreground()
        resultArea.text = "Executing..."

        scope.launch {
            try {
                val result = connectionManager.callMethod(serverName, method.name, arguments)

                SwingUtilities.invokeLater {
                    if (result.success) {
                        val resultJson = result.result?.let {
                            json.encodeToString(JsonElement.serializer(), it)
                        } ?: "null"

                        resultArea.text = resultJson
                        statusLabel.text = "Success (${result.executionTime}ms)"
                        statusLabel.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
                    } else {
                        resultArea.text = "Error: ${result.error}"
                        statusLabel.text = "Failed (${result.executionTime}ms)"
                        statusLabel.foreground = JBUI.CurrentTheme.Link.Foreground.DISABLED
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    resultArea.text = "Exception: ${e.message}"
                    statusLabel.text = "Error"
                    statusLabel.foreground = JBUI.CurrentTheme.Link.Foreground.DISABLED
                }
            }
        }
    }
}
