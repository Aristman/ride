package ru.marslab.ide.ride.model

/**
 * События от агента при потоковой передаче
 */
sealed class AgentEvent {
    /**
     * Начало обработки запроса
     */
    data object Started : AgentEvent()

    /**
     * Частичный ответ (chunk)
     *
     * @property content Часть содержимого ответа
     * @property metadata Дополнительные метаданные
     */
    data class ContentChunk(
        val content: String,
        val metadata: Map<String, Any> = emptyMap()
    ) : AgentEvent()

    /**
     * Завершение обработки запроса
     *
     * @property response Финальный ответ агента
     */
    data class Completed(
        val response: AgentResponse
    ) : AgentEvent()

    /**
     * Ошибка при обработке запроса
     *
     * @property error Сообщение об ошибке
     * @property throwable Исключение (если есть)
     */
    data class Error(
        val error: String,
        val throwable: Throwable? = null
    ) : AgentEvent()
}
