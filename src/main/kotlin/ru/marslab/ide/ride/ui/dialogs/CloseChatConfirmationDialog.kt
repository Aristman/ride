package ru.marslab.ide.ride.ui.dialogs

import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.JPanel

class CloseChatConfirmationDialog(parent: java.awt.Component?) {
    enum class Action { CLOSE, HIDE, CANCEL }
    data class Result(val action: Action)

    fun showAndGet(): Result {
        val options = arrayOf("Закрыть", "Скрыть", "Отмена")
        val choice = JOptionPane.showOptionDialog(
            parentOrNull(parent = null),
            "Что сделать с выбранной сессией?",
            "Подтверждение закрытия",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        )
        return when (choice) {
            0 -> Result(Action.CLOSE)
            1 -> Result(Action.HIDE)
            else -> Result(Action.CANCEL)
        }
    }

    private fun parentOrNull(parent: java.awt.Component?): java.awt.Component? = parent
}
