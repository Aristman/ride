package ru.marslab.ide.ride.agent.parser

import kotlin.test.Test
import kotlin.test.assertIs
import ru.marslab.ide.ride.model.ParsedResponse
import ru.marslab.ide.ride.model.ResponseSchema

class TextResponseParserTest {

    private val parser = TextResponseParser()

    @Test
    fun `parses plain text`() {
        val raw = "Just a plain text"
        val res = parser.parse(raw, ResponseSchema.text("any text"))
        assertIs<ParsedResponse.TextResponse>(res)
    }
}
