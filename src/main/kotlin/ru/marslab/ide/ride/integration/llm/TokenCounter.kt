package ru.marslab.ide.ride.integration.llm

import ru.marslab.ide.ride.model.ConversationMessage

/**
 * Интерфейс для подсчёта токенов в тексте
 * 
 * Разные LLM провайдеры используют разные токенизаторы,
 * поэтому нужна абстракция для подсчёта токенов
 */
interface TokenCounter {
    /**
     * Подсчитывает количество токенов в тексте
     * 
     * @param text Текст для подсчёта
     * @return Количество токенов
     */
    fun countTokens(text: String): Int
    
    /**
     * Подсчитывает количество токенов в списке сообщений
     * 
     * @param messages Список сообщений
     * @return Количество токенов
     */
    fun countTokens(messages: List<ConversationMessage>): Int {
        return messages.sumOf { countTokens(it.content) }
    }
    
    /**
     * Подсчитывает общее количество токенов для запроса
     * (системный промпт + история + текущее сообщение)
     * 
     * @param systemPrompt Системный промпт
     * @param userMessage Сообщение пользователя
     * @param conversationHistory История диалога
     * @return Количество токенов
     */
    fun countRequestTokens(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<ConversationMessage>
    ): Int {
        val systemTokens = if (systemPrompt.isNotBlank()) countTokens(systemPrompt) else 0
        val historyTokens = countTokens(conversationHistory)
        val messageTokens = countTokens(userMessage)
        
        // Добавляем небольшой overhead на форматирование (примерно 4 токена на сообщение)
        val overheadTokens = (conversationHistory.size + 2) * 4
        
        return systemTokens + historyTokens + messageTokens + overheadTokens
    }
    
    /**
     * Возвращает имя токенизатора
     */
    fun getTokenizerName(): String
}
