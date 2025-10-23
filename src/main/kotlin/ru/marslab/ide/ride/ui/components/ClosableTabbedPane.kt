package ru.marslab.ide.ride.ui.components

import com.intellij.ui.components.JBTabbedPane
import java.awt.*
import java.awt.geom.Line2D
import javax.swing.*

class ClosableTabbedPane : JBTabbedPane() {
    interface CloseListener {
        fun onTabClose(index: Int)
    }

    var closeListener: CloseListener? = null

    fun addClosableTab(title: String, component: JPanel) {
        val idx = this.tabCount
        this.addTab(title, component)
        setTabComponentAt(idx, createTabHeader(title))
    }

    private fun createTabHeader(title: String): JComponent {
        val panel = JPanel(BorderLayout(6, 0))
        panel.isOpaque = false

        val label = JLabel(title)
        label.border = BorderFactory.createEmptyBorder(0, 6, 0, 0)

        val closeButton = JButton(CloseIcon(normal = true)).apply {
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            isOpaque = false
            margin = Insets(0, 0, 0, 0)
            preferredSize = Dimension(12, 12)
            minimumSize = Dimension(12, 12)
            maximumSize = Dimension(12, 12)
            toolTipText = "Закрыть вкладку"
            rolloverIcon = CloseIcon(normal = false)
            pressedIcon = CloseIcon(normal = false)
            addActionListener {
                val i = indexOfTabComponent(panel)
                if (i >= 0) closeListener?.onTabClose(i)
            }
        }

        val rightWrap = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            add(closeButton)
        }

        panel.add(label, BorderLayout.CENTER)
        panel.add(rightWrap, BorderLayout.EAST)
        return panel
    }

    private class CloseIcon(private val normal: Boolean) : Icon {
        private val size = 10
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            val g2 = (g as Graphics2D)
            val old = g2.stroke
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = if (normal) Color(0x9AA0A6) else Color(0x6E7378)
            g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

            val pad = 2
            val x1 = x + pad
            val y1 = y + pad
            val x2 = x + size - pad
            val y2 = y + size - pad
            g2.draw(Line2D.Float(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()))
            g2.draw(Line2D.Float(x1.toFloat(), y2.toFloat(), x2.toFloat(), y1.toFloat()))
            g2.stroke = old
        }
    }
}
