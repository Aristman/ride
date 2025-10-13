package ru.marslab.ide.ride.integration.mcp

import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPServerType

/**
 * Фабрика для создания MCP клиентов
 */
object MCPClientFactory {
    
    /**
     * Создает клиент на основе конфигурации
     * 
     * @param config Конфигурация сервера
     * @param timeout Таймаут операций в миллисекундах (по умолчанию 30 секунд)
     * @return MCP клиент
     * @throws IllegalArgumentException если тип сервера не поддерживается
     */
    fun createClient(config: MCPServerConfig, timeout: Long = 30000): MCPClient {
        return when (config.type) {
            MCPServerType.STDIO -> StdioMCPClient(config, timeout)
            MCPServerType.HTTP -> HttpMCPClient(config, timeout)
        }
    }
}
