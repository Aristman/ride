package ru.marslab.ide.ride.settings

/**
 * State класс для хранения настроек плагина
 * 
 * Используется для персистентности настроек между сессиями IDE
 */
data class PluginSettingsState(
    var folderId: String = "",
    var temperature: Double = 0.3,
    var maxTokens: Int = 2000,
    var chatFontSize: Int = DEFAULT_CHAT_FONT_SIZE,
    var chatPrefixColor: String = DEFAULT_PREFIX_COLOR,
    var chatCodeBackgroundColor: String = DEFAULT_CODE_BACKGROUND_COLOR,
    var chatCodeTextColor: String = DEFAULT_CODE_TEXT_COLOR,
    var chatCodeBorderColor: String = DEFAULT_CODE_BORDER_COLOR,
    var chatUserBackgroundColor: String = DEFAULT_USER_BACKGROUND_COLOR,
    var chatUserBorderColor: String = DEFAULT_USER_BORDER_COLOR,
    var yandexModelId: String = DEFAULT_YANDEX_MODEL_ID,
    var selectedProvider: String = DEFAULT_PROVIDER,
    var showProviderName: Boolean = false
) {
    companion object {
        const val DEFAULT_CHAT_FONT_SIZE = 9
        const val DEFAULT_PREFIX_COLOR = "#6b6b6b"
        const val DEFAULT_CODE_BACKGROUND_COLOR = "#2b2b2b"
        const val DEFAULT_CODE_TEXT_COLOR = "#e6e6e6"
        const val DEFAULT_CODE_BORDER_COLOR = "#444444"
        const val DEFAULT_USER_BACKGROUND_COLOR = "#28292D"
        const val DEFAULT_USER_BORDER_COLOR = "#6d8fd8"
        const val DEFAULT_YANDEX_MODEL_ID = "yandexgpt"
        const val DEFAULT_PROVIDER = "YandexGPT"
    }
}
