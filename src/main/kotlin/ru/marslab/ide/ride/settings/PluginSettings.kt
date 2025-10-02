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
        ensureDefaults()
        return state
    }
    
    override fun loadState(state: PluginSettingsState) {
        this.state = state
        ensureDefaults()
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
     * Размер шрифта в окне чата
     */
    var chatFontSize: Int
        get() = state.chatFontSize
        set(value) {
            state.chatFontSize = value.coerceIn(10, 32)
        }

    /**
     * Цвет заголовка сообщений (префиксов)
     */
    var chatPrefixColor: String
        get() = state.chatPrefixColor
        set(value) {
            state.chatPrefixColor = normalizeColor(value, PluginSettingsState.DEFAULT_PREFIX_COLOR)
        }

    /**
     * Цвет фона кодовых блоков
     */
    var chatCodeBackgroundColor: String
        get() = state.chatCodeBackgroundColor
        set(value) {
            state.chatCodeBackgroundColor = normalizeColor(value, PluginSettingsState.DEFAULT_CODE_BACKGROUND_COLOR)
        }

    /**
     * Цвет текста в кодовых блоках
     */
    var chatCodeTextColor: String
        get() = state.chatCodeTextColor
        set(value) {
            state.chatCodeTextColor = normalizeColor(value, PluginSettingsState.DEFAULT_CODE_TEXT_COLOR)
        }

    /**
     * Цвет рамки кодовых блоков
     */
    var chatCodeBorderColor: String
        get() = state.chatCodeBorderColor
        set(value) {
            state.chatCodeBorderColor = normalizeColor(value, PluginSettingsState.DEFAULT_CODE_BORDER_COLOR)
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
        private val COLOR_REGEX = Regex("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    }

    private fun ensureDefaults() {
        state.chatFontSize = state.chatFontSize.takeIf { it in 10..32 } ?: PluginSettingsState.DEFAULT_CHAT_FONT_SIZE
        state.chatPrefixColor = normalizeColor(state.chatPrefixColor, PluginSettingsState.DEFAULT_PREFIX_COLOR)
        state.chatCodeBackgroundColor = normalizeColor(state.chatCodeBackgroundColor, PluginSettingsState.DEFAULT_CODE_BACKGROUND_COLOR)
        state.chatCodeTextColor = normalizeColor(state.chatCodeTextColor, PluginSettingsState.DEFAULT_CODE_TEXT_COLOR)
        state.chatCodeBorderColor = normalizeColor(state.chatCodeBorderColor, PluginSettingsState.DEFAULT_CODE_BORDER_COLOR)
    }

    private fun normalizeColor(value: String?, default: String): String {
        val trimmed = value?.trim().orEmpty()
        return if (COLOR_REGEX.matches(trimmed)) trimmed else default
    }
}
