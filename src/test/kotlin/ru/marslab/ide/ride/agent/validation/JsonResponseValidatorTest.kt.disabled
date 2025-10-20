package ru.marslab.ide.ride.agent.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ru.marslab.ide.ride.model.ParsedResponse
import ru.marslab.ide.ride.model.ResponseSchema
import kotlinx.serialization.json.Json

class JsonResponseValidatorTest {

    private val validator = JsonResponseValidator()
    private val json = Json { }

    @Test
    fun `validates ok when matches example schema`() {
        val schema = ResponseSchema.json("""
            {
              "answer": "string",
              "confidence": 0.0
            }
        """.trimIndent())
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"answer":"ok","confidence":0.95}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `fails when field missing`() {
        val schema = ResponseSchema.json("""{"answer": "string", "confidence": 0.0}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"answer":"ok"}""")
        )
        val err = validator.validate(parsed, schema)
        // must report missing field
        require(err != null)
    }

    @Test
    fun `fails when type mismatch`() {
        val schema = ResponseSchema.json("""{"confidence": 0.0}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"confidence":"high"}""")
        )
        val err = validator.validate(parsed, schema)
        require(err != null)
    }
}
