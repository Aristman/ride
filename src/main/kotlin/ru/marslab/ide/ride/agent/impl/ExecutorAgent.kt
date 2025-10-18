package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.*
import ru.marslab.ide.ride.model.chat.*
import ru.marslab.ide.ride.model.llm.*
import ru.marslab.ide.ride.model.task.*
import ru.marslab.ide.ride.model.schema.*
import ru.marslab.ide.ride.model.mcp.*
import ru.marslab.ide.ride.formatter.CodeBlockFormatter

/**
 * Агент для выполнения задач из плана
 * 
 * Получает отдельную задачу с промптом и выполняет её.
 * НЕ видит другие задачи и результаты предыдущих задач.
 *
 * @property llmProvider Провайдер для взаимодействия с LLM
 */
class ExecutorAgent(
    private val llmProvider: LLMProvider
) : Agent {

    private val logger = Logger.getInstance(ExecutorAgent::class.java)
    private val codeBlockFormatter = CodeBlockFormatter()
    
    override val capabilities: AgentCapabilities = AgentCapabilities(
        stateful = false,
        streaming = false,
        reasoning = true,
        tools = emptySet(),
        systemPrompt = SYSTEM_PROMPT,
        responseRules = listOf(
            "Выполнять задачу согласно промпту",
            "Давать четкие и структурированные ответы",
            "Использовать markdown для форматирования"
        )
    )

    override suspend fun ask(req: AgentRequest): AgentResponse {
        if (!llmProvider.isAvailable()) {
            logger.warn("LLM provider is not available")
            return AgentResponse.error(
                error = "LLM провайдер недоступен",
                content = "Пожалуйста, настройте API ключ в Settings → Tools → Ride"
            )
        }

        return try {
            // Отправляем запрос в LLM
            val llmResponse = llmProvider.sendRequest(
                systemPrompt = SYSTEM_PROMPT,
                userMessage = req.request,
                conversationHistory = emptyList(),
                parameters = req.parameters
            )

            if (!llmResponse.success) {
                return AgentResponse.error(
                    error = llmResponse.error ?: "Неизвестная ошибка",
                    content = "Ошибка при выполнении задачи"
                )
            }

            // Используем форматтер для обработки блоков кода в ответе
            val formattedOutput = codeBlockFormatter.formatAsHtml(llmResponse.content)

            // Возвращаем результат выполнения с форматированным выводом
            AgentResponse.success(
                content = llmResponse.content,
                formattedOutput = formattedOutput,
                metadata = mapOf(
                    "tokensUsed" to llmResponse.tokensUsed,
                    "provider" to llmProvider.getProviderName()
                )
            )

        } catch (e: Exception) {
            logger.error("Error executing task", e)
            AgentResponse.error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Произошла ошибка при выполнении задачи"
            )
        }
    }

    override fun updateSettings(settings: AgentSettings) {
        logger.info("Updating ExecutorAgent settings: $settings")
        // ExecutorAgent не требует специальных настроек
    }

    override fun dispose() {
        logger.info("Disposing ExecutorAgent")
    }

    companion object {
        private val SYSTEM_PROMPT = """
Ты - агент-исполнитель (ExecutorAgent) в системе AI-ассистента для разработчиков.
Твоя задача - выполнять конкретные задачи, которые тебе передает планировщик.

ПРАВИЛА ВЫПОЛНЕНИЯ:
1. Внимательно читай промпт задачи - он содержит всю необходимую информацию
2. Выполняй ТОЛЬКО то, что указано в промпте
3. Давай четкие, структурированные ответы
4. Используй markdown для форматирования кода и текста
5. Если задача требует написания кода - предоставь полный, рабочий код
6. Если задача требует объяснения - будь кратким и понятным

ВАЖНО:
- Ты НЕ видишь другие задачи в плане
- Ты НЕ видишь результаты предыдущих задач
- Весь необходимый контекст уже включен в промпт задачи
- Сосредоточься на качественном выполнении текущей задачи
        """.trimIndent()
    }
}
