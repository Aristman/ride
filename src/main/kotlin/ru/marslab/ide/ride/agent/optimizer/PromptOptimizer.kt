package ru.marslab.ide.ride.agent.optimizer

import ru.marslab.ide.ride.model.orchestrator.ComplexityLevel
import ru.marslab.ide.ride.agent.analyzer.UncertaintyResult
import ru.marslab.ide.ride.model.chat.ChatContext

/**
 * Оптимизатор системных промптов для разных сценариев использования
 *
 * Адаптирует промпты в зависимости от сложности запроса для достижения
 * максимальной производительности и качества ответов.
 */
object PromptOptimizer {

    /**
     * Возвращает оптимизированный системный промпт на основе анализа неопределенности
     */
    fun getOptimizedSystemPrompt(
        baseSystemPrompt: String,
        uncertainty: UncertaintyResult,
        context: ChatContext
    ): String {
        return when (uncertainty.complexity) {
            ComplexityLevel.LOW -> getSimplePrompt(baseSystemPrompt, uncertainty, context)
            ComplexityLevel.MEDIUM -> getMediumPrompt(baseSystemPrompt, uncertainty, context)
            ComplexityLevel.HIGH -> getComplexPrompt(baseSystemPrompt, uncertainty, context)
            ComplexityLevel.VERY_HIGH -> getComplexPrompt(baseSystemPrompt, uncertainty, context) // Используем тот же метод
        }
    }

    /**
     * Оптимизированный промпт для простых запросов
     *
     * Принципы оптимизации:
     * - Короткие и четкие инструкции
     * - Минимум контекстной информации
     * - Фокус на прямом ответе
     * - Быстрая обработка
     */
    private fun getSimplePrompt(
        basePrompt: String,
        uncertainty: UncertaintyResult,
        context: ChatContext
    ): String {
        return buildString {
            appendLine("Отвечай кратко и по существу.")
            appendLine("Избегай лишних деталей и уточнений.")
            appendLine("Если вопрос простой - дай прямой ответ.")

            // Добавляем контекст только если он релевантен
            if (hasRelevantContext(context)) {
                appendLine("\nКонтекст:")
                appendLine(getRelevantContextSummary(context))
            }

            appendLine("\nОсновная роль:")
            appendLine(basePrompt.take(200)) // Ограничиваем базовый промпт
        }
    }

    /**
     * Оптимизированный промпт для запросов средней сложности
     *
     * Принципы оптимизации:
     * - Структурированный ответ
     * - Умеренный уровень детализации
     * - Проверка на понятность
     */
    private fun getMediumPrompt(
        basePrompt: String,
        uncertainty: UncertaintyResult,
        context: ChatContext
    ): String {
        return buildString {
            appendLine("Предоставь структурированный ответ.")
            appendLine("Включи основные моменты и примеры если необходимо.")
            appendLine("Объясни ключевые концепции.")

            if (hasRelevantContext(context)) {
                appendLine("\nКонтекст:")
                appendLine(getFormattedContext(context))
            }

            appendLine("\nРоль и инструкции:")
            appendLine(basePrompt.take(500)) // Средняя длина промпта

            appendLine("\nСтиль ответа:")
            appendLine("- Четкая структура")
            appendLine("- Практические примеры при необходимости")
            appendLine("- Без излишней детализации")
        }
    }

    /**
     * Оптимизированный промпт для сложных запросов
     *
     * Принципы оптимизации:
     * - Полный контекст и инструкции
     * - Многоуровневый анализ
     * - Детальные объяснения
     */
    private fun getComplexPrompt(
        basePrompt: String,
        uncertainty: UncertaintyResult,
        context: ChatContext
    ): String {
        return buildString {
            appendLine("Выполни детальный анализ запроса.")
            appendLine("Учитывай все аспекты и возможные подходы.")
            appendLine("Предоставь исчерпывающий ответ с примерами.")

            if (hasRelevantContext(context)) {
                appendLine("\nПолный контекст:")
                appendLine(getDetailedContext(context))
            }

            appendLine("\nПолные инструкции:")
            appendLine(basePrompt)

            appendLine("\nТребования к ответу:")
            appendLine("- Комплексный анализ")
            appendLine("- Альтернативные подходы")
            appendLine("- Практические рекомендации")
            appendLine("- Возможные риски и ограничения")

            // Добавляем специфические инструкции based on uncertainty
            when {
                uncertainty.suggestedActions.contains("исследование") -> {
                    appendLine("\nДополнительно: проведи исследование темы")
                }
                uncertainty.suggestedActions.contains("сравнение") -> {
                    appendLine("\nДополнительно: сравни разные подходы")
                }
                uncertainty.suggestedActions.contains("рекомендации") -> {
                    appendLine("\nДополнительно: предоставь конкретные рекомендации")
                }
            }
        }
    }

    /**
     * Возвращает оптимизированный промпт для обработки ошибок
     */
    fun getErrorHandlingPrompt(originalError: String, context: ChatContext): String {
        return buildString {
            appendLine("Произошла ошибка. Проанализируй ситуацию и предложи решение.")
            appendLine("Ошибка: $originalError")

            if (hasRelevantContext(context)) {
                appendLine("\nКонтекст выполнения:")
                appendLine(getFormattedContext(context))
            }

            appendLine("\nТребования:")
            appendLine("1. Определи причину ошибки")
            appendLine("2. Предложи конкретные шаги для исправления")
            appendLine("3. Дай рекомендации по предотвращению в будущем")
        }
    }

