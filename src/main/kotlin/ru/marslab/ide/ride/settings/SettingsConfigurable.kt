package ru.marslab.ide.ride.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.ColorPanel
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.compound
import com.intellij.util.ui.JBUI.Borders.customLine
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.CurrentTheme.Label.disabledForeground
import com.intellij.util.ui.JBUI.CurrentTheme.ToolWindow.borderColor
import ru.marslab.ide.ride.agent.tools.EmbeddingIndexerToolAgent
import ru.marslab.ide.ride.model.embedding.IndexingResult
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.service.ChatService
import ru.marslab.ide.ride.service.embedding.IndexingStatusService
import ru.marslab.ide.ride.service.rules.RuleItem
import ru.marslab.ide.ride.service.rules.RulesService
import ru.marslab.ide.ride.ui.RuleListItem
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.*

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
    private lateinit var maxContextTokensField: JBTextField
    private lateinit var enableAutoSummarizationCheck: JBCheckBox
    private lateinit var enableRagEnrichmentCheck: JBCheckBox
    // RAG params UI
    private lateinit var ragTopKField: JBTextField
    private lateinit var ragCandidateKField: JBTextField
    private lateinit var ragSimilarityThresholdField: JBTextField
    private lateinit var ragRerankerStrategyComboBox: ComboBox<String>
    private lateinit var ragMmrLambdaField: JBTextField
    private lateinit var ragMmrTopKField: JBTextField
    private lateinit var ragSourceLinksEnabledCheck: JBCheckBox

    // Custom Rules UI components
    private lateinit var globalRulesList: JPanel
    private lateinit var projectRulesList: JPanel
    private lateinit var globalRulesScroll: JScrollPane
    private lateinit var projectRulesScroll: JScrollPane
    private lateinit var addGlobalRuleButton: JButton
    private lateinit var addProjectRuleButton: JButton

    // Списки правил
    private val globalRules = mutableListOf<RuleItem>()
    private val projectRules = mutableListOf<RuleItem>()

    private var panel: DialogPanel? = null
    private var initialApiKey: String = ""
    private var apiKeyLoaded = false
    private var initialHFToken: String = ""
    private var hfTokenLoaded = false

    // Управление индексацией
    private lateinit var startIndexButton: JButton
    private lateinit var clearIndexButton: JButton
    private lateinit var stopIndexButton: JButton

    @Volatile
    private var indexingTask: java.util.concurrent.Future<*>? = null

    // Embedding indexer UI components
    private lateinit var indexProgressBar: javax.swing.JProgressBar
    private lateinit var currentFileLabel: javax.swing.JLabel
    private lateinit var indexingSummaryArea: javax.swing.JTextArea
    private lateinit var indexingScrollPane: javax.swing.JScrollPane

    override fun getDisplayName(): String = "Ride"

    override fun createComponent(): JComponent {
        val llmConfigPanel = createLlmConfigPanel()
        val chatAppearancePanel = createChatAppearancePanel()
        val agentSettingsPanel = createAgentSettingsPanel()
        val codeSettingsPanel = createCodeSettingsPanel()
        val rulesPanel = createRulesPanel()

        panel = panel {
            row {
                val tabs = JBTabbedPane()
                tabs.addTab("Chat Appearance", chatAppearancePanel)
                tabs.addTab("Agent Settings", agentSettingsPanel)
                tabs.addTab("Code Settings", codeSettingsPanel)
                tabs.addTab("Rules", rulesPanel)
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
                enableUncertaintyAnalysisCheck.isSelected != settings.enableUncertaintyAnalysis ||
                maxContextTokensField.text != settings.maxContextTokens.toString() ||
                enableAutoSummarizationCheck.isSelected != settings.enableAutoSummarization ||
                enableRagEnrichmentCheck.isSelected != settings.enableRagEnrichment ||
                ragTopKField.text != settings.ragTopK.toString() ||
                ragCandidateKField.text != settings.ragCandidateK.toString() ||
                ragSimilarityThresholdField.text != settings.ragSimilarityThreshold.toString() ||
                (ragRerankerStrategyComboBox.selectedItem as? String).orEmpty() != settings.ragRerankerStrategy ||
                ragMmrLambdaField.text != settings.ragMmrLambda.toString() ||
                ragMmrTopKField.text != settings.ragMmrTopK.toString() ||
                ragSourceLinksEnabledCheck.isSelected != settings.ragSourceLinksEnabled
    }

    override fun apply() {
        // Определяем выбранную модель/провайдер сверху и сохраняем
        val selectedTop = modelSelectorComboBox.selectedItem as? String ?: PluginSettings.PROVIDER_YANDEX
        when (selectedTop) {
            PluginSettings.PROVIDER_YANDEX -> {
                settings.selectedProvider = PluginSettings.PROVIDER_YANDEX
                settings.yandexModelId =
                    yandexModelSelectorComboBox.selectedItem as? String ?: PluginSettingsState.DEFAULT_YANDEX_MODEL_ID
            }

            PluginSettings.PROVIDER_HUGGINGFACE -> {
                settings.selectedProvider = PluginSettings.PROVIDER_HUGGINGFACE
                settings.huggingFaceModelId =
                    hfModelSelectorComboBox.selectedItem as? String ?: PluginSettingsState.DEFAULT_HUGGINGFACE_MODEL_ID
            }

            else -> {
                settings.selectedProvider = PluginSettings.PROVIDER_YANDEX
                settings.yandexModelId =
                    yandexModelSelectorComboBox.selectedItem as? String ?: PluginSettingsState.DEFAULT_YANDEX_MODEL_ID
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

        // Настройки управления токенами
        try {
            settings.maxContextTokens = maxContextTokensField.text.toInt()
        } catch (e: NumberFormatException) {
            settings.maxContextTokens = PluginSettingsState.DEFAULT_MAX_CONTEXT_TOKENS
        }
        settings.enableAutoSummarization = enableAutoSummarizationCheck.isSelected
        settings.enableRagEnrichment = enableRagEnrichmentCheck.isSelected

        // RAG parameters (with validation and coercion)
        try {
            val topK = ragTopKField.text.toInt()
            settings.ragTopK = topK
        } catch (_: NumberFormatException) {
            settings.ragTopK = PluginSettingsState.DEFAULT_RAG_TOP_K
        }
        try {
            val candidateK = ragCandidateKField.text.toInt()
            settings.ragCandidateK = candidateK
        } catch (_: NumberFormatException) {
            settings.ragCandidateK = PluginSettingsState.DEFAULT_RAG_CANDIDATE_K
        }
        try {
            val thr = ragSimilarityThresholdField.text.toFloat()
            settings.ragSimilarityThreshold = thr
        } catch (_: NumberFormatException) {
            settings.ragSimilarityThreshold = PluginSettingsState.DEFAULT_RAG_SIMILARITY_THRESHOLD
        }
        settings.ragRerankerStrategy = (ragRerankerStrategyComboBox.selectedItem as? String) ?: PluginSettingsState.DEFAULT_RAG_RERANKER_STRATEGY
        // MMR params
        try {
            val mmrLambda = ragMmrLambdaField.text.toFloat()
            settings.ragMmrLambda = mmrLambda
        } catch (_: NumberFormatException) {
            settings.ragMmrLambda = PluginSettingsState.DEFAULT_RAG_MMR_LAMBDA
        }
        try {
            val mmrTopK = ragMmrTopKField.text.toInt()
            settings.ragMmrTopK = mmrTopK
        } catch (_: NumberFormatException) {
            settings.ragMmrTopK = PluginSettingsState.DEFAULT_RAG_MMR_TOP_K
        }

        // RAG Source Links
        settings.ragSourceLinksEnabled = ragSourceLinksEnabledCheck.isSelected

        // Очищаем кеш правил при изменении настроек
        service<RulesService>().clearCache()

        initialApiKey = apiKey
        apiKeyLoaded = true
        initialHFToken = hfToken
        hfTokenLoaded = true

        // Пересоздаём агента с новыми настройками (моментальная смена LLM в чате)
        service<ChatService>().recreateAgent()
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
        maxContextTokensField.text = settings.maxContextTokens.toString()
        enableAutoSummarizationCheck.isSelected = settings.enableAutoSummarization
        enableRagEnrichmentCheck.isSelected = settings.enableRagEnrichment
        // RAG params
        ragTopKField.text = settings.ragTopK.toString()
        ragCandidateKField.text = settings.ragCandidateK.toString()
        ragSimilarityThresholdField.text = settings.ragSimilarityThreshold.toString()
        ragRerankerStrategyComboBox.selectedItem = settings.ragRerankerStrategy
        ragMmrLambdaField.text = settings.ragMmrLambda.toString()
        ragMmrTopKField.text = settings.ragMmrTopK.toString()
        ragSourceLinksEnabledCheck.isSelected = settings.ragSourceLinksEnabled
        updateMmrVisibility()

        // Обновляем списки правил после загрузки настроек
        SwingUtilities.invokeLater {
            loadRules()
        }
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
            renderer = object : DefaultListCellRenderer() {
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
            renderer = object : DefaultListCellRenderer() {
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
            renderer = object : DefaultListCellRenderer() {
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

        group("Token Management") {
            row("Max Context Tokens:") {
                maxContextTokensField = JBTextField()
                cell(maxContextTokensField)
                    .columns(10)
                    .comment("Максимальное количество токенов в контексте (запрос + история). Рекомендуется: 8000")
            }

            row {
                enableAutoSummarizationCheck = JBCheckBox("Включить автоматическое сжатие истории")
                cell(enableAutoSummarizationCheck)
                    .comment("При превышении лимита токенов история будет автоматически сжиматься через SummarizerAgent")
            }
        }

        group("RAG Enrichment") {
            row {
                enableRagEnrichmentCheck = JBCheckBox("Включить обогащение запросов через RAG")
                cell(enableRagEnrichmentCheck)
                    .comment("Добавлять релевантные фрагменты из индексированных файлов проекта в запросы к LLM")
            }
            row("Reranker Strategy:") {
                ragRerankerStrategyComboBox = ComboBox(arrayOf("THRESHOLD", "MMR")).apply {
                    addActionListener { updateMmrVisibility() }
                }
                cell(ragRerankerStrategyComboBox)
                    .align(AlignX.LEFT)
                    .comment("Стратегия второго этапа: THRESHOLD (порог) или MMR (диверсификация)")
            }
            row("Top K:") {
                ragTopKField = JBTextField()
                cell(ragTopKField)
                    .columns(6)
                    .comment("Сколько фрагментов оставить после фильтрации. Диапазон: 1–10. По умолчанию 5")
            }
            row("Candidate K:") {
                ragCandidateKField = JBTextField()
                cell(ragCandidateKField)
                    .columns(6)
                    .comment("Сколько кандидатов запрашивать на первом этапе. Диапазон: 30–100. По умолчанию 60")
            }
            row("Similarity threshold:") {
                ragSimilarityThresholdField = JBTextField()
                cell(ragSimilarityThresholdField)
                    .columns(6)
                    .comment("Порог релевантности (0..1). По умолчанию 0.25")
            }
            row("MMR lambda:") {
                ragMmrLambdaField = JBTextField()
                cell(ragMmrLambdaField)
                    .columns(6)
                    .comment("Баланс релевантности/разнообразия [0..1]. По умолчанию 0.5")
            }
            row("MMR Top K:") {
                ragMmrTopKField = JBTextField()
                cell(ragMmrTopKField)
                    .columns(6)
                    .comment("Размер результата после MMR. По умолчанию равен Top K")
            }
            row {
                ragSourceLinksEnabledCheck = JBCheckBox("Включить ссылки на источники в ответах RAG")
                cell(ragSourceLinksEnabledCheck)
                    .comment("Добавлять кликабельные ссылки на исходный код в ответах RAG")
            }
        }

    }

    private fun updateMmrVisibility() {
        val strategy = (ragRerankerStrategyComboBox.selectedItem as? String).orEmpty()
        val visible = strategy == "MMR"
        ragMmrLambdaField.isEnabled = visible
        ragMmrTopKField.isEnabled = visible
    }

    private fun createCodeSettingsPanel(): DialogPanel = panel {
        group("Embedding Indexer") {
            row {
                comment("Индексация файлов проекта для семантического поиска")
            }
            row {
                startIndexButton = JButton("Запустить индексацию").apply {
                    addActionListener { startEmbeddingIndexing() }
                }
                cell(startIndexButton)
                clearIndexButton = JButton("Очистить индекс").apply {
                    addActionListener { clearEmbeddingIndex() }
                }
                cell(clearIndexButton)
                val statsButton = JButton("Показать статистику").apply {
                    addActionListener { showIndexStatistics() }
                }
                cell(statsButton)
                stopIndexButton = JButton("Остановить индексацию").apply {
                    isEnabled = false
                    addActionListener { stopEmbeddingIndexing() }
                }
                cell(stopIndexButton)
            }
            row {
                indexProgressBar = javax.swing.JProgressBar(0, 100).apply {
                    isIndeterminate = false
                    value = 0
                    isStringPainted = true
                    isVisible = true
                }
                cell(indexProgressBar)
                    .align(Align.FILL)
                    .resizableColumn()
            }
            row("Текущий файл:") {
                currentFileLabel = javax.swing.JLabel("")
                cell(currentFileLabel)
                    .align(Align.FILL)
                    .resizableColumn()
            }
            row("Итоги индексации:") {
                indexingSummaryArea = javax.swing.JTextArea(5, 60).apply {
                    lineWrap = true
                    wrapStyleWord = true
                    isEditable = false
                }
                indexingScrollPane = javax.swing.JScrollPane(indexingSummaryArea)
                cell(indexingScrollPane)
                    .align(Align.FILL)
                    .resizableColumn()
                    .comment("После завершения индексации здесь появится сводка")
            }
        }

        group("Embedding Configuration") {
            row {
                comment("Используется локальная модель nomic-embed-text через Ollama для генерации эмбеддингов.")
            }
            row {
                comment("Модель: nomic-embed-text (768 dimensions)")
            }
            row {
                comment("Убедитесь, что Ollama запущен и модель установлена: ollama pull nomic-embed-text")
            }
        }
    }

    private fun startEmbeddingIndexing() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        if (project == null) {
            showErrorDialog(
                "Нет открытых проектов",
                "Ошибка"
            )
            return
        }

        // Обновляем состояние кнопок
        SwingUtilities.invokeLater { updateIndexingButtons(true) }
        // Фиксируем глобальный статус индексации
        service<IndexingStatusService>().setInProgress(true)

        val future = ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val agent = EmbeddingIndexerToolAgent()
                val projectPath = project.basePath ?: return@executeOnPooledThread

                // Устанавливаем callback для прогресса
                agent.setProgressCallback { progress ->
                    SwingUtilities.invokeLater {
                        if (!this::indexProgressBar.isInitialized) return@invokeLater
                        indexProgressBar.isIndeterminate = false
                        indexProgressBar.isVisible = true
                        indexProgressBar.value = progress.percentComplete.coerceIn(0, 100)
                        indexProgressBar.string =
                            "${progress.percentComplete}% (${progress.filesProcessed}/${progress.totalFiles})"
                        if (this::currentFileLabel.isInitialized) {
                            currentFileLabel.text = progress.currentFile
                        }
                    }
                    // Обновляем глобальный статус
                    service<IndexingStatusService>().updateProgress(
                        IndexingStatusService.Progress(
                            percent = progress.percentComplete,
                            filesProcessed = progress.filesProcessed,
                            totalFiles = progress.totalFiles,
                            currentFile = progress.currentFile
                        )
                    )
                }

                // Запускаем индексацию
                kotlinx.coroutines.runBlocking {
                    val step = ToolPlanStep(
                        description = "Index project files",
                        agentType = AgentType.EMBEDDING_INDEXER,
                        input = StepInput.empty()
                            .set("action", "index")
                            .set("project_path", projectPath)
                            .set("force_reindex", false)
                    )

                    val result = agent.executeStep(
                        step,
                        ExecutionContext(projectPath)
                    )

                    SwingUtilities.invokeLater {
                        // Прогрессбар не скрываем – он отображает уровень индексации
                        updateIndexingButtons(false)

                        if (result.success) {
                            // Пытаемся извлечь сводку из результата
                            val data = result.output.data
                            val resultObj = data["result"]
                            var summary = ""
                            when (resultObj) {
                                is IndexingResult -> {
                                    summary = "Файлов обработано: ${resultObj.filesProcessed}\n" +
                                            "Чанков создано: ${resultObj.chunksCreated}\n" +
                                            "Эмбеддингов сгенерировано: ${resultObj.embeddingsGenerated}\n" +
                                            "Длительность: ${resultObj.durationMs} мс"
                                }

                                is Map<*, *> -> {
                                    val filesProcessed = (resultObj["filesProcessed"] as? Number)?.toInt() ?: 0
                                    val chunksCreated = (resultObj["chunksCreated"] as? Number)?.toInt() ?: 0
                                    val embeddingsGenerated =
                                        (resultObj["embeddingsGenerated"] as? Number)?.toInt() ?: 0
                                    val durationMs = (resultObj["durationMs"] as? Number)?.toLong() ?: 0L
                                    summary = "Файлов обработано: ${filesProcessed}\n" +
                                            "Чанков создано: ${chunksCreated}\n" +
                                            "Эмбеддингов сгенерировано: ${embeddingsGenerated}\n" +
                                            "Длительность: ${durationMs} мс"
                                }
                            }

                            if (this@SettingsConfigurable::indexingSummaryArea.isInitialized) {
                                this@SettingsConfigurable.indexingSummaryArea.text = summary
                            }

                            Messages.showInfoMessage(
                                summary,
                                "Индексация завершена"
                            )
                        } else {
                            showErrorDialog(
                                "Ошибка индексации: ${result.error}",
                                "Ошибка"
                            )
                        }
                    }
                    // Завершаем глобальный статус
                    service<IndexingStatusService>().setInProgress(false)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    // Прогрессбар не скрываем – он отображает уровень индексации
                    updateIndexingButtons(false)
                    showErrorDialog(
                        "Ошибка: ${e.message}",
                        "Ошибка"
                    )
                }
                // Завершаем глобальный статус при ошибке
                service<IndexingStatusService>().setInProgress(false)
            }
        }
        indexingTask = future
    }

    private fun stopEmbeddingIndexing() {
        val task = indexingTask
        if (task != null && !task.isDone) {
            task.cancel(true)
        }
        SwingUtilities.invokeLater {
            // Прогрессбар не скрываем – он отображает уровень индексации
            updateIndexingButtons(false)
            Messages.showInfoMessage(
                "Индексация остановлена пользователем",
                "Остановлено"
            )
        }
        service<IndexingStatusService>().setInProgress(false)
    }

    private fun updateIndexingButtons(inProgress: Boolean) {
        if (this::startIndexButton.isInitialized) startIndexButton.isEnabled = !inProgress
        if (this::clearIndexButton.isInitialized) clearIndexButton.isEnabled = !inProgress
        if (this::stopIndexButton.isInitialized) stopIndexButton.isEnabled = inProgress
    }

    private fun clearEmbeddingIndex() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        if (project == null) {
            showErrorDialog(
                "Нет открытых проектов",
                "Ошибка"
            )
            return
        }

        val confirm = Messages.showYesNoDialog(
            "Вы уверены, что хотите очистить индекс?",
            "Подтверждение",
            Messages.getQuestionIcon()
        )

        if (confirm == Messages.YES) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val agent = EmbeddingIndexerToolAgent()
                    val projectPath = project.basePath ?: return@executeOnPooledThread

                    kotlinx.coroutines.runBlocking {
                        val step = ToolPlanStep(
                            description = "Clear index",
                            agentType = AgentType.EMBEDDING_INDEXER,
                            input = ru.marslab.ide.ride.model.tool.StepInput.empty()
                                .set("action", "clear")
                                .set("project_path", projectPath)
                        )

                        val result = agent.executeStep(
                            step,
                            ExecutionContext(projectPath)
                        )

                        SwingUtilities.invokeLater {
                            if (result.success) {
                                Messages.showInfoMessage(
                                    "Индекс очищен",
                                    "Успех"
                                )
                            } else {
                                showErrorDialog(
                                    "Ошибка: ${'$'}{result.error}",
                                    "Ошибка"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        showErrorDialog(
                            "Ошибка: ${'$'}{e.message}",
                            "Ошибка"
                        )
                    }
                }
            }
        }
    }

    private fun showIndexStatistics() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        if (project == null) {
            showErrorDialog(
                "Нет открытых проектов",
                "Ошибка"
            )
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val agent = EmbeddingIndexerToolAgent()
                val projectPath = project.basePath ?: return@executeOnPooledThread

                kotlinx.coroutines.runBlocking {
                    val step = ToolPlanStep(
                        description = "Get statistics",
                        agentType = AgentType.EMBEDDING_INDEXER,
                        input = ru.marslab.ide.ride.model.tool.StepInput.empty()
                            .set("action", "stats")
                            .set("project_path", projectPath)
                    )

                    val result = agent.executeStep(
                        step,
                        ExecutionContext(projectPath)
                    )

                    SwingUtilities.invokeLater {
                        if (result.success) {
                            val stats = result.output.get<Map<String, Int>>("statistics") ?: emptyMap()
                            val message = """
                                Статистика индекса:
                                Файлов: ${stats["files"] ?: 0}
                                Чанков: ${stats["chunks"] ?: 0}
                                Эмбеддингов: ${stats["embeddings"] ?: 0}
                            """.trimIndent()
                            Messages.showInfoMessage(
                                message,
                                "Статистика"
                            )
                        } else {
                            showErrorDialog(
                                "Ошибка: ${result.error}",
                                "Ошибка"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showErrorDialog(
                        "Ошибка: ${e.message}",
                        "Ошибка"
                    )
                }
            }
        }
    }

    /**
     * Создает панель настроек правил
     */
    private fun createRulesPanel(): DialogPanel = panel {
        group("Custom Rules") {
            row {
                cell(JBLabel("Глобальные правила")).bold()
                addGlobalRuleButton = JButton("+").apply {
                    toolTipText = "Добавить новое глобальное правило"
                    preferredSize = Dimension(10, 30)
                    addActionListener {
                        showAddRuleDialog(true)
                    }
                }
                cell(addGlobalRuleButton).align(AlignX.RIGHT)
            }
            row {
                globalRulesList = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    preferredSize = Dimension(300, 200)
                    border = compound(
                        customLine(borderColor(), 1),
                        empty(5)
                    )
                }
                globalRulesScroll = JScrollPane(globalRulesList).apply {
                    preferredSize = Dimension(300, 200)
                    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                }
                cell(globalRulesScroll)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }

            row {
                // Разделитель
                cell(JSeparator()).align(AlignX.FILL)
            }

            row {
                cell(JBLabel("Проектные правила")).bold()
                addProjectRuleButton = JButton("+").apply {
                    toolTipText = "Добавить новое проектное правило"
                    preferredSize = Dimension(30, 30)
                    addActionListener {
                        showAddRuleDialog(false)
                    }
                }
                cell(addProjectRuleButton).align(AlignX.RIGHT)
            }
            row {
                projectRulesList = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    border = compound(
                        customLine(borderColor(), 1),
                        empty(5)
                    )
                }
                projectRulesScroll = JScrollPane(projectRulesList).apply {
                    preferredSize = Dimension(300, 200)
                    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                }
                cell(projectRulesScroll)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }

            row {
                cell(JBLabel("Примечание: правила применяются только когда активны (✓)").apply {
                    font = font.deriveFont(font.size - 2f)
                    foreground = disabledForeground()
                }).align(AlignX.LEFT)
            }
        }

        // Инициализируем списки правил при создании панели
        SwingUtilities.invokeLater {
            loadRules()
        }
    }

    /**
     * Загружает правила из файловой системы с учетом активных
     */
    private fun loadRules() {
        try {
            val rulesService = service<RulesService>()
            val settings = service<PluginSettings>()
            val project = ProjectManager.getInstance().openProjects.firstOrNull()

            // Очищаем текущие списки
            globalRules.clear()
            projectRules.clear()

            // Загружаем активные глобальные правила
            val activeGlobalRules = settings.getActiveGlobalRules()
            val globalDir = rulesService.getGlobalRulesDirectory()
            if (globalDir.exists()) {
                val globalFiles = globalDir.listFiles { file ->
                    file.isFile && file.extension.equals("md", ignoreCase = true)
                }?.sortedBy { it.name }?.toList() ?: emptyList()

                globalFiles.forEach { file ->
                    val fileName = file.nameWithoutExtension
                    val isActive = activeGlobalRules[fileName] ?: true // По умолчанию активно
                    val rule = RuleItem(fileName = fileName, isActive = isActive, isGlobal = true)
                    globalRules.add(rule)
                }
            }

            // Загружаем активные проектные правила
            if (project != null) {
                val activeProjectRules = settings.getActiveProjectRules()
                val projectDir = rulesService.getProjectRulesDirectory(project)
                if (projectDir?.exists() == true) {
                    val projectFiles = projectDir.listFiles { file ->
                        file.isFile && file.extension.equals("md", ignoreCase = true)
                    }?.sortedBy { it.name }?.toList() ?: emptyList()

                    projectFiles.forEach { file ->
                        val fileName = file.nameWithoutExtension
                        val isActive = activeProjectRules[fileName] ?: true // По умолчанию активно
                        val rule = RuleItem(fileName = fileName, isActive = isActive, isGlobal = false)
                        projectRules.add(rule)
                    }
                }
            }

            // Обновляем UI
            updateRulesLists()
        } catch (e: Exception) {
            showErrorDialog(
                "Ошибка при загрузке правил: ${e.message}",
                "Ошибка"
            )
        }
    }

    /**
     * Обновляет списки правил в UI
     */
    private fun updateRulesLists() {
        val settings = service<PluginSettings>()

        // Обновляем глобальные правила
        globalRulesList.removeAll()
        globalRules.forEach { rule ->
            val component = RuleListItem(
                fileName = rule.fileName,
                isActive = rule.isActive,
                isGlobal = true,
                onDelete = { fileName ->
                    deleteRule(fileName, true)
                },
                onToggleActive = { fileName, isActive ->
                    val index = globalRules.indexOfFirst { it.fileName == fileName }
                    if (index >= 0) {
                        globalRules[index] = globalRules[index].copy(isActive = isActive)
                        // Сохраняем активность правила
                        settings.updateRuleActivity(fileName, isActive, true)
                    }
                },
                onOpenFile = { fileName, isGlobal ->
                    openRuleFileForEditing(fileName, isGlobal)
                }
            )
            globalRulesList.add(component)
            globalRulesList.add(Box.createVerticalStrut(2))
        }

        // Обновляем проектные правила
        projectRulesList.removeAll()
        projectRules.forEach { rule ->
            val component = RuleListItem(
                fileName = rule.fileName,
                isActive = rule.isActive,
                isGlobal = false,
                onDelete = { fileName ->
                    deleteRule(fileName, false)
                },
                onToggleActive = { fileName, isActive ->
                    val index = projectRules.indexOfFirst { it.fileName == fileName }
                    if (index >= 0) {
                        projectRules[index] = projectRules[index].copy(isActive = isActive)
                        // Сохраняем активность правила
                        settings.updateRuleActivity(fileName, isActive, false)
                    }
                },
                onOpenFile = { fileName, isGlobal ->
                    openRuleFileForEditing(fileName, isGlobal)
                }
            )
            projectRulesList.add(component)
            projectRulesList.add(Box.createVerticalStrut(2))
        }

        globalRulesList.revalidate()
        globalRulesList.repaint()
        projectRulesList.revalidate()
        projectRulesList.repaint()
    }

    /**
     * Показывает диалог для добавления нового правила
     */
    private fun showAddRuleDialog(isGlobal: Boolean) {
        val title = if (isGlobal) "Новое глобальное правило" else "Новое проектное правило"
        val message = "Введите имя файла правила (без расширения .md):"

        val fileName = Messages.showInputDialog(
            message,
            title,
            Messages.getQuestionIcon(),
            "",
            null
        )?.trim()

        if (fileName.isNullOrBlank()) {
            return
        }

        // Проверяем корректность имени файла
        if (!fileName.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            Messages.showErrorDialog(
                "Имя файла может содержать только буквы, цифры, подчеркивания и дефисы",
                "Некорректное имя файла"
            )
            return
        }

        // Проверяем, что такого файла еще нет
        val existingRules = if (isGlobal) globalRules else projectRules
        if (existingRules.any { it.fileName.equals(fileName, ignoreCase = true) }) {
            Messages.showErrorDialog(
                "Правило с таким именем уже существует",
                "Ошибка"
            )
            return
        }

        // Создаем файл правила
        try {
            val rulesService = service<RulesService>()
            val project = if (!isGlobal) ProjectManager.getInstance().openProjects.firstOrNull() else null

            val ruleFile = if (isGlobal) {
                val globalDir = rulesService.getGlobalRulesDirectory()
                if (rulesService.ensureRulesDirectoryExists(true)) {
                    globalDir.resolve("$fileName.md")
                } else {
                    throw Exception("Не удалось создать директорию глобальных правил")
                }
            } else {
                if (project == null) {
                    throw Exception("Нет открытого проекта")
                }
                val projectDir = rulesService.getProjectRulesDirectory(project)
                    ?: throw Exception("Не удалось определить директорию проектных правил")
                if (rulesService.ensureRulesDirectoryExists(false, project)) {
                    projectDir.resolve("$fileName.md")
                } else {
                    throw Exception("Не удалось создать директорию проектных правил")
                }
            }

            // Создаем файл с шаблоном
            val templateContent = """# $fileName

## Описание
*Описание правила*

## Когда применять
*Условия применения правила*

## Примеры
*Примеры использования правила*
"""
            ruleFile.writeText(templateContent)

            // Добавляем правило в список (по умолчанию активно)
            val newRule = RuleItem(fileName = fileName, isActive = true, isGlobal = isGlobal)
            if (isGlobal) {
                globalRules.add(newRule)
            } else {
                projectRules.add(newRule)
            }

            // Сохраняем активность правила в настройках
            val settings = service<PluginSettings>()
            if (isGlobal) {
                val currentRules = settings.getActiveGlobalRules().toMutableMap()
                currentRules[fileName] = true
                settings.setActiveGlobalRules(currentRules)
            } else {
                val currentRules = settings.getActiveProjectRules().toMutableMap()
                currentRules[fileName] = true
                settings.setActiveProjectRules(currentRules)
            }

            // Обновляем UI
            updateRulesLists()

            // Спрашиваем, открыть ли файл для редактирования
            val shouldOpen = Messages.showYesNoDialog(
                "Правило '$fileName.md' создано. Открыть для редактирования?",
                "Правило создано",
                Messages.getQuestionIcon()
            ) == Messages.YES

            if (shouldOpen) {
                openRuleFileForEditing(newRule.fileName, newRule.isGlobal)
            }

        } catch (e: Exception) {
            Messages.showErrorDialog(
                "Ошибка при создании правила: ${e.message}",
                "Ошибка"
            )
        }
    }

    /**
     * Удаляет правило
     */
    private fun deleteRule(fileName: String, isGlobal: Boolean) {
        val result = Messages.showYesNoDialog(
            "Удалить правило '$fileName'?",
            "Подтверждение удаления",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            try {
                val rulesService = service<RulesService>()
                val settings = service<PluginSettings>()
                val project = if (!isGlobal) ProjectManager.getInstance().openProjects.firstOrNull() else null

                val ruleFile = if (isGlobal) {
                    val globalDir = rulesService.getGlobalRulesDirectory()
                    globalDir.resolve("$fileName.md")
                } else {
                    if (project == null) {
                        throw Exception("Нет открытого проекта")
                    }
                    val projectDir = rulesService.getProjectRulesDirectory(project)
                        ?: throw Exception("Не удалось определить директорию проектных правил")
                    projectDir.resolve("$fileName.md")
                }

                if (ruleFile.exists()) {
                    ruleFile.delete()
                }

                // Удаляем из списка
                if (isGlobal) {
                    globalRules.removeAll { it.fileName == fileName }
                } else {
                    projectRules.removeAll { it.fileName == fileName }
                }

                // Удаляем активность правила из настроек
                if (isGlobal) {
                    val currentRules = settings.getActiveGlobalRules().toMutableMap()
                    currentRules.remove(fileName)
                    settings.setActiveGlobalRules(currentRules)
                } else {
                    val currentRules = settings.getActiveProjectRules().toMutableMap()
                    currentRules.remove(fileName)
                    settings.setActiveProjectRules(currentRules)
                }

                // Обновляем UI
                updateRulesLists()

            } catch (e: Exception) {
                Messages.showErrorDialog(
                    "Ошибка при удалении правила: ${e.message}",
                    "Ошибка"
                )
            }
        }
    }

    /**
     * Открывает файл правила для редактирования
     */
    private fun openRuleFileForEditing(fileName: String, isGlobal: Boolean) {
        try {
            val project = if (!isGlobal) {
                ProjectManager.getInstance().openProjects.firstOrNull()
            } else null
            val rulesService = service<RulesService>()

            val file = if (isGlobal) {
                val globalDir = rulesService.getGlobalRulesDirectory()
                globalDir.resolve("$fileName.md")
            } else {
                if (project == null) {
                    throw Exception("Нет открытого проекта")
                }
                val projectDir = rulesService.getProjectRulesDirectory(project)
                    ?: throw Exception("Не удалось определить директорию проектных правил")
                projectDir.resolve("$fileName.md")
            }

            if (file.exists()) {
                // Открываем файл в редакторе
                val virtualFile = VirtualFileManager.getInstance().findFileByUrl(file.toURI().toString())
                if (virtualFile != null && project != null) {
                    FileEditorManager.getInstance(project).openFile(virtualFile, true)

                    // Закрываем настройки через OK
                    SwingUtilities.invokeLater {
                        val window = SwingUtilities.getWindowAncestor(panel)
                        if (window is java.awt.Dialog) {
                            window.dispose()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "Ошибка при открытии файла: ${e.message}",
                "Ошибка"
            )
        }
    }
}
