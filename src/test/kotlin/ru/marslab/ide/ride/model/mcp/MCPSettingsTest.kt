package ru.marslab.ide.ride.model.mcp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class MCPSettingsTest {
    
    private val testServer1 = MCPServerConfig(
        name = "server1",
        type = MCPServerType.STDIO,
        command = "node",
        args = listOf("server.js"),
        enabled = true
    )
    
    private val testServer2 = MCPServerConfig(
        name = "server2",
        type = MCPServerType.HTTP,
        url = "http://localhost:3000/mcp",
        enabled = true
    )
    
    @Test
    fun `test empty settings`() {
        val settings = MCPSettings.empty()
        
        assertTrue(settings.servers.isEmpty())
        assertTrue(settings.isValid())
    }
    
    @Test
    fun `test add server`() {
        val settings = MCPSettings.empty()
        val updated = settings.addServer(testServer1)
        
        assertEquals(1, updated.servers.size)
        assertEquals("server1", updated.servers[0].name)
    }
    
    @Test
    fun `test add duplicate server throws exception`() {
        val settings = MCPSettings.empty()
            .addServer(testServer1)
        
        assertThrows<IllegalArgumentException> {
            settings.addServer(testServer1)
        }
    }
    
    @Test
    fun `test update server`() {
        val settings = MCPSettings.empty()
            .addServer(testServer1)
        
        val updatedServer = testServer1.copy(enabled = false)
        val updated = settings.updateServer("server1", updatedServer)
        
        assertEquals(1, updated.servers.size)
        assertFalse(updated.servers[0].enabled)
    }
    
    @Test
    fun `test update non-existent server throws exception`() {
        val settings = MCPSettings.empty()
        
        assertThrows<IllegalArgumentException> {
            settings.updateServer("non-existent", testServer1)
        }
    }
    
    @Test
    fun `test remove server`() {
        val settings = MCPSettings.empty()
            .addServer(testServer1)
            .addServer(testServer2)
        
        val updated = settings.removeServer("server1")
        
        assertEquals(1, updated.servers.size)
        assertEquals("server2", updated.servers[0].name)
    }
    
    @Test
    fun `test get server`() {
        val settings = MCPSettings.empty()
            .addServer(testServer1)
        
        val server = settings.getServer("server1")
        
        assertNotNull(server)
        assertEquals("server1", server?.name)
    }
    
    @Test
    fun `test get non-existent server returns null`() {
        val settings = MCPSettings.empty()
        
        val server = settings.getServer("non-existent")
        
        assertNull(server)
    }
    
    @Test
    fun `test get enabled servers`() {
        val disabledServer = testServer1.copy(enabled = false)
        val settings = MCPSettings.empty()
            .addServer(disabledServer)
            .addServer(testServer2)
        
        val enabled = settings.getEnabledServers()
        
        assertEquals(1, enabled.size)
        assertEquals("server2", enabled[0].name)
    }
    
    @Test
    fun `test validation with valid servers`() {
        val settings = MCPSettings.empty()
            .addServer(testServer1)
            .addServer(testServer2)
        
        val errors = settings.validate()
        
        assertTrue(errors.isEmpty())
        assertTrue(settings.isValid())
    }
    
    @Test
    fun `test validation with invalid server`() {
        val invalidServer = MCPServerConfig(
            name = "invalid",
            type = MCPServerType.STDIO,
            command = null, // Missing command
            enabled = true
        )
        
        val settings = MCPSettings.empty()
            .addServer(invalidServer)
        
        val errors = settings.validate()
        
        assertFalse(errors.isEmpty())
        assertFalse(settings.isValid())
        assertEquals("invalid", errors[0].first)
    }
    
    @Test
    fun `test default settings`() {
        val settings = MCPSettings.default()
        
        assertEquals(2, settings.servers.size)
        assertTrue(settings.servers.all { !it.enabled }) // Default servers are disabled
    }
}
