package ru.marslab.ide.ride.agent.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import ru.marslab.ide.ride.model.schema.JsonResponseSchema
import ru.marslab.ide.ride.model.schema.ParsedResponse
import ru.marslab.ide.ride.model.schema.ResponseFormat
import ru.marslab.ide.ride.model.schema.TextResponseSchema
import ru.marslab.ide.ride.model.schema.XmlResponseSchema

class XmlResponseValidatorTest {

    private val validator = XmlResponseValidator()

    @Test
    fun `valid when root tag matches`() {
        val schema = XmlResponseSchema.create("""
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

    @Test
    fun `valid when schema is blank`() {
        val schema = XmlResponseSchema.create("")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<any><structure>content</structure></any>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when root tag matches with attributes`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """<response status="success" code="200"><content>test</content></response>"""
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when root tag matches with namespace`() {
        val schema = XmlResponseSchema.create("<root/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """<root xmlns:ns="http://example.com"><ns:item>test</ns:item></root>"""
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `fails when root tag mismatched`() {
        val schema = XmlResponseSchema.create("<response>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<error><message>Something went wrong</message></error>"
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Несовпадение корневого тега: ожидается <response>, получено <error>", err)
    }

    @Test
    fun `fails when root tag mismatched with complex schema`() {
        val schema = XmlResponseSchema.create("""
            <interview>
                <candidate>
                    <name>string</name>
                </candidate>
            </interview>
        """.trimIndent())
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<analysis><result>data</result></analysis>"
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertEquals("Несовпадение корневого тега: ожидается <interview>, получено <analysis>", err)
    }

    @Test
    fun `valid when root tag matches with self-closing schema`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<response><content>test</content></response>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when root tag matches with nested schema`() {
        val schema = XmlResponseSchema.create("""
            <root>
                <level1>
                    <level2>
                        <data>string</data>
                    </level2>
                </level1>
            </root>
        """.trimIndent())
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<root><level1><level2><data>actual content</data></level2></level1></root>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `fails when XML is malformed - missing closing tag`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<response><message>test</message>" // missing closing response tag
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertTrue(err.contains("Невалидный XML"))
    }

    @Test
    fun `fails when XML is malformed - unclosed quote`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """<response message="unclosed quote></response>"""
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertTrue(err.contains("Невалидный XML"))
    }

    @Test
    fun `fails when XML is completely malformed`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "This is not XML at all!"
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertTrue(err.contains("Невалидный XML"))
    }

    @Test
    fun `valid when XML has special characters and unicode`() {
        val schema = XmlResponseSchema.create("<message/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """<message>Привет мир! 🚀 Special: &lt;&gt;&amp;"'</message>"""
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has CDATA sections`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<response><![CDATA[Some <raw> content & symbols]]></response>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has comments`() {
        val schema = XmlResponseSchema.create("<root/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """
                <root>
                    <!-- This is a comment -->
                    <data>content</data>
                    <!-- Another comment -->
                </root>
            """.trimIndent()
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has processing instructions`() {
        val schema = XmlResponseSchema.create("<?xml version=\"1.0\"?><root/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """<?xml version="1.0" encoding="UTF-8"?><root><data>content</data></root>"""
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has DOCTYPE declaration`() {
        val schema = XmlResponseSchema.create("<root/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """
                <!DOCTYPE root [
                    <!ELEMENT root (data)>
                    <!ELEMENT data (#PCDATA)>
                ]>
                <root><data>content</data></root>
            """.trimIndent()
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has mixed content`() {
        val schema = XmlResponseSchema.create("<description/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<description>Text before <b>bold</b> and after</description>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `returns null for non-XML format`() {
        val schema = JsonResponseSchema.create("{}")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<response><data>test</data></response>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `returns error for non-XmlResponse parsed response`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.TextResponse(
            rawContent = "plain text"
        )
        val err = validator.validate(parsed, schema)
        assertEquals("Неверный тип распарсенного ответа: ожидается XML", err)
    }

    @Test
    fun `valid when schema has attributes in root tag`() {
        val schema = XmlResponseSchema.create("""<response xmlns="http://example.com" version="1.0"/>""")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<response><data>content</data></response>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has nested structures`() {
        val schema = XmlResponseSchema.create("""
            <interview>
                <candidate>
                    <name>string</name>
                    <skills>
                        <skill>string</skill>
                    </skills>
                </candidate>
            </interview>
        """.trimIndent())
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """
                <interview>
                    <candidate>
                        <name>John Doe</name>
                        <skills>
                            <skill>Kotlin</skill>
                            <skill>Java</skill>
                        </skills>
                    </candidate>
                </interview>
            """.trimIndent()
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has empty elements`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<response><empty></empty><selfclosing/></response>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has whitespace and newlines`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """

                <response>

                    <message>
                        Hello with spaces
                    </message>

                </response>

            """.trimIndent()
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `extracts root tag correctly from schema with complex structure`() {
        val schema = XmlResponseSchema.create("""
            <!-- Comment before root -->
            <?xml version="1.0" encoding="UTF-8"?>
            <complexResponse xmlns:ns="http://example.com" version="2.0">
                <header>
                    <status>string</status>
                </header>
                <body>
                    <content>string</content>
                </body>
            </complexResponse>
        """.trimIndent())
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<complexResponse><data>test</data></complexResponse>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has numeric and boolean content`() {
        val schema = XmlResponseSchema.create("<data/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """
                <data>
                    <number>42</number>
                    <decimal>3.14</decimal>
                    <boolean>true</boolean>
                    <zero>0</zero>
                </data>
            """.trimIndent()
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when root tag extraction handles different cases`() {
        val schema = XmlResponseSchema.create("<  Response  >")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<Response><data>test</data></Response>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `valid when XML has nested attributes`() {
        val schema = XmlResponseSchema.create("<root/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = """
                <root>
                    <item id="1" type="test" status="active">
                        <subitem name="test" value="123">content</subitem>
                    </item>
                </root>
            """.trimIndent()
        )
        val err = validator.validate(parsed, schema)
        assertNull(err)
    }

    @Test
    fun `fails when XML has multiple root elements`() {
        val schema = XmlResponseSchema.create("<response/>")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<response>test</response><extra>another root</extra>"
        )
        val err = validator.validate(parsed, schema)
        assertNotNull(err)
        assertTrue(err.contains("Невалидный XML"))
    }

    @Test
    fun `valid when schema root tag extraction fails - fallback to no validation`() {
        val schema = XmlResponseSchema.create("Invalid XML without proper tags")
        val parsed = ParsedResponse.XmlResponse(
            rawContent = "",
            xmlContent = "<anyRoot><data>content</data></anyRoot>"
        )
        val err = validator.validate(parsed, schema)
        assertNull(err) // No root tag extracted from schema, so no root validation
    }

    private fun assertTrue(condition: Boolean, message: String? = null) {
        if (!condition) {
            throw AssertionError(message ?: "Expected true but was false")
        }
    }
}
