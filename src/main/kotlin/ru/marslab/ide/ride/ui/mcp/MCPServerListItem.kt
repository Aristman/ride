package ru.marslab.ide.ride.ui.mcp

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import ru.marslab.ide.ride.model.mcp.MCPMethod
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPServerStatus
import ru.marslab.ide.ride.service.mcp.MCPConnectionManager
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Компонент для отображения одного MCP сервера в виде списка
 */
class MCPServerListItem(
    private val project: Project,
    private val server: MCPServerConfig,
    private val connectionManager: MCPConnectionManager,
    private val onRefreshComplete: () -> Unit,
    private val onEditServer: (MCPServerConfig) -> Unit
) : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Компоненты UI
    private val statusIcon = JBLabel()
    private val nameLabel = JBLabel()
    private val typeLabel = JBLabel()
    private val refreshButton = JButton(AllIcons.Actions.Refresh)
    private val editButton = JButton(AllIcons.Actions.Edit)
    private val expandButton = JButton(AllIcons.General.ArrowDown)
    private val methodsPanel = JPanel()
    private val methodsLayout = CardLayout()
    private val methodsListPanel = JPanel()
    private lateinit var methodsScrollPane: JScrollPane

    // Состояние
    private var currentStatus: MCPServerStatus? = null
    private var isExpanded = false
    private var isRefreshing = false

    init {
        setupUI()
        setupActions()
        updateServerInfo()
        refreshStatus()
    }

    private fun setupUI() {
        border = JBUI.Borders.compound(
            JBUI.Borders.empty(5),
            JBUI.Borders.customLine(JBUI.CurrentTheme.Label.foreground(), 1),
            JBUI.Borders.empty(10)
        )

        // Основная панель с информацией о сервере
        val headerPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        // Статус (кружок)
        statusIcon.preferredSize = Dimension(16, 16)
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(2, 0, 2, 8)
        headerPanel.add(statusIcon, gbc)

        // Кнопка раскрытия (рядом с названием)
        expandButton.border = JBUI.Borders.empty(2)
        expandButton.isContentAreaFilled = false
        expandButton.isVisible = false // По умолчанию скрыта
        expandButton.preferredSize = Dimension(16, 16)
        gbc.gridx = 1
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(2, 0, 2, 4)
        headerPanel.add(expandButton, gbc)

        // Название сервера
        nameLabel.font = nameLabel.font.deriveFont(nameLabel.font.style or java.awt.Font.BOLD)
        gbc.gridx = 2
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(2, 0, 2, 8)
        headerPanel.add(nameLabel, gbc)

        // Тип сервера
        typeLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
        gbc.gridx = 3
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.insets = JBUI.insets(2, 0, 2, 8)
        headerPanel.add(typeLabel, gbc)

        // Кнопка рефреш
        refreshButton.border = JBUI.Borders.empty(4)
        refreshButton.toolTipText = "Refresh server connection"
        refreshButton.isContentAreaFilled = false
        gbc.gridx = 4
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.EAST
        gbc.insets = JBUI.insets(2, 0, 2, 4)
        headerPanel.add(refreshButton, gbc)

        // Кнопка редактирования
        editButton.border = JBUI.Borders.empty(4)
        editButton.toolTipText = "Edit server"
        editButton.isContentAreaFilled = false
        gbc.gridx = 5
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.EAST
        gbc.insets = JBUI.insets(2, 0, 2, 0)
        headerPanel.add(editButton, gbc)

        add(headerPanel, BorderLayout.NORTH)

        // Панель для методов (скрыта по умолчанию)
        methodsPanel.layout = methodsLayout
        methodsPanel.border = JBUI.Borders.emptyLeft(24)

        val emptyPanel = JPanel()
        methodsPanel.add(emptyPanel, "empty")

        methodsListPanel.layout = BoxLayout(methodsListPanel, BoxLayout.Y_AXIS)
        methodsScrollPane = JScrollPane(methodsListPanel)
        methodsScrollPane.preferredSize = Dimension(0, 200)
        methodsScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        methodsScrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        methodsPanel.add(methodsScrollPane, "methods")

        add(methodsPanel, BorderLayout.CENTER)
        methodsPanel.isVisible = false
    }

    private fun setupActions() {
        // Кнопка рефреш
        refreshButton.addActionListener {
            refreshServer()
        }

        // Кнопка редактирования
        editButton.addActionListener {
            onEditServer(server)
        }

        // Кнопка раскрытия списка методов
        expandButton.addActionListener {
            toggleMethodsList()
        }

        // Клик по всей карточке раскрывает список тулзов
        val clickListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1 && currentStatus?.hasMethods() == true) {
                    toggleMethodsList()
                }
            }
        }

        // Добавляем слушатель на всю панель
        addMouseListener(clickListener)
        nameLabel.addMouseListener(clickListener)
        typeLabel.addMouseListener(clickListener)
        statusIcon.addMouseListener(clickListener)

        // Изменение курсора при наведении
        val cursorListener = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                if (currentStatus?.hasMethods() == true) {
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                }
            }

            override fun mouseExited(e: MouseEvent) {
                cursor = Cursor.getDefaultCursor()
            }
        }

        addMouseListener(cursorListener)
        nameLabel.addMouseListener(cursorListener)
        typeLabel.addMouseListener(cursorListener)
    }

    private fun updateServerInfo() {
        nameLabel.text = server.name
        typeLabel.text = "[${server.type.name}]"
    }

    fun refreshStatus() {
        // getServerStatus - синхронная функция, выполняем в UI потоке
        currentStatus = connectionManager.getServerStatus(server.name)
        println("[MCPServerListItem] Refreshed status for ${server.name}: connected=${currentStatus?.connected}, methods=${currentStatus?.methods?.size}")
        updateStatusDisplay()
    }

    private fun updateStatusDisplay() {
        println("[MCPServerListItem] updateStatusDisplay for ${server.name}: currentStatus=$currentStatus")
        when {
            currentStatus == null -> {
                println("[MCPServerListItem] Status is null for ${server.name}")
                statusIcon.icon = AllIcons.General.QuestionDialog
                statusIcon.toolTipText = "Status unknown"
                expandButton.isVisible = false
                methodsPanel.isVisible = false
                isExpanded = false
                typeLabel.text = "[${server.type.name}]"
            }
            currentStatus!!.connected -> {
                println("[MCPServerListItem] Status is connected for ${server.name}, methods: ${currentStatus!!.methods.size}")
                statusIcon.icon = AllIcons.General.InspectionsOK
                statusIcon.toolTipText = "Connected (${currentStatus!!.methods.size} methods)"
                expandButton.isVisible = currentStatus!!.hasMethods()
                if (!currentStatus!!.hasMethods()) {
                    methodsPanel.isVisible = false
                    isExpanded = false
                } else {
                    // Ensure expand button is visible and properly styled when there are methods
                    expandButton.isVisible = true
                }
                // Показываем реальное количество тулзов при подключении
                val count = currentStatus!!.methods.size
                nameLabel.text = "${server.name} - $count tools"
//                typeLabel.text = "(${count}) [${server.type.name}]"
            }
            currentStatus!!.hasError() -> {
                println("[MCPServerListItem] Status has error for ${server.name}: ${currentStatus!!.error}")
                statusIcon.icon = AllIcons.General.Error
                statusIcon.toolTipText = "Error: ${currentStatus!!.error}"
                expandButton.isVisible = false
                methodsPanel.isVisible = false
                isExpanded = false
                typeLabel.text = "[${server.type.name}]"
            }
            else -> {
                println("[MCPServerListItem] Status is disconnected for ${server.name}")
                statusIcon.icon = AllIcons.General.Warning
                statusIcon.toolTipText = "Disconnected"
                expandButton.isVisible = false
                methodsPanel.isVisible = false
                isExpanded = false
                typeLabel.text = "[${server.type.name}]"
            }
        }
        updateExpandButtonIcon()

        // Принудительно обновляем UI
        statusIcon.revalidate()
        statusIcon.repaint()
        revalidate()
        repaint()
    }

    private fun refreshServer() {
        if (isRefreshing) return

        isRefreshing = true
        refreshButton.icon = AllIcons.Process.Step_1
        refreshButton.toolTipText = "Refreshing..."

        println("[MCPServerListItem] Starting refresh for server: ${server.name}")

        scope.launch(Dispatchers.IO) {
            try {
                // Синхронизируем сервер - это обновит статус в БД
                println("[MCPServerListItem] Connecting to server: ${server.name}")
                val success = connectionManager.connectServer(server)
                println("[MCPServerListItem] Connection result for ${server.name}: $success")

                // Получаем обновленный статус из БД
                val updatedStatus = connectionManager.getServerStatus(server.name)
                println("[MCPServerListItem] Updated status for ${server.name}: connected=${updatedStatus?.connected}, methods=${updatedStatus?.methods?.size}")
                println("[MCPServerListItem] Switching to UI thread...")

                SwingUtilities.invokeLater {
                    println("[MCPServerListItem] In UI thread, updating UI...")
                    currentStatus = updatedStatus
                    // Обновляем отображение на основе данных из БД
                    updateStatusDisplay()
                    onRefreshComplete()
                    println("[MCPServerListItem] UI update completed")
                }
            } catch (e: Exception) {
                println("[MCPServerListItem] Error refreshing server ${server.name}: ${e.message}")
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    // Получаем статус из БД (там может быть ошибка)
                    currentStatus = connectionManager.getServerStatus(server.name)
                    updateStatusDisplay()
                    onRefreshComplete()
                }
            } finally {
                SwingUtilities.invokeLater {
                    isRefreshing = false
                    refreshButton.icon = AllIcons.Actions.Refresh
                    refreshButton.toolTipText = "Refresh server connection"
                }
            }
        }
    }

    private fun toggleMethodsList() {
        println("[MCPServerListItem] toggleMethodsList called for ${server.name}")
        println("[MCPServerListItem] currentStatus: $currentStatus")
        println("[MCPServerListItem] connected: ${currentStatus?.connected}, hasMethods: ${currentStatus?.hasMethods()}")
        
        if (currentStatus?.connected != true || !currentStatus!!.hasMethods()) {
            println("[MCPServerListItem] Cannot toggle - not connected or no methods")
            return
        }

        isExpanded = !isExpanded
        println("[MCPServerListItem] isExpanded: $isExpanded")
        updateExpandButtonIcon()

        if (isExpanded) {
            showMethods()
        } else {
            methodsPanel.isVisible = false
        }
    }

    private fun updateExpandButtonIcon() {
        expandButton.icon = if (isExpanded) {
            AllIcons.General.ArrowUp
        } else {
            AllIcons.General.ArrowDown
        }
    }

    private fun showMethods() {
        println("[MCPServerListItem] showMethods called for ${server.name}")
        println("[MCPServerListItem] methods count: ${currentStatus?.methods?.size}")
        
        methodsListPanel.removeAll()

        currentStatus?.methods?.forEachIndexed { index, method ->
            println("[MCPServerListItem] Adding method #$index: ${method.name}")
            
            val methodPanel = JPanel(BorderLayout())
            methodPanel.border = JBUI.Borders.empty(2, 4)
            methodPanel.background = null // Прозрачный фон

            // Показываем только имя тулзы
            val nameLabel = JBLabel(method.name)
            nameLabel.font = nameLabel.font.deriveFont(11f)
            nameLabel.foreground = JBUI.CurrentTheme.Label.foreground()
            methodPanel.add(nameLabel, BorderLayout.WEST)
            
            // Устанавливаем размеры после добавления компонентов
            val preferredHeight = nameLabel.preferredSize.height + 4
            methodPanel.preferredSize = Dimension(Int.MAX_VALUE, preferredHeight)
            methodPanel.maximumSize = Dimension(Int.MAX_VALUE, preferredHeight)

            methodsListPanel.add(methodPanel)
            methodsListPanel.add(Box.createVerticalStrut(2))
        }

        println("[MCPServerListItem] methodsListPanel component count: ${methodsListPanel.componentCount}")
        println("[MCPServerListItem] Showing methods panel...")
        
        methodsLayout.show(methodsPanel, "methods")
        methodsPanel.isVisible = true
        
        println("[MCPServerListItem] methodsPanel visible: ${methodsPanel.isVisible}")
        
        // Обновляем размеры и перерисовываем
        methodsListPanel.revalidate()
        methodsListPanel.repaint()
        methodsPanel.revalidate()
        methodsPanel.repaint()
        revalidate()
        repaint()
        
        // Обновляем родительскую панель
        parent?.revalidate()
        parent?.repaint()
    }

    fun dispose() {
        scope.cancel()
    }
}