    /**
     * Возвращает оптимизированный промпт для уточняющих вопросов
     */
    fun getClarificationPrompt(originalRequest: String, uncertainty: UncertaintyResult): String {
        return buildString {
            appendLine("Требуется уточнение запроса.")
            appendLine("Оригинальный запрос: $originalRequest")
            appendLine("Уровень неопределенности: ${uncertainty.score}")
            appendLine("Причина неопределенности: ${uncertainty.reasoning}")

            appendLine("\nЗадачи:")
            appendLine("1. Сформулируй уточняющие вопросы")
            appendLine("2. Укажи, какая именно информация нужна")
            appendLine("3. Предложи возможные варианты интерпретации")

            appendLine("\nСтиль:")
            appendLine("- Вежливые и конкретные вопросы")
            appendLine("- Максимум 3-5 вопросов")
            appendLine("- Четкое указание на необходимую информацию")
        }
    }

    /**
     * Возвращает оптимизированный промпт для fast-path обработки
     */
    fun getFastPathPrompt(basePrompt: String): String {
        return buildString {
            appendLine("Отвечай максимально быстро и кратко.")
            appendLine("Простой вопрос - простой ответ.")
            appendLine("Без анализов и рассуждений.")
            appendLine(basePrompt.take(100))
        }
    }

    /**
     * Возвращает промпт для планирования
     */
    fun getPlanningPrompt(request: String, context: ChatContext): String {
        return buildString {
            appendLine("Создай детальный план выполнения запроса.")
            appendLine("Запрос: $request")

            if (hasRelevantContext(context)) {
                appendLine("\nДоступный контекст:")
                appendLine(getFormattedContext(context))
            }

            appendLine("\nТребования к плану:")
            appendLine("1. Разбей задачу на конкретные шаги")
            appendLine("2. Укажи необходимый инструментарий")
            appendLine("3. Определи зависимости между шагами")
            appendLine("4. Оцени сложность каждого шага")
            appendLine("5. Предложи критерии завершения")
        }
    }

    /**
     * Проверяет наличие релевантного контекста
     */
    private fun hasRelevantContext(context: ChatContext): Boolean {
        return context.history.isNotEmpty() ||
               context.additionalContext.isNotEmpty()
    }

    /**
     * Возвращает краткое изложение релевантного контекста
     */
    private fun getRelevantContextSummary(context: ChatContext): String {
        return buildString {
            if (context.additionalContext.isNotEmpty()) {
                context.additionalContext.forEach { (key, value) ->
                    appendLine("- $key: $value")
                }
            }

            // Только последние сообщения для простых запросов
            context.history.takeLast(2).forEach { message ->
                appendLine("- ${message.role.name}: ${message.content.take(50)}...")
            }
        }
    }

    /**
     * Возвращает отформатированный контекст для запросов средней сложности
     */
    private fun getFormattedContext(context: ChatContext): String {
        return buildString {
            if (context.additionalContext.isNotEmpty()) {
                appendLine("Дополнительный контекст:")
                context.additionalContext.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
                appendLine()
            }

            if (context.history.isNotEmpty()) {
                appendLine("История диалога:")
                context.history.takeLast(5).forEach { message ->
                    appendLine("  ${message.role.name}: ${message.content}")
                }
            }
        }
    }

    /**
     * Возвращает детальный контекст для сложных запросов
     */
    private fun getDetailedContext(context: ChatContext): String {
        return buildString {
            if (context.additionalContext.isNotEmpty()) {
                appendLine("Полный дополнительный контекст:")
                context.additionalContext.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
                appendLine()
            }

            if (context.history.isNotEmpty()) {
                appendLine("Полная история диалога:")
                context.history.forEach { message ->
                    appendLine("  ${message.role.name}: ${message.content}")
                }
            }
        }
    }

    /**
     * Анализирует эффективность промпта и предлагает улучшения
     */
    fun analyzePromptEffectiveness(
        prompt: String,
        responseTime: Long,
        responseQuality: Double
    ): PromptOptimizationSuggestion {
        return when {
            responseTime > 5000 && prompt.length > 1000 -> {
                PromptOptimizationSuggestion(
                    issue = "Слишком длинный промпт",
                    suggestion = "Укоротите промпт, оставьте только ключевые инструкции",
                    expectedImprovement = "Сокращение времени ответа на 30-50%"
                )
            }
            responseTime < 1000 && responseQuality < 0.7 -> {
                PromptOptimizationSuggestion(
                    issue = "Слишком короткий промпт",
                    suggestion = "Добавьте больше контекста и конкретных инструкций",
                    expectedImprovement = "Повышение качества ответа на 20-40%"
                )
            }
            else -> {
                PromptOptimizationSuggestion(
                    issue = "Оптимизация не требуется",
                    suggestion = "Текущий промпт эффективен",
                    expectedImprovement = "Текущая производительность оптимальна"
                )
            }
        }
    }
}

/**
 * Предложение по оптимизации промпта
 */
data class PromptOptimizationSuggestion(
    val issue: String,
    val suggestion: String,
    val expectedImprovement: String
)