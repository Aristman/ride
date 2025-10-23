package ru.marslab.ide.ride.model.agent

import ru.marslab.ide.ride.model.schema.ParsedResponse

/**
 * Ответ от агента на запрос пользователя
 *
 * @property content Содержимое ответа (текстовое представление)
 * @property success Флаг успешности обработки запроса
 * @property error Сообщение об ошибке (если success = false)
 * @property metadata Дополнительные метаданные ответа
 * @property parsedContent Распарсенное содержимое (если задан формат ответа)
 * @property formattedOutput Форматированный вывод для отображения в UI
 * @property isFinal Флаг окончательного ответа (true = ответ полный, false = требуются уточнения)
 * @property uncertainty Уровень неопределенности ответа (0.0 - 1.0)
 */
data class AgentResponse(
    val content: String,
    val success: Boolean,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val parsedContent: ParsedResponse? = null,
    val formattedOutput: FormattedOutput? = null,
    val isFinal: Boolean = true,
    val uncertainty: Double? = null
) {
    companion object {
        /**
         * Создает успешный ответ
         */
        fun success(content: String, metadata: Map<String, Any> = emptyMap()): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                metadata = metadata
            )
        }

        /**
         * Создает успешный ответ с параметрами окончательности
         */
        fun success(
            content: String,
            isFinal: Boolean = true,
            uncertainty: Double? = null,
            metadata: Map<String, Any> = emptyMap()
        ): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                isFinal = isFinal,
                uncertainty = uncertainty,
                metadata = metadata
            )
        }

        /**
         * Создает успешный ответ с распарсенным содержимым
         */
        fun success(
            content: String,
            parsedContent: ParsedResponse,
            metadata: Map<String, Any> = emptyMap()
        ): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                metadata = metadata,
                parsedContent = parsedContent
            )
        }

        /**
         * Создает успешный ответ с распарсенным содержимым и параметрами окончательности
         */
        fun success(
            content: String,
            parsedContent: ParsedResponse,
            isFinal: Boolean = true,
            uncertainty: Double? = null,
            metadata: Map<String, Any> = emptyMap()
        ): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                metadata = metadata,
                parsedContent = parsedContent,
                isFinal = isFinal,
                uncertainty = uncertainty
            )
        }

        /**
         * Создает ответ, требующий уточнений (неокончательный)
         */
        fun clarification(
            content: String,
            uncertainty: Double,
            metadata: Map<String, Any> = emptyMap()
        ): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                isFinal = false,
                uncertainty = uncertainty,
                metadata = metadata
            )
        }

        /**
         * Создает успешный ответ с форматированным выводом
         */
        fun success(
            content: String,
            formattedOutput: FormattedOutput,
            metadata: Map<String, Any> = emptyMap()
        ): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                metadata = metadata,
                formattedOutput = formattedOutput
            )
        }

        /**
         * Создает успешный ответ с форматированным выводом и параметрами окончательности
         */
        fun success(
            content: String,
            formattedOutput: FormattedOutput,
            isFinal: Boolean = true,
            uncertainty: Double? = null,
            metadata: Map<String, Any> = emptyMap()
        ): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                metadata = metadata,
                formattedOutput = formattedOutput,
                isFinal = isFinal,
                uncertainty = uncertainty
            )
        }

        /**
         * Создает успешный ответ с распарсенным содержимым и форматированным выводом
         */
        fun success(
            content: String,
            parsedContent: ParsedResponse,
            formattedOutput: FormattedOutput? = null,
            isFinal: Boolean = true,
            uncertainty: Double? = null,
            metadata: Map<String, Any> = emptyMap()
        ): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                metadata = metadata,
                parsedContent = parsedContent,
                formattedOutput = formattedOutput,
                isFinal = isFinal,
                uncertainty = uncertainty
            )
        }

        /**
         * Создает ответ, требующий уточнений (неокончательный)
         */
        fun clarification(
            content: String,
            uncertainty: Double,
            formattedOutput: FormattedOutput? = null,
            metadata: Map<String, Any> = emptyMap()
        ): AgentResponse {
            return AgentResponse(
                content = content,
                success = true,
                isFinal = false,
                uncertainty = uncertainty,
                formattedOutput = formattedOutput,
                metadata = metadata
            )
        }

        /**
         * Создает ответ с ошибкой
         */
        fun error(error: String, content: String = ""): AgentResponse {
            return AgentResponse(
                content = content,
                success = false,
                error = error
            )
        }
    }
}
