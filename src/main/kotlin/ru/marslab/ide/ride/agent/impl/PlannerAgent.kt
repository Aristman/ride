package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.agent.formatter.PromptFormatter
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentCapabilities
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.agent.AgentSettings
import ru.marslab.ide.ride.model.schema.ResponseFormat
import ru.marslab.ide.ride.model.schema.ResponseSchema
import ru.marslab.ide.ride.model.schema.TaskPlanData
import ru.marslab.ide.ride.model.schema.TaskPlanSchema

/**
 * Агент для создания плана задач
 *
 * Получает запрос пользователя и создает структурированный план задач,
 * который затем будет выполнен ExecutorAgent.
 *
 * @property llmProvider Провайдер для взаимодействия с LLM
 */
class PlannerAgent(
    private val llmProvider: LLMProvider
) : Agent {

    private val logger = Logger.getInstance(PlannerAgent::class.java)
    private var responseSchema: ResponseSchema = TaskPlanSchema.createJsonSchema()

    override val capabilities: AgentCapabilities = AgentCapabilities(
        stateful = false,
        streaming = false,
        reasoning = true,
        tools = emptySet(),
        systemPrompt = SYSTEM_PROMPT,
        responseRules = listOf(
            "Создавать структурированный план задач",
            "Разбивать сложные задачи на простые шаги",
            "Формулировать четкие промпты для ExecutorAgent"
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
            // Формируем системный промпт с инструкциями по формату
            val systemPromptWithSchema = PromptFormatter.formatPrompt(SYSTEM_PROMPT, responseSchema)

            // Отправляем запрос в LLM
            val llmResponse = llmProvider.sendRequest(
                systemPrompt = systemPromptWithSchema,
                userMessage = req.request,
                conversationHistory = emptyList(),
                parameters = req.parameters
            )

            if (!llmResponse.success) {
                return AgentResponse.error(
                    error = llmResponse.error ?: "Неизвестная ошибка",
                    content = "Ошибка при создании плана задач"
                )
            }

            // Парсим план из ответа
            val parsedPlan = responseSchema.parseResponse(llmResponse.content)

            if (parsedPlan == null || parsedPlan !is TaskPlanData) {
                logger.warn("Failed to parse task plan from response")
                return AgentResponse.error(
                    error = "Не удалось распарсить план задач",
                    content = buildString {
                        appendLine("⚠️ **Ошибка парсинга плана задач**")
                        appendLine()
                        appendLine("**Сырой ответ:**")
                        appendLine("```")
                        appendLine(llmResponse.content)
                        appendLine("```")
                    }
                )
            }

            val plan = parsedPlan.plan

            // Проверяем, что план не пустой
            if (plan.isEmpty()) {
                return AgentResponse.error(
                    error = "План задач пуст",
                    content = "Не удалось создать план задач для данного запроса"
                )
            }

            // Формируем читаемое представление плана
            val planContent = buildString {
                appendLine("📋 **План задач создан**")
                appendLine()
                appendLine("**Цель:** ${plan.description}")
                appendLine()
                appendLine("**Задачи (${plan.size()}):**")
                plan.tasks.forEach { task ->
                    appendLine()
                    appendLine("**${task.id}. ${task.title}**")
                    appendLine("   ${task.description}")
                }
            }

            // Возвращаем успешный ответ с планом
            AgentResponse.success(
                content = planContent,
                parsedContent = parsedPlan,
                metadata = mapOf(
                    "tokensUsed" to llmResponse.tokensUsed,
                    "provider" to llmProvider.getProviderName(),
                    "tasksCount" to plan.size()
                )
            )

        } catch (e: Exception) {
            logger.error("Error creating task plan", e)
            AgentResponse.error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Произошла ошибка при создании плана задач"
            )
        }
    }

    override fun updateSettings(settings: AgentSettings) {
        logger.info("Updating PlannerAgent settings: $settings")
        // Обновляем схему ответа если нужно
        responseSchema = when (settings.defaultResponseFormat) {
            ResponseFormat.JSON -> TaskPlanSchema.createJsonSchema()
            ResponseFormat.XML -> TaskPlanSchema.createXmlSchema()
            ResponseFormat.TEXT -> TaskPlanSchema.createJsonSchema() // По умолчанию JSON
        }
    }

    override fun dispose() {
        logger.info("Disposing PlannerAgent")
    }

    companion object {
        private val SYSTEM_PROMPT = """
Ты - агент-планировщик (PlannerAgent) в системе AI-ассистента для разработчиков.
Твоя задача - анализировать запросы пользователя и создавать структурированный план задач.

ПРАВИЛА СОЗДАНИЯ ПЛАНА:
1. Разбивай сложные задачи на простые, последовательные шаги
2. Каждая задача должна быть атомарной и выполнимой независимо
3. Формулируй четкие промпты для ExecutorAgent - он будет выполнять каждую задачу отдельно
4. Промпт должен содержать всю необходимую информацию для выполнения задачи
5. Нумеруй задачи последовательно, начиная с 1

СТРУКТУРА ПЛАНА:
- description: краткое описание общей цели (1-2 предложения)
- tasks: массив задач, где каждая задача содержит:
  - id: порядковый номер задачи
  - title: краткое название задачи (3-5 слов)
  - description: подробное описание задачи (1-2 предложения)
  - prompt: детальный промпт для ExecutorAgent с полным контекстом

ВАЖНО:
- ExecutorAgent НЕ видит другие задачи и результаты предыдущих задач
- Каждый prompt должен быть самодостаточным
- Используй четкие, конкретные формулировки
- Избегай абстрактных или неоднозначных инструкций
        """.trimIndent()
    }
}
