package ru.marslab.ide.ride.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.awt.CardLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * UI для настроек плагина в IDE Settings
 */
class SettingsConfigurable : Configurable {

    private val settings = service<PluginSettings>()

    private lateinit var apiKeyField: JBPasswordField
    private lateinit var hfTokenField: JBPasswordField
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
    private lateinit var modelSelectorComboBox: ComboBox<String>
    private lateinit var hfModelSelectorComboBox: ComboBox<String>
    private lateinit var yandexModelSelectorComboBox: ComboBox<String>
    private lateinit var showProviderNameCheck: JBCheckBox
    private lateinit var enableUncertaintyAnalysisCheck: JBCheckBox

    private var panel: DialogPanel? = null
    private var initialApiKey: String = ""
    private var apiKeyLoaded = false
    private var initialHFToken: String = ""
    private var hfTokenLoaded = false

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
        val hfTokenModified = if (hfTokenLoaded) {
            hfTokenField.password.concatToString() != initialHFToken
        } else {
            false
        }

        val selectedTop = (modelSelectorComboBox.selectedItem as? String)?.trim().orEmpty()
        val expectedTop = when (settings.selectedProvider) {
            PluginSettings.PROVIDER_YANDEX -> PluginSettings.PROVIDER_YANDEX
            PluginSettings.PROVIDER_HUGGINGFACE -> PluginSettings.PROVIDER_HUGGINGFACE
            else -> PluginSettings.PROVIDER_YANDEX
        }
        val selectedHFModel = (hfModelSelectorComboBox.selectedItem as? String)?.trim().orEmpty()
        val selectedYandexModel = (yandexModelSelectorComboBox.selectedItem as? String)?.trim().orEmpty()

