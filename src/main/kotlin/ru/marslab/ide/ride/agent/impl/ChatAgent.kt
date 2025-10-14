package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.agent.formatter.PromptFormatter
import ru.marslab.ide.ride.agent.parser.ResponseParserFactory
import ru.marslab.ide.ride.agent.validation.ResponseValidatorFactory
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.integration.llm.TokenCounter
import ru.marslab.ide.ride.integration.llm.impl.TiktokenCounter
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTProvider
import ru.marslab.ide.ride.model.agent.AgentCapabilities
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.agent.AgentSettings
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.chat.ConversationMessage
import ru.marslab.ide.ride.model.chat.ConversationRole
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.model.chat.MessageRole
import ru.marslab.ide.ride.model.schema.ResponseFormat
import ru.marslab.ide.ride.model.schema.ResponseSchema
import ru.marslab.ide.ride.model.schema.UncertaintyResponseSchema
import ru.marslab.ide.ride.model.schema.XmlResponseData
import ru.marslab.ide.ride.model.schema.JsonResponseData
import ru.marslab.ide.ride.model.TextResponseData
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.ui.ResponseFormatter.formatJsonResponseData
import ru.marslab.ide.ride.ui.ResponseFormatter.formatXmlResponseData

/**
 * Универсальная реализация агента для общения с пользователем
 *
 * Агент НЕ привязан к конкретному LLM провайдеру.
 * Провайдер передается через конструктор (Dependency Injection).
 *
 * @property initialProvider Начальный провайдер для взаимодействия с LLM
 * @property systemPrompt Системный промпт для агента
 */
