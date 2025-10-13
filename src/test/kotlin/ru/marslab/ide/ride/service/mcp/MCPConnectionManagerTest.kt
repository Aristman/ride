package ru.marslab.ide.ride.service.mcp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import ru.marslab.ide.ride.integration.mcp.MCPClient
import ru.marslab.ide.ride.integration.mcp.MCPClientFactory
import ru.marslab.ide.ride.model.mcp.*

class MCPConnectionManagerTest : BasePlatformTestCase() {

    private lateinit var connectionManager: MCPConnectionManager
    private lateinit var mockClient: MCPClient
    
    override fun setUp() {
        super.setUp()
        
        // Создаем мок клиента
        mockClient = mockk()
        
        // Создаем менеджер подключений
        connectionManager = MCPConnectionManager(project)
    }
    
    override fun tearDown() {
        runBlocking {
            connectionManager.disconnectAll()
        }
        super.tearDown()
    }
    
    @Test
    fun testGetServerStatusWhenNotInitialized() {
        // Действие
        val status = connectionManager.getServerStatus("non-existent")
        
        // Проверка
        assertNull(status)
    }
    
    @Test
    fun testGetAllServerStatusesWhenEmpty() {
        // Действие
        val statuses = connectionManager.getAllStatuses()
        
        // Проверка
        assertNotNull(statuses)
        assertTrue(statuses.isEmpty())
    }
    
    @Test
    fun testDisconnectServerWhenNotConnected() = runBlocking {
        // Действие - не должно быть исключений
        connectionManager.disconnectServer("non-existent")
        
        // Проверка
        val status = connectionManager.getServerStatus("non-existent")
        assertNull(status)
    }
    
    @Test
    fun testDisconnectAll() = runBlocking {
        // Действие - не должно быть исключений
        connectionManager.disconnectAll()
        
        // Проверка
        val statuses = connectionManager.getAllStatuses()
        assertTrue(statuses.isEmpty())
    }
    
    @Test
    fun testCallMethodWhenServerNotFound() = runBlocking {
        // Действие
        val result = connectionManager.callMethod(
            "non-existent",
            "test-method",
            JsonPrimitive("test")
        )
        
        // Проверка
        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue(result.error?.contains("not found") == true)
    }
    
    @Test
    fun testRefreshMethodsWhenNotFound() = runBlocking {
        // Действие
        val methods = connectionManager.refreshMethods("non-existent")
        
        // Проверка
        assertNotNull(methods)
        assertTrue(methods.isEmpty())
    }
    
    @Test
    fun testGetAllAvailableMethods() {
        // Действие
        val methods = connectionManager.getAllAvailableMethods()
        
        // Проверка
        assertNotNull(methods)
        assertTrue(methods.isEmpty())
    }
    
    @Test
    fun testIsServerConnectedWhenNotFound() {
        // Действие
        val isConnected = connectionManager.isServerConnected("non-existent")
        
        // Проверка
        assertFalse(isConnected)
    }
    
    @Test
    fun testReconnectServerWhenNotFound() = runBlocking {
        // Действие
        val result = connectionManager.reconnectServer("non-existent")
        
        // Проверка
        assertFalse(result)
    }
    
    @Test
    fun testCallMethodWithNullArguments() = runBlocking {
        // Действие
        val result = connectionManager.callMethod(
            "test-server",
            "test-method",
            null
        )
        
        // Проверка
        assertFalse(result.success)
        assertNotNull(result.error)
    }
    
    @Test
    fun testCallMethodWithEmptyMethodName() = runBlocking {
        // Действие
        val result = connectionManager.callMethod(
            "test-server",
            "",
            JsonPrimitive("test")
        )
        
        // Проверка
        assertFalse(result.success)
        assertNotNull(result.error)
    }
    
    @Test
    fun testMultipleDisconnectAllCalls() = runBlocking {
        // Действие - несколько вызовов не должны вызывать ошибок
        connectionManager.disconnectAll()
        connectionManager.disconnectAll()
        connectionManager.disconnectAll()
        
        // Проверка
        val statuses = connectionManager.getAllStatuses()
        assertTrue(statuses.isEmpty())
    }
    
    @Test
    fun testRefreshMethodsMultipleTimes() = runBlocking {
        // Действие - несколько обновлений методов
        val methods1 = connectionManager.refreshMethods("test-server")
        val methods2 = connectionManager.refreshMethods("test-server")
        val methods3 = connectionManager.refreshMethods("test-server")
        
        // Проверка
        assertTrue(methods1.isEmpty())
        assertTrue(methods2.isEmpty())
        assertTrue(methods3.isEmpty())
    }
}
