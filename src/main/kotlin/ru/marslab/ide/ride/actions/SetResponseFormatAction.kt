package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.Nullable
import ru.marslab.ide.ride.model.schema.ResponseFormat
import ru.marslab.ide.ride.model.schema.JsonResponseSchema
import ru.marslab.ide.ride.model.schema.XmlResponseSchema
import ru.marslab.ide.ride.service.ChatService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Единый Action для выбора формата ответа и ввода схемы.
 * Размещается в меню: Tools → Ride → Set Response Format...
 */
class SetResponseFormatAction : AnAction("Set Response Format...") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val chatService = service<ChatService>()

        // Текущие значения для префила
        val currentFormat = chatService.getResponseFormat()
        val currentSchema = chatService.getResponseSchema()?.schemaDefinition

        val dialog = ResponseFormatDialog(project, currentFormat, currentSchema)
        if (dialog.showAndGet()) {
            val selectedFormat = dialog.getSelectedFormat()
            val schemaText = dialog.getSchemaText().orEmpty()

            when (selectedFormat) {
                ResponseFormat.TEXT -> {
                    chatService.clearResponseFormat()
                    Messages.showInfoMessage(project, "Формат сброшен на TEXT.", "Формат изменён")
                }
                ResponseFormat.JSON -> {
                    val schema = if (schemaText.isBlank())
                        JsonResponseSchema.create("{}", "Структурированный JSON ответ")
                    else
                        JsonResponseSchema.create(schemaText, "Пользовательская JSON схема")
                    chatService.setResponseFormat(ResponseFormat.JSON, schema)
                    Messages.showInfoMessage(project, "JSON формат установлен.", "Формат изменён")
                }
                ResponseFormat.XML -> {
                    val schema = if (schemaText.isBlank())
                        XmlResponseSchema.create("<root></root>", "Структурированный XML ответ")
                    else
                        XmlResponseSchema.create(schemaText, "Пользовательская XML схема")
                    chatService.setResponseFormat(ResponseFormat.XML, schema)
                    Messages.showInfoMessage(project, "XML формат установлен.", "Формат изменён")
                }
            }
        }
    }
}

private class ResponseFormatDialog(
    project: com.intellij.openapi.project.Project?,
    private val defaultFormat: ResponseFormat?,
    private val defaultSchema: String?
) : DialogWrapper(project) {
    private val jsonRadio = JRadioButton("JSON")
    private val xmlRadio = JRadioButton("XML")
    private val textRadio = JRadioButton("TEXT")
    private val schemaArea = JTextArea(10, 50)

    init {
        title = "Set Response Format"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))

        val radios = JPanel()
        val group = ButtonGroup()
        group.add(jsonRadio)
        group.add(xmlRadio)
        group.add(textRadio)
        radios.layout = BoxLayout(radios, BoxLayout.X_AXIS)
        radios.add(jsonRadio)
        radios.add(Box.createHorizontalStrut(10))
        radios.add(xmlRadio)
        radios.add(Box.createHorizontalStrut(10))
        radios.add(textRadio)

        val schemaScroll = JScrollPane(schemaArea)
        schemaScroll.preferredSize = Dimension(600, 180)

        val hint = JLabel("Введите схему для JSON/XML. Для TEXT схема не требуется.")

        panel.add(radios, BorderLayout.NORTH)
        panel.add(schemaScroll, BorderLayout.CENTER)
        panel.add(hint, BorderLayout.SOUTH)

        // Префил значений
        when (defaultFormat) {
            ResponseFormat.JSON -> jsonRadio.isSelected = true
            ResponseFormat.XML -> xmlRadio.isSelected = true
            ResponseFormat.TEXT, null -> textRadio.isSelected = true
        }
        if (!defaultSchema.isNullOrBlank()) {
            schemaArea.text = defaultSchema
        }

        // Переключение доступности поля схемы
        val updateSchemaEnabled = {
            schemaArea.isEnabled = jsonRadio.isSelected || xmlRadio.isSelected
        }
        val listener = { _: java.awt.event.ActionEvent -> updateSchemaEnabled() }
        jsonRadio.addActionListener(listener)
        xmlRadio.addActionListener(listener)
        textRadio.addActionListener(listener)
        updateSchemaEnabled()

        return panel
    }

    fun getSelectedFormat(): ResponseFormat = when {
        jsonRadio.isSelected -> ResponseFormat.JSON
        xmlRadio.isSelected -> ResponseFormat.XML
        else -> ResponseFormat.TEXT
    }

    fun getSchemaText(): String? = schemaArea.text
}
