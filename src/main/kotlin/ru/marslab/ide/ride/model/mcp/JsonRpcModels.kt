package ru.marslab.ide.ride.model.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSON-RPC запрос
 *
 * @property jsonrpc Версия протокола (всегда "2.0")
 * @property id Идентификатор запроса
 * @property method Имя метода
 * @property params Параметры метода
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: JsonElement? = null
)

/**
 * JSON-RPC ответ
 *
 * @property jsonrpc Версия протокола (обычно "2.0", опционально)
 * @property id Идентификатор запроса (может быть null для notifications)
 * @property result Результат выполнения (если успешно)
 * @property error Ошибка (если не успешно)
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String? = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
) {
    /**
     * Проверяет, успешен ли ответ
     */
    fun isSuccess(): Boolean = error == null && result != null

    /**
     * Проверяет, есть ли ошибка
     */
    fun hasError(): Boolean = error != null
}

/**
 * JSON-RPC ошибка
 *
 * @property code Код ошибки
 * @property message Сообщение об ошибке
 * @property data Дополнительные данные об ошибке
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
) {
    companion object {
        // Стандартные коды ошибок JSON-RPC
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603

        /**
         * Создает ошибку парсинга
         */
        fun parseError(message: String = "Parse error"): JsonRpcError {
            return JsonRpcError(PARSE_ERROR, message)
        }

        /**
         * Создает ошибку невалидного запроса
         */
        fun invalidRequest(message: String = "Invalid request"): JsonRpcError {
            return JsonRpcError(INVALID_REQUEST, message)
        }

        /**
         * Создает ошибку "метод не найден"
         */
        fun methodNotFound(method: String): JsonRpcError {
            return JsonRpcError(METHOD_NOT_FOUND, "Method not found: $method")
        }

        /**
         * Создает ошибку невалидных параметров
         */
        fun invalidParams(message: String = "Invalid params"): JsonRpcError {
            return JsonRpcError(INVALID_PARAMS, message)
        }

        /**
         * Создает внутреннюю ошибку
         */
        fun internalError(message: String = "Internal error"): JsonRpcError {
            return JsonRpcError(INTERNAL_ERROR, message)
        }
    }
}

/**
 * Ответ на запрос списка инструментов (tools/list)
 *
 * @property tools Список доступных инструментов
 */
@Serializable
data class ToolsListResponse(
    val tools: List<ToolInfo>
)

/**
 * Информация об инструменте
 *
 * @property name Имя инструмента
 * @property description Описание инструмента
 * @property inputSchema JSON Schema для параметров
 */
@Serializable
data class ToolInfo(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject? = null
)

/**
 * Параметры для вызова инструмента (tools/call)
 *
 * @property name Имя инструмента
 * @property arguments Аргументы для вызова
 */
@Serializable
data class ToolCallParams(
    val name: String,
    val arguments: JsonObject
)

/**
 * Результат вызова инструмента
 *
 * @property content Содержимое результата
 * @property isError Является ли результат ошибкой
 */
@Serializable
data class ToolCallResult(
    val content: List<ContentItem>,
    val isError: Boolean = false
)

/**
 * Элемент содержимого результата
 *
 * @property type Тип содержимого (text, image, resource)
 * @property text Текстовое содержимое
 * @property data Данные (для image)
 * @property mimeType MIME тип (для image)
 * @property uri URI ресурса (для resource)
 */
@Serializable
data class ContentItem(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
    val uri: String? = null
)
