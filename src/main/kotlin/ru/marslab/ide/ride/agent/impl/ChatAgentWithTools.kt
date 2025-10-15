package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTConfig
import ru.marslab.ide.ride.integration.llm.impl.YandexGPTToolsProvider
import ru.marslab.ide.ride.mcp.MCPClient
import ru.marslab.ide.ride.mcp.MCPServerManager
import ru.marslab.ide.ride.mcp.MCPToolExecutor
import ru.marslab.ide.ride.mcp.MCPToolsRegistry
import ru.marslab.ide.ride.model.agent.*
import ru.marslab.ide.ride.model.chat.*
import ru.marslab.ide.ride.model.llm.*
import ru.marslab.ide.ride.model.task.*
import ru.marslab.ide.ride.model.schema.*
import ru.marslab.ide.ride.model.mcp.*

/**
 * Chat Agent с поддержкой MCP Tools через Yandex GPT Tools API
 */
class ChatAgentWithTools(
    private val config: YandexGPTConfig,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    
    private val logger = Logger.getInstance(ChatAgentWithTools::class.java)
    private val toolsProvider = YandexGPTToolsProvider(config)
    private val serverManager = MCPServerManager.getInstance()
    
    private val mcpClient by lazy {
        MCPClient(serverManager.getServerUrl())
    }
    
    private val toolExecutor by lazy {
        MCPToolExecutor(mcpClient)
    }
    
    companion object {
        // Базовый системный промпт для работы с tools
        private const val DEFAULT_SYSTEM_PROMPT = """
            Ты - AI ассистент для разработчиков с доступом к файловой системе через инструменты.

            ПРАВИЛА:
            1. ВСЕГДА используй инструменты, когда пользователь просит создать, прочитать, изменить или удалить файлы
            2. Не отвечай текстом о создании файлов - используй инструменты!
            3. После каждого запроса о файловых операциях вызывай соответствующий инструмент
            4. Даже если похожая операция уже выполнялась ранее, используй инструменты снова

            ПРАВИЛА ПУТЕЙ ФАЙЛОВ:
            - ВСЕГДА используй прямой слэш / в путях файлов (например: src/main/kotlin/Main.kt)
            - НЕ используй обратные слэши \ в путях
            - Примеры правильных путей: src/Main.kt, src/main/kotlin/MainActivity.kt
            - НЕ используй escape-последовательности
            - На Windows также используй прямые слэши / вместо \

            КРИТИЧЕСКИ ВАЖНО ДЛЯ МОДИФИКАЦИЙ ФАЙЛОВ:
            - Когда пользователь говорит "измени файл", "обнови файл", "модифицируй файл", "добавь код", "замени код" - ВСЕГДА используй инструмент update_file
            - НЕ генерируй измененное содержимое в текстовом ответе - ВСЕГДА вызывай update_file инструмент
            - СНАЧАЛА объясни, что ты собираешься сделать, ПОТОМ вызови update_file инструмент

            ВАЖНО: Когда получаешь результаты выполнения инструментов:
            - Если прочитал файл - покажи его содержимое пользователю
            - Если создал/изменил файл - сообщи об этом и покажи содержимое если нужно
            - Если удалил файл - сообщи об удалении
            - ВСЕГДА отвечай на запрос пользователя, не задавай встречных вопросов

            ДОСТУПНЫЕ ОПЕРАЦИИ:
            - Создание файлов (create_file)
            - Чтение файлов (read_file)
            - ОБНОВЛЕНИЕ ФАЙЛОВ (update_file) - ИСПОЛЬЗУЙ ДЛЯ ЛЮБЫХ ИЗМЕНЕНИЙ
            - Удаление файлов (delete_file)
            - Изменение файлов (update_file)

            ПРИМЕРЫ:
            - "измени файл" -> вызови update_file
            - "добавь функцию" -> вызови update_file
            - "замени код" -> вызови update_file
            - "обнови содержимое" -> вызови update_file

            Важно: объясняй свои действия ПЕРЕД использованием инструментов, но всегда используй инструменты для файловых операций.
        """

        private const val MAX_TOOL_ITERATIONS = 5
    }
    
    /**
     * Обработать запрос пользователя с поддержкой tool calling
     */
    suspend fun processRequest(
        userMessage: String,
        conversationHistory: List<ConversationMessage> = emptyList(),
        parameters: LLMParameters = LLMParameters()
    ): AgentResponse = withContext(Dispatchers.IO) {
        
        // Проверяем, запущен ли MCP Server
        val serverRunning = serverManager.isServerRunning()
        println("🔧 MCP Server running: $serverRunning")
        println("🔧 MCP Server URL: ${serverManager.getServerUrl()}")

        if (!serverRunning) {
            println("❌ MCP Server is not running - trying to start it")
            val started = serverManager.ensureServerRunning()
            println("🔧 MCP Server start result: $started")

            if (!started) {
                logger.warn("MCP Server is not running")
                return@withContext AgentResponse.error(
                    error = "MCP Server не запущен",
                    content = "Файловые операции недоступны. Запустите MCP Server в настройках."
                )
            }
        }
        
        try {
            // Получаем доступные tools
            val tools = MCPToolsRegistry.getAllTools()
            println("🔧 Available tools: ${tools.size}")
            tools.forEach { tool ->
                println("  📋 ${tool.function.name}: ${tool.function.description.take(100)}...")
            }

            // Формируем начальные сообщения
            val messages = buildInitialMessages(userMessage, conversationHistory)
            println("💬 Initial messages count: ${messages.size}")

            // Запускаем цикл tool calling
            val result = toolCallingLoop(messages, tools, parameters)
            
            AgentResponse.success(
                content = result.content,
                metadata = result.metadata
            )
            
        } catch (e: Exception) {
            logger.error("Error processing request with tools", e)
            AgentResponse.error(
                error = "Ошибка обработки запроса: ${e.message}",
                content = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Цикл tool calling
     */
    private suspend fun toolCallingLoop(
        initialMessages: List<YandexToolsMessage>,
        tools: List<Tool>,
        parameters: LLMParameters
    ): ToolCallingResult {
        var messages = initialMessages.toMutableList()
        var iteration = 0
        val executedTools = mutableListOf<String>()
        
        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++
            println("=== Tool Calling Iteration $iteration ===")
            println("Messages count: ${messages.size}")
            logger.info("Tool calling iteration $iteration, messages count: ${messages.size}")

            // Отправляем запрос с tools
            val response = toolsProvider.sendRequestWithTools(
                messages = messages,
                tools = tools,
                parameters = parameters
            )

            val alternative = response.result.alternatives.firstOrNull()
                ?: return ToolCallingResult(
                    content = "Пустой ответ от LLM",
                    metadata = emptyMap()
                )

            val message = alternative.message
            println("LLM Response: ${message.text}")
            logger.info("LLM response text: ${message.text}")

            // Enhanced logging for tool calls
            if (message.toolCallList == null) {
                println("🚨 NO TOOL CALLS in LLM response!")
                println("📝 Response was pure text instead of tool execution")
                logger.warn("LLM responded with text instead of tool calls")
            } else if (message.toolCallList.toolCalls.isEmpty()) {
                println("🚨 EMPTY TOOL CALL LIST in LLM response!")
                println("📝 Response had empty tool calls array")
                logger.warn("LLM responded with empty tool calls array")
            }

            // Проверяем, есть ли tool calls
            if (message.toolCallList != null && message.toolCallList.toolCalls.isNotEmpty()) {
                println("Tool calls requested: ${message.toolCallList.toolCalls.size}")
                logger.info("LLM requested ${message.toolCallList.toolCalls.size} tool calls")
                
                // Добавляем сообщение ассистента с tool calls
                messages.add(message)
                
                // Выполняем все tool calls
                val toolResults = message.toolCallList.toolCalls.map { toolCall ->
                    val functionCall = toolCall.functionCall
                    println("🔧 Executing tool: ${functionCall.name}")
                    logger.info("Executing tool: ${functionCall.name}")
                    executedTools.add(functionCall.name)

                    val result = toolExecutor.executeTool(functionCall)
                    println("✅ Tool result: ${result.functionResult.content}")
                    result
                }
                
                 // Добавляем результаты tool calls как часть сообщения пользователя
                // (Yandex GPT API не поддерживает роль 'tool')
                val toolResultsText = buildString {
                    appendLine("РЕЗУЛЬТАТЫ ВЫПОЛНЕНИЯ ИНСТРУМЕНТОВ:")
                    toolResults.forEach { result ->
                        appendLine()
                        appendLine("Операция: ${result.functionResult.name}")
                        appendLine("Результат:")
                        appendLine(result.functionResult.content)
                        appendLine("---")
                    }
                    appendLine()
                    appendLine("ОТВЕТЬ ПОЛЬЗОВАТЕЛЮ на основе этих результатов. Покажи содержимое файлов, сообщи о выполненных операциях.")
                }

                println("📝 Adding tool results as user message:")
                println(toolResultsText)

                messages.add(
                    YandexToolsMessage(
                        role = "user",
                        text = toolResultsText
                    )
                )
                
                // Продолжаем цикл для получения финального ответа
                continue
            }
            
            // Нет tool calls - это финальный ответ
            val content = message.text ?: "Нет ответа"
            val usage = response.result.usage

            println("⚠️ LLM provided final response WITHOUT tool calls!")
            println("📄 Final response: $content")
            println("🔧 Executed tools so far: ${executedTools.joinToString(", ")}")
            logger.warn("LLM provided final response without tool calls on iteration $iteration")
            logger.warn("Final response: $content")
            logger.warn("Executed tools so far: ${executedTools.joinToString(", ")}")

            return ToolCallingResult(
                content = content,
                metadata = mapOf(
                    "executedTools" to executedTools.joinToString(", "),
                    "iterations" to iteration.toString(),
                    "inputTokens" to usage.inputTextTokens,
                    "outputTokens" to usage.completionTokens,
                    "totalTokens" to usage.totalTokens
                )
            )
        }
        
        // Достигнут лимит итераций
        return ToolCallingResult(
            content = "Достигнут лимит итераций tool calling ($MAX_TOOL_ITERATIONS)",
            metadata = mapOf(
                "executedTools" to executedTools.joinToString(", "),
                "iterations" to iteration.toString()
            )
        )
    }
    
    /**
     * Построить начальные сообщения
     */
    private fun buildInitialMessages(
        userMessage: String,
        conversationHistory: List<ConversationMessage>
    ): List<YandexToolsMessage> {
        return buildList {
            // Системный промпт (только если не пустой и нет системных сообщений в истории)
            val hasSystemMessages = conversationHistory.any { it.role == ConversationRole.SYSTEM }
            if (systemPrompt.isNotBlank() && !hasSystemMessages) {
                println("🤖 Adding system prompt (no system messages in history)")
                add(YandexToolsMessage(role = "system", text = systemPrompt))
            } else if (hasSystemMessages) {
                println("🤖 Skipping system prompt - history already contains system messages")
            }

            // История диалога (включая системные сообщения)
            conversationHistory.forEach { convMsg ->
                val role = when (convMsg.role) {
                    ConversationRole.USER -> "user"
                    ConversationRole.ASSISTANT -> "assistant"
                    ConversationRole.SYSTEM -> "system"
                }
                if (convMsg.content.isNotBlank()) {
                    add(YandexToolsMessage(role = role, text = convMsg.content))
                    println("💬 Added ${convMsg.role} message: ${convMsg.content.take(50)}...")
                }
            }

            // Добавляем текущее сообщение пользователя только если его нет в истории
            val hasUserMessageInHistory = conversationHistory.any {
                it.role == ConversationRole.USER && it.content == userMessage
            }
            if (!hasUserMessageInHistory) {
                println("💬 Adding current user message: $userMessage")
                add(YandexToolsMessage(role = "user", text = userMessage))
            }
        }
    }
    
    private data class ToolCallingResult(
        val content: String,
        val metadata: Map<String, String>
    )
}
