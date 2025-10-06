package ru.marslab.ide.ride.ui.builder

import com.intellij.ui.components.JBTextArea
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Компоненты нижней панели
 */
data class BottomPanelComponents(
    val panel: JPanel,
    val inputArea: JBTextArea,
    val sendButton: JButton,
    val clearButton: JButton
)