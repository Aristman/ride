package ru.marslab.ide.ride.ui

import com.intellij.openapi.project.Project
import ru.marslab.ide.ride.model.AgentResponse
import ru.marslab.ide.ride.model.Message
import ru.marslab.ide.ride.model.MessageRole
import ru.marslab.ide.ride.model.ParsedResponse
import ru.marslab.ide.ride.service.ChatService
import java.text.DecimalFormat

/**
 * Data class для извлеченных данных из XML ответа
 */
data class UncertaintyResponseData(
    val isFinal: Boolean,
    val uncertainty: Double,
    val message: String,
    val clarifyingQuestions: List<String> = emptyList(),
    val reasoning: String? = null
)

/**
 * Форматирует ответы от агента для отображения в UI с учетом схемы ответа
 */
object ResponseFormatter {

    private val percentageFormat = DecimalFormat("#%")

    /**
     * Извлекает и форматирует основное содержание из ответа для отображения
     */
    fun extractMainContent(
        content: String,
        message: Message,
        project: Project,
        chatService: ChatService
    ): String {
        println("=== DEBUG: ResponseFormatter.extractMainContent START ===")
        println("Input content length: ${content.length}")
        println("Input content preview: ${content.take(200)}...")

        val format = chatService.getResponseFormat()
        val schema = chatService.getResponseSchema()
        println("Response format: $format")
        println("Response schema: $schema")

        // Если нет схемы - возвращаем обычный текст
        if (schema == null || format == null) {
            println("No schema or format, returning raw content")
            return content
        }

        // Извлекаем и форматируем контент из XML/JSON
        val result = try {
            when (format) {
                ru.marslab.ide.ride.model.ResponseFormat.JSON -> {
                    println("Formatting as JSON")
                    formatJsonContent(content)
                }
                ru.marslab.ide.ride.model.ResponseFormat.XML -> {
                    println("Formatting as XML")
                    formatXmlContent(content)
                }
                else -> {
                    println("Unknown format, returning raw content")
                    content
                }
            }
        } catch (e: Exception) {
            println("=== ERROR: Failed to format content, trying fallback ===")
            println("Error: ${e.message}")

            // Запасной вариант - простое извлечение текста из XML
            if (format == ru.marslab.ide.ride.model.ResponseFormat.XML) {
                println("Using fallback XML extraction")
                extractFromMalformedXml(content)
            } else {
                println("Fallback failed, returning raw content")
                content
            }
        }

        println("Result content length: ${result.length}")
        println("Result content preview: ${result.take(200)}...")
        println("=== DEBUG: ResponseFormatter.extractMainContent END ===")
        return result
    }

    /**
     * Запасной вариант извлечения контента из невалидного XML
     */
    private fun extractFromMalformedXml(xmlString: String): String {
        println("DEBUG: Using fallback XML extraction for: ${xmlString.take(100)}...")

        // Ищем<message>теги и извлекаем их содержимое
        val messageMatch = Regex("""<message[^>]*>(.*?)</message>""", RegexOption.DOT_MATCHES_ALL).find(xmlString)
        if (messageMatch != null) {
            val messageContent = messageMatch.groupValues[1].trim()
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
            println("DEBUG: Extracted message via fallback: ${messageContent.take(100)}...")
            return messageContent
        }

        // Если нет<message>тегов, ищем другой контент
        val isFinalMatch = Regex("""<isFinal[^>]*>(true|false)</isFinal>""").find(xmlString)
        val isFinal = isFinalMatch?.groupValues?.get(1) == "true"

        if (isFinal) {
            // Для окончательных ответов пробуем извлечь полезный контент
            val lines = xmlString.split("\n")
            val contentLines = lines.filter { line ->
                line.trim().isNotEmpty() &&
                !line.trim().startsWith("<") &&
                !line.trim().endsWith(">") &&
                !line.contains("isFinal") &&
                !line.contains("uncertainty")
            }

            if (contentLines.isNotEmpty()) {
                val result = contentLines.joinToString("\n").trim()
                println("DEBUG: Extracted content lines via fallback: ${result.take(100)}...")
                return result
            }
        }

        println("DEBUG: Fallback extraction failed, returning processed XML")
        return xmlString
            .replace("<[^>]+>".toRegex(), "") // Убираем теги
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()
    }

