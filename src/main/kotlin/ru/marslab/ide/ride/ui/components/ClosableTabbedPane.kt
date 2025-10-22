package ru.marslab.ide.ride.ui.components

import com.intellij.ui.components.JBTabbedPane
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class ClosableTabbedPane : JBTabbedPane() {
    interface CloseListener {
        fun onTabClose(index: Int)
    }

    var closeListener: CloseListener? = null

    fun addClosableTab(title: String, component: JPanel) {
        val idx = this.tabCount
        this.addTab(title, component)
        setTabComponentAt(idx, createTabHeader(title, idx))
    }

    private fun createTabHeader(title: String, index: Int): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        val label = JLabel(title)
        val btn = JButton("×")
        btn.margin = java.awt.Insets(0, 5, 0, 5)
        btn.addActionListener {
            val i = indexOfTabComponent(panel)
            if (i >= 0) closeListener?.onTabClose(i)
        }
        panel.isOpaque = false
        panel.add(label)
        panel.add(btn)
        return panel
    }
}
