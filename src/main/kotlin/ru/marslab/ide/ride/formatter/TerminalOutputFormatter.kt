package ru.marslab.ide.ride.formatter

import ru.marslab.ide.ride.model.agent.AgentOutputType
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
                appendLine("STDOUT:")
                appendLine(stdout)
            }

            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("STDERR:")
                appendLine(stderr)
            }

            if (stdout.isEmpty() && stderr.isEmpty()) {
                append("(No output)")
            }
        }
    }

    /**
     * Экранирует HTML-символы
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    /**
     * Создает блок с простым текстовым выводом (fallback)
     */
    fun formatAsText(
        command: String,
        exitCode: Int,
        executionTime: Long,
        stdout: String,
        stderr: String,
        success: Boolean = exitCode == 0
    ): FormattedOutput {
        val content = buildString {
            appendLine("Command: $command")
            appendLine("Exit Code: $exitCode")
            appendLine("Execution Time: ${executionTime}ms")
            appendLine("Status: ${if (success) "Success ✅" else "Error ❌"}")
            appendLine()

            if (stdout.isNotEmpty()) {
                appendLine("STDOUT:")
                appendLine(stdout)
                appendLine()
            }

            if (stderr.isNotEmpty()) {
                appendLine("STDERR:")
                appendLine(stderr)
                appendLine()
            }

            if (stdout.isEmpty() && stderr.isEmpty()) {
                appendLine("(No output)")
            }
        }

        return FormattedOutput.markdown(content)
    }

    /**
     * Создает блок для ошибки выполнения команды
     */
    fun formatError(
        command: String,
        errorMessage: String,
        executionTime: Long? = null
    ): FormattedOutput {
        val content = buildString {
            appendLine("Command: $command")
            if (executionTime != null) {
                appendLine("Execution Time: ${executionTime}ms")
            }
            appendLine("Status: Error ❌")
            appendLine()
            appendLine("Error:")
            appendLine(errorMessage)
        }

        return FormattedOutput.markdown(content)
    }
}