class ChatAgent(
    initialProvider: LLMProvider,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) : Agent {

    private var llmProvider: LLMProvider = initialProvider
    private var settings: AgentSettings = AgentSettings(
        llmProvider = initialProvider.getProviderName(),
        defaultResponseFormat = ResponseFormat.XML
    )
    private var responseFormat: ResponseFormat? = ResponseFormat.XML
    private var responseSchema: ResponseSchema? = UncertaintyResponseSchema.createXmlSchema()
    
    private val tokenCounter: TokenCounter by lazy {
        if (llmProvider is YandexGPTProvider) {
            (llmProvider as YandexGPTProvider).getTokenCounter()
        } else {
            TiktokenCounter.forGPT()
        }
    }
    
    private val summarizerAgent: SummarizerAgent by lazy {
        SummarizerAgent(llmProvider)
    }

    private val logger = Logger.getInstance(ChatAgent::class.java)

    override val capabilities: AgentCapabilities = AgentCapabilities(
        stateful = true,
        streaming = false,
        reasoning = true,
        tools = emptySet(),
        systemPrompt = systemPrompt,
        responseRules = listOf(
            "Анализировать неопределенность перед ответом",
            "Задавать уточняющие вопросы при неопределенности > 0.1",
            "Использовать markdown для форматирования"
        )
    )

    override suspend fun ask(req: AgentRequest): AgentResponse {
        val request = req.request
        val context = req.context
        val parameters = req.parameters

        // Проверяем доступность провайдера
        if (!llmProvider.isAvailable()) {
            logger.warn("LLM provider is not available")
            return AgentResponse.error(
                error = "LLM провайдер недоступен. Проверьте настройки.",
                content = "Пожалуйста, настройте API ключ в Settings → Tools → Ride"
            )
        }

        return try {
            val settings = service<PluginSettings>()
            
            // Системный промпт (опционально расширяем инструкциями формата)
            val systemPromptForRequest = buildSystemPrompt()

            // Полная история диалога для контекста
            val conversationHistory = buildConversationHistory(context)
            
            // Управляем контекстом: проверяем токены и сжимаем если нужно
            // ВАЖНО: userMessage уже добавлено в conversationHistory в ChatService,
            // поэтому передаём пустую строку чтобы избежать двойного подсчёта
            val (managedHistory, systemMessage) = manageContext(
                systemPrompt = systemPromptForRequest,
                userMessage = "",  // Уже в conversationHistory
                conversationHistory = conversationHistory,
                project = context.project
            )

            // Делегируем запрос в LLM провайдер с переданными параметрами
            val llmResponse = llmProvider.sendRequest(
                systemPrompt = systemPromptForRequest,
                userMessage = request,
                conversationHistory = managedHistory,
                parameters = parameters
            )

            // Проверяем успешность ответа
            if (!llmResponse.success) {
                return AgentResponse.error(
                    error = llmResponse.error ?: "Неизвестная ошибка",
                    content = "Извините, произошла ошибка при обработке запроса."
                )
            }

            // Парсим ответ если задан формат и включен анализ неопределенности
            val parsedResponse = if (settings.enableUncertaintyAnalysis) {
                responseSchema?.parseResponse(llmResponse.content)
            } else {
                null
            }

            // Анализируем неопределенность ответа из распарсенных данных или из сырого текста
            val (uncertainty, isFinal) = if (settings.enableUncertaintyAnalysis) {
                when (val parsed = parsedResponse) {
                    is XmlResponseData -> {
                        // Берем данные из распарсенного XML
                        Pair(parsed.uncertainty, parsed.isFinal)
                    }

                    is JsonResponseData -> {
                        // Берем данные из распарсенного JSON
                        Pair(parsed.uncertainty, parsed.isFinal)
                    }

                    else -> {
                        // Анализируем из сырого текста
                        val uncertainty = UncertaintyAnalyzer.analyzeUncertainty(llmResponse.content, context)
                        val isFinal = UncertaintyAnalyzer.isFinalResponse(uncertainty)
                        Pair(uncertainty, isFinal)
                    }
                }
            } else {
                // Без анализа неопределенности - всегда финальный ответ с нулевой неопределенностью
                Pair(0.0, true)
            }

            // Собираем метаданные ответа
            val baseMetadata = mutableMapOf(
                "tokenUsage" to llmResponse.tokenUsage,
                "provider" to llmProvider.getProviderName()
            )

            if (responseFormat != null) {
                baseMetadata["format"] = responseFormat!!.name
            }
            
            // Добавляем системное сообщение о сжатии если есть
            if (systemMessage != null) {
                baseMetadata["systemMessage"] = systemMessage
                // Добавляем сжатую историю для замены в ChatService
                baseMetadata["compressedHistory"] = managedHistory.map { msg ->
                    val role = when (msg.role) {
                        ConversationRole.USER -> MessageRole.USER
                        ConversationRole.ASSISTANT -> MessageRole.ASSISTANT
                        ConversationRole.SYSTEM -> MessageRole.SYSTEM
                    }
                    Message(content = msg.content, role = role)
                }
            }

            // Добавляем информацию о неопределенности
            baseMetadata["uncertainty"] = uncertainty
            if (settings.enableUncertaintyAnalysis) {
                baseMetadata["hasClarifyingQuestions"] = UncertaintyAnalyzer.hasExplicitUncertainty(llmResponse.content)
            }

            // Если парсинг не удался (только при включенном анализе неопределенности), возвращаем ошибку
            val currentSchema = responseSchema
            if (settings.enableUncertaintyAnalysis && currentSchema != null && parsedResponse == null) {
                logger.warn("Failed to parse response with format ${currentSchema.format}")
                val errorContent = buildString {
                    appendLine("⚠️ **Ошибка парсинга ответа:** Не удалось распарсить ответ в формате ${currentSchema.format}")
                    appendLine()
                    appendLine("**Сырой ответ от агента:**")
                    appendLine("```${currentSchema.format.name.lowercase()}")
                    appendLine(llmResponse.content)
                    appendLine("```")
                }
                return AgentResponse.error(
                    error = "Ошибка парсинга ответа: не удалось распарсировать ${currentSchema.format}",
                    content = errorContent
                )
            }

            // Формируем контент на основе распарсенных данных
            val finalContent = when (val parsed = parsedResponse) {
                is XmlResponseData -> {
                    // Используем ResponseFormatter для форматирования с уточняющими вопросами
                    formatXmlResponseData(parsed)
                }

                is JsonResponseData -> {
                    // Используем ResponseFormatter для форматирования с уточняющими вопросами
                    formatJsonResponseData(parsed)
                }

                is TextResponseData -> parsed.content
                else -> llmResponse.content
            }
            println("DEBUG finalContent=$finalContent")

            // Возвращаем успешный ответ с учетом неопределенности
            AgentResponse.success(
                content = finalContent,
                isFinal = isFinal,
                uncertainty = uncertainty,
                metadata = baseMetadata + mapOf("parsedData" to (parsedResponse != null))
            )

        } catch (e: Exception) {
            logger.error("Error processing request", e)
            AgentResponse.error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Произошла непредвиденная ошибка при обработке запроса."
            )
        }
    }

    override fun updateSettings(settings: AgentSettings) {
        logger.info("Updating agent settings: $settings")
        this.settings = settings
        
        // Обновляем формат ответа если указан
        if (settings.defaultResponseFormat != responseFormat) {
            responseFormat = settings.defaultResponseFormat
            responseSchema = when (settings.defaultResponseFormat) {
                ResponseFormat.XML -> UncertaintyResponseSchema.createXmlSchema()
                ResponseFormat.JSON -> UncertaintyResponseSchema.createJsonSchema()
                ResponseFormat.TEXT -> null
            }
        }
    }

    override fun dispose() {
        logger.info("Disposing ChatAgent")
        // Освобождаем ресурсы если необходимо
    }

    /**
     * Возвращает текущий LLM провайдер (для внутреннего использования)
     */
    internal fun getProvider(): LLMProvider = llmProvider

    /**
     * Формирует системный промпт. Если задана схема ответа,
     * добавляет инструкции по формату в системный промпт.
     */
    private fun buildSystemPrompt(): String {
        val settings = service<PluginSettings>()
        val base = if (settings.enableUncertaintyAnalysis) {
            systemPrompt
        } else {
            SIMPLE_SYSTEM_PROMPT
        }
        return if (responseSchema != null && settings.enableUncertaintyAnalysis) {
            PromptFormatter.formatPrompt(base, responseSchema)
        } else base
    }

    /**
     * Строит полную историю диалога для LLM провайдера
     * Использует ВСЮ историю без ограничений - сжатие управляется через manageContext()
     */
    private fun buildConversationHistory(context: ChatContext): List<ConversationMessage> {
        // Берём ВСЮ историю, а не только последние N сообщений
        val allMessages = context.history
        return allMessages.map { message ->
            val role = when (message.role) {
                MessageRole.USER -> ConversationRole.USER
                MessageRole.ASSISTANT -> ConversationRole.ASSISTANT
                MessageRole.SYSTEM -> ConversationRole.SYSTEM
            }
            ConversationMessage(role, message.content)
        }
    }
    
    /**
     * Управляет контекстом с учётом лимита токенов
     * Сжимает историю если превышен лимит
     * 
     * @return Пара: (история для отправки, системное сообщение о сжатии или null)
     */
    private suspend fun manageContext(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        project: com.intellij.openapi.project.Project
    ): Pair<List<ConversationMessage>, String?> {
        // Получаем настройки из PluginSettings
        val pluginSettings = service<PluginSettings>()
        val maxContextTokens = pluginSettings.maxContextTokens
        val enableAutoSummarization = pluginSettings.enableAutoSummarization
        
        println("=== ChatAgent.manageContext() ===")
        println("Настройки:")
        println("  - maxContextTokens: $maxContextTokens")
        println("  - enableAutoSummarization: $enableAutoSummarization")
        println("  - conversationHistory.size: ${conversationHistory.size}")
        
        // Подсчитываем токены в запросе
        val requestTokens = tokenCounter.countRequestTokens(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            conversationHistory = conversationHistory
        )
        
        println("Подсчёт токенов:")
        println("  - systemPrompt: ${tokenCounter.countTokens(systemPrompt)} токенов")
        println("  - userMessage: ${tokenCounter.countTokens(userMessage)} токенов")
        println("  - conversationHistory: ${tokenCounter.countTokens(conversationHistory)} токенов")
        println("  - ИТОГО requestTokens: $requestTokens")
        
        logger.info("Request tokens: $requestTokens, max context tokens: $maxContextTokens")
        
        // Если не превышен лимит, возвращаем историю как есть
        if (requestTokens <= maxContextTokens) {
            println("✅ Лимит НЕ превышен ($requestTokens <= $maxContextTokens)")
            println("   История возвращается без изменений")
            println("=================================\n")
            return Pair(conversationHistory, null)
        }
        
        println("⚠️ ЛИМИТ ПРЕВЫШЕН! ($requestTokens > $maxContextTokens)")
        
        // Если превышен лимит и автосжатие выключено, обрезаем старые сообщения
        if (!enableAutoSummarization) {
            println("❌ Автосжатие ВЫКЛЮЧЕНО - обрезаем историю")
            logger.warn("Token limit exceeded ($requestTokens > $maxContextTokens), truncating history")
            val truncatedHistory = truncateHistory(
                systemPrompt, userMessage, conversationHistory, maxContextTokens
            )
            val systemMessage = "⚠️ История диалога была обрезана из-за превышения лимита токенов ($requestTokens > $maxContextTokens)"
            println("   Обрезано до ${truncatedHistory.size} сообщений")
            println("=================================\n")
            return Pair(truncatedHistory, systemMessage)
        }
        
        // Сжимаем историю через SummarizerAgent
        println("🔄 Автосжатие ВКЛЮЧЕНО - запускаем SummarizerAgent")
        logger.info("Token limit exceeded, summarizing history...")
        return try {
            val historyMessages = conversationHistory.map { msg ->
                val role = when (msg.role) {
                    ConversationRole.USER -> MessageRole.USER
                    ConversationRole.ASSISTANT -> MessageRole.ASSISTANT
                    ConversationRole.SYSTEM -> MessageRole.SYSTEM
                }
                Message(content = msg.content, role = role)
            }
            
            println("   Подготовка к сжатию:")
            println("   - Количество сообщений для сжатия: ${historyMessages.size}")
            
            // Создаём временный контекст для суммаризации
            val summaryContext = ChatContext(
                project = project,
                history = historyMessages
            )
            
            val summaryRequest = AgentRequest(
                request = "Создай краткое резюме истории диалога",
                context = summaryContext,
                parameters = LLMParameters.PRECISE
            )
            
            println("   Вызов SummarizerAgent.ask()...")
            val summaryResponse = summarizerAgent.ask(summaryRequest)
            
            if (summaryResponse.success) {
                println("   ✅ SummarizerAgent вернул успешный результат")
                println("   Длина резюме: ${summaryResponse.content.length} символов")
                
                // Создаём сжатую историю: резюме + последние N сообщений
                val summaryMessage = ConversationMessage(
                    role = ConversationRole.SYSTEM,
                    content = "[РЕЗЮМЕ ПРЕДЫДУЩЕГО ДИАЛОГА]\n${summaryResponse.content}"
                )
                
                val recentMessages = conversationHistory.takeLast(2) // Берём последние 2 сообщения
                val compressedHistory = listOf(summaryMessage) + recentMessages
                
                val compressedTokens = tokenCounter.countRequestTokens(
                    systemPrompt = systemPrompt,
                    userMessage = "",
                    conversationHistory = compressedHistory
                )
                
                println("   Результат сжатия:")
                println("   - Было сообщений: ${conversationHistory.size}")
                println("   - Стало сообщений: ${compressedHistory.size} (резюме + 2 последних)")
                println("   - Было токенов: $requestTokens")
                println("   - Стало токенов: $compressedTokens")
                println("   - Экономия: ${requestTokens - compressedTokens} токенов")
                
                val systemMessage = "🔄 История диалога была сжата для экономии токенов (было: $requestTokens токенов)"
                
                logger.info("History summarized successfully")
                println("=================================\n")
                Pair(compressedHistory, systemMessage)
            } else {
                println("   ❌ SummarizerAgent вернул ошибку: ${summaryResponse.error}")
                println("   Fallback: обрезаем историю")
                
                // Если сжатие не удалось, обрезаем историю
                logger.warn("Summarization failed, falling back to truncation")
                val truncatedHistory = truncateHistory(
                    systemPrompt, userMessage, conversationHistory, settings.maxContextTokens
                )
                val systemMessage = "⚠️ Не удалось сжать историю, она была обрезана"
                println("   Обрезано до ${truncatedHistory.size} сообщений")
                println("=================================\n")
                Pair(truncatedHistory, systemMessage)
            }
        } catch (e: Exception) {
            println("   ❌ ИСКЛЮЧЕНИЕ при сжатии: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            
            logger.error("Error during summarization", e)
            val truncatedHistory = truncateHistory(
                systemPrompt, userMessage, conversationHistory, settings.maxContextTokens
            )
            val systemMessage = "⚠️ Ошибка при сжатии истории: ${e.message}"
            println("   Fallback: обрезано до ${truncatedHistory.size} сообщений")
            println("=================================\n")
            Pair(truncatedHistory, systemMessage)
        }
    }
    
    /**
     * Обрезает историю диалога, удаляя старые сообщения
     */
    private fun truncateHistory(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>,
        maxTokens: Int
    ): List<ConversationMessage> {
        if (conversationHistory.isEmpty()) return emptyList()
        
        // Начинаем с последних сообщений и добавляем пока не превысим лимит
        val result = mutableListOf<ConversationMessage>()
        var currentTokens = tokenCounter.countTokens(systemPrompt) + 
                           tokenCounter.countTokens(userMessage) + 10 // overhead
        
        for (message in conversationHistory.asReversed()) {
            val messageTokens = tokenCounter.countTokens(message.content) + 4 // overhead
            if (currentTokens + messageTokens > maxTokens) {
                break
            }
            result.add(0, message)
            currentTokens += messageTokens
        }
        
        return result
    }

    companion object {
        /**
         * Системный промпт с анализом неопределенности
         */
        private val DEFAULT_SYSTEM_PROMPT = """
Ты - AI-ассистент для разработчиков в IntelliJ IDEA.
Твоя задача - помогать программистам с их вопросами о коде, отладке и разработке.

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
     """.trimIndent()
        
        /**
         * Упрощенный системный промпт без анализа неопределенности
         */
        private val SIMPLE_SYSTEM_PROMPT = """
Ты - AI-ассистент для разработчиков в IntelliJ IDEA.
Твоя задача - помогать программистам с их вопросами о коде, отладке и разработке.

Отвечай четко и по существу, без лишних рассуждений.
Используй markdown для форматирования, но экранируй спецсимволы HTML.
Будь дружелюбным и профессиональным.
     """.trimIndent()
    }
}
