package ru.marslab.ide.ride.formatter

import ru.marslab.ide.ride.model.agent.FormattedOutput
import ru.marslab.ide.ride.model.agent.FormattedOutputBlock

/**
 * Форматтер для вывода терминальных команд
 */
class TerminalOutputFormatter {

    /**
     * Форматирует результат выполнения терминальной команды в FormattedOutput
     */
    fun formatAsHtml(
        command: String,
        exitCode: Int,
        executionTime: Long,
        stdout: String,
        stderr: String,
        success: Boolean = exitCode == 0
    ): FormattedOutput {
        val terminalBlock = FormattedOutputBlock.terminal(
            content = buildTerminalContent(stdout, stderr),
            command = command,
            exitCode = exitCode,
            executionTime = executionTime,
            success = success,
            order = 0
        )

        return FormattedOutput.single(terminalBlock)
    }

  
    /**
     * Создает содержимое для терминального блока
     */
    private fun buildTerminalContent(stdout: String, stderr: String): String {
        return buildString {
            if (stdout.isNotEmpty()) {
                appendLine(stdout)
            }

            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine(stderr)
            }

            if (stdout.isEmpty() && stderr.isEmpty()) {
                append("(No output)")
            }
        }
    }
}