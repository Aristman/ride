package ru.marslab.ide.ride.agent

import kotlinx.coroutines.flow.Flow
import ru.marslab.ide.ride.model.agent.AgentCapabilities
import ru.marslab.ide.ride.model.agent.AgentEvent
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.agent.AgentSettings

/**
 * Унифицированный интерфейс для всех агентов
 * 
 * Агент - это функциональная единица работы, которая обрабатывает запросы пользователя.
 * Агент НЕ привязан к конкретному LLM провайдеру и получает его через настройки.
 */
interface Agent {
    /**
     * Возможности агента
     */
    val capabilities: AgentCapabilities

    /**
     * Обрабатывает запрос пользователя (single request-response)
     * 
     * @param req Запрос к агенту
     * @return Ответ агента
     */
    suspend fun ask(req: AgentRequest): AgentResponse

    /**
     * Опциональный streaming API. Если не поддерживается, возвращает null.
     * 
     * @param req Запрос к агенту
     * @return Flow событий от агента или null, если streaming не поддерживается
     */
    fun start(req: AgentRequest): Flow<AgentEvent>? = null

    /**
     * Обновляет настройки агента во время выполнения
     * (LLM провайдер, форматы, MCP и т.д.)
     * 
     * @param settings Новые настройки
     */
    fun updateSettings(settings: AgentSettings)

    /**
     * Освобождает ресурсы агента
     */
    fun dispose()
}
