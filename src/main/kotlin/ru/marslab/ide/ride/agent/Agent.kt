package ru.marslab.ide.ride.agent

import ru.marslab.ide.ride.model.AgentResponse
import ru.marslab.ide.ride.model.ChatContext

/**
 * Базовый интерфейс для всех агентов
 * 
 * Агент - это функциональная единица работы, которая обрабатывает запросы пользователя.
 * Агент НЕ привязан к конкретному LLM провайдеру и получает его через dependency injection.
 */
interface Agent {
    /**
     * Обрабатывает запрос пользователя
     * 
     * @param request Текст запроса пользователя
     * @param context Контекст чата (история, проект, файлы)
     * @return Ответ агента
     */
    suspend fun processRequest(request: String, context: ChatContext): AgentResponse
    
    /**
     * Возвращает имя агента
     * 
     * @return Имя агента для отображения в UI
     */
    fun getName(): String
    
    /**
     * Возвращает описание агента
     * 
     * @return Описание функциональности агента
     */
    fun getDescription(): String
}
