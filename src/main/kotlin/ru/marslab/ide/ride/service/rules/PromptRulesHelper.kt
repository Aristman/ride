package ru.marslab.ide.ride.service.rules

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import ru.marslab.ide.ride.settings.PluginSettings

/**
 * Утилитарный класс для универсального применения правил к промптам
 *
 * Используется всеми агентами для добавления правил к системным промптам
 * без дублирования логики
 */
object PromptRulesHelper {

    /**
     * Применяет правила к базовому системному промпту
     *
     * @param basePrompt Базовый системный промпт
     * @param project Текущий проект (может быть null)
     * @return Системный промпт с добавленными правилами (если включены)
     */
    fun applyRulesToPrompt(basePrompt: String, project: Project?): String {
        val settings = service<PluginSettings>()

        // Если правила отключены в настройках, возвращаем базовый промпт
        if (!settings.enableCustomRules) {
            return basePrompt
        }

        // Добавляем правила через RulesService
        val rulesService = service<RulesService>()
        return rulesService.composeSystemPromptWithRules(basePrompt, project)
    }

    /**
     * Применяет правила к системному промпту и добавляет форматирование ответа если нужно
     *
     * @param basePrompt Базовый системный промпт
     * @param project Текущий проект
     * @param enableUncertaintyAnalysis Включен ли анализ неопределенности
     * @param responseFormat Формат ответа (если задан)
     * @return Готовый системный промпт с правилами и форматированием
     */
    fun applyRulesAndFormatting(
        basePrompt: String,
        project: Project?,
        enableUncertaintyAnalysis: Boolean,
        responseFormat: Any? = null
    ): String {
        val settings = service<PluginSettings>()
        val finalBase = if (enableUncertaintyAnalysis) {
            basePrompt
        } else {
            SIMPLE_SYSTEM_PROMPT
        }

        // Применяем правила
        val promptWithRules = applyRulesToPrompt(finalBase, project)

        // Если нужен анализ неопределенности и есть схема формата, применяем форматирование
        return if (enableUncertaintyAnalysis && responseFormat != null) {
            // TODO: Добавить поддержку форматирования если нужно
            // PromptFormatter.formatPrompt(promptWithRules, responseSchema)
            promptWithRules
        } else {
            promptWithRules
        }
    }

    // Простой системный промпт для случаев без анализа неопределенности
    private val SIMPLE_SYSTEM_PROMPT = """
        Ты — профессиональный AI-ассистент для разработчиков.
        Отвечай на вопросы по программированию, помогай с кодом, объясняй концепции.
        Используй markdown для форматирования ответов.
        Будь вежливым и профессиональным.
    """.trimIndent()
}