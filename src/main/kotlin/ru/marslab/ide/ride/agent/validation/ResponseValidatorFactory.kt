package ru.marslab.ide.ride.agent.validation

import ru.marslab.ide.ride.model.schema.ParsedResponse
import ru.marslab.ide.ride.model.schema.ResponseSchema
import ru.marslab.ide.ride.model.schema.ResponseFormat

object ResponseValidatorFactory {
    private val jsonValidator = JsonResponseValidator()
    private val xmlValidator = XmlResponseValidator()

    fun getValidator(format: ResponseFormat): ResponseValidator = when (format) {
        ResponseFormat.JSON -> jsonValidator
        ResponseFormat.XML -> xmlValidator
        ResponseFormat.TEXT -> NoopValidator
    }

    private object NoopValidator : ResponseValidator {
        override fun validate(parsed: ParsedResponse, schema: ResponseSchema): String? = null
    }
}
