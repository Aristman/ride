package ru.marslab.ide.ride.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

/**
 * UI для настроек плагина в IDE Settings
 */
class SettingsConfigurable : Configurable {
    
    private val settings = service<PluginSettings>()
    
    private lateinit var apiKeyField: JBPasswordField
    private lateinit var folderIdField: JBTextField
    private lateinit var systemPromptArea: JBTextArea
    private lateinit var temperatureField: JBTextField
    private lateinit var maxTokensField: JBTextField
    
    private var panel: DialogPanel? = null
    
    override fun getDisplayName(): String = "Ride"
    
    override fun createComponent(): JComponent {
        panel = panel {
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
            }
            
            group("Agent Settings") {
                row("System Prompt:") {
                    systemPromptArea = JBTextArea()
                    systemPromptArea.rows = 8
                    cell(systemPromptArea)
                        .columns(COLUMNS_LARGE)
                        .comment("Системный промпт для агента")
                }
                
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
            
            row {
                comment("""
                    <html>
                    <b>Как получить API ключ:</b><br>
                    1. Перейдите в <a href="https://console.cloud.yandex.ru">Yandex Cloud Console</a><br>
                    2. Создайте API ключ в разделе "Сервисные аккаунты"<br>
                    3. Скопируйте Folder ID из настроек проекта
                    </html>
                """.trimIndent())
            }
        }
        
        reset()
        return panel!!
    }
    
    override fun isModified(): Boolean {
        return apiKeyField.password.concatToString() != settings.getApiKey() ||
                folderIdField.text != settings.folderId ||
                systemPromptArea.text != settings.systemPrompt ||
                temperatureField.text != settings.temperature.toString() ||
                maxTokensField.text != settings.maxTokens.toString()
    }
    
    override fun apply() {
        // Сохраняем API ключ
        val apiKey = apiKeyField.password.concatToString()
        if (apiKey.isNotBlank()) {
            settings.saveApiKey(apiKey)
        }
        
        // Сохраняем остальные настройки
        settings.folderId = folderIdField.text.trim()
        settings.systemPrompt = systemPromptArea.text
        
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
        
        // Пересоздаем агента с новыми настройками
        service<ru.marslab.ide.ride.service.ChatService>().recreateAgent()
    }
    
    override fun reset() {
        // Загружаем текущие настройки
        apiKeyField.text = settings.getApiKey()
        folderIdField.text = settings.folderId
        systemPromptArea.text = settings.systemPrompt
        temperatureField.text = settings.temperature.toString()
        maxTokensField.text = settings.maxTokens.toString()
    }
    
    override fun disposeUIResources() {
        panel = null
    }
}
