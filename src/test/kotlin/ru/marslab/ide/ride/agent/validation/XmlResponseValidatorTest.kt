package ru.marslab.ide.ride.agent.validation

import kotlin.test.Test
import kotlin.test.assertNull
import ru.marslab.ide.ride.model.ParsedResponse
import ru.marslab.ide.ride.model.ResponseSchema

class XmlResponseValidatorTest {

    private val validator = XmlResponseValidator()

    @Test
    fun `valid when root tag matches`() {
        val schema = ResponseSchema.xml("""
            <response>
                <answer>string</answer>
            </response>
        """.trimIndent())
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<response><answer>ok</answer></response>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }
}
