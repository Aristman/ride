package ru.marslab.ide.ride.agent.monitoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import ru.marslab.ide.ride.agent.cache.UncertaintyAnalysisCache
import java.awt.BorderLayout
import java.awt.GridLayout
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * Дашборд для мониторинга производительности EnhancedChatAgent
 *
 * Отображает:
 * - Общие метрики производительности
 * - Статистику кэширования
 * - Графики использования по времени
 * - Рекомендации по оптимизации
 * - Оповещения о проблемах
 */
class PerformanceDashboard(
    private val project: Project,
    private val performanceMonitor: PerformanceMonitor,
    private val uncertaintyCache: UncertaintyAnalysisCache
) : SimpleToolWindowPanel(false, true) {

    private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(PerformanceDashboard::class.java)

    // UI компоненты
    private val refreshButton = JButton("Обновить")
    private val resetButton = JButton("Сбросить статистику")
    private val exportButton = JButton("Экспортировать")

    // Метрики
    private val totalRequestsLabel = JBLabel("0")
    private val averageTimeLabel = JBLabel("0 мс")
    private val cacheHitRateLabel = JBLabel("0%")
    private val qualityScoreLabel = JBLabel("0.0")
    private val errorRateLabel = JBLabel("0%")

    // Статистика по сложности
    private val simpleTimeLabel = JBLabel("0 мс")
    private val mediumTimeLabel = JBLabel("0 мс")
    private val complexTimeLabel = JBLabel("0 мс")

    // Рекомендации
    private val recommendationsArea = JTextArea(8, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }

    // График (упрощенный)
    private val performanceChart = createPerformanceChart()

    // Таймер автообновления
    private val refreshTimer = Timer()

    init {
        setupUI()
        setupActions()
        startAutoRefresh()
        refreshData()
    }

    /**
     * Настраивает пользовательский интерфейс
     */
    private fun setupUI() {
        val contentPanel = JBPanel<BorderLayout>()

        // Верхняя панель с кнопками
        val topPanel = JBPanel<BorderLayout>()
        val buttonPanel = JPanel(GridLayout(1, 3, 5, 0))
        buttonPanel.add(refreshButton)
        buttonPanel.add(resetButton)
        buttonPanel.add(exportButton)
        topPanel.add(buttonPanel, BorderLayout.EAST)

        // Основная панель с метриками
        val metricsPanel = createMetricsPanel()

        // Панель с графиком
        val chartPanel = JBPanel<BorderLayout>().apply {
            border = JBUI.Borders.empty(10)
            add(JBLabel("Производительность по времени"), BorderLayout.NORTH)
            add(performanceChart, BorderLayout.CENTER)
        }

        // Панель с рекомендациями
        val recommendationsPanel = createRecommendationsPanel()

        // Размещаем компоненты
        val leftPanel = JSplitPane(JSplitPane.VERTICAL_SPLIT, metricsPanel, chartPanel).apply {
            resizeWeight = 0.4
            isContinuousLayout = true
        }

        val rightPanel = JSplitPane(JSplitPane.VERTICAL_SPLIT, recommendationsPanel, createDetailedStatsPanel()).apply {
            resizeWeight = 0.6
            isContinuousLayout = true
        }

        val mainSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
            resizeWeight = 0.6
            isContinuousLayout = true
        }

        contentPanel.add(topPanel, BorderLayout.NORTH)
        contentPanel.add(mainSplitPane, BorderLayout.CENTER)

        setContent(contentPanel)
    }

    /**
     * Создает панель с основными метриками
     */
    private fun createMetricsPanel(): JPanel {
        val panel = JPanel(GridLayout(3, 3, 10, 10))
        panel.border = JBUI.Borders.titledBorder("Основные метрики")

        // Первая строка - общие метрики
        panel.add(createMetricPanel("Всего запросов", totalRequestsLabel))
        panel.add(createMetricPanel("Среднее время", averageTimeLabel))
        panel.add(createMetricPanel("Эффективность кэша", cacheHitRateLabel))

        // Вторая строка - качество и ошибки
        panel.add(createMetricPanel("Качество ответов", qualityScoreLabel))
        panel.add(createMetricPanel("Уровень ошибок", errorRateLabel))
        panel.add(JPanel()) // Пустая ячейка для баланса

        // Третья строка - время по сложности
        panel.add(createMetricPanel("Простые (мс)", simpleTimeLabel))
        panel.add(createMetricPanel("Средние (мс)", mediumTimeLabel))
        panel.add(createMetricPanel("Сложные (мс)", complexTimeLabel))

        return panel
    }

    /**
     * Создает панель для отдельной метрики
     */
    private fun createMetricPanel(title: String, valueLabel: JBLabel): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.compound(
            JBUI.Borders.line(JBUI.CurrentTheme.ToolWindow.borderColor()),
            JBUI.Borders.empty(5)
        )

        val titleLabel = JBLabel(title).apply {
            font = font.deriveFont(font.style or java.awt.Font.BOLD, font.size - 2)
        }
        valueLabel.font = valueLabel.font.deriveFont(java.awt.Font.PLAIN, valueLabel.size + 2)

        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(valueLabel, BorderLayout.CENTER)

        return panel
    }

    /**
     * Создает панель с рекомендациями
     */
    private fun createRecommendationsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.titledBorder("Рекомендации по оптимизации")

        val scrollPane = JScrollPane(recommendationsArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    /**
     * Создает панель с детальной статистикой
     */
    private fun createDetailedStatsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.titledBorder("Детальная статистика")

        val textArea = JTextArea(15, 40).apply {
            isEditable = false
            font = font.deriveFont(java.awt.Font.MONOSPACED, font.size - 2)
        }

        val scrollPane = JScrollPane(textArea)
        panel.add(scrollPane, BorderLayout.CENTER)

        // Обновляем детальную статистику при обновлении данных
        refreshButton.addActionListener {
            textArea.text = generateDetailedStatsText()
        }

        return panel
    }

    /**
     * Создает простой график производительности
     */
    private fun createPerformanceChart(): JPanel {
        return object : JPanel() {
            override fun paintComponent(g: java.awt.Graphics) {
                super.paintComponent(g)
                drawPerformanceChart(g)
            }
        }
    }

    /**
     * Рисует график производительности
     */
    private fun drawPerformanceChart(g: java.awt.Graphics) {
        val stats = performanceMonitor.getCurrentStats()
        val width = width
        val height = height

        if (width <= 0 || height <= 0) return

        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, width, height)

        // Рисуем сетку
        g.color = java.awt.Color.LIGHT_GRAY
        for (i in 0..10) {
            val y = (height * i) / 10
            g.drawLine(0, y, width, y)
        }

        // Рисуем данные по часам
        val maxTime = stats.processingTimesByComplexity.values
            .maxOfOrNull { it.maxTime }?.toDouble() ?: 1000.0

        val hourWidth = width / 24.0
        stats.hourlyStats.toSortedMap().forEach { (hour, hourStats) ->
            val barHeight = (hourStats.averageTime / maxTime) * (height - 20)
            val x = (hour * hourWidth).toInt()
            val y = height - barHeight.toInt() - 10

            // Цвет зависит от производительности
            g.color = when {
                hourStats.averageTime < 1000 -> java.awt.Color.GREEN
                hourStats.averageTime < 3000 -> java.awt.Color.YELLOW
                else -> java.awt.Color.RED
            }

            g.fillRect(x, y, hourWidth.toInt() - 2, barHeight.toInt())

            // Подпись часа
            g.color = java.awt.Color.BLACK
            g.drawString("$hour", x, height - 2)
        }
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

        exportButton.addActionListener {
            exportStats()
        }
    }

    /**
     * Запускает автообновление
     */
    private fun startAutoRefresh() {
        refreshTimer.schedule(object : TimerTask() {
            override fun run() {
                SwingUtilities.invokeLater { refreshData() }
            }
        }, 30_000, 30_000) // Каждые 30 секунд
    }

    /**
     * Обновляет данные на дашборде
     */
    private fun refreshData() {
        try {
            val stats = performanceMonitor.getCurrentStats()
            val cacheStats = uncertaintyCache.getStats()

            // Обновляем основные метрики
            totalRequestsLabel.text = stats.totalRequests.toString()
            averageTimeLabel.text = "${stats.averageProcessingTime.toInt()} мс"
            cacheHitRateLabel.text = "${(stats.cacheStats.hitRate * 100).toInt()}%"
            qualityScoreLabel.text = String.format("%.2f", stats.qualityStats.averageScore)

            val errorRate = if (stats.totalRequests > 0) {
                (stats.errorStats.totalErrors.toDouble() / stats.totalRequests * 100)
            } else 0.0
            errorRateLabel.text = "${errorRate.toInt()}%"

            // Обновляем время по сложности
            stats.processingTimesByComplexity[ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.SIMPLE]?.let {
                simpleTimeLabel.text = "${it.averageTime.toInt()} мс"
            }

            stats.processingTimesByComplexity[ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.MEDIUM]?.let {
                mediumTimeLabel.text = "${it.averageTime.toInt()} мс"
            }

            stats.processingTimesByComplexity[ru.marslab.ide.ride.agent.analyzer.ComplexityLevel.COMPLEX]?.let {
                complexTimeLabel.text = "${it.averageTime.toInt()} мс"
            }

            // Обновляем рекомендации
            updateRecommendations(performanceMonitor.analyzePerformance())

            // Перерисовываем график
            performanceChart.repaint()

        } catch (e: Exception) {
            logger.error("Error refreshing dashboard data", e)
        }
    }

    /**
     * Обновляет панель рекомендаций
     */
    private fun updateRecommendations(recommendations: List<PerformanceRecommendation>) {
        if (recommendations.isEmpty()) {
            recommendationsArea.text = "✅ Все показатели в норме, рекомендаций нет."
            return
        }

        val text = buildString {
            recommendations.forEach { rec ->
                appendLine("🔍 ${rec.message}")
                appendLine("   💡 ${rec.suggestion}")
                appendLine("   📈 Ожидаемый эффект: ${rec.impact}")
                appendLine("   ⚡ Приоритет: ${getPriorityText(rec.priority)}")
                appendLine()
            }
        }

        recommendationsArea.text = text
    }

    /**
     * Возвращает текстовое представление приоритета
     */
    private fun getPriorityText(priority: PerformanceMonitor.RecommendationPriority): String {
        return when (priority) {
            PerformanceMonitor.RecommendationPriority.HIGH -> "Высокий"
            PerformanceMonitor.RecommendationPriority.MEDIUM -> "Средний"
            PerformanceMonitor.RecommendationPriority.LOW -> "Низкий"
        }
    }

    /**
     * Генерирует детальную статистику в текстовом формате
     */
    private fun generateDetailedStatsText(): String {
        val stats = performanceMonitor.getCurrentStats()
        val cacheStats = uncertaintyCache.getStats()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())

        return buildString {
            appendLine("=== Детальная статистика производительности ===")
            appendLine("Время обновления: $timestamp")
            appendLine()

            appendLine("--- Общие метрики ---")
            appendLine("Всего запросов: ${stats.totalRequests}")
            appendLine("Среднее время обработки: ${stats.averageProcessingTime.toInt()} мс")
            appendLine()

            appendLine("--- Метрики по сложности ---")
            stats.processingTimesByComplexity.forEach { (complexity, timeStats) ->
                appendLine("${complexity.name}:")
                appendLine("  Количество: ${timeStats.count}")
                appendLine("  Среднее время: ${timeStats.averageTime.toInt()} мс")
                appendLine("  Минимальное время: ${timeStats.minTime} мс")
                appendLine("  Максимальное время: ${timeStats.maxTime} мс")
                appendLine()
            }

            appendLine("--- Метрики кэша ---")
            appendLine("Размер кэша: ${cacheStats.size}/${cacheStats.maxSize}")
            appendLine("Hit rate: ${(cacheStats.hitRate * 100).toInt()}%")
            appendLine("Эффективность: ${(cacheStats.efficiency * 100).toInt()}%")
            appendLine("Использование: ${(cacheStats.utilization * 100).toInt()}%")
            appendLine()

            appendLine("--- Статистика ошибок ---")
            appendLine("Всего ошибок: ${stats.errorStats.totalErrors}")
            stats.errorStats.errorsByType.forEach { (type, count) ->
                appendLine("  $type: $count")
            }
            appendLine()

            appendLine("--- Почасовая статистика ---")
            stats.hourlyStats.toSortedMap().forEach { (hour, hourStats) ->
                appendLine("Час $hour: ${hourStats.requestCount} запросов, среднее время ${hourStats.averageTime.toInt()} мс")
            }
        }
    }

    /**
     * Экспортирует статистику в файл
     */
    private fun exportStats() {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
            val fileName = "ride_performance_stats_$timestamp.json"

            val fileChooser = JFileChooser().apply {
                selectedFile = java.io.File(fileName)
                dialogTitle = "Экспорт статистики"
            }

            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                val selectedFile = fileChooser.selectedFile
                selectedFile.writeText(performanceMonitor.exportStats())
                JOptionPane.showMessageDialog(
                    this,
                    "Статистика успешно экспортирована в:\n${selectedFile.absolutePath}",
                    "Экспорт завершен",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } catch (e: Exception) {
            logger.error("Error exporting stats", e)
            JOptionPane.showMessageDialog(
                this,
                "Ошибка при экспорте статистики: ${e.message}",
                "Ошибка экспорта",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * Останавливает таймер автообновления
     */
    fun dispose() {
        refreshTimer.cancel()
    }
}