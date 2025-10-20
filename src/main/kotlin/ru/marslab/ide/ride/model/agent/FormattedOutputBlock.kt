package ru.marslab.ide.ride.model.agent

/**
 * Блок форматированного вывода от агента
 *
 * @property type Тип блока вывода
 * @property content Основной контент блока
 * @property htmlTemplate HTML-шаблон для рендеринга (опционально)
 * @property cssClasses CSS-классы для стилизации
 * @property metadata Дополнительные данные блока
 * @property order Порядок отображения блока
 */
data class FormattedOutputBlock(
    val type: AgentOutputType,
    val content: String,
    val htmlTemplate: String? = null,
    val cssClasses: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
    val order: Int = 0
) {
    companion object {
        /**
         * Создает markdown блок
         */
        fun markdown(content: String, order: Int = 0): FormattedOutputBlock {
            return FormattedOutputBlock(
                type = AgentOutputType.MARKDOWN,
                content = content,
                order = order
            )
        }

        /**
         * Создает терминальный блок
         */
        fun terminal(
            content: String,
            command: String? = null,
            exitCode: Int? = null,
            executionTime: Long? = null,
            success: Boolean = true,
            order: Int = 0
        ): FormattedOutputBlock {
            val metadata = mutableMapOf<String, Any>()
            command?.let { metadata["command"] = it }
            exitCode?.let { metadata["exitCode"] = it }
            executionTime?.let { metadata["executionTime"] = it }
            metadata["success"] = success

            return FormattedOutputBlock(
                type = AgentOutputType.TERMINAL,
                content = content,
                cssClasses = listOf("terminal-output"),
                metadata = metadata,
                order = order
            )
        }

        /**
         * Создает блок кода
         */
        fun codeBlock(
            content: String,
            language: String = "",
            order: Int = 0
        ): FormattedOutputBlock {
            return FormattedOutputBlock(
                type = AgentOutputType.CODE_BLOCKS,
                content = content,
                cssClasses = listOf("code-block", "language-$language"),
                metadata = mapOf("language" to language),
                order = order
            )
        }

        /**
         * Создает блок результата инструмента
         */
        fun toolResult(
            content: String,
            toolName: String,
            operationType: String = "",
            success: Boolean = true,
            order: Int = 0
        ): FormattedOutputBlock {
            return FormattedOutputBlock(
                type = AgentOutputType.TOOL_RESULT,
                content = content,
                cssClasses = listOf("tool-result"),
                metadata = mapOf(
                    "toolName" to toolName,
                    "operationType" to operationType,
                    "success" to success
                ),
                order = order
            )
        }

        /**
         * Создает HTML блок
         */
        fun html(
            content: String,
            cssClasses: List<String> = emptyList(),
            order: Int = 0
        ): FormattedOutputBlock {
            return FormattedOutputBlock(
                type = AgentOutputType.HTML,
                content = content,
                cssClasses = cssClasses,
                order = order
            )
        }

        /**
         * Создает структурированный блок
         */
        fun structured(
            content: String,
            format: String = "json",
            order: Int = 0
        ): FormattedOutputBlock {
            return FormattedOutputBlock(
                type = AgentOutputType.STRUCTURED,
                content = content,
                cssClasses = listOf("structured-data", "format-$format"),
                metadata = mapOf("format" to format),
                order = order
            )
        }
    }
}