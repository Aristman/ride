package ru.marslab.ide.ride.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*

/**
 * Сервис для хранения настроек плагина
 * 
 * Application Service (Singleton) с персистентностью.
 * API ключ хранится в PasswordSafe для безопасности.
 */
@Service(Service.Level.APP)
@State(
    name = "RidePluginSettings",
    storages = [Storage("RidePlugin.xml")]
)
class PluginSettings : PersistentStateComponent<PluginSettingsState> {
    
    private var state = PluginSettingsState()
    
    override fun getState(): PluginSettingsState {
        return state
    }
    
    override fun loadState(state: PluginSettingsState) {
        this.state = state
    }
    
    /**
     * Folder ID для Yandex GPT
     */
    var folderId: String
        get() = state.folderId
        set(value) {
            state.folderId = value
        }
    
    /**
     * Системный промпт для агента
     */
    var systemPrompt: String
        get() = state.systemPrompt
        set(value) {
            state.systemPrompt = value
        }
    
    /**
     * Температура генерации
     */
    var temperature: Double
        get() = state.temperature
        set(value) {
            state.temperature = value.coerceIn(0.0, 1.0)
        }
    
    /**
     * Максимальное количество токенов
     */
    var maxTokens: Int
        get() = state.maxTokens
        set(value) {
            state.maxTokens = value.coerceAtLeast(1)
        }
    
    /**
     * Сохраняет API ключ в безопасном хранилище
     * 
     * @param apiKey API ключ для сохранения
     */
    fun saveApiKey(apiKey: String) {
        val attributes = createCredentialAttributes()
        val credentials = Credentials(API_KEY_USERNAME, apiKey)
        PasswordSafe.instance.set(attributes, credentials)
    }
    
    /**
     * Получает API ключ из безопасного хранилища
     * 
     * @return API ключ или пустая строка если не найден
     */
    fun getApiKey(): String {
        val attributes = createCredentialAttributes()
        return PasswordSafe.instance.getPassword(attributes) ?: ""
    }
    
    /**
     * Удаляет API ключ из хранилища
     */
    fun clearApiKey() {
        val attributes = createCredentialAttributes()
        PasswordSafe.instance.set(attributes, null)
    }
    
    /**
     * Проверяет, настроен ли плагин
     * 
     * @return true если API ключ и Folder ID заданы
     */
    fun isConfigured(): Boolean {
        return getApiKey().isNotBlank() && folderId.isNotBlank()
    }
    
    /**
     * Создает атрибуты для хранения credentials
     */
    private fun createCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            serviceName = SERVICE_NAME,
            userName = API_KEY_USERNAME
        )
    }
    
    companion object {
        private const val SERVICE_NAME = "ru.marslab.ide.ride.yandexgpt"
        private const val API_KEY_USERNAME = "api_key"
    }
}
