package ru.marslab.ide.ride.model.mcp

import kotlinx.serialization.Serializable

/**
 * Настройки MCP
 *
 * @property servers Список конфигураций серверов
 */
@Serializable
data class MCPSettings(
    val servers: List<MCPServerConfig> = emptyList()
) {
    /**
     * Получает сервер по имени
     */
    fun getServer(name: String): MCPServerConfig? {
        return servers.find { it.name == name }
    }

    /**
     * Получает все включенные серверы
     */
    fun getEnabledServers(): List<MCPServerConfig> {
        return servers.filter { it.enabled }
    }

    /**
     * Проверяет, есть ли сервер с таким именем
     */
    fun hasServer(name: String): Boolean {
        return servers.any { it.name == name }
    }

    /**
     * Добавляет новый сервер
     */
    fun addServer(server: MCPServerConfig): MCPSettings {
        if (hasServer(server.name)) {
            throw IllegalArgumentException("Server with name '${server.name}' already exists")
        }
        return copy(servers = servers + server)
    }

    /**
     * Обновляет существующий сервер
     */
    fun updateServer(name: String, server: MCPServerConfig): MCPSettings {
        val index = servers.indexOfFirst { it.name == name }
        if (index == -1) {
            throw IllegalArgumentException("Server with name '$name' not found")
        }
        val updatedServers = servers.toMutableList()
        updatedServers[index] = server
        return copy(servers = updatedServers)
    }

    /**
     * Удаляет сервер
     */
    fun removeServer(name: String): MCPSettings {
        return copy(servers = servers.filter { it.name != name })
    }

    /**
     * Валидация всех серверов
     */
    fun validate(): List<Pair<String, String>> {
        val errors = mutableListOf<Pair<String, String>>()

        servers.forEach { server ->
            val result = server.validate()
            if (!result.isValid()) {
                errors.add(server.name to (result.getErrorMessage() ?: "Unknown error"))
            }
        }

        // Проверка на дубликаты имен
        val duplicates = servers.groupBy { it.name }
            .filter { it.value.size > 1 }
            .keys

        duplicates.forEach { name ->
            errors.add(name to "Duplicate server name")
        }

        return errors
    }

    /**
     * Проверяет, валидны ли все настройки
     */
    fun isValid(): Boolean = validate().isEmpty()

    companion object {
        /**
         * Создает пустые настройки
         */
        fun empty(): MCPSettings = MCPSettings(emptyList())
    }
}
