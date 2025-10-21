package ru.marslab.ide.ride.integration.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPServerType

class HttpMCPClientTest {
    
    private lateinit var config: MCPServerConfig
    private lateinit var client: HttpMCPClient
    
    @Before
    fun setUp() {
        config = MCPServerConfig(
            name = "test-http-server",
            type = MCPServerType.HTTP,
            url = "http://localhost:3000/mcp",
            enabled = true
        )
    }
    
    @After
    fun tearDown() {
        runBlocking {
            if (::client.isInitialized && client.isConnected()) {
                client.disconnect()
            }
        }
    }
    
    @Test
    fun testGetServerName() {
        client = HttpMCPClient(config)
        assertEquals("test-http-server", client.getServerName())
    }
    
    @Test
    fun testIsConnectedInitially() {
        client = HttpMCPClient(config)
        assertFalse(client.isConnected())
    }
    
    @Test
    fun testConfigValidation() {
        val invalidConfig = config.copy(url = null)
        
        try {
            HttpMCPClient(invalidConfig)
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("URL is required") == true)
        }
    }
    
    @Test
    fun testConfigValidationWithInvalidUrl() {
        val invalidConfig = config.copy(url = "not-a-valid-url")
        
        try {
            HttpMCPClient(invalidConfig)
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid URL") == true)
        }
    }
    
    @Test
    fun testConnectWithInvalidUrl() = runBlocking {
        val invalidConfig = config.copy(url = "http://localhost:99999/mcp")
        client = HttpMCPClient(invalidConfig)
        
        try {
            val result = client.connect()
            assertFalse(result)
        } catch (e: MCPConnectionException) {
            // Ожидаемое исключение при недоступном сервере
            assertTrue(e.message?.contains("Failed to connect") == true || 
                      e.message?.contains("Connection refused") == true)
        }
    }
    
    @Test
    fun testDisconnect() = runBlocking {
        client = HttpMCPClient(config)
        
        // Disconnect без подключения не должен вызывать ошибок
        client.disconnect()
        assertFalse(client.isConnected())
    }
    
    @Test
    fun testListMethodsWithoutConnection() = runBlocking {
        client = HttpMCPClient(config)
        
        try {
            client.listMethods()
            fail("Should throw MCPException")
        } catch (e: MCPException) {
            assertTrue(e.message?.contains("not connected") == true)
        }
    }
    
    @Test
    fun testCallMethodWithoutConnection() = runBlocking {
        client = HttpMCPClient(config)
        
        try {
            client.callMethod("test-method", JsonPrimitive("test"))
            fail("Should throw MCPException")
        } catch (e: MCPException) {
            assertTrue(e.message?.contains("not connected") == true)
        }
    }
    
    @Test
    fun testHttpsUrl() {
        val httpsConfig = config.copy(url = "https://api.example.com/mcp")
        client = HttpMCPClient(httpsConfig)
        
        assertEquals("test-http-server", client.getServerName())
    }
    
    @Test
    fun testUrlWithPort() {
        val configWithPort = config.copy(url = "http://localhost:8080/mcp")
        client = HttpMCPClient(configWithPort)
        
        assertEquals("test-http-server", client.getServerName())
    }
    
    @Test
    fun testUrlWithPath() {
        val configWithPath = config.copy(url = "http://localhost:3000/api/v1/mcp")
        client = HttpMCPClient(configWithPath)
        
        assertEquals("test-http-server", client.getServerName())
    }
    
    @Test
    fun testUrlWithQueryParams() {
        val configWithQuery = config.copy(url = "http://localhost:3000/mcp?token=abc123")
        client = HttpMCPClient(configWithQuery)
        
        assertEquals("test-http-server", client.getServerName())
    }
    
    @Test(expected = MCPException::class)
    fun testCallMethodWithEmptyMethodName() = runBlocking {
        client = HttpMCPClient(config)
        
        // Попытка вызвать метод с пустым именем
        client.callMethod("", JsonPrimitive("test"))
    }
    
    @Test
    fun testCallMethodWithNullArguments() = runBlocking {
        client = HttpMCPClient(config)
        
        try {
            client.callMethod("test-method", null)
            fail("Should throw MCPException (not connected)")
        } catch (e: MCPException) {
            assertTrue(e.message?.contains("not connected") == true)
        }
    }
    
    @Test
    fun testCallMethodWithJsonObjectArguments() = runBlocking {
        client = HttpMCPClient(config)
        
        val args = JsonObject(mapOf(
            "param1" to JsonPrimitive("value1"),
            "param2" to JsonPrimitive(42)
        ))
        
        try {
            client.callMethod("test-method", args)
            fail("Should throw MCPException (not connected)")
        } catch (e: MCPException) {
            assertTrue(e.message?.contains("not connected") == true)
        }
    }
    
    @Test
    fun testToString() {
        client = HttpMCPClient(config)
        val str = client.toString()
        
        assertTrue(str.contains("test-http-server"))
        assertTrue(str.contains("HTTP"))
        assertTrue(str.contains("localhost:3000"))
    }
    
    @Test
    fun testMultipleDisconnectCalls() = runBlocking {
        client = HttpMCPClient(config)
        
        // Несколько вызовов disconnect не должны вызывать ошибок
        client.disconnect()
        client.disconnect()
        client.disconnect()
        
        assertFalse(client.isConnected())
    }
}
