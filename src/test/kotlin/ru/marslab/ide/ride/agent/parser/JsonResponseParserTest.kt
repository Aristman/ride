package ru.marslab.ide.ride.agent.parser

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import ru.marslab.ide.ride.model.ParsedResponse
import ru.marslab.ide.ride.model.ResponseSchema

class JsonResponseParserTest {

    private val parser = JsonResponseParser()

    @Test
    fun `parses plain json`() {
        val raw = """{"answer": "ok", "score": 0.9}"""
        val res = parser.parse(raw, ResponseSchema.json("{}"))
        assertIs<ParsedResponse.JsonResponse>(res)
        assertTrue { res.jsonElement.toString().contains("answer") }
    }

    @Test
    fun `parses markdown json block`() {
        val raw = """
            ```json
            {"answer":"ok"}
            ```
        """.trimIndent()
        val res = parser.parse(raw, ResponseSchema.json("{}"))
        assertIs<ParsedResponse.JsonResponse>(res)
    }

    @Test
    fun `returns ParseError on invalid json`() {
        val raw = "{" // invalid
        val res = parser.parse(raw, ResponseSchema.json("{}"))
        assertIs<ParsedResponse.ParseError>(res)
    }
}
