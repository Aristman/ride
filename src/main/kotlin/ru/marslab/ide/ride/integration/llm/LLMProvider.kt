package ru.marslab.ide.ride.integration.llm

import ru.marslab.ide.ride.model.LLMParameters
import ru.marslab.ide.ride.model.LLMResponse

/**
 * Интерфейс для работы с LLM провайдерами
 * 
 * Абстракция позволяет легко добавлять поддержку различных LLM
 * (Yandex GPT, OpenAI, Claude и т.д.) без изменения кода агентов
 */
interface LLMProvider {
    /**
     * Отправляет запрос в LLM и получает ответ
     * 
     * @param systemPrompt Системный промпт от агента (контекст и инструкции)
     * @param userMessage Пользовательское обращение к модели
     * @param assistantHistory Список ответов модели (assistant) для сохранения контекста
     * @param parameters Параметры генерации
     * @return Ответ от LLM
     */
    suspend fun sendRequest(
        systemPrompt: String,
        userMessage: String,
        assistantHistory: List<String>,
        parameters: LLMParameters
    ): LLMResponse
    
    /**
     * Проверяет доступность провайдера
     * 
     * @return true если провайдер настроен и доступен
     */
    fun isAvailable(): Boolean
    
    /**
     * Возвращает имя провайдера
     * 
     * @return Имя провайдера (например, "Yandex GPT", "OpenAI")
     */
    fun getProviderName(): String
}
