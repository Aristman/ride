package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.AgentResponse
import ru.marslab.ide.ride.model.ChatContext
import ru.marslab.ide.ride.model.LLMParameters

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
    private val llmProvider: LLMProvider,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) : Agent {
    
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
            // Формируем промпт с учетом контекста
            val fullPrompt = buildPrompt(request, context)
            
            logger.debug("Built prompt, length: ${fullPrompt.length}")
            
            // Делегируем запрос в LLM провайдер
            val llmResponse = llmProvider.sendRequest(
                prompt = fullPrompt,
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
            
            // Возвращаем успешный ответ
            AgentResponse.success(
                content = llmResponse.content,
                metadata = mapOf(
                    "tokensUsed" to llmResponse.tokensUsed,
                    "provider" to llmProvider.getProviderName()
                )
            )
            
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
    
    /**
     * Формирует полный промпт с учетом системного промпта и контекста
     */
    private fun buildPrompt(request: String, context: ChatContext): String {
        val promptBuilder = StringBuilder()
        
        // Системный промпт
        promptBuilder.append(systemPrompt).append("\n\n")
        
        // История сообщений (последние N сообщений)
        val recentHistory = context.getRecentHistory(HISTORY_LIMIT)
        if (recentHistory.isNotEmpty()) {
            promptBuilder.append("История диалога:\n")
            recentHistory.forEach { message ->
                val roleText = when {
                    message.isFromUser() -> "Пользователь"
                    message.isFromAssistant() -> "Ассистент"
                    else -> "Система"
                }
                promptBuilder.append("$roleText: ${message.content}\n")
            }
            promptBuilder.append("\n")
        }
        
        // Контекст текущего файла (опционально)
        if (context.hasCurrentFile()) {
            context.currentFile?.let { file ->
                promptBuilder.append("Текущий файл: ${file.name}\n")
                promptBuilder.append("Путь: ${file.path}\n\n")
            }
        }
        
        // Выделенный текст (опционально)
        if (context.hasSelectedText()) {
            context.selectedText?.let { text ->
                promptBuilder.append("Выделенный код:\n")
                promptBuilder.append("```\n")
                promptBuilder.append(text)
                promptBuilder.append("\n```\n\n")
            }
        }
        
        // Запрос пользователя
        promptBuilder.append("Пользователь: $request")
        
        return promptBuilder.toString()
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
