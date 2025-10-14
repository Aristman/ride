package ru.marslab.ide.ride.integration.mcp

import org.junit.Test
import org.junit.Assert.*
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPServerType

class MCPClientFactoryTest {
    
    private val factory = MCPClientFactory
    
    @Test
    fun testCreateStdioClient() {
        // Подготовка
        val config = MCPServerConfig(
            name = "stdio-server",
            type = MCPServerType.STDIO,
            command = "node",
            args = listOf("server.js"),
            enabled = true
        )
        
        // Действие
        val client = factory.createClient(config)
        
        // Проверка
        assertNotNull(client)
        assertTrue(client is StdioMCPClient)
        assertEquals("stdio-server", client.getServerName())
    }
    
    @Test
    fun testCreateHttpClient() {
        // Подготовка
        val config = MCPServerConfig(
            name = "http-server",
            type = MCPServerType.HTTP,
            url = "http://localhost:3000/mcp",
            enabled = true
        )
        
        // Действие
        val client = factory.createClient(config)
        
        // Проверка
        assertNotNull(client)
        assertTrue(client is HttpMCPClient)
        assertEquals("http-server", client.getServerName())
    }
    
    @Test
    fun testCreateMultipleClients() {
        // Подготовка
        val stdioConfig = MCPServerConfig(
            name = "stdio-server",
            type = MCPServerType.STDIO,
            command = "node",
            args = listOf("server.js"),
            enabled = true
        )
        
        val httpConfig = MCPServerConfig(
            name = "http-server",
            type = MCPServerType.HTTP,
            url = "http://localhost:3000/mcp",
            enabled = true
        )
        
        // Действие
        val stdioClient = factory.createClient(stdioConfig)
        val httpClient = factory.createClient(httpConfig)
        
        // Проверка
        assertNotNull(stdioClient)
        assertNotNull(httpClient)
        assertTrue(stdioClient is StdioMCPClient)
        assertTrue(httpClient is HttpMCPClient)
        assertNotEquals(stdioClient.getServerName(), httpClient.getServerName())
    }
    
    @Test
    fun testCreateClientWithDisabledServer() {
        // Подготовка
        val config = MCPServerConfig(
            name = "disabled-server",
            type = MCPServerType.STDIO,
            command = "node",
            enabled = false
        )
        
        // Действие
        val client = factory.createClient(config)
        
        // Проверка - клиент создается независимо от enabled
        assertNotNull(client)
        assertTrue(client is StdioMCPClient)
    }
    
    @Test
    fun testCreateStdioClientWithEnvironment() {
        // Подготовка
        val config = MCPServerConfig(
            name = "stdio-with-env",
            type = MCPServerType.STDIO,
            command = "node",
            args = listOf("server.js"),
            env = mapOf("NODE_ENV" to "production"),
            enabled = true
        )
        
        // Действие
        val client = factory.createClient(config)
        
        // Проверка
        assertNotNull(client)
        assertTrue(client is StdioMCPClient)
    }
    
    @Test
    fun testCreateHttpClientWithHttps() {
        // Подготовка
        val config = MCPServerConfig(
            name = "https-server",
            type = MCPServerType.HTTP,
            url = "https://api.example.com/mcp",
            enabled = true
        )
        
        // Действие
        val client = factory.createClient(config)
        
        // Проверка
        assertNotNull(client)
        assertTrue(client is HttpMCPClient)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testCreateStdioClientWithoutCommand() {
        // Подготовка
        val config = MCPServerConfig(
            name = "invalid-stdio",
            type = MCPServerType.STDIO,
            command = null,
            enabled = true
        )
        
        // Действие - должно выбросить исключение
        factory.createClient(config)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testCreateHttpClientWithoutUrl() {
        // Подготовка
        val config = MCPServerConfig(
            name = "invalid-http",
            type = MCPServerType.HTTP,
            url = null,
            enabled = true
        )
        
        // Действие - должно выбросить исключение
        factory.createClient(config)
    }
}
