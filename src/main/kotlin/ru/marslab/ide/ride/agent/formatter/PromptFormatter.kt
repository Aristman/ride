package ru.marslab.ide.ride.agent.formatter

import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema

/**
 * Форматтер для создания промптов с учетом требуемого формата ответа
 */
object PromptFormatter {
    
    /**
     * Добавляет инструкции по форматированию к промпту
     * 
     * @param basePrompt Базовый промпт
     * @param schema Схема ответа (если задана)
     * @return Промпт с инструкциями по форматированию
     */
    fun formatPrompt(basePrompt: String, schema: ResponseSchema?): String {
        if (schema == null) {
            return basePrompt
        }
        
        val formatInstructions = when (schema.format) {
            ResponseFormat.JSON -> buildJsonInstructions(schema)
            ResponseFormat.XML -> buildXmlInstructions(schema)
            ResponseFormat.TEXT -> buildTextInstructions(schema)
        }
        
        return """
$basePrompt

$formatInstructions
        """.trimIndent()
    }
    
    /**
     * Создает инструкции для JSON формата
     */
    private fun buildJsonInstructions(schema: ResponseSchema): String {
        val instructions = StringBuilder()
        
        instructions.append("ВАЖНО: Ответ должен быть в формате JSON.\n")
        
        if (schema.description.isNotBlank()) {
            instructions.append("Описание: ${schema.description}\n")
        }
        
        if (schema.schemaDefinition.isNotBlank()) {
            instructions.append("\nТребуемая структура JSON:\n")
            instructions.append("```json\n")
            instructions.append(schema.schemaDefinition)
            instructions.append("\n```\n")
        }
        
        instructions.append("\nПравила:\n")
        instructions.append("- Ответ должен быть валидным JSON\n")
        instructions.append("- Используй двойные кавычки для строк\n")
        instructions.append("- Не добавляй комментарии в JSON\n")
        instructions.append("- Можешь обернуть JSON в markdown блок ```json ... ```\n")
        
        return instructions.toString()
    }
    
    /**
     * Создает инструкции для XML формата
     */
    private fun buildXmlInstructions(schema: ResponseSchema): String {
        val instructions = StringBuilder()
        
        instructions.append("ВАЖНО: Ответ должен быть в формате XML.\n")
        
        if (schema.description.isNotBlank()) {
            instructions.append("Описание: ${schema.description}\n")
        }
        
        if (schema.schemaDefinition.isNotBlank()) {
            instructions.append("\nТребуемая структура XML:\n")
            instructions.append("```xml\n")
            instructions.append(schema.schemaDefinition)
            instructions.append("\n```\n")
        }
        
        instructions.append("\nПравила:\n")
        instructions.append("- Ответ должен быть валидным XML\n")
        instructions.append("- Используй корректные открывающие и закрывающие теги\n")
        instructions.append("- Можешь обернуть XML в markdown блок ```xml ... ```\n")
        
        return instructions.toString()
    }
    
    /**
     * Создает инструкции для текстового формата
     */
    private fun buildTextInstructions(schema: ResponseSchema): String {
        if (schema.description.isBlank()) {
            return ""
        }
        
        return """
ВАЖНО: Формат ответа - обычный текст.
${schema.description}
        """.trimIndent()
    }
}
