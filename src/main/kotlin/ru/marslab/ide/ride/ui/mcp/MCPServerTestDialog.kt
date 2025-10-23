package ru.marslab.ide.ride.ui.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionListener
import javax.swing.*

/**
 * Диалог для отображения прогресса тестирования MCP серверов в реальном времени
 */
class MCPServerTestDialog(
    private val project: Project,
    private val servers: List<MCPServerConfig>,
    private val connectionManager: MCPConnectionManager,
    private val onRefreshCallback: (() -> Unit)? = null
) : JDialog() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val resultArea = JTextArea()
    private val progressBar = JProgressBar()
    private val statusLabel = JBLabel("Testing servers...")
    private val closeButton = JButton("Close")
    private var testingCompleted = false

    init {
        title = "Testing MCP Servers"
        isModal = false
        setSize(600, 400)
        setLocationRelativeTo(null)
        defaultCloseOperation = DISPOSE_ON_CLOSE

        setupUI()
        startTesting()
    }

    private fun setupUI() {
        layout = BorderLayout()

        // Верхняя панель со статусом и прогрессом
        val topPanel = JPanel(BorderLayout())
        topPanel.border = JBUI.Borders.empty(10)

        statusLabel.border = JBUI.Borders.emptyBottom(5)
        topPanel.add(statusLabel, BorderLayout.NORTH)

        progressBar.isIndeterminate = true
        progressBar.border = JBUI.Borders.emptyBottom(10)
        topPanel.add(progressBar, BorderLayout.CENTER)

        add(topPanel, BorderLayout.NORTH)

        // Область с результатами
        resultArea.isEditable = false
        resultArea.font = resultArea.font.deriveFont(12f)
        resultArea.border = JBUI.Borders.empty(10)

        val scrollPane = JBScrollPane(resultArea)
        scrollPane.preferredSize = Dimension(580, 300)
        add(scrollPane, BorderLayout.CENTER)

        // Нижняя панель с кнопкой
        val bottomPanel = JPanel()
        bottomPanel.border = JBUI.Borders.empty(10)

        closeButton.isEnabled = false
        closeButton.addActionListener {
            dispose()
        }
        bottomPanel.add(closeButton)

        add(bottomPanel, BorderLayout.SOUTH)

        // Добавляем начальный текст
        resultArea.text = "Starting server tests...\n\n"
    }

    private fun startTesting() {
        scope.launch {
            var successCount = 0
            var failCount = 0

            servers.forEachIndexed { index, server ->
                // Обновляем статус
                SwingUtilities.invokeLater {
                    statusLabel.text = "Testing ${server.name} (${index + 1}/${servers.size})"
                    appendResult("Testing ${server.name}...\n")
                }

                // Тестируем сервер
                try {
                    val success = connectionManager.connectServer(server)
                    if (success) {
                        successCount++
                        SwingUtilities.invokeLater {
                            appendResult("✓ ${server.name}: Connected\n")
                            // Вызываем callback для обновления статусов в таблице
                            onRefreshCallback?.invoke()
                        }
                    } else {
                        failCount++
                        SwingUtilities.invokeLater {
                            appendResult("✗ ${server.name}: Failed to connect\n")
                            // Вызываем callback для обновления статусов в таблице
                            onRefreshCallback?.invoke()
                        }
                    }
                } catch (e: Exception) {
                    failCount++
                    SwingUtilities.invokeLater {
                        appendResult("✗ ${server.name}: Error - ${e.message}\n")
                        // Вызываем callback для обновления статусов в таблице
                        onRefreshCallback?.invoke()
                    }
                }

                // Небольшая задержка для визуального эффекта
                delay(300)
            }

            // Завершение тестирования
            SwingUtilities.invokeLater {
                testingCompleted = true
                progressBar.isVisible = false
                statusLabel.text = "Testing completed"
                closeButton.isEnabled = true

                appendResult("\n" + "=".repeat(50) + "\n")
                appendResult("Test Results:\n")
                appendResult("✓ Success: $successCount servers\n")
                appendResult("✗ Failed: $failCount servers\n")

                if (failCount == 0) {
                    appendResult("\nAll servers connected successfully!\n")
                } else {
                    appendResult("\nSome servers failed to connect.\n")
                }

                // Прокручиваем к концу
                resultArea.caretPosition = resultArea.document.length

                // Автоматически закрываем диалог через 2 секунды
                Timer(2000, ActionListener {
                    dispose()
                }).start()
            }
        }
    }

    private fun appendResult(text: String) {
        resultArea.append(text)
        resultArea.caretPosition = resultArea.document.length
    }

    override fun dispose() {
        super.dispose()
        scope.cancel()
    }

    companion object {
        fun showAndTest(project: Project, servers: List<MCPServerConfig>, connectionManager: MCPConnectionManager) {
            if (servers.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "No servers configured to test",
                    "Connection Test"
                )
                return
            }

            val dialog = MCPServerTestDialog(project, servers, connectionManager)
            dialog.isVisible = true
        }

        fun showAndTestWithRefresh(
            project: Project,
            servers: List<MCPServerConfig>,
            connectionManager: MCPConnectionManager,
            onRefresh: () -> Unit
        ) {
            if (servers.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "No servers configured to test",
                    "Connection Test"
                )
                return
            }

            val dialog = MCPServerTestDialog(project, servers, connectionManager, onRefresh)
            dialog.isVisible = true
        }
    }
}