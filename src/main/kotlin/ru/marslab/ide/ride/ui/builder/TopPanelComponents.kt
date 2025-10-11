package ru.marslab.ide.ride.ui.builder

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.ui.components.JBTabbedPane
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Компоненты верхней панели
 */
data class TopPanelComponents(
    val panel: JPanel,
    val toolbar: ActionToolbar,
    val sessionsTabs: JBTabbedPane,
    val contextSizeLabel: JLabel
)