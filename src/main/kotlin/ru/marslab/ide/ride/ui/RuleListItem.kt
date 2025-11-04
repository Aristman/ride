package ru.marslab.ide.ride.ui

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Элемент списка для отображения правила с чекбоксом активации и кнопкой удаления
 */
class RuleListItem(
    private val fileName: String,
    private var isActive: Boolean,
    private val isGlobal: Boolean,
    private val onDelete: (String) -> Unit,
    private val onToggleActive: (String, Boolean) -> Unit,
    private val onOpenFile: (String, Boolean) -> Unit
) : JPanel() {

    private val checkBox: JBCheckBox = JBCheckBox().apply {
        isSelected = isActive
        addActionListener {
            onToggleActive(fileName, isSelected)
        }
    }

    private val deleteButton: JButton = JButton("-").apply {
        addActionListener {
            onDelete(fileName)
        }
    }

    private val label: JBLabel = JBLabel(fileName).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    onOpenFile(fileName, isGlobal)
                }
            }
        })
    }

    init {
        layout = BorderLayout(5, 0)
        border = JBUI.Borders.empty(2)

        // Панель с чекбоксом и кнопкой удаления
        val controlsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            add(checkBox)
            add(label)
            add(deleteButton)
        }

        add(controlsPanel, BorderLayout.WEST)
//        add(label, BorderLayout.CENTER)
    }

    fun setActive(active: Boolean) {
        isActive = active
        checkBox.isSelected = active
    }

    fun getFileName(): String = fileName
    fun isRuleActive(): Boolean = isActive
}