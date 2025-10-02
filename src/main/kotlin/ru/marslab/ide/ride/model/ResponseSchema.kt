package ru.marslab.ide.ride.model

/**
 * Схема для структурированного ответа
 * 
 * @property format Формат ответа
 * @property schemaDefinition Определение схемы (JSON Schema, XML Schema или описание)
 * @property description Описание ожидаемого ответа для LLM
 */
data class ResponseSchema(
    val format: ResponseFormat,
    val schemaDefinition: String,
    val description: String = ""
) {
    companion object {
        /**
         * Создает схему для JSON формата
         * 
         * @param schema JSON Schema или пример структуры
         * @param description Описание для LLM
         */
        fun json(schema: String, description: String = ""): ResponseSchema {
            return ResponseSchema(
                format = ResponseFormat.JSON,
                schemaDefinition = schema,
                description = description
            )
        }
        
        /**
         * Создает схему для XML формата
         * 
         * @param schema XML Schema или пример структуры
         * @param description Описание для LLM
         */
        fun xml(schema: String, description: String = ""): ResponseSchema {
            return ResponseSchema(
                format = ResponseFormat.XML,
                schemaDefinition = schema,
                description = description
            )
        }
        
        /**
         * Создает схему для текстового формата
         * 
         * @param description Описание ожидаемого формата текста
         */
        fun text(description: String = ""): ResponseSchema {
            return ResponseSchema(
                format = ResponseFormat.TEXT,
                schemaDefinition = "",
                description = description
            )
        }
    }
    
    /**
     * Проверяет, является ли схема валидной
     */
    fun isValid(): Boolean {
        return when (format) {
            ResponseFormat.JSON, ResponseFormat.XML -> schemaDefinition.isNotBlank()
            ResponseFormat.TEXT -> true
        }
    }
}
