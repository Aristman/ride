package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.orchestrator.InteractionType
import ru.marslab.ide.ride.model.orchestrator.UserPrompt
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.StepOutput
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep

/**
 * Агент для взаимодействия с пользователем
 *
 * Поддерживает различные типы взаимодействия:
 * - Подтверждение (Да/Нет)
 * - Выбор из списка
 * - Свободный ввод
 * - Множественный выбор
 */
class UserInteractionAgent : BaseToolAgent(
    agentType = AgentType.USER_INTERACTION,
    toolCapabilities = setOf(
        "user_input",
        "confirmation",
        "choice_selection",
        "multi_choice",
        "input_validation"
    )
) {
    override fun getDescription(): String = "Агент для интерактивного взаимодействия с пользователем"

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        logger.info("Executing user interaction step: ${step.id}")

        try {
            // Извлекаем параметры из входных данных
            val promptType = step.input.getString("prompt_type") ?: "confirmation"
            val message = step.input.getString("message") ?: "Подтвердите действие"
            val options = step.input.getList<String>("options") ?: getDefaultOptions(promptType)
            val defaultValue = step.input.getString("default_value")
            val timeoutMs = step.input.get<Long>("timeout_ms")
            val data = step.input.get<Map<String, Any>>("data") ?: emptyMap()

            // Создаем UserPrompt
            val interactionType = parseInteractionType(promptType)
            val userPrompt = UserPrompt(
                type = interactionType,
                message = message,
                options = options,
                defaultValue = defaultValue,
                timeout = timeoutMs,
                metadata = data
            )

            // Форматируем запрос для пользователя
            val formattedPrompt = userPrompt.format()

            logger.info("User interaction prompt created: $formattedPrompt")

            // Возвращаем результат, требующий пользовательского ввода
            return StepResult.requiresInput(
                prompt = formattedPrompt,
                output = StepOutput.of(
                    "prompt_id" to userPrompt.id,
                    "prompt_type" to interactionType.name,
                    "prompt_data" to userPrompt
                )
            )

        } catch (e: Exception) {
            logger.error("Error executing user interaction step", e)
            return StepResult.error(
                error = "Ошибка при создании запроса пользователю: ${e.message}",
                output = StepOutput.empty()
            )
        }
    }

    /**
     * Обрабатывает ответ пользователя
     */
    fun processUserResponse(
        prompt: UserPrompt,
        userInput: String
    ): StepResult {
        logger.info("Processing user response for prompt: ${prompt.id}")

        // Валидируем ввод
        val validationResult = prompt.validate(userInput)
        if (!validationResult.isValid) {
            logger.warn("User input validation failed: ${validationResult.errors}")
            return StepResult.error(
                error = "Некорректный ввод: ${validationResult.errors.joinToString(", ")}",
                output = StepOutput.of("validation_errors" to validationResult.errors)
            )
        }

        // Обрабатываем ответ в зависимости от типа
        val processedValue = when (prompt.type) {
            InteractionType.CONFIRMATION -> {
                parseConfirmation(userInput)
            }

            InteractionType.CHOICE -> {
                userInput
            }

            InteractionType.MULTI_CHOICE -> {
                userInput.split(",").map { it.trim() }
            }

            InteractionType.INPUT -> {
                userInput.ifBlank { prompt.defaultValue ?: "" }
            }
        }

        logger.info("User response processed successfully: $processedValue")

        return StepResult.success(
            output = StepOutput.of(
                "user_input" to userInput,
                "processed_value" to processedValue,
                "prompt_id" to prompt.id
            ),
            metadata = mapOf(
                "interaction_type" to prompt.type.name,
                "timestamp" to kotlinx.datetime.Clock.System.now().toString()
            )
        )
    }

    override fun validateInput(input: StepInput): ValidationResult {
        val message = input.getString("message")
        if (message.isNullOrBlank()) {
            return ValidationResult.failure("Сообщение для пользователя не может быть пустым")
        }

        val promptType = input.getString("prompt_type") ?: "confirmation"
        try {
            parseInteractionType(promptType)
        } catch (e: IllegalArgumentException) {
            return ValidationResult.failure("Неизвестный тип взаимодействия: $promptType")
        }

        return ValidationResult.success()
    }

    /**
     * Парсит тип взаимодействия из строки
     */
    private fun parseInteractionType(type: String): InteractionType {
        return when (type.lowercase()) {
            "confirmation" -> InteractionType.CONFIRMATION
            "choice" -> InteractionType.CHOICE
            "input" -> InteractionType.INPUT
            "multi_choice" -> InteractionType.MULTI_CHOICE
            else -> throw IllegalArgumentException("Unknown interaction type: $type")
        }
    }

    /**
     * Возвращает опции по умолчанию для типа взаимодействия
     */
    private fun getDefaultOptions(type: String): List<String> {
        return when (type.lowercase()) {
            "confirmation" -> listOf("Да", "Нет")
            else -> emptyList()
        }
    }

    /**
     * Парсит подтверждение в булево значение
     */
    private fun parseConfirmation(input: String): Boolean {
        val normalized = input.trim().lowercase()
        return when (normalized) {
            "да", "yes", "y", "1", "true" -> true
            "нет", "no", "n", "0", "false" -> false
            else -> normalized.startsWith("да") || normalized.startsWith("yes")
        }
    }

}
