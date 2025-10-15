package ru.marslab.ide.ride.agent.impl

import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.*
import ru.marslab.ide.ride.model.chat.*
import ru.marslab.ide.ride.model.llm.*
import ru.marslab.ide.ride.model.task.*
import ru.marslab.ide.ride.model.schema.*
import ru.marslab.ide.ride.model.mcp.*

/**
 * Агент для сжатия (суммаризации) истории диалога
 * 
 * Используется для уменьшения количества токенов в контексте
 * путём создания краткого резюме предыдущих сообщений
 */
class SummarizerAgent(
    private val llmProvider: LLMProvider
) : Agent {
    
    override val capabilities: AgentCapabilities = AgentCapabilities(
        stateful = false,
        streaming = false,
        reasoning = true,
        tools = emptySet(),
        systemPrompt = "Агент для сжатия истории диалога",
        responseRules = listOf("Создавать краткое резюме диалога")
    )
    
    private val systemPrompt = """
        Ты - специализированный агент для сжатия истории диалога.
        
        Твоя задача:
        1. Прочитать историю диалога между пользователем и ассистентом
        2. Создать краткое, но информативное резюме ключевых моментов
        3. Сохранить важный контекст и факты
        4. Удалить повторения и несущественные детали
        
        Формат резюме:
        - Используй маркированный список для ключевых моментов
        - Сохраняй хронологию важных событий
        - Выделяй нерешённые вопросы или задачи
        - Будь максимально кратким, но не теряй важную информацию
        
        Отвечай ТОЛЬКО резюме, без дополнительных комментариев.
    """.trimIndent()
    
    override suspend fun ask(req: AgentRequest): AgentResponse {
        // Формируем запрос на суммаризацию из истории
        val history = req.context.history.map { message ->
            val role = when (message.role) {
                MessageRole.USER -> ConversationRole.USER
                MessageRole.ASSISTANT -> ConversationRole.ASSISTANT
                MessageRole.SYSTEM -> ConversationRole.SYSTEM
            }
            ConversationMessage(role, message.content)
        }
        val summaryRequest = buildSummaryRequest(history)
        
        // Отправляем запрос к LLM (без истории, т.к. мы её сжимаем)
        val response = llmProvider.sendRequest(
            systemPrompt = systemPrompt,
            userMessage = summaryRequest,
            conversationHistory = emptyList(),
            parameters = req.parameters.copy(
                temperature = 0.3, // Низкая температура для более точного резюме
                maxTokens = 1000   // Ограничиваем размер резюме
            )
        )
        
        return if (response.success) {
            AgentResponse.success(
                content = response.content,
                isFinal = true,
                uncertainty = 0.0
            )
        } else {
            AgentResponse.error(
                error = response.error ?: "Ошибка при создании резюме",
                content = "Не удалось создать резюме истории диалога"
            )
        }
    }
    
    override fun updateSettings(settings: AgentSettings) {
        // Summarizer не требует обновления настроек
    }
    
    override fun dispose() {
        // Нет ресурсов для освобождения
    }
    
    /**
     * Формирует запрос на суммаризацию из истории диалога
     */
    private fun buildSummaryRequest(history: List<ConversationMessage>): String {
        if (history.isEmpty()) {
            return "История диалога пуста."
        }
        
        val historyText = StringBuilder()
        historyText.appendLine("Создай краткое резюме следующей истории диалога:\n")
        
        history.forEach { message ->
            val role = when (message.role) {
                ConversationRole.USER -> "Пользователь"
                ConversationRole.ASSISTANT -> "Ассистент"
                ConversationRole.SYSTEM -> "Система"
            }
            historyText.appendLine("$role: ${message.content}")
            historyText.appendLine()
        }
        
        return historyText.toString()
    }
    
    /**
     * Создаёт сжатое сообщение из резюме для использования в контексте
     */
    fun createSummaryMessage(summaryContent: String): ConversationMessage {
        return ConversationMessage(
            role = ConversationRole.SYSTEM,
            content = "[РЕЗЮМЕ ПРЕДЫДУЩЕГО ДИАЛОГА]\n$summaryContent"
        )
    }
}
