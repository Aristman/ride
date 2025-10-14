package ru.marslab.ide.ride.model.agent

/**
 * Возможности агента
 *
 * @property stateful Поддерживает ли агент состояние между запросами
 * @property streaming Поддерживает ли агент потоковую передачу ответов
 * @property reasoning Поддерживает ли агент рассуждения (reasoning)
 * @property tools Набор инструментов, которые поддерживает агент
 * @property systemPrompt Системный промпт агента
 * @property responseRules Правила формирования ответов
 */
data class AgentCapabilities(
    val stateful: Boolean,
    val streaming: Boolean,
    val reasoning: Boolean,
    val tools: Set<String> = emptySet(),
    val systemPrompt: String? = null,
    val responseRules: List<String> = emptyList()
)
