package ru.marslab.ide.ride.model.agent

import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters

/**
 * Запрос к агенту
 *
 * @property request Текст запроса пользователя
 * @property context Контекст чата (история, проект, файлы)
 * @property parameters Параметры для LLM (температура, maxTokens и т.д.)
 */
data class AgentRequest(
    val request: String,
    val context: ChatContext,
    val parameters: LLMParameters = LLMParameters.DEFAULT
)