    /**
     * Форматирует ответ агента для отображения в чате
     */
    fun formatResponse(
        agentResponse: AgentResponse,
        message: Message,
        project: Project,
        chatService: ChatService
    ): String {
        val format = chatService.getResponseFormat()
        val schema = chatService.getResponseSchema()

        // Если нет схемы - возвращаем обычный текст
        if (schema == null || format == null) {
            return agentResponse.content
        }

        // Если есть распарсенный контент - форматируем его
        return when (val parsed = agentResponse.parsedContent) {
            is ParsedResponse.JsonResponse -> formatJsonResponse(parsed.jsonElement.toString(), agentResponse)
            is ParsedResponse.XmlResponse -> formatXmlResponse(parsed.xmlContent, agentResponse)
            is ParsedResponse.ParseError -> {
                // В случае ошибки парсинга показываем оригинальный контент с предупреждением
                "⚠️ Ошибка парсинга ${format.name}: ${parsed.error}\n\n${agentResponse.content}"
            }
            else -> agentResponse.content
        }
    }

    /**
     * Форматирует JSON ответ от агента
     */
    private fun formatJsonResponse(jsonString: String, agentResponse: AgentResponse): String {
        return try {
            // Простое форматирование JSON для отображения
            val formatted = buildString {
                append("📋 **Структурированный ответ:**\n\n")

                // Добавляем индикаторы неопределенности
                val uncertainty = agentResponse.uncertainty ?: 0.0
                val uncertaintyPercent = percentageFormat.format(uncertainty)

                if (agentResponse.isFinal) {
                    append("✅ **Окончательный ответ** (уверенность: ${((1 - uncertainty) * 100).toInt()}%)\n\n")
                } else {
                    append("❓ **Требуются уточнения** (неопределенность: $uncertaintyPercent)\n\n")
                }

                // Добавляем контент
                append("```json\n")
                append(jsonString)
                append("\n```\n")
            }

            formatted
        } catch (e: Exception) {
            "⚠️ Ошибка форматирования JSON ответа:\n\n${agentResponse.content}"
        }
    }

    /**
     * Форматирует XML ответ от агента
     */
    private fun formatXmlResponse(xmlString: String, agentResponse: AgentResponse): String {
        return try {
            val formatted = buildString {
                append("📋 **Структурированный ответ:**\n\n")

                // Добавляем индикаторы неопределенности
                val uncertainty = agentResponse.uncertainty ?: 0.0
                val uncertaintyPercent = percentageFormat.format(uncertainty)

                if (agentResponse.isFinal) {
                    append("✅ **Окончательный ответ** (уверенность: ${((1 - uncertainty) * 100).toInt()}%)\n\n")
                } else {
                    append("❓ **Требуются уточнения** (неопределенность: $uncertaintyPercent)\n\n")
                }

                // Добавляем контент
                append("```xml\n")
                append(xmlString)
                append("\n```\n")
            }

            formatted
        } catch (e: Exception) {
            "⚠️ Ошибка форматирования XML ответа:\n\n${agentResponse.content}"
        }
    }

    /**
     * Извлекает полезную информацию из структурированного ответа для краткого отображения
     */
    fun extractMainContent(agentResponse: AgentResponse): String {
        return when (val parsed = agentResponse.parsedContent) {
            is ParsedResponse.JsonResponse -> formatJsonContent(parsed.jsonElement.toString())
            is ParsedResponse.XmlResponse -> formatXmlContent(parsed.xmlContent)
            is ParsedResponse.ParseError -> agentResponse.content
            else -> agentResponse.content
        }
    }

    /**
     * Форматирует JSON контент в человекочитаемый вид
     */
    private fun formatJsonContent(jsonString: String): String {
        return try {
            val data = parseUncertaintyResponse(jsonString)
            formatUncertaintyResponse(data)
        } catch (e: Exception) {
            jsonString
        }
    }

    /**
     * Форматирует XML контент в человекочитаемый вид
     */
    private fun formatXmlContent(xmlString: String): String {
        return try {
            val data = parseXmlResponse(xmlString)
            formatUncertaintyResponse(data)
        } catch (e: Exception) {
            xmlString
        }
    }

