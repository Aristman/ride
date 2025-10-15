package ru.marslab.ide.ride.agent.validation

import ru.marslab.ide.ride.model.schema.ParsedResponse
import ru.marslab.ide.ride.model.schema.ResponseSchema

/**
 * Интерфейс валидатора распарсенных ответов относительно схемы
 */
interface ResponseValidator {
    /**
     * Валидирует распарсенный ответ относительно схемы
     * @return null если валиден, иначе текст ошибки
     */
    fun validate(parsed: ParsedResponse, schema: ResponseSchema): String?
}
