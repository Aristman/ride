package ru.marslab.ide.ride.agent.formatter

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import ru.marslab.ide.ride.model.schema.ResponseFormat
import ru.marslab.ide.ride.model.schema.JsonResponseSchema
import ru.marslab.ide.ride.model.schema.XmlResponseSchema
import ru.marslab.ide.ride.model.schema.TextResponseSchema

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
        val schema = JsonResponseSchema.create("""{"answer": "string"}""", "Test JSON")
        val result = PromptFormatter.formatPrompt(base, schema)

        assertTrue(result.contains("User request"))
        assertTrue(result.contains("JSON"))
        assertTrue(result.contains("Test JSON"))
        assertTrue(result.contains("""{"answer": "string"}"""))
        assertTrue(result.contains("ВАЖНО: Ответ должен быть в формате JSON"))
        assertTrue(result.contains("Правила:"))
    }

    @Test
    fun `adds XML instructions when schema provided`() {
        val base = "User request"
        val schema = XmlResponseSchema.create("<response><answer>string</answer></response>", "Test XML")
        val result = PromptFormatter.formatPrompt(base, schema)

        assertTrue(result.contains("User request"))
        assertTrue(result.contains("XML"))
        assertTrue(result.contains("Test XML"))
        assertTrue(result.contains("ВАЖНО: Ответ должен быть в формате XML"))
        assertTrue(result.contains("Правила:"))
    }

    @Test
    fun `adds TEXT instructions when description provided`() {
        val base = "User request"
        val schema = TextResponseSchema.create("Provide detailed explanation")
        val result = PromptFormatter.formatPrompt(base, schema)

        assertTrue(result.contains("User request"))
        assertTrue(result.contains("Provide detailed explanation"))
        assertTrue(result.contains("ВАЖНО: Формат ответа - обычный текст"))
    }

    @Test
    fun `handles empty TEXT schema gracefully`() {
        val base = "User request"
        val schema = TextResponseSchema.create("")
        val result = PromptFormatter.formatPrompt(base, schema)

        // Even empty TEXT schema might add some formatting, just check it contains base
        assertTrue(result.contains(base))
    }

    @Test
    fun `includes JSON rules and formatting`() {
        val base = "Base prompt"
        val schema = JsonResponseSchema.create("""{"name": "string", "age": "number"}""", "Person schema")
        val result = PromptFormatter.formatPrompt(base, schema)

        assertTrue(result.contains("Ответ должен быть валидным JSON"))
        assertTrue(result.contains("Используй двойные кавычки для строк"))
        assertTrue(result.contains("Не добавляй комментарии в JSON"))
        assertTrue(result.contains("```json"))
    }

    @Test
    fun `includes XML rules and formatting`() {
        val base = "Base prompt"
        val schema = XmlResponseSchema.create("<person><name>string</name></person>", "Person XML")
        val result = PromptFormatter.formatPrompt(base, schema)

        assertTrue(result.contains("Ответ должен быть валидным XML"))
        assertTrue(result.contains("Используй корректные открывающие и закрывающие теги"))
        assertTrue(result.contains("```xml"))
    }

    @Test
    fun `handles schema without description`() {
        val base = "Base prompt"
        val jsonSchema = JsonResponseSchema.create("""{"data": "string"}""", "")
        val xmlSchema = XmlResponseSchema.create("<data>string</data>", "")

        val jsonResult = PromptFormatter.formatPrompt(base, jsonSchema)
        val xmlResult = PromptFormatter.formatPrompt(base, xmlSchema)

        assertTrue(jsonResult.contains("JSON"))
        assertTrue(xmlResult.contains("XML"))
        assertFalse(jsonResult.contains("Описание:"))
        assertFalse(xmlResult.contains("Описание:"))
    }

    @Test
    fun `preserves base prompt structure`() {
        val base = """
            First line
            Second line
            Third line
        """.trimIndent()

        val schema = JsonResponseSchema.create("""{"test": "string"}""", "Test")
        val result = PromptFormatter.formatPrompt(base, schema)

        assertTrue(result.contains("First line"))
        assertTrue(result.contains("Second line"))
        assertTrue(result.contains("Third line"))
        assertTrue(result.contains("Test"))
    }
}