package ru.marslab.ide.ride.service.mcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.marslab.ide.ride.model.mcp.MCPSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Сервис для работы с конфигурацией MCP серверов
 * 
 * Управляет загрузкой, сохранением и валидацией конфигурации из файла .ride/mcp.json
 */
@Service(Service.Level.PROJECT)
class MCPConfigService(private val project: Project) {
    
    private val logger = Logger.getInstance(MCPConfigService::class.java)
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Получает путь к конфигурационному файлу
     */
    fun getConfigPath(): Path? {
        val projectPath = project.basePath ?: return null
        val rideDir = Path.of(projectPath, ".ride")
        return rideDir.resolve("mcp.json")
    }
    
    /**
     * Загружает конфигурацию из файла
     * 
     * @return Настройки MCP или настройки по умолчанию, если файл не существует
     */
    fun loadConfig(): MCPSettings {
        val configPath = getConfigPath() ?: return MCPSettings()
        
        if (!configPath.exists()) {
            logger.info("MCP config file not found, creating default config")
            val defaultConfig = getDefaultConfig()
            saveConfig(defaultConfig)
            return defaultConfig
        }
        
        return try {
            val content = configPath.readText()
            val settings = json.decodeFromString<MCPSettings>(content)
            logger.info("MCP config loaded successfully: ${settings.servers.size} servers")
            settings
        } catch (e: Exception) {
            logger.error("Failed to load MCP config", e)
            MCPSettings.empty()
        }
    }
    
    /**
     * Сохраняет конфигурацию в файл
     * 
     * @param settings Настройки для сохранения
     * @return true если сохранение успешно, false в противном случае
     */
    fun saveConfig(settings: MCPSettings): Boolean {
        val configPath = getConfigPath() ?: return false
        
        return try {
            // Создаем директорию .ride если не существует
            val rideDir = configPath.parent
            if (!rideDir.exists()) {
                Files.createDirectories(rideDir)
                logger.info("Created .ride directory: $rideDir")
            }
            
            // Сериализуем и сохраняем
            val content = json.encodeToString(settings)
            Files.writeString(
                configPath,
                content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            
            logger.info("MCP config saved successfully: ${settings.servers.size} servers")
            true
        } catch (e: Exception) {
            logger.error("Failed to save MCP config", e)
            false
        }
    }
    
    /**
     * Валидирует конфигурацию
     * 
     * @param settings Настройки для валидации
     * @return Список ошибок валидации (пустой если валидация успешна)
     */
    fun validateConfig(settings: MCPSettings): List<Pair<String, String>> {
        return settings.validate()
    }
    
    /**
     * Проверяет, валидна ли конфигурация
     * 
     * @param settings Настройки для проверки
     * @return true если конфигурация валидна
     */
    fun isConfigValid(settings: MCPSettings): Boolean {
        return settings.isValid()
    }
    
    /**
     * Получает конфигурацию по умолчанию
     * 
     * @return Настройки по умолчанию с примерами
     */
    fun getDefaultConfig(): MCPSettings {
        return MCPSettings.default()
    }
    
    /**
     * Создает пустую конфигурацию
     * 
     * @return Пустые настройки
     */
    fun createEmptyConfig(): MCPSettings {
        return MCPSettings.empty()
    }
    
    /**
     * Проверяет, существует ли конфигурационный файл
     * 
     * @return true если файл существует
     */
    fun configExists(): Boolean {
        return getConfigPath()?.exists() ?: false
    }
    
    /**
     * Удаляет конфигурационный файл
     * 
     * @return true если удаление успешно
     */
    fun deleteConfig(): Boolean {
        val configPath = getConfigPath() ?: return false
        
        return try {
            if (configPath.exists()) {
                Files.delete(configPath)
                logger.info("MCP config deleted")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to delete MCP config", e)
            false
        }
    }
    
    /**
     * Перезагружает конфигурацию из файла
     * 
     * @return Обновленные настройки
     */
    fun reloadConfig(): MCPSettings {
        logger.info("Reloading MCP config")
        return loadConfig()
    }
    
    companion object {
        /**
         * Получает экземпляр сервиса для проекта
         */
        fun getInstance(project: Project): MCPConfigService {
            return project.getService(MCPConfigService::class.java)
        }
    }
}
