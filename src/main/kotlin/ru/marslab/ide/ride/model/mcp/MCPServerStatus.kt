package ru.marslab.ide.ride.model.mcp

/**
 * Статус подключения к MCP серверу
 *
 * @property name Имя сервера
 * @property connected Подключен ли сервер
 * @property error Сообщение об ошибке (если есть)
 * @property methods Список доступных методов
 * @property lastConnected Время последнего успешного подключения (Unix timestamp)
 * @property lastError Время последней ошибки (Unix timestamp)
 */
data class MCPServerStatus(
    val name: String,
    val connected: Boolean = false,
    val error: String? = null,
    val methods: List<MCPMethod> = emptyList(),
    val lastConnected: Long? = null,
    val lastError: Long? = null
) {
    /**
     * Проверяет, есть ли ошибка
     */
    fun hasError(): Boolean = error != null
    
    /**
     * Проверяет, есть ли доступные методы
     */
    fun hasMethods(): Boolean = methods.isNotEmpty()
    
    /**
     * Получает количество доступных методов
     */
    fun getMethodCount(): Int = methods.size
    
    /**
     * Создает копию с обновленным статусом подключения
     */
    fun withConnected(connected: Boolean, error: String? = null): MCPServerStatus {
        return copy(
            connected = connected,
            error = error,
            lastConnected = if (connected) System.currentTimeMillis() else lastConnected,
            lastError = if (!connected && error != null) System.currentTimeMillis() else lastError
        )
    }
    
    /**
     * Создает копию с обновленным списком методов
     */
    fun withMethods(methods: List<MCPMethod>): MCPServerStatus {
        return copy(methods = methods)
    }
    
    /**
     * Создает копию с ошибкой
     */
    fun withError(error: String): MCPServerStatus {
        return copy(
            connected = false,
            error = error,
            lastError = System.currentTimeMillis()
        )
    }
}
