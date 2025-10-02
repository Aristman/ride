package ru.marslab.ide.ride.agent.validation

import org.w3c.dom.Document
import ru.marslab.ide.ride.model.ParsedResponse
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Простейшая валидация XML: проверяем, что XML валиден синтаксически и
 * (опционально) совпадает корневой тег с тегом из схемы-примера.
 */
class XmlResponseValidator : ResponseValidator {

    override fun validate(parsed: ParsedResponse, schema: ResponseSchema): String? {
        if (schema.format != ResponseFormat.XML) return null
        if (parsed !is ParsedResponse.XmlResponse) return "Неверный тип распарсенного ответа: ожидается XML"

        val xml = parsed.xmlContent.trim()
        val doc = try {
            parseXml(xml)
        } catch (e: Exception) {
            return "Невалидный XML: ${e.message}"
        }

        // Если схема задана как пример, попробуем вытащить ожидаемый корневой тег
        if (schema.schemaDefinition.isNotBlank()) {
            val expectedRoot = extractRootTag(schema.schemaDefinition)
            if (expectedRoot != null) {
                val actualRoot = doc.documentElement?.nodeName
                if (actualRoot != expectedRoot) {
                    return "Несовпадение корневого тега: ожидается <$expectedRoot>, получено <$actualRoot>"
                }
            }
        }

        return null
    }

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(xml.toByteArray()))
    }

    private fun extractRootTag(exampleXml: String): String? {
        val regex = Regex("<\\s*([a-zA-Z_][\\w.-]*)[\\s>]")
        return regex.find(exampleXml)?.groupValues?.getOrNull(1)
    }
}
