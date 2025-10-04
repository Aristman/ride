package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.agent.formatter.PromptFormatter
import ru.marslab.ide.ride.agent.parser.ResponseParserFactory
import ru.marslab.ide.ride.agent.validation.ResponseValidatorFactory
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.AgentResponse
import ru.marslab.ide.ride.model.ChatContext
import ru.marslab.ide.ride.model.ConversationMessage
import ru.marslab.ide.ride.model.ConversationRole
import ru.marslab.ide.ride.model.LLMParameters
import ru.marslab.ide.ride.model.Message
import ru.marslab.ide.ride.model.MessageRole
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema
import ru.marslab.ide.ride.model.UncertaintyResponseSchema

/**
 * Универсальная реализация агента для общения с пользователем
 * 
 * Агент НЕ привязан к конкретному LLM провайдеру.
 * Провайдер передается через конструктор (Dependency Injection).
 * 
 * @property llmProvider Провайдер для взаимодействия с LLM
 * @property systemPrompt Системный промпт для агента
 */
class ChatAgent(
    private var llmProvider: LLMProvider,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) : Agent {

    private var responseFormat: ResponseFormat? = ResponseFormat.XML
    private var responseSchema: ResponseSchema? = UncertaintyResponseSchema.createXmlSchema()

    private val logger = Logger.getInstance(ChatAgent::class.java)
    
    override suspend fun processRequest(request: String, context: ChatContext): AgentResponse {

        // Проверяем доступность провайдера
        if (!llmProvider.isAvailable()) {
            logger.warn("LLM provider is not available")
            return AgentResponse.error(
                error = "LLM провайдер недоступен. Проверьте настройки.",
                content = "Пожалуйста, настройте API ключ в Settings → Tools → Ride"
            )
        }
        
        return try {
            // Системный промпт (опционально расширяем инструкциями формата)
            val systemPromptForRequest = buildSystemPrompt()

            // Полная история диалога для контекста
            val conversationHistory = buildConversationHistory(context)

            // Делегируем запрос в LLM провайдер
            val llmResponse = llmProvider.sendRequest(
                systemPrompt = systemPromptForRequest,
                userMessage = request,
                conversationHistory = conversationHistory,
                parameters = LLMParameters.DEFAULT
            )
            
            // Проверяем успешность ответа
            if (!llmResponse.success) {
                return AgentResponse.error(
                    error = llmResponse.error ?: "Неизвестная ошибка",
                    content = "Извините, произошла ошибка при обработке запроса."
                )
            }

            // Парсим ответ если задан формат
            val parsedResponse = if (responseSchema != null) {
                val parser = ResponseParserFactory.getParser(responseSchema!!)
                val parsed = parser.parse(llmResponse.content, responseSchema)
                parsed
            } else {
                null
            }

            // Анализируем неопределенность ответа
            val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(llmResponse.content, context)
            val isFinal = UncertaintyAnalyzer.isFinalResponse(uncertainty)

            logger.info("Uncertainty analysis: uncertainty=$uncertainty, isFinal=$isFinal")

            // Собираем метаданные ответа
            val baseMetadata = mutableMapOf(
                "tokensUsed" to llmResponse.tokensUsed,
                "provider" to llmProvider.getProviderName()
            )

            if (responseFormat != null) {
                baseMetadata["format"] = responseFormat!!.name
            }

            // Добавляем информацию о неопределенности
            baseMetadata["uncertainty"] = uncertainty
            baseMetadata["hasClarifyingQuestions"] = UncertaintyAnalyzer.hasExplicitUncertainty(llmResponse.content)

            // Если парсинг задан и произошла ошибка — формируем ошибку
            if (responseSchema != null && parsedResponse is ru.marslab.ide.ride.model.ParsedResponse.ParseError) {
                val errorContent = buildString {
                    appendLine("⚠️ **Ошибка парсинга ответа:** ${parsedResponse.error}")
                    appendLine()
                    appendLine("**Сырой ответ от агента:**")
                    appendLine("```xml")
                    appendLine(llmResponse.content)
                    appendLine("```")
                }
                return AgentResponse.error(
                    error = "Ошибка парсинга ответа: ${parsedResponse.error}",
                    content = errorContent
                )
            }

            // Валидация распарсенного ответа по схеме
            if (responseSchema != null && parsedResponse != null) {
                val validator = ResponseValidatorFactory.getValidator(responseSchema!!.format)
                val validationError = validator.validate(parsedResponse, responseSchema!!)
                if (validationError != null) {

                    // Если XML невалидный, пробуем обработать его напрямую через ResponseFormatter
                    if (responseSchema!!.format == ResponseFormat.XML && validationError.contains("XML", ignoreCase = true)) {
                        try {
                            // Пробуем извлечь контент напрямую из невалидного XML
                            val extractedContent = ru.marslab.ide.ride.ui.ResponseFormatter.extractMainContent(
                                llmResponse.content,
                                ru.marslab.ide.ride.model.Message(
                                    content = llmResponse.content,
                                    role = ru.marslab.ide.ride.model.MessageRole.ASSISTANT
                                ),
                                com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
                                    ?: com.intellij.openapi.project.ProjectManager.getInstance().defaultProject,
                                ru.marslab.ide.ride.service.ChatService()
                            )

                            return AgentResponse.success(
                                content = extractedContent,
                                isFinal = isFinal,
                                uncertainty = uncertainty,
                                metadata = baseMetadata + mapOf(
                                    "xmlValidationFailed" to true,
                                    "extractedFromInvalidXml" to true
                                )
                            )
                        } catch (e: Exception) {
                            logger.warn("Failed to extract content from invalid XML: ${e.message}")
                        }
                    }

                    val errorContent = buildString {
                        appendLine("⚠️ **Ошибка валидации ответа:** $validationError")
                        appendLine()
                        appendLine("**Сырой ответ от агента:**")
                        appendLine("```xml")
                        appendLine(llmResponse.content)
                        appendLine("```")
                    }
                    return AgentResponse.error(
                        error = "Ответ не соответствует схеме: $validationError",
                        content = errorContent
                    )
                }
            }

            // Возвращаем успешный ответ с учетом неопределенности
            if (parsedResponse != null) {
                AgentResponse.success(
                    content = llmResponse.content,
                    parsedContent = parsedResponse,
                    isFinal = isFinal,
                    uncertainty = uncertainty,
                    metadata = baseMetadata
                )
            } else {
                AgentResponse.success(
                    content = llmResponse.content,
                    isFinal = isFinal,
                    uncertainty = uncertainty,
                    metadata = baseMetadata
                )
            }
            
        } catch (e: Exception) {
            logger.error("Error processing request", e)
            AgentResponse.error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Произошла непредвиденная ошибка при обработке запроса."
            )
        }
    }
    
    override fun getName(): String = "Chat Agent"
    
    override fun getDescription(): String = 
        "Универсальный агент для общения с пользователем через ${llmProvider.getProviderName()}"
    
    override fun setLLMProvider(provider: LLMProvider) {
        logger.info("Changing LLM provider from ${llmProvider.getProviderName()} to ${provider.getProviderName()}")
        llmProvider = provider
    }
    
    override fun getLLMProvider(): LLMProvider = llmProvider
    
    override fun setResponseFormat(format: ResponseFormat, schema: ResponseSchema?) {
        logger.info("Setting response format to $format")
        responseFormat = format
        responseSchema = schema
        
        // Валидация схемы если она задана
        if (schema != null && !schema.isValid()) {
            logger.warn("Invalid schema provided for format $format")
        }
    }
    
    override fun getResponseFormat(): ResponseFormat? = responseFormat
    
    override fun clearResponseFormat() {
        logger.info("Clearing response format")
        responseFormat = null
        responseSchema = null
    }
    
    /**
     * Формирует системный промпт. Если задана схема ответа,
     * добавляет инструкции по формату в системный промпт.
     */
    private fun buildSystemPrompt(): String {
        val base = systemPrompt
        return if (responseSchema != null) {
            PromptFormatter.formatPrompt(base, responseSchema)
        } else base
    }

    /**
     * Строит полную историю диалога для LLM провайдера
     */
    private fun buildConversationHistory(context: ChatContext): List<ConversationMessage> {
        val recentMessages = context.getRecentHistory(HISTORY_LIMIT)
        return recentMessages.map { message ->
            val role = when (message.role) {
                MessageRole.USER -> ConversationRole.USER
                MessageRole.ASSISTANT -> ConversationRole.ASSISTANT
                MessageRole.SYSTEM -> ConversationRole.SYSTEM
            }
            ConversationMessage(role, message.content)
        }
    }
    
    companion object {
        private const val HISTORY_LIMIT = 5
        
        private val DEFAULT_SYSTEM_PROMPT = """
Ты - AI-ассистент для разработчиков в IntelliJ IDEA.
Твоя задача - помогать программистам с их вопросами о коде, отладке и разработке.

ВАЖНО: ФОРМАТ ОТВЕТА - XML СТРОГАЯ СТРУКТУРА
Твой ответ должен быть валидным XML с ТОЛЬКО ОДНИМ КОРНЕВЫМ ЭЛЕМЕНТОМ.

Структура ответа:
```xml
<response>
  <isFinal>true/false</isFinal>
  <uncertainty>0.0-1.0</uncertainty>
  <message>Твоё сообщение здесь</message>
  <reasoning>Твоё пояснение (если нужно)</reasoning>
  <clarifyingQuestions>
    <question>Вопрос 1</question>
    <question>Вопрос 2</question>
  </clarifyingQuestions>
</response>
```

ПРАВИЛА XML:
1. Только один корневой элемент <response>
2. Все теги должны быть парными и правильно вложены
3. Специальные символы в message должны быть экранированы: &lt; &gt; &amp; &quot; &apos;
4. Если есть уточняющие вопросы - isFinal=false, нет - isFinal=true
5. НЕ добавляй текст вне XML структуры

ПРАВИЛО ОЦЕНКИ НЕОПРЕДЕЛЕННОСТИ:
Прежде чем дать окончательный ответ, оцени свою уверенность в том, что ты полностью понял вопрос и можешь дать исчерпывающий ответ.

- Если твоя неопределенность больше 0.1 (из 1.0) - ЗАДАВАЙ УТОЧНЯЮЩИЕ ВОПРОСЫ
- Если неопределенность 0.1 или меньше - давай окончательный ответ

Критерии неопределенности:
- Недостаточно контекста или информации о проблеме (0.2-0.4)
- Неясен конкретный сценарий использования кода (0.2-0.3)
- Отсутствуют детали об окружении или технологиях (0.1-0.3)
- Вопрос слишком общий или допускает множество интерпретаций (0.3-0.5)
- Неизвестен уровень знаний пользователя (0.1-0.2)

Правила ответов:
- Если неопределенность > 0.1: isFinal=false и заполни clarifyingQuestions
- Если неопределенность ≤ 0.1: isFinal=true и дай полный ответ в message
- В message можно использовать markdown, но спецсимволы HTML должны быть экранированы
- Будь дружелюбным и профессиональным

Пример ответа с вопросами:
```xml
<response>
  <isFinal>false</isFinal>
  <uncertainty>0.3</uncertainty>
  <message>Давайте уточню несколько деталей, чтобы дать точный ответ.</message>
  <reasoning>Нужно больше информации о технологии и версии</reasoning>
  <clarifyingQuestions>
    <question>Какую версию Kotlin вы используете?</question>
    <question>Это веб-приложение или мобильное?</question>
  </clarifyingQuestions>
</response>
```

Пример окончательного ответа:
```xml
<response>
  <isFinal>true</isFinal>
  <uncertainty>0.05</uncertainty>
  <message>Окончательный ответ: вот решение вашей проблемы с примером кода...</message>
  <reasoning>Достаточно информации для полного ответа</reasoning>
</response>
```

Отвечай на русском языке, если пользователь пишет на русском.
        """.trimIndent()
    }
}
