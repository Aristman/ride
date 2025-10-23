package ru.marslab.ide.ride.integration.mcp

import kotlinx.serialization.json.JsonElement
import ru.marslab.ide.ride.model.mcp.MCPMethod
import ru.marslab.ide.ride.model.mcp.MCPMethodResult

/**
 * Интерфейс для работы с MCP сервером
 */
interface MCPClient {
    /**
     * Подключается к серверу
     *
     * @return true если подключение успешно
     * @throws MCPConnectionException если не удалось подключиться
     */
    suspend fun connect(): Boolean

    /**
     * Отключается от сервера
     */
    suspend fun disconnect()

    /**
     * Проверяет, подключен ли клиент
     *
     * @return true если подключен
     */
    fun isConnected(): Boolean

    /**
     * Получает список доступных методов
     *
     * @return Список методов
     * @throws MCPException если произошла ошибка
     */
    suspend fun listMethods(): List<MCPMethod>

    /**
     * Вызывает метод сервера
     *
     * @param methodName Имя метода
     * @param arguments Аргументы метода
     * @return Результат вызова
     * @throws MCPException если произошла ошибка
     */
    suspend fun callMethod(methodName: String, arguments: JsonElement?): MCPMethodResult

    /**
     * Получает имя сервера
     */
    fun getServerName(): String
}

/**
 * Базовое исключение для MCP операций
 */
open class MCPException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Исключение при подключении к MCP серверу
 */
class MCPConnectionException(message: String, cause: Throwable? = null) : MCPException(message, cause)

/**
 * Исключение при вызове метода MCP сервера
 */
class MCPMethodCallException(message: String, cause: Throwable? = null) : MCPException(message, cause)

/**
 * Исключение при таймауте операции
 */
class MCPTimeoutException(message: String) : MCPException(message)
