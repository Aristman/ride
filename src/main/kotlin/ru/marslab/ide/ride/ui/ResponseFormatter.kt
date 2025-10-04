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
        val format = chatService.getResponseFormat()
        val schema = chatService.getResponseSchema()

        // Если нет схемы - возвращаем обычный текст
        if (schema == null || format == null) {
            return content
        }

        // Извлекаем и форматируем контент из XML/JSON
        return when (format) {
            ru.marslab.ide.ride.model.ResponseFormat.JSON -> formatJsonContent(content)
            ru.marslab.ide.ride.model.ResponseFormat.XML -> formatXmlContent(content)
            else -> content
        }
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
        val isFinal = Regex("""<isFinal[^>]*>(true|false)</isFinal>""").find(xmlString)?.groupValues?.get(1) == "true"
        val uncertainty = Regex("""<uncertainty[^>]*>([\d.]+)</uncertainty>""").find(xmlString)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val message = Regex("""<message[^>]*>([^<]+)</message>""").find(xmlString)?.groupValues?.get(1)?.trim() ?: ""
        val reasoning = Regex("""<reasoning[^>]*>([^<]+)</reasoning>""").find(xmlString)?.groupValues?.get(1)?.trim()

        // Извлекаем уточняющие вопросы
        val clarifyingQuestions = mutableListOf<String>()
        val questionMatches = Regex("""<question[^>]*>([^<]+)</question>""").findAll(xmlString)
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
     * Парсит JSON ответ в структурированные данные
     */
    private fun parseUncertaintyResponse(jsonString: String): UncertaintyResponseData {
        val isFinal = Regex(""""isFinal"\s*:\s*(true|false)""").find(jsonString)?.groupValues?.get(1) == "true"
        val uncertainty = Regex(""""uncertainty"\s*:\s*([\d.]+)""").find(jsonString)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val message = Regex(""""message"\s*:\s*"([^"]+)""").find(jsonString)?.groupValues?.get(1) ?: ""

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
}