        return apiKeyModified ||
                hfTokenModified ||
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
                selectedTop != expectedTop ||
                selectedHFModel != settings.huggingFaceModelId ||
                selectedYandexModel != settings.yandexModelId ||
                showProviderNameCheck.isSelected != settings.showProviderName ||
                enableUncertaintyAnalysisCheck.isSelected != settings.enableUncertaintyAnalysis
    }

    override fun apply() {
        // Определяем выбранную модель/провайдер сверху и сохраняем
        val selectedTop = modelSelectorComboBox.selectedItem as? String ?: PluginSettings.PROVIDER_YANDEX
        when (selectedTop) {
            PluginSettings.PROVIDER_YANDEX -> {
                settings.selectedProvider = PluginSettings.PROVIDER_YANDEX
                settings.yandexModelId = yandexModelSelectorComboBox.selectedItem as? String ?: PluginSettingsState.DEFAULT_YANDEX_MODEL_ID
            }
            PluginSettings.PROVIDER_HUGGINGFACE -> {
                settings.selectedProvider = PluginSettings.PROVIDER_HUGGINGFACE
                settings.huggingFaceModelId = hfModelSelectorComboBox.selectedItem as? String ?: PluginSettingsState.DEFAULT_HUGGINGFACE_MODEL_ID
            }
            else -> {
                settings.selectedProvider = PluginSettings.PROVIDER_YANDEX
                settings.yandexModelId = yandexModelSelectorComboBox.selectedItem as? String ?: PluginSettingsState.DEFAULT_YANDEX_MODEL_ID
            }
        }

        // Сохраняем токены в зависимости от провайдера
        val apiKey = apiKeyField.password.concatToString()
        val hfToken = hfTokenField.password.concatToString()
        when (settings.selectedProvider) {
            PluginSettings.PROVIDER_YANDEX -> {
                if (apiKey.isNotBlank()) settings.saveApiKey(apiKey)
            }
            PluginSettings.PROVIDER_HUGGINGFACE -> {
                if (hfToken.isNotBlank()) settings.saveHuggingFaceToken(hfToken)
            }
        }

        // Сохраняем остальные настройки
        settings.folderId = folderIdField.text.trim()

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

        // Флаг отображения имени провайдера в чате
        settings.showProviderName = showProviderNameCheck.isSelected
        
        // Флаг анализа неопределенности
        settings.enableUncertaintyAnalysis = enableUncertaintyAnalysisCheck.isSelected

        initialApiKey = apiKey
        apiKeyLoaded = true
        initialHFToken = hfToken
        hfTokenLoaded = true

        // Пересоздаём агента с новыми настройками (моментальная смена LLM в чате)
        service<ru.marslab.ide.ride.service.ChatService>().recreateAgent()
    }

    override fun reset() {
        // Загружаем текущие настройки
        apiKeyLoaded = false
        initialApiKey = ""
        apiKeyField.text = ""
        hfTokenLoaded = false
        initialHFToken = ""
        hfTokenField.text = ""
        ApplicationManager.getApplication().executeOnPooledThread {
            val key = settings.getApiKey()
            SwingUtilities.invokeLater {
                initialApiKey = key
                apiKeyLoaded = true
                apiKeyField.text = key
            }
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val token = settings.getHuggingFaceToken()
            SwingUtilities.invokeLater {
                initialHFToken = token
                hfTokenLoaded = true
                hfTokenField.text = token
            }
        }
        folderIdField.text = settings.folderId
        // Устанавливаем верхний выбор: провайдер
        modelSelectorComboBox.selectedItem = when (settings.selectedProvider) {
            PluginSettings.PROVIDER_YANDEX -> PluginSettings.PROVIDER_YANDEX
            PluginSettings.PROVIDER_HUGGINGFACE -> PluginSettings.PROVIDER_HUGGINGFACE
            else -> PluginSettings.PROVIDER_YANDEX
        }
        // Устанавливаем модели
        hfModelSelectorComboBox.selectedItem = settings.huggingFaceModelId
        yandexModelSelectorComboBox.selectedItem = settings.yandexModelId
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
        showProviderNameCheck.isSelected = settings.showProviderName
        enableUncertaintyAnalysisCheck.isSelected = settings.enableUncertaintyAnalysis
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

    private fun createLlmConfigPanel(): DialogPanel {
        // Верхний комбобокс: только провайдеры
        val topEntries: List<String> = buildList {
            add(PluginSettings.PROVIDER_YANDEX)
            add(PluginSettings.PROVIDER_HUGGINGFACE)
        }
        modelSelectorComboBox = ComboBox(topEntries.toTypedArray()).apply {
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
                    text = PluginSettings.AVAILABLE_PROVIDERS[key] ?: key.orEmpty()
                    return component
                }
            }
        }

        // Комбобокс для выбора моделей HuggingFace
        hfModelSelectorComboBox = ComboBox(PluginSettings.AVAILABLE_HUGGINGFACE_MODELS.keys.toTypedArray()).apply {
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
                    text = PluginSettings.AVAILABLE_HUGGINGFACE_MODELS[key] ?: key.orEmpty()
                    return component
                }
            }
        }

        // Комбобокс для выбора моделей Yandex
        yandexModelSelectorComboBox = ComboBox(PluginSettings.AVAILABLE_YANDEX_MODELS.keys.toTypedArray()).apply {
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

        // Подпанель Yandex
        val yandexSubPanel: DialogPanel = panel {
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
                cell(yandexModelSelectorComboBox)
                    .align(Align.FILL)
                    .resizableColumn()
                    .comment("Выберите модель Yandex GPT")
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

        // Подпанель Hugging Face
        val hfSubPanel: DialogPanel = panel {
            row("Token:") {
                hfTokenField = JBPasswordField()
                cell(hfTokenField)
                    .columns(COLUMNS_LARGE)
                    .comment("Токен Hugging Face (Settings -> Access Tokens -> New token)")
            }
            row("Model:") {
                cell(hfModelSelectorComboBox)
                    .align(Align.FILL)
                    .resizableColumn()
                    .comment("Выберите модель HuggingFace")
            }
            row {
                comment(
                    """
                    Подсказка: используйте токен Hugging Face с правами access inference API.
                    Все модели доступны через HuggingFace Router API.
                """.trimIndent()
                )
            }
        }

        // CardLayout со вложенными подпанелями
        val cardPanel = JPanel(CardLayout())
        val CARD_YANDEX = PluginSettings.PROVIDER_YANDEX
        val CARD_HF = PluginSettings.PROVIDER_HUGGINGFACE
        cardPanel.add(yandexSubPanel, CARD_YANDEX)
        cardPanel.add(hfSubPanel, CARD_HF)

        fun updateCard() {
            val layout = cardPanel.layout as CardLayout
            val selected = modelSelectorComboBox.selectedItem as? String
            when (selected) {
                PluginSettings.PROVIDER_YANDEX -> {
                    layout.show(cardPanel, CARD_YANDEX)
                }
                PluginSettings.PROVIDER_HUGGINGFACE -> {
                    layout.show(cardPanel, CARD_HF)
                }
                else -> {
                    layout.show(cardPanel, CARD_YANDEX)
                }
            }
        }
        modelSelectorComboBox.addActionListener { updateCard() }

        // Основная панель вкладки LlmConfig
        val root = panel {
            row("Provider / Model:") {
                cell(modelSelectorComboBox)
                    .align(Align.FILL)
                    .resizableColumn()
            }
            row { cell(cardPanel).align(Align.FILL) }
            row {
                showProviderNameCheck = JBCheckBox("Показывать имя модели рядом с Агент")
                cell(showProviderNameCheck)
            }
        }

        // Инициализируем карточку
        SwingUtilities.invokeLater { updateCard() }
        return root
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
            
            row {
                enableUncertaintyAnalysisCheck = JBCheckBox("Включить анализ неопределенности")
                cell(enableUncertaintyAnalysisCheck)
                    .comment("Если включено, агент будет задавать уточняющие вопросы при неопределенности > 0.1")
            }
        }
    }
}