    /**
     * Парсит XML ответ в структурированные данные
     */
    private fun parseXmlResponse(xmlString: String): UncertaintyResponseData {
        println("DEBUG: Parsing XML response: $xmlString")

        // Сначала пробуем исправить XML если он невалидный
        val fixedXml = tryFixMalformedXml(xmlString)
        println("DEBUG: Fixed XML: $fixedXml")

        val isFinal = Regex("""<isFinal[^>]*>(true|false)</isFinal>""").find(fixedXml)?.groupValues?.get(1) == "true"
        val uncertainty = Regex("""<uncertainty[^>]*>([\d.]+)</uncertainty>""").find(fixedXml)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val message = Regex("""<message[^>]*>(.*?)</message>""", RegexOption.DOT_MATCHES_ALL).find(fixedXml)?.groupValues?.get(1)?.trim() ?: ""
        println("DEBUG: Extracted message: $message")
        val reasoning = Regex("""<reasoning[^>]*>([^<]+)</reasoning>""").find(fixedXml)?.groupValues?.get(1)?.trim()

        // Извлекаем уточняющие вопросы
        val clarifyingQuestions = mutableListOf<String>()
        val questionMatches = Regex("""<question[^>]*>([^<]+)</question>""").findAll(fixedXml)
        questionMatches.forEach { match ->
            clarifyingQuestions.add(match.groupValues[1].trim())
        }

        // Если есть уточняющие вопросы, устанавливаем высокую неопределенность
        val finalUncertainty = if (clarifyingQuestions.isNotEmpty()) {
            0.8 // Высокая неопределенность для уточняющих вопросов
        } else {
            uncertainty
        }

        return UncertaintyResponseData(
            isFinal = isFinal,
            uncertainty = finalUncertainty,
            message = message,
            clarifyingQuestions = clarifyingQuestions,
            reasoning = reasoning
        )
    }

