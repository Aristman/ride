package ru.marslab.ide.ride.service.mcp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPServerType
import ru.marslab.ide.ride.model.mcp.MCPSettings
import java.nio.file.Files
import kotlin.io.path.writeText

class MCPConfigServiceTest : BasePlatformTestCase() {
    
    private lateinit var configService: MCPConfigService
    
    override fun setUp() {
        super.setUp()
        configService = MCPConfigService(project)
    }
    
    @Test
    fun testLoadConfigWhenFileDoesNotExist() {
        // Действие
        val settings = configService.loadConfig()
        
        // Проверка - должны вернуться настройки по умолчанию
        assertNotNull(settings)
        assertTrue(settings.servers.isNotEmpty())
    }
    
    @Test
    fun testSaveAndLoadConfig() {
        // Подготовка
        val testConfig = MCPServerConfig(
            name = "test-server",
            type = MCPServerType.STDIO,
            command = "node",
            args = listOf("server.js"),
            env = mapOf("NODE_ENV" to "test"),
            enabled = true
        )
        
        val settings = MCPSettings(servers = listOf(testConfig))
        
        // Действие - сохранение
        val saveResult = configService.saveConfig(settings)
        assertTrue(saveResult)
        
        // Действие - загрузка
        val loadedSettings = configService.loadConfig()
        
        // Проверка
        assertEquals(1, loadedSettings.servers.size)
        val loadedServer = loadedSettings.servers[0]
        assertEquals("test-server", loadedServer.name)
        assertEquals(MCPServerType.STDIO, loadedServer.type)
        assertEquals("node", loadedServer.command)
        assertEquals(listOf("server.js"), loadedServer.args)
        assertEquals(mapOf("NODE_ENV" to "test"), loadedServer.env)
        assertTrue(loadedServer.enabled)
    }
    
    @Test
    fun testSaveMultipleServers() {
        // Подготовка
        val server1 = MCPServerConfig(
            name = "stdio-server",
            type = MCPServerType.STDIO,
            command = "node",
            args = listOf("server1.js"),
            enabled = true
        )
        
        val server2 = MCPServerConfig(
            name = "http-server",
            type = MCPServerType.HTTP,
            url = "http://localhost:3000/mcp",
            enabled = false
        )
        
        val settings = MCPSettings(servers = listOf(server1, server2))
        
        // Действие
        configService.saveConfig(settings)
        val loadedSettings = configService.loadConfig()
        
        // Проверка
        assertEquals(2, loadedSettings.servers.size)
        assertTrue(loadedSettings.servers.any { it.name == "stdio-server" && it.enabled })
        assertTrue(loadedSettings.servers.any { it.name == "http-server" && !it.enabled })
    }
    
    @Test
    fun testValidateConfig() {
        // Подготовка - валидная конфигурация
        val validConfig = MCPServerConfig(
            name = "valid-server",
            type = MCPServerType.STDIO,
            command = "node",
            enabled = true
        )
        
        val settings = MCPSettings(servers = listOf(validConfig))
        
        // Действие
        val errors = configService.validateConfig(settings)
        
        // Проверка
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun testValidateConfigWithInvalidServer() {
        // Подготовка - невалидная конфигурация (отсутствует command для STDIO)
        val invalidConfig = MCPServerConfig(
            name = "invalid-server",
            type = MCPServerType.STDIO,
            command = null,
            enabled = true
        )
        
        val settings = MCPSettings(servers = listOf(invalidConfig))
        
        // Действие
        val errors = configService.validateConfig(settings)
        
        // Проверка
        assertFalse(errors.isEmpty())
        assertEquals("invalid-server", errors[0].first)
        assertTrue(errors[0].second.contains("Command is required"))
    }
    
    @Test
    fun testGetConfigPath() {
        // Действие
        val configPath = configService.getConfigPath()
        
        // Проверка
        assertNotNull(configPath)
        assertTrue(configPath.toString().contains(".ride"))
        assertTrue(configPath.toString().endsWith("mcp.json"))
    }
    
    @Test
    fun testLoadConfigWithMalformedJson() {
        // Подготовка - создаем файл с невалидным JSON
        val configPath = configService.getConfigPath()
        Files.createDirectories(configPath.parent)
        configPath.writeText("{ invalid json }")
        
        // Действие
        val settings = configService.loadConfig()
        
        // Проверка - должны вернуться настройки по умолчанию
        assertNotNull(settings)
        assertTrue(settings.servers.isNotEmpty())
    }
    
    @Test
    fun testSaveConfigCreatesDirectory() {
        // Подготовка
        val configPath = configService.getConfigPath()
        
        // Удаляем директорию, если существует
        if (Files.exists(configPath.parent)) {
            Files.walk(configPath.parent)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
        
        val settings = MCPSettings(servers = emptyList())
        
        // Действие
        val result = configService.saveConfig(settings)
        
        // Проверка
        assertTrue(result)
        assertTrue(Files.exists(configPath.parent))
        assertTrue(Files.exists(configPath))
    }
    
    @Test
    fun testLoadConfigWithEmptyServers() {
        // Подготовка
        val settings = MCPSettings(servers = emptyList())
        configService.saveConfig(settings)
        
        // Действие
        val loadedSettings = configService.loadConfig()
        
        // Проверка
        assertNotNull(loadedSettings)
        assertTrue(loadedSettings.servers.isEmpty())
    }
}
