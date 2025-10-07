package ru.marslab.ide.ride.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*
import java.util.LinkedHashMap

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
     * Выбранный провайдер LLM
     */
    var selectedProvider: String
        get() = state.selectedProvider
        set(value) {
            state.selectedProvider = value
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
            state.chatFontSize = value.coerceIn(8, 32)
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
     * Цвет фона сообщений пользователя
     */
    var chatUserBackgroundColor: String
        get() = state.chatUserBackgroundColor
        set(value) {
            state.chatUserBackgroundColor = normalizeColor(value, PluginSettingsState.DEFAULT_USER_BACKGROUND_COLOR)
        }

    /**
     * Цвет рамки сообщений пользователя
     */
    var chatUserBorderColor: String
        get() = state.chatUserBorderColor
        set(value) {
            state.chatUserBorderColor = normalizeColor(value, PluginSettingsState.DEFAULT_USER_BORDER_COLOR)
        }

    /**
     * Текущий идентификатор модели Yandex для генерации
     */
    var yandexModelId: String
        get() = state.yandexModelId
        set(value) {
            state.yandexModelId = normalizeModelId(value)
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
     * Сохраняет токен Hugging Face в безопасном хранилище
     */
    fun saveHuggingFaceToken(token: String) {
        val attributes = createHFCredentialAttributes()
        val credentials = Credentials(HF_TOKEN_USERNAME, token)
        PasswordSafe.instance.set(attributes, credentials)
    }

    /**
     * Получает токен Hugging Face из безопасного хранилища
     */
    fun getHuggingFaceToken(): String {
        val attributes = createHFCredentialAttributes()
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
     * @return true если требуемые поля для выбранного провайдера заданы
     */
    fun isConfigured(): Boolean {
        return when (selectedProvider) {
            PROVIDER_YANDEX -> getApiKey().isNotBlank() && folderId.isNotBlank()
            PROVIDER_HF_DEEPSEEK -> getHuggingFaceToken().isNotBlank()
            else -> false
        }
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
    private fun createHFCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            serviceName = HF_SERVICE_NAME,
            userName = HF_TOKEN_USERNAME
        )
    }
    
    companion object {
        private const val SERVICE_NAME = "ru.marslab.ide.ride.yandexgpt"
        private const val API_KEY_USERNAME = "api_key"
        private const val HF_SERVICE_NAME = "ru.marslab.ide.ride.huggingface"
        private const val HF_TOKEN_USERNAME = "hf_token"
        private val COLOR_REGEX = Regex("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
        val AVAILABLE_YANDEX_MODELS: LinkedHashMap<String, String> = linkedMapOf(
            "yandexgpt-lite" to "YandexGPT Lite",
            "yandexgpt" to "YandexGPT",
//            "qwen3-235b-a22b-fp8" to "Qwen3 235B (fp8)",
//            "gpt-oss-120b" to "GPT-OSS 120B",
//            "gpt-oss-20b" to "GPT-OSS 20B"
        )
        val AVAILABLE_PROVIDERS: LinkedHashMap<String, String> = linkedMapOf(
            PROVIDER_YANDEX to "Yandex GPT",
            PROVIDER_HF_DEEPSEEK to "HuggingFace DeepSeek-R1"
        )
        const val PROVIDER_YANDEX = "YandexGPT"
        const val PROVIDER_HF_DEEPSEEK = "HuggingFaceDeepSeekR1"
    }

    private fun ensureDefaults() {
        state.chatFontSize = state.chatFontSize.takeIf { it in 8..32 } ?: PluginSettingsState.DEFAULT_CHAT_FONT_SIZE
        state.chatPrefixColor = normalizeColor(state.chatPrefixColor, PluginSettingsState.DEFAULT_PREFIX_COLOR)
        state.chatCodeBackgroundColor = normalizeColor(state.chatCodeBackgroundColor, PluginSettingsState.DEFAULT_CODE_BACKGROUND_COLOR)
        state.chatCodeTextColor = normalizeColor(state.chatCodeTextColor, PluginSettingsState.DEFAULT_CODE_TEXT_COLOR)
        state.chatCodeBorderColor = normalizeColor(state.chatCodeBorderColor, PluginSettingsState.DEFAULT_CODE_BORDER_COLOR)
        state.chatUserBackgroundColor = normalizeColor(state.chatUserBackgroundColor, PluginSettingsState.DEFAULT_USER_BACKGROUND_COLOR)
        state.chatUserBorderColor = normalizeColor(state.chatUserBorderColor, PluginSettingsState.DEFAULT_USER_BORDER_COLOR)
        state.yandexModelId = normalizeModelId(state.yandexModelId)
        if (!AVAILABLE_PROVIDERS.containsKey(state.selectedProvider)) {
            state.selectedProvider = PluginSettingsState.DEFAULT_PROVIDER
        }
    }

    private fun normalizeColor(value: String?, default: String): String {
        val trimmed = value?.trim().orEmpty()
        return if (COLOR_REGEX.matches(trimmed)) trimmed else default
    }

    private fun normalizeModelId(value: String?): String {
        val normalized = value?.trim().orEmpty()
        return if (AVAILABLE_YANDEX_MODELS.containsKey(normalized)) normalized else PluginSettingsState.DEFAULT_YANDEX_MODEL_ID
    }
}
