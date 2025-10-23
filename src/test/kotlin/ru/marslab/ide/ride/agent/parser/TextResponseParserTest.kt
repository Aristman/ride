package ru.marslab.ide.ride.agent.parser

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import ru.marslab.ide.ride.model.schema.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TextResponseParserTest {

    private val parser = TextResponseParser()

    @Test
    fun `parses plain text`() {
        val raw = "Just a plain text"
        val schema = TextResponseSchema.create("any text")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.TEXT, res.format)
    }

    @Test
    fun `parses empty text`() {
        val raw = ""
        val schema = TextResponseSchema.create()
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals("", res.rawContent)
        assertEquals(ResponseFormat.TEXT, res.format)
    }

    @Test
    fun `parses multiline text`() {
        val raw = """
            This is a multiline
            text response
            with multiple lines
        """.trimIndent()
        val schema = TextResponseSchema.create("multiline response")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.TEXT, res.format)
        assertTrue(res.rawContent.contains("multiline"))
        assertTrue(res.rawContent.contains("multiple lines"))
    }

    @Test
    fun `parses text with special characters`() {
        val raw = "Text with special chars: !@#$%^&*()_+-={}[]|\\:;\"'<>?,./"
        val schema = TextResponseSchema.create("text with special characters")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertTrue(res.rawContent.contains("!@#$%^&*()"))
    }

    @Test
    fun `parses text with unicode characters`() {
        val raw = "Текст на русском: Привет мир! Emoji: 🚀🌟💡"
        val schema = TextResponseSchema.create("unicode text")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertTrue(res.rawContent.contains("Привет мир"))
        assertTrue(res.rawContent.contains("🚀"))
    }

    @Test
    fun `parses text with whitespace`() {
        val raw = "   Text with leading and trailing spaces   \n   and newlines   "
        val schema = TextResponseSchema.create("text with whitespace")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.TEXT, res.format)
    }

    @Test
    fun `parses text with code blocks`() {
        val raw = """
            Here's some code:

            ```kotlin
            fun hello() {
                println("Hello, World!")
            }
            ```

            And more text after.
        """.trimIndent()
        val schema = TextResponseSchema.create("text with code blocks")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertTrue(res.rawContent.contains("```kotlin"))
        assertTrue(res.rawContent.contains("println"))
    }

    @Test
    fun `parses text with markdown formatting`() {
        val raw = """
            # Heading 1
            This is **bold** and *italic* text.
            - List item 1
            - List item 2
            - List item 3

            [Link](https://example.com)
        """.trimIndent()
        val schema = TextResponseSchema.create("markdown text")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertTrue(res.rawContent.contains("# Heading 1"))
        assertTrue(res.rawContent.contains("**bold**"))
        assertTrue(res.rawContent.contains("*italic*"))
    }

    @Test
    fun `parses text with JSON-like content`() {
        val raw = """
            The response looks like JSON but should be treated as text:

            {
                "answer": "This is actually plain text",
                "status": "not parsed as JSON"
            }

            End of text.
        """.trimIndent()
        val schema = TextResponseSchema.create("text containing JSON-like content")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.TEXT, res.format)
        assertTrue(res.rawContent.contains("This is actually plain text"))
    }

    @Test
    fun `parses text with XML-like content`() {
        val raw = """
            Here's some XML-like content that should remain as text:

            <response>
                <message>This is plain text, not XML</message>
                <status>unparsed</status>
            </response>

            More text here.
        """.trimIndent()
        val schema = TextResponseSchema.create("text containing XML-like content")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertTrue(res.rawContent.contains("<message>"))
        assertTrue(res.rawContent.contains("This is plain text, not XML"))
    }

    @Test
    fun `parses very long text`() {
        val raw = "A".repeat(10000) + " " + "B".repeat(5000)
        val schema = TextResponseSchema.create("very long text")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertEquals(15001, res.rawContent.length)
    }

    @Test
    fun `parses text with only whitespace`() {
        val raw = "   \n\t   \n   \t  \n  "
        val schema = TextResponseSchema.create("whitespace only text")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.TEXT, res.format)
    }

    @Test
    fun `supports TEXT format`() {
        val textSchema = TextResponseSchema.create("test text")
        val jsonSchema = JsonResponseSchema.create("{}")
        val xmlSchema = XmlResponseSchema.create("<root></root>")

        assertTrue(parser.supports(textSchema))
        assertFalse(parser.supports(jsonSchema))
        assertFalse(parser.supports(xmlSchema))
    }

    @Test
    fun `parses text with emoji and special Unicode`() {
        val raw =
            "Emoji test: 😀😃😄😁😆😅😂🤣☺️😊😇🙂🙃😉😌😍🥰😘😗😙😚😋😛😝😜🤪🤨🧐🤓😎🤩🥳😏😒😞😔😟😕🙁☹️😣😖😫😩🥺😢😭😤😠😡🤬🤯😳🥵🥶😱😨😰😥😓🤗🤔🤭🤫🤥😶😐😑😬🙄😯😦😧😮😲🥱😴🤤😪😵🤐🥴🤢🤮🤧😷🤒🤕🤑🤠😈👿👹👺🤡💩👻💀☠️👽👾🤖🎃😺😸😹😻😼😽🙀😿😾"
        val schema = TextResponseSchema.create("emoji text")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertTrue(res.rawContent.contains("😀"))
        assertTrue(res.rawContent.contains("🤖"))
    }

    @Test
    fun `parses text with numbers and special formatting`() {
        val raw = """
            Price: $99.99
            Discount: 25%
            Date: 2025-10-20
            Time: 14:30:00
            Phone: +7 (999) 123-45-67
            Email: test@example.com
            URL: https://example.com/path?param=value&other=test
        """.trimIndent()
        val schema = TextResponseSchema.create("text with formatted data")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertTrue(res.rawContent.contains("$99.99"))
        assertTrue(res.rawContent.contains("25%"))
        assertTrue(res.rawContent.contains("2025-10-20"))
        assertTrue(res.rawContent.contains("https://example.com"))
    }

    @Test
    fun `parses text with mixed languages`() {
        val raw = """
            English: Hello, World!
            Русский: Привет, мир!
            Español: ¡Hola, mundo!
            Français: Bonjour, le monde!
            Deutsch: Hallo, Welt!
            中文: 你好，世界！
            日本語: こんにちは、世界！
            한국어: 안녕하세요, 세계!
            العربية: مرحبا بالعالم!
            עברית: שלום עולם!
        """.trimIndent()
        val schema = TextResponseSchema.create("multilingual text")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertTrue(res.rawContent.contains("Hello, World!"))
        assertTrue(res.rawContent.contains("Привет, мир!"))
        assertTrue(res.rawContent.contains("こんにちは、世界！"))
    }

    @Test
    fun `handles null schema gracefully`() {
        val raw = "Test text with null schema"
        val res = parser.parse(raw, null)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        assertEquals(ResponseFormat.TEXT, res.format)
    }

    @Test
    fun `preserves original content exactly`() {
        val raw = "Original text with exact content\nincluding newlines and  spaces   "
        val schema = TextResponseSchema.create("test")
        val res = parser.parse(raw, schema)

        assertIs<ParsedResponse.TextResponse>(res)
        assertEquals(raw, res.rawContent)
        // Ensure no transformations are applied
        assertEquals(raw.length, res.rawContent.length)
    }
}
