package ru.marslab.ide.ride.model.mcp

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.junit.Assert.*

class MCPServerStatusTest {
    
    private val testMethod = MCPMethod(
        name = "test_method",
        description = "Test method",
        inputSchema = JsonPrimitive("test")
    )
    
    @Test
    fun `test default status`() {
        val status = MCPServerStatus(name = "test-server")
        
        assertEquals("test-server", status.name)
        assertFalse(status.connected)
        assertNull(status.error)
        assertTrue(status.methods.isEmpty())
        assertNull(status.lastConnected)
        assertNull(status.lastError)
    }
    
    @Test
    fun `test hasError returns true when error present`() {
        val status = MCPServerStatus(
            name = "test-server",
            error = "Connection failed"
        )
        
        assertTrue(status.hasError())
    }
    
    @Test
    fun `test hasError returns false when no error`() {
        val status = MCPServerStatus(name = "test-server")
        
        assertFalse(status.hasError())
    }
    
    @Test
    fun `test hasMethods returns true when methods present`() {
        val status = MCPServerStatus(
            name = "test-server",
            methods = listOf(testMethod)
        )
        
        assertTrue(status.hasMethods())
    }
    
    @Test
    fun `test hasMethods returns false when no methods`() {
        val status = MCPServerStatus(name = "test-server")
        
        assertFalse(status.hasMethods())
    }
    
    @Test
    fun `test getMethodCount`() {
        val status = MCPServerStatus(
            name = "test-server",
            methods = listOf(testMethod, testMethod.copy(name = "test_method_2"))
        )
        
        assertEquals(2, status.getMethodCount())
    }
    
    @Test
    fun `test withConnected updates status`() {
        val status = MCPServerStatus(name = "test-server")
        val updated = status.withConnected(true)
        
        assertTrue(updated.connected)
        assertNotNull(updated.lastConnected)
        assertNull(updated.error)
    }
    
    @Test
    fun `test withConnected with error`() {
        val status = MCPServerStatus(name = "test-server")
        val updated = status.withConnected(false, "Connection failed")
        
        assertFalse(updated.connected)
        assertEquals("Connection failed", updated.error)
        assertNotNull(updated.lastError)
    }
    
    @Test
    fun `test withMethods updates methods list`() {
        val status = MCPServerStatus(name = "test-server")
        val methods = listOf(testMethod)
        val updated = status.withMethods(methods)
        
        assertEquals(1, updated.methods.size)
        assertEquals("test_method", updated.methods[0].name)
    }
    
    @Test
    fun `test withError sets error and disconnects`() {
        val status = MCPServerStatus(
            name = "test-server",
            connected = true
        )
        val updated = status.withError("Test error")
        
        assertFalse(updated.connected)
        assertEquals("Test error", updated.error)
        assertNotNull(updated.lastError)
    }
}
