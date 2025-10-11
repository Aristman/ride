package ru.marslab.ide.ride.model

/**
 * Настройки агента
 *
 * @property llmProvider Провайдер LLM (например, "openai", "anthropic")
 * @property model Модель LLM (например, "gpt-4", "claude-3")
 * @property mcpEnabled Включена ли поддержка MCP (Model Context Protocol)
 * @property defaultResponseFormat Формат ответа по умолчанию
 * @property maxContextTokens Максимальное количество токенов в контексте (запрос + история)
 * @property enableAutoSummarization Включить автоматическое сжатие истории при превышении лимита
 */
data class AgentSettings(
    val llmProvider: String? = null,
    val model: String? = null,
    val mcpEnabled: Boolean = false,
    val defaultResponseFormat: ResponseFormat = ResponseFormat.TEXT,
    val maxContextTokens: Int = 8000,
    val enableAutoSummarization: Boolean = true
)
