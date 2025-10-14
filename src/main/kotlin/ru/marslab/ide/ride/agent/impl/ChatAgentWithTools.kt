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
import ru.marslab.ide.ride.model.*

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
        private const val DEFAULT_SYSTEM_PROMPT = """
            Ты - AI ассистент для разработчиков. 
            У тебя есть доступ к файловой системе через специальные инструменты (tools).
            
            Когда пользователь просит создать, прочитать, изменить или удалить файлы - используй соответствующие tools.
            Всегда объясняй, что ты делаешь, перед использованием tools.
            После выполнения операции с файлами, сообщи результат пользователю.
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
        if (!serverManager.isServerRunning()) {
            logger.warn("MCP Server is not running")
            return@withContext AgentResponse.error(
                error = "MCP Server не запущен",
                content = "Файловые операции недоступны. Запустите MCP Server в настройках."
            )
        }
        
        try {
            // Получаем доступные tools
            val tools = MCPToolsRegistry.getAllTools()
            
            // Формируем начальные сообщения
            val messages = buildInitialMessages(userMessage, conversationHistory)
            
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
            logger.info("Tool calling iteration $iteration")
            
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
            
            // Проверяем, есть ли tool calls
            if (message.toolCallList != null && message.toolCallList.toolCalls.isNotEmpty()) {
                logger.info("LLM requested ${message.toolCallList.toolCalls.size} tool calls")
                
                // Добавляем сообщение ассистента с tool calls
                messages.add(message)
                
                // Выполняем все tool calls
                val toolResults = message.toolCallList.toolCalls.map { toolCall ->
                    val functionCall = toolCall.functionCall
                    logger.info("Executing tool: ${functionCall.name}")
                    executedTools.add(functionCall.name)
                    
                    toolExecutor.executeTool(functionCall)
                }
                
                // Добавляем результаты tool calls
                messages.add(
                    YandexToolsMessage(
                        role = "tool",
                        toolResultList = ToolResultList(toolResults)
                    )
                )
                
                // Продолжаем цикл для получения финального ответа
                continue
            }
            
            // Нет tool calls - это финальный ответ
            val content = message.text ?: "Нет ответа"
            val usage = response.result.usage
            
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
            // Системный промпт
            if (systemPrompt.isNotBlank()) {
                add(YandexToolsMessage(role = "system", text = systemPrompt))
            }
            
            // История диалога
            conversationHistory.forEach { convMsg ->
                val role = when (convMsg.role) {
                    ConversationRole.USER -> "user"
                    ConversationRole.ASSISTANT -> "assistant"
                    ConversationRole.SYSTEM -> "system"
                }
                if (convMsg.content.isNotBlank()) {
                    add(YandexToolsMessage(role = role, text = convMsg.content))
                }
            }
            
            // Текущее сообщение пользователя
            add(YandexToolsMessage(role = "user", text = userMessage))
        }
    }
    
    private data class ToolCallingResult(
        val content: String,
        val metadata: Map<String, String>
    )
}
