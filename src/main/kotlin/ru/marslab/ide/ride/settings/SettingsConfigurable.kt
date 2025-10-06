package ru.marslab.ide.ride.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.*
import com.intellij.util.SlowOperations
import java.awt.Color
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * UI для настроек плагина в IDE Settings
 */
class SettingsConfigurable : Configurable {

    private val settings = service<PluginSettings>()

    private lateinit var apiKeyField: JBPasswordField
    private lateinit var folderIdField: JBTextField
      private lateinit var temperatureField: JBTextField
    private lateinit var maxTokensField: JBTextField
    private lateinit var chatFontSizeField: JBTextField
    private lateinit var chatPrefixColorPanel: ColorPanel
    private lateinit var chatCodeBackgroundPanel: ColorPanel
    private lateinit var chatCodeTextPanel: ColorPanel
    private lateinit var chatCodeBorderPanel: ColorPanel
    private lateinit var chatUserBackgroundPanel: ColorPanel
    private lateinit var chatUserBorderPanel: ColorPanel
    private lateinit var modelComboBox: ComboBox<String>

    private var panel: DialogPanel? = null
    private var initialApiKey: String = ""
    private var apiKeyLoaded = false

    override fun getDisplayName(): String = "Ride"

    override fun createComponent(): JComponent {
        val llmConfigPanel = createLlmConfigPanel()
        val chatAppearancePanel = createChatAppearancePanel()
        val agentSettingsPanel = createAgentSettingsPanel()

        panel = panel {
            row {
                val tabs = JBTabbedPane()
                tabs.addTab("Chat Appearance", chatAppearancePanel)
                tabs.addTab("Agent Settings", agentSettingsPanel)
                tabs.addTab("LlmConfig", llmConfigPanel)
                cell(tabs)
                    .align(Align.FILL)
                    .resizableColumn()
            }
        }

        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val apiKeyModified = if (apiKeyLoaded) {
            apiKeyField.password.concatToString() != initialApiKey
        } else {
            false
        }

        val selectedModel = (modelComboBox.selectedItem as? String)?.trim().orEmpty()

        return apiKeyModified ||
                folderIdField.text != settings.folderId ||
                temperatureField.text != settings.temperature.toString() ||
                maxTokensField.text != settings.maxTokens.toString() ||
                chatFontSizeField.text != settings.chatFontSize.toString() ||
                colorToHex(
                    chatPrefixColorPanel.selectedColor,
                    PluginSettingsState.DEFAULT_PREFIX_COLOR
                ) != settings.chatPrefixColor ||
                colorToHex(
                    chatCodeBackgroundPanel.selectedColor,
                    PluginSettingsState.DEFAULT_CODE_BACKGROUND_COLOR
                ) != settings.chatCodeBackgroundColor ||
                colorToHex(
                    chatCodeTextPanel.selectedColor,
                    PluginSettingsState.DEFAULT_CODE_TEXT_COLOR
                ) != settings.chatCodeTextColor ||
                colorToHex(
                    chatCodeBorderPanel.selectedColor,
                    PluginSettingsState.DEFAULT_CODE_BORDER_COLOR
                ) != settings.chatCodeBorderColor ||
                colorToHex(
                    chatUserBackgroundPanel.selectedColor,
                    PluginSettingsState.DEFAULT_USER_BACKGROUND_COLOR
                ) != settings.chatUserBackgroundColor ||
                colorToHex(
                    chatUserBorderPanel.selectedColor,
                    PluginSettingsState.DEFAULT_USER_BORDER_COLOR
                ) != settings.chatUserBorderColor ||
                selectedModel != settings.yandexModelId
    }

    override fun apply() {
        // Сохраняем API ключ
        val apiKey = apiKeyField.password.concatToString()
        if (apiKey.isNotBlank()) {
            settings.saveApiKey(apiKey)
        }

        // Сохраняем остальные настройки
        settings.folderId = folderIdField.text.trim()
        settings.yandexModelId = modelComboBox.selectedItem as? String ?: PluginSettingsState.DEFAULT_YANDEX_MODEL_ID

        // Валидация и сохранение числовых параметров
        try {
            settings.temperature = temperatureField.text.toDouble()
        } catch (e: NumberFormatException) {
            settings.temperature = 0.7
        }

        try {
            settings.maxTokens = maxTokensField.text.toInt()
        } catch (e: NumberFormatException) {
            settings.maxTokens = 2000
        }

        try {
            settings.chatFontSize = chatFontSizeField.text.toInt()
        } catch (e: NumberFormatException) {
            settings.chatFontSize = PluginSettingsState.DEFAULT_CHAT_FONT_SIZE
        }

        settings.chatPrefixColor =
            colorToHex(chatPrefixColorPanel.selectedColor, PluginSettingsState.DEFAULT_PREFIX_COLOR)
        settings.chatCodeBackgroundColor =
            colorToHex(chatCodeBackgroundPanel.selectedColor, PluginSettingsState.DEFAULT_CODE_BACKGROUND_COLOR)
        settings.chatCodeTextColor =
            colorToHex(chatCodeTextPanel.selectedColor, PluginSettingsState.DEFAULT_CODE_TEXT_COLOR)
        settings.chatCodeBorderColor =
            colorToHex(chatCodeBorderPanel.selectedColor, PluginSettingsState.DEFAULT_CODE_BORDER_COLOR)
        settings.chatUserBackgroundColor =
            colorToHex(chatUserBackgroundPanel.selectedColor, PluginSettingsState.DEFAULT_USER_BACKGROUND_COLOR)
        settings.chatUserBorderColor =
            colorToHex(chatUserBorderPanel.selectedColor, PluginSettingsState.DEFAULT_USER_BORDER_COLOR)

        initialApiKey = apiKey
        apiKeyLoaded = true

        // Пересоздаем агента с новыми настройками
//        service<ru.marslab.ide.ride.service.ChatService>().recreateAgent()
    }

