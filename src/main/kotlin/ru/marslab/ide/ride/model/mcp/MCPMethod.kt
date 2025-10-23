package ru.marslab.ide.ride.model.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Метод MCP сервера
 *
 * @property name Имя метода
 * @property description Описание метода
 * @property inputSchema JSON Schema для параметров метода
 */
@Serializable
data class MCPMethod(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonElement? = null
) {
    /**
     * Проверяет, есть ли описание
     */
    fun hasDescription(): Boolean = !description.isNullOrBlank()

    /**
     * Проверяет, есть ли схема параметров
     */
    fun hasInputSchema(): Boolean = inputSchema != null

    /**
     * Получает отображаемое имя метода
     */
    fun getDisplayName(): String = name

    /**
     * Получает отображаемое описание метода
     */
    fun getDisplayDescription(): String = description ?: "No description available"
}

/**
 * Результат вызова MCP метода
 *
 * @property success Успешен ли вызов
 * @property result Результат вызова (если успешен)
 * @property error Сообщение об ошибке (если не успешен)
 * @property executionTime Время выполнения в миллисекундах
 */
data class MCPMethodResult(
    val success: Boolean,
    val result: JsonElement? = null,
    val error: String? = null,
    val executionTime: Long = 0
) {
    /**
     * Проверяет, есть ли результат
     */
    fun hasResult(): Boolean = result != null

    /**
     * Проверяет, есть ли ошибка
     */
    fun hasError(): Boolean = error != null

    companion object {
        /**
         * Создает успешный результат
         */
        fun success(result: JsonElement, executionTime: Long = 0): MCPMethodResult {
            return MCPMethodResult(
                success = true,
                result = result,
                executionTime = executionTime
            )
        }

        /**
         * Создает результат с ошибкой
         */
        fun error(error: String, executionTime: Long = 0): MCPMethodResult {
            return MCPMethodResult(
                success = false,
                error = error,
                executionTime = executionTime
            )
        }
    }
}
