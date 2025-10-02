package ru.marslab.ide.ride.agent.parser

import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema

/**
 * Фабрика для создания парсеров ответов
 */
object ResponseParserFactory {
    
    private val jsonParser = JsonResponseParser()
    private val xmlParser = XmlResponseParser()
    private val textParser = TextResponseParser()
    
    /**
     * Возвращает парсер для указанного формата
     * 
     * @param format Формат ответа
     * @return Соответствующий парсер
     */
    fun getParser(format: ResponseFormat): ResponseParser {
        return when (format) {
            ResponseFormat.JSON -> jsonParser
            ResponseFormat.XML -> xmlParser
            ResponseFormat.TEXT -> textParser
        }
    }
    
    /**
     * Возвращает парсер для указанной схемы
     * 
     * @param schema Схема ответа
     * @return Соответствующий парсер
     */
    fun getParser(schema: ResponseSchema): ResponseParser {
        return getParser(schema.format)
    }
}
