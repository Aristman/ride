package ru.marslab.ide.ride.service.mcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import ru.marslab.ide.ride.integration.mcp.MCPClient
import ru.marslab.ide.ride.integration.mcp.MCPClientFactory
import ru.marslab.ide.ride.integration.mcp.MCPConnectionException
import ru.marslab.ide.ride.model.mcp.MCPMethod
import ru.marslab.ide.ride.model.mcp.MCPMethodResult
import ru.marslab.ide.ride.model.mcp.MCPServerConfig
import ru.marslab.ide.ride.model.mcp.MCPServerStatus
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления подключениями к MCP серверам
 * 
 * Управляет жизненным циклом подключений, кэширует статусы и методы
 */
@Service(Service.Level.PROJECT)
class MCPConnectionManager(private val project: Project) {
    
    private val logger = Logger.getInstance(MCPConnectionManager::class.java)
    private val configService = MCPConfigService.getInstance(project)
    private val persistenceService = MCPStatusPersistenceService.getInstance(project)
    
    // Клиенты для каждого сервера
    private val clients = ConcurrentHashMap<String, MCPClient>()
    
    // Статусы серверов (кэш)
    private val statuses = ConcurrentHashMap<String, MCPServerStatus>()
    
    // Scope для корутин
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Загружаем статусы из БД при инициализации
        loadStatusesFromDatabase()
    }
    
    /**
     * Инициализирует подключения ко всем включенным серверам
     */
    fun initializeConnections() {
        scope.launch {
            logger.info("Initializing MCP connections")
            
            val settings = configService.loadConfig()
            val enabledServers = settings.getEnabledServers()
            
            logger.info("Found ${enabledServers.size} enabled servers")
            
            enabledServers.forEach { config ->
                launch {
                    try {
                        connectServer(config)
                    } catch (e: Exception) {
                        logger.error("Failed to connect to ${config.name}", e)
                        updateStatus(config.name, MCPServerStatus(
                            name = config.name,
                            connected = false,
                            error = e.message
                        ))
                    }
                }
            }
        }
    }
    
    /**
     * Подключается к серверу
     * 
     * @param config Конфигурация сервера
     * @return true если подключение успешно
     */
    suspend fun connectServer(config: MCPServerConfig): Boolean = withContext(Dispatchers.IO) {
        logger.info("Connecting to server: ${config.name}")
        
        try {
            // Создаем клиент
            val client = MCPClientFactory.createClient(config)
            
            // Подключаемся
            val connected = client.connect()
            
            if (connected) {
                // Сохраняем клиент
                clients[config.name] = client
                
                // Получаем список методов
                val methods = try {
                    client.listMethods()
                } catch (e: Exception) {
                    logger.warn("Failed to list methods for ${config.name}", e)
                    emptyList()
                }
                
                // Обновляем статус
                updateStatus(config.name, MCPServerStatus(
                    name = config.name,
                    connected = true,
                    methods = methods,
                    lastConnected = System.currentTimeMillis()
                ))
                
                logger.info("Successfully connected to ${config.name}, found ${methods.size} methods")
                true
            } else {
                updateStatus(config.name, MCPServerStatus(
                    name = config.name,
                    connected = false,
                    error = "Failed to connect"
                ))
                false
            }
        } catch (e: MCPConnectionException) {
            logger.error("Connection error for ${config.name}", e)
            updateStatus(config.name, MCPServerStatus(
                name = config.name,
                connected = false,
                error = e.message
            ))
            false
        }
    }
    
    /**
     * Отключается от сервера
     * 
     * @param serverName Имя сервера
     */
    suspend fun disconnectServer(serverName: String) = withContext(Dispatchers.IO) {
        logger.info("Disconnecting from server: $serverName")
        
        val client = clients.remove(serverName)
        if (client != null) {
            try {
                client.disconnect()
                updateStatus(serverName, MCPServerStatus(
                    name = serverName,
                    connected = false
                ))
                logger.info("Disconnected from $serverName")
            } catch (e: Exception) {
                logger.error("Error disconnecting from $serverName", e)
            }
        }
    }
    
    /**
     * Переподключается к серверу
     * 
     * @param serverName Имя сервера
     * @return true если переподключение успешно
     */
    suspend fun reconnectServer(serverName: String): Boolean {
        logger.info("Reconnecting to server: $serverName")
        
        // Отключаемся
        disconnectServer(serverName)
        
        // Загружаем конфигурацию
        val settings = configService.loadConfig()
        val config = settings.getServer(serverName)
        
        if (config == null) {
            logger.error("Server configuration not found: $serverName")
            return false
        }
        
        // Подключаемся заново
        return connectServer(config)
    }
    
    /**
     * Обновляет список методов для сервера
     * 
     * @param serverName Имя сервера
     * @return Обновленный список методов
     */
    suspend fun refreshMethods(serverName: String): List<MCPMethod> = withContext(Dispatchers.IO) {
        logger.info("Refreshing methods for server: $serverName")
        
        val client = clients[serverName]
        if (client == null || !client.isConnected()) {
            logger.warn("Server $serverName is not connected")
            return@withContext emptyList()
        }
        
        try {
            val methods = client.listMethods()
            
            // Обновляем статус с новыми методами
            val currentStatus = statuses[serverName]
            if (currentStatus != null) {
                updateStatus(serverName, currentStatus.withMethods(methods))
            }
            
            logger.info("Refreshed ${methods.size} methods for $serverName")
            methods
        } catch (e: Exception) {
            logger.error("Failed to refresh methods for $serverName", e)
            emptyList()
        }
    }
    
    /**
     * Вызывает метод сервера
     * 
     * @param serverName Имя сервера
     * @param methodName Имя метода
     * @param arguments Аргументы метода
     * @return Результат вызова
     */
    suspend fun callMethod(
        serverName: String,
        methodName: String,
        arguments: JsonElement?
    ): MCPMethodResult = withContext(Dispatchers.IO) {
        logger.info("Calling method $methodName on server $serverName")
        
        val client = clients[serverName]
        if (client == null || !client.isConnected()) {
            return@withContext MCPMethodResult.error("Server $serverName is not connected")
        }
        
        try {
            client.callMethod(methodName, arguments)
        } catch (e: Exception) {
            logger.error("Failed to call method $methodName on $serverName", e)
            MCPMethodResult.error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Получает статус сервера
     * 
     * @param serverName Имя сервера
     * @return Статус сервера или null если не найден
     */
    fun getServerStatus(serverName: String): MCPServerStatus? {
        return statuses[serverName]
    }
    
    /**
     * Получает статусы всех серверов
     * 
     * @return Список статусов
     */
    fun getAllStatuses(): List<MCPServerStatus> {
        return statuses.values.toList()
    }
    
    /**
     * Проверяет, подключен ли сервер
     * 
     * @param serverName Имя сервера
     * @return true если подключен
     */
    fun isServerConnected(serverName: String): Boolean {
        return clients[serverName]?.isConnected() == true
    }
    
    /**
     * Получает список всех доступных методов со всех подключенных серверов
     * 
     * @return Карта: имя сервера -> список методов
     */
    fun getAllAvailableMethods(): Map<String, List<MCPMethod>> {
        return statuses
            .filter { it.value.connected }
            .mapValues { it.value.methods }
    }
    
    /**
     * Отключается от всех серверов
     */
    fun disconnectAll() {
        scope.launch {
            logger.info("Disconnecting from all servers")
            
            clients.keys.forEach { serverName ->
                try {
                    disconnectServer(serverName)
                } catch (e: Exception) {
                    logger.error("Error disconnecting from $serverName", e)
                }
            }
            
            clients.clear()
            statuses.clear()
        }
    }
    
    /**
     * Обновляет статус сервера
     */
    private fun updateStatus(serverName: String, status: MCPServerStatus) {
        statuses[serverName] = status
        // Сохраняем в БД
        persistenceService.saveStatus(status)
        logger.debug("Updated status for $serverName: connected=${status.connected}, methods=${status.methods.size}")
    }
    
    /**
     * Загружает статусы из базы данных
     */
    private fun loadStatusesFromDatabase() {
        val savedStatuses = persistenceService.getAllStatuses()
        savedStatuses.forEach { status ->
            statuses[status.name] = status
        }
        logger.info("Loaded ${savedStatuses.size} server statuses from database")
    }
    
    companion object {
        /**
         * Получает экземпляр сервиса для проекта
         */
        fun getInstance(project: Project): MCPConnectionManager {
            return project.getService(MCPConnectionManager::class.java)
        }
    }
}
