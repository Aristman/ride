package ru.marslab.ide.ride.agent.parser

import kotlin.test.Test
import kotlin.test.assertIs
import ru.marslab.ide.ride.model.ParsedResponse
import ru.marslab.ide.ride.model.ResponseSchema

class XmlResponseParserTest {

    private val parser = XmlResponseParser()

    @Test
    fun `parses plain xml`() {
        val raw = "<response><answer>ok</answer></response>"
        val res = parser.parse(raw, ResponseSchema.xml("<response><answer>string</answer></response>"))
        assertIs<ParsedResponse.XmlResponse>(res)
    }

    @Test
    fun `parses markdown xml block`() {
        val raw = """
            ```xml
            <response><answer>ok</answer></response>
            ```
        """.trimIndent()
        val res = parser.parse(raw, ResponseSchema.xml("<response></response>"))
        assertIs<ParsedResponse.XmlResponse>(res)
    }

    @Test
    fun `returns ParseError on invalid xml`() {
        val raw = "<response>" // invalid
        val res = parser.parse(raw, ResponseSchema.xml("<response></response>"))
        assertIs<ParsedResponse.ParseError>(res)
    }
}
