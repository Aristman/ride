package ru.marslab.ide.ride.model.chat

import kotlinx.datetime.Instant
import java.util.*

/**
 * Статус выполнения tool agent
 */
enum class ToolAgentExecutionStatus {
    PENDING,        // Ожидает выполнения
    RUNNING,        // Выполняется
    COMPLETED,      // Завершен успешно
    FAILED,         // Завершен с ошибкой
    CANCELLED       // Отменен
}

/**
 * Информация о результате выполнения tool agent
 */
data class ToolAgentResult(
    val stepId: String,
    val agentType: String,
    val agentName: String,
    val status: ToolAgentExecutionStatus,
    val startTime: Instant,
    val endTime: Instant? = null,
    val output: Map<String, Any>? = null,
    val metadata: Map<String, Any>? = null,
    val error: String? = null,
    val executionTimeMs: Long? = null
) {
    val isCompleted: Boolean
        get() = status in setOf(
            ToolAgentExecutionStatus.COMPLETED,
            ToolAgentExecutionStatus.FAILED,
            ToolAgentExecutionStatus.CANCELLED
        )

    val hasOutput: Boolean
        get() = !output.isNullOrEmpty()

    val isSuccessful: Boolean
        get() = status == ToolAgentExecutionStatus.COMPLETED
}

/**
 * Сообщение о статусе tool agent для отображения в чате
 */
data class ToolAgentStatusMessage(
    val id: String = UUID.randomUUID().toString(),
    val result: ToolAgentResult,
    val displayMessage: String,
    val isProgressMessage: Boolean = !result.isCompleted,
    val isCollapsible: Boolean = result.hasOutput,
    val isExpanded: Boolean = result.isCompleted // Развернуты по умолчанию для завершенных
) {
    /**
     * Генерирует HTML для отображения в чате
     */
    fun toHtml(): String {
        val statusClass = when (result.status) {
            ToolAgentExecutionStatus.PENDING -> "pending"
            ToolAgentExecutionStatus.RUNNING -> "running"
            ToolAgentExecutionStatus.COMPLETED -> "completed"
            ToolAgentExecutionStatus.FAILED -> "failed"
            ToolAgentExecutionStatus.CANCELLED -> "cancelled"
        }

        val statusIcon = when (result.status) {
            ToolAgentExecutionStatus.PENDING -> "⏳"
            ToolAgentExecutionStatus.RUNNING -> "🔄"
            ToolAgentExecutionStatus.COMPLETED -> "✅"
            ToolAgentExecutionStatus.FAILED -> "❌"
            ToolAgentExecutionStatus.CANCELLED -> "⏹️"
        }

        val outputHtml = if (result.hasOutput && result.isCompleted) {
            val markdownContent = formatOutputForDisplay(result.output ?: emptyMap())
            val htmlContent = convertMarkdownToHtml(markdownContent)
            """
            <div class="tool-agent-output ${if (isExpanded) "expanded" else "collapsed"}" id="output-${id}">
                <div class="output-header" data-output-id="output-${id}">
                    <span class="toggle-icon">${if (isExpanded) "▼" else "▶"}</span>
                    <span class="output-title">Результат выполнения (клик для разворачивания)</span>
                    <span class="output-size">${markdownContent.length} символов</span>
                </div>
                <div class="output-content" style="${if (isExpanded) "display:block" else "display:none"}">
                    <div class="findings-content">$htmlContent</div>
                </div>
            </div>
            """.trimIndent()
        } else ""

        val executionTime = result.executionTimeMs?.let { "${it}мс" } ?: ""

        return """
            <div class="tool-agent-status $statusClass" id="status-${id}">
                <div class="status-header">
                    <span class="status-icon">$statusIcon</span>
                    <span class="status-text">$displayMessage</span>
                    <span class="status-details">$executionTime</span>
                </div>
                $outputHtml
                ${result.error?.let { "<div class=\"error-message\">Ошибка: $it</div>" } ?: ""}
            </div>
        """.trimIndent()
    }

    private fun convertMarkdownToHtml(markdown: String): String {
        return markdown
            // Заголовки
            .replace(Regex("^### (.+)$", RegexOption.MULTILINE)) { "<h3>${it.groupValues[1]}</h3>" }
            .replace(Regex("^## (.+)$", RegexOption.MULTILINE)) { "<h2>${it.groupValues[1]}</h2>" }
            .replace(Regex("^# (.+)$", RegexOption.MULTILINE)) { "<h1>${it.groupValues[1]}</h1>" }
            // Жирный текст
            .replace(Regex("\\*\\*(.+?)\\*\\*")) { "<strong>${it.groupValues[1]}</strong>" }
            // Курсив
            .replace(Regex("\\*(.+?)\\*")) { "<em>${it.groupValues[1]}</em>" }
            // Инлайн код
            .replace(Regex("`(.+?)`")) { "<code>${it.groupValues[1]}</code>" }
            // Горизонтальная линия
            .replace(Regex("^---$", RegexOption.MULTILINE), "<hr>")
            // Переносы строк
            .replace("\n\n", "</p><p>")
            .let { "<p>$it</p>" }
            // Убираем пустые параграфы
            .replace("<p></p>", "")
            .replace("<p><h", "<h")
            .replace("</h3></p>", "</h3>")
            .replace("</h2></p>", "</h2>")
            .replace("</h1></p>", "</h1>")
            .replace("<p><hr></p>", "<hr>")
    }

    private fun formatOutputForDisplay(output: Map<String, Any>): String {
        // Извлекаем findings из output
        val findings = output["findings"] as? List<*> ?: return "Нет результатов"

        if (findings.isEmpty()) {
            return "Проблем не обнаружено"
        }

        // Форматируем findings как markdown
        return buildString {
            findings.forEachIndexed { index, finding ->
                when (finding) {
                    is Map<*, *> -> {
                        val findingMap = finding as Map<String, Any>

                        // Заголовок с номером
                        appendLine("### ${index + 1}. ${findingMap["message"] ?: findingMap["description"] ?: "Проблема"}")
                        appendLine()

                        // Severity
                        findingMap["severity"]?.let { severity ->
                            val severityEmoji = when (severity.toString().uppercase()) {
                                "CRITICAL" -> "🔴"
                                "HIGH" -> "🟠"
                                "MEDIUM" -> "🟡"
                                "LOW" -> "🟢"
                                else -> "⚪"
                            }
                            appendLine("**Уровень:** $severityEmoji $severity")
                        }

                        // File and line
                        findingMap["file"]?.let { file ->
                            val line = findingMap["line"]
                            if (line != null && line != "null") {
                                appendLine("**Файл:** `$file:$line`")
                            } else {
                                appendLine("**Файл:** `$file`")
                            }
                        }

                        // Rule/Type
                        findingMap["rule"]?.let { rule ->
                            appendLine("**Правило:** `$rule`")
                        }
                        findingMap["type"]?.let { type ->
                            appendLine("**Тип:** `$type`")
                        }

                        // Suggestion
                        findingMap["suggestion"]?.let { suggestion ->
                            appendLine()
                            appendLine("**Рекомендация:** $suggestion")
                        }

                        // Description (для архитектурных проблем)
                        findingMap["description"]?.let { description ->
                            if (findingMap["message"] == null) {
                                appendLine()
                                appendLine("$description")
                            }
                        }

                        // Modules (для циклических зависимостей)
                        findingMap["modules"]?.let { modules ->
                            appendLine()
                            appendLine("**Модули:** ${modules}")
                        }

                        appendLine()
                        appendLine("---")
                        appendLine()
                    }

                    else -> {
                        appendLine("- $finding")
                        appendLine()
                    }
                }
            }
        }.trim()
    }
}