    override fun reset() {
        // Загружаем текущие настройки
        apiKeyLoaded = false
        initialApiKey = ""
        apiKeyField.text = ""
        ApplicationManager.getApplication().executeOnPooledThread {
            val key = settings.getApiKey()
            SwingUtilities.invokeLater {
                initialApiKey = key
                apiKeyLoaded = true
                apiKeyField.text = key
            }
        }
        folderIdField.text = settings.folderId
        modelComboBox.selectedItem = settings.yandexModelId
        temperatureField.text = settings.temperature.toString()
        maxTokensField.text = settings.maxTokens.toString()
        chatFontSizeField.text = settings.chatFontSize.toString()
        chatPrefixColorPanel.selectedColor =
            parseColor(settings.chatPrefixColor, PluginSettingsState.DEFAULT_PREFIX_COLOR)
        chatCodeBackgroundPanel.selectedColor =
            parseColor(settings.chatCodeBackgroundColor, PluginSettingsState.DEFAULT_CODE_BACKGROUND_COLOR)
        chatCodeTextPanel.selectedColor =
            parseColor(settings.chatCodeTextColor, PluginSettingsState.DEFAULT_CODE_TEXT_COLOR)
        chatCodeBorderPanel.selectedColor =
            parseColor(settings.chatCodeBorderColor, PluginSettingsState.DEFAULT_CODE_BORDER_COLOR)
        chatUserBackgroundPanel.selectedColor =
            parseColor(settings.chatUserBackgroundColor, PluginSettingsState.DEFAULT_USER_BACKGROUND_COLOR)
        chatUserBorderPanel.selectedColor =
            parseColor(settings.chatUserBorderColor, PluginSettingsState.DEFAULT_USER_BORDER_COLOR)
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun colorToHex(color: Color?, fallbackHex: String): String {
        val resolved = color ?: parseColor(fallbackHex, fallbackHex)
        return String.format("#%06X", resolved.rgb and 0xFFFFFF)
    }

    private fun parseColor(value: String, fallbackHex: String): Color {
        return runCatching { Color.decode(value) }.getOrElse { Color.decode(fallbackHex) }
    }

    private fun createLlmConfigPanel(): DialogPanel = panel {
        group("Yandex GPT Configuration") {
            row("API Key:") {
                apiKeyField = JBPasswordField()
                cell(apiKeyField)
                    .columns(COLUMNS_LARGE)
                    .comment("API ключ для доступа к Yandex GPT")
            }

            row("Folder ID:") {
                folderIdField = JBTextField()
                cell(folderIdField)
                    .columns(COLUMNS_LARGE)
                    .comment("Folder ID из Yandex Cloud")
            }

            row("Model:") {
                modelComboBox = comboBox(PluginSettings.AVAILABLE_YANDEX_MODELS.keys.toList())
                    .applyToComponent {
                        renderer = object : javax.swing.DefaultListCellRenderer() {
                            override fun getListCellRendererComponent(
                                list: javax.swing.JList<*>,
                                value: Any?,
                                index: Int,
                                isSelected: Boolean,
                                cellHasFocus: Boolean
                            ): java.awt.Component {
                                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                                val key = value as? String
                                text = PluginSettings.AVAILABLE_YANDEX_MODELS[key] ?: key.orEmpty()
                                return component
                            }
                        }
                    }
                    .component
            }
        }

        row {
            comment(
                """
                Как получить API ключ:
                1. Перейдите в Yandex Cloud Console
                2. Создайте API ключ в разделе "Сервисные аккаунты"
                3. Скопируйте Folder ID из настроек проекта
            """.trimIndent()
            )
        }
    }

    private fun createChatAppearancePanel(): DialogPanel = panel {
        group("Fonts and Colors") {
            row("Font Size:") {
                chatFontSizeField = JBTextField()
                cell(chatFontSizeField)
                    .columns(8)
                    .comment("Размер шрифта (8-32)")
            }

            row("Font Color:") {
                chatPrefixColorPanel = ColorPanel()
                cell(chatPrefixColorPanel)
                    .align(AlignX.LEFT)
                    .comment("Цвет подписей сообщений")
            }
        }

        group("Code Block Colors") {
            row("Background:") {
                chatCodeBackgroundPanel = ColorPanel()
                cell(chatCodeBackgroundPanel)
                    .align(AlignX.LEFT)
            }

            row("Text:") {
                chatCodeTextPanel = ColorPanel()
                cell(chatCodeTextPanel)
                    .align(AlignX.LEFT)
            }

            row("Border:") {
                chatCodeBorderPanel = ColorPanel()
                cell(chatCodeBorderPanel)
                    .align(AlignX.LEFT)
            }
        }

        group("User Message Colors") {
            row("Background:") {
                chatUserBackgroundPanel = ColorPanel()
                cell(chatUserBackgroundPanel)
                    .align(AlignX.LEFT)
            }

            row("Border:") {
                chatUserBorderPanel = ColorPanel()
                cell(chatUserBorderPanel)
                    .align(AlignX.LEFT)
            }
        }
    }

    private fun createAgentSettingsPanel(): DialogPanel = panel {
        group("Agent Settings") {
            row("Temperature:") {
                temperatureField = JBTextField()
                cell(temperatureField)
                    .columns(10)
                    .comment("Температура генерации (0.0 - 1.0)")
            }

            row("Max Tokens:") {
                maxTokensField = JBTextField()
                cell(maxTokensField)
                    .columns(10)
                    .comment("Максимальное количество токенов в ответе")
            }
        }
    }
}
