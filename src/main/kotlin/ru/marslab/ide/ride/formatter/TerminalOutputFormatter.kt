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
     * Создает HTML-представление терминального окна
     */
    fun createTerminalWindow(
        command: String,
        exitCode: Int,
        executionTime: Long,
        stdout: String,
        stderr: String,
        success: Boolean = exitCode == 0
    ): String {
        return buildString {
            appendLine("<div class=\"terminal-output\">")
            appendLine("  <div class=\"terminal-header\">")
            appendLine("    <div class=\"terminal-title\">")
            appendLine("      <span class=\"terminal-icon\">🖥️</span>")
            appendLine("      <span class=\"terminal-text\">Terminal Output</span>")
            appendLine("    </div>")
            appendLine("    <div class=\"terminal-status\">")
            if (success) {
                appendLine("      <span class=\"status-success\">✅ Success</span>")
            } else {
                appendLine("      <span class=\"status-error\">❌ Error</span>")
            }
            appendLine("    </div>")
            appendLine("  </div>")

            appendLine("  <div class=\"terminal-info\">")
            appendLine("    <div class=\"terminal-command\">")
            appendLine("      <span class=\"command-label\">Command:</span>")
            appendLine("      <span class=\"command-value\">${escapeHtml(command)}</span>")
            appendLine("    </div>")
            appendLine("    <div class=\"terminal-exit-code\">")
            appendLine("      <span class=\"exit-code-label\">Exit Code:</span>")
            appendLine("      <span class=\"exit-code-value\">$exitCode</span>")
            appendLine("    </div>")
            appendLine("    <div class=\"terminal-execution-time\">")
            appendLine("      <span class=\"execution-time-label\">Execution Time:</span>")
            appendLine("      <span class=\"execution-time-value\">${executionTime}ms</span>")
            appendLine("    </div>")
            appendLine("  </div>")

            if (stdout.isNotEmpty()) {
                appendLine("  <div class=\"terminal-stdout\">")
                appendLine("    <pre class=\"terminal-content\">${escapeHtml(stdout)}</pre>")
                appendLine("  </div>")
            }

            if (stderr.isNotEmpty()) {
                appendLine("  <div class=\"terminal-stderr\">")
                appendLine("    <pre class=\"terminal-content error\">${escapeHtml(stderr)}</pre>")
                appendLine("  </div>")
            }

            if (stdout.isEmpty() && stderr.isEmpty()) {
                appendLine("  <div class=\"terminal-output-content\">")
                appendLine("    <pre class=\"terminal-content\">(No output)</pre>")
                appendLine("  </div>")
            }

            appendLine("</div>")
        }
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