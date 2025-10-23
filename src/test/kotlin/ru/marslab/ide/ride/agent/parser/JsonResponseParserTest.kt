package ru.marslab.ide.ride.agent.parser

import junit.framework.TestCase.assertFalse
import ru.marslab.ide.ride.model.schema.JsonResponseSchema
import ru.marslab.ide.ride.model.schema.ParsedResponse
import ru.marslab.ide.ride.model.schema.ResponseFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonResponseParserTest {

    private val parser = JsonResponseParser()

    @Test
    fun `parses plain json`() {
        val raw = """{"answer": "ok", "score": 0.9}"""
        val schema = JsonResponseSchema.create("{}")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.JsonResponse>(res)
        assertTrue { res.jsonElement.toString().contains("answer") }
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.JSON, res.format)
    }

    @Test
    fun `parses markdown json block`() {
        val raw = """
            ```json
            {"answer":"ok"}
            ```
        """.trimIndent()
        val schema = JsonResponseSchema.create("{}")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.JsonResponse>(res)
        assertNotNull(res.jsonElement)
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.JSON, res.format)
    }

    @Test
    fun `parses nested json`() {
        val raw = """
            {
                "user": {
                    "name": "John",
                    "age": 30
                },
                "active": true
            }
        """.trimIndent()
        val schema = JsonResponseSchema.create("{}")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.JsonResponse>(res)
        assertTrue { res.jsonElement.toString().contains("John") }
        assertTrue { res.jsonElement.toString().contains("30") }
    }

    @Test
    fun `returns ParseError on invalid json`() {
        val raw = "{" // invalid
        val schema = JsonResponseSchema.create("{}")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.ParseError>(res)
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.JSON, res.format)
        assertNotNull(res.error)
        assertTrue(res.error!!.contains("Ошибка парсинга JSON"))
    }

    @Test
    fun `returns ParseError on empty string`() {
        val raw = ""
        val schema = JsonResponseSchema.create("{}")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.ParseError>(res)
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.JSON, res.format)
        assertNotNull(res.error)
    }

    @Test
    fun `handles json with extra fields`() {
        val raw = """{"answer": "ok", "extra": "field", "score": 0.9}"""
        val schema = JsonResponseSchema.create("""{"answer": "string"}""")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.JsonResponse>(res)
        // Should parse successfully with ignoreUnknownKeys = true
        assertTrue { res.jsonElement.toString().contains("answer") }
        assertTrue { res.jsonElement.toString().contains("extra") }
    }

    @Test
    fun `parses json array`() {
        val raw = """["item1", "item2", "item3"]"""
        val schema = JsonResponseSchema.create("""["string"]""")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.JsonResponse>(res)
        assertTrue { res.jsonElement.toString().contains("item1") }
        assertEquals(raw, res.rawContent)
    }

    @Test
    fun `supports JSON format`() {
        val jsonSchema = JsonResponseSchema.create("{}")
        val xmlSchema = ru.marslab.ide.ride.model.schema.XmlResponseSchema.create("<root></root>")
        val textSchema = ru.marslab.ide.ride.model.schema.TextResponseSchema.create("test")

        assertTrue(parser.supports(jsonSchema))
        assertFalse(parser.supports(xmlSchema))
        assertFalse(parser.supports(textSchema))
    }

    @Test
    fun `handles unicode in json`() {
        val raw = """{"message": "Привет мир", "emoji": "🚀"}"""
        val schema = JsonResponseSchema.create("{}")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.JsonResponse>(res)
        assertTrue { res.jsonElement.toString().contains("Привет мир") }
        assertTrue { res.jsonElement.toString().contains("🚀") }
    }

    @Test
    fun `parses json with numbers and booleans`() {
        val raw = """
            {
                "count": 42,
                "active": true,
                "ratio": 3.14
            }
        """.trimIndent()
        val schema = JsonResponseSchema.create("{}")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.JsonResponse>(res)
        assertTrue { res.jsonElement.toString().contains("42") }
        assertTrue { res.jsonElement.toString().contains("true") }
        assertTrue { res.jsonElement.toString().contains("3.14") }
    }

    @Test
    fun `parses json with null values`() {
        val raw = """
            {
                "field": "value",
                "nullable": null
            }
        """.trimIndent()
        val schema = JsonResponseSchema.create("""{"field": "string", "nullable": "string"}""")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.JsonResponse>(res)
        assertTrue { res.jsonElement.toString().contains("value") }
        assertTrue { res.jsonElement.toString().contains("null") }
    }

    @Test
    fun `JsonResponse toFormattedString works`() {
        val raw = """{"test": "data"}"""
        val schema = JsonResponseSchema.create("{}")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.JsonResponse>(res)
        val jsonResponse = res as ParsedResponse.JsonResponse
        val formatted = jsonResponse.toFormattedString()

        assertTrue(formatted.contains("test"))
        assertTrue(formatted.contains("data"))
    }
}