    /**
     * Пытается исправить невалидный XML от LLM
     */
    private fun tryFixMalformedXml(xmlString: String): String {
        println("DEBUG: Attempting to fix malformed XML")

        var fixed = xmlString.trim()

        // 1. Ищем XML теги и оборачиваем в <response> если нужно
        if (!fixed.contains("<response>", ignoreCase = true)) {
            println("DEBUG: No <response> tag found, trying to wrap content")

            // Извлекаем все содержимое между тегами
            val tagPattern = Regex("""<([^/!][^>]*)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
            val matches = tagPattern.findAll(fixed).toList()

            if (matches.isNotEmpty()) {
                // Собираем все найденные теги
                val xmlContent = matches.joinToString("\n") { it.value }
                fixed = "<response>\n$xmlContent\n</response>"
                println("DEBUG: Wrapped content in <response> tags")
            } else {
                // Если тегов нет, создаем базовую структуру
                fixed = """<response>
  <isFinal>true</isFinal>
  <uncertainty>0.0</uncertainty>
  <message>${escapeXml(fixed)}</message>
</response>"""
                println("DEBUG: Created basic response structure")
            }
        }

        // 2. Убираем множественные корневые элементы
        val responseOpenCount = "<response>".toRegex().findAll(fixed).count()
        val responseCloseCount = "</response>".toRegex().findAll(fixed).count()

        if (responseOpenCount > 1) {
            // Оставляем только первую пару <response>...</response>
            val firstOpen = fixed.indexOf("<response>")
            val firstClose = fixed.indexOf("</response>", firstOpen)
            if (firstOpen != -1 && firstClose != -1) {
                fixed = fixed.substring(firstOpen, firstClose + "</response>".length)
                println("DEBUG: Removed multiple response elements")
            }
        }

        // 3. Исправляем незакрытые теги
        val allTags = Regex("""<(/?)([^>/]+)(/?)>""").findAll(fixed).toList()
        val tagStack = mutableListOf<String>()

        for (match in allTags) {
            val (fullMatch, slash, tagName, endSlash) = match.destructured
            when {
                slash.isNotEmpty() -> {
                    // Закрывающий тег
                    if (tagStack.isNotEmpty() && tagStack.last() == tagName) {
                        tagStack.removeAt(tagStack.size - 1)
                    }
                }
                endSlash.isNotEmpty() -> {
                    // Самозакрывающийся тег - ничего не делаем
                }
                else -> {
                    // Открывающий тег
                    if (!listOf("br", "hr", "img", "input", "meta", "link").contains(tagName.lowercase())) {
                        tagStack.add(tagName)
                    }
                }
            }
        }

        // Закрываем незакрытые теги в обратном порядке
        for (tagName in tagStack.reversed()) {
            fixed += "</$tagName>"
            println("DEBUG: Added missing closing tag: </$tagName>")
        }

        // 4. Удаляем текст вне корневого элемента
        val responseStart = fixed.indexOf("<response")
        val responseEnd = fixed.lastIndexOf("</response>")

        if (responseStart != -1 && responseEnd != -1) {
            val insideResponse = fixed.substring(responseStart, responseEnd + "</response>".length)
            // Проверяем, что есть текст вне <response>
            val before = fixed.substring(0, responseStart).trim()
            val after = fixed.substring(responseEnd + "</response>".length).trim()

            if (before.isNotEmpty() || after.isNotEmpty()) {
                println("DEBUG: Found text outside response element, removing it")
                fixed = insideResponse
            }
        }

        println("DEBUG: Fixed XML result: $fixed")
        return fixed
    }

    /**
     * Экранирует специальные символы XML
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Парсит JSON ответ в структурированные данные
     */
    private fun parseUncertaintyResponse(jsonString: String): UncertaintyResponseData {
        val isFinal = Regex(""""isFinal"\s*:\s*(true|false)""").find(jsonString)?.groupValues?.get(1) == "true"
        val uncertainty = Regex(""""uncertainty"\s*:\s*([\d.]+)""").find(jsonString)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val message = extractJsonMessage(jsonString)

        // Извлекаем уточняющие вопросы
        val clarifyingQuestions = mutableListOf<String>()
        val questionsArrayMatch = Regex(""""clarifyingQuestions"\s*:\s*\[([^\]]+)\]""").find(jsonString)
        questionsArrayMatch?.let {
            val questionsContent = it.groupValues[1]
            val questionMatches = Regex(""""([^"]+)"""").findAll(questionsContent)
            questionMatches.forEach { match ->
                clarifyingQuestions.add(match.groupValues[1])
            }
        }

        val reasoning = Regex(""""reasoning"\s*:\s*"([^"]+)""").find(jsonString)?.groupValues?.get(1)

        // Если есть уточняющие вопросы, устанавливаем высокую неопределенность
        val finalUncertainty = if (clarifyingQuestions.isNotEmpty()) {
            0.8 // Высокая неопределенность для уточняющих вопросов
        } else {
            uncertainty
        }

        return UncertaintyResponseData(
            isFinal = isFinal,
            uncertainty = finalUncertainty,
            message = message,
            clarifyingQuestions = clarifyingQuestions,
            reasoning = reasoning
        )
    }

    /**
     * Форматирует данные ответа в человекочитаемый вид с поддержкой markdown
     */
    private fun formatUncertaintyResponse(data: UncertaintyResponseData): String {
        return buildString {
            if (data.isFinal) {
                // Окончательный ответ - только сообщение
                append(data.message)
            } else {
                // Уточняющий ответ - сообщение + вопросы

                // Добавляем заголовок
                append("❓ **Требуются уточнения**\n\n")

                // Добавляем основное сообщение если есть
                if (data.message.isNotBlank()) {
                    append(data.message.trim())
                    append("\n\n")
                }

                // Добавляем пояснение если есть
                data.reasoning?.let { reasoning ->
                    if (reasoning.isNotBlank()) {
                        append("*$reasoning*\n\n")
                    }
                }

                // Добавляем вопросы если есть
                if (data.clarifyingQuestions.isNotEmpty()) {
                    append("**Уточняющие вопросы:**\n")
                    data.clarifyingQuestions.forEachIndexed { index, question ->
                        append("${index + 1}. $question\n")
                    }
                }

                // Убираем неопределенность из сообщения, она будет только в статусной строке
            }
        }.trim()
    }

    /**
     * Извлекает сообщение из JSON поля с учетом экранирования
     */
    private fun extractJsonMessage(jsonString: String): String {
        // Ищем "message": "..." с учетом экранирования
        val pattern = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val match = pattern.find(jsonString)
        return if (match != null) {
            // Восстанавливаем экранированные символы
            match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
        } else {
            ""
        }
    }
}