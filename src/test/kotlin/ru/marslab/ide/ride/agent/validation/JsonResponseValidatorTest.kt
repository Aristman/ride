package ru.marslab.ide.ride.agent.validation

import kotlinx.serialization.json.Json
import ru.marslab.ide.ride.model.schema.JsonResponseSchema
import ru.marslab.ide.ride.model.schema.ParsedResponse
import ru.marslab.ide.ride.model.schema.XmlResponseSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JsonResponseValidatorTest {

    private val validator = JsonResponseValidator()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `validates ok when matches example schema`() {
        val schema = JsonResponseSchema.create(
            """
            {
              "answer": "string",
              "confidence": 0.0
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"answer":"ok","confidence":0.95}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `validates ok with extra fields in response`() {
        val schema = JsonResponseSchema.create("""{"answer": "string", "confidence": 0.0}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"answer":"ok","confidence":0.95,"extra":"field"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `fails when field missing`() {
        val schema = JsonResponseSchema.create("""{"answer": "string", "confidence": 0.0}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"answer":"ok"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Отсутствует поле '$.confidence'", err)
    }

    @Test
    fun `fails when type mismatch from string to number`() {
        val schema = JsonResponseSchema.create("""{"confidence": 0.0}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"confidence":"high"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Несовпадение типа в '$.confidence': ожидается number, получено string", err)
    }

    @Test
    fun `fails when type mismatch from number to string`() {
        val schema = JsonResponseSchema.create("""{"answer": "string"}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"answer": 123}""")
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Несовпадение типа в '$.answer': ожидается string, получено number", err)
    }

    @Test
    fun `fails when type mismatch from boolean to string`() {
        val schema = JsonResponseSchema.create("""{"valid": true}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"valid": "yes"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Несовпадение типа в '$.valid': ожидается boolean, получено string", err)
    }

    @Test
    fun `validates nested objects correctly`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "user": {
                    "name": "string",
                    "age": 0
                }
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"user":{"name":"John","age":25}}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `fails when nested field missing`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "user": {
                    "name": "string",
                    "age": 0
                }
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"user":{"name":"John"}}""")
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Отсутствует поле '$.user.age'", err)
    }

    @Test
    fun `fails when nested type mismatch`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "user": {
                    "name": "string",
                    "age": 0
                }
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"user":{"name":"John","age":"25"}}""")
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Несовпадение типа в '$.user.age': ожидается number, получено string", err)
    }

    @Test
    fun `validates deeply nested objects`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "level1": {
                    "level2": {
                        "level3": {
                            "value": "string",
                            "count": 0
                        }
                    }
                }
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"level1":{"level2":{"level3":{"value":"test","count":42}}}}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `fails for deeply nested missing field`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "level1": {
                    "level2": {
                        "level3": {
                            "value": "string",
                            "count": 0
                        }
                    }
                }
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"level1":{"level2":{"level3":{"value":"test"}}}}""")
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Отсутствует поле '$.level1.level2.level3.count'", err)
    }

    @Test
    fun `returns null for non-JSON format`() {
        val schema = XmlResponseSchema.create("<root></root>")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"test":"data"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `returns error for non-JsonResponse parsed response`() {
        val schema = JsonResponseSchema.create("""{"test": "string"}""")
        val parsed = ParsedResponse.TextResponse(
            rawContent = "plain text"
        )
        val err = validator.validate(parsed, schema)
        assertEquals("Неверный тип распарсенного ответа: ожидается JSON", err)
    }

    @Test
    fun `returns null for blank schema definition`() {
        val schema = JsonResponseSchema.create("")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"any":"structure"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `returns null for invalid schema JSON`() {
        val schema = JsonResponseSchema.create("""{invalid json}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"valid":"json"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `validates boolean types correctly`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "isActive": true,
                "isValid": false
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"isActive": true, "isValid": false}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `validates different number types`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "integer": 0,
                "float": 0.0,
                "negative": -1
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"integer": 42, "float": 3.14, "negative": -5}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `validates unicode strings correctly`() {
        val schema = JsonResponseSchema.create("""{"message": "string"}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"message":"Привет мир! 🚀"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `validates empty strings correctly`() {
        val schema = JsonResponseSchema.create("""{"empty": "string", "whitespace": "string"}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"empty":"","whitespace":"   "}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `validates zero and negative numbers`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "zero": 0,
                "negative": -1,
                "float": 0.0
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"zero": 0, "negative": -100, "float": 0.0}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `skips validation for arrays in schema`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "items": ["string"],
                "value": "string"
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"items": [1, 2, 3], "value": "test"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err) // Arrays are simplified - skipped in strict validation
    }

    @Test
    fun `handles mixed types in validation`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "stringField": "string",
                "numberField": 0,
                "booleanField": true,
                "objectField": {"nested": "string"}
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement(
                """
                {
                    "stringField": "test",
                    "numberField": 42,
                    "booleanField": false,
                    "objectField": {"nested": "value"}
                }
            """
            )
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `provides detailed error path for complex nesting`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "a": {
                    "b": {
                        "c": {
                            "d": "string"
                        }
                    }
                }
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"a":{"b":{"c":{"d":123}}}}""")
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Несовпадение типа в '$.a.b.c.d': ожидается string, получено number", err)
    }

    @Test
    fun `validates when actual response has additional nested levels`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "user": {
                    "name": "string"
                }
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"user":{"name":"John","profile":{"age":25}}}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `handles null values in actual response`() {
        val schema = JsonResponseSchema.create("""{"value": "string"}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"value": null}""")
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err) // null is treated as different from string
        assertEquals("Несовпадение типа в '$.value': ожидается string, получено unknown", err)
    }

    @Test
    fun `validates empty objects correctly`() {
        val schema = JsonResponseSchema.create("""{}""")
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"any": "structure", "with": "fields"}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `validates schema with only nested structure`() {
        val schema = JsonResponseSchema.create(
            """
            {
                "nested": {
                    "deep": {
                        "value": "string"
                    }
                }
            }
        """.trimIndent()
        )
        val parsed = ParsedResponse.JsonResponse(
            rawContent = "",
            jsonElement = json.parseToJsonElement("""{"nested":{"deep":{"value":"correct","extra":"ignored"}}}""")
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }
}
