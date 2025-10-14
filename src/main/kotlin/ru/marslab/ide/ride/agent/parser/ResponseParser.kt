package ru.marslab.ide.ride.agent.parser

import ru.marslab.ide.ride.model.schema.ParsedResponse
import ru.marslab.ide.ride.model.schema.ResponseSchema

/**
 * Интерфейс для парсинга ответов от LLM в заданном формате
 */
interface ResponseParser {
    /**
     * Парсит ответ от LLM согласно схеме
     * 
     * @param rawContent Сырой текст ответа от LLM
     * @param schema Схема для валидации (опционально)
     * @return Распарсенный ответ или ошибка парсинга
     */
    fun parse(rawContent: String, schema: ResponseSchema? = null): ParsedResponse
    
    /**
     * Проверяет, может ли парсер обработать данную схему
     * 
     * @param schema Схема для проверки
     * @return true если парсер поддерживает данную схему
     */
    fun supports(schema: ResponseSchema): Boolean
}
