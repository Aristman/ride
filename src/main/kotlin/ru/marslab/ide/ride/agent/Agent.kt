package ru.marslab.ide.ride.agent

import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.AgentResponse
import ru.marslab.ide.ride.model.ChatContext
import ru.marslab.ide.ride.model.LLMParameters
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema

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
     * @param parameters Параметры для LLM (температура, maxTokens и т.д.)
     * @return Ответ агента
     */
    suspend fun processRequest(
        request: String, 
        context: ChatContext, 
        parameters: LLMParameters = LLMParameters.DEFAULT
    ): AgentResponse
    
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
    
    /**
     * Устанавливает LLM провайдер для агента
     * 
     * @param provider Новый провайдер
     */
    fun setLLMProvider(provider: LLMProvider)
    
    /**
     * Возвращает текущий LLM провайдер
     * 
     * @return Текущий провайдер
     */
    fun getLLMProvider(): LLMProvider
    
    /**
     * Устанавливает формат ответа с опциональной схемой
     * 
     * @param format Формат ответа (JSON, XML, TEXT)
     * @param schema Схема для структурированного ответа (опционально)
     */
    fun setResponseFormat(format: ResponseFormat, schema: ResponseSchema? = null)
    
    /**
     * Возвращает текущий формат ответа
     *
     * @return Текущий формат или null если не установлен
     */
    fun getResponseFormat(): ResponseFormat?

    /**
     * Возвращает текущую схему ответа
     *
     * @return Текущая схема или null если не установлена
     */
    fun getResponseSchema(): ResponseSchema?
    
    /**
     * Сбрасывает формат ответа к дефолтному (TEXT)
     */
    fun clearResponseFormat()
}