/**
 * Менеджер статусов tool agents для отслеживания и обновления
 */
class ToolAgentStatusManager {
    private val activeStatuses = mutableMapOf<String, ToolAgentStatusMessage>()

    /**
     * Создает новое сообщение о статусе
     */
    fun createStatusMessage(
        stepId: String,
        agentType: String,
        agentName: String,
        displayMessage: String,
        status: ToolAgentExecutionStatus = ToolAgentExecutionStatus.PENDING
    ): ToolAgentStatusMessage {
        val now = kotlinx.datetime.Clock.System.now()
        val result = ToolAgentResult(
            stepId = stepId,
            agentType = agentType,
            agentName = agentName,
            status = status,
            startTime = now
        )

        val message = ToolAgentStatusMessage(
            result = result,
            displayMessage = displayMessage,
            isProgressMessage = status != ToolAgentExecutionStatus.COMPLETED,
            isCollapsible = false // Будет collapsible когда появится output
        )

        activeStatuses[stepId] = message
        return message
    }

    /**
     * Обновляет существующий статус
     */
    fun updateStatus(
        stepId: String,
        status: ToolAgentExecutionStatus,
        output: Map<String, Any>? = null,
        metadata: Map<String, Any>? = null,
        error: String? = null
    ): ToolAgentStatusMessage? {
        val existing = activeStatuses[stepId] ?: return null
        val now = kotlinx.datetime.Clock.System.now()

        val updatedResult = existing.result.copy(
            status = status,
            endTime = now,
            output = output,
            metadata = metadata,
            error = error,
            executionTimeMs = (now - existing.result.startTime).inWholeMilliseconds
        )

        val updatedMessage = existing.copy(
            result = updatedResult,
            isProgressMessage = status != ToolAgentExecutionStatus.COMPLETED,
            isCollapsible = !output.isNullOrEmpty(),
            displayMessage = when (status) {
                ToolAgentExecutionStatus.RUNNING -> existing.displayMessage
                ToolAgentExecutionStatus.COMPLETED -> "${existing.result.agentName} завершил работу"
                ToolAgentExecutionStatus.FAILED -> "${existing.result.agentName} завершил с ошибкой"
                ToolAgentExecutionStatus.CANCELLED -> "${existing.result.agentName} отменен"
                else -> existing.displayMessage
            }
        )

        activeStatuses[stepId] = updatedMessage
        return updatedMessage
    }

    /**
     * Получает статус по stepId
     */
    fun getStatus(stepId: String): ToolAgentStatusMessage? = activeStatuses[stepId]

    /**
     * Удаляет завершенные статусы
     */
    fun removeCompletedStatuses() {
        activeStatuses.values.removeAll { it.result.isCompleted }
    }

    /**
     * Получает все активные статусы
     */
    fun getAllStatuses(): List<ToolAgentStatusMessage> = activeStatuses.values.toList()
}