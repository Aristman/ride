package ru.marslab.ide.ride.integration.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPServerType

class StdioMCPClientTest {
    
    private lateinit var config: MCPServerConfig
    private lateinit var client: StdioMCPClient
    
    @Before
    fun setUp() {
        config = MCPServerConfig(
            name = "test-server",
            type = MCPServerType.STDIO,
            command = "echo",
            args = listOf("test"),
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
        client = StdioMCPClient(config)
        assertEquals("test-server", client.getServerName())
    }
    
    @Test
    fun testIsConnectedInitially() {
        client = StdioMCPClient(config)
        assertFalse(client.isConnected())
    }
    
    @Test
    fun testConnectWithInvalidCommand() = runBlocking {
        val invalidConfig = config.copy(command = "nonexistent-command-12345")
        client = StdioMCPClient(invalidConfig)
        
        try {
            val result = client.connect()
            assertFalse(result)
        } catch (e: MCPConnectionException) {
            // Ожидаемое исключение
            assertTrue(e.message?.contains("Failed to start process") == true)
        }
    }
    
    @Test
    fun testDisconnectWithoutConnect() = runBlocking {
        client = StdioMCPClient(config)
        
        // Не должно быть исключений
        client.disconnect()
        assertFalse(client.isConnected())
    }
    
    @Test
    fun testListMethodsWithoutConnection() = runBlocking {
        client = StdioMCPClient(config)
        
        try {
            client.listMethods()
            fail("Should throw MCPException")
        } catch (e: MCPException) {
            assertTrue(e.message?.contains("not connected") == true)
        }
    }
    
    @Test
    fun testCallMethodWithoutConnection() = runBlocking {
        client = StdioMCPClient(config)
        
        try {
            client.callMethod("test-method", JsonPrimitive("test"))
            fail("Should throw MCPException")
        } catch (e: MCPException) {
            assertTrue(e.message?.contains("not connected") == true)
        }
    }
    
    @Test
    fun testConfigValidation() {
        val invalidConfig = config.copy(command = null)
        
        try {
            StdioMCPClient(invalidConfig)
            fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Command is required") == true)
        }
    }
    
    @Test
    fun testEnvironmentVariables() {
        val configWithEnv = config.copy(
            env = mapOf(
                "TEST_VAR" to "test_value",
                "NODE_ENV" to "production"
            )
        )
        
        client = StdioMCPClient(configWithEnv)
        assertEquals("test-server", client.getServerName())
    }
    
    @Test
    fun testMultipleArgs() {
        val configWithArgs = config.copy(
            args = listOf("arg1", "arg2", "arg3")
        )
        
        client = StdioMCPClient(configWithArgs)
        assertEquals("test-server", client.getServerName())
    }
    
    @Test
    fun testEmptyArgs() {
        val configWithoutArgs = config.copy(args = emptyList())
        
        client = StdioMCPClient(configWithoutArgs)
        assertEquals("test-server", client.getServerName())
    }
    
    @Test(expected = MCPException::class)
    fun testCallMethodWithInvalidMethodName() = runBlocking {
        client = StdioMCPClient(config)
        
        // Попытка вызвать метод без подключения
        client.callMethod("", JsonPrimitive("test"))
    }
    
    @Test
    fun testToString() {
        client = StdioMCPClient(config)
        val str = client.toString()
        
        assertTrue(str.contains("test-server"))
        assertTrue(str.contains("STDIO"))
    }
}
