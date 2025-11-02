package ru.marslab.ide.ride.agent.monitoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import ru.marslab.ide.ride.agent.cache.UncertaintyAnalysisCache
import java.awt.BorderLayout
import javax.swing.*

/**
 * Упрощенный дашборд для мониторинга производительности EnhancedChatAgent
 */
class PerformanceDashboard(
    private val project: Project,
    private val performanceMonitor: PerformanceMonitor,
    private val uncertaintyCache: UncertaintyAnalysisCache
) : SimpleToolWindowPanel(false, true) {

    // UI компоненты
    private val refreshButton = JButton("Обновить")
    private val resetButton = JButton("Сбросить статистику")

    // Метрики
    private val totalRequestsLabel = JBLabel("0")
    private val averageTimeLabel = JBLabel("0 мс")
    private val cacheHitRateLabel = JBLabel("0%")

    init {
        setupUI()
        setupActions()
        refreshData()
    }

    /**
     * Настраивает пользовательский интерфейс
     */
    private fun setupUI() {
        val contentPanel = JPanel(BorderLayout())

        // Верхняя панель с кнопками
        val topPanel = JPanel(BorderLayout())
        val buttonPanel = JPanel()
        buttonPanel.add(refreshButton)
        buttonPanel.add(resetButton)
        topPanel.add(buttonPanel, BorderLayout.EAST)

        // Основная панель с метриками
        val metricsPanel = createMetricsPanel()

        contentPanel.add(topPanel, BorderLayout.NORTH)
        contentPanel.add(metricsPanel, BorderLayout.CENTER)

        setContent(contentPanel)
    }

    /**
     * Создает панель с основными метриками
     */
    private fun createMetricsPanel(): JPanel {
        val panel = JPanel()
        panel.layout = java.awt.GridLayout(3, 2, 10, 10)
        panel.border = javax.swing.border.TitledBorder("Основные метрики")

        panel.add(JBLabel("Всего запросов:"))
        panel.add(totalRequestsLabel)
        panel.add(JBLabel("Среднее время:"))
        panel.add(averageTimeLabel)
        panel.add(JBLabel("Эффективность кэша:"))
        panel.add(cacheHitRateLabel)

        return panel
    }

    /**
     * Настраивает обработчики событий
     */
    private fun setupActions() {
        refreshButton.addActionListener {
            refreshData()
        }

        resetButton.addActionListener {
            val option = JOptionPane.showConfirmDialog(
                this,
                "Вы уверены, что хотите сбросить всю статистику?",
                "Сброс статистики",
                JOptionPane.YES_NO_OPTION
            )

            if (option == JOptionPane.YES_OPTION) {
                performanceMonitor.reset()
                uncertaintyCache.clear()
                refreshData()
                JOptionPane.showMessageDialog(this, "Статистика успешно сброшена")
            }
        }
    }

    /**
     * Обновляет данные на дашборде
     */
    private fun refreshData() {
        try {
            val stats = performanceMonitor.getCurrentStats()

            // Обновляем основные метрики
            totalRequestsLabel.text = stats.totalRequests.toString()
            averageTimeLabel.text = "${stats.averageProcessingTime.toInt()} мс"
            cacheHitRateLabel.text = "${(stats.cacheStats.hitRate * 100).toInt()}%"

        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Ошибка при обновлении данных: ${e.message}", "Ошибка", JOptionPane.ERROR_MESSAGE)
        }
    }

    /**
     * Останавливает ресурсы
     */
    fun dispose() {
        // Нет специфических ресурсов для очистки
    }
}