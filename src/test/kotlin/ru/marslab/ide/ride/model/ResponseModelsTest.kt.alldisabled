package ru.marslab.ide.ride.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseModelsTest {

    @Test
    fun `response schema validity - json`() {
        val s = ResponseSchema.json("{" + "\"a\": \"string\"" + "}")
        assertTrue(s.isValid())
        assertEquals(ResponseFormat.JSON, s.format)
    }

    @Test
    fun `response schema validity - xml`() {
        val s = ResponseSchema.xml("<response><a>string</a></response>")
        assertTrue(s.isValid())
        assertEquals(ResponseFormat.XML, s.format)
    }

    @Test
    fun `response schema validity - text`() {
        val s = ResponseSchema.text("plain text")
        assertTrue(s.isValid())
        assertEquals(ResponseFormat.TEXT, s.format)
    }
}
