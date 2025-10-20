package ru.marslab.ide.ride.model.orchestrator

import kotlinx.datetime.Instant
import java.util.*

/**
 * Типы взаимодействия с пользователем
 */
enum class InteractionType {
    /** Подтверждение (Да/Нет) */
    CONFIRMATION,
    
    /** Выбор из списка */
    CHOICE,
    
    /** Свободный ввод */
    INPUT,
    
    /** Множественный выбор */
    MULTI_CHOICE
}

/**
 * Запрос пользовательского ввода
 */
data class UserPrompt(
    val id: String = UUID.randomUUID().toString(),
    val type: InteractionType,
    val message: String,
    val options: List<String> = emptyList(),
    val defaultValue: String? = null,
    val validator: ((String) -> Boolean)? = null,
    val timeout: Long? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = kotlinx.datetime.Clock.System.now()
) {
    /**
     * Валидирует пользовательский ввод
     */
    fun validate(input: String): ValidationResult {
        // Проверка на пустой ввод для обязательных полей
        if (input.isBlank() && defaultValue == null) {
            return ValidationResult(false, listOf("Ввод не может быть пустым"))
        }
        
        // Проверка выбора из списка
        if (type == InteractionType.CHOICE && options.isNotEmpty()) {
            if (!options.contains(input)) {
                return ValidationResult(false, listOf("Выберите один из предложенных вариантов: ${options.joinToString(", ")}"))
            }
        }
        
        // Проверка множественного выбора
        if (type == InteractionType.MULTI_CHOICE && options.isNotEmpty()) {
            val selectedOptions = input.split(",").map { it.trim() }
            val invalidOptions = selectedOptions.filter { !options.contains(it) }
            if (invalidOptions.isNotEmpty()) {
                return ValidationResult(false, listOf("Недопустимые варианты: ${invalidOptions.joinToString(", ")}"))
            }
        }
        
        // Пользовательская валидация
        validator?.let { validatorFn ->
            if (!validatorFn(input)) {
                return ValidationResult(false, listOf("Введенное значение не прошло валидацию"))
            }
        }
        
        return ValidationResult(true)
    }
    
    /**
     * Форматирует запрос для отображения пользователю
     */
    fun format(): String = buildString {
        appendLine(message)
        
        when (type) {
            InteractionType.CONFIRMATION -> {
                appendLine()
                appendLine("Варианты: ${options.ifEmpty { listOf("Да", "Нет") }.joinToString(" / ")}")
            }
            InteractionType.CHOICE -> {
                appendLine()
                options.forEachIndexed { index, option ->
                    appendLine("${index + 1}. $option")
                }
            }
            InteractionType.MULTI_CHOICE -> {
                appendLine()
                appendLine("Выберите несколько вариантов (через запятую):")
                options.forEachIndexed { index, option ->
                    appendLine("${index + 1}. $option")
                }
            }
            InteractionType.INPUT -> {
                defaultValue?.let {
                    appendLine()
                    appendLine("Значение по умолчанию: $it")
                }
            }
        }
        
        timeout?.let {
            appendLine()
            appendLine("Время ожидания: ${it / 1000}с")
        }
    }
}

/**
 * Результат валидации пользовательского ввода
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun success() = ValidationResult(true)
        fun failure(vararg errors: String) = ValidationResult(false, errors.toList())
    }
}

/**
 * Ответ пользователя на запрос
 */
data class UserResponse(
    val promptId: String,
    val input: String,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * История взаимодействий с пользователем
 */
data class InteractionHistory(
    val planId: String,
    val interactions: MutableList<Interaction> = mutableListOf()
) {
    /**
     * Добавляет взаимодействие в историю
     */
    fun addInteraction(prompt: UserPrompt, response: UserResponse? = null) {
        interactions.add(Interaction(prompt, response))
    }
    
    /**
     * Получает последнее взаимодействие
     */
    fun getLastInteraction(): Interaction? = interactions.lastOrNull()
    
    /**
     * Получает взаимодействие по ID запроса
     */
    fun getInteractionByPromptId(promptId: String): Interaction? =
        interactions.find { it.prompt.id == promptId }
}

/**
 * Одно взаимодействие (запрос + ответ)
 */
data class Interaction(
    val prompt: UserPrompt,
    val response: UserResponse? = null,
    val createdAt: Instant = kotlinx.datetime.Clock.System.now()
) {
    /**
     * Проверяет, получен ли ответ
     */
    fun hasResponse(): Boolean = response != null
}
