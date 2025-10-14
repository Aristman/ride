package ru.marslab.ide.ride.model.mcp

import org.junit.Test
import org.junit.Assert.*

class MCPServerConfigTest {
    
    @Test
    fun `test stdio config validation - valid`() {
        val config = MCPServerConfig(
            name = "test-server",
            type = MCPServerType.STDIO,
            command = "node",
            args = listOf("server.js"),
            enabled = true
        )
        
        val result = config.validate()
        assertTrue(result.isValid())
    }
    
    @Test
    fun `test stdio config validation - missing command`() {
        val config = MCPServerConfig(
            name = "test-server",
            type = MCPServerType.STDIO,
            command = null,
            enabled = true
        )
        
        val result = config.validate()
        assertFalse(result.isValid())
        assertEquals("Command is required for STDIO type", result.getErrorMessage())
    }
    
    @Test
    fun `test http config validation - valid`() {
        val config = MCPServerConfig(
            name = "test-server",
            type = MCPServerType.HTTP,
            url = "http://localhost:3000/mcp",
            enabled = true
        )
        
        val result = config.validate()
        assertTrue(result.isValid())
    }
    
    @Test
    fun `test http config validation - missing url`() {
        val config = MCPServerConfig(
            name = "test-server",
            type = MCPServerType.HTTP,
            url = null,
            enabled = true
        )
        
        val result = config.validate()
        assertFalse(result.isValid())
        assertEquals("URL is required for HTTP type", result.getErrorMessage())
    }
    
    @Test
    fun `test http config validation - invalid url`() {
        val config = MCPServerConfig(
            name = "test-server",
            type = MCPServerType.HTTP,
            url = "not-a-url",
            enabled = true
        )
        
        val result = config.validate()
        assertFalse(result.isValid())
        assertEquals("Invalid URL format", result.getErrorMessage())
    }
    
    @Test
    fun `test config validation - empty name`() {
        val config = MCPServerConfig(
            name = "",
            type = MCPServerType.STDIO,
            command = "node",
            enabled = true
        )
        
        val result = config.validate()
        assertFalse(result.isValid())
        assertEquals("Server name cannot be empty", result.getErrorMessage())
    }
}
