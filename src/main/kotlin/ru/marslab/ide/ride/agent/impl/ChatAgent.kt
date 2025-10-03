package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.formatter.PromptFormatter
import ru.marslab.ide.ride.agent.parser.ResponseParserFactory
import ru.marslab.ide.ride.agent.validation.ResponseValidatorFactory
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.AgentResponse
import ru.marslab.ide.ride.model.ChatContext
import ru.marslab.ide.ride.model.LLMParameters
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema
import ru.marslab.ide.ride.model.Message

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
    
    private var responseFormat: ResponseFormat? = null
    private var responseSchema: ResponseSchema? = null
    
    private val logger = Logger.getInstance(ChatAgent::class.java)
    
    override suspend fun processRequest(request: String, context: ChatContext): AgentResponse {
        logger.info("Processing request, length: ${request.length}")
        
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
            
            // История ответов ассистента для контекста
            val assistantHistory = getAssistantHistory(context)
            
            // Делегируем запрос в LLM провайдер
            val llmResponse = llmProvider.sendRequest(
                systemPrompt = systemPromptForRequest,
                userMessage = request,
                assistantHistory = assistantHistory,
                parameters = LLMParameters.DEFAULT
            )
            
            // Проверяем успешность ответа
            if (!llmResponse.success) {
                logger.warn("LLM provider returned error: ${llmResponse.error}")
                return AgentResponse.error(
                    error = llmResponse.error ?: "Неизвестная ошибка",
                    content = "Извините, произошла ошибка при обработке запроса."
                )
            }
            
            logger.info("Request processed successfully, tokens used: ${llmResponse.tokensUsed}")
            
            // Парсим ответ если задан формат
            val parsedResponse = if (responseSchema != null) {
                val parser = ResponseParserFactory.getParser(responseSchema!!)
                parser.parse(llmResponse.content, responseSchema)
            } else {
                null
            }

            // Если парсинг задан и произошла ошибка — формируем ошибку
            if (responseSchema != null && parsedResponse is ru.marslab.ide.ride.model.ParsedResponse.ParseError) {
                logger.warn("Parse error for format ${responseSchema?.format}: ${parsedResponse.error}")
                return AgentResponse.error(
                    error = "Ошибка парсинга ответа: ${parsedResponse.error}",
                    content = llmResponse.content
                )
            }

            // Валидация распарсенного ответа по схеме
            if (responseSchema != null && parsedResponse != null) {
                val validator = ResponseValidatorFactory.getValidator(responseSchema!!.format)
                val validationError = validator.validate(parsedResponse, responseSchema!!)
                if (validationError != null) {
                    logger.warn("Validation failed: $validationError")
                    return AgentResponse.error(
                        error = "Ответ не соответствует схеме: $validationError",
                        content = llmResponse.content
                    )
                }
            }
            
            // Возвращаем успешный ответ
            if (parsedResponse != null) {
                AgentResponse.success(
                    content = llmResponse.content,
                    parsedContent = parsedResponse,
                    metadata = mapOf(
                        "tokensUsed" to llmResponse.tokensUsed,
                        "provider" to llmProvider.getProviderName(),
                        "format" to (responseFormat?.name ?: "TEXT")
                    )
                )
            } else {
                AgentResponse.success(
                    content = llmResponse.content,
                    metadata = mapOf(
                        "tokensUsed" to llmResponse.tokensUsed,
                        "provider" to llmProvider.getProviderName()
                    )
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
     * Возвращает последние ответы ассистента для контекста (до HISTORY_LIMIT)
     */
    private fun getAssistantHistory(context: ChatContext): List<String> {
        val recent: List<Message> = context.getRecentHistory(HISTORY_LIMIT)
        return recent.filter { it.isFromAssistant() }.map { it.content }
    }
    
    companion object {
        private const val HISTORY_LIMIT = 5
        
        private val DEFAULT_SYSTEM_PROMPT = """
Ты - AI-ассистент для разработчиков в IntelliJ IDEA.
Твоя задача - помогать программистам с их вопросами о коде, отладке и разработке.

Правила:
- Отвечай четко, по существу и профессионально
- Если нужно показать код, используй markdown форматирование с указанием языка
- Если не уверен в ответе, честно скажи об этом
- Предлагай лучшие практики и современные подходы
- Будь дружелюбным и помогающим

Отвечай на русском языке, если пользователь пишет на русском.
        """.trimIndent()
    }
}
