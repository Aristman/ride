package ru.marslab.ide.ride.agent.formatter

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema

class PromptFormatterTest {

    @Test
    fun `returns base prompt when no schema`() {
        val base = "User request"
        val result = PromptFormatter.formatPrompt(base, null)
        assertEquals(base, result)
    }

    @Test
    fun `adds JSON instructions when schema provided`() {
        val base = "User request"
        val schema = ResponseSchema.json("""{"answer": "string"}""", "Test JSON")
        val result = PromptFormatter.formatPrompt(base, schema)
        
        assertTrue(result.contains("User request"))
        assertTrue(result.contains("JSON"))
        assertTrue(result.contains("Test JSON"))
        assertTrue(result.contains("""{"answer": "string"}"""))
    }

    @Test
    fun `adds XML instructions when schema provided`() {
        val base = "User request"
        val schema = ResponseSchema.xml("<response><answer>string</answer></response>", "Test XML")
        val result = PromptFormatter.formatPrompt(base, schema)
        
        assertTrue(result.contains("User request"))
        assertTrue(result.contains("XML"))
        assertTrue(result.contains("Test XML"))
    }

    @Test
    fun `adds TEXT instructions when description provided`() {
        val base = "User request"
        val schema = ResponseSchema.text("Provide detailed explanation")
        val result = PromptFormatter.formatPrompt(base, schema)
        
        assertTrue(result.contains("User request"))
        assertTrue(result.contains("Provide detailed explanation"))
    